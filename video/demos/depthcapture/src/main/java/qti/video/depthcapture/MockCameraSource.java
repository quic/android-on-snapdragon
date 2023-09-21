/*
 **************************************************************************************************
 * Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 **************************************************************************************************
 */

package qti.video.depthcapture;

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageWriter;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import java.nio.ByteBuffer;
import qti.video.depth.DepthFormat;

public final class MockCameraSource implements CameraSource {

  static final String TAG = "MockCameraSource";
  static final boolean DEBUG = true;

  public static final int VIDEO_FRAME_RATE = 30;
  static final int AVSYNC_THRESHOLD_MS = 10;
  static final boolean DISABLE_AVSYNC = true;

  // push clips from \\hw-lubiny-lv\Public\depth
  /**
   * IMG_0400_remuxed.mp4: total 4 tracks
   *     0 - video avc 3840x2160
   *     1 - audio aac
   *     2 - depth hevc 512x288
   *     3 - metadata
   */
  private static final String VIDEO_INPUT_CLIP = AppConfig.MOCK_CAMERA_INPUT_CLIP;
  private static final int VIDEO_INPUT_TRACK_COUNT = 4;
  private static final boolean[] VIDEO_TRACK_SELECTION = new boolean[] {
      true,
      false,
      true,
      true,
  };

  private static final int MAX_TRACK_TYPES = DepthFormat.MAX_TRACK_TYPE_COUNT;
  // mapping: depth type type -> canned data track
  private static final int[] TRACK_TYPES_MAP = new int[] {
      0, // TRACK_TYPE_SHARP_VIDEO (0)
      2, // TRACK_TYPE_DEPTH_LINEAR (1)
      -1, // TRACK_TYPE_DEPTH_INVERSE (2)
      3, // TRACK_TYPE_METADATA (3)
      0, // TRACK_TYPE_TRANSLUCENT_VIDEO (4)
  };
  private static final int METADATA_TRACK_INDEX = TRACK_TYPES_MAP[DepthFormat.TRACK_TYPE_METADATA];
  private static final int MAX_PENDING_IMAGE_COUNT = 2;
  private final HandlerThread handlerThread = new HandlerThread("MockCameraSource");
  private final MainHandler mainHandler;

  private final CannedDataSource cannedDataSource;
  private final CannedDataSource.OutputsListener
      outputsListener = new CannedDataSource.OutputsListener() {
    @Override
    public void onOutputAvailable(int trackIndex) {
      Log.v(TAG, "onOutputAvailable for trackIndex " + trackIndex);
      mainHandler.obtainMessage(WHAT_OUTPUT_AVAILABLE, trackIndex, -1).sendToTarget();
    }
  };

  private final ImageWriter[] imageWriters = new ImageWriter[MAX_TRACK_TYPES];
  private final int[] pendingBufferCountInSurfaces = new int[MAX_TRACK_TYPES];
  private MetaDataSource metaDataSource;
  private OnFinishedListener onFinishedListener;
  private boolean allEos;
  private long firstFrameTimeMs = -1;

  public MockCameraSource() {
    handlerThread.start();
    mainHandler = new MainHandler(handlerThread.getLooper());
    cannedDataSource = new CannedDataSource(VIDEO_INPUT_CLIP, VIDEO_TRACK_SELECTION,
        outputsListener);
  }

  @Override
  public void init() {
    Log.v(TAG, "init");
    cannedDataSource.init();
  }

  @Override
  public boolean supportTrack(int trackType) {
    return TRACK_TYPES_MAP[trackType] >= 0;
  }

  @Override
  public MediaFormat getTrackFormat(int trackType) {
    int track = TRACK_TYPES_MAP[trackType];
    if (track < 0) {
      throw new IllegalArgumentException("Unsupported track type: " + trackType);
    }
    return cannedDataSource.getTrackFormat(track);
  }

