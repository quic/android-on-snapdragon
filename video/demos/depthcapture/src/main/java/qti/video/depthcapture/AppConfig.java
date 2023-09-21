/*
 **************************************************************************************************
 * Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 **************************************************************************************************
 */

package qti.video.depthcapture;

public final class AppConfig {
  public static final String APP_EXTERNAL_DIR
      = "/storage/emulated/0/Android/data/qti.video.depthcapture/files";

  // adb push \\hw-lubiny-lv\Public\depth\IMG_0400_remuxed.mp4 \
  //     /storage/emulated/0/Android/data/qti.video.depthcapture/files/
  public static final String MOCK_CAMERA_INPUT_CLIP = APP_EXTERNAL_DIR + "/IMG_0400_remuxed.mp4";
  public static final int VIDEO_WIDTH = 1920;
  public static final int VIDEO_HEIGHT = 1080;
  public static final String CAPTURE_OUTPUT_FILE_PATH
      = APP_EXTERNAL_DIR + "/depth_capture_output.mp4";

  // adb push \\hw-lubiny-lv\Public\depth\GDepthClip.mp4 \
  // /storage/emulated/0/Android/data/qti.video.depthcapture/files/
  public static final String DEPTH_CLIP_SAMPLE = APP_EXTERNAL_DIR + "/GDepthClip.mp4";

}
