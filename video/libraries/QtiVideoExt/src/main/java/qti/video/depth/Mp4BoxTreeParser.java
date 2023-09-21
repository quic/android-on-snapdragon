/*
 **************************************************************************************************
 * Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 **************************************************************************************************
 */

package qti.video.depth;

import android.util.Log;
import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class Mp4BoxTreeParser {
  static final String TAG = "Mp4BoxTreeParser";

  public enum ParseRule {
    bypassPayload,
    loadPayload,
    parseSubBoxes,
  }

  public interface ParseRules {
    ParseRule getCurrentParseRule(Mp4BoxParser parser);
  }

  private final Mp4BoxParser boxParser;
  private final ParseRules parseRules;

  public Mp4BoxTreeParser(DataInput input, ParseRules rules) {
    assert (input != null);
    assert (rules != null);
    boxParser = new Mp4BoxParser(input);
    parseRules = rules;
  }

  public Mp4Box parse() throws IOException {
    // create a virtual root box
    Mp4Box root = new Mp4Box(Mp4Box.BOX_SIZE_UNKNOWN, Mp4Utils.toFourCc("root"));
    root.addChildren(parseTopBoxes());
    return root;
  }

  private List<Mp4Box> parseTopBoxes() throws IOException {
    List<Mp4Box> boxes = new ArrayList<>();
    while (true) {
      Mp4Box box = boxParser.nextBox();
      if (box == null) {
        Log.v(TAG, "last box");
        break;
      }
      ParseRule rule = parseRules.getCurrentParseRule(boxParser);
      if (rule == ParseRule.bypassPayload) {
        boxParser.closeBox();
      } else if (rule == ParseRule.loadPayload) {
        box = boxParser.loadAndCloseBox();
      } else if (rule == ParseRule.parseSubBoxes) {
        List<Mp4Box> subBoxes = parseSubBoxes();
        box.addChildren(subBoxes);
        boxParser.closeBox();
      } else {
        assert (false);
      }
      boxes.add(box);
    }
    return boxes;
  }

  private List<Mp4Box> parseSubBoxes() throws IOException {
    List<Mp4Box> boxes = new ArrayList<>();
    while (!boxParser.shouldCloseParent()) {
      Mp4Box box = boxParser.nextBox();
      assert (box != null);
      ParseRule rule = parseRules.getCurrentParseRule(boxParser);
      if (rule == ParseRule.bypassPayload) {
        boxParser.closeBox();
      } else if (rule == ParseRule.loadPayload) {
        box = boxParser.loadAndCloseBox();
      } else if (rule == ParseRule.parseSubBoxes) {
        List<Mp4Box> subBoxes = parseSubBoxes();
        box.addChildren(subBoxes);
        boxParser.closeBox();
      } else {
        assert (false);
      }
      boxes.add(box);
    }
    return boxes;
  }

  /**
   * Parse boxes tree, load /moov/meta and /edvd/moov/meta boxes if present.
   *
   * @param input data input
   * @return a virtual root box
   * @apiNote payload of meta box is not loaded
   */
  public static Mp4Box parseForMeta(DataInput input) {
    ParseRules rules = parser -> {
      if (parser.checkBoxStack(Mp4Box.FOURCC_MOOV)) {
        return ParseRule.parseSubBoxes;
      }
      if (parser.checkBoxStack(Mp4Box.FOURCC_MOOV, Mp4Box.FOURCC_META)) {
        return ParseRule.loadPayload;
      }
      if (parser.checkBoxStack(Mp4Box.FOURCC_EDVD)
          || parser.checkBoxStack(Mp4Box.FOURCC_EDVD, Mp4Box.FOURCC_MOOV)) {
        return ParseRule.parseSubBoxes;
      }
      if (parser.checkBoxStack(Mp4Box.FOURCC_EDVD, Mp4Box.FOURCC_MOOV, Mp4Box.FOURCC_META)) {
        return ParseRule.loadPayload;
      }
      return ParseRule.bypassPayload;
    };

    try {
      Mp4BoxTreeParser treeParser = new Mp4BoxTreeParser(input, rules);
      return treeParser.parse();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Parse the buffer and load the first level boxes into memory.
   *
   * @param data A input buffer to be parsed.
   * @param offset The byte offset into the input buffer at which the data starts.
   * @param length The number of bytes of valid input data.
   * @return A virtual box of the parsed box tree. It only has 1 level boxes and all of them are
   *     Mp4InMemBox.
   */
  public static Mp4Box splitBoxes(byte[] data, int offset, int length) {
    ParseRules rules = parser -> ParseRule.loadPayload;

    try (DataInputStream input
             = new DataInputStream(new ByteArrayInputStream(data, offset, length))) {
      Mp4BoxTreeParser treeParser = new Mp4BoxTreeParser(input, rules);
      return treeParser.parse();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Parse the buffer and load the first level boxes into memory.
   *
   * @param data A input buffer to be parsed.
   * @return A virtual box of the parsed box tree. It only has 1 level boxes and all of them are
   *     Mp4InMemBox.
   */
  public static Mp4Box splitBoxes(byte[] data) {
    return splitBoxes(data, 0, data.length);
  }
}
