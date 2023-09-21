/*
 **************************************************************************************************
 * Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 **************************************************************************************************
 */

package qti.video.depth;

/**
 * Depth format. It contains keys and values for depth inner track formats.
 */
public final class DepthFormat {
  public static final String KEY_TRACK_TYPE = "depth-track-type";
  public static final int TRACK_TYPE_SHARP_VIDEO = 0;
  public static final int TRACK_TYPE_DEPTH_LINEAR = 1;
  public static final int TRACK_TYPE_DEPTH_INVERSE = 2;
  public static final int TRACK_TYPE_METADATA = 3;
  public static final int TRACK_TYPE_TRANSLUCENT_VIDEO = 4;
  public static final int MAX_TRACK_TYPE_COUNT = 5;

  // Non-public meta keys and types for depth container.
  // Only used by muxer and extractor internally.
  static final String META_KEY_EDVD_OFFSET = "editable.tracks.offset";
  static final int META_TYPE_EDVD_OFFSET = 78; // type 78: BE 64-bit Unsigned Integer
  static final String META_KEY_EDVD_LENGTH = "editable.tracks.length";
  static final int META_TYPE_EDVD_LENGTH = 78; // type 78: BE 64-bit Unsigned Integer
  static final String META_KEY_DEPTH_TRACK_TYPES = "editable.tracks.map";
  static final int META_TYPE_DEPTH_TRACK_TYPES = 0; // type 0: no type needs to be indicated
}
