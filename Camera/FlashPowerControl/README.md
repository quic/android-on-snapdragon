# flashpowercontrol

Flash strength level to use in capture mode i.e. when the applications control flash with either SINGLE or TORCH mode.
Flash Strength level introduce link:https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#FLASH_STRENGTH_LEVEL

## Developing flashpowercontrol

#### Project branches

Development work happens on the `main` branch. Pull requests should normally be made to this branch from `dev` branch.

The `release` branch holds the most recent stable release.

The `dev` branch holds unreviewed changes.

#### Using Android Studio

To develop flashpowercontrol using Android Studio, simply open the project in the root directory of this repository.

#### API info
(1) In demo code, app will check FLASH_SINGLE_STRENGTH_MAX_LEVEL is above 0 or not.
If yes, you can set flash strength  and CONTROL_AE_MODE_ON when flash on.
If you want to set Flash strength in Manual AEC mode (manual set SENSOR_EXPOSURE_TIME and SENSOR_SENSITIVITY),
you need set CONTROL_AE_MODE_OFF.

    public int getMaxFlashLevel() {
        int max = 0;
        try {
            max = mCameraManager.getCameraCharacteristics(mCameraId).get(CameraCharacteristics.FLASH_SINGLE_STRENGTH_MAX_LEVEL);
        }catch(NoSuchFieldError e){
            Log.i(TAG,"NoSuchFieldError e="+e);
        } catch (CameraAccessException e) {
            Log.i(TAG,"CameraAccessException e="+e);
        }
        return max;
    }

    private void setFlashLevel(CaptureRequest.Builder request,int value) {
        try {
            request.set(CaptureRequest.FLASH_STRENGTH_LEVEL, value);
            request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            //Note : If you set SENSOR_EXPOSURE_TIME and SENSOR_SENSITIVITY ,you need set CONTROL_AE_MODE_OFF
            Log.i(TAG, "setFlashLevel level=" + value);
        } catch (NoSuchFieldError e) {
            Log.i(TAG, "e=" + e);
        }
    }

(2) After trigger snapshot,you need to set FLASH_MODE_SINGLE in the last captuere request.
When we start to snapShot with flash on ,we had to CONTROL_AF_TRIGGER_START -> CONTROL_AE_PRECAPTURE_TRIGGER_START
->CONTROL_AE_LOCK -> Capture

    public void capture() {
        try {
            final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.addTarget(mImageReader.getSurface());
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATION.get(rotation));
            captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);//This is setted when flash on
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


