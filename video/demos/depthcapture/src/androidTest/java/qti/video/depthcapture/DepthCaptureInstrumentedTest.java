/*
 **************************************************************************************************
 * Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 **************************************************************************************************
 */

package qti.video.depthcapture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.ImageFormat;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.util.Arrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import qti.video.depth.DepthFormat;
import qti.video.depthcapture.playback.DepthVideoSource;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class DepthCaptureInstrumentedTest {

  static final String TAG = "DepthTest";

  private HandlerThread handlerThread;
  private Handler mainHandler;
  @Before
  public void beforeTestCase() {
    handlerThread = new HandlerThread("DepthUnitTest");
    handlerThread.start();
    mainHandler = new Handler(handlerThread.getLooper());
  }

  @After
  public void afterTestCase() {
    mainHandler = null;
    handlerThread.quitSafely();
    handlerThread = null;
  }

  @Test
  public void useAppContext() {
    // Context of the app under test.
    Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    assertEquals("qti.video.depthcapture", appContext.getPackageName());
  }

  CannedDataSource cannedDataSource;

  @Test
  public void testCannedDataSource() {
    final String inputClip = AppConfig.MOCK_CAMERA_INPUT_CLIP;
    final int kMetadataTrackIndex = 3;
    final int kOutputCount = CannedDataSource.MAX_FRAME_COUNT;

    final boolean[] trackSelection = new boolean[] {true, false, true, true};
    final int[] outputCounts = new int[4];
    Runnable readInputs = () -> {
      for (int trackIndex = 0; trackIndex < trackSelection.length; ++trackIndex) {
        if (trackSelection[trackIndex]) {
          while (true) {
            CannedDataSource.Output output = cannedDataSource.dequeueOutput(trackIndex);
            if (output == null) {
              break;
            }
            outputCounts[trackIndex]++;
            assertTrue(outputCounts[trackIndex] <= kOutputCount);
            if (outputCounts[trackIndex] == kOutputCount) {
              assertNotEquals(0,
              output.bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM);
              assertTrue(cannedDataSource.isEos(trackIndex));
            } else {
              assertEquals(0,
              output.bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM);
              assertFalse(cannedDataSource.isEos(trackIndex));
            }
            assertEquals(trackIndex, output.trackIndex);
            assertNotNull(output.bufferInfo);
            if (trackIndex == kMetadataTrackIndex) {
              assertEquals(-1, output.bufferIndex);
              assertNull(output.image);
              assertNotNull(output.byteBuffer);
            } else {
              assertNotEquals(-1, output.bufferIndex);
              assertNotNull(output.image);
              assertNull(output.byteBuffer);
              if (cannedDataSource.is10Bit(output.trackIndex)) {
                assertEquals(ImageFormat.YCBCR_P010, output.image.getFormat());
              } else {
                assertEquals(ImageFormat.YUV_420_888, output.image.getFormat());
              }
            }
            cannedDataSource.queueOutput(output);
          }
        }
      }
    };
    CannedDataSource.OutputsListener listener = new CannedDataSource.OutputsListener() {
      @Override
      public void onOutputAvailable(int trackIndex) {
        Log.v(TAG, "onOutputAvailable " + trackIndex);
        assertTrue(trackSelection[trackIndex]);
        mainHandler.post(readInputs);
      }
    };

    assertNull(cannedDataSource);
    cannedDataSource = new CannedDataSource(inputClip, trackSelection, listener);
    cannedDataSource.init();

    {
      assertFalse(cannedDataSource.is10Bit(0));
      MediaFormat format = cannedDataSource.getTrackFormat(0);
      assertNotNull(format);
      int width = format.getInteger(MediaFormat.KEY_WIDTH);
      int height = format.getInteger(MediaFormat.KEY_HEIGHT);
      assertEquals(width, 3840);
      assertEquals(height, 2160);
    }

    {
      assertTrue(cannedDataSource.is10Bit(2));
      MediaFormat format = cannedDataSource.getTrackFormat(2);
      assertNotNull(format);
      int width = format.getInteger(MediaFormat.KEY_WIDTH);
      int height = format.getInteger(MediaFormat.KEY_HEIGHT);
      assertEquals(width, 512);
      assertEquals(height, 288);
    }

    {
      MediaFormat format = cannedDataSource.getTrackFormat(3);
      assertNotNull(format);
      String mime = format.getString(MediaFormat.KEY_MIME);
      assertTrue(mime.startsWith("application/"));
    }

    cannedDataSource.start();

    try {
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    Log.w(TAG, "output counts: " + Arrays.toString(outputCounts));

    for (int trackIndex = 0; trackIndex < trackSelection.length; ++trackIndex) {
      if (trackSelection[trackIndex]) {
        assertTrue(cannedDataSource.isEos(trackIndex));
      }
    }

    cannedDataSource.stop();
    cannedDataSource.release();
    cannedDataSource = null;

    Log.w(TAG, "final output counts: " + Arrays.toString(outputCounts));
    for (int trackIndex = 0; trackIndex < trackSelection.length; ++trackIndex) {
      if (trackSelection[trackIndex]) {
        assertEquals(kOutputCount, outputCounts[trackIndex]);
      } else {
        assertEquals(outputCounts[trackIndex], 0);
      }
    }
  }

  private DepthVideoSource depthVideoSource;
  @Test
  public void testDepthVideoSource() {
    final String inputClip = AppConfig.DEPTH_CLIP_SAMPLE;
    assertTrue(new File(inputClip).exists());
    final int kMetadataTrackIndex = 2;
    final int kOutputCount = DepthVideoSource.MAX_FRAME_COUNT;

    final int trackCount = 3;
    final int[] outputCounts = new int[3];
    Runnable readInputs = () -> {
      for (int trackIndex = 0; trackIndex < trackCount; ++trackIndex) {
        while (true) {
          DepthVideoSource.Output output = depthVideoSource.dequeueOutput(trackIndex);
          if (output == null) {
            break;
          }
          outputCounts[trackIndex]++;
          assertTrue(outputCounts[trackIndex] <= kOutputCount);
          if (outputCounts[trackIndex] == kOutputCount) {
            assertNotEquals(0,
                output.bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            assertTrue(depthVideoSource.isEos(trackIndex));
          } else {
            assertEquals(0,
                output.bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            assertFalse(depthVideoSource.isEos(trackIndex));
          }
          assertEquals(trackIndex, output.trackIndex);
          assertNotNull(output.bufferInfo);
          assertEquals(-1, output.bufferIndex);
          if (trackIndex == kMetadataTrackIndex) {
            assertNull(output.image);
            assertNotNull(output.byteBuffer);
          } else {
            assertNotNull(output.image);
            assertNotNull(output.image.getHardwareBuffer());
            assertNull(output.byteBuffer);
            if (depthVideoSource.is10Bit(output.trackIndex)) {
              assertEquals(ImageFormat.YCBCR_P010, output.image.getFormat());
            } else {
              assertEquals(ImageFormat.YUV_420_888, output.image.getFormat());
            }
          }
          depthVideoSource.queueOutput(output);
        }
      }
    };
    DepthVideoSource.OutputsListener listener = trackIndex -> {
      Log.v(TAG, "onOutputAvailable " + trackIndex);
      mainHandler.post(readInputs);
    };

    assertNull(depthVideoSource);
    depthVideoSource = new DepthVideoSource(inputClip, listener);
    depthVideoSource.init();
    assertEquals(trackCount, depthVideoSource.getTrackCount());

    {
      assertFalse(depthVideoSource.is10Bit(0));
      MediaFormat format = depthVideoSource.getTrackFormat(0);
      assertNotNull(format);
      assertFalse(format.containsKey(DepthFormat.KEY_TRACK_TYPE));
      int width = format.getInteger(MediaFormat.KEY_WIDTH);
      int height = format.getInteger(MediaFormat.KEY_HEIGHT);
      assertEquals(width, 3840);
      assertEquals(height, 2160);
    }

    {
      assertTrue(depthVideoSource.is10Bit(1));
      MediaFormat format = depthVideoSource.getTrackFormat(1);
      assertNotNull(format);
      assertTrue(format.containsKey(DepthFormat.KEY_TRACK_TYPE));
      assertEquals(DepthFormat.TRACK_TYPE_DEPTH_LINEAR,
          format.getInteger(DepthFormat.KEY_TRACK_TYPE));
      int width = format.getInteger(MediaFormat.KEY_WIDTH);
      int height = format.getInteger(MediaFormat.KEY_HEIGHT);
      assertEquals(width, 512);
      assertEquals(height, 288);
    }

    {
      MediaFormat format = depthVideoSource.getTrackFormat(2);
      assertNotNull(format);
      assertTrue(format.containsKey(DepthFormat.KEY_TRACK_TYPE));
      assertEquals(DepthFormat.TRACK_TYPE_METADATA,
          format.getInteger(DepthFormat.KEY_TRACK_TYPE));
      String mime = format.getString(MediaFormat.KEY_MIME);
      assertTrue(mime.startsWith("application/"));
    }

    depthVideoSource.start();
    try {
      Thread.sleep(5000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    Log.w(TAG, "depth video output counts: " + Arrays.toString(outputCounts));

    for (int trackIndex = 0; trackIndex < trackCount; ++trackIndex) {
      assertTrue(depthVideoSource.isEos(trackIndex));
    }

    depthVideoSource.stop();
    depthVideoSource.release();
    depthVideoSource = null;

    Log.w(TAG, "final output counts: " + Arrays.toString(outputCounts));
    for (int trackIndex = 0; trackIndex < trackCount; ++trackIndex) {
      assertEquals(kOutputCount, outputCounts[trackIndex]);
    }
  }
}
