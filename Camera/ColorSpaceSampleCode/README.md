# ColorSpaceSampleCode
Digital Camera Initiatives-Protocol 3 (DCI-P3) is a color space specification that enables the use of a wider color gamut. This helps to increase the range of colors that can be used for capturing images.

ColorSpace introduce link:https://developer.android.com/reference/android/graphics/ColorSpace

## Developing ColorSpaceSampleCode

#### Project branches

Development work happens on the `main` branch. Pull requests should normally be made to this branch from `dev` branch.

The `release` branch holds the most recent stable release.

The `dev` branch holds unreviewed changes.

#### Using Android Studio

To develop ColorSpaceSampleCode using Android Studio, simply open the project in the root directory of this repository.

#### API info

(1) CameraCharacteristics.REQUEST_AVAILABLE_COLOR_SPACE_PROFILES
    App can get the ColorSpaceProfiles values  by reading the REQUEST_AVAILABLE_COLOR_SPACE_PROFILES from camera's Characteristics. The code snippet is as below: 
    ColorSpaceProfiles colorSpaceProfiles = mCharacteristics.get(cameraId).get(
                CameraCharacteristics.REQUEST_AVAILABLE_COLOR_SPACE_PROFILES);

    Then app can get the supported set of ColorSpace.Named for different imageformats.
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

Here is the log of getting color space values:

07-07 16:11:56.440 8646 8646 V MainActivity: getAvailableColorSpacesChecked colorSpaces :{SRGB, DISPLAY_P3}

(2) App can set a specific device-supported color spac for config stream via Function setColorSpace in Class SessionConfiguration.
    This is the important step for setting the ColorSpace.Named value to cam hal. 
    sessionConfig.setColorSpace(ColorSpace.Named.DISPLAY_P3);

Clients can choose from any profile advertised as supported in CameraCharacteristics#REQUEST_AVAILABLE_COLOR_SPACE_PROFILES queried using ColorSpaceProfiles#getSupportedColorSpaces. 
When set, the colorSpace will override the default color spaces of the output targets, or the color space implied by the dataSpace passed into an ImageReader's constructor.
