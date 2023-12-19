/*
 * Copyright (C) 2013, 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.VideoSampleApp;

import static android.opengl.EGL14.EGL_NONE;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class GFXSurface implements SurfaceTexture.OnFrameAvailableListener {
  private static EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
  private static EGLSurface mEGLSurface = EGL14.EGL_NO_SURFACE;
  private final String TAG = "QC_MediaCode_GFXSurface";
  private final EGLConfig[] mConfigs = new EGLConfig[1];
  Surface mgSurface;
  boolean mFrameAvailable = false;
  TextureRender mTextureRender;
  private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
  private SurfaceTexture mSurfaceTexture;

  /**
   * Creates an GFXSurface from a Surface.
   */
  public GFXSurface(Surface surface) throws Exception {
    if (surface == null) {
      throw new Exception();
    }
    mgSurface = surface;
    eglSetup();
  }

  public GFXSurface() throws Exception {
    mTextureRender = new TextureRender();
    mTextureRender.surfaceCreated();

    mSurfaceTexture = new SurfaceTexture(mTextureRender.getTextureId());

    mSurfaceTexture.setOnFrameAvailableListener(this);
    mgSurface = new Surface(mSurfaceTexture);
    Log.d(TAG, "Surface create : " + mgSurface.isValid());
  }

  /**
   * Calls eglSwapBuffers.  Use this to "publish" the current frame.
   */
  public static boolean swapBuffers() {
    return EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface);
  }

  /**
   * Sends the presentation time stamp to EGL.  Time is expressed in nanoseconds.
   */
  public static void setPresentationTime(long nsecs) {
    EGLExt.eglPresentationTimeANDROID(mEGLDisplay, mEGLSurface, nsecs);
  }

  @Override
  public void onFrameAvailable(SurfaceTexture surfaceTexture) {
    mFrameAvailable = true;
  }

  public SurfaceTexture awaitNewImage() throws Exception {
    synchronized (mSurfaceTexture) {
      mSurfaceTexture.wait(100);
    }
    if (!mFrameAvailable) {
      throw new Exception("Surface frame wait timed out");
    }
    mSurfaceTexture.updateTexImage(); // not needed if one img
    Log.d(TAG, "mSurfaceTexture.updateTexImage  ");
    mFrameAvailable = false;
    return mSurfaceTexture;
  }

  public void drawImage() throws Exception {
    mTextureRender.drawFrame(mSurfaceTexture);
  }

  /**
   * Prepares EGL.  We want a GLES 2.0 context and a surface that supports recording.
   */
  private void eglSetup() throws Exception {
    mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
    if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
      throw new Exception("unable to get EGL14 display");
    }
    int[] version = new int[2];
    if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
      mEGLDisplay = null;
      throw new Exception("unable to initialize EGL14");
    }
    Log.i(TAG, "  eglInitialize 10 bit");
    int eglColorSize = 10;
    int eglAlphaSize = 2;
    int[] attribList = {
        EGL14.EGL_RED_SIZE, eglColorSize,
        EGL14.EGL_GREEN_SIZE, eglColorSize,
        EGL14.EGL_BLUE_SIZE, eglColorSize,
        EGL14.EGL_ALPHA_SIZE, eglAlphaSize,
        EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
        EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
        EGL14.EGL_NONE
    };
    int[] numConfigs = new int[1];
    if (!EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, mConfigs, 0, mConfigs.length,
        numConfigs, 0)) {
      throw new Exception("unable to find RGB888+recordable ES2 EGL config");
    }
    Log.i(TAG, "  eglChooseConfig");
    int[] attrib_list = {
        EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, //YuvSampling ? 3 : 2
        EGL_NONE
    };
    mEGLContext = EGL14.eglCreateContext(mEGLDisplay, mConfigs[0], EGL14.EGL_NO_CONTEXT,
        attrib_list, 0);
    checkEglError("eglCreateContext");
    if (mEGLContext == null) {
      throw new Exception("null context");
    }
    createEGLSurface();
    int mWidth = getWidth();
    int mHeight = getHeight();
    Log.i(TAG, "  createEGLSurface " + mWidth + "*" + mHeight);
  }

  private void createEGLSurface() throws Exception {
    int[] surfaceAttribs = {
        EGL_NONE
    };
    mEGLSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, mConfigs[0], mgSurface, surfaceAttribs, 0);
    Log.i(TAG, "  eglCreateWindowSurface");
    checkEglError("eglCreateWindowSurface");
    if (mEGLSurface == null) {
      throw new Exception("surface was null");
    }
  }

  /**
   * Discard all resources held by this class, notably the EGL context.  Also releases the
   * Surface that was passed to our constructor.
   */
  public void release() {
    if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
      EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface);
      EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
      EGL14.eglReleaseThread();
      EGL14.eglTerminate(mEGLDisplay);
    }

    boolean mReleaseSurface = false;
    if (mReleaseSurface) {
      mgSurface.release();
    }

    mEGLDisplay = EGL14.EGL_NO_DISPLAY;
    mEGLContext = EGL14.EGL_NO_CONTEXT;
    mEGLSurface = EGL14.EGL_NO_SURFACE;

    mgSurface = null;
  }

  /**
   * Makes our EGL context and surface current.
   */
  public void makeCurrent() throws Exception {
    if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
      throw new Exception("eglMakeCurrent failed");
    }
  }

  public void makeUnCurrent() throws Exception {
    if (!EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
        EGL14.EGL_NO_CONTEXT)) {
      throw new Exception("eglMakeCurrent failed");
    }
  }

  /**
   * Queries the surface's width.
   */
  public int getWidth() {
    int[] value = new int[1];
    EGL14.eglQuerySurface(mEGLDisplay, mEGLSurface, EGL14.EGL_WIDTH, value, 0);
    return value[0];
  }

  /**
   * Queries the surface's height.
   */
  public int getHeight() {
    int[] value = new int[1];
    EGL14.eglQuerySurface(mEGLDisplay, mEGLSurface, EGL14.EGL_HEIGHT, value, 0);
    return value[0];
  }

  /**
   * Checks for EGL errors.
   */
  private void checkEglError(String msg) throws Exception {
    int error;
    if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
      throw new Exception(msg + ": EGL error: 0x" + Integer.toHexString(error));
    }
  }

  public Surface createInputSurface() {
    return mgSurface;
  }

}


