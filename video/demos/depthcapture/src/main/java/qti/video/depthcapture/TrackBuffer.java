/*
 **************************************************************************************************
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause
 **************************************************************************************************
 */

package qti.video.depthcapture;

import android.media.MediaCodec;
import java.nio.ByteBuffer;

public class TrackBuffer {
  public final ByteBuffer byteBuf;
  public final MediaCodec.BufferInfo bufferInfo;

  TrackBuffer(ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo, Object obj) {
    assert (byteBuf != null);
    assert (bufferInfo != null);
    this.byteBuf = byteBuf;
    this.bufferInfo = bufferInfo;
    this.internalObj = obj;
  }

  final Object internalObj;
}
