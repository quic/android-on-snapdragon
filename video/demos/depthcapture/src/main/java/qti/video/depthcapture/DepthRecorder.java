/*
 **************************************************************************************************
 * Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 **************************************************************************************************
 */

package qti.video.depthcapture;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import androidx.annotation.NonNull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import qti.video.QMediaExtensions;
import qti.video.depth.DepthFormat;
import qti.video.depth.DepthMuxer;

/*
 * DepthRecorder has similar state machine and APIs as MediaRecorder.
 * States:
 * 1. Initial
 *   setMetadataSource()
 *   -> setTrackSource() for video or depth track type ->
 * 2. Initialized
 *   setMetadataSource()
 *   setTrackSource()
 *   -> setOutputFormat() ->
 * 3. DataSourceConfigured
 *   setTrackEncoder() for each track type
 *   setVideoFrameRate()
 *   setTrackSize()
 *   setOutputFile()
 *   setPreviewDisplay()
 *   -> prepare() ->
 * 4. Prepared
 *   getTrackSurface()
 *   -> start() ->
 * 5. Recording
 *   -> stop() ->
 * 6. Initial (Cannot be re-initialized)
 *   -> reset ->
 * 7. Released
 *
 *   -> any error happens ->
 * other state: Error
 */
public final class DepthRecorder {
  static final String TAG = "DepthRecorder";
  static final boolean DEBUG = true;

  public static final int HEVC = 5;
  public static final int DEPTH_ENCODER_HEVC_8BIT = 0x10;
  public static final int DEPTH_ENCODER_HEVC_10BIT = 0x11;
  public static final int TRACK_SOURCE_SURFACE = 2;
  public static final int OUTPUT_FORMAT_DEPTH_MPEG_4 = 2;

  private final Context context;
  private final Track[] tracks = new Track[DepthFormat.MAX_TRACK_TYPE_COUNT];
  private final Surface[] previewSurfaces = new Surface[DepthFormat.MAX_TRACK_TYPE_COUNT];
  private final HandlerThread handlerThread = new HandlerThread("DepthRecorder");
  private final MainHandler mainHandler;
  private final HandlerThread decoderThread = new HandlerThread("DecodeRende");
  private final Handler decoderHandler;
  private State state = State.Initial;
  private TrackDataSource metadataSource;
  private int frameRate = -1;
  private int outputContainerFormat = -1;
  private String outputPath;
  private DepthMuxer muxer;

  public DepthRecorder(Context context) {
    this.context = context;

    handlerThread.start();
    mainHandler = new MainHandler(handlerThread.getLooper());

    decoderThread.start();
    decoderHandler = new Handler(decoderThread.getLooper());
  }

  public void setTrackSource(int trackType, int source) {
    assert (source == TRACK_SOURCE_SURFACE);
    assert (state == State.Initial || state == State.Initialized);
    assert (tracks[trackType] == null);
    Track track = new Track(trackType, source);
    tracks[trackType] = track;
    if (trackType == DepthFormat.TRACK_TYPE_DEPTH_INVERSE
        || trackType == DepthFormat.TRACK_TYPE_DEPTH_LINEAR) {
      Log.v(TAG, "Create metadata track for depth");
    }
    if (state == State.Initial) {
      setState(State.Initialized);
    }
  }

  public void setMetadataSource(TrackDataSource metadataSource) {
    assert (state == State.Initial || state == State.Initialized);
    assert (metadataSource != null);
    this.metadataSource = metadataSource;
    Track track = new Track(DepthFormat.TRACK_TYPE_METADATA, -1);
    track.isMetadataTrack = true;
    tracks[DepthFormat.TRACK_TYPE_METADATA] = track;
  }

  public void setOutputFormat(int outputFormat) {
    assert (outputFormat == OUTPUT_FORMAT_DEPTH_MPEG_4);
    assert (state == State.Initialized);
    outputContainerFormat = outputFormat;
    setState(State.DataSourceConfigured);
  }

