/*
 **************************************************************************************************
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause
 **************************************************************************************************
 */

package qti.video.depthcapture;

public final class AppConfig {
  public static final String APP_EXTERNAL_DIR
      = "/storage/emulated/0/Android/data/qti.video.depthcapture/files";

  // Push an approved public test clip to this path before running the mock capture demo.
  // Example:
  // adb push <local-test-media-dir>/IMG_0400_remuxed.mp4 \
  //     /storage/emulated/0/Android/data/qti.video.depthcapture/files/
  public static final String MOCK_CAMERA_INPUT_CLIP = APP_EXTERNAL_DIR + "/IMG_0400_remuxed.mp4";
  public static final int VIDEO_WIDTH = 1920;
  public static final int VIDEO_HEIGHT = 1080;
  public static final String CAPTURE_OUTPUT_FILE_PATH
      = APP_EXTERNAL_DIR + "/depth_capture_output.mp4";

  // Push an approved public depth sample to this path before running the playback demo.
  // Example:
  // adb push <local-test-media-dir>/GDepthClip.mp4 \
  //     /storage/emulated/0/Android/data/qti.video.depthcapture/files/
  public static final String DEPTH_CLIP_SAMPLE = APP_EXTERNAL_DIR + "/GDepthClip.mp4";

}
