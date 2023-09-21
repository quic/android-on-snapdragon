/*
 **************************************************************************************************
 * Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 **************************************************************************************************
 */

package qti.video;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * <p>Run the tests with adb command:
 *     1. Install test apk
 *     2. adb shell am instrument -w qti.video.test/androidx.test.runner.AndroidJUnitRunner
 *
 *  <p>Run the tests from Android Studio:
 *     Create Run Configuration for Android Instrumented Tests (All Tests), then run it
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4ClassRunner.class)
public class ExampleInstrumentedTest {

  private static final String TAG = "QtiVidExtJUnit";

  private static boolean queryCapsSupported;
  private static boolean isSupportedLegacyDevice;

  /**
   * Setup.
   */
  @BeforeClass
  public static void setup() {
    // needed variables
    String codecNameEnc = "c2.qti.hevc.encoder";
    String codecNameDec = "c2.qti.hevc.decoder";
    final String mime = "video/hevc";
    final int width = 640;
    final int height = 272;

    // TODO: extend to new platforms
    Assume.assumeTrue("Only run on Kailua or Lanai",
        "sm8550".equalsIgnoreCase(Build.SOC_MODEL)
        || "sm8650".equalsIgnoreCase(Build.SOC_MODEL));

    // check if assetFile is found
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    QMediaCodecCapabilities extEnc = QMediaCodecCapabilities.createForCodec(codecNameEnc, mime, context);
    QMediaCodecCapabilities extDec = QMediaCodecCapabilities.createForCodec(codecNameDec, mime, context);
    assertNotNull(extEnc);
    assertNotNull(extDec);

    if (extEnc == null || extDec == null) {
      Log.e(TAG, "Failed to create QMediaCodecCapabilities instance to check for assetFile");
      isSupportedLegacyDevice = false;
      return;
    }
    String assetFile = extEnc.getAssetFile();
    if (assetFile != null) {
      isSupportedLegacyDevice = true;
    }

    // create the codec
    MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
    format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
    format.setInteger(MediaFormat.KEY_BIT_RATE, 10000000);
    format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
    MediaCodec codecEnc;
    MediaCodec codecDec;
    try {
      Log.v(TAG, "Creating Encoder " + codecNameEnc);
      codecEnc = MediaCodec.createByCodecName(codecNameEnc);
      codecEnc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
      Log.v(TAG, "Creating Decoder " + codecNameDec);
      codecDec = MediaCodec.createByCodecName(codecNameDec);
      codecDec.configure(format, null, null, 0);
    } catch (Exception e) {
      Log.e(TAG, "Failed to create codec");
      queryCapsSupported = false;
      return;
    }
    List<String> vendorParams = codecEnc.getSupportedVendorParameters();
    final String C2CapsQueryInput_Key = "vendor.qti-ext-caps-query-input.value";
    final String C2CapsQueryOutput_Key = "vendor.qti-ext-caps-query-output.value";
    queryCapsSupported = vendorParams.contains(C2CapsQueryInput_Key)
        && vendorParams.contains(C2CapsQueryOutput_Key);
  }

  @Test
  public void useAppContext() {
    // Context of the app under test.
    Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    assertEquals("qti.video.test", appContext.getPackageName());
  }

  @Test
  public void createForCodec_isCorrect() {
    // TODO: TIMING LOGGING
    // needed variables
    final String codecName = "c2.qti.hevc.encoder";
    final String fakeCodecName = "thisIsNotACodec";
    final String mime = "video/hevc";
    final String fakeMime = "notAMime";
    final String mimeButWrong = "video/avc";
    final int width = 640;
    final int height = 272;

    // create the codec
    MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
    format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
    format.setInteger(MediaFormat.KEY_BIT_RATE, 10000000);
    format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
    MediaCodec codec = null;
    try {
      Log.v(TAG, "Creating Codec " + codecName);
      codec = MediaCodec.createByCodecName(codecName);
      codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    } catch (Exception e) {
      Log.e(TAG, "Failed to create codec");
      fail();
    }

    // get context
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    // createForCodec with codecName
    QMediaCodecCapabilities ext;
    Log.d(TAG, "check for invalid codec name + valid mime type");
    long start1 = System.nanoTime();
    ext = QMediaCodecCapabilities.createForCodec(fakeCodecName, mime, context);
    long end1 = System.nanoTime();
    Log.d(TAG, "Time taken by createForCodec(fakeCodecName, mime, context) in nanoseconds "
        + (end1 - start1));
    assertNull(ext);

    Log.d(TAG, "check for valid codec name + invalid mime type");
    start1 = System.nanoTime();
    ext = QMediaCodecCapabilities.createForCodec(codecName, fakeMime, context);
    end1 = System.nanoTime();
    Log.d(TAG, "Time taken by createForCodec(codecName, fakeMime, context) in nanoseconds "
        + (end1 - start1));
    assertNull(ext);

    Log.d(TAG, "check for valid codec name + valid mime type but of wrong mime");
    start1 = System.nanoTime();
    ext = QMediaCodecCapabilities.createForCodec(codecName, mimeButWrong, context);
    end1 = System.nanoTime();
    Log.d(TAG, "Time taken by createForCodec(codecName, mimeButWrong, context) in nanoseconds "
        + (end1 - start1));
    assertNull(ext);

    Log.d(TAG, "check for invalid codec name + invalid mime type");
    start1 = System.nanoTime();
    ext = QMediaCodecCapabilities.createForCodec(fakeCodecName, fakeMime, context);
    end1 = System.nanoTime();
    Log.d(TAG, "Time taken by createForCodec(fakeCodecName, fakeMime, context) in nanoseconds "
        + (end1 - start1));
    assertNull(ext);

    Log.d(TAG, "check for valid codec name + valid mime type");
    start1 = System.nanoTime();
    ext = QMediaCodecCapabilities.createForCodec(codecName, mime, context);
    end1 = System.nanoTime();
    Log.d(TAG,
        "Time taken by createForCodec(codecName, mime, context) in nanoseconds " + (end1 - start1));
    if (queryCapsSupported || isSupportedLegacyDevice) {
      assertNotNull(ext);
    } else {
      assertNull(ext);
    }

    // createForCodec with input codec instance
    Log.d(TAG, "check for valid codec name + valid mime type with input codec instance");
    start1 = System.nanoTime();
    ext = QMediaCodecCapabilities.createForCodec(codec, mime, context);
    end1 = System.nanoTime();
    Log.d(TAG,
        "Time taken by createForCodec(codec, mime, context) in nanoseconds " + (end1 - start1));
    if (queryCapsSupported || isSupportedLegacyDevice) {
      assertNotNull(ext);
    } else {
      assertNull(ext);
    }

    // createForCodec with null Context with codecName
    Log.d(TAG, "check for valid codec name + valid mime type with null context");
    start1 = System.nanoTime();
    ext = QMediaCodecCapabilities.createForCodec(codecName, mime, null);
    end1 = System.nanoTime();
    Log.d(TAG,
        "Time taken by createForCodec(codecName, mime, null) in nanoseconds " + (end1 - start1));
    if (queryCapsSupported) {
      assertNotNull(ext);
    } else {
      assertNull(ext);
    }

    // with null Context with input codec instance
    Log.d(TAG, "check for valid codec name + valid mime type with input codex and null context");
    start1 = System.nanoTime();
    ext = QMediaCodecCapabilities.createForCodec(codec, mime, null);
    end1 = System.nanoTime();
    Log.d(TAG, "Time taken by createForCodec(codec, mime, null) in nanoseconds " + (end1 - start1));
    if (queryCapsSupported) {
      assertNotNull(ext);
    } else {
      assertNull(ext);
    }

    // cleanup the codec instance
    codec.release();
  }

  @Test
  public void getParameterRangeInteger_isCorrect() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    String codecName = "c2.qti.hevc.decoder";
    String mime = "video/hevc";
    QMediaCodecCapabilities ext = QMediaCodecCapabilities.createForCodec(codecName, mime, context);
    if (!queryCapsSupported && !isSupportedLegacyDevice) {
      Log.e(TAG,
          "Not a supported device for QMediaCodecCapabilities;"
              + " skipping test getParameterRangeInteger_isCorrect");
      assertNull(ext);
      return;
    }
    assertNotNull(ext);

    // vendor.qti-ext-dec-info-crop.height integer type range
    Log.d(TAG, "checking for integer param with type range");
    QMediaCodecCapabilities.SupportedValues<Integer> intRangeParam =
        ext.getParameterRangeInteger("vendor.qti-ext-dec-info-crop.height");
    assertNotNull(intRangeParam);
    assertEquals(intRangeParam.getDataType(),
        QMediaCodecCapabilities.SupportedValues.DataType.INTEGER);
    assertEquals(intRangeParam.getType(), QMediaCodecCapabilities.SupportedValues.Type.RANGE);
    assertNull(intRangeParam.getValues());
    assertNull(intRangeParam.getFlags());
    assertNotNull(intRangeParam.getRange());

