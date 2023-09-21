/*
 **************************************************************************************************
 * Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 **************************************************************************************************
 */

package qti.video;

import android.app.Activity;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import androidx.annotation.NonNull;
import java.util.List;
import java.util.Map;
import qti.video.demo.R;

/**
 * Main activity of demo app for codec capability query.
 */
public class MainActivity extends Activity implements TextureView.SurfaceTextureListener {
  // set up logging TAG
  private static final String TAG = "QtiVidExtAPK";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    TextureView surface = findViewById(R.id.textureView);
    Log.v(TAG, "Started the test, waiting for surface to be ready");
    surface.setSurfaceTextureListener(this);
  }

  @Override
  public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
    // wait for the surface to be ready
    Log.v(TAG, "In handler (surface ready), calling functions()");
    // test QMediaCodecCapabilities API
    Log.v(TAG, "---- TESTING EXT API ----");
    testExtApiAll();
  }

  @Override
  public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
    // don't need this for this simple test
  }

  @Override
  public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
    return false;
  }

  @Override
  public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
    // don't need this for this simple test
  }

  private String supportedValueToString(QMediaCodecCapabilities.SupportedValues supportedValues) {
    if (supportedValues == null) {
      return "NULL";
    }
    String datatypeStr = supportedValues.getDataType().name();
    QMediaCodecCapabilities.SupportedValues.Type type = supportedValues.getType();
    String typeStr = type.name();
    String ext;
    if (type == QMediaCodecCapabilities.SupportedValues.Type.EMPTY
        || type == QMediaCodecCapabilities.SupportedValues.Type.ANY) {
      ext = "{}";
    } else if (type == QMediaCodecCapabilities.SupportedValues.Type.RANGE) {
      QMediaCodecCapabilities.ValueRange<?> range = supportedValues.getRange();
      ext = "{ " + range.getMin() + ", "
          + range.getMax() + ", "
          + range.getStep() + ", "
          + range.getNum() + ", "
          + range.getDenom() + " }";
    } else {
      // values
      List<?> values = supportedValues.getValues();
      if (values == null || values.isEmpty()) {
        return "supported range type " + typeStr + " for datatype " + datatypeStr + ": {}";
      }
      StringBuilder stringBuilder = new StringBuilder("{ " + values.get(0));
      for (int i = 1; i < values.size(); i++) {
        stringBuilder.append(", ");
        stringBuilder.append(values.get(i));
      }
      stringBuilder.append(" }");
      ext = stringBuilder.toString();
    }
    return "supported range type " + typeStr + " for datatype " + datatypeStr + ": " + ext;
  }


  private void testExtApiAll() {
    Context context = getApplicationContext();
    MediaCodecInfo[] infos = new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos();
    for (MediaCodecInfo info : infos) {
      String codecName = info.getName();
      String[] types = info.getSupportedTypes();
      String mime = types[0];
      if (mime.toLowerCase().contains("video")) {
        Log.v(TAG, "----");
        Log.v(TAG, "testing QMediaCodecCapabilities API with codec " + codecName);
        Log.v(TAG, "mime type " + mime);
        Map<String, QMediaCodecCapabilities.SupportedValues> supportedValuesMap;
        QMediaCodecCapabilities ext;
        try {
          ext = QMediaCodecCapabilities.createForCodec(codecName, mime, context);
        } catch (Exception e) {
          Log.e(TAG, "createForCodec threw exception");
          e.printStackTrace();
          continue;
        }
        if (ext == null) {
          Log.e(TAG, "createForCodec returned null");
          continue;
        }
        supportedValuesMap = ext.getSupportedParameterRanges();
        if (supportedValuesMap.isEmpty()) {
          Log.e(TAG, "getSupportedParameterRanges returned empty");
        }
        for (String key : supportedValuesMap.keySet()) {
          Log.v(TAG,
              "param '" + key + "' with " + supportedValueToString(supportedValuesMap.get(key)));
        }
        Log.v(TAG, "----");
      }
    }
  }
}
