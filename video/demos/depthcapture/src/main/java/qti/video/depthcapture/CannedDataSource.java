/*
 **************************************************************************************************
 * Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 **************************************************************************************************
 */

package qti.video.depthcapture;

import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import androidx.annotation.NonNull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Parse local clip, output metadata and decoded video images.
 */
class CannedDataSource {

  static final String TAG = "CannedDataSource";
  static final boolean DEBUG = true;
  static final int MAX_FRAME_COUNT = 300;

  static final int REATTACH_TIMESTAMP_FRAME_RATE = 30;
  static final boolean REATTACH_TIMESTAMP = true;

  public static class Output {
    public int trackIndex = -1;
    public MediaCodec.BufferInfo bufferInfo;
    public int bufferIndex =  -1; // for media codec output
    public Image image; // for media codec output
    public ByteBuffer byteBuffer; // for metadata track
  }

  interface OutputsListener {
    void onOutputAvailable(int trackIndex);
  }

  private final OutputsListener listener;
  private final String clipPath;
  private final MediaExtractor extractor = new MediaExtractor();
  private boolean extractorAllEos;
  private final int trackCount;
  private final boolean[] trackSelection;
  private final Track[] tracks;

  enum State {
    Initial,
    Initialized,
    Started,
    Stopped,
    Released,
  }

  private State state = State.Initial;
  private final HandlerThread handlerThread =  new HandlerThread("CannedDataSource");
  private final Handler mainHandler;

  /**
   * CannedDataSource constructor.
   *
   * @param clipPath clip path.
   * @param trackSelection which tracks will be enabled.
   * @param listener {@link OutputsListener#onOutputAvailable(int)} will be called
   *     whenever an output is available.
   */
  public CannedDataSource(String clipPath,
                          boolean[] trackSelection,
                          OutputsListener listener) {
    handlerThread.start();
    mainHandler = new MainHandler(handlerThread.getLooper());
    this.clipPath = clipPath;
    trackCount = trackSelection.length;
    this.trackSelection = Arrays.copyOf(trackSelection, trackSelection.length);
    this.listener = listener;
    tracks = new Track[trackCount];
  }

  /**
   * All track formats are available after init() returns.
   */
  public synchronized void init() {
    assert (state == State.Initial);
    mainHandler.obtainMessage(WHAT_INIT).sendToTarget();
    waitForState(State.Initialized);
  }

  public MediaFormat getTrackFormat(int trackIndex) {
    assert (state == State.Initialized | state == State.Started);
    assert (tracks[trackIndex] != null);
    return tracks[trackIndex].outputFormat;
  }

  public boolean is10Bit(int trackIndex) {
    assert (state == State.Initialized | state == State.Started);
    assert (tracks[trackIndex] != null);
    MediaFormat format = tracks[trackIndex].inputFormat;
    assert (format != null);
    String mime = format.getString(MediaFormat.KEY_MIME);
    int profile = format.getInteger(MediaFormat.KEY_PROFILE);
    return "video/hevc".equals(mime)
        && (profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
        || profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
        || profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus);
  }

  public void start() {
    assert (state == State.Initialized);
    mainHandler.obtainMessage(WHAT_START).sendToTarget();
    waitForState(State.Started);
  }

  public Output peekOutput(int trackIndex) {
    assert (tracks[trackIndex] != null);
    return tracks[trackIndex].peekOutput();
  }

  /**
   * Get next output for the particular track type. It's non-blocking.
   *
   * @param trackIndex track index of the clip
   * @return the output. Return null if there is no output currently.
   */
  public Output dequeueOutput(int trackIndex) {
    assert (tracks[trackIndex] != null);
    Output output = tracks[trackIndex].dequeueOutput();
    if (DEBUG && output != null) {
      Log.v(TAG, "dequeueOutput trackIndex " + trackIndex
          + ", outputIndex " + output.bufferIndex);
    }
    return output;
  }

  public void queueOutput(Output output) {
    assert (output != null);
    if (DEBUG) {
      Log.v(TAG, "queueOutput trackIndex " + output.trackIndex
          + ", outputIndex " + output.bufferIndex);
    }
    mainHandler.obtainMessage(WHAT_RELEASE_OUTPUT, output.trackIndex, output.bufferIndex, output)
        .sendToTarget();
  }

  public boolean isEos(int trackIndex) {
    Track track = tracks[trackIndex];
    assert (track != null);
    return track.isEos();
  }

