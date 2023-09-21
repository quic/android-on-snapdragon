/*
 **************************************************************************************************
 * Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 **************************************************************************************************
 */

package qti.video.depth;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

class Mp4InMemBox extends Mp4Box {

  private final byte[] payload;

  public Mp4InMemBox(int fourCc, byte[] payload) {
    super(payload.length + Mp4Box.SIZE_COMPACT_BOX_HEADER, fourCc);
    this.payload = payload;
    assert (isCompact());
  }

  public Mp4InMemBox(Mp4Box header, byte[] payload) {
    super(header);
    this.payload = payload;
    assert (getPayloadSize() == payload.length);
  }

  public byte[] getPayload() {
    return payload;
  }

  public static class Builder {
    private final int fourCc;
    private final ByteArrayOutputStream byteArrayOutputStream;
    private final DataOutputStream dataOutputStream;

    public Builder(int fourCc) {
      this.fourCc = fourCc;
      byteArrayOutputStream = new ByteArrayOutputStream();
      dataOutputStream = new DataOutputStream(byteArrayOutputStream);
    }

    public Mp4InMemBox build() {
      byte[] payload = byteArrayOutputStream.toByteArray();
      // no need to close streams, so we can build for multiple times
      return new Mp4InMemBox(fourCc, payload);
    }

    public Builder putByte(int x) {
      try {
        dataOutputStream.write(x);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return this;
    }

    public Builder putInt(int x) {
      try {
        dataOutputStream.writeInt(x);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return this;
    }

    public Builder putLong(long x) {
      try {
        dataOutputStream.writeLong(x);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return this;
    }

    public Builder putBytes(byte[] bytes) {
      try {
        dataOutputStream.write(bytes);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return this;
    }

    public Builder putSubBox(Mp4InMemBox box) {
      putBytes(box.getHeaderBlob());
      putBytes(box.getPayload());
      return this;
    }
  }
}
