/*
 **************************************************************************************************
 * Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 **************************************************************************************************
 */

package qti.video.depthcapture.playback;

import android.content.Context;
import android.hardware.HardwareBuffer;
import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import androidx.graphics.opengl.egl.EGLSpec;
import androidx.opengl.EGLExt;
import androidx.opengl.EGLImageKHR;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import qti.video.depthcapture.R;

class BokehEffect {

  static String TAG = "BokehEffect";

  private static final int FLOAT_SIZE_BYTES = 4;
  private static final float[] mFullscreenVerticesData = {
      -1.0f, -1.0f,
      1.0f, -1.0f,
      -1.0f, 1.0f,
      1.0f, 1.0f,
  };

  private final Context context;
  private final FloatBuffer fullscreenVertices;
  private int program;
  private int positionHandle;
  private int uniformVideoTextureLoc;
  private int uniformDepthTextureLoc;
  private int uniformDepthThresholdLoc;
  private float bokehThreshold = 0.3f;

  private EGLImageKHR videoEglImage;
  private EGLImageKHR depthEglImage;

  public BokehEffect(Context context) {
    this.context = context;
    fullscreenVertices = ByteBuffer.allocateDirect(mFullscreenVerticesData.length
        * FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
    fullscreenVertices.put(mFullscreenVerticesData).position(0);
  }

  public void init() {
    // Ignore the passed-in GL10 interface, and use the GLES30
    // class's static methods instead.
    program = GlUtils.createProgramFromResRaw(context, R.raw.bokeh_vert, R.raw.bokeh_frag);
    assert (program > 0);
    positionHandle = GLES30.glGetAttribLocation(program, "aPosition");
    GlUtils.checkGlError("glGetAttribLocation aPosition");
    if (positionHandle == -1) {
      throw new RuntimeException("Could not get attrib location for aPosition");
    }

    uniformVideoTextureLoc = GLES30.glGetUniformLocation(program, "sVideoTexture");
    GlUtils.checkGlError("glGetUniformLocation sVideoTexture");
    assert (uniformVideoTextureLoc >= 0);

    uniformDepthTextureLoc = GLES30.glGetUniformLocation(program, "sDepthTexture");
    GlUtils.checkGlError("glGetUniformLocation sDepthTexture");
    assert (uniformDepthTextureLoc > 0);

    uniformDepthThresholdLoc = GLES30.glGetUniformLocation(program, "uDepthThreshold");
    GlUtils.checkGlError("glGetUniformLocation uDepthThreshold");
    assert (uniformDepthTextureLoc > 0);

    GLES30.glUseProgram(program);
    GlUtils.checkGlError("glUseProgram");
  }

  public void release() {
    // TODO
    GLES30.glUseProgram(0);
  }

  public void setBokehThreshold(float threshold) {
    bokehThreshold = threshold;
  }

  public void draw(HardwareBuffer videoFrame, HardwareBuffer depthFrame) {
    int[] textures = createLoadTextures(videoFrame, depthFrame);
    GLES30.glUniform1f(uniformDepthThresholdLoc, bokehThreshold);

    fullscreenVertices.position(0);
    GLES30.glVertexAttribPointer(positionHandle, 2, GLES30.GL_FLOAT, false,
        0, fullscreenVertices);
    GlUtils.checkGlError("glVertexAttribPointer maPosition");
    GLES30.glEnableVertexAttribArray(positionHandle);
    GlUtils.checkGlError("glEnableVertexAttribArray maPositionHandle");

    GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
    GlUtils.checkGlError("glDrawArrays");

    deleteTextures(textures);
  }

  // the returned textures ids must be deleted after drawing
  private int[] createLoadTextures(HardwareBuffer videoFrame, HardwareBuffer depthFrame) {
    // looks like we need to create/bind textures for every new frames

    int[] textures = new int[2];
    GLES30.glGenTextures(2, textures, 0);

    // video frame
    GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
    GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0]);
    GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST);
    GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST);
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT);
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT);
    GLES30.glUniform1i(uniformVideoTextureLoc, 0);

    if (videoEglImage != null) {
      EGLSpec.V14.eglDestroyImageKHR(videoEglImage);
    }
    videoEglImage = EGLSpec.V14.eglCreateImageFromHardwareBuffer(videoFrame);
    assert (videoEglImage != null);
    EGLExt.glEGLImageTargetTexture2DOES(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoEglImage);


    // depth frame
    GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
    GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[1]);
    GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST);
    GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST);
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT);
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT);
    GLES30.glUniform1i(uniformDepthTextureLoc, 1);

    if (depthEglImage != null) {
      EGLSpec.V14.eglDestroyImageKHR(depthEglImage);
    }
    depthEglImage = EGLSpec.V14.eglCreateImageFromHardwareBuffer(depthFrame);
    assert (depthEglImage != null);
    EGLExt.glEGLImageTargetTexture2DOES(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, depthEglImage);

    return textures;
  }

  private void deleteTextures(int[] textures) {
    GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
    GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);

    GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
    GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);

    GLES30.glDeleteTextures(textures.length, textures, 0);
  }
}
