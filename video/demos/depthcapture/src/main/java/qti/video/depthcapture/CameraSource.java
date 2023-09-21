/*
 **************************************************************************************************
 * Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 **************************************************************************************************
 */

package qti.video.depthcapture;

import android.media.MediaFormat;
import android.view.Surface;

public interface CameraSource {
  interface OnFinishedListener {
    void onFinished();
  }

  /**
   * Check if the current camera source supports the track type.
   *
   * @param trackType the track type to check. Value is
   *     {@link qti.video.depth.DepthFormat#TRACK_TYPE_SHARP_VIDEO},
   *     {@link qti.video.depth.DepthFormat#TRACK_TYPE_DEPTH_LINEAR},
   *     {@link qti.video.depth.DepthFormat#TRACK_TYPE_DEPTH_INVERSE},
   *     {@link qti.video.depth.DepthFormat#TRACK_TYPE_METADATA} or
   *     {@link qti.video.depth.DepthFormat#TRACK_TYPE_TRANSLUCENT_VIDEO}.
   * @return true if the track is supported.
   */
  boolean supportTrack(int trackType);

  /**
   * Get format of the specific track.
   *
   * @param trackType the track type. Value is
   *     {@link qti.video.depth.DepthFormat#TRACK_TYPE_SHARP_VIDEO},
   *     {@link qti.video.depth.DepthFormat#TRACK_TYPE_DEPTH_LINEAR},
   *     {@link qti.video.depth.DepthFormat#TRACK_TYPE_DEPTH_INVERSE},
   *     {@link qti.video.depth.DepthFormat#TRACK_TYPE_METADATA} or
   *     {@link qti.video.depth.DepthFormat#TRACK_TYPE_TRANSLUCENT_VIDEO}.
   * @return track format.
   * @throws UnsupportedOperationException if the track is unsupported.
   */
  MediaFormat getTrackFormat(int trackType);

  /**
   * Set output surface of a track.
   *
   * @param trackType the track type. Value is
   *     {@link qti.video.depth.DepthFormat#TRACK_TYPE_SHARP_VIDEO},
   *     {@link qti.video.depth.DepthFormat#TRACK_TYPE_DEPTH_LINEAR},
   *     {@link qti.video.depth.DepthFormat#TRACK_TYPE_DEPTH_INVERSE} or
   *     {@link qti.video.depth.DepthFormat#TRACK_TYPE_TRANSLUCENT_VIDEO}.
   * @param surface the output surface of the track.
   * @throws UnsupportedOperationException if the track is unsupported.
   */
  void setOutputSurface(int trackType, Surface surface);

  /**
   * Get the TrackDataSource for depth metadata.
   *
   * @return TrackDataSource for depth metadata.
   * @throws UnsupportedOperationException if metadata track is unsupported.
   */
  TrackDataSource getMetadataDataSource();

  /**
   * Set on finished listener. For mimic CameraSource implementations, there are limited frames and
   *   it will reach EOS when all the frames are consumed, so client may need to receive
   *   notification for the EOS.
   *
   * @param listener {@link OnFinishedListener#onFinished()} will be called when the CameraSource
   *     reaches EOS. It will not be called when {@link #stop()} is called.
   * @apiNote it should be called before {@link #start()} is called.
   */
  void setOnFinishedListener(OnFinishedListener listener);

  /**
   * Initialize CameraSource. Track formats will be available after init() returns.
   */
  void init();

  /**
   * Start CameraSource. Buffers will be queued to Surface and/or metadata TrackDataSource.
   */
  void start();

  /**
   * Stop CameraSource. Buffers will not be queued to Surface and/or metadata TrackDataSource after
   *   this method is returned.
   */
  void stop();

  /**
   * Release CameraSource. All resources will be released.
   */
  void release();
}
