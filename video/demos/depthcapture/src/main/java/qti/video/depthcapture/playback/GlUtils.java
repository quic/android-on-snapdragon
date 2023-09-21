/*
 **************************************************************************************************
 * Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 **************************************************************************************************
 */

package qti.video.depthcapture.playback;

import android.content.Context;
import android.opengl.GLES30;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class GlUtils {
  static final String TAG = "GlUtils";

  public static void checkGlError(String op) {
    int error = GLES30.glGetError();
    if (error != GLES30.GL_NO_ERROR) {
      String msg = op + ": glError 0x" + Integer.toHexString(error);
      Log.wtf(TAG, msg, new RuntimeException(msg));
    }
  }

  public static void checkLocation(int location, String label) {
    if (location < 0) {
      throw new RuntimeException("Unable to locate '" + label + "' in program");
    }
  }

  public static int createProgramFromResRaw(Context context, int vertexResId, int fragmentResId) {
    return createProgram(
        loadResRaw(context, vertexResId),
        loadResRaw(context, fragmentResId)
    );
  }

  public static int createProgram(String vertexSource, String fragmentSource) {
    int vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexSource);
    assert (vertexShader != 0);
    int pixelShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource);
    assert (pixelShader != 0);
    int program = GLES30.glCreateProgram();
    checkGlError("glCreateProgram");
    assert (program != 0);
    GLES30.glAttachShader(program, vertexShader);
    checkGlError("glAttachShader");
    GLES30.glAttachShader(program, pixelShader);
    checkGlError("glAttachShader");
    GLES30.glLinkProgram(program);
    int[] linkStatus = new int[1];
    GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0);
    if (linkStatus[0] != GLES30.GL_TRUE) {
      Log.e(TAG, "Could not link program: ");
      Log.e(TAG, GLES30.glGetProgramInfoLog(program));
      GLES30.glDeleteProgram(program);
      program = 0;
      assert (false);
    }
    return program;
  }

  private static int loadShader(int shaderType, String source) {
    int shader = GLES30.glCreateShader(shaderType);
    checkGlError("glCreateShader type=$shaderType");
    GLES30.glShaderSource(shader, source);
    checkGlError("glShaderSource");
    GLES30.glCompileShader(shader);
    checkGlError("glCompileShader");
    int[] compiled = new int[1];
    GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0);
    if (compiled[0] == 0) {
      Log.e(TAG, "Could not compile shader $shaderType:");
      Log.e(TAG, " " + GLES30.glGetShaderInfoLog(shader));
      GLES30.glDeleteShader(shader);
      shader = 0;
      assert (false);
    }
    return shader;
  }

  public static String loadResRaw(Context context, int resRawId) {
    try (InputStream in = context.getResources().openRawResource(resRawId)) {
      byte[] bytes = in.readAllBytes();
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
