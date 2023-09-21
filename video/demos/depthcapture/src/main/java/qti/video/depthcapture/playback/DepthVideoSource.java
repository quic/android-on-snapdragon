/*
 **************************************************************************************************
 * Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 **************************************************************************************************
 */

package qti.video.depthcapture.playback;

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
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
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import qti.video.depth.DepthExtractor;
import qti.video.depth.DepthFormat;

/**
 * Parse local clip, output metadata and decoded video images.
 */
public class DepthVideoSource {

  static final String TAG = "DepthVideoSource";
  static final boolean DEBUG = true;
  private static final int MAX_PENDING_IMAGE_COUNT = 1;
  public static final int MAX_FRAME_COUNT = 290;

  static final int REATTACH_TIMESTAMP_FRAME_RATE = 30;
  static final boolean REATTACH_TIMESTAMP = true;

  public static class Output {
    public int trackIndex = -1;
    public MediaCodec.BufferInfo bufferInfo;
    public int bufferIndex =  -1; // for media codec output
    public Image image; // for media codec output
    public ByteBuffer byteBuffer; // for metadata track
  }

  public interface OutputsListener {
    void onOutputAvailable(int trackIndex);
  }

  private final OutputsListener listener;
  private final String clipPath;
  private final DepthExtractor extractor = new DepthExtractor();
  private boolean extractorAllEos;
  private int trackCount;
  private Track[] tracks;

  private enum State {
    Initial,
    Initialized,
    Started,
    Stopped,
    Released,
  }

  private State state = State.Initial;
  private final HandlerThread handlerThread =  new HandlerThread("DepthVideoSource");
  private final Handler mainHandler;

  /**
   * DepthVideoSource constructor.
   *
   * @param clipPath clip path.
   * @param listener {@link OutputsListener#onOutputAvailable(int)} will be called
   *     whenever an output is available.
   */
  public DepthVideoSource(String clipPath, OutputsListener listener) {
    handlerThread.start();
    mainHandler = new MainHandler(handlerThread.getLooper());
    this.clipPath = clipPath;
    this.listener = listener;
  }

  /**
   * All track formats are available after init() returns.
   */
  public synchronized void init() {
    assert (state == State.Initial);
    mainHandler.obtainMessage(WHAT_INIT).sendToTarget();
    waitForState(State.Initialized);
  }

  public int getTrackCount() {
    assert (state == State.Initialized | state == State.Started);
    return trackCount;
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
    return is10Bit(format);
  }

