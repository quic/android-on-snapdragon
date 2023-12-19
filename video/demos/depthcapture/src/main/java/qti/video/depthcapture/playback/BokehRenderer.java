/*
 **************************************************************************************************
 * Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 **************************************************************************************************
 */

package qti.video.depthcapture.playback;

import android.content.Context;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.Pair;
import androidx.annotation.NonNull;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

class BokehRenderer implements GLSurfaceView.Renderer {
  static final String TAG = "BokehRenderer";
  static final boolean DEBUG = true;

  private final BokehEffect bokehEffect;

  private final Context context;
  private final GLSurfaceView view;
  private final DepthVideoSource tracksSource;
  private int trackCount = -1;
  private int videoTrackIndex = -1;
  private int depthTrackIndex = -1;
  private final AtomicReference<Pair<DepthVideoSource.Output, DepthVideoSource.Output>>
      videoDepthPair = new AtomicReference<>();
  private long renderStartTimeMs = -1;

  enum State {
    Initial,
    Initialized,
    Started,
    Stopped,
  }

  private State state = State.Initial;
  private final HandlerThread handlerThread =  new HandlerThread("Bokeh");
  private final Handler mainHandler;

  public BokehRenderer(Context context, GLSurfaceView view, String clip) {
    this.context = context;
    bokehEffect = new BokehEffect(context);
    this.view = view;

    handlerThread.start();
    mainHandler = new MainHandler(handlerThread.getLooper());

    tracksSource = new DepthVideoSource(clip, trackIndex -> {
      mainHandler.obtainMessage(WHAT_CHECK_OUTPUT, trackIndex).sendToTarget();
    });
  }

  public void setBokehThreshold(float threshold) {
    bokehEffect.setBokehThreshold(threshold);
  }

  public void init() {
    Log.v(TAG, "init");
    assert (state == State.Initial);
    mainHandler.obtainMessage(WHAT_INIT).sendToTarget();
    waitForState(State.Initialized);
  }

  public void start() {
    Log.v(TAG, "start");
    assert (state == State.Initialized);
    mainHandler.obtainMessage(WHAT_START).sendToTarget();
    waitForState(State.Started);
  }

  public void stop() {
    Log.v(TAG, "stop");
    assert (state == State.Started);
    mainHandler.obtainMessage(WHAT_STOP).sendToTarget();
    waitForState(State.Stopped);
  }

  public void release() {
    Log.v(TAG, "release");
    assert (state == State.Stopped);
    mainHandler.obtainMessage(WHAT_RELEASE).sendToTarget();
    waitForState(State.Initial);
    handlerThread.quitSafely();
  }

  @Override
  public void onSurfaceCreated(GL10 gl, EGLConfig eglConfig) {
    Log.v(TAG, "onSurfaceCreated");

    bokehEffect.init();
  }

  @Override
  public void onSurfaceChanged(GL10 gl10, int width, int height) {
    Log.v(TAG, "onSurfaceChanged w/h: " + width + "/" + height);
    GLES30.glViewport(0, 0, width, height);
  }

  @Override
  public void onDrawFrame(GL10 gl10) {
    if (DEBUG) {
      Log.v(TAG, "onDrawFrame");
    }

    Pair<DepthVideoSource.Output, DepthVideoSource.Output> vdPair
        = videoDepthPair.getAndSet(null);
    if (vdPair == null) {
      Log.e(TAG, "video/depth frames are null when rendering");
      return;
    }
    mainHandler.obtainMessage(WHAT_CHECK_OUTPUT).sendToTarget();

    long t0;
    if (DEBUG) {
      t0 = System.nanoTime();
    }

    bokehEffect.draw(vdPair.first.image.getHardwareBuffer(),
        vdPair.second.image.getHardwareBuffer());

    if (DEBUG) {
      long t1 = System.nanoTime();
      Log.v(TAG, "draw latency: " + (t1 - t0));
    }

    mainHandler.obtainMessage(WHAT_RELEASE_OUTPUT, vdPair).sendToTarget();
  }

  static final int WHAT_INIT = 0;
  static final int WHAT_START = 1;
  static final int WHAT_STOP = 2;
  static final int WHAT_RELEASE = 3;

  static final int WHAT_CHECK_OUTPUT = 10;
  static final int WHAT_RENDER = 11;
  static final int WHAT_RELEASE_OUTPUT = 13;

