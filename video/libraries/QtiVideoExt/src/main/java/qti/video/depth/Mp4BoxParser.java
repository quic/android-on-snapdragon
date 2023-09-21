/*
 **************************************************************************************************
 * Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 **************************************************************************************************
 */

package qti.video.depth;

import java.io.DataInput;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

final class Mp4BoxParser {
  private final DataInput dataInput;
  private long currentOffset = 0;
  private final Stack<Mp4Box> currentBoxes = new Stack<>();

  public Mp4BoxParser(DataInput input) {
    dataInput = input;
  }

  /**
   * Parse next box header. Only read Mp4Box header.
   * When this method returns, the input stream position is at the beginning.
   * Even if the current box doesn't have payload, it's still not closed until closeBox() or
   * onBoxClosedExternally() is called.
   * of the box payload.
   *
   * @return next box
   * @throws IOException if any IO error happens
   */
  public Mp4Box nextBox() throws IOException {
    Mp4Box box = Mp4Box.parseHeader(dataInput);
    if (box == null) {
      return null;
    }
    box.setFileOffset(currentOffset);
    increaseOffset(box.getHeaderSize());
    currentBoxes.add(box); // current box is not closed even if it doesn't have payload
    return box;
  }

  /**
   * Read current box payload and reach next box header.
   *
   * @return payload size read in the splitter.
   * @apiNote parent boxes may also be closed if current box is the last child of the parent.
   */
  public long closeBox() throws IOException {
    Mp4Box box = currentBoxes.pop();
    long needSkip = box.getFileOffset() + box.getSize() - currentOffset;
    int skipped = dataInput.skipBytes((int) needSkip);
    assert (skipped == needSkip);
    increaseOffset(skipped);
    return skipped;
  }

  /**
   * Read the box into memory and close the box in parser.
   *
   * @return the box payload
   * @throws IOException if IO error happens
   */
  public Mp4InMemBox loadAndCloseBox() throws IOException {
    Mp4Box box = currentBoxes.pop();
    assert (currentOffset == box.getFileOffset() + box.getHeaderSize());
    long payloadSize = box.getPayloadSize();
    assert (payloadSize <= 1024 * 1024); // don't allocate too large memory
    byte[] payload = new byte[(int) payloadSize];
    dataInput.readFully(payload);
    increaseOffset(payloadSize);
    return new Mp4InMemBox(box, payload);
  }

  /**
   * Check if client needs to call closeBox() again to close parent box.
   *
   * @return true if the parent box must be closed
   */
  public boolean shouldCloseParent() {
    if (currentBoxes.empty()) {
      return false;
    }
    Mp4Box box = currentBoxes.peek();
    assert (currentOffset <= box.getFileOffset() + box.getSize());
    return currentOffset == (box.getFileOffset() + box.getSize());
  }

  private void increaseOffset(long delta) {
    currentOffset += delta;
  }

  long getCurrentOffset() {
    return currentOffset;
  }

  /**
   * Get current box stack.
   *
   * @return current box stack
   */
  public List<Mp4Box> getCurrentBoxes() {
    return Collections.unmodifiableList(currentBoxes);
  }

  /**
   * Get current box. The input stream position is at the beginning of this box payload.
   *
   * @return current box. If box is closed and nextBox() is not called yet, it may return null.
   */
  public Mp4Box getCurrentBox() {
    return currentBoxes.empty() ? null : currentBoxes.peek();
  }

  /**
   * Compare the current box stack with the parameters.
   *
   * @param fourCcs for boxes to be compared with current box stack
   * @return true if current box stack is the same as the parameters.
   */
  public boolean checkBoxStack(int... fourCcs) {
    if (fourCcs.length != currentBoxes.size()) {
      return false;
    }
    for (int index = 0; index < fourCcs.length; ++index) {
      if (fourCcs[index] != currentBoxes.get(index).fourCc) {
        return false;
      }
    }
    return true;
  }
}