class TextureRender {
  private final String TAG = "TextureRender";

  private final int FLOAT_SIZE_BYTES = 4;
  private final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
  private final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
  private final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;
  private final String VERTEX_SHADER =
      "uniform mat4 uMVPMatrix;\n" +
          "uniform mat4 uSTMatrix;\n" +
          "attribute vec4 aPosition;\n" +
          "attribute vec4 aTextureCoord;\n" +
          "varying vec2 vTextureCoord;\n" +
          "void main() {\n" +
          "  gl_Position = uMVPMatrix * aPosition;\n" +
          "  vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
          "}\n";
  private final String FRAGMENT_SHADER =
      "#extension GL_OES_EGL_image_external : require\n" +
          "precision mediump float;\n" +  // highp here doesn't seem to matter
          "varying vec2 vTextureCoord;\n" +
          "uniform samplerExternalOES sTexture;\n" +
          "void main() {\n" +
          "  gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
          "}\n";
  private final float[] mTriangleVerticesData = {
      // X, Y, Z, U, V
      -1.0f, -1.0f, 0, 0.f, 0.f,
      1.0f, -1.0f, 0, 1.f, 0.f,
      -1.0f, 1.0f, 0, 0.f, 1.f,
      1.0f, 1.0f, 0, 1.f, 1.f,
  };
  private final FloatBuffer mTriangleVertices;
  private final float[] mMVPMatrix = new float[16];
  private final float[] mSTMatrix = new float[16];

  private int mProgram;
  private int mTextureID = -12345;
  private int muMVPMatrixHandle;
  private int muSTMatrixHandle;
  private int maPositionHandle;
  private int maTextureHandle;

  public TextureRender() {
    mTriangleVertices = ByteBuffer.allocateDirect(
            mTriangleVerticesData.length * FLOAT_SIZE_BYTES)
        .order(ByteOrder.nativeOrder()).asFloatBuffer();
    mTriangleVertices.put(mTriangleVerticesData).position(0);
    Matrix.setIdentityM(mSTMatrix, 0);
  }

