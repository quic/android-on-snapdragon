# Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
# SPDX-License-Identifier: Apache-2.0

package com.example.instantzoomsamplecode;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.graphics.ColorSpace;
import android.graphics.ColorSpace.Named;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.ColorSpaceProfiles;
import android.hardware.camera2.params.DynamicRangeProfiles;
import android.hardware.camera2.params.InputConfiguration;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import com.android.internal.graphics.cam.Cam;

import java.util.ArrayList;
import java.util.Arrays;
import android.util.ArraySet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

public class MainActivity extends Activity {

    private static final SparseIntArray ORIENTATION = new SparseIntArray();
    private static final String TAG = "MainActivity";
    public static final HashMap<String, Integer> KEY_IMAGE_FORMAT_INDEX = new HashMap<String, Integer>();

    static {
        ORIENTATION.append(Surface.ROTATION_0, 90);
        ORIENTATION.append(Surface.ROTATION_90, 0);
        ORIENTATION.append(Surface.ROTATION_180, 270);
        ORIENTATION.append(Surface.ROTATION_270, 180);
        KEY_IMAGE_FORMAT_INDEX.put("0", ImageFormat.JPEG);
        KEY_IMAGE_FORMAT_INDEX.put("1", ImageFormat.HEIC);
        KEY_IMAGE_FORMAT_INDEX.put("3", ImageFormat.JPEG_R);
    }

