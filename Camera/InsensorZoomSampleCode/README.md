# InsensorZoomSampleCode

With traditional digital zoom, image quality may suffer at higher zoom levels. The In-sensor Zoom feature leverages Quad Bayer sensor technology to provide high quality images even with high digital zoom.

In-Sensor Zoom introduce link:https://developer.android.com/reference/android/hardware/camera2/CameraMetadata#SCALER_AVAILABLE_STREAM_USE_CASES_CROPPED_RAW

## Developing InsensorZoomSampleCode

#### Project branches

Development work happens on the `main` branch. Pull requests should normally be made to this branch from `dev` branch.

The `release` branch holds the most recent stable release.

The `dev` branch holds unreviewed changes.

#### Using Android Studio

To develop InsensorZoomSampleCode using Android Studio, simply open the project in the root directory of this repository.

#### API info
(1) In demo code, app will check SCALER_AVAILABLE_STREAM_USE_CASES is supported or not first. If yes, set it to configure stream.
If this stream usecase is set on a non-RAW stream, i.e. not one of :RAW_SENSOR/RAW10/RAW12, session configuration is not guaranteed to succeed.


    public boolean isAvailableUseCase(long useCaseId) {
        boolean isSupported = false;
        try {
            long[] avilibleCase = mCameraManager.getCameraCharacteristics(mCameraId).get(CameraCharacteristics.SCALER_AVAILABLE_STREAM_USE_CASES);
            for (int i = 0; i < avilibleCase.length; i++) {
                Log.d(TAG, "isAvailableUseCase avilibleCase[i]=" + avilibleCase[i]);
                if (useCaseId == avilibleCase[i]) isSupported = true;
            }
        } catch (IllegalArgumentException | NoSuchFieldError | CameraAccessException e) {
            Log.w(TAG, "exception  e= " + e);
        }
        Log.d(TAG, "isAvailableUseCase isSupported=" + isSupported);
        return isSupported;
    }


            if(isAvailableUseCase(CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_CROPPED_RAW)){
                outputConfiguration.setStreamUseCase(CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_CROPPED_RAW);
            }

(2) When capture complete, app can read SCALER_RAW_CROP_REGION value,this key specifies the crop region achieved for the RAW stream when cropped RAW is requested through the stream use case. 

When Zoom Ratio is >=1.0f and <2.0f, SCALER_RAW_CROP_REGION will be 1.0f

When Zoom Ratio is >=2.0f and <4.0f, SCALER_RAW_CROP_REGION will be 2.0f

When Zoom Ratio is >=4.0f, SCALER_RAW_CROP_REGION will be 4.0f

