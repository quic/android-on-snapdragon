# lowlightboost

Low Light Boost automatically adjusts the brightness of the Preview stream in low-light conditions.
Low Light Boost introduce link:https://developer.android.com/media/camera/camera2/low-light-boost

## Developing lowlightboost

#### Project branches

Development work happens on the `main` branch. Pull requests should normally be made to this branch from `dev` branch.

The `release` branch holds the most recent stable release.

The `dev` branch holds unreviewed changes.

#### Using Android Studio

To develop lowlightboost using Android Studio, simply open the project in the root directory of this repository.

#### API info
(1) In demo code, app will check camera2.CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES.
And check if the returned modes include CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY:

    public boolean isLowLightBoostSupported(){
        boolean isSupported = false;
        int[]aeModes = mCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES);
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
            Range<Float> lumRange = mCharacteristics.get(CameraCharacteristics.CONTROL_LOW_LIGHT_BOOST_INFO_LUMINANCE_RANGE);
            isSupported = lumRange != null;
            Log.d(TAG," lumRange="+lumRange);
        }
        return isSupported;
    }

       

(2) set CaptureRequest.CONTROL_AE_MODE to ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY

     private void applyLowLightBoost(CaptureRequest.Builder request){
        String value = mSettingsManager.getValue(SettingsManager.KEY_LOWLIGHT_BOOST);
        if(value != null && value.equals("1")){
            request.set(CaptureRequest.CONTROL_AE_MODE,
                                     CameraMetadata.CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY);
        }
    }
(3) You can confirm whether Low Light Boost is currently active by checking the CaptureResult.CONTROL_LOW_LIGHT_BOOST_STATE field

     public void updateLowLightText(CaptureResult result) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_LOWLIGHT_BOOST);
        if (value != null && value.equals("1")) {
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
        
          


