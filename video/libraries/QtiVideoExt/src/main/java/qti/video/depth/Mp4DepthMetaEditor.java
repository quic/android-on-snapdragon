/*
 **************************************************************************************************
 * Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 **************************************************************************************************
 */

package qti.video.depth;

import android.util.Log;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Editor for depth outer clip metadata and inner clip metadata. It also merged the inner clip into
 * the outer clip.
 */
class Mp4DepthMetaEditor {
  private static final String TAG = "Mp4DepthMetaEditor";
  private final RandomAccessFile outerFile;
  private final RandomAccessFile innerFile;

  enum EditType {
    cannotEdit,
    moovBeforeFree,
    moovAtFileEnd;
  }

  /**
   * Constructor of Mp4DepthMetaEditor.
   *
   * @param outerFile RandomAccessFile for the outer clip. It must be opened in rw mode.
   * @param innerFile RandomAccessFile for the inner clip. It must be opened in rw mode.
   */
  public Mp4DepthMetaEditor(RandomAccessFile outerFile, RandomAccessFile innerFile) {
    this.outerFile = outerFile;
    this.innerFile = innerFile;
  }

  /**
   * Edit the static metadata in outer clip.
   *
   * @return if the edit was successful.
   * @throws IOException if any IO error happens.
   * @apiNote this method should be called after {@link #editInnerClip} because the static metadata
   *     depends on the size of inner clip, and {@link #editInnerClip} may change the inner clip
   *     size.
   */
  public boolean editOuterClip() throws IOException {
    Mp4Box root = Mp4BoxTreeParser.parseForMeta(outerFile);
    EditType editType = howToEdit(root);
    assert (editType != EditType.cannotEdit);
    Mp4Box meta = root.findChild(Mp4Box.FOURCC_MOOV).findChild(Mp4Box.FOURCC_META);

    long outerClipSize = outerFile.length();
    long innerClipSize = innerFile.length();
    Mp4Box edvd = Mp4Box.createForPayloadSize(Mp4Box.FOURCC_EDVD, innerClipSize);
    Mp4InMemBox newMeta = createOuterMeta(
        (Mp4InMemBox) meta, outerClipSize, edvd.getSize());
    if (editType == EditType.moovAtFileEnd) {
      // if the new meta box will be appended to file end, we need to add the meta box size to
      // edvd offset
      newMeta = createOuterMeta(
          (Mp4InMemBox) meta, outerClipSize + newMeta.getSize(), edvd.getSize());
    }
    updateMeta(editType, outerFile, root, newMeta);
    return true;
  }

  /**
   * Edit the static metadata in inner clip.
   *
   * @return if the edit was successful
   * @throws IOException if any IO error happens.
   */
  public boolean editInnerClip(byte[] trackTypes) throws IOException {
    Mp4Box root = Mp4BoxTreeParser.parseForMeta(innerFile);
    EditType editType = howToEdit(root);
    assert (editType != EditType.cannotEdit);
    Mp4Box meta = root.findChild(Mp4Box.FOURCC_MOOV).findChild(Mp4Box.FOURCC_META);
    Mp4InMemBox newMeta = createInnerMeta((Mp4InMemBox) meta, trackTypes);
    updateMeta(editType, innerFile, root, newMeta);
    return true;
  }

  /**
   * Merge the inner clip into the outer clip.
   *
   * @return true if it is successful.
   * @throws IOException if any IO error happens.
   */
  public boolean mergeClip() throws IOException {
    long innerClipSize = innerFile.length();
    Mp4Box edvd = Mp4Box.createForPayloadSize(Mp4Box.FOURCC_EDVD, innerClipSize);
    outerFile.seek(outerFile.length());
    innerFile.seek(0);
    outerFile.write(edvd.getHeaderBlob());

    // buffer size has large impact on merge latency
    // 128KB+ has minimal latency (197ms in a test). 64KB is also good (223ms in a test).
    byte[] buf = new byte[1024 * 128];
    int readSize;
    while ((readSize = innerFile.read(buf)) != -1) {
      outerFile.write(buf, 0, readSize);
    }
    return true;
  }