    // vendor.qti-ext-dec-linear-color-format.value integer type values
    Log.d(TAG, "checking for integer param with type values");
    QMediaCodecCapabilities.SupportedValues<Integer> intValuesParam =
        ext.getParameterRangeInteger("vendor.qti-ext-dec-linear-color-format.value");
    assertNotNull(intValuesParam);
    assertEquals(intValuesParam.getDataType(),
        QMediaCodecCapabilities.SupportedValues.DataType.INTEGER);
    assertEquals(intValuesParam.getType(), QMediaCodecCapabilities.SupportedValues.Type.VALUES);
    assertNotNull(intValuesParam.getValues());
    assertNull(intValuesParam.getFlags());
    assertNull(intValuesParam.getRange());

    // vendor.qti-ext-enc-info-interlace.mbaff integer type empty
    Log.d(TAG, "checking for integer param with type empty");
    QMediaCodecCapabilities.SupportedValues<Integer> intEmptyParam =
        ext.getParameterRangeInteger("vendor.qti-ext-enc-info-interlace.mbaff");
    assertNotNull(intEmptyParam);
    assertEquals(intEmptyParam.getDataType(),
        QMediaCodecCapabilities.SupportedValues.DataType.INTEGER);
    assertEquals(intEmptyParam.getType(), QMediaCodecCapabilities.SupportedValues.Type.EMPTY);
    assertNull(intEmptyParam.getValues());
    assertNull(intEmptyParam.getFlags());
    assertNull(intEmptyParam.getRange());

    // vendor.qti-ext-dec-caps-vt-driver-version.number integer type any
    Log.d(TAG, "checking for integer param with type any");
    QMediaCodecCapabilities.SupportedValues<Integer> intAnyParam =
        ext.getParameterRangeInteger("vendor.qti-ext-dec-caps-vt-driver-version.number");
    assertNotNull(intAnyParam);
    assertEquals(intAnyParam.getDataType(),
        QMediaCodecCapabilities.SupportedValues.DataType.INTEGER);
    assertEquals(intAnyParam.getType(), QMediaCodecCapabilities.SupportedValues.Type.ANY);
    assertNull(intAnyParam.getValues());
    assertNull(intAnyParam.getFlags());
    assertNull(intAnyParam.getRange());

    // try a non-existent param, should fail
    Log.d(TAG, "checking for invalid param");
    QMediaCodecCapabilities.SupportedValues<Integer> improperParam =
        ext.getParameterRangeInteger("vendor.this-param-does-not-exist");
    assertNull(improperParam);