  @Override
  public void setOutputSurface(int trackType, Surface surface) {
    assert (supportTrack(trackType));
    assert (imageWriters[trackType] == null);

    int track = TRACK_TYPES_MAP[trackType];
    imageWriters[trackType] = createImageWriter(getTrackFormat(trackType), surface,
        cannedDataSource.is10Bit(track));
    imageWriters[trackType].setOnImageReleasedListener(imageWriter -> {
      assert (pendingBufferCountInSurfaces[trackType] > 0);
      pendingBufferCountInSurfaces[trackType]--;
      mainHandler.obtainMessage(WHAT_TRY_DELIVER_IMAGES).sendToTarget();
    }, mainHandler);
  }

  @Override
  public TrackDataSource getMetadataDataSource() {
    assert (cannedDataSource.getState() == CannedDataSource.State.Initialized);
    if (metaDataSource == null) {
      metaDataSource = new MetaDataSource();
    }
    return metaDataSource;
  }

  @SuppressLint("WrongConstant")
  private ImageWriter createImageWriter(MediaFormat format, Surface surface, boolean is10Bit) {
    int width = format.getInteger(MediaFormat.KEY_WIDTH);
    int height = format.getInteger(MediaFormat.KEY_HEIGHT);
    ImageWriter.Builder builder = new ImageWriter.Builder(surface);
    builder.setWidthAndHeight(width, height)
        .setImageFormat(is10Bit ? ImageFormat.YCBCR_P010 : ImageFormat.YUV_420_888)
        .setMaxImages(MAX_PENDING_IMAGE_COUNT)
        .setUsage(HardwareBuffer.USAGE_CPU_WRITE_OFTEN);
    return builder.build();
  }

  // Unsupported for now.
  @Override
  public void setOnFinishedListener(OnFinishedListener listener) {
    assert (onFinishedListener == null);
    onFinishedListener = listener;
  }

  @Override
  public void start() {
    Log.v(TAG, "start");
    cannedDataSource.start();
  }

  @Override
  public void stop() {
    Log.v(TAG, "stop");
    if (metaDataSource != null && !metaDataSource.isEos()) {
      Log.v(TAG, "stop when metadata source is not EOS");
    }
    cannedDataSource.stop();
  }