  static EditType howToEdit(Mp4Box root) {
    int moovIndex = root.indexOfChild(Mp4Box.FOURCC_MOOV);
    if (moovIndex == -1) {
      Log.e(TAG, "moov is not found");
      return EditType.cannotEdit;
    }
    if (moovIndex == root.getChildren().size() - 1) {
      Log.v(TAG, "moov at file end");
      return EditType.moovAtFileEnd;
    }
    Mp4Box moov = root.getChildAt(moovIndex);
    if (moov.indexOfChild(Mp4Box.FOURCC_META) == -1) {
      Log.w(TAG, "No meta box in moov");
      // the clip doesn't have a meta, we can simply add a new one.
    }
    Mp4Box freeBox = root.getChildren().get(moovIndex + 1);
    if (freeBox.fourCc != Mp4Box.FOURCC_FREE) {
      Log.e(TAG, "Require free box after moov box");
      return EditType.cannotEdit;
    }
    if (freeBox.getSize() < 512) {
      // TODO 1: check exact free box size with new meta xo size
      // TODO 2: if free box is too small or no free box after moov, we can replace the whole moov
      // with a free box, and edit/move the moov to file end
      Log.e(TAG, "free box too small " + freeBox.getSize());
      return EditType.cannotEdit;
    }
    return EditType.moovBeforeFree;
  }

  private Mp4InMemBox createOuterMeta(
      Mp4InMemBox origMeta, long edvdOffset, long edvdLength) {
    // TODO: read original meta and append
    return Mp4MetaUtils.createMetaForOuterClip(edvdOffset, edvdLength);
  }

  private Mp4InMemBox createInnerMeta(Mp4InMemBox origMeta, byte[] trackTypes) {
    // TODO: read original meta and append
    return Mp4MetaUtils.createMetaForInnerClip(trackTypes);
  }

  private void updateMeta(EditType editType,
                          RandomAccessFile randomAccess,
                          Mp4Box root, Mp4InMemBox meta) throws IOException {
    assert (editType != EditType.cannotEdit);
    Mp4Box oldMoov = root.findChild(Mp4Box.FOURCC_MOOV);
    Mp4Box oldMeta = oldMoov.findChild(Mp4Box.FOURCC_META);
    Mp4Box oldFree = root.findChild(Mp4Box.FOURCC_FREE);
    int newMetaSize = (int) meta.getSize();

    // 1. replace moov/meta with a free box of same size
    Mp4FreeBox freeForMeta = Mp4FreeBox.createFreeBox((int) oldMeta.getSize());
    freeForMeta.setFileOffset(oldMeta.getFileOffset());
    randomAccess.seek(freeForMeta.getFileOffset());
    randomAccess.write(freeForMeta.getHeaderBlob());
    randomAccess.write(freeForMeta.getPayload());

    // 2. append meta to the end of moov
    meta.setFileOffset(oldMoov.getFileOffset() + oldMoov.getSize());
    randomAccess.seek(meta.getFileOffset());
    randomAccess.write(meta.getHeaderBlob());
    randomAccess.write(meta.getPayload());

    // 3. update moov box size
    assert (oldMoov.isCompact());
    Mp4Box newMoov = new Mp4Box(oldMoov.getSize() + newMetaSize, Mp4Box.FOURCC_MOOV);
    newMoov.setFileOffset(oldMoov.getFileOffset());
    // write moov header to update size
    randomAccess.seek(newMoov.getFileOffset());
    randomAccess.write(newMoov.getHeaderBlob());

    // 4. if moov is before a free box, shrink free box size and update offset
    if (editType == EditType.moovBeforeFree) {
      assert (oldFree.getSize() >= (newMetaSize + 8));
      Mp4FreeBox newFree = Mp4FreeBox.createFreeBox(oldFree.getSize() - newMetaSize);
      newFree.setFileOffset(oldFree.getFileOffset() + newMetaSize);
      randomAccess.seek(newFree.getFileOffset());
      randomAccess.write(newFree.getHeaderBlob());
      randomAccess.write(newFree.getPayload()); // optional
    }
  }
}
