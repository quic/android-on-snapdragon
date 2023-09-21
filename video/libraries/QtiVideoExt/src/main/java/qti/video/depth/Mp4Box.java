/*
 **************************************************************************************************
 * Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 **************************************************************************************************
 */

package qti.video.depth;

import android.util.Log;
import java.io.DataInput;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

class Mp4Box {
  private static final String TAG = "Mp4Box";
  public static final int SIZE_COMPACT_BOX_HEADER = 8;
  private static final int SIZE_LARGE_SIZE = 8;
  private static final int SIZE_USER_TYPE = 16;
  private static final int USE_LARGE_SIZE = 1;
  public static final int BOX_SIZE_TO_FILE_END = 0;
  public static final int BOX_SIZE_UNKNOWN = -1;

  public static final int FOURCC_UUID = Mp4Utils.toFourCc("uuid");
  public static final int FOURCC_MOOV = Mp4Utils.toFourCc("moov");
  public static final int FOURCC_FREE = Mp4Utils.toFourCc("free");
  public static final int FOURCC_META = Mp4Utils.toFourCc("meta");
  public static final int FOURCC_EDVD = Mp4Utils.toFourCc("edvd");

  private final long u32Size;
  final int fourCc;
  private final String debugFourCcStr; // for debug only, can be removed

  private final long largeSize;
  private final byte[] userType;

  private long fileOffset = -1; // optional
  private ArrayList<Mp4Box> children; // optional, nullable

  /**
   * Read box header from input and create Box object.
   */
  public static Mp4Box parseHeader(DataInput in) throws IOException {
    final long u32Size;
    final int fourCc;
    final long largeSize;
    final byte[] userType;

    try {
      u32Size = Mp4Utils.u32ToI64(in.readInt());
    } catch (EOFException e) {
      Log.e(TAG, "EOF");
      return null;
    }
    fourCc = in.readInt();
    if (u32Size == USE_LARGE_SIZE) {
      largeSize = in.readLong();
    } else {
      largeSize = -1;
    }
    if (fourCc == FOURCC_UUID) {
      userType = new byte[16];
      in.readFully(userType);
    } else {
      userType = null;
    }

    return new Mp4Box(u32Size, fourCc, largeSize, userType);
  }

  /**
   * Create a compact box for the given four CC and payload size.
   */
  public static Mp4Box createForPayloadSize(int fourCc, long payloadSize) {
    long compactBoxSize = SIZE_COMPACT_BOX_HEADER + payloadSize;
    if (compactBoxSize <= Mp4Utils.MAX_U32) {
      return new Mp4Box(compactBoxSize, fourCc);
    } else {
      return new Mp4Box(
          USE_LARGE_SIZE, fourCc,
          SIZE_COMPACT_BOX_HEADER + SIZE_LARGE_SIZE + payloadSize, null);
    }
  }

  /**
   * create a compact box which has a header of 8 bytes.
   *
   * @param u32Size box size, cannot be 1
   * @param fourCc box type
   */
  public Mp4Box(long u32Size, int fourCc) {
    this(u32Size, fourCc, -1, null);
    assert (u32Size != USE_LARGE_SIZE && u32Size <= Mp4Utils.MAX_U32);
  }

  private Mp4Box(long u32Size, int fourCc, long largeSize, byte[] userType) {
    assert (u32Size == USE_LARGE_SIZE ^ largeSize == -1);
    // TODO: size 0 (box size extends to the file end) is unsupported for now
    assert (u32Size != BOX_SIZE_TO_FILE_END && largeSize != BOX_SIZE_TO_FILE_END);
    this.u32Size = u32Size;
    this.fourCc = fourCc;
    this.largeSize = largeSize;
    this.userType = userType;
    debugFourCcStr = Mp4Utils.fourCcToStr(this.fourCc);
  }

  protected Mp4Box(Mp4Box other) {
    this(other.u32Size, other.fourCc, other.largeSize, other.userType);
    fileOffset = other.fileOffset;
    children = other.children;
  }

  public boolean isCompact() {
    return (u32Size != USE_LARGE_SIZE) && (userType == null);
  }

  /**
   * Get box size.
   *
   * @return the entire size of the box, including the header
   */
  public long getSize() {
    return (u32Size == USE_LARGE_SIZE) ? largeSize : u32Size;
  }

  public int getHeaderSize() {
    return SIZE_COMPACT_BOX_HEADER
        + ((u32Size == USE_LARGE_SIZE) ? SIZE_LARGE_SIZE : 0)
        + ((userType == null) ? 0 : SIZE_USER_TYPE);
  }

  public long getPayloadSize() {
    return getSize() - getHeaderSize();
  }

  public final byte[] getHeaderBlob() {
    ByteBuffer buf = ByteBuffer.allocate(getHeaderSize());
    if (u32Size != USE_LARGE_SIZE) {
      buf.putInt(Mp4Utils.i64ToU32(u32Size));
      buf.putInt(fourCc);
    } else {
      buf.putInt(USE_LARGE_SIZE);
      buf.putInt(fourCc);
      buf.putLong(u32Size);
    }
    return buf.array();
  }

  public void setFileOffset(long offset) {
    fileOffset = offset;
  }

  public long getFileOffset() {
    return fileOffset;
  }

  public void addChild(Mp4Box box) {
    if (children == null) {
      children = new ArrayList<>();
    }
    children.add(box);
  }

  public void addChildren(List<Mp4Box> boxes) {
    assert (children == null);
    children = new ArrayList<>(boxes);
  }

  public ArrayList<Mp4Box> getChildren() {
    return children;
  }

  public Mp4Box getChildAt(int index) {
    assert  (children != null);
    return children.get(index);
  }

  public Mp4Box findChild(int fourCc) {
    assert  (children != null);
    for (int index = 0; index < children.size(); ++index) {
      Mp4Box box = children.get(index);
      if (box.fourCc == fourCc) {
        return box;
      }
    }
    return null;
  }

  public int indexOfChild(int fourCc) {
    if (children == null) {
      return -1;
    }
    for (int index = 0; index < children.size(); ++index) {
      if (children.get(index).fourCc == fourCc) {
        return index;
      }
    }
    return -1;
  }
}