  public void setOutputFile(String path) {
    assert (state == State.DataSourceConfigured);
    outputPath = path;
  }

  public void setPreviewDisplay(int trackType, Surface surface) {
    assert (trackType != DepthFormat.TRACK_TYPE_METADATA);
    previewSurfaces[trackType] = surface;
  }

  public void setTrackSize(int trackType, int width, int height) {
    assert (state == State.DataSourceConfigured);
    tracks[trackType].size = new Size(width, height);
  }

  /**
   * Set encoder type for the given track type.
   *
   * @param trackType which track type to set the encoder type
   * @param encoder encoder type. For video tracks, it can only be {@link #HEVC}. For depth tracks
   *     it can be one of {@link #DEPTH_ENCODER_HEVC_8BIT} and
   *     {@link #DEPTH_ENCODER_HEVC_10BIT}
   */
  public void setTrackEncoder(int trackType, int encoder) {
    assert (state == State.DataSourceConfigured);
    switch (trackType) {
      case DepthFormat.TRACK_TYPE_SHARP_VIDEO:
      case DepthFormat.TRACK_TYPE_TRANSLUCENT_VIDEO:
        assert (encoder == HEVC);
        break;
      case DepthFormat.TRACK_TYPE_DEPTH_LINEAR:
      case DepthFormat.TRACK_TYPE_DEPTH_INVERSE:
        assert (encoder == DEPTH_ENCODER_HEVC_10BIT);
        break;
      default:
        assert (false);
        throw new IllegalArgumentException("unsupported encoder for track type: " + trackType);
    }
    tracks[trackType].encoder = encoder;
  }

  public void setVideoFrameRate(int rate) {
    assert (state == State.DataSourceConfigured);
    frameRate = rate;
  }

  public void prepare() {
    Log.v(TAG, "prepare");
    assert (state == State.DataSourceConfigured);
    mainHandler.obtainMessage(WHAT_PREPARE).sendToTarget();
    waitForState(State.Prepared);
  }

  public Surface getTrackSurface(int trackType) {
    assert (state == State.Prepared);
    Track track = tracks[trackType];
    assert (track != null);
    assert (track.codec != null);
    assert (track.inputSurface == null);
    track.inputSurface = track.codec.createInputSurface();
    return track.inputSurface;
  }

  public void start() {
    Log.v(TAG, "start");
    assert (state == State.Prepared);
    mainHandler.obtainMessage(WHAT_START).sendToTarget();
    waitForState(State.Recording);
  }

  public void stop() {
    Log.v(TAG, "stop");
    assert (state == State.Recording);
    mainHandler.obtainMessage(WHAT_STOP).sendToTarget();
    waitForState(State.InitialAfterReset);
  }

  public void release() {
    Log.v(TAG, "release");
    assert (state == State.InitialAfterReset);
    mainHandler.obtainMessage(WHAT_RELEASE).sendToTarget();
    waitForState(State.Released);
    handlerThread.quitSafely();
    decoderThread.quitSafely();
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
    state = newState;
    notifyAll();
  }


  private enum State {
    Initial,
    Initialized,
    DataSourceConfigured,
    Prepared,
    Recording,
    InitialAfterReset,
    Released,
  }

  private static class Output extends TrackBuffer {
    public final int trackType;
    public final int bufferIndex;

    Output(int trackType, int bufferIndex,
        ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo, Object obj) {
      super(byteBuf, bufferInfo, obj);
      this.trackType = trackType;
      this.bufferIndex = bufferIndex;
    }
  }

  private static class Track {
    Track(int trackType, int source) {
      this.trackType = trackType;
      this.source = source;
    }

    public final int trackType;
    public final int source;

    public int encoder = -1;
    public Size size;
    public boolean isMetadataTrack;
    private MediaFormat codecFormat;
    public MediaFormat outputFormat;
    public MediaCodec codec;
    public Surface inputSurface;
    public int muxerTrackIndex;
    private MediaCodec.Callback callback;
    private final Queue<Output> outputs = new LinkedList<>();
    public int muxCount;
    private boolean eos = false;
    private DecodeRender preview;
  }