  public int getTextureId() {
    return mTextureID;
  }

  public void drawFrame(SurfaceTexture st) throws Exception {
    checkGlError("onDrawFrame start");
    st.getTransformMatrix(mSTMatrix);

    GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);

    GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
    GLES20.glUseProgram(mProgram);
    checkGlError("glUseProgram");
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);

    mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
    GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false,
        TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
    checkGlError("glVertexAttribPointer maPosition");
    GLES20.glEnableVertexAttribArray(maPositionHandle);
    checkGlError("glEnableVertexAttribArray maPositionHandle");

    mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
    GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false,
        TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
    checkGlError("glVertexAttribPointer maTextureHandle");
    GLES20.glEnableVertexAttribArray(maTextureHandle);
    checkGlError("glEnableVertexAttribArray maTextureHandle");

    Matrix.setIdentityM(mMVPMatrix, 0);
    GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0);
    GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0);

    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    checkGlError("glDrawArrays");
    GLES20.glFinish();
  }

  /**
   * Initializes GL state.  Call this after the EGL surface has been created and made current.
   */
  public void surfaceCreated() throws Exception {
    mProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
    if (mProgram == 0) {
      throw new Exception("failed creating program");
    }
    maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
    checkGlError("glGetAttribLocation aPosition");
    if (maPositionHandle == -1) {
      throw new Exception("Could not get attrib location for aPosition");
    }
    maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
    checkGlError("glGetAttribLocation aTextureCoord");
    if (maTextureHandle == -1) {
      throw new Exception("Could not get attrib location for aTextureCoord");
    }

    muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
    checkGlError("glGetUniformLocation uMVPMatrix");
    if (muMVPMatrixHandle == -1) {
      throw new Exception("Could not get attrib location for uMVPMatrix");
    }

    muSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix");
    checkGlError("glGetUniformLocation uSTMatrix");
    if (muSTMatrixHandle == -1) {
      throw new Exception("Could not get attrib location for uSTMatrix");
    }


    int[] textures = new int[1];
    GLES20.glGenTextures(1, textures, 0);

    mTextureID = textures[0];
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);
    checkGlError("glBindTexture mTextureID");

    GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
        GLES20.GL_NEAREST);
    GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
        GLES20.GL_LINEAR);
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
        GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
        GLES20.GL_CLAMP_TO_EDGE);
    checkGlError("glTexParameter");
  }

  private int loadShader(int shaderType, String source) throws Exception {
    int shader = GLES20.glCreateShader(shaderType);
    checkGlError("glCreateShader type=" + shaderType);
    GLES20.glShaderSource(shader, source);
    GLES20.glCompileShader(shader);
    int[] compiled = new int[1];
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
    if (compiled[0] == 0) {
      Log.e(TAG, "Could not compile shader " + shaderType + ":");
      Log.e(TAG, " " + GLES20.glGetShaderInfoLog(shader));
      GLES20.glDeleteShader(shader);
      shader = 0;
    }
    return shader;
  }

  private int createProgram(String vertexSource, String fragmentSource) throws Exception {
    Log.e(TAG, "createProgram ");
    int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
    if (vertexShader == 0) {
      return 0;
    }
    int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
    if (pixelShader == 0) {
      return 0;
    }

    int program = GLES20.glCreateProgram();
    checkGlError("glCreateProgram");
    if (program == 0) {
      Log.e(TAG, "Could not create program");
    }
    GLES20.glAttachShader(program, vertexShader);
    checkGlError("glAttachShader");
    GLES20.glAttachShader(program, pixelShader);
    checkGlError("glAttachShader");
    GLES20.glLinkProgram(program);
    int[] linkStatus = new int[1];
    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
    if (linkStatus[0] != GLES20.GL_TRUE) {
      Log.e(TAG, "Could not link program: ");
      Log.e(TAG, GLES20.glGetProgramInfoLog(program));
      GLES20.glDeleteProgram(program);
      program = 0;
    }
    return program;
  }

  public void checkGlError(String op) throws Exception {
    int error;
    while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
      Log.e(TAG, op + ": glError " + error);
      throw new Exception(op + ": glError " + error);
    }
  }
}