    private String mCameraId;
    private Size mPreviewSize;
    private HandlerThread mCameraThread;
    private Handler mCameraHandler;
    private CameraDevice mCameraDevice;
    private TextureView mTextureView;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CameraCaptureSession mCameraCaptureSession;
    CameraManager mCameraManager;
    private ArrayList<CameraCharacteristics> mCharacteristics;
    private Button mCaptureButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        mTextureView = (TextureView) findViewById(R.id.textureView);
        mCaptureButton = (Button) findViewById(R.id.photoButton);
        if (mCaptureButton != null) {
            mCaptureButton.setVisibility(View.GONE);
        }
        mCharacteristics = new ArrayList<>();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startThread();
        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (!mTextureView.isAvailable()) {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        } else {
            startPreview();
        }
    }

    private void initCharacteristics(){
        if(mCharacteristics.size() >0) {
            return;
        }
        CameraManager manager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIdList = manager.getCameraIdList();
            boolean isFirstBackCameraId = true;
            boolean isRearCameraPresent = false;
            Log.i(TAG,"cameraIdList size ="+cameraIdList.length);
            for (int i = 0; i < cameraIdList.length; i++) {
                String cameraId = cameraIdList[i];
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);
                int facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    Log.i(TAG,"facing front id ="+ cameraId);
                }
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    isRearCameraPresent = true;
                    if (isFirstBackCameraId) {
                        isFirstBackCameraId = false;
                    }
                }
                mCharacteristics.add(i, characteristics);
            }
        } catch (CameraAccessException e) {
            Log.e(TAG,e.toString());
        }
    }

    public Set<ColorSpace.Named> getAvailableColorSpacesChecked(int cameraId, int imageFormat) {
        ColorSpaceProfiles colorSpaceProfiles = mCharacteristics.get(cameraId).get(
                CameraCharacteristics.REQUEST_AVAILABLE_COLOR_SPACE_PROFILES);
        Log.v(TAG, " getAvailableColorSpacesChecked imageFormat :" + imageFormat);
        if (colorSpaceProfiles == null) {
            return new ArraySet<ColorSpace.Named>();
        }
        Set<ColorSpace.Named> colorSpaces = colorSpaceProfiles.getSupportedColorSpaces(imageFormat);
        Log.v(TAG, " getAvailableColorSpacesChecked colorSpaces :" + colorSpaces);
        Set<ColorSpace.Named> colorSpacesDR = colorSpaceProfiles.getSupportedColorSpacesForDynamicRange(imageFormat, DynamicRangeProfiles.HLG10);
        Log.v(TAG, " getAvailableColorSpacesChecked colorSpacesDR :" + colorSpacesDR);
        return colorSpaces;
    }
    public ColorSpaceProfiles getColorSpaceProfiles(int cameraId) {
        ColorSpaceProfiles colorSpaceProfiles= mCharacteristics.get(cameraId).get(
                CameraCharacteristics.REQUEST_AVAILABLE_COLOR_SPACE_PROFILES);
        Log.v(TAG, " getColorSpaceProfiles colorSpaceProfiles :" + colorSpaceProfiles);
        return colorSpaceProfiles;
    }

    private void startThread() {
        mCameraThread = new HandlerThread("CameraBackground");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());
    }

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            setupCameraParams(width, height);
            openCamera();

            initCharacteristics();
            int cameraID = Integer.parseInt(mCameraId);
            getColorSpaceProfiles(cameraID);
            Log.v(TAG, "cameraID :" + cameraID);
            String format = "3";
            if (format != null) {
                getAvailableColorSpacesChecked(cameraID, KEY_IMAGE_FORMAT_INDEX.get(format));
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private void setupCameraParams(int width, int height) {
        try {
            for (String cameraId : mCameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT)
                    continue;
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null)
                    continue;
                mPreviewSize = getOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height);
                mCameraId = cameraId;
                break;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private Size getOptimalSize(Size[] sizeMap, int width, int height) {
        List<Size> sizeList = new ArrayList<>();
        for (Size option : sizeMap) {
            if (width > height) {
                if (option.getWidth() > width && option.getHeight() > height) {
                    sizeList.add(option);
                }
            } else {
                if (option.getWidth() > height && option.getHeight() > width) {
                    sizeList.add(option);
                }
            }
        }
        if (sizeList.size() > 0) {
            return Collections.min(sizeList, new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getWidth() * rhs.getHeight());
                }
            });
        }
        return sizeMap[0];
    }


    private void openCamera() {
        try {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            mCameraManager.openCamera(mCameraId, mStateCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            Log.i(TAG, "  onOpened  ");
            mCameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            Log.i(TAG, "  onDisconnected  ");
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            Log.i(TAG, "  onError  error :" + error);
            camera.close();
            mCameraDevice = null;
        }
    };

    private void startPreview() {
        Log.i(TAG, "  startPreview  ");
        SurfaceTexture mSurfaceTexture = mTextureView.getSurfaceTexture();
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface previewSurface = new Surface(mSurfaceTexture);
        List<OutputConfiguration> outputConfigurations = new ArrayList<OutputConfiguration>();
        try {
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(previewSurface);
            outputConfigurations.add(new OutputConfiguration(previewSurface));

            CameraCaptureSession.StateCallback captureSessionCallback = new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    Log.i(TAG, "capturesession - onConfigured ");
                    try {
                        mCameraCaptureSession = session;
                        mCameraCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mCameraHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    Log.i(TAG, "cameracapturesession - onConfigureFailed");
                }

                @Override
                public void onClosed(CameraCaptureSession session) {
                    Log.i(TAG, "cameracapturesession - onClosed");
                }
            };

            createCaptureSessionWithSessionConfiguration(mCameraDevice, 0, outputConfigurations,
                    null, captureSessionCallback, mCameraHandler, mPreviewRequestBuilder);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private class HandlerExecutor implements Executor {
        private final Handler ihandler;

        public HandlerExecutor(Handler handler) {
            ihandler = handler;
        }

        @Override
        public void execute(Runnable runCmd) {
            ihandler.post(runCmd);
        }
    }

    private void createCaptureSessionWithSessionConfiguration(CameraDevice camera, int opMode,
                                                              List<OutputConfiguration> outConfigurations,
                                                              InputConfiguration inputConfig,
                                                              CameraCaptureSession.StateCallback listener,
                                                              Handler handler,
                                                              CaptureRequest.Builder initialRequest) {
        Log.i(TAG, "  createCaptureSessionWithSessionConfiguration  ");
        SessionConfiguration sessionConfig = new SessionConfiguration(opMode, outConfigurations,
                new HandlerExecutor(handler), listener);
        sessionConfig.setSessionParameters(initialRequest.build());
        if (true) {
            Log.i(TAG, "  setColorSpace ColorSpace.Named.DISPLAY_P3 ");
            sessionConfig.setColorSpace(ColorSpace.Named.DISPLAY_P3);
        } else {
            Log.i(TAG, "  setColorSpace ColorSpace.Named.SRGB ");
            sessionConfig.setColorSpace(ColorSpace.Named.SRGB);
        }
        if (inputConfig != null) {
            sessionConfig.setInputConfiguration(inputConfig);
        }
        try{
            boolean supported = camera.isSessionConfigurationSupported(sessionConfig);
            Log.i(TAG, " isSessionConfigurationSupported :" + supported);
        } catch (CameraAccessException | IllegalArgumentException | NullPointerException e) {
            Log.w(TAG, " check isSessionConfigurationSupported sessionConfig error ="+ e);
        }
        try{
            Log.i(TAG, "  createCaptureSession called ");
            camera.createCaptureSession(sessionConfig);
        } catch (CameraAccessException e) {
            Log.e(TAG, " error:",e);
        }
    }

    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {

        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
        }
    };

    public void applyZoom(View view){
        updateZoomSmooth(1,2,10);
    }

    public void updateZoomSmooth(float from, float to, int frame) {
        float delta = (to - from) / frame;
        float zoom = from;
        for (int i = 0; i < frame; i++) {
            zoom = zoom + delta;
            applyZoomRatio(mPreviewRequestBuilder, zoom);
        }
    }

    private void applyZoomRatio(CaptureRequest.Builder request, float zoomValue) {
        try {
            Log.d(TAG,"applyzoomratio="+zoomValue);
            request.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomValue);
            mCameraCaptureSession.capture(mPreviewRequestBuilder
                    .build(), mCaptureCallback, mCameraHandler);
        } catch(IllegalArgumentException|NoSuchFieldError| CameraAccessException e) {
            Log.w(TAG, " apply zoom ratio failed");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }

        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }
}
