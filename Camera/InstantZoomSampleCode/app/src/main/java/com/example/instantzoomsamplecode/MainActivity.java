# Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
# SPDX-License-Identifier: Apache-2.0

package com.example.instantzoomsamplecode;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
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

import com.android.internal.graphics.cam.Cam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {

    private static final SparseIntArray ORIENTATION = new SparseIntArray();
    private static final String TAG = "MainActivity";

    static {
        ORIENTATION.append(Surface.ROTATION_0, 90);
        ORIENTATION.append(Surface.ROTATION_90, 0);
        ORIENTATION.append(Surface.ROTATION_180, 270);
        ORIENTATION.append(Surface.ROTATION_270, 180);
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        mTextureView = (TextureView) findViewById(R.id.textureView);
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
            mCameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }
    };

    private void startPreview() {
        SurfaceTexture mSurfaceTexture = mTextureView.getSurfaceTexture();
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface previewSurface = new Surface(mSurfaceTexture);
        try {
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(previewSurface);
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        mCameraCaptureSession = session;
                        if(isInStantZoomSupported()){
                            applyInStantZoom(mPreviewRequestBuilder);
                        }
                        mCameraCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mCameraHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {

                }
            }, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {

        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            //get the value of setting override
            Log.i(TAG,"setting override:" + result.get(CaptureResult.CONTROL_SETTINGS_OVERRIDE));
            //get zoom value
            Log.i(TAG,"zoom value:" + result.get(CaptureResult.CONTROL_ZOOM_RATIO));
        }
    };

    public boolean isInStantZoomSupported() {
        try {
            if (mCameraManager.getCameraCharacteristics(mCameraId) != null) {
                int[] availableOverrides = mCameraManager.getCameraCharacteristics(mCameraId).get(CameraCharacteristics
                        .CONTROL_AVAILABLE_SETTINGS_OVERRIDES);
                if (availableOverrides == null) {
                    return false;
                }
            }
        }catch(IllegalArgumentException | NoSuchFieldError |CameraAccessException e) {
            Log.w(TAG,"exception  e= "+e);
        }
        return true;
    }

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

    private void applyInStantZoom(CaptureRequest.Builder request) {
        try {
            if (true) {
                request.set(CaptureRequest.CONTROL_SETTINGS_OVERRIDE,
                        CameraMetadata.CONTROL_SETTINGS_OVERRIDE_ZOOM);
            } else {
                request.set(CaptureRequest.CONTROL_SETTINGS_OVERRIDE,
                        CameraMetadata.CONTROL_SETTINGS_OVERRIDE_OFF);
            }
        } catch (IllegalArgumentException | NoSuchFieldError e) {
            Log.v(TAG, " applyInStantZoom didn`t exist CONTROL_SETTINGS_OVERRIDE");
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