  public static boolean is10Bit(MediaFormat format) {
    assert (format != null);
    String mime = format.getString(MediaFormat.KEY_MIME);
    assert (mime != null);
    if (!mime.equals("video/hevc")) {
      return false;
    }
    int profile = format.getInteger(MediaFormat.KEY_PROFILE);
    return (profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
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

    public Track(int trackIndex, MediaFormat inputFormat) {
      this(trackIndex, inputFormat, null, null, null);
      assert (isMetadataTrack);
      setOutputFormat(inputFormat);
    }

    public Track(
        int trackIndex,
        MediaFormat inputFormat,
        MediaCodec codec,
        MediaCodec.Callback callback,
        ImageReader imageReader
    ) {
      this.trackIndex = trackIndex;
      this.inputFormat = inputFormat;
      String mime = inputFormat.getString(MediaFormat.KEY_MIME);
      assert (mime != null);
      isMetadataTrack = mime.startsWith("application/");
      this.codec = codec;
      this.callback = callback;
      this.imageReader = imageReader;
    }

    public void setOutputFormat(MediaFormat format) {
      outputFormat = new MediaFormat(format);
      if (inputFormat.containsKey(DepthFormat.KEY_TRACK_TYPE)) {
        outputFormat.setInteger(DepthFormat.KEY_TRACK_TYPE,
            inputFormat.getInteger(DepthFormat.KEY_TRACK_TYPE));
      }
    }

    final int trackIndex;
    final MediaFormat inputFormat;
    final boolean isMetadataTrack;
    public MediaFormat outputFormat;
    final MediaCodec codec;
    final ImageReader imageReader;
    int pendingImageCount;
    final MediaCodec.Callback callback;
    public final Queue<Integer> inputs = new LinkedList<>();
    public int inputCount;
    public int outputCount;
    private final BlockingQueue<Output> outputs = new ArrayBlockingQueue<>(64);
    public final Queue<Integer> unrenderedOutputs = new LinkedList<>();
    public int renderedCount;
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
        // blocking to avoid too many metadata buffered
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
      Track track = tracks[trackIndex];
      track.unrenderedOutputs.offer(i);
      mainHandler.obtainMessage(WHAT_TRY_RENDER_NEXT, trackIndex, -1).sendToTarget();
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
  static final int WHAT_IMAGE_AVAILABLE = 7;
  static final int WHAT_TRY_RENDER_NEXT = 8;

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
          track.setOutputFormat((MediaFormat) msg.obj);
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
          if (track.isMetadataTrack) {
            assert (output.image == null);
            assert (output.byteBuffer != null);
          } else {
            assert (track.codec != null);
            assert (output.image != null);
            assert (output.byteBuffer == null);
            if (DEBUG) {
              Log.v(TAG, "release output trackIndex " + output.trackIndex);
            }
            output.image.close();
            track.pendingImageCount--;
            assert (track.pendingImageCount >= 0);
            pullImage(output.trackIndex);
            tryRenderNext(output.trackIndex);
          }
          break;
        case WHAT_IMAGE_AVAILABLE:
          pullImage(msg.arg1);
          break;
        case WHAT_TRY_RENDER_NEXT:
          tryRenderNext(msg.arg1);
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

  // below methods are called from handler thread.

  // start parsing and decoding in preInit to get output formats
  private void onInit() throws IOException {
    extractor.setDataSource(new File(clipPath));
    trackCount = extractor.getTrackCount();
    tracks = new Track[trackCount];
    for (int trackIndex = 0; trackIndex < trackCount; ++ trackIndex) {
      extractor.selectTrack(trackIndex);
      MediaFormat format = extractor.getTrackFormat(trackIndex);
      String mime = format.getString(MediaFormat.KEY_MIME);
      boolean isVideoOrDepth = mime.startsWith("video/");
      tracks[trackIndex] = isVideoOrDepth
          ? createTrackWithCodec(trackIndex, format)
          : new Track(trackIndex, format);
    }
  }

  private Track createTrackWithCodec(final int trackIndex, MediaFormat format) {
    String mime = format.getString(MediaFormat.KEY_MIME);
    assert (mime != null);
    MediaCodec codec = null;
    try {
      codec = MediaCodec.createDecoderByType(mime);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    MediaCodec.Callback callback = new CodecCallback(trackIndex);
    codec.setCallback(callback, mainHandler);

    MediaFormat codecFormat = new MediaFormat(format);
    boolean is10BitOutput = is10Bit(format);
    if (is10BitOutput) {
      codecFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
          MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010);
    } else {
      codecFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
          MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
    }

    int width = codecFormat.getInteger(MediaFormat.KEY_WIDTH);
    int height = codecFormat.getInteger(MediaFormat.KEY_HEIGHT);
    ImageReader imageReader = createImageReader(width, height, is10BitOutput);
    imageReader.setOnImageAvailableListener(imageReader1 -> {
          Log.v(TAG, "OnImageAvailableListener  track index " + trackIndex);
          mainHandler.obtainMessage(WHAT_IMAGE_AVAILABLE, trackIndex, -1)
              .sendToTarget();
        }, mainHandler);
    codec.configure(codecFormat, imageReader.getSurface(), null, 0);
    codec.start();
    return new Track(trackIndex, format, codec, callback, imageReader);
  }

  @SuppressLint("WrongConstant")
  private static ImageReader createImageReader(int width, int height, boolean is10Bit) {
    return new ImageReader.Builder(width, height)
        .setImageFormat(is10Bit ? ImageFormat.YCBCR_P010 : ImageFormat.YUV_420_888)
        .setUsage(HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE)
        .setMaxImages(MAX_PENDING_IMAGE_COUNT)
        .build();
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
        track.imageReader.close();
      }
    }
    extractor.release();
    trackCount = 0;
    tracks = null;
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

  private void pullImage(int trackIndex) {
    Track track = tracks[trackIndex];
    assert (track != null);
    assert (track.imageReader != null);
    while (true) {
      if (track.pendingImageCount >= MAX_PENDING_IMAGE_COUNT) {
        return;
      }
      Image image = track.imageReader.acquireNextImage();
      if (image == null) {
        return;
      }
      track.pendingImageCount++;

      MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
      bufferInfo.presentationTimeUs = image.getTimestamp() / 1000;
      Output output = new Output();
      output.trackIndex = trackIndex;
      output.bufferInfo = bufferInfo;
      output.image = image;
      mainHandler.obtainMessage(WHAT_OUTPUT_AVAILABLE, trackIndex, -1, output).sendToTarget();
    }
  }

  private void tryRenderNext(int trackIndex) {
    Track track = tracks[trackIndex];
    int unrenderedCount = track.unrenderedOutputs.size();
    if (unrenderedCount == 0) {
      return;
    }
    if (track.renderedCount > track.outputCount) {
      return;
    }
    if (track.pendingImageCount >= MAX_PENDING_IMAGE_COUNT) {
      return;
    }
    Integer outputIndex = track.unrenderedOutputs.poll();
    assert (outputIndex != null);
    track.codec.releaseOutputBuffer(outputIndex, true);
    track.renderedCount++;
    Log.v(TAG, "render buffer for trackIndex " + trackIndex);
  }
}
