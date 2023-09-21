/*
 **************************************************************************************************
 * Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 **************************************************************************************************
 */

package qti.video.depthcapture;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Toast;
import java.io.File;
import qti.video.depth.DepthFormat;
import qti.video.depthcapture.playback.DepthPlaybackActivity;

/*
Input file: /storage/emulated/0/Android/data/qti.video.depthcapture/files/IMG_0400_remuxed.mp4
adb push \\hw-lubiny-lv\Public\depth\IMG_0400_remuxed.mp4 \
  /storage/emulated/0/Android/data/qti.video.depthcapture/files/
Output file: /storage/emulated/0/Android/data/qti.video.depthcapture/files/depth_capture_output.mp4

Start the activity in auto test mode:
adb shell am start -n "qti.video.depthcapture/qti.video.depthcapture.DepthCaptureActivity" \
  -a android.intent.action.MAIN -c android.intent.category.LAUNCHER --ez AutoTestMode true
 */
public class DepthCaptureActivity extends Activity {
  private static final String KEY_AUTO_TEST_MODE = "AutoTestMode";
  static final String TAG = "DepthCaptureActivity";

  private static final String OUTPUT_CLIP = AppConfig.CAPTURE_OUTPUT_FILE_PATH;

  private boolean autoTestMode;
  private SurfaceView surfaceViewVideo;
  private SurfaceView surfaceViewDepth;
  private final HandlerThread handlerThread = new HandlerThread("DepthCaptureActivity");
  private MainHandler mainHandler;
  private DepthRecorder depthRecorder;
  private MockCameraSource mockCameraSource;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    handlerThread.start();
    mainHandler = new MainHandler(handlerThread.getLooper());
    Log.e(TAG, "external path: " + this.getExternalFilesDir(""));

    autoTestMode = getIntent().getBooleanExtra(KEY_AUTO_TEST_MODE, false);
    Log.v(TAG, "hasExtra: " + getIntent().hasExtra(KEY_AUTO_TEST_MODE)
        + ", AutoTestMode: " + autoTestMode);
    if (autoTestMode) {
      mainHandler.sendEmptyMessageDelayed(WHAT_START_CAPTURE, 100);
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    surfaceViewVideo = findViewById(R.id.surfaceViewVideo);
    surfaceViewDepth = findViewById(R.id.surfaceViewDepth);

    surfaceViewVideo.getHolder().setFixedSize(AppConfig.VIDEO_WIDTH, AppConfig.VIDEO_HEIGHT);
  }

  @Override
  protected void onDestroy() {
    handlerThread.quitSafely();
    super.onDestroy();
  }

  public void onClickStartCapture(View v) {
    mainHandler.obtainMessage(WHAT_START_CAPTURE).sendToTarget();
  }

  public void onClickStopCapture(View v) {
    mainHandler.obtainMessage(WHAT_STOP_CAPTURE).sendToTarget();
  }

  public void onClickStartPlaybackActivity(View v) {
    Intent intent = new Intent(this, DepthPlaybackActivity.class);
    intent.setData(Uri.fromFile(new File(OUTPUT_CLIP)));
    startActivity(intent);
  }

  private void showCameraFinished() {
    Toast.makeText(this, "Camera finished. Please stop capture.", Toast.LENGTH_LONG).show();
  }

  private static final int WHAT_START_CAPTURE = 0;
  private static final int WHAT_STOP_CAPTURE = 1;

  private class MainHandler extends Handler {
    public MainHandler(Looper looper) {
      super(looper);
    }

    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
        case WHAT_START_CAPTURE:
          onStartCapture();
          break;
        case WHAT_STOP_CAPTURE:
          onStopCapture();
          break;
        default:
          assert (false);
      }
    }
  }

  private void onStartCapture() {
    assert (mockCameraSource == null);
    assert (depthRecorder == null);

    final boolean kPreviewTranslucentVideo = true;
    final boolean kPreviewDepth = true;
    final int kDepthType = DepthFormat.TRACK_TYPE_DEPTH_LINEAR;
    final int[] kEnabledTrackTypes = new int[] {
        DepthFormat.TRACK_TYPE_TRANSLUCENT_VIDEO,
        // DepthFormat.TRACK_TYPE_SHARP_VIDEO,
        kDepthType,
    };
    final int[] kEncoders = new int[] {
        DepthRecorder.HEVC,
        // DepthRecorder.HEVC,
        DepthRecorder.DEPTH_ENCODER_HEVC_10BIT,
    };

    mockCameraSource = new MockCameraSource();
    if (autoTestMode) {
      mockCameraSource.setOnFinishedListener(() -> {
        Log.w(TAG, "AutoTestMode - MockCameraSource finished");
        runOnUiThread(this::showCameraFinished);
        runOnUiThread(this::finish);
      });
    } else {
      mockCameraSource.setOnFinishedListener(() -> {
        Log.w(TAG, "MockCameraSource finished");
        DepthCaptureActivity.this.runOnUiThread(this::showCameraFinished);
      });
    }
    mockCameraSource.init();
    depthRecorder = new DepthRecorder(this);
    depthRecorder.setMetadataSource(mockCameraSource.getMetadataDataSource());
    for (int trackType : kEnabledTrackTypes) {
      depthRecorder.setTrackSource(trackType, DepthRecorder.TRACK_SOURCE_SURFACE);
    }

    depthRecorder.setOutputFormat(DepthRecorder.OUTPUT_FORMAT_DEPTH_MPEG_4);

    if (kPreviewTranslucentVideo) {
      depthRecorder.setPreviewDisplay(DepthFormat.TRACK_TYPE_TRANSLUCENT_VIDEO,
          surfaceViewVideo.getHolder().getSurface());
    }

    if (kPreviewDepth) {
      depthRecorder.setPreviewDisplay(kDepthType,
          surfaceViewDepth.getHolder().getSurface());
    }


    for (int index = 0; index < kEnabledTrackTypes.length; ++index) {
      int trackType = kEnabledTrackTypes[index];
      int encoder = kEncoders[index];
      depthRecorder.setTrackEncoder(trackType, encoder);

      MediaFormat format = mockCameraSource.getTrackFormat(trackType);
      int width = format.getInteger(MediaFormat.KEY_WIDTH);
      int height = format.getInteger(MediaFormat.KEY_HEIGHT);
      depthRecorder.setTrackSize(trackType, width, height);
    }

    depthRecorder.setVideoFrameRate(MockCameraSource.VIDEO_FRAME_RATE);
    depthRecorder.setOutputFile(OUTPUT_CLIP);
    depthRecorder.prepare();

    for (int trackType : kEnabledTrackTypes) {
      mockCameraSource.setOutputSurface(trackType,
          depthRecorder.getTrackSurface(trackType));
    }

    depthRecorder.start();
    mockCameraSource.start();
  }

  private void onStopCapture() {
    mockCameraSource.stop();
    depthRecorder.stop();
    mockCameraSource.release();
    depthRecorder.release();
    mockCameraSource = null;
    depthRecorder = null;
  }
}