  private static final int WHAT_PREPARE = 0;
  private static final int WHAT_START = 1;
  private static final int WHAT_STOP = 2;
  private static final int WHAT_RELEASE = 3;

  // arg1 = trackType
  private static final int WHAT_OUTPUT_AVAILABLE = 10; // obj = Output
  private static final int WHAT_OUTPUT_FORMAT_AVAILABLE = 11; // obj = MediaFormat
  private static final int WHAT_EOS = 12;
  private static final int WHAT_TRY_MUX_NEXT = 20;

  private class MainHandler extends Handler {
    public MainHandler(Looper looper) {
      super(looper);
    }

    @Override
    public void handleMessage(Message msg) {
      Log.v(TAG, "msg what: " + msg.what);
      switch (msg.what) {
        case WHAT_PREPARE:
          assert (state == State.DataSourceConfigured);
          onPrepare();
          setState(State.Prepared);
          break;
        case WHAT_START:
          assert (state == State.Prepared);
          onStart();
          setState(State.Recording);
          break;
        case WHAT_STOP:
          assert (state == State.Recording);
          onStop();
          setState(State.InitialAfterReset);
          break;
        case WHAT_RELEASE:
          assert (state == State.InitialAfterReset);
          onRelease();
          setState(State.Released);
          break;
        case WHAT_OUTPUT_AVAILABLE:
          assert (state == State.Recording);
          Output output = (Output) msg.obj;
          assert (output != null);
          handleOutput(output);
          break;
        case WHAT_OUTPUT_FORMAT_AVAILABLE:
          assert (state == State.Recording);
          MediaFormat format = (MediaFormat) msg.obj;
          assert (format != null);
          onOutputFormatAvailable(msg.arg1, format);
          break;
        case WHAT_EOS:
          assert (state == State.Recording);
          onEos(msg.arg1);
          break;
        case WHAT_TRY_MUX_NEXT:
          assert (state == State.Recording);
          onTryMuxNext();
          break;
        default:
      }
    }
  }