    // try a non-integer param, should fail
    Log.d(TAG, "checking for non-integer param");
    QMediaCodecCapabilities.SupportedValues<Integer> longParam =
        ext.getParameterRangeInteger("vendor.qti-ext-enc-roiinfo.timestamp");
    assertNull(longParam);
  }

  @Test
  public void getParameterRangeLong_isCorrect() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    String codecName = "c2.qti.hevc.encoder";
    String mime = "video/hevc";
    QMediaCodecCapabilities ext = QMediaCodecCapabilities.createForCodec(codecName, mime, context);
    if (!queryCapsSupported && !isSupportedLegacyDevice) {
      Log.e(TAG,
          "Not a supported device for QMediaCodecCapabilities;"
              + " skipping test getParameterRangeLong_isCorrect");
      assertNull(ext);
      return;
    }
    assertNotNull(ext);

    // vendor.qti-ext-enc-roiinfo.timestamp long type range
    QMediaCodecCapabilities.SupportedValues<Long> longRangeParam =
        ext.getParameterRangeLong("vendor.qti-ext-enc-roiinfo.timestamp");
    assertNotNull(longRangeParam);
    assertEquals(longRangeParam.getDataType(),
        QMediaCodecCapabilities.SupportedValues.DataType.LONG);
    assertEquals(longRangeParam.getType(), QMediaCodecCapabilities.SupportedValues.Type.RANGE);
    assertNull(longRangeParam.getValues());
    assertNull(longRangeParam.getFlags());
    assertNotNull(longRangeParam.getRange());

    // try a non-existent param, should fail
    QMediaCodecCapabilities.SupportedValues<Long> improperParam =
        ext.getParameterRangeLong("vendor.this-param-does-not-exist");
    assertNull(improperParam);

    // try a non-long param, should fail
    QMediaCodecCapabilities.SupportedValues<Long> intParam =
        ext.getParameterRangeLong("vendor.qti-ext-enc-info-interlace.mbaff");
    assertNull(intParam);
  }

  @Test
  public void getParameterRangeFloat_isCorrect() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    String codecName = "c2.qti.hevc.decoder";
    String mime = "video/hevc";
    QMediaCodecCapabilities ext = QMediaCodecCapabilities.createForCodec(codecName, mime, context);
    if (!queryCapsSupported && !isSupportedLegacyDevice) {
      Log.e(TAG,
          "Not a supported device for QMediaCodecCapabilities;"
              + " skipping test getParameterRangeFloat_isCorrect");
      assertNull(ext);
      return;
    }
    assertNotNull(ext);

    // vendor.qti-ext-dec-output-render-frame-rate.value float range
    QMediaCodecCapabilities.SupportedValues<Float> floatRangeParam =
        ext.getParameterRangeFloat("vendor.qti-ext-dec-output-render-frame-rate.value");
    assertNotNull(floatRangeParam);
    assertEquals(floatRangeParam.getDataType(),
        QMediaCodecCapabilities.SupportedValues.DataType.FLOAT);
    assertEquals(floatRangeParam.getType(), QMediaCodecCapabilities.SupportedValues.Type.RANGE);
    assertNull(floatRangeParam.getValues());
    assertNull(floatRangeParam.getFlags());
    assertNotNull(floatRangeParam.getRange());

    // try a non-existent param, should fail
    QMediaCodecCapabilities.SupportedValues<Float> improperParam =
        ext.getParameterRangeFloat("vendor.this-param-does-not-exist");
    assertNull(improperParam);

    // try a non-float param, should fail
    QMediaCodecCapabilities.SupportedValues<Float> intParam =
        ext.getParameterRangeFloat("vendor.qti-ext-enc-info-interlace.mbaff");
    assertNull(intParam);
  }

  @Test
  public void getParameterRangeForString_isCorrect() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    String codecName = "c2.qti.hevc.encoder";
    String mime = "video/hevc";
    QMediaCodecCapabilities ext = QMediaCodecCapabilities.createForCodec(codecName, mime, context);
    if (!queryCapsSupported && !isSupportedLegacyDevice) {
      Log.e(TAG,
          "Not a supported device for QMediaCodecCapabilities;"
              + " skipping test getParameterRangeForString_isCorrect");
      assertNull(ext);
      return;
    }
    assertNotNull(ext);

    // vendor.qti-ext-enc-roiinfo.rect-payload string type range
    QMediaCodecCapabilities.SupportedValues<Integer> stringRangeParam =
        ext.getParameterRangeForString("vendor.qti-ext-enc-roiinfo.rect-payload");
    assertNotNull(stringRangeParam);
    // or should this return QMediaCodecCapabilities.SupportedValues.DataType.INTEGER???
    Log.d(TAG, "returned supported param for vendor.qti-ext-enc-roiinfo.rect-payload is " + stringRangeParam);
    assertEquals(stringRangeParam.getDataType(),
        QMediaCodecCapabilities.SupportedValues.DataType.STRING);
    assertEquals(stringRangeParam.getType(), QMediaCodecCapabilities.SupportedValues.Type.RANGE);
    assertNull(stringRangeParam.getValues());
    assertNull(stringRangeParam.getFlags());
    assertNotNull(stringRangeParam.getRange());

    // try a non-existent param, should fail
    QMediaCodecCapabilities.SupportedValues<Integer> improperParam =
        ext.getParameterRangeForString("vendor.this-param-does-not-exist");
    assertNull(improperParam);

    // try a non-string param, should fail
    QMediaCodecCapabilities.SupportedValues<Integer> intParam =
        ext.getParameterRangeForString("vendor.qti-ext-enc-info-interlace.mbaff");
    assertNull(intParam);
  }

  @Test
  public void getParameterRangeForByteBuffer_isCorrect() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    String codecName = "c2.qti.hevc.decoder";
    String mime = "video/hevc";
    QMediaCodecCapabilities ext = QMediaCodecCapabilities.createForCodec(codecName, mime, context);
    if (!queryCapsSupported && !isSupportedLegacyDevice) {
      Log.e(TAG,
          "Not a supported device for QMediaCodecCapabilities;"
              + " skipping test getParameterRangeForByteBuffer_isCorrect");
      assertNull(ext);
      return;
    }
    assertNotNull(ext);

    // vendor.qti-ext-dec-info-misr.value byte_buffer type range
    QMediaCodecCapabilities.SupportedValues<Integer> byteBufferRangeParam =
        ext.getParameterRangeForByteBuffer("vendor.qti-ext-dec-info-misr.value");
    assertNotNull(byteBufferRangeParam);
    // or should this return QMediaCodecCapabilities.SupportedValues.DataType.INTEGER???
    assertEquals(byteBufferRangeParam.getDataType(),
        QMediaCodecCapabilities.SupportedValues.DataType.BYTE_BUFFER);
    assertEquals(byteBufferRangeParam.getType(),
        QMediaCodecCapabilities.SupportedValues.Type.RANGE);
    assertNull(byteBufferRangeParam.getValues());
    assertNull(byteBufferRangeParam.getFlags());
    assertNotNull(byteBufferRangeParam.getRange());

    // try a non-existent param, should fail
    QMediaCodecCapabilities.SupportedValues<Integer> improperParam =
        ext.getParameterRangeForByteBuffer("vendor.this-param-does-not-exist");
    assertNull(improperParam);

    // try a non-bytebuffer param, should fail
    QMediaCodecCapabilities.SupportedValues<Integer> intParam =
        ext.getParameterRangeForByteBuffer("vendor.qti-ext-enc-info-interlace.mbaff");
    assertNull(intParam);
  }

  private String supportedValueToString(
      QMediaCodecCapabilities.SupportedValues<?> supportedValues) {
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
    return "[ supported range type " + typeStr + " for datatype " + datatypeStr + ": " + ext + " ]";
  }

  @Test
  public void getSupportedParameterRanges_isCorrect() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    String codecName = "c2.qti.hevc.encoder";
    String mime = "video/hevc";
    QMediaCodecCapabilities ext = QMediaCodecCapabilities.createForCodec(codecName, mime, context);
    if (!queryCapsSupported && !isSupportedLegacyDevice) {
      Log.e(TAG,
          "Not a supported device for QMediaCodecCapabilities;"
              + " skipping test getSupportedParameterRanges_isCorrect");
      assertNull(ext);
      return;
    }
    assertNotNull(ext);

    Map<String, QMediaCodecCapabilities.SupportedValues> supportedParamRanges =
        ext.getSupportedParameterRanges();
    assertNotNull(supportedParamRanges);

    // verify values?
    HashSet<String> set = new HashSet<>(supportedParamRanges.keySet());
    for (String param : set) {
      QMediaCodecCapabilities.SupportedValues<?> range = supportedParamRanges.get(param);
      assertNotNull(range);
      Log.d(TAG, "param " + param + " has range " + supportedValueToString(range));
      switch (range.getType()) {
        case EMPTY:
          // same as for ANY
        case ANY:
          assertNull(range.getRange());
          assertNull(range.getValues());
          assertNull(range.getFlags());
          break;
        case RANGE:
          assertNotNull(range.getRange());
          assertNull(range.getValues());
          assertNull(range.getFlags());
          break;
        case FLAGS:
          assertNull(range.getRange());
          assertNull(range.getValues());
          assertNotNull(range.getFlags());
          break;
        case VALUES:
          assertNull(range.getRange());
          assertNotNull(range.getValues());
          assertNull(range.getFlags());
          break;
        default:
          // should not get here
          Log.e(TAG, "unknown range Type");
          fail();
          break;
      }
    }
  }

  // test private methods
  @Test
  public void isSupportedDevice_isCorrect() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    String codecName = "c2.qti.hevc.encoder";
    String mime = "video/hevc";
    QMediaCodecCapabilities ext = QMediaCodecCapabilities.createForCodec(codecName, mime, context);
    if (!queryCapsSupported && !isSupportedLegacyDevice) {
      Log.e(TAG,
          "Not a supported device for QMediaCodecCapabilities;"
              + " skipping test isSupportedDevice_isCorrect");
      assertNull(ext);
      return;
    }
    assertNotNull(ext);

    Class<?>[] params = new Class[0];
    Method isLegacyDevice = null;
    try {
      isLegacyDevice = ext.getClass().getDeclaredMethod("isLegacyDevice", params);
    } catch (NoSuchMethodException e) {
      // should not happen
      e.printStackTrace();
      fail();
    }
    assertNotNull(isLegacyDevice);
    isLegacyDevice.setAccessible(true);

    boolean isLegacyDev = false;
    try {
      isLegacyDev = (boolean) isLegacyDevice.invoke(ext, (Object[]) null);
    } catch (Exception e) {
      e.printStackTrace();
      fail();
    }

    if (!isLegacyDev) {
      MediaCodec codec = null;
      try {
        Log.v(TAG, "Creating Codec " + codecName);
        codec = MediaCodec.createByCodecName(codecName);
      } catch (Exception e) {
        Log.e(TAG, "Failed to create codec");
        fail();
      }
      List<String> vendorParams = codec.getSupportedVendorParameters();
      final String C2CapsQueryInput_Key = "vendor.qti-ext-caps-query-input.value";
      final String C2CapsQueryOutput_Key = "vendor.qti-ext-caps-query-output.value";
      if (vendorParams.contains(C2CapsQueryInput_Key)
          && vendorParams.contains(C2CapsQueryOutput_Key)) {
        assertNotNull(ext);
      }
    }
  }

  @Test
  public void isLegacyDevice_isCorrect() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    String codecName = "c2.qti.hevc.encoder";
    String mime = "video/hevc";
    QMediaCodecCapabilities ext = QMediaCodecCapabilities.createForCodec(codecName, mime, context);
    if (!queryCapsSupported && !isSupportedLegacyDevice) {
      Log.e(TAG,
          "Not a supported device for QMediaCodecCapabilities;"
              + " skipping test isLegacyDevice_isCorrect");
      assertNull(ext);
      return;
    }
    assertNotNull(ext);

    Class<?>[] params = new Class[0];
    Method isLegacyDevice = null;
    try {
      isLegacyDevice = ext.getClass().getDeclaredMethod("isLegacyDevice", params);
    } catch (NoSuchMethodException e) {
      // should not happen
      e.printStackTrace();
      fail();
    }
    assertNotNull(isLegacyDevice);
    isLegacyDevice.setAccessible(true);
    boolean isLegacyDev = false;
    String assetFile = ext.getAssetFile();
    try {
      isLegacyDev = (boolean) isLegacyDevice.invoke(ext, (Object[]) null);
    } catch (Exception e) {
      e.printStackTrace();
      fail();
    }

    if (assetFile == null) {
      // non-legacy dev
      Log.d(TAG, "is not a legacy device");
      assertFalse(isLegacyDev);
    } else {
      Log.d(TAG, "is a legacy device");
      String[] list;
      String jsonFile = "";
      try {
        list = context.getAssets().list("");
        for (String file : list) {
          if (file.contains("QMediaCodecCapabilities")) {
            String board = Build.BOARD.toLowerCase(Locale.ROOT);
            String model = Build.SOC_MODEL.toLowerCase(Locale.ROOT);
            if (file.contains(board) || file.contains(model)) {
              jsonFile = file;
              break;
            }
          }
        }
      } catch (IOException e) {
        Log.e(TAG, "failed to getAssets()");
        fail();
      }
      assertEquals(assetFile, jsonFile);
      assertTrue(isLegacyDev);
    }
  }

  @Test
  public void getParamDataType_isCorrect() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    String codecName = "c2.qti.hevc.encoder";
    String mime = "video/hevc";
    QMediaCodecCapabilities ext = QMediaCodecCapabilities.createForCodec(codecName, mime, context);
    if (!queryCapsSupported && !isSupportedLegacyDevice) {
      Log.e(TAG,
          "Not a supported device for QMediaCodecCapabilities;"
              + " skipping test getParamDataType_isCorrect");
      assertNull(ext);
      return;
    }
    assertNotNull(ext);

    Class<?>[] params = new Class[1];
    params[0] = String.class;
    Method getParamDataType = null;
    try {
      getParamDataType = ext.getClass().getDeclaredMethod("getParamDataType", params);
    } catch (NoSuchMethodException e) {
      // should not happen
      e.printStackTrace();
      fail();
    }
    assertNotNull(getParamDataType);
    getParamDataType.setAccessible(true);

    QMediaCodecCapabilities.SupportedValues.DataType int32DataType = null;
    QMediaCodecCapabilities.SupportedValues.DataType uint32DataType = null;
    QMediaCodecCapabilities.SupportedValues.DataType cntr32DataType = null;
    QMediaCodecCapabilities.SupportedValues.DataType int64DataType = null;
    QMediaCodecCapabilities.SupportedValues.DataType uint64DataType = null;
    QMediaCodecCapabilities.SupportedValues.DataType cntr64DataType = null;
    QMediaCodecCapabilities.SupportedValues.DataType stringDataType = null;
    QMediaCodecCapabilities.SupportedValues.DataType blobDataType = null;
    QMediaCodecCapabilities.SupportedValues.DataType floatDataType = null;
    QMediaCodecCapabilities.SupportedValues.DataType structDataType = null;
    QMediaCodecCapabilities.SupportedValues.DataType emptyDataType = null;
    QMediaCodecCapabilities.SupportedValues.DataType invalidDataType = null;
    try {
      int32DataType =
          (QMediaCodecCapabilities.SupportedValues.DataType) getParamDataType.invoke(ext, "INT32");
      uint32DataType =
          (QMediaCodecCapabilities.SupportedValues.DataType) getParamDataType.invoke(ext, "UINT32");
      cntr32DataType =
          (QMediaCodecCapabilities.SupportedValues.DataType) getParamDataType.invoke(ext, "CNTR32");
      int64DataType =
          (QMediaCodecCapabilities.SupportedValues.DataType) getParamDataType.invoke(ext, "INT64");
      uint64DataType =
          (QMediaCodecCapabilities.SupportedValues.DataType) getParamDataType.invoke(ext, "UINT64");
      cntr64DataType =
          (QMediaCodecCapabilities.SupportedValues.DataType) getParamDataType.invoke(ext, "CNTR64");
      stringDataType =
          (QMediaCodecCapabilities.SupportedValues.DataType) getParamDataType.invoke(ext, "STRING");
      blobDataType =
          (QMediaCodecCapabilities.SupportedValues.DataType) getParamDataType.invoke(ext, "BLOB");
      floatDataType =
          (QMediaCodecCapabilities.SupportedValues.DataType) getParamDataType.invoke(ext, "FLOAT");
      structDataType =
          (QMediaCodecCapabilities.SupportedValues.DataType) getParamDataType.invoke(ext,
              "STRUCT_FLAG");
      emptyDataType =
          (QMediaCodecCapabilities.SupportedValues.DataType) getParamDataType.invoke(ext, "");
      invalidDataType =
          (QMediaCodecCapabilities.SupportedValues.DataType) getParamDataType.invoke(ext,
              "INVALID_TYPE");
    } catch (Exception e) {
      e.printStackTrace();
      fail();
    }

    assertNotNull(int32DataType);
    assertEquals(int32DataType, QMediaCodecCapabilities.SupportedValues.DataType.INTEGER);
    assertNotNull(uint32DataType);
    assertEquals(uint32DataType, QMediaCodecCapabilities.SupportedValues.DataType.INTEGER);
    assertNotNull(cntr32DataType);
    assertEquals(cntr32DataType, QMediaCodecCapabilities.SupportedValues.DataType.INTEGER);
    assertNotNull(int64DataType);
    assertEquals(int64DataType, QMediaCodecCapabilities.SupportedValues.DataType.LONG);
    assertNotNull(uint64DataType);
    assertEquals(uint64DataType, QMediaCodecCapabilities.SupportedValues.DataType.LONG);
    assertNotNull(cntr64DataType);
    assertEquals(cntr64DataType, QMediaCodecCapabilities.SupportedValues.DataType.LONG);
    assertNotNull(stringDataType);
    assertEquals(stringDataType, QMediaCodecCapabilities.SupportedValues.DataType.STRING);
    assertNotNull(blobDataType);
    assertEquals(blobDataType, QMediaCodecCapabilities.SupportedValues.DataType.BYTE_BUFFER);
    assertNotNull(floatDataType);
    assertEquals(floatDataType, QMediaCodecCapabilities.SupportedValues.DataType.FLOAT);
    assertNotNull(structDataType);
    assertEquals(structDataType, QMediaCodecCapabilities.SupportedValues.DataType.NULL);
    assertNotNull(emptyDataType);
    assertEquals(emptyDataType, QMediaCodecCapabilities.SupportedValues.DataType.NULL);
    assertNotNull(invalidDataType);
    assertEquals(invalidDataType, QMediaCodecCapabilities.SupportedValues.DataType.NULL);
  }

  @Test
  public void getParamRangeType_isCorrect() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    String codecName = "c2.qti.hevc.encoder";
    String mime = "video/hevc";
    QMediaCodecCapabilities ext = QMediaCodecCapabilities.createForCodec(codecName, mime, context);
    if (!queryCapsSupported && !isSupportedLegacyDevice) {
      Log.e(TAG,
          "Not a supported device for QMediaCodecCapabilities;"
              + " skipping test getParamRangeType_isCorrect");
      assertNull(ext);
      return;
    }
    assertNotNull(ext);

    Class<?>[] params = new Class[1];
    params[0] = String.class;
    Method getParamRangeType = null;
    try {
      getParamRangeType = ext.getClass().getDeclaredMethod("getParamRangeType", params);
    } catch (NoSuchMethodException e) {
      // should not happen
      e.printStackTrace();
      fail();
    }
    assertNotNull(getParamRangeType);
    getParamRangeType.setAccessible(true);

    QMediaCodecCapabilities.SupportedValues.Type anyType = null;
    QMediaCodecCapabilities.SupportedValues.Type flagsType = null;
    QMediaCodecCapabilities.SupportedValues.Type valuesType = null;
    QMediaCodecCapabilities.SupportedValues.Type rangeType = null;
    QMediaCodecCapabilities.SupportedValues.Type emptyType = null;
    QMediaCodecCapabilities.SupportedValues.Type emptyInputType = null;
    QMediaCodecCapabilities.SupportedValues.Type invalidInputType = null;
    try {
      anyType = (QMediaCodecCapabilities.SupportedValues.Type) getParamRangeType.invoke(ext, "ANY");
      flagsType =
          (QMediaCodecCapabilities.SupportedValues.Type) getParamRangeType.invoke(ext, "FLAGS");
      valuesType =
          (QMediaCodecCapabilities.SupportedValues.Type) getParamRangeType.invoke(ext, "VALUES");
      rangeType =
          (QMediaCodecCapabilities.SupportedValues.Type) getParamRangeType.invoke(ext, "RANGE");
      emptyType =
          (QMediaCodecCapabilities.SupportedValues.Type) getParamRangeType.invoke(ext, "EMPTY");
      emptyInputType =
          (QMediaCodecCapabilities.SupportedValues.Type) getParamRangeType.invoke(ext, "");
      invalidInputType =
          (QMediaCodecCapabilities.SupportedValues.Type) getParamRangeType.invoke(ext, "INVALID");
    } catch (Exception e) {
      e.printStackTrace();
      fail();
    }

    assertNotNull(anyType);
    assertEquals(anyType, QMediaCodecCapabilities.SupportedValues.Type.ANY);
    assertNotNull(flagsType);
    assertEquals(flagsType, QMediaCodecCapabilities.SupportedValues.Type.FLAGS);
    assertNotNull(valuesType);
    assertEquals(valuesType, QMediaCodecCapabilities.SupportedValues.Type.VALUES);
    assertNotNull(rangeType);
    assertEquals(rangeType, QMediaCodecCapabilities.SupportedValues.Type.RANGE);
    assertNotNull(emptyType);
    assertEquals(emptyType, QMediaCodecCapabilities.SupportedValues.Type.EMPTY);
    assertNotNull(emptyInputType);
    assertEquals(emptyInputType, QMediaCodecCapabilities.SupportedValues.Type.EMPTY);
    assertNotNull(invalidInputType);
    assertEquals(invalidInputType, QMediaCodecCapabilities.SupportedValues.Type.EMPTY);
  }

  @Test
  public void checkForUnsignedRange_isCorrect() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    String codecName = "c2.qti.hevc.encoder";
    String mime = "video/hevc";
    QMediaCodecCapabilities ext = QMediaCodecCapabilities.createForCodec(codecName, mime, context);
    if (!queryCapsSupported && !isSupportedLegacyDevice) {
      Log.e(TAG,
          "Not a supported device for QMediaCodecCapabilities;"
              + " skipping test checkForUnsignedRange_isCorrect");
      assertNull(ext);
      return;
    }
    assertNotNull(ext);

    Class<?>[] params = new Class[1];
    params[0] = String.class;
    Method checkForUnsignedRange = null;
    try {
      checkForUnsignedRange = ext.getClass().getDeclaredMethod("checkForUnsignedRange", params);
    } catch (NoSuchMethodException e) {
      // should not happen
      e.printStackTrace();
      fail();
    }
    assertNotNull(checkForUnsignedRange);
    checkForUnsignedRange.setAccessible(true);
    boolean isInt32UnsignedRange = false;
    boolean isUint32UnsignedRange = false;
    boolean isCntr32UnsignedRange = false;
    boolean isInt64UnsignedRange = false;
    boolean isUint64UnsignedRange = false;
    boolean isCntr64UnsignedRange = false;
    boolean isBlobUnsignedRange = false;
    boolean isStringUnsignedRange = false;
    boolean isFloatUnsignedRange = false;
    boolean isStructUnsignedRange = false;
    boolean isUnknownUnsignedRange = false;
    try {
      isInt32UnsignedRange = (boolean) checkForUnsignedRange.invoke(ext, "INT32");
      isUint32UnsignedRange = (boolean) checkForUnsignedRange.invoke(ext, "UINT32");
      isCntr32UnsignedRange = (boolean) checkForUnsignedRange.invoke(ext, "CNTR32");
      isInt64UnsignedRange = (boolean) checkForUnsignedRange.invoke(ext, "INT64");
      isUint64UnsignedRange = (boolean) checkForUnsignedRange.invoke(ext, "UINT64");
      isCntr64UnsignedRange = (boolean) checkForUnsignedRange.invoke(ext, "CNTR64");
      isBlobUnsignedRange = (boolean) checkForUnsignedRange.invoke(ext, "BLOB");
      isStringUnsignedRange = (boolean) checkForUnsignedRange.invoke(ext, "STRING");
      isFloatUnsignedRange = (boolean) checkForUnsignedRange.invoke(ext, "FLOAT");
      isStructUnsignedRange = (boolean) checkForUnsignedRange.invoke(ext, "STRUCT_FLAG");
      isUnknownUnsignedRange = (boolean) checkForUnsignedRange.invoke(ext, "UNKNOWN");
    } catch (Exception e) {
      e.printStackTrace();
      fail();
    }

    assertFalse(isInt32UnsignedRange);
    assertTrue(isUint32UnsignedRange);
    assertTrue(isCntr32UnsignedRange);
    assertFalse(isInt64UnsignedRange);
    assertTrue(isUint64UnsignedRange);
    assertTrue(isCntr64UnsignedRange);
    assertTrue(isBlobUnsignedRange);
    assertTrue(isStringUnsignedRange);
    assertFalse(isFloatUnsignedRange);
    assertFalse(isStructUnsignedRange);
    assertFalse(isUnknownUnsignedRange);
  }

  @Test
  public void getRangeForAny_isCorrect() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    String codecName = "c2.qti.hevc.encoder";
    String mime = "video/hevc";
    QMediaCodecCapabilities ext = QMediaCodecCapabilities.createForCodec(codecName, mime, context);
    if (!queryCapsSupported && !isSupportedLegacyDevice) {
      Log.e(TAG,
          "Not a supported device for QMediaCodecCapabilities;"
              + " skipping test getRangeForAny_isCorrect");
      assertNull(ext);
      return;
    }
    assertNotNull(ext);

    Class<?>[] params = new Class[1];
    params[0] = String.class;
    Method getRangeForAny = null;
    try {
      getRangeForAny = ext.getClass().getDeclaredMethod("getRangeForAny", params);
    } catch (NoSuchMethodException e) {
      // should not happen
      e.printStackTrace();
      fail();
    }
    assertNotNull(getRangeForAny);
    getRangeForAny.setAccessible(true);
    QMediaCodecCapabilities.ValueRange<?> int32Range = null;
    QMediaCodecCapabilities.ValueRange<?> uint32Range = null;
    QMediaCodecCapabilities.ValueRange<?> cntr32Range = null;
    QMediaCodecCapabilities.ValueRange<?> int64Range = null;
    QMediaCodecCapabilities.ValueRange<?> uint64Range = null;
    QMediaCodecCapabilities.ValueRange<?> cntr64Range = null;
    QMediaCodecCapabilities.ValueRange<?> blobRange = null;
    QMediaCodecCapabilities.ValueRange<?> stringRange = null;
    QMediaCodecCapabilities.ValueRange<?> floatRange = null;
    QMediaCodecCapabilities.ValueRange<?> structRange = null;
    QMediaCodecCapabilities.ValueRange<?> unknownRange = null;
    try {
      int32Range = (QMediaCodecCapabilities.ValueRange<?>)
          getRangeForAny.invoke(ext, "INT32");
      uint32Range = (QMediaCodecCapabilities.ValueRange<?>)
          getRangeForAny.invoke(ext, "UINT32");
      cntr32Range = (QMediaCodecCapabilities.ValueRange<?>)
          getRangeForAny.invoke(ext, "CNTR32");
      int64Range = (QMediaCodecCapabilities.ValueRange<?>)
          getRangeForAny.invoke(ext, "INT64");
      uint64Range = (QMediaCodecCapabilities.ValueRange<?>)
          getRangeForAny.invoke(ext, "UINT64");
      cntr64Range = (QMediaCodecCapabilities.ValueRange<?>)
          getRangeForAny.invoke(ext, "CNTR64");
      blobRange = (QMediaCodecCapabilities.ValueRange<?>)
          getRangeForAny.invoke(ext, "BLOB");
      stringRange = (QMediaCodecCapabilities.ValueRange<?>)
          getRangeForAny.invoke(ext, "STRING");
      floatRange = (QMediaCodecCapabilities.ValueRange<?>)
          getRangeForAny.invoke(ext, "FLOAT");
      structRange = (QMediaCodecCapabilities.ValueRange<?>)
          getRangeForAny.invoke(ext, "STRUCT_FLAG");
      unknownRange = (QMediaCodecCapabilities.ValueRange<?>)
          getRangeForAny.invoke(ext, "UNKNOWN");
    } catch (Exception e) {
      e.printStackTrace();
      fail();
    }

    assertNotNull(int32Range);
    assertEquals(int32Range.getMin(), Integer.MIN_VALUE);
    assertEquals(int32Range.getMax(), Integer.MAX_VALUE);

    assertNotNull(uint32Range);
    assertEquals(uint32Range.getMin(), 0);
    assertEquals(uint32Range.getMax(), Integer.MAX_VALUE);

    assertNotNull(cntr32Range);
    assertEquals(cntr32Range.getMin(), 0);
    assertEquals(cntr32Range.getMax(), Integer.MAX_VALUE);

    assertNotNull(int64Range);
    assertEquals(int64Range.getMin(), Long.MIN_VALUE);
    assertEquals(int64Range.getMax(), Long.MAX_VALUE);

    assertNotNull(uint64Range);
    assertEquals(uint64Range.getMin(), 0L);
    assertEquals(uint64Range.getMax(), Long.MAX_VALUE);

    assertNotNull(cntr64Range);
    assertEquals(cntr64Range.getMin(), 0L);
    assertEquals(cntr64Range.getMax(), Long.MAX_VALUE);

    assertNotNull(blobRange);
    assertEquals(blobRange.getMin(), 0);
    assertEquals(blobRange.getMax(), 255);

    assertNotNull(stringRange);
    assertEquals(stringRange.getMin(), 0);
    assertEquals(stringRange.getMax(), 255);

    assertNotNull(floatRange);
    assertEquals(floatRange.getMin(), Float.MIN_VALUE);
    assertEquals(floatRange.getMax(), Float.MAX_VALUE);

    assertNull(structRange);
    assertNull(unknownRange);
  }

  @Test
  public void getValuesFromJson_isCorrect() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    String codecName = "c2.qti.hevc.encoder";
    String mime = "video/hevc";
    QMediaCodecCapabilities ext = QMediaCodecCapabilities.createForCodec(codecName, mime, context);
    if (!queryCapsSupported && !isSupportedLegacyDevice) {
      Log.e(TAG,
          "Not a supported device for QMediaCodecCapabilities;"
              + " skipping test getValuesFromJSON_isCorrect");
      assertNull(ext);
      return;
    }
    assertNotNull(ext);

    Class<?>[] params = new Class[2];
    params[0] = String.class;
    params[1] = JSONArray.class;
    Method getValuesFromJson = null;
    try {
      getValuesFromJson = ext.getClass().getDeclaredMethod("getValuesFromJson", params);
    } catch (NoSuchMethodException e) {
      // should not happen
      e.printStackTrace();
      fail();
    }
    assertNotNull(getValuesFromJson);
    getValuesFromJson.setAccessible(true);

    JSONArray properValues = null;
    JSONArray properFloatValues = null;
    JSONArray emptyValues = null;
    try {
      properValues = new JSONArray("[ 0, 100, 1, -4, 2, 7, 13 ]");
      properFloatValues = new JSONArray("[ 1.0, 480.0, 2.0, 4.0, -2.0, 360.4 ]");
      emptyValues = new JSONArray("[]");
    } catch (JSONException e) {
      Log.e(TAG, "failed to create JSONArray");
      fail();
    }

    List<?> nullRangeResult = null;
    List<?> emptyRangeResult = null;
    List<?> intRangeResult = null;
    List<?> longRangeResult = null;
    List<?> floatRangeResult = null;
    List<?> structRangeResult = null;
    List<?> otherRangeResult = null;

    try {
      nullRangeResult = (List<?>) getValuesFromJson.invoke(ext, "INT32", null);
      emptyRangeResult = (List<?>) getValuesFromJson.invoke(ext, "INT32", emptyValues);
      intRangeResult = (List<?>) getValuesFromJson.invoke(ext, "INT32", properValues);
      longRangeResult = (List<?>) getValuesFromJson.invoke(ext, "INT64", properValues);
      floatRangeResult = (List<?>) getValuesFromJson.invoke(ext, "FLOAT", properFloatValues);
      structRangeResult = (List<?>) getValuesFromJson.invoke(ext, "STRUCT_FLAG", properValues);
      otherRangeResult = (List<?>) getValuesFromJson.invoke(ext, "UNKNOWN", properValues);
    } catch (Exception e) {
      e.printStackTrace();
      fail();
    }

    assertNull(nullRangeResult);
    assertNull(structRangeResult);
    assertNull(otherRangeResult);
    assertNotNull(emptyRangeResult);

    // [ 0, 100, 1, -4, 2, 7, 13 ]
    assertNotNull(intRangeResult);
    assertEquals(intRangeResult.get(0), 0);
    assertEquals(intRangeResult.get(1), 100);
    assertEquals(intRangeResult.get(2), 1);
    assertEquals(intRangeResult.get(3), -4);
    assertEquals(intRangeResult.get(4), 2);
    assertEquals(intRangeResult.get(5), 7);
    assertEquals(intRangeResult.get(6), 13);
    assertNotNull(longRangeResult);
    assertEquals(longRangeResult.get(0), 0L);
    assertEquals(longRangeResult.get(1), 100L);
    assertEquals(longRangeResult.get(2), 1L);
    assertEquals(longRangeResult.get(3), (long) -4);
    assertEquals(longRangeResult.get(4), 2L);
    assertEquals(longRangeResult.get(5), 7L);
    assertEquals(longRangeResult.get(6), 13L);
    // [ 1.0, 480.0, 2.0, 4.0, -2.0, 360.4 ]
    assertNotNull(floatRangeResult);
    assertEquals(floatRangeResult.get(0), 1.0F);
    assertEquals(floatRangeResult.get(1), 480.0F);
    assertEquals(floatRangeResult.get(2), 2.0F);
    assertEquals(floatRangeResult.get(3), 4.0F);
    assertEquals(floatRangeResult.get(4), -2.0F);
    assertEquals(floatRangeResult.get(5), 360.4F);
  }

  @Test
  public void getRangeFromJson_isCorrect() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    String codecName = "c2.qti.hevc.encoder";
    String mime = "video/hevc";
    QMediaCodecCapabilities ext = QMediaCodecCapabilities.createForCodec(codecName, mime, context);
    if (!queryCapsSupported && !isSupportedLegacyDevice) {
      Log.e(TAG,
          "Not a supported device for QMediaCodecCapabilities;"
              + " skipping test getRangeFromJSON_isCorrect");
      assertNull(ext);
      return;
    }
    assertNotNull(ext);

    Class<?>[] params = new Class[2];
    params[0] = String.class;
    params[1] = JSONArray.class;
    Method getRangeFromJson = null;
    try {
      getRangeFromJson = ext.getClass().getDeclaredMethod(
          "getRangeFromJson", String.class, JSONArray.class);
    } catch (NoSuchMethodException e) {
      // should not happen
      e.printStackTrace();
      fail();
    }
    assertNotNull(getRangeFromJson);
    getRangeFromJson.setAccessible(true);

    JSONArray properRange = null;
    JSONArray properFloatRange = null;
    JSONArray improperRange = null;
    JSONArray emptyRange = null;
    try {
      properRange = new JSONArray("[ 0, 100, 1, 4, 2 ]");
      properFloatRange = new JSONArray("[ 1.0, 480.0, 2.0, 4.0, 2.0 ]");
      improperRange = new JSONArray("[ 0, 100, 1, 4, 2, 13, 0 ]");
      emptyRange = new JSONArray("[]");
    } catch (JSONException e) {
      Log.e(TAG, "failed to create JSONArray");
      fail();
    }

    QMediaCodecCapabilities.ValueRange<?> nullRangeResult = null;
    QMediaCodecCapabilities.ValueRange<?> improperRangeResult = null;
    QMediaCodecCapabilities.ValueRange<?> emptyRangeResult = null;
    QMediaCodecCapabilities.ValueRange<?> intRangeResult = null;
    QMediaCodecCapabilities.ValueRange<?> longRangeResult = null;
    QMediaCodecCapabilities.ValueRange<?> floatRangeResult = null;
    QMediaCodecCapabilities.ValueRange<?> structRangeResult = null;
    QMediaCodecCapabilities.ValueRange<?> otherRangeResult = null;

    try {
      nullRangeResult = (QMediaCodecCapabilities.ValueRange<?>)
          getRangeFromJson.invoke(ext, "INT32", null);
      improperRangeResult = (QMediaCodecCapabilities.ValueRange<?>)
          getRangeFromJson.invoke(ext, "INT32", improperRange);
      emptyRangeResult = (QMediaCodecCapabilities.ValueRange<?>)
          getRangeFromJson.invoke(ext, "INT32", emptyRange);
      intRangeResult = (QMediaCodecCapabilities.ValueRange<?>)
          getRangeFromJson.invoke(ext, "INT32", properRange);
      longRangeResult = (QMediaCodecCapabilities.ValueRange<?>)
          getRangeFromJson.invoke(ext, "INT64", properRange);
      floatRangeResult = (QMediaCodecCapabilities.ValueRange<?>)
          getRangeFromJson.invoke(ext, "FLOAT", properFloatRange);
      structRangeResult = (QMediaCodecCapabilities.ValueRange<?>)
          getRangeFromJson.invoke(ext, "STRUCT_FLAG", properRange);
      otherRangeResult = (QMediaCodecCapabilities.ValueRange<?>)
          getRangeFromJson.invoke(ext, "UNKNOWN", properRange);
    } catch (Exception e) {
      e.printStackTrace();
      fail();
    }

    assertNull(nullRangeResult);
    assertNull(improperRangeResult);
    assertNull(emptyRangeResult);
    assertNull(structRangeResult);
    assertNull(otherRangeResult);

    // [ 0, 100, 1, 4, 2 ]
    assertNotNull(intRangeResult);
    assertEquals(intRangeResult.getMin(), 0);
    assertEquals(intRangeResult.getMax(), 100);
    assertEquals(intRangeResult.getStep(), 1);
    assertEquals(intRangeResult.getNum(), 4);
    assertEquals(intRangeResult.getDenom(), 2);
    assertNotNull(longRangeResult);
    assertEquals(longRangeResult.getMin(), 0L);
    assertEquals(longRangeResult.getMax(), 100L);
    assertEquals(longRangeResult.getStep(), 1L);
    assertEquals(longRangeResult.getNum(), 4L);
    assertEquals(longRangeResult.getDenom(), 2L);
    // [ 1.0, 480.0, 2.0, 4.0, 2.0 ]
    assertNotNull(floatRangeResult);
    assertEquals(floatRangeResult.getMin(), 1.0F);
    assertEquals(floatRangeResult.getMax(), 480.0F);
    assertEquals(floatRangeResult.getStep(), 2.0F);
    assertEquals(floatRangeResult.getNum(), 4.0F);
    assertEquals(floatRangeResult.getDenom(), 2.0F);
  }

  @Test
  public void getParameterType_isCorrect() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    String codecName = "c2.qti.hevc.encoder";
    String mime = "video/hevc";
    QMediaCodecCapabilities ext = QMediaCodecCapabilities.createForCodec(codecName, mime, context);
    if (!queryCapsSupported && !isSupportedLegacyDevice) {
      Log.e(TAG,
          "Not a supported device for QMediaCodecCapabilities;"
              + " skipping test getParameterType_isCorrect");
      assertNull(ext);
      return;
    }
    assertNotNull(ext);
    MediaCodec codec = null;
    try {
      codec = MediaCodec.createByCodecName(codecName);
    } catch (Exception e) {
      Log.e(TAG, "MediaCodec.createByCodecName threw exception");
      e.printStackTrace();
      fail();
    }
    assertNotNull(codec);

    Class<?>[] params = new Class[1];
    params[0] = String.class;
    Method getParameterType = null;
    try {
      getParameterType = ext.getClass().getDeclaredMethod("getParameterType", params);
    } catch (NoSuchMethodException e) {
      // should not happen
      e.printStackTrace();
      fail();
    }
    assertNotNull(getParameterType);
    getParameterType.setAccessible(true);

    List<String> vendorParams = codec.getSupportedVendorParameters();
    for (String param : vendorParams) {
      int type = -1;
      try {
        type = (int) getParameterType.invoke(ext, param);
      } catch (Exception e) {
        e.printStackTrace();
        fail();
      }
      if (!param.equalsIgnoreCase("vendor.qti-ext-caps-query-input.value")
          && !param.equalsIgnoreCase("vendor.qti-ext-caps-query-output.value")) {
        int expectedType = codec.getParameterDescriptor(param).getType();
        if (type != 0) {
          // supported param
          Log.d(TAG,
              "param " + param + " with type " + type + " (expected type #" + expectedType + ")");
          assertEquals(type, expectedType);
        } else {
          // type returned TYPE_NULL in paramRangeMap so unsupported param
          Log.e(TAG, "param " + param + "with expected type # " + expectedType
              + " is not supported");
        }
      }
    }
  }

  @Test
  public void parseParamRangeFromJson_isCorrect() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    String codecName = "c2.qti.hevc.decoder";
    String mime = "video/hevc";
    QMediaCodecCapabilities ext = QMediaCodecCapabilities.createForCodec(codecName, mime, context);
    if (!queryCapsSupported && !isSupportedLegacyDevice) {
      Log.e(TAG,
          "Not a supported device for QMediaCodecCapabilities;"
              + " skipping test parseParamRangeFromJson_isCorrect");
      assertNull(ext);
      return;
    }
    assertNotNull(ext);
    MediaCodec codec = null;
    try {
      codec = MediaCodec.createByCodecName(codecName);
    } catch (Exception e) {
      Log.e(TAG, "MediaCodec.createByCodecName threw exception");
      e.printStackTrace();
      fail();
    }
    assertNotNull(codec);

    // since the vendor.qti-ext-dec-frame-rate.value param is normally a UINT
    // it should be parsed to a RANGE type for 0-Integer.MAX_VALUE
    // (custom setting min of range to 0 for unsigned)
    // verify that this is true first
    QMediaCodecCapabilities.SupportedValues<?> origRangeParam =
        ext.getParameterRangeInteger("vendor.qti-ext-dec-frame-rate.value");
    assertNotNull(origRangeParam);
    assertEquals(origRangeParam.getDataType(),
        QMediaCodecCapabilities.SupportedValues.DataType.INTEGER);
    assertEquals(origRangeParam.getType(), QMediaCodecCapabilities.SupportedValues.Type.RANGE);
    assertNull(origRangeParam.getValues());
    assertNull(origRangeParam.getFlags());
    assertNotNull(origRangeParam.getRange());

    Class<?>[] params = new Class[2];
    params[0] = String.class;
    params[1] = JSONObject.class;
    Method parseNestedParamRangeFromJson = null;
    try {
      parseNestedParamRangeFromJson =
          ext.getClass().getDeclaredMethod("parseNestedParamRangeFromJson", params);
    } catch (NoSuchMethodException e) {
      // should not happen
      e.printStackTrace();
      fail();
    }
    assertNotNull(parseNestedParamRangeFromJson);
    parseNestedParamRangeFromJson.setAccessible(true);

    // here we set vendor.qti-ext-dec-frame-rate.value  to INT32 in this JSON
    // so in the func it should set it to ANY instead of RANGE type in the internal map
    String json =
        "{\"vendor.qti-ext-dec-frame-rate\":"
            + "{\"value\":{\"datatype\":\"INT32\",\"values\":[],\"valuestype\":\"ANY\"}}}";

    JSONObject validJsonParam = null;
    JSONObject emptyJsonParam = null;
    try {
      validJsonParam = new JSONObject(json);
      emptyJsonParam = new JSONObject("{}");
    } catch (JSONException e) {
      e.printStackTrace();
      fail();
    }

    boolean validSucceeded = false;
    boolean emptySucceeded = false;
    boolean nullSucceeded = false;
    try {
      validSucceeded = (boolean) parseNestedParamRangeFromJson.invoke(ext, null, validJsonParam);
      emptySucceeded = (boolean) parseNestedParamRangeFromJson.invoke(ext, null, emptyJsonParam);
      nullSucceeded = (boolean) parseNestedParamRangeFromJson.invoke(ext, null, null);
    } catch (Exception e) {
      e.printStackTrace();
      fail();
    }

    assertFalse(emptySucceeded);
    assertFalse(nullSucceeded);
    assertTrue(validSucceeded);

    QMediaCodecCapabilities.SupportedValues<?> changedRangeParam =
        ext.getParameterRangeInteger("vendor.qti-ext-dec-frame-rate.value");
    assertNotNull(changedRangeParam);
    assertEquals(changedRangeParam.getDataType(),
        QMediaCodecCapabilities.SupportedValues.DataType.INTEGER);
    assertEquals(changedRangeParam.getType(), QMediaCodecCapabilities.SupportedValues.Type.ANY);
    assertNull(changedRangeParam.getValues());
    assertNull(changedRangeParam.getFlags());
    assertNull(changedRangeParam.getRange());
  }

  // test for timing main functions in QMediaCodecCapabilities
  @Test
  public void logFunctionCallTimings() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    String codecName = "c2.qti.hevc.decoder";
    final String mime = "video/hevc";
    long start1 = System.nanoTime();
    QMediaCodecCapabilities ext = QMediaCodecCapabilities.createForCodec(codecName, mime, context);
    long end1 = System.nanoTime();
    Log.d(TAG, "Time taken by createForCodec in nanoseconds " + (end1 - start1));
    String codecNameEnc = "c2.qti.hevc.encoder";
    QMediaCodecCapabilities extEnc = QMediaCodecCapabilities.createForCodec(codecNameEnc, mime, context);
    if (!queryCapsSupported && !isSupportedLegacyDevice) {
      Log.e(TAG,
          "Not a supported device for QMediaCodecCapabilities;"
              + " skipping test logFunctionCallTimings");
      assertNull(ext);
      assertNull(extEnc);
      return;
    }
    assertNotNull(ext);
    assertNotNull(extEnc);

    // vendor.qti-ext-dec-info-crop.height integer type range
    start1 = System.nanoTime();
    QMediaCodecCapabilities.SupportedValues<Integer> intRangeParam =
        ext.getParameterRangeInteger("vendor.qti-ext-dec-info-crop.height");
    end1 = System.nanoTime();
    Log.d(TAG, "Time taken by getParameterRangeInteger in nanoseconds " + (end1 - start1));
    assertNotNull(intRangeParam);

    // vendor.qti-ext-enc-roiinfo.timestamp long type range
    start1 = System.nanoTime();
    QMediaCodecCapabilities.SupportedValues<Long> longRangeParam =
        extEnc.getParameterRangeLong("vendor.qti-ext-enc-roiinfo.timestamp");
    end1 = System.nanoTime();
    Log.d(TAG, "Time taken by getParameterRangeLong in nanoseconds " + (end1 - start1));
    assertNotNull(longRangeParam);

    // vendor.qti-ext-dec-output-render-frame-rate.value float range
    start1 = System.nanoTime();
    QMediaCodecCapabilities.SupportedValues<Float> floatRangeParam =
        ext.getParameterRangeFloat("vendor.qti-ext-dec-output-render-frame-rate.value");
    end1 = System.nanoTime();
    Log.d(TAG, "Time taken by getParameterRangeFloat in nanoseconds " + (end1 - start1));
    assertNotNull(floatRangeParam);

    // vendor.qti-ext-enc-roiinfo.rect-payload string type range
    start1 = System.nanoTime();
    QMediaCodecCapabilities.SupportedValues<Integer> StringRangeParam =
        extEnc.getParameterRangeForString("vendor.qti-ext-enc-roiinfo.rect-payload");
    end1 = System.nanoTime();
    Log.d(TAG, "Time taken by getParameterRangeForString in nanoseconds " + (end1 - start1));
    assertNotNull(StringRangeParam);

    // vendor.qti-ext-dec-info-misr.value byte_buffer type range
    start1 = System.nanoTime();
    QMediaCodecCapabilities.SupportedValues<Integer> byteBufferRangeParam =
        ext.getParameterRangeForByteBuffer("vendor.qti-ext-dec-info-misr.value");
    end1 = System.nanoTime();
    Log.d(TAG, "Time taken by getParameterRangeForByteBuffer in nanoseconds " + (end1 - start1));
    assertNotNull(byteBufferRangeParam);

    start1 = System.nanoTime();
    Map<String, QMediaCodecCapabilities.SupportedValues> supportedParamRanges =
        ext.getSupportedParameterRanges();
    end1 = System.nanoTime();
    Log.d(TAG, "Time taken by getSupportedParameterRanges in nanoseconds " + (end1 - start1));
    assertNotNull(supportedParamRanges);
  }

  // test for timing main functions in QMediaCodecCapabilities
  @Test
  public void logFunctionCallTimingsInputCodec() {
    final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    final String codecNameEnc = "c2.qti.hevc.encoder";
    final String codecNameDec = "c2.qti.hevc.decoder";
    final String mime = "video/hevc";
    final int width = 640;
    final int height = 272;
    MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
    format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
    format.setInteger(MediaFormat.KEY_BIT_RATE, 10000000);
    format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);

    MediaCodec encCodec = null;
    MediaCodec decCodec = null;
    try {
      encCodec = MediaCodec.createByCodecName(codecNameEnc);
      encCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
      decCodec = MediaCodec.createByCodecName(codecNameDec);
      decCodec.configure(format, null, null, 0);
    } catch (IOException e) {
      Log.e(TAG, "failed to create codecs for testing (logFunctionCallTimingsInputCodec)");
      fail();
    }

    // encoder (should be same as c2.qti.hevc.encoder)
    long start1 = System.nanoTime();
    QMediaCodecCapabilities extEnc = QMediaCodecCapabilities.createForCodec(encCodec, mime, context);
    long end1 = System.nanoTime();
    Log.d(TAG, "Time taken by createForCodec(encCodec) in nanoseconds (encoder) " + (end1 - start1));
    start1 = System.nanoTime();
    QMediaCodecCapabilities extDec = QMediaCodecCapabilities.createForCodec(decCodec, mime, context);
    end1 = System.nanoTime();
    Log.d(TAG, "Time taken by createForCodec(encCodec) in nanoseconds (decoder) " + (end1 - start1));
    if (!queryCapsSupported && !isSupportedLegacyDevice) {
      Log.e(TAG,
          "Not a supported device for QMediaCodecCapabilities;"
              + " skipping test logFunctionCallTimingsInputCodec");
      assertNull(extEnc);
      assertNull(extDec);
      return;
    }
    assertNotNull(extEnc);
    assertNotNull(extDec);

    // vendor.qti-ext-dec-info-crop.height integer type range
    start1 = System.nanoTime();
    QMediaCodecCapabilities.SupportedValues<Integer> intRangeParam =
        extDec.getParameterRangeInteger("vendor.qti-ext-dec-info-crop.height");
    end1 = System.nanoTime();
    Log.d(TAG, "Time taken by getParameterRangeInteger in nanoseconds " + (end1 - start1));
    assertNotNull(intRangeParam);

    // vendor.qti-ext-enc-roiinfo.timestamp long type range
    start1 = System.nanoTime();
    QMediaCodecCapabilities.SupportedValues<Long> longRangeParam =
        extEnc.getParameterRangeLong("vendor.qti-ext-enc-roiinfo.timestamp");
    end1 = System.nanoTime();
    Log.d(TAG, "Time taken by getParameterRangeLong in nanoseconds " + (end1 - start1));
    assertNotNull(longRangeParam);

    // vendor.qti-ext-dec-output-render-frame-rate.value float range
    start1 = System.nanoTime();
    QMediaCodecCapabilities.SupportedValues<Float> floatRangeParam =
        extDec.getParameterRangeFloat("vendor.qti-ext-dec-output-render-frame-rate.value");
    end1 = System.nanoTime();
    Log.d(TAG, "Time taken by getParameterRangeFloat in nanoseconds " + (end1 - start1));
    assertNotNull(floatRangeParam);

    // vendor.qti-ext-enc-roiinfo.rect-payload string type range
    start1 = System.nanoTime();
    QMediaCodecCapabilities.SupportedValues<Integer> stringRangeParam =
        extEnc.getParameterRangeForString("vendor.qti-ext-enc-roiinfo.rect-payload");
    end1 = System.nanoTime();
    Log.d(TAG, "Time taken by getParameterRangeForString in nanoseconds " + (end1 - start1));
    assertNotNull(stringRangeParam);

    // vendor.qti-ext-dec-info-misr.value byte_buffer type range
    start1 = System.nanoTime();
    QMediaCodecCapabilities.SupportedValues<Integer> byteBufferRangeParam =
        extDec.getParameterRangeForByteBuffer("vendor.qti-ext-dec-info-misr.value");
    end1 = System.nanoTime();
    Log.d(TAG, "Time taken by getParameterRangeForByteBuffer in nanoseconds " + (end1 - start1));
    assertNotNull(byteBufferRangeParam);

    start1 = System.nanoTime();
    Map<String, QMediaCodecCapabilities.SupportedValues> supportedParamRanges =
        extEnc.getSupportedParameterRanges();
    end1 = System.nanoTime();
    Log.d(TAG, "Time taken by getSupportedParameterRanges in nanoseconds (encoder)" + (end1 - start1));
    assertNotNull(supportedParamRanges);
    assertFalse(supportedParamRanges.isEmpty());
    encCodec.release();

    start1 = System.nanoTime();
    supportedParamRanges =
        extDec.getSupportedParameterRanges();
    end1 = System.nanoTime();
    Log.d(TAG, "Time taken by getSupportedParameterRanges in nanoseconds (decoder)" + (end1 - start1));
    assertNotNull(supportedParamRanges);
    assertFalse(supportedParamRanges.isEmpty());
  }

  // parseNestedJsonParam can assume this works
  // since parsing a nested param using parseParamRangeFromJson works

  // getParameterRange can assume this works since getParameterRangeXxx for each type works

  // populateRangeMap can assume this works since createForCodec uses it every time

  @Test
  public void test_QMediaCodecCapabilities_isProSightSupported() {
    MediaCodec codec;
    try {
      codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    assertNotNull(codec);
    boolean isProSightSupported = QMediaCodecCapabilities.isProSightSupported(codec);
    // TODO: change to assertTrue when vendor change is ready
    assertFalse(isProSightSupported);
  }

  @Test
  public void test_QMediaCodecCapabilities_enableProSight() {
    // TODO: make a complete test case
    MediaFormat format = new MediaFormat();
    MediaFormat newFormat = QMediaCodecCapabilities.enableProSight(format);
    assertNotNull(newFormat);
  }

  private void validateExtensionName(String ext) {
    assertNotNull(ext);
    assertNotEquals("", ext);
  }

  @Test
  public void test_QMediaExtensions_extensions() {
    validateExtensionName(QMediaExtensions.KEY_INIT_QP_I_FRAME_ENABLE);
    validateExtensionName(QMediaExtensions.KEY_INIT_QP_P_FRAME_ENABLE);
    validateExtensionName(QMediaExtensions.KEY_INIT_QP_B_FRAME_ENABLE);
    validateExtensionName(QMediaExtensions.KEY_INIT_QP_I_FRAME);
    validateExtensionName(QMediaExtensions.KEY_INIT_QP_P_FRAME);
    validateExtensionName(QMediaExtensions.KEY_INIT_QP_B_FRAME);
    validateExtensionName(QMediaExtensions.KEY_ROI_INFO_TYPE);
    validateExtensionName(QMediaExtensions.KEY_ROI_RECT_INFO);
    validateExtensionName(QMediaExtensions.KEY_ROI_RECT_INFO_EXT);
    validateExtensionName(QMediaExtensions.KEY_ROI_INFO_TIMESTAMP);
    validateExtensionName(QMediaExtensions.KEY_ADV_QP_BITRATE_MODE);
    validateExtensionName(QMediaExtensions.KEY_ADV_QP_FRAME_QP_VALUE);
    validateExtensionName(QMediaExtensions.KEY_RESYNC_MARKER_SIZE);
    validateExtensionName(QMediaExtensions.KEY_SLICE_SPACING_SIZE);
    validateExtensionName(QMediaExtensions.KEY_LTR_MAX_FRAMES);
    validateExtensionName(QMediaExtensions.KEY_LTR_MARK_FRAME);
    validateExtensionName(QMediaExtensions.KEY_LTR_USE_FRAME);
    validateExtensionName(QMediaExtensions.KEY_LTR_RESPONSE);
    validateExtensionName(QMediaExtensions.KEY_INIT_QP_I_FRAME_ENABLE);
    validateExtensionName(QMediaExtensions.KEY_INIT_QP_I_FRAME_ENABLE);
    validateExtensionName(QMediaExtensions.KEY_INIT_QP_I_FRAME_ENABLE);
    validateExtensionName(QMediaExtensions.KEY_INIT_QP_I_FRAME_ENABLE);
    validateExtensionName(QMediaExtensions.KEY_INIT_QP_I_FRAME_ENABLE);
    validateExtensionName(QMediaExtensions.KEY_ROI_MAP_MB_SIDE_LENGTH);
    validateExtensionName(QMediaExtensions.KEY_ROI_MAP_MB_QP_BIAS_MAP);
  }

  @Test
  public void test_QMediaExtensions_ProSightExtensionRange() {
    assertEquals(QMediaExtensions.ProSightExtensionRange.DEFAULT.getValue(), 0);
    assertEquals(QMediaExtensions.ProSightExtensionRange.PROSIGHT.getValue(), 1);
  }
}