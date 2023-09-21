/*
 **************************************************************************************************
 * Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 **************************************************************************************************
 */

package qti.video.depth;

import java.util.Arrays;

class Mp4FreeBox extends Mp4InMemBox {
  // huge free box is not allowed to avoid out of memory
  private static final int MAX_FREE_BOX_SIZE = 1024 * 1024;
  private static final byte FREE_BOX_FILL = '1';

  private Mp4FreeBox(int u32Size) {
    super(Mp4Box.FOURCC_FREE, new byte[u32Size - Mp4Box.SIZE_COMPACT_BOX_HEADER]);
    Arrays.fill(getPayload(), FREE_BOX_FILL);
  }

  public static Mp4FreeBox createFreeBox(long u32BoxSize) {
    assert (u32BoxSize <= MAX_FREE_BOX_SIZE);
    return new Mp4FreeBox((int) u32BoxSize);
  }
}