  public void stop() {
    assert (state == State.Started);
    mainHandler.obtainMessage(WHAT_STOP).sendToTarget();
    waitForState(State.Stopped);
  }

  public void release() {
    assert (state == State.Stopped);
    mainHandler.obtainMessage(WHAT_RELEASE).sendToTarget();
    waitForState(State.Released);
    handlerThread.quitSafely();
  }

  private class Track {
    public Track(int trackIndex, MediaFormat format) {
      this.trackIndex = trackIndex;
      this.inputFormat = format;
      String mime = format.getString(MediaFormat.KEY_MIME);
      assert (mime.startsWith("video/") || mime.startsWith("audio/")
          || mime.startsWith("application/"));
      isMetadataTrack = mime.startsWith("application/");

      if (isMetadataTrack) {
        codec = null;
        callback = null;
        inputs = null;
        outputFormat = format;
        // WHAT_OUTPUT_FORMAT_AVAILABLE is not sent for metadata track
      } else {
        try {
          codec = MediaCodec.createDecoderByType(mime);
          callback = new CodecCallback(trackIndex);
          inputs = new LinkedList<>();
          startDecoder();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }

    private void startDecoder() throws IOException {
      assert (!isMetadataTrack);
      assert (codec != null);
      assert (callback != null);
      String mime = inputFormat.getString(MediaFormat.KEY_MIME);
      codec.setCallback(callback, mainHandler);

      MediaFormat format = new MediaFormat(inputFormat);
      int profile = format.getInteger(MediaFormat.KEY_PROFILE);
      if (mime.equals("video/hevc")
          && (profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
          || profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
          || profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus)) {
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010);
      } else {
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
      }

      codec.configure(format, null, null, 0);
      codec.start();
    }

    public final int trackIndex;
    private final MediaFormat inputFormat;
    public final boolean isMetadataTrack;
    public MediaFormat outputFormat;
    public final MediaCodec codec;
    private final MediaCodec.Callback callback;
    public final Queue<Integer> inputs;
    public int inputCount;
    public int outputCount;
    private final BlockingQueue<Output> outputs = new ArrayBlockingQueue<>(64);
    private boolean eos = false;

    public synchronized void addOutput(Output output) {
      if (outputCount >= MAX_FRAME_COUNT) {
        if (DEBUG) {
          Log.v(TAG, "ignore output after max, trackIndex " + trackIndex
              + ", outputCount " + outputCount);
        }
        return;
      }
      if (REATTACH_TIMESTAMP) {
        long newPtsUs = 1000000L * outputCount / REATTACH_TIMESTAMP_FRAME_RATE;
        Log.w(TAG, String.format("reattach ts orig ts [%d], new ts [%d], image ts[%d]",
            output.bufferInfo.presentationTimeUs, newPtsUs,
            output.image == null ? -1 : output.image.getTimestamp()));
        output.bufferInfo.presentationTimeUs = newPtsUs;
        if (output.image != null) {
          output.image.setTimestamp(newPtsUs * 1000); // looks like the time unit is nano second
        }
      }
      outputCount++;
      if (DEBUG) {
        Log.v(TAG, "trackIndex " + trackIndex + ", outputCount " + outputCount
            + ", ptsUs " + output.bufferInfo.presentationTimeUs);
      }
      if (outputCount == MAX_FRAME_COUNT) {
        Log.v(TAG, "set output eos at max count, trackIndex " + trackIndex);
        output.bufferInfo.flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
      }

      try {
        // blocking to avoid too many metadata bufferd
        outputs.offer(output, 1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }

      if ((output.bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
        Log.v(TAG, "eos flag found for trackIndex " + trackIndex);
        eos = true;
      }
    }

    // non-blocking
    public Output dequeueOutput() {
      return outputs.poll();
    }

    public Output peekOutput() {
      return outputs.peek();
    }

    public boolean hasOutput() {
      return !outputs.isEmpty();
    }

    public synchronized boolean isEos() {
      return eos && outputs.isEmpty();
    }
  }

  private class CodecCallback extends MediaCodec.Callback {

    private final int trackIndex;

    CodecCallback(int trackIndex) {
      this.trackIndex = trackIndex;
    }

    @Override
    public void onInputBufferAvailable(@NonNull MediaCodec mediaCodec, int i) {
      if (DEBUG) {
        Log.v(TAG, "onInputBufferAvailable trackIndex " + trackIndex + ", inputIndex " + i);
      }
      mainHandler.obtainMessage(WHAT_INPUT_AVAILABLE, trackIndex, i).sendToTarget();
    }

    @Override
    public void onOutputBufferAvailable(@NonNull MediaCodec mediaCodec, int i,
                                        @NonNull MediaCodec.BufferInfo bufferInfo) {
      if (DEBUG) {
        Log.v(TAG, "onOutputBufferAvailable trackIndex " + trackIndex + ", outputIndex " + i);
      }
      Output output = new Output();
      output.trackIndex = trackIndex;
      output.bufferIndex = i;
      output.bufferInfo = bufferInfo;
      output.image = mediaCodec.getOutputImage(i);
      assert (output.image != null);
      mainHandler.obtainMessage(WHAT_OUTPUT_AVAILABLE, trackIndex, i, output).sendToTarget();
    }

    @Override
    public void onError(@NonNull MediaCodec mediaCodec, @NonNull MediaCodec.CodecException e) {
      throw new RuntimeException(e);
    }

    @Override
    public void onOutputFormatChanged(@NonNull MediaCodec mediaCodec,
                                      @NonNull MediaFormat mediaFormat) {
      if (DEBUG) {
        Log.v(TAG, "onOutputFormatChanged");
      }
      mainHandler.obtainMessage(WHAT_OUTPUT_FORMAT_AVAILABLE, trackIndex, -1, mediaFormat)
          .sendToTarget();
    }
  }

  // msg.arg1 = trackIndex
  // msg.arg2 = bufferIndex
  static final int WHAT_INPUT_AVAILABLE = 0;
  static final int WHAT_OUTPUT_AVAILABLE = 1; // msg.obj = Output
  // we don't send WHAT_OUTPUT_FORMAT_AVAILABLE for metadata track
  static final int WHAT_OUTPUT_FORMAT_AVAILABLE = 2; // msg.obj = MediaFormat
  static final int WHAT_RELEASE_OUTPUT = 4; // msg.obj = Output
  static final int WHAT_TRY_FILL_INPUT = 6;
  static final int WHAT_INIT = 10;
  static final int WHAT_START = 11;
  static final int WHAT_STOP = 12;
  static final int WHAT_RELEASE = 13;

  private class MainHandler extends Handler {
    public MainHandler(Looper looper) {
      super(looper);
    }

    @Override
    public void handleMessage(Message msg) {
      if (DEBUG) {
        Log.v(TAG, "msg what: " + msg.what);
      }
      switch (msg.what) {
        case WHAT_INPUT_AVAILABLE:
          // only for media codec
          tracks[msg.arg1].inputs.offer(msg.arg2);
          // fall through
        case WHAT_TRY_FILL_INPUT:
          readInput();
          break;
        case WHAT_OUTPUT_AVAILABLE:
          assert (msg.obj != null);
          tracks[msg.arg1].addOutput((Output) msg.obj);
          if (DEBUG) {
            Log.v(TAG, "output available for track " + msg.arg1);
          }
          if (state == State.Started && listener != null) {
            listener.onOutputAvailable(msg.arg1);
          }
          break;
        case WHAT_OUTPUT_FORMAT_AVAILABLE: {
          assert (state == State.Initial);
          Track track = tracks[msg.arg1];
          assert (track != null);
          assert (track.outputFormat == null);
          track.outputFormat = (MediaFormat) msg.obj;
          boolean initialized = true;
          for (Track t : tracks) {
            if (t != null && t.outputFormat == null) {
              initialized = false;
              break;
            }
          }
          if (initialized) {
            setState(State.Initialized);
          }
          break;
        }
        case WHAT_RELEASE_OUTPUT:
          Output output = (Output) msg.obj;
          Track track = tracks[output.trackIndex];
          assert (track != null);
          if (!track.isMetadataTrack) {
            assert (track.codec != null);
            if (DEBUG) {
              Log.v(TAG, "release output trackIndex " + output.trackIndex
                  + ", outputIndex " + output.bufferIndex);
            }
            track.codec.releaseOutputBuffer(output.bufferIndex, true);
          }
          break;
        case WHAT_INIT:
          assert (state == State.Initial);
          try {
            onInit();
            break;
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        case WHAT_START:
          assert (state == State.Initialized);
          onStart();
          setState(State.Started);
          break;
        case WHAT_STOP:
          assert (state == State.Started);
          onStop();
          setState(State.Stopped);
          break;
        case WHAT_RELEASE:
          assert (state == State.Stopped);
          onRelease();
          setState(State.Released);
          break;
        default:
          throw new IllegalArgumentException("unknown msg: " + msg);
      }
    }
  }

  private synchronized void waitForState(State newState) {
    try {
      while (state != newState) {
        wait(10); // 10ms
      }
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private synchronized void setState(State newState) {
    Log.v(TAG, "new state " + newState);
    state = newState;
    notifyAll();
  }

  public State getState() {
    return state;
  }

  // below methods are called from handler thread.

  // start parsing and decoding in preInit to get output formats
  private void onInit() throws IOException {
    extractor.setDataSource(clipPath);
    assert (extractor.getTrackCount() == trackCount);
    for (int trackIndex = 0; trackIndex < trackCount; ++ trackIndex) {
      if (!trackSelection[trackIndex]) {
        continue;
      }
      extractor.selectTrack(trackIndex);
      MediaFormat format = extractor.getTrackFormat(trackIndex);
      tracks[trackIndex] = new Track(trackIndex, format);
    }

    int metadataTrackCount = 0;
    for (int trackIndex = 0; trackIndex < trackCount; ++ trackIndex) {
      Track track = tracks[trackIndex];
      assert ((trackSelection[trackIndex] == (track != null)));
      if (track != null && track.isMetadataTrack) {
        metadataTrackCount++;
      }
    }
    assert (metadataTrackCount == 0 || metadataTrackCount == 1);
  }

  private void onStart() {
    Log.v(TAG, "onStart");
    if (listener != null) {
      for (Track track : tracks) {
        if (track != null && track.hasOutput()) {
          listener.onOutputAvailable(track.trackIndex);
        }
      }
    }
  }

  private void onStop() {
    for (Track track : tracks) {
      if (track != null) {
        Log.v(TAG, "trackIndex " + track.trackIndex + ", total output: " + track.outputCount);
        if (!track.isMetadataTrack) {
          track.codec.stop();
        }
      }
    }
  }

  private void onRelease() {
    for (Track track : tracks) {
      if (track != null && !track.isMetadataTrack) {
        track.codec.release();
      }
    }
    extractor.release();
  }

  private void readInput() {
    if (extractorAllEos) {
      return;
    }
    int trackIndex = extractor.getSampleTrackIndex();
    if (trackIndex == -1) {
      Log.v(TAG, "all EOS");
      extractorAllEos = true;
      return;
    }
    Track track = tracks[trackIndex];
    assert (track != null);
    ByteBuffer buf;
    int inputIndex = -1;
    if (track.isMetadataTrack) {
      buf = ByteBuffer.allocate((int) extractor.getSampleSize());
    } else if (!track.inputs.isEmpty()) {
      Integer v = track.inputs.poll();
      assert (v != null);
      inputIndex = v;
      if (DEBUG) {
        Log.v(TAG, "fill input trackIndex " + trackIndex + ", inputIndex " + inputIndex);
      }
      buf = track.codec.getInputBuffer(inputIndex);
    } else {
      if (DEBUG) {
        Log.w(TAG, "waiting input buffer for trackIndex" + trackIndex);
      }
      return;
    }

    int sz = extractor.readSampleData(buf, 0);
    assert (sz == extractor.getSampleSize());
    buf.flip();
    long pts = extractor.getSampleTime();
    int extractorFlags = extractor.getSampleFlags();
    int codecFlags = 0;
    if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
      codecFlags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
    }
    if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
      codecFlags |= MediaCodec.BUFFER_FLAG_PARTIAL_FRAME;
    }
    if (!extractor.advance()) { // advance
      codecFlags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
    }

    tracks[trackIndex].inputCount++;

    if (track.isMetadataTrack) {
      MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
      bufferInfo.set(
          0,
          sz,
          pts,
          codecFlags
      );

      Output output = new Output();
      output.trackIndex = trackIndex;
      output.bufferInfo = bufferInfo;
      output.byteBuffer = buf;
      mainHandler.obtainMessage(WHAT_OUTPUT_AVAILABLE, trackIndex, -1, output).sendToTarget();
    } else {
      track.codec.queueInputBuffer(inputIndex, 0, sz, pts, codecFlags);
    }

    mainHandler.obtainMessage(WHAT_TRY_FILL_INPUT).sendToTarget();
  }
}
