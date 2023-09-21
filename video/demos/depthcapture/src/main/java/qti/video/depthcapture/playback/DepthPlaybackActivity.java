/*
 **************************************************************************************************
 * Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 **************************************************************************************************
 */

package qti.video.depthcapture.playback;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import qti.video.depthcapture.AppConfig;
import qti.video.depthcapture.R;

public class DepthPlaybackActivity extends Activity {
  static final String TAG = "DepthPlaybackActivity ";

  private TextView textView;
  private float bokehThreshold;
  private GLSurfaceView glSurfaceViewVideo;
  private BokehRenderer bokehRenderer;
  private GLSurfaceView.Renderer renderer;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_playback);

    String inputClip = getIntent().getData().getPath();
    Log.v(TAG, "bokeh input clip: " + inputClip);

    textView = findViewById(R.id.text_output);
    SeekBar seekBar = findViewById(R.id.seekBar);
    bokehThreshold = seekBar.getProgress() / 100.0f;
    printlnText("bokeh threshold " + bokehThreshold);
    seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }

      @Override
      public void onStartTrackingTouch(SeekBar seekBar) { }

      @Override
      public void onStopTrackingTouch(SeekBar seekBar) {
        bokehThreshold = seekBar.getProgress() / 100.0f;
        bokehRenderer.setBokehThreshold(bokehThreshold);
        printlnText("bokeh threshold " + bokehThreshold);
      }
    });

    glSurfaceViewVideo = findViewById(R.id.glSurfaceViewVideo);
    glSurfaceViewVideo.setEGLContextClientVersion(3);

    glSurfaceViewVideo.getHolder().setFixedSize(AppConfig.VIDEO_WIDTH, AppConfig.VIDEO_HEIGHT);
    glSurfaceViewVideo.setDebugFlags(GLSurfaceView.DEBUG_CHECK_GL_ERROR);

    bokehRenderer = new BokehRenderer(this, glSurfaceViewVideo, inputClip);
    bokehRenderer.setBokehThreshold(bokehThreshold);
    glSurfaceViewVideo.setRenderer(bokehRenderer);
    glSurfaceViewVideo.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

  }

  @Override
  protected void onResume() {
    super.onResume();
    glSurfaceViewVideo.onResume();
  }

  @Override
  protected void onPause() {
    super.onPause();
    glSurfaceViewVideo.onPause();
  }

  public void onClickStartPlay(View v) {
    Log.v(TAG, "onClickStartPlay");

    v.setEnabled(false);
    bokehRenderer.init();
    bokehRenderer.start();
  }

  public void onClickStopPlay(View v) {
    Log.v(TAG, "onClickStopPlay");
    bokehRenderer.stop();
    bokehRenderer.release();
  }

  private void printText(String s) {
    Log.v(TAG, s);
    textView.setText(textView.getText() + s);
  }

  private void printlnText(String s) {
    printText(s + "\n");
  }
}
