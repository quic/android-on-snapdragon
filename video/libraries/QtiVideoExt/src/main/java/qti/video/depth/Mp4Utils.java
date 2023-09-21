/*
 **************************************************************************************************
 * Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 **************************************************************************************************
 */

package qti.video.depth;

import java.nio.ByteBuffer;

final class Mp4Utils {

  public static final long MAX_U32 = 0xFFFFFFFFL;

  // Convert i64 to u32 (int type, can be negative for large u32)
  public static int i64ToU32(long x) {
    return (int) x;
  }

  // Convert u32 (int type) to i64. u32 is filled to low 4 bytes of the i64. Unsigned extension.
  public static long u32ToI64(int x) {
    return Integer.toUnsignedLong(x);
  }

  static byte[] u32To4Bytes(int x) {
    byte[] bytes = new byte[4];
    ByteBuffer.wrap(bytes).putInt(x);
    return bytes;
  }

  static byte[] i64To8Bytes(long x) {
    byte[] bytes = new byte[8];
    ByteBuffer.wrap(bytes).putLong(x);
    return bytes;
  }

  static long i64From8Bytes(byte[] bytes) {
    assert (bytes.length == 8);
    return ByteBuffer.wrap(bytes).getLong();
  }

  static int u32From4Bytes(byte[] bytes) {
    assert (bytes.length == 4);
    return ByteBuffer.wrap(bytes).getInt();
  }

  public static int toFourCc(String s) {
    byte[] bytes = s.getBytes();
    assert (bytes != null && bytes.length == 4);
    return u32From4Bytes(bytes);
  }

  static int toFourCc(byte[] bytes) {
    assert (bytes != null && bytes.length == 4);
    return u32From4Bytes(bytes);
  }

  public static String fourCcToStr(int fourCc) {
    byte[] bytes = u32To4Bytes(fourCc);
    return new String(bytes);
  }

}