  private class MainHandler extends Handler {
    public MainHandler(Looper looper) {
      super(looper);
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
      if (DEBUG) {
        Log.v(TAG, "msg what: " + msg.what);
      }
      switch (msg.what) {
        case WHAT_INIT:
          tracksSource.init();
          trackCount = tracksSource.getTrackCount();
          // TODO: check track format instead of hardcode
          videoTrackIndex = 0;
          depthTrackIndex = 1;
          // TODO: make complete state machine and state check
          setState(State.Initialized);
          break;
        case WHAT_START:
          tracksSource.start();
          setState(State.Started);
          break;
        case WHAT_STOP:
          tracksSource.stop();
          setState(State.Stopped);
          break;
        case WHAT_RELEASE:
          tracksSource.release();
          setState(State.Initial);
          break;
        case WHAT_CHECK_OUTPUT:
          checkOutputs();
          break;
        case WHAT_RENDER:
          render();
          break;
        case WHAT_RELEASE_OUTPUT:
          Pair<DepthVideoSource.Output, DepthVideoSource.Output> vd =
              (Pair<DepthVideoSource.Output, DepthVideoSource.Output>) msg.obj;
          assert (vd != null);
          tracksSource.queueOutput(vd.first);
          tracksSource.queueOutput(vd.second);
          break;
        default:
          Log.e(TAG, "Unknown msg what = " + msg.what);
          assert (false);
      }
    }
  }

  private void checkOutputs() {
    if (videoDepthPair.get() != null) {
      return;
    }
    // consume all non video/depth outputs
    for (int track = 0; track < trackCount; ++track) {
      if (track != videoTrackIndex && track != depthTrackIndex) {
        while (true) {
          DepthVideoSource.Output output = tracksSource.dequeueOutput(track);
          if (output == null) {
            break;
          }
          tracksSource.queueOutput(output);
        }
      }
    }
    DepthVideoSource.Output videoFrame = tracksSource.peekOutput(videoTrackIndex);
    if (videoFrame == null) {
      return;
    }
    long videoPtsMs = videoFrame.bufferInfo.presentationTimeUs / 1000;
    DepthVideoSource.Output depthFrame = tracksSource.peekOutput(depthTrackIndex);
    if (depthFrame == null) {
      return;
    }
    long depthPtsMs = depthFrame.bufferInfo.presentationTimeUs / 1000;
    long nowMs = System.currentTimeMillis();
    if (renderStartTimeMs == -1) {
      renderStartTimeMs = nowMs - Math.min(videoPtsMs, depthPtsMs);
    }
    Log.v(TAG, "video pts ms: " + videoPtsMs + ", depth pts ms: " + depthPtsMs);
    if (videoPtsMs > depthPtsMs) {
      depthFrame = tracksSource.dequeueOutput(depthTrackIndex);
      tracksSource.queueOutput(depthFrame);
      depthFrame = tracksSource.peekOutput(depthTrackIndex);
      if (depthFrame == null) {
        Log.v(TAG, "no more depth frame to catch video pts");
        return;
      }
      depthPtsMs = depthFrame.bufferInfo.presentationTimeUs / 1000;
    }
    if (videoPtsMs < depthPtsMs) {
      videoFrame = tracksSource.dequeueOutput(videoTrackIndex);
      tracksSource.queueOutput(videoFrame);
      videoFrame = tracksSource.peekOutput(videoTrackIndex);
      if (videoFrame == null) {
        Log.v(TAG, "no more video frame to catch depth pts");
        return;
      }
      videoPtsMs = videoFrame.bufferInfo.presentationTimeUs / 1000;
    }
    if (videoPtsMs != depthPtsMs) {
      Log.e(TAG, "different video/depth pts " + videoPtsMs + "/" + depthPtsMs);
      assert (false);
      return;
    }

    videoFrame = tracksSource.dequeueOutput(videoTrackIndex);
    depthFrame = tracksSource.dequeueOutput(depthTrackIndex);
    videoDepthPair.set(new Pair<>(videoFrame, depthFrame));
    nowMs = System.currentTimeMillis();
    long renderDelayMs = videoPtsMs - (nowMs - renderStartTimeMs);
    renderDelayMs = renderDelayMs < 0 ? 0 : renderDelayMs;
    mainHandler.sendEmptyMessageDelayed(WHAT_RENDER, renderDelayMs);
    Log.v(TAG, "pts " + videoPtsMs + " is scheduled to render after " + renderDelayMs + "ms");
  }

  private void render() {
    if (videoDepthPair.get() == null) {
      Log.e(TAG, "video and depth frames are null");
      assert (false);
      return;
    }
    view.requestRender();
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

}
