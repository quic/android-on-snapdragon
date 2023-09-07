# UltraHDRSampleCode

Enhances JPEG snapshots to support higher dynamic range for more vivid colors and contrast. The fully backward compatible enhancement allows older devices to still render the content using standard JPEG.

UltraHDR introduce link: https://developer.android.com/reference/android/graphics/ImageFormat#JPEG_R
https://developer.android.com/guide/topics/media/platform/hdr-image-format#introduction

## Developing UltraHDRSampleCode

#### Project branches

Development work happens on the `main` branch. Pull requests should normally be made to this branch from `dev` branch.

The `release` branch holds the most recent stable release.

The `dev` branch holds unreviewed changes.

#### Using Android Studio

To develop UltraHDRSampleCode using Android Studio, simply open the project in the root directory of this repository.

#### API info
When create session, app set picture format as JPEG_R and set dynamic range profile 2 for stream , last save the jpeg R image.

(1) ImageFormat.JPEG_R, configure stream format

    private void setupImageReader() {
        mImageReader = ImageReader.newInstance(mJpegize.getWidth(), mJpegize.getHeight(),
                ImageFormat.JPEG_R, 2);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                mCameraHandler.post(new ImageSaver(reader.acquireNextImage(), getApplicationContext()));
            }
        }, mCameraHandler);
    }
    
(2) setDynamicRangeProfile

            OutputConfiguration outputConfiguration = new OutputConfiguration(mImageReader.getSurface());
            //set HLG progile for jpeg R stream
            outputConfiguration.setDynamicRangeProfile(Long.parseLong("2"));
            
When create session, app set picture format as JPEG_R, but it will be changed to YUV P010 at framework, then configure stream will be YCbCr_420_P010, when generate finial jpeg, yuv will be transferd to jpeg R again at framework.

16639 17189 I CamX    : [CORE_CFG][HAL    ] camxhal3.cpp:1468 configure_streams()  FINAL stream[1] = 0xb4000072daca32f8 - info:    

16639 17189 I CamX    : [CORE_CFG][HAL    ] camxhal3.cpp:1471 configure_streams()             format       : 54, HAL_PIXEL_FORMAT_YCbCr_420_P010   
