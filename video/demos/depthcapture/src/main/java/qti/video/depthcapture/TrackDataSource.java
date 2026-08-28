/*
 **************************************************************************************************
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause
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
