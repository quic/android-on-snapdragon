/*
 **************************************************************************************************
 * Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 **************************************************************************************************
 */

package qti.video.depthcapture;

import android.media.MediaFormat;

public interface TrackDataSource {

  interface OutputListener {
    void onOutputAvailable();

    void onEos();
  }

  MediaFormat getFormat();

  void setOutputListener(OutputListener listener);

  // non-blocking call
  TrackBuffer dequeueBuffer();

  void queueBuffer(TrackBuffer buffer);

  boolean isEos();
}
