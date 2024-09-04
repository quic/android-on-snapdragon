// Copyright (c) 2024, Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
package com.example.lowlightboost;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class MainActivity extends Activity {

    private static final SparseIntArray ORIENTATION = new SparseIntArray();
    private static final String TAG = "MainActivity";
    private boolean mShouldRequestCameraPermission;
    private boolean mFlagHasCameraPermission;
    String[] permissionsToRequest = new String[1];
    double PreviewRatio = (double) 16 / 9;
    private static final MeteringRectangle[] ZERO_WEIGHT_3A_REGION = new MeteringRectangle[]{
            new MeteringRectangle(0, 0, 0, 0, 0)};

    static {
        ORIENTATION.append(Surface.ROTATION_0, 90);
        ORIENTATION.append(Surface.ROTATION_90, 0);
        ORIENTATION.append(Surface.ROTATION_180, 270);
        ORIENTATION.append(Surface.ROTATION_270, 180);
    }

    private String mCameraId;
    private Size mPreviewSize;
    private Size mPictureSize;
    private HandlerThread mCameraThread;
    private Handler mCameraHandler;
    private CameraDevice mCameraDevice;
    private AutoFitTextureView mTextureView;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CameraCaptureSession mCameraCaptureSession;
    private CameraManager mCameraManager;
    private ImageReader mImageReader;
    private int mCaptureState;
    private TextView mLowLightText;
    private static final int STATE_PREVIEW = 0;
    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_AF_LOCK = 1;
    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;
    /**
     * Camera state: Waiting for the exposure state to be locked.
     */
    private static final int STATE_WAITING_AE_LOCK = 3;
    /**
     * Camera state: Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
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
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            setupCameraParams();
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
    private final CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {

        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            updateCaptureStateMachine(result);
            MainActivity.this.runOnUiThread(() -> {
                updateLowLightText(result);
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(!hasCameraPermission()){
            return;
        }
        setLayout();
    }

    private void setLayout() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        mTextureView = findViewById(R.id.textureView);
        mLowLightText = findViewById(R.id.lowlightboost_text);
    }

    private boolean hasCameraPermission() {
        if (checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest[0] = Manifest.permission.CAMERA;
            Log.i(TAG, "requestPermissions1111111=");
            mShouldRequestCameraPermission = true;
            mFlagHasCameraPermission = false;
            requestPermissions(permissionsToRequest, 1);
        } else {
            mFlagHasCameraPermission = true;
        }
        Log.i(TAG, "hasCameraPermission mFlagHasCameraPermission="+mFlagHasCameraPermission);
        return mFlagHasCameraPermission;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        if (grantResults.length < 1) {
            return;
        }
        if (mShouldRequestCameraPermission) {
            if ((grantResults.length >= 1) &&
                    (grantResults[0] ==
                            PackageManager.PERMISSION_GRANTED)) {
                mFlagHasCameraPermission = true;
                mShouldRequestCameraPermission = false;
                setLayout();
            } else {
                mShouldRequestCameraPermission = true;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!hasCameraPermission()) {
            return;
        }
        mTextureView.setVisibility(View.VISIBLE);
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

    private void setupCameraParams() {
        try {
            for (String cameraId : mCameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT)
                    continue;
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null)
                    continue;
                mPreviewSize = getOptimalSize(map.getOutputSizes(SurfaceTexture.class),PreviewRatio,true);
                mTextureView.setAspectRatio(mPreviewSize.getHeight(),mPreviewSize.getWidth());
                mPictureSize = getOptimalSize(map.getOutputSizes(ImageFormat.JPEG),PreviewRatio,false);
                setupImageReader();
                mCameraId = cameraId;
                break;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private DisplayMetrics getDisplaySize() {
        DisplayMetrics metrics =new     DisplayMetrics();

    WindowManager wm = (WindowManager)
            this.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().
    getMetrics(metrics);
        return metrics;
}



    public  Size getOptimalSize(Size[] sizes, double targetRatio,boolean isPreviewSize) {
        // TODO(andyhuibers): Don't hardcode this but use device's measurements.
        DisplayMetrics metrics = getDisplaySize();
        // Use a very small tolerance because we want an exact match.
        final double ASPECT_TOLERANCE = 0.01;
        if (sizes == null) return null;

        int optimalSizeIndex = -1;
        int targetHeight = Math.min(metrics.heightPixels, metrics.widthPixels);
        double minDiff = targetHeight;
        // Try to find an size match aspect ratio and size
        for (int i = 0; i < sizes.length; i++) {
            double ratio = (double) sizes[i].getWidth() / sizes[i].getHeight();
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            else if(!isPreviewSize){
                optimalSizeIndex = i;
                break;
            }

            if ( sizes[i].getHeight() > targetHeight) continue;
                double heightDiff = Math.abs(sizes[i].getHeight() - targetHeight);
                if (heightDiff < minDiff) {
                    optimalSizeIndex = i;
                    minDiff = Math.abs(sizes[i].getHeight() - targetHeight);
                } else if (heightDiff == minDiff) {
                    // Prefer resolutions smaller-than-display when an equally close
                    // larger-than-display resolution is available
                    if (sizes[i].getHeight() < targetHeight) {
                        optimalSizeIndex = i;
                        minDiff = heightDiff;
                    }
                }

        }
        // Cannot find the one match the aspect ratio. This should not happen.
        // Ignore the requirement.
        if (optimalSizeIndex == -1) {
            Log.w(TAG, "No  size match the aspect ratio");
            minDiff = Double.MAX_VALUE;
            for (int i = 0; i < sizes.length; i++) {
                double ratio = (double) sizes[i].getWidth() / sizes[i].getHeight();
                if (Math.abs(ratio - targetRatio) < minDiff) {
                    optimalSizeIndex = i;
                    minDiff = Math.abs(ratio - targetRatio);
                }
            }
        }
        return sizes[optimalSizeIndex];
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

    private void startPreview() {
        SurfaceTexture mSurfaceTexture = mTextureView.getSurfaceTexture();
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface previewSurface = new Surface(mSurfaceTexture);
        if (mCameraDevice == null) {
            setupCameraParams();
            openCamera();
            return;
        }
        try {
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(previewSurface);
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        mCameraCaptureSession = session;
                        applyCommonSettings(mPreviewRequestBuilder);
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, ZERO_WEIGHT_3A_REGION);
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, ZERO_WEIGHT_3A_REGION);
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
                        if(isLowLightBoostSupported()) {
                            applyLowLightBoost(mPreviewRequestBuilder);
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
        } catch (CameraAccessException | IllegalArgumentException | NoSuchFieldError e) {
            e.printStackTrace();
        }
    }
    private void applyCommonSettings(CaptureRequest.Builder builder){
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
    }

    public void capture(boolean flashOn) {
        try {
            final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.addTarget(mImageReader.getSurface());
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATION.get(rotation));
            if(flashOn) {
                captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);//This is setted when flash on
            }
            CameraCaptureSession.CaptureCallback CaptureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    unLockFocus();
                }
            };
            mCameraCaptureSession.stopRepeating();
            mCameraCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            Log.i(TAG,e.toString());
        }
    }

    public void captureWithFlashOn(View view) {
        lockFocus();
    }
    public void captureWithFlashOff(View view) {
        capture(false);
    }
    private void lockFocus() {
        try {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
            mCaptureState = STATE_WAITING_AF_LOCK;
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, ZERO_WEIGHT_3A_REGION);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, ZERO_WEIGHT_3A_REGION);
            applyCommonSettings(mPreviewRequestBuilder);
            mCameraCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mCameraHandler);
        } catch (CameraAccessException | IllegalStateException e) {
            Log.e(TAG,"lockFocus exception ="+e);
        }
    }
    private void runPrecaptureSequence() {
        try {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            applyCommonSettings(mPreviewRequestBuilder);
            mCaptureState = STATE_WAITING_PRECAPTURE;
            mCameraCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mCameraHandler);
        } catch (CameraAccessException | IllegalStateException e) {
            Log.e(TAG,e.toString());
        }
    }
    private void lockExposure() {
        try {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, Boolean.TRUE);
            mCaptureState = STATE_WAITING_AE_LOCK;
            mCameraCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mCameraHandler);
        } catch (CameraAccessException | IllegalStateException e) {
            Log.e(TAG,e.toString());
        }
    }
    private void updateCaptureStateMachine( CaptureResult result) {
        Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
        switch (mCaptureState) {
            case STATE_PREVIEW: {
                break;
            }
            case STATE_WAITING_AF_LOCK:
                if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState) {
                    runPrecaptureSequence();
                }
                break;
            case STATE_WAITING_PRECAPTURE: {
                if (aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                    lockExposure();
                }
                break;
            }
            case STATE_WAITING_AE_LOCK: {
                if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_LOCKED ) {
                    mCaptureState = STATE_PICTURE_TAKEN;
                    capture(true);
                }else if( aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED ||
                        aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE){
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                            CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL);
                    try {
                        mCameraCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mCameraHandler);
                    } catch (CameraAccessException e) {
                        throw new RuntimeException(e);
                    }

                }
                break;
            }
        }
    }

    private void unLockFocus() {
        Log.i(TAG,"unLockFocus");
        try {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL);
            mCameraCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mCameraHandler);
            mCaptureState = STATE_PREVIEW;
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, Boolean.FALSE);
           applyCommonSettings(mPreviewRequestBuilder);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, ZERO_WEIGHT_3A_REGION);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, ZERO_WEIGHT_3A_REGION);
            mPreviewRequestBuilder.set(
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
            if(isLowLightBoostSupported()) {
                applyLowLightBoost(mPreviewRequestBuilder);
            }
            mCameraCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
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
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        if(mTextureView != null) {
            mTextureView.setVisibility(View.INVISIBLE);
        }
    }

    private void setupImageReader() {
        mImageReader = ImageReader.newInstance(mPictureSize.getWidth(), mPictureSize.getHeight(),
                ImageFormat.JPEG, 2);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = reader.acquireNextImage();
                mCameraHandler.post(new ImageSaver(image, getApplicationContext()));

            }
        }, mCameraHandler);
    }

    private boolean writeFile(String path, byte[] data) {
        boolean value = false;
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(path);
            out.write(data);
            value = true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to write data", e);
        } finally {
            try {
                out.close();
            } catch (Exception e) {
                Log.e(TAG, "Failed to close file after write", e);
            }
        }
        return value;
    }

    public class ImageSaver implements Runnable {

        private final Image mImage;
        private final Context mContext;

        public ImageSaver(Image image, Context context) {
            mImage = image;
            mContext = context;
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            String path = mContext.getExternalFilesDir(null).getAbsolutePath();
            Log.i(TAG,"image width:" + mImage.getWidth() + ",height:" + mImage.getHeight() + ",path:" + path + ",size:" + data.length +",format:" + mImage.getFormat());
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String fileName = path + "IMG_" + timeStamp + ".jpg";
            if(writeFile(fileName, data))
                Toast.makeText(mContext, "Image Saved!", Toast.LENGTH_SHORT).show();
            mImage.close();
        }
    }

    public boolean isLowLightBoostSupported() {
        boolean isSupported = false;
        int[]aeModes = null;
        try {
            aeModes = mCameraManager.getCameraCharacteristics(mCameraId).get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES);
        } catch (CameraAccessException e) {
            Log.d(TAG," CameraAccessException e ="+e);
        }
        if(aeModes == null){
            return  false;
        }
        for(int aeMode:aeModes){
            Log.d(TAG," aemode="+aeMode);
            if(aeMode == CameraMetadata.CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY){
                isSupported = true;
                break;
            }
        }
        if(isSupported){
            Range<Float> lumRange = null;
            try {
                lumRange = mCameraManager.getCameraCharacteristics(mCameraId).get(CameraCharacteristics.CONTROL_LOW_LIGHT_BOOST_INFO_LUMINANCE_RANGE);
            } catch (CameraAccessException e) {
                Log.d(TAG," CameraAccessException e ="+e);
            }
            isSupported = lumRange != null;
            Log.d(TAG," lumRange="+lumRange);
        }
        return isSupported;
    }
    private void applyLowLightBoost(CaptureRequest.Builder request){
        if(!isLowLightBoostSupported()){
            Toast.makeText(MainActivity.this, "Don't support LowLightBoost!", Toast.LENGTH_SHORT).show();
            return;
        }
            request.set(CaptureRequest.CONTROL_AE_MODE,
                    CameraMetadata.CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY);
        }
    public void updateLowLightText(CaptureResult result) {
       if(isLowLightBoostSupported()){
            try {
                int aemode = result.get(CaptureResult.CONTROL_AE_MODE);
                int lowLightBoostState = result.get(CaptureResult.CONTROL_LOW_LIGHT_BOOST_STATE);
                if (lowLightBoostState == CameraMetadata.CONTROL_LOW_LIGHT_BOOST_STATE_ACTIVE) {
                    mLowLightText.setText("LowLightBoost_Active");
                } else if (lowLightBoostState == CameraMetadata.CONTROL_LOW_LIGHT_BOOST_STATE_INACTIVE) {
                    mLowLightText.setText("LowLightBoost_InActive");
                } else {
                    mLowLightText.setText("LowLightBoost_Unknown");
                }
            } catch (NullPointerException e) {
                mLowLightText.setText("LowLightBoost_Unknown");
            }
            mLowLightText.setVisibility(View.VISIBLE);
        } else {
            mLowLightText.setVisibility(View.INVISIBLE);
        }
    }


}