  // below methods are called from handler thread.
  private void onPrepare() {
    Log.v(TAG, "onPrepare");
    for (int trackType = 0; trackType < DepthFormat.MAX_TRACK_TYPE_COUNT; ++trackType) {
      Track track = tracks[trackType];
      Surface previewSurface = previewSurfaces[trackType];
      if (previewSurface != null && track == null) {
        Log.e(TAG, "trackType " + trackType + " has preview surface but not output");
        assert (false);
      }
      if (previewSurface != null) {
        track.preview = new DecodeRender(previewSurface);
      }
    }
    for (final Track track : tracks) {
      if (track != null) {
        if (track.trackType == DepthFormat.TRACK_TYPE_METADATA) {
          assert (metadataSource != null);
          metadataSource.setOutputListener(new TrackDataSource.OutputListener() {
            private int metadataCount;
            @Override
            public void onOutputAvailable() {
              while (true) {
                TrackBuffer trackBuf = metadataSource.dequeueBuffer();
                if (trackBuf == null) {
                  break;
                }
                ++metadataCount;
                if (DEBUG) {
                  Log.v(TAG, "metadata count" + metadataCount);
                }
                Output output = new Output(DepthFormat.TRACK_TYPE_METADATA, -1,
                    trackBuf.byteBuf, trackBuf.bufferInfo, trackBuf);
                mainHandler.obtainMessage(WHAT_OUTPUT_AVAILABLE, output.trackType, -1, output)
                    .sendToTarget();
              }
            }

            @Override
            public void onEos() {
              mainHandler.obtainMessage(
                  WHAT_EOS, DepthFormat.TRACK_TYPE_METADATA, -1).sendToTarget();
            }
          });
        } else {
          String mime = "video/hevc";
          MediaFormat format = new MediaFormat();
          format.setString(MediaFormat.KEY_MIME, mime);
          format.setInteger(MediaFormat.KEY_WIDTH, track.size.getWidth());
          format.setInteger(MediaFormat.KEY_HEIGHT, track.size.getHeight());
          format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
          format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 30);
          format.setInteger(MediaFormat.KEY_BIT_RATE, 2000000);
          if (track.encoder == DEPTH_ENCODER_HEVC_10BIT) {
            format.setInteger(MediaFormat.KEY_PROFILE,
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010);
          } else {
            assert (track.encoder == HEVC || track.encoder == DEPTH_ENCODER_HEVC_8BIT);
            format.setInteger(MediaFormat.KEY_PROFILE,
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
          }
          if (track.trackType == DepthFormat.TRACK_TYPE_DEPTH_INVERSE
              || track.trackType == DepthFormat.TRACK_TYPE_DEPTH_LINEAR) {
            format.setInteger(QMediaExtensions.KEY_PROSIGHT_ENCODER_MODE,
                2 /* QcDepth */);
          }

          track.codecFormat = format;
          try {
            track.codec = MediaCodec.createEncoderByType(mime);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
          track.callback = new CodecCallback(track.trackType);
          track.codec.setCallback(track.callback, mainHandler);
          track.codec.configure(track.codecFormat, null, null,
              MediaCodec.CONFIGURE_FLAG_ENCODE);
        }
      }
    }
  }

  private void onStart() {
    Log.v(TAG, "onStart");
    for (Track track : tracks) {
      if (track == null) {
        continue;
      }
      if (track.trackType == DepthFormat.TRACK_TYPE_METADATA) {
        track.outputFormat = metadataSource.getFormat();
      } else {
        assert (track.codec != null);
        track.codec.start();
      }
    }
  }

  private void onStop() {
    Log.v(TAG, "onStop");
    for (Track track : tracks) {
      if (track == null) {
        continue;
      }
      Log.v(TAG, "trackType " + track.trackType + ", total mux output: " + track.muxCount);
      if (track.trackType != DepthFormat.TRACK_TYPE_METADATA) {
        assert (track.codec != null);
        track.codec.stop();
        if (track.preview != null) {
          track.preview.stop();
        }
      }
    }
    assert (muxer != null);
    muxer.stop();
  }

  private void onRelease() {
    Log.v(TAG, "onRelease");
    for (Track track : tracks) {
      if (track == null) {
        continue;
      }
      if (track.trackType != DepthFormat.TRACK_TYPE_METADATA) {
        assert (track.codec != null);
        track.codec.release();
        if (track.preview != null) {
          track.preview.release();
        }
      }
    }
    Arrays.fill(tracks, null);
    assert (muxer != null);
    muxer.release();
  }

  private void handleOutput(@NonNull Output output) {
    Track track = tracks[output.trackType];
    if (track.preview != null) {
      track.preview.queueInput(output);
    }
    track.outputs.offer(output);
    mainHandler.obtainMessage(WHAT_TRY_MUX_NEXT).sendToTarget();
  }

  private void onTryMuxNext() {
    if (muxer == null) {
      Log.w(TAG, "muxer is null");
      return;
    }
    int nextTrackType = nextMuxTrackType();
    if (nextTrackType == -1) {
      return;
    }
    Track track = tracks[nextTrackType];
    Output output = track.outputs.poll();
    assert (output != null);
    track.muxCount++;
    if (DEBUG) {
      Log.v(TAG, "mux for track type " + nextTrackType + ", size " + output.bufferInfo.size
          + ", timeUs " + output.bufferInfo.presentationTimeUs
          + ", muxCount " + track.muxCount);
    }
    muxer.writeSampleData(track.muxerTrackIndex, output.byteBuf, output.bufferInfo);

    if (track.trackType == DepthFormat.TRACK_TYPE_METADATA) {
      assert (output.internalObj != null);
      metadataSource.queueBuffer((TrackBuffer) output.internalObj);
    } else {
      track.codec.releaseOutputBuffer(output.bufferIndex, false);
    }

    mainHandler.obtainMessage(WHAT_TRY_MUX_NEXT).sendToTarget();
  }

  private int nextMuxTrackType() {
    int nextTrackType = -1;
    long ts = Long.MAX_VALUE;

    if (DEBUG) {
      long[] tss = new long[DepthFormat.MAX_TRACK_TYPE_COUNT];
      int[] counts = new int[DepthFormat.MAX_TRACK_TYPE_COUNT];
      Arrays.fill(tss, -1);
      for (int tt = 0; tt < DepthFormat.MAX_TRACK_TYPE_COUNT; ++tt) {
        Track t = tracks[tt];
        if (t != null) {
          counts[tt] = t.outputs.size();
        }
        if (t != null && !t.outputs.isEmpty()) {
          tss[tt] = t.outputs.peek().bufferInfo.presentationTimeUs;
        }
      }
      Log.v(TAG, "nextMuxTrackType tss " + Arrays.toString(tss)
          + ", counts " + Arrays.toString(counts));
    }

    for (Track track : tracks) {
      if (track == null) {
        continue;
      }
      if (track.outputs.isEmpty()) {
        if (track.eos) {
          continue;
        } else {
          Log.v(TAG, "nextMuxTrackType returns -1");
          // cannot decide which track to mux
          return -1;
        }
      }
      Output output = track.outputs.peek();
      assert (output != null);
      if (output.bufferInfo.presentationTimeUs < ts) {
        ts = output.bufferInfo.presentationTimeUs;
        nextTrackType = track.trackType;
      }
    }
    Log.v(TAG, "nextMuxTrackType returns " + nextTrackType);
    return nextTrackType;
  }

  private void onOutputFormatAvailable(int trackType, MediaFormat format) {
    if (tracks[trackType].outputFormat != null) {
      Log.w(TAG, "update output format for track type: " + trackType);
      tracks[trackType].outputFormat = format;
      return;
    }

    {
      Track track = tracks[trackType];
      track.outputFormat = format;
      if (track.preview != null) {
        track.preview.configure(format);
        track.preview.start();
      }
    }

    boolean allOutputFormatAvailable = true;
    for (Track track : tracks) {
      if (track != null) {
        if (track.outputFormat == null) {
          allOutputFormatAvailable = false;
          break;
        }
      }
    }

    if (allOutputFormatAvailable) {
      Log.w(TAG, "all output formats available");
      assert (muxer == null);
      try {
        assert (outputContainerFormat == OUTPUT_FORMAT_DEPTH_MPEG_4);
        muxer = new DepthMuxer(outputPath);
        for (Track track : tracks) {
          if (track != null) {
            if (track.trackType != DepthFormat.TRACK_TYPE_TRANSLUCENT_VIDEO) {
              // store translucent video in outer clip
              track.outputFormat.setInteger(DepthFormat.KEY_TRACK_TYPE, track.trackType);
            }
            track.muxerTrackIndex = muxer.addTrack(track.outputFormat);
          }
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      muxer.start();
    }
    mainHandler.obtainMessage(WHAT_TRY_MUX_NEXT).sendToTarget();
  }

  private void onEos(int trackType) {
    tracks[trackType].eos = true;
    mainHandler.obtainMessage(WHAT_TRY_MUX_NEXT).sendToTarget();
  }

  private class CodecCallback extends MediaCodec.Callback {
    CodecCallback(int trackType) {
      this.trackType = trackType;
    }

    private final int trackType;
    private int outputCount;

    @Override
    public void onInputBufferAvailable(@NonNull MediaCodec mediaCodec, int i) {
      Log.e(TAG, "unexpected onInputBufferAvailable for track type: " + trackType);
    }

    @Override
    public void onOutputBufferAvailable(@NonNull MediaCodec mediaCodec, int i,
                                        @NonNull MediaCodec.BufferInfo bufferInfo) {
      outputCount++;
      if (DEBUG) {
        Log.v(TAG, "onOutputBufferAvailable trackType " + trackType + ", outputIndex " + i
            + ", ptsUs " + bufferInfo.presentationTimeUs
            + ", outputCount " + outputCount);
      }
      Output output = new Output(trackType, i,
          mediaCodec.getOutputBuffer(i), bufferInfo, null);
      mainHandler.obtainMessage(WHAT_OUTPUT_AVAILABLE, trackType, -1, output).sendToTarget();
    }

    @Override
    public void onError(@NonNull MediaCodec mediaCodec, @NonNull MediaCodec.CodecException e) {
      Log.e(TAG, "Codec error for track type: " + trackType);
      throw new RuntimeException(e);
    }

    @Override
    public void onOutputFormatChanged(@NonNull MediaCodec mediaCodec,
                                      @NonNull MediaFormat mediaFormat) {
      Log.v(TAG, "onOutputFormatChanged");
      mainHandler.obtainMessage(WHAT_OUTPUT_FORMAT_AVAILABLE, trackType, -1, mediaFormat)
          .sendToTarget();
    }
  }

  private class DecodeRender {
    private final Surface surface;
    private MediaCodec codec;
    private final BlockingQueue<Integer> inputs = new LinkedBlockingQueue<>();

    public DecodeRender(Surface surface) {
      this.surface = surface;
    }

    public void configure(MediaFormat format) {
      if (DEBUG) {
        Log.v(TAG, "DRC config");
      }
      assert (codec == null);
      String mime = format.getString(MediaFormat.KEY_MIME);
      try {
        codec = MediaCodec.createDecoderByType(mime);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      codec.setCallback(new DecodeRenderCallback(), decoderHandler);
      assert (surface != null);
      codec.configure(format, surface, null, 0);
    }

    public void start() {
      if (DEBUG) {
        Log.v(TAG, "DRC start");
      }
      codec.start();
    }

    public void queueInput(TrackBuffer buf) {
      if (DEBUG) {
        Log.v(TAG, "DRC queueInput");
      }
      Integer iv = null;
      try {
        iv = inputs.take();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      assert (iv != null);
      int inputIndex = iv;
      ByteBuffer inputBuffer = codec.getInputBuffer(inputIndex);

      buf.byteBuf.rewind();
      buf.byteBuf.limit(buf.bufferInfo.size);
      inputBuffer.rewind();
      inputBuffer.put(buf.byteBuf);

      if (DEBUG) {
        Log.v(TAG, "DRC queueInputBuffer index " + inputIndex
            + ", size " + buf.bufferInfo.size);
      }
      codec.queueInputBuffer(inputIndex, 0, buf.bufferInfo.size,
          buf.bufferInfo.presentationTimeUs, buf.bufferInfo.flags);
    }

    public void stop() {
      codec.stop();
    }

    public void release() {
      codec.release();
    }

    class DecodeRenderCallback extends MediaCodec.Callback {

      @Override
      public void onInputBufferAvailable(@NonNull MediaCodec mediaCodec, int i) {
        if (DEBUG) {
          Log.v(TAG, "DRC onInputBufferAvailable");
        }
        try {
          inputs.put(i);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public void onOutputBufferAvailable(@NonNull MediaCodec mediaCodec, int i,
                                          @NonNull MediaCodec.BufferInfo bufferInfo) {
        if (DEBUG) {
          Log.v(TAG, "DRC onOutputBufferAvailable");
        }
        mediaCodec.releaseOutputBuffer(i, true);
      }

      @Override
      public void onError(@NonNull MediaCodec mediaCodec, @NonNull MediaCodec.CodecException e) {
        throw new RuntimeException(e);
      }

      @Override
      public void onOutputFormatChanged(@NonNull MediaCodec mediaCodec,
                                        @NonNull MediaFormat mediaFormat) {
        // nothing
        if (DEBUG) {
          Log.v(TAG, "DRC onOutputFormatChanged");
        }
      }
    }
  }
}