  @Override
  public void release() {
    Log.v(TAG, "release");
    cannedDataSource.release();
    handlerThread.quitSafely();
    try {
      handlerThread.join();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    for (ImageWriter imageWriter : imageWriters) {
      if (imageWriter != null) {
        imageWriter.close();
      }
    }
  }

  // arg1 = trackIndex
  private static final int WHAT_OUTPUT_AVAILABLE = 0; // arg1 = trackIndex
  // arg2 = trackType (for metadata only), obj = Output
  private static final int WHAT_OUTPUT_RELEASED = 1;
  private static final int WHAT_IMAGE_RELEASED = 2; // arg2 = trackType (for video/depth only)
  private static final int WHAT_TRY_DELIVER_IMAGES = 4;
  private static final int WHAT_CHECK_ALL_EOS = 5;

  private class MainHandler extends Handler {
    public MainHandler(Looper looper) {
      super(looper);
    }

    @Override
    public void handleMessage(Message msg) {
      Log.v(TAG, "msg what: " + msg.what);
      switch (msg.what) {
        case WHAT_OUTPUT_AVAILABLE: {
          mainHandler.obtainMessage(WHAT_CHECK_ALL_EOS).sendToTarget();
          if (msg.arg1 == DepthFormat.TRACK_TYPE_METADATA) {
            assert (metaDataSource.outputListener != null);
            metaDataSource.outputListener.onOutputAvailable();
            break;
          }
        }
        // fall throught
        case WHAT_IMAGE_RELEASED:
        case WHAT_TRY_DELIVER_IMAGES: {
          deliverImages();
          break;
        }
        case WHAT_OUTPUT_RELEASED: {
          int trackType = msg.arg2;
          CannedDataSource.Output output = (CannedDataSource.Output) msg.obj;
          assert (trackType == DepthFormat.TRACK_TYPE_METADATA);
          assert (output != null);
          assert (output.trackIndex == METADATA_TRACK_INDEX);
          cannedDataSource.queueOutput(output);
          break;
        }
        case WHAT_CHECK_ALL_EOS:
          checkAllEos();
          break;
        default:
          throw new RuntimeException("Unknown msg: " + msg);
      }
    }
  }

  private void deliverImages() {
    long minTooEarlyMs = Long.MAX_VALUE;
    for (int trackIndex = 0; trackIndex < VIDEO_INPUT_TRACK_COUNT; ++trackIndex) {
      if (!VIDEO_TRACK_SELECTION[trackIndex]) {
        continue;
      }

      if (trackIndex == METADATA_TRACK_INDEX) {
        continue;
      }

      if (cannedDataSource.peekOutput(trackIndex) == null) {
        continue;
      }

      boolean trackRequired = false;
      boolean allSurfaceReady = true;
      for (int trackType = 0; trackType < MAX_TRACK_TYPES; ++trackType) {
        if (TRACK_TYPES_MAP[trackType] == trackIndex) {
          if (imageWriters[trackType] != null) {
            trackRequired = true;
            if (pendingBufferCountInSurfaces[trackType] >= MAX_PENDING_IMAGE_COUNT) {
              allSurfaceReady = false;
              break;
            }
          }
        }
      }

      if (!trackRequired) {
        if (DEBUG) {
          Log.v(TAG, "drop images for track " + trackIndex);
        }
        dropImages(trackIndex);
      } else if (allSurfaceReady) {
        CannedDataSource.Output output = cannedDataSource.peekOutput(trackIndex);
        if (output == null) {
          continue;
        }

        if (firstFrameTimeMs == -1) {
          firstFrameTimeMs = System.currentTimeMillis();
          Log.v(TAG, "AVSYNC firstFrameTimeMs " + firstFrameTimeMs);
        }

        if (DISABLE_AVSYNC) {
          minTooEarlyMs = 0;
        } else {
          long nowMs = System.currentTimeMillis();
          long earlyMs = output.bufferInfo.presentationTimeUs / 1000 + firstFrameTimeMs - nowMs;
          if (DEBUG) {
            Log.v(TAG, String.format("AVSYNC trackIndex[%d] bufferIndex[%d] "
                    + "ts[%d] early[%d] timeMs[%d]",
                output.trackIndex, output.bufferIndex,
                output.bufferInfo.presentationTimeUs / 1000, earlyMs, nowMs));
          }
          if (earlyMs > AVSYNC_THRESHOLD_MS) {
            if (earlyMs < minTooEarlyMs) {
              minTooEarlyMs = earlyMs;
            }
            if (DEBUG) {
              Log.v(TAG, "AVSYNC pending at least " + minTooEarlyMs + " ms");
            }
            continue;
          }
          minTooEarlyMs = 0;
        }
        if (DEBUG) {
          Log.v(TAG, "copy images for track " + trackIndex);
        }
        copyAnImage(trackIndex);
      } else {
        if (DEBUG) {
          Log.v(TAG, "pending on images for track " + trackIndex);
        }
      }
    }

    if (minTooEarlyMs != Long.MAX_VALUE) {
      mainHandler.obtainMessage(WHAT_TRY_DELIVER_IMAGES);
      mainHandler.sendEmptyMessageDelayed(WHAT_TRY_DELIVER_IMAGES, minTooEarlyMs);
    }
  }

  private void dropImages(int trackIndex) {
    while (true) {
      CannedDataSource.Output output = cannedDataSource.dequeueOutput(trackIndex);
      if (output == null) {
        return;
      }
      Log.v(TAG, "drop an image from track " + trackIndex);
      cannedDataSource.queueOutput(output);
    }
  }

  private void copyAnImage(int trackIndex) {
    CannedDataSource.Output output = cannedDataSource.dequeueOutput(trackIndex);
    if (output == null) {
      return;
    }
    for (int trackType = 0; trackType < MAX_TRACK_TYPES; ++trackType) {
      if (TRACK_TYPES_MAP[trackType] == trackIndex) {
        if (imageWriters[trackType] != null) {
          Log.v(TAG, String.format("Copy image from track[%d] to track type [%d], ts[%d]",
              trackIndex, trackType, output.bufferInfo.presentationTimeUs));
          copyImage(output, imageWriters[trackType]);
          pendingBufferCountInSurfaces[trackType]++;
        }
      }
    }
    cannedDataSource.queueOutput(output);
  }

  private static void copyImage(CannedDataSource.Output from, ImageWriter to) {
    Image fromImage = from.image;
    Image toImage = to.dequeueInputImage();
    assert (fromImage != null);
    assert (toImage != null);
    assert (fromImage.getFormat() == toImage.getFormat());
    Image.Plane[] fromPlanes = fromImage.getPlanes();
    Image.Plane[] toPlanes = toImage.getPlanes();
    assert (fromPlanes.length == toPlanes.length);
    for (int planeIndex = 0; planeIndex < fromPlanes.length; ++planeIndex) {
      Image.Plane fromPlane = fromPlanes[planeIndex];
      Image.Plane toPlane = toPlanes[planeIndex];
      assert (fromPlane.getPixelStride() == toPlane.getPixelStride());
      assert (fromPlane.getRowStride() == toPlane.getRowStride());
      ByteBuffer fromBuf = fromPlane.getBuffer();
      ByteBuffer toBuf = toPlane.getBuffer();
      // there may be slightly difference in from/to buffer sizes
      assert (fromBuf.capacity() * 1.0 / toBuf.capacity() > 0.99);
      assert (toBuf.capacity() * 1.0 / fromBuf.capacity() > 0.99);

      fromBuf.rewind();
      toBuf.rewind();
      toBuf.put(fromBuf);
    }
    toImage.setTimestamp(fromImage.getTimestamp());
    to.queueInputImage(toImage);
  }

  private void checkAllEos() {
    if (allEos) {
      return;
    }
    for (int trackIndex = 0; trackIndex < VIDEO_INPUT_TRACK_COUNT; ++trackIndex) {
      if (VIDEO_TRACK_SELECTION[trackIndex] && !cannedDataSource.isEos(trackIndex)) {
        return;
      }
    }
    allEos = true;
    if (onFinishedListener != null) {
      onFinishedListener.onFinished();
    }
  }

  private class MetaDataSource implements TrackDataSource {
    OutputListener outputListener;

    @Override
    public MediaFormat getFormat() {
      return cannedDataSource.getTrackFormat(METADATA_TRACK_INDEX);
    }

    @Override
    public void setOutputListener(OutputListener listener) {
      assert (outputListener == null);
      outputListener = listener;
    }

    @Override
    public TrackBuffer dequeueBuffer() {
      CannedDataSource.Output output = cannedDataSource.dequeueOutput(METADATA_TRACK_INDEX);
      return output == null ? null : new TrackBuffer(output.byteBuffer, output.bufferInfo, output);
    }

    @Override
    public void queueBuffer(TrackBuffer buffer) {
      CannedDataSource.Output output = (CannedDataSource.Output) buffer.internalObj;
      assert (output != null);
      assert (output.trackIndex == METADATA_TRACK_INDEX);
      mainHandler.obtainMessage(
          WHAT_OUTPUT_RELEASED,
          METADATA_TRACK_INDEX,
          DepthFormat.TRACK_TYPE_METADATA,
          output).sendToTarget();
    }

    @Override
    public boolean isEos() {
      return cannedDataSource.isEos(METADATA_TRACK_INDEX);
    }
  }
}
