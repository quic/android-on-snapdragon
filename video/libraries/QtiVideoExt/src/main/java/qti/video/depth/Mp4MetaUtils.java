/*
 **************************************************************************************************
 * Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 **************************************************************************************************
 */

package qti.video.depth;

import android.util.Log;
import android.util.Pair;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class Mp4MetaUtils {
  static final String TAG = "Mp4MetaUtils";

  static Mp4InMemBox createHdlrBox() {
    return new Mp4InMemBox.Builder(Mp4Utils.toFourCc("hdlr"))
        .putInt(0) // version, flags
        .putInt(0) // predefined
        .putInt(Mp4Utils.toFourCc("mdta"))
        .putInt(0) // reserved[0]
        .putInt(0) // reserved[1]
        .putInt(0) // reserved[2]
        .putByte(0) // Empty name. 1 byte.
        .build();
  }

  static Mp4InMemBox createMdtaKeyBox(String key) {
    return new Mp4InMemBox.Builder(Mp4Utils.toFourCc("mdta"))
        .putBytes(key.getBytes())
        .build();
  }

  static Mp4InMemBox createMdtaDataBox(int valueType, byte[] valuePayload) {
    return new Mp4InMemBox.Builder(Mp4Utils.toFourCc("data"))
        .putInt(valueType) // value type
        .putInt(0) // default county/language
        .putBytes(valuePayload) // value payload
        .build();
  }

  static Mp4InMemBox createKeysBox(List<Mp4InMemBox> subBoxes) {
    Mp4InMemBox.Builder builder = new Mp4InMemBox.Builder(Mp4Utils.toFourCc("keys"));
    builder
        .putInt(0) // version, flags
        .putInt(subBoxes.size()); // count
    for (Mp4InMemBox box : subBoxes) {
      builder.putSubBox(box);
    }
    return builder.build();
  }

  static Mp4InMemBox createKeysBox(String... keys) {
    ArrayList<Mp4InMemBox> keyBoxes = new ArrayList<>(2);
    for (String key : keys) {
      keyBoxes.add(createMdtaKeyBox(key));
    }
    return createKeysBox(keyBoxes);
  }

  static Mp4InMemBox createIlstBox(Mp4InMemBox... dataBoxes) {
    Mp4InMemBox.Builder builder = new Mp4InMemBox.Builder(Mp4Utils.toFourCc("ilst"));
    for (int dataIndex = 0; dataIndex < dataBoxes.length; ++dataIndex) {
      Mp4InMemBox itemBox = new Mp4InMemBox.Builder(dataIndex + 1)
          .putSubBox(dataBoxes[dataIndex])
          .build();
      builder.putSubBox(itemBox);
    }
    return builder.build();
  }

  static Mp4InMemBox createMetaBox(
      Mp4InMemBox hdlr, Mp4InMemBox keys, Mp4InMemBox ilst) {
    return new Mp4InMemBox.Builder(Mp4Utils.toFourCc("meta"))
        .putSubBox(hdlr)
        .putSubBox(keys)
        .putSubBox(ilst)
        .build();
  }

  public static Mp4InMemBox createMetaForOuterClip(long edvdOffset, long edvdLength) {
    Mp4InMemBox hdlrBox = createHdlrBox();
    Mp4InMemBox keysBox = createKeysBox(
        DepthFormat.META_KEY_EDVD_OFFSET,
        DepthFormat.META_KEY_EDVD_LENGTH);
    Mp4InMemBox ilstBox = createIlstBox(
        createMdtaDataBox(DepthFormat.META_TYPE_EDVD_OFFSET, Mp4Utils.i64To8Bytes(edvdOffset)),
        createMdtaDataBox(DepthFormat.META_TYPE_EDVD_LENGTH, Mp4Utils.i64To8Bytes(edvdLength))
    );
    return createMetaBox(hdlrBox, keysBox, ilstBox);
  }

  public static Mp4InMemBox createMetaForInnerClip(byte[] trackTypes) {
    ByteBuffer metaValue = ByteBuffer.allocate(1 + 1 + trackTypes.length);
    metaValue.put((byte) 1); // 1 byte version = 1
    metaValue.put((byte) trackTypes.length); // 1 byte track count = n
    for (byte type : trackTypes) {
      metaValue.put(type); // n bytes for track types
    }

    Mp4InMemBox hdlrBox = createHdlrBox();
    Mp4InMemBox keysBox = createKeysBox(
        DepthFormat.META_KEY_DEPTH_TRACK_TYPES);
    Mp4InMemBox ilstBox = createIlstBox(
        createMdtaDataBox(DepthFormat.META_TYPE_DEPTH_TRACK_TYPES, metaValue.array())
    );
    return createMetaBox(hdlrBox, keysBox, ilstBox);
  }

  static int parseHdlrBox(Mp4InMemBox box) {
    byte[] payload = box.getPayload();
    ByteBuffer buf = ByteBuffer.wrap(payload);
    buf.getInt(); // version, flags
    buf.getInt(); // predefined
    int fourCc = buf.getInt();
    return fourCc;
  }

  /**
   * Parse keys box in meta.
   *
   * @param box the keys box loaded in memory
   * @return a list of keys parsed from keys box
   */
  static ArrayList<String> parseKeysBox(Mp4InMemBox box) {
    byte[] payload = box.getPayload();
    ByteBuffer buf = ByteBuffer.wrap(payload);
    buf.getInt(); // version, flags
    int entryCount = buf.getInt();
    Mp4Box subRoot = Mp4BoxTreeParser.splitBoxes(
        payload, buf.position(), buf.remaining());
    assert (subRoot.getChildren().size() == entryCount);
    ArrayList<String> keys = new ArrayList<>();
    for (Mp4Box subBox : subRoot.getChildren()) {
      Mp4InMemBox inMemBox = (Mp4InMemBox) subBox;
      assert (inMemBox.fourCc == Mp4Utils.toFourCc("mdta")
          || inMemBox.fourCc == Mp4Utils.toFourCc("udta"));
      String key = new String(inMemBox.getPayload());
      Log.v(TAG, "meta key: " + key);
      keys.add(key);
    }
    return keys;
  }

  /**`
   * Parse ilst box in meta.
   *
   * @param box the ilst box loaded in memory
   * @return an array list of (data type, date blob representation)
   */
  static ArrayList<Pair<Integer, byte[]>> parseIlistBox(Mp4InMemBox box) {
    byte[] payload = box.getPayload();
    Mp4BoxTreeParser.ParseRules rules = parser -> {
      int level = parser.getCurrentBoxes().size();
      assert (level > 0);
      return (level == 1)
          ? Mp4BoxTreeParser.ParseRule.parseSubBoxes : // parse sub boxes for boxes at 1st level
          Mp4BoxTreeParser.ParseRule.loadPayload; // load boxes at 2nd level
    };

    Mp4Box subRoot;
    try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
      Mp4BoxTreeParser treeParser = new Mp4BoxTreeParser(input, rules);
      subRoot = treeParser.parse();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    int size = subRoot.getChildren().size();
    assert (size != 0);
    ArrayList<Pair<Integer, byte[]>> values = new ArrayList<>();
    for (int index = 0; index < size; ++index) {
      Mp4Box keyIdBox = subRoot.getChildAt(index);
      assert (keyIdBox.fourCc == index + 1);
      Mp4InMemBox dataBox = (Mp4InMemBox) keyIdBox.findChild(Mp4Utils.toFourCc("data"));
      ByteBuffer buf = ByteBuffer.wrap(dataBox.getPayload());
      int type = buf.getInt();
      buf.getInt(); // country/language
      byte[] value = new byte[buf.remaining()];
      buf.get(value);
      values.add(new Pair<>(type, value));
    }
    return values;
  }

  public static Map<String, Pair<Integer, byte[]>> parseMetaBoxWithMdtaHandler(
      Mp4InMemBox metaBox) {
    Mp4Box metaTreeNode = Mp4BoxTreeParser.splitBoxes(metaBox.getPayload());

    Mp4InMemBox hdlrBox = (Mp4InMemBox) metaTreeNode.findChild(Mp4Utils.toFourCc("hdlr"));
    if (hdlrBox == null) {
      Log.e(TAG, "Cannot find hdlr box in meta box");
      return null;
    }
    int hdlrValue = Mp4MetaUtils.parseHdlrBox(hdlrBox);
    if (hdlrValue != Mp4Utils.toFourCc("mdta")) {
      Log.e(TAG, "meta hdlr is not mdta: " + Mp4Utils.fourCcToStr(hdlrValue));
      return null;
    }

    Mp4InMemBox keysBox = (Mp4InMemBox) metaTreeNode.findChild(Mp4Utils.toFourCc("keys"));
    Mp4InMemBox ilstBox = (Mp4InMemBox) metaTreeNode.findChild(Mp4Utils.toFourCc("ilst"));

    ArrayList<String> keys = Mp4MetaUtils.parseKeysBox(keysBox);
    ArrayList<Pair<Integer, byte[]>> values = Mp4MetaUtils.parseIlistBox(ilstBox);
    assert (keys != null);
    assert (values != null);
    assert (keys.size() != 0);
    assert (keys.size() == values.size());

    Map<String, Pair<Integer, byte[]>> map = new HashMap<>();
    for (int index = 0; index < keys.size(); ++index) {
      map.put(keys.get(index), values.get(index));
    }
    return map;
  }

  // return track types
  public static byte[] parseInnerStaticMeta(byte[] dataBoxPayload) {
    ByteBuffer buf = ByteBuffer.wrap(dataBoxPayload);
    buf.get(); // version
    int trackCount = buf.get(); // track count
    assert (trackCount == buf.remaining());
    byte[] trackTypes = new byte[trackCount];
    buf.get(trackTypes);
    return trackTypes;
  }
}
