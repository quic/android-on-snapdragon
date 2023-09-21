/*
 **************************************************************************************************
 * Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 **************************************************************************************************
 */

package qti.video.depth;

import android.util.Log;
import android.util.Pair;
import java.io.DataInput;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Map;

class Mp4DepthMetaParser {
  static final String TAG = "Mp4DepthMetaParser";
  private final RandomAccessFile accessFile;
  private long innerClipOffset = -1;
  private long innerClipLength = -1;
  private byte[] innerTrackTypes;

  public Mp4DepthMetaParser(RandomAccessFile accessFile) {
    this.accessFile = accessFile;
  }

  /**
   * Parse the clip.
   *
   * @return true if the clip is a depth clip
   * @throws IOException if any IO error happens
   */
  public boolean parse() throws IOException {
    accessFile.seek(0);
    long[] edvdInfo = parseOuterClipMeta(accessFile);
    if (edvdInfo == null) {
      Log.e(TAG, "Failed to parse outer clip. Not a depth clip");
      return false;
    }
    assert (edvdInfo.length == 3);
    Log.v(TAG, String.format("edvd box info: offset[%d], size[%d], header size[%d]",
        edvdInfo[0], edvdInfo[1], edvdInfo[2]));
    final long edvdOffset = edvdInfo[0];
    final long edvdSize = edvdInfo[1];
    final long edvdHeaderSize = edvdInfo[2];
    innerClipOffset = edvdOffset + edvdHeaderSize;
    innerClipLength = edvdSize - edvdHeaderSize;

    accessFile.seek(innerClipOffset);
    byte[] innerStaticMetadata = parseInnerClipMeta(accessFile);
    innerTrackTypes = Mp4MetaUtils.parseInnerStaticMeta(innerStaticMetadata);
    Log.v(TAG, "inner track types: " + Arrays.toString(innerTrackTypes));
    return true;
  }

  public boolean isDepthClip() {
    return (innerClipOffset > 0);
  }

  public long getInnerClipOffset() {
    return innerClipOffset;
  }

  public long getInnerClipLength() {
    return innerClipLength;
  }

  public byte[] getInnerClipTrackTypes() {
    return innerTrackTypes;
  }

  // return [edvdOffset, edvdSize, edvdHeaderSize]
  static long[] parseOuterClipMeta(DataInput dataInput) {
    Mp4Box root = Mp4BoxTreeParser.parseForMeta(dataInput);
    Mp4Box edvdBox = root.findChild(Mp4Box.FOURCC_EDVD);
    if (edvdBox == null) {
      Log.e(TAG, "Cannot find edvd box");
      return null;
    }

    Mp4Box moovBox = root.findChild(Mp4Box.FOURCC_MOOV);
    assert (moovBox != null);
    Mp4Box metaBox = moovBox.findChild(Mp4Box.FOURCC_META);
    if (metaBox == null) {
      Log.e(TAG, "edvd box exists but canot find corresponding meta in outer clip");
      return new long[] {edvdBox.getFileOffset(), edvdBox.getSize(), edvdBox.getHeaderSize()};
    }
    assert (metaBox instanceof Mp4InMemBox);
    Map<String, Pair<Integer, byte[]>> map
        = Mp4MetaUtils.parseMetaBoxWithMdtaHandler((Mp4InMemBox) metaBox);
    if (map == null) {
      Log.v(TAG, "Cannot parse meta");
      return null;
    }
    Pair<Integer, byte[]> offsetEntry = map.getOrDefault(DepthFormat.META_KEY_EDVD_OFFSET, null);
    if (offsetEntry == null) {
      Log.e(TAG, "Cannot find edvd offset in meta");
      return null;
    }
    assert (offsetEntry.first == DepthFormat.META_TYPE_EDVD_OFFSET);
    // TODO: need to change if value type changes
    long edvdOffset = Mp4Utils.i64From8Bytes(offsetEntry.second);
    Pair<Integer, byte[]> lengthEntry = map.getOrDefault(DepthFormat.META_KEY_EDVD_LENGTH, null);
    if (lengthEntry == null) {
      Log.e(TAG, "Cannot find edvd length in meta");
      return null;
    }
    assert (lengthEntry.first == DepthFormat.META_TYPE_EDVD_LENGTH);
    // TODO: need to change if value type changes
    long edvdLength = Mp4Utils.i64From8Bytes(lengthEntry.second);
    assert (edvdOffset == edvdBox.getFileOffset());
    assert (edvdLength == edvdBox.getSize());
    return new long[] {edvdBox.getFileOffset(), edvdBox.getSize(), edvdBox.getHeaderSize()};
  }

  // return track types
  static byte[] parseInnerClipMeta(DataInput dataInput) {
    Mp4Box root = Mp4BoxTreeParser.parseForMeta(dataInput);
    Mp4Box moovBox = root.findChild(Mp4Box.FOURCC_MOOV);
    assert (moovBox != null);
    Mp4Box metaBox = moovBox.findChild(Mp4Box.FOURCC_META);
    if (metaBox == null) {
      Log.e(TAG, "inner clip: cannot find meta box");
      return null;
    }
    assert (metaBox instanceof Mp4InMemBox);
    Map<String, Pair<Integer, byte[]>> map
        = Mp4MetaUtils.parseMetaBoxWithMdtaHandler((Mp4InMemBox) metaBox);
    if (map == null) {
      Log.v(TAG, "Cannot parse meta");
      return null;
    }
    Pair<Integer, byte[]> trackTypesEntry = map.getOrDefault(
        DepthFormat.META_KEY_DEPTH_TRACK_TYPES, null);
    if (trackTypesEntry == null) {
      Log.e(TAG, "Cannot find track types in meta");
      return null;
    }
    assert (trackTypesEntry.second != null);
    return trackTypesEntry.second;
  }
}
