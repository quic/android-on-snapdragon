# InstantZoomSampleCode

Quickly jump to a new zoom level in your camera application without having to use the traditional pinch-and-zoom for faster zoom response.
InstantZoomSampleCode apply the requested zoom value onto earlier request that's already in the pipeline, thus improves zoom.

Instant Zoom introduce link:https://developer.android.com/reference/android/hardware/camera2/CameraMetadata#CONTROL_SETTINGS_OVERRIDE_ZOOM

## Developing InstantZoomSampleCode

#### Project branches

Development work happens on the `main` branch. Pull requests should normally be made to this branch from `dev` branch.

The `release` branch holds the most recent stable release.

The `dev` branch holds unreviewed changes.

#### Using Android Studio

To develop InstantZoomSampleCode using Android Studio, simply open the project in the root directory of this repository.

#### API info
(1) App read CONTROL_AVAILABLE_SETTINGS_OVERRIDES tag to check whether it is supported or not

             if (mCameraManager.getCameraCharacteristics(mCameraId) != null) {
                int[] availableOverrides = mCameraManager.getCameraCharacteristics(mCameraId).get(CameraCharacteristics
                        .CONTROL_AVAILABLE_SETTINGS_OVERRIDES);
                if (availableOverrides == null) {
                    return false;
                }
            }
        

(2) set CONTROL_SETTINGS_OVERRIDE_ZOOM for capture request

                request.set(CaptureRequest.CONTROL_SETTINGS_OVERRIDE,
                        CameraMetadata.CONTROL_SETTINGS_OVERRIDE_ZOOM);
        
App send zoom ratio as below for instant zoom case and normal zoom case:

D/MainActivity: applyzoomratio=1.1

D/MainActivity: applyzoomratio=1.2

D/MainActivity: applyzoomratio=1.3000001

D/MainActivity: applyzoomratio=1.4000001

D/MainActivity: applyzoomratio=1.5000001

D/MainActivity: applyzoomratio=1.6000001

D/MainActivity: applyzoomratio=1.7000002

D/MainActivity: applyzoomratio=1.8000002

D/MainActivity: applyzoomratio=1.9000002

D/MainActivity: applyzoomratio=2.0000002

##### If use Instant zoom, the result zoom value will be below, some zoom values will be skipped

05-13 23:32:57.678 30922 30945 I MainActivity: setting override:1 

I/MainActivity: zoom value:1.0

I/MainActivity: zoom value:1.6000001

I/MainActivity: zoom value:1.7000002

I/MainActivity: zoom value:1.8000002

I/MainActivity: zoom value:1.9000002

I/MainActivity: zoom value:2.0000002


##### Without Instant zoom, result will be below, no zoom values are skipped

05-13 23:39:50.572 32333 32356 I MainActivity: setting override:0                                                                                                                                                                            
I/MainActivity: zoom value:1.1

I/MainActivity: zoom value:1.2

I/MainActivity: zoom value:1.3000001

I/MainActivity: zoom value:1.4000001

I/MainActivity: zoom value:1.5000001

I/MainActivity: zoom value:1.6000001

I/MainActivity: zoom value:1.7000002

I/MainActivity: zoom value:1.8000002

I/MainActivity: zoom value:1.9000002

I/MainActivity: zoom value:2.0000002
