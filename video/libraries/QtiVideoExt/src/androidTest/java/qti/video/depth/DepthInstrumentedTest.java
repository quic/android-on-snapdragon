/*
 **************************************************************************************************
 * Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 **************************************************************************************************
 */

package qti.video.depth;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import android.util.Pair;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Android instrumented unit test for depth muxer and extractor.
 */
@RunWith(AndroidJUnit4ClassRunner.class)
public class DepthInstrumentedTest {

  static final String TAG = "DepthInstrumentedTest";

  static final String CLIP_SERVER_LOCATION
      = "\\\\armory\\videocoreswdev1\\Dropbox\\videoNext"
      + "\\Lanai\\depth\\unittest_data";

  private static Context context;
  // /storage/emulated/0/Android/data/qti.video.test/files
  private static File externalFileDir;
  private static File origClipWithNoDepth;

  // track 0: video/avc, video, UHD
  // track 1: video/hevc, depth, 512x288
  // track 3: audio/mp4a-latm, audio
  // track 4: application/octet-stream, metadata?
  private static File videoDepthAudioMetaClip;

  private static long testId;
  private static File outerClip;
  private static File innerClip;

  // sample meta box from camcorder_no_depth.mp4:
  //   handler: mdta. count: 1.
  //   key: com.android.version, value: "13", value type UTF-8 (1).
  private static final byte[] sampleMetaBoxHeader
      = new byte[] {0x00, 0x00, 0x00, 0x76, 0x6D, 0x65, 0x74, 0x61, };
  private static final byte[] sampleMetaBoxPayload = new byte[] {
      0x00, 0x00, 0x00, 0x21, 0x68, 0x64, 0x6C, 0x72, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x6D, 0x64, 0x74, 0x61, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x2B, 0x6B, 0x65, 0x79, 0x73, 0x00, 0x00,
      0x00,
      0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x1B, 0x6D, 0x64, 0x74, 0x61, 0x63, 0x6F,
      0x6D,
      0x2E, 0x61, 0x6E, 0x64, 0x72, 0x6F, 0x69, 0x64, 0x2E, 0x76, 0x65, 0x72, 0x73, 0x69, 0x6F,
      0x6E,
      0x00, 0x00, 0x00, 0x22, 0x69, 0x6C, 0x73, 0x74, 0x00, 0x00, 0x00, 0x1A, 0x00, 0x00, 0x00,
      0x01,
      0x00, 0x00, 0x00, 0x12, 0x64, 0x61, 0x74, 0x61, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00,
      0x00,
      0x31, 0x33, };

  /**
   * Check clips for testing.
   */
  @BeforeClass
  public static void prepareClips() {
    context = InstrumentationRegistry.getInstrumentation().getContext();
    externalFileDir = context.getExternalFilesDir(null);
    Log.e(TAG, "test clip folder: " + externalFileDir.getAbsolutePath());
    origClipWithNoDepth = new File(externalFileDir, "camcorder_no_depth.mp4");
    if (!origClipWithNoDepth.exists()) {
      String msg = "test clip [" + origClipWithNoDepth.getName()
          + "] doesn't exist, please push it to device\n"
          + "cmd: adb push "
          + CLIP_SERVER_LOCATION + "\\" + origClipWithNoDepth.getName()
          + " " + origClipWithNoDepth.getAbsolutePath();
      fail(msg);
    }

    videoDepthAudioMetaClip = new File(externalFileDir, "VideoDepthAudioMeta.MOV");
    if (!videoDepthAudioMetaClip.exists()) {
      String msg = "test clip [" + videoDepthAudioMetaClip.getName()
          + "] doesn't exist, please push it to device\n"
          + "cmd: adb push "
          + CLIP_SERVER_LOCATION + "\\" + videoDepthAudioMetaClip.getName()
          + " " + videoDepthAudioMetaClip.getAbsolutePath();
      fail(msg);
    }

    testId = System.currentTimeMillis();
    Log.e(TAG, "test ID: " + testId);
    outerClip = new File(externalFileDir, "outerClip-" + testId + ".mp4");
    innerClip = new File(externalFileDir, "innerClip-" + testId + ".mp4");
    try {
      Files.copy(origClipWithNoDepth.toPath(), outerClip.toPath());
      Files.copy(origClipWithNoDepth.toPath(), innerClip.toPath());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testMp4Utils() {
    assertEquals((int) 0x05060708, (int) Mp4Utils.i64ToU32(0x0102030405060708L));
    assertEquals((int) 0xF5060708, (int) Mp4Utils.i64ToU32(0x01020304F5060708L));

    assertEquals(0x05060708L, Mp4Utils.u32ToI64(0x05060708));
    assertEquals(0xF5060708L, Mp4Utils.u32ToI64(0xF5060708));
    assertTrue(Mp4Utils.u32ToI64(0xF5060708) > 0);

    assertArrayEquals(new byte[]{5, 6, 7, 8}, Mp4Utils.u32To4Bytes(0x05060708));
    assertArrayEquals(new byte[]{(byte) 0xF5, 6, 7, 8}, Mp4Utils.u32To4Bytes(0xF5060708));
    assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8},
        Mp4Utils.i64To8Bytes(0x0102030405060708L));
    assertArrayEquals(new byte[]{(byte) 0xF1, 2, 3, 4, 5, 6, 7, 8},
        Mp4Utils.i64To8Bytes(0xF102030405060708L));

    assertEquals(0x05060708, Mp4Utils.u32From4Bytes(new byte[]{5, 6, 7, 8}));
    assertEquals(0xF5060708, Mp4Utils.u32From4Bytes(new byte[]{(byte) 0xF5, 6, 7, 8}));

    assertEquals(0x01020304, Mp4Utils.toFourCc("\u0001\u0002\u0003\u0004"));
    assertEquals(Mp4Utils.u32From4Bytes(new byte[]{'z', 'a', 'b', 'c'}),
        Mp4Utils.toFourCc("zabc"));
    assertEquals(Mp4Utils.u32From4Bytes(new byte[]{'z', 'a', 'b', 'c'}),
        Mp4Utils.toFourCc(new byte[]{'z', 'a', 'b', 'c'}));

    assertEquals("zabc", Mp4Utils.fourCcToStr(Mp4Utils.toFourCc("zabc")));
  }

  @Test
  public void testMp4Box() {
    Mp4Box compactBox = new Mp4Box(0x1234, Mp4Box.FOURCC_MOOV);
    Mp4Box dupBox = new Mp4Box(compactBox);
    for (Mp4Box box : new Mp4Box[]{compactBox, dupBox}) {
      assertTrue(box.isCompact());
      assertEquals(0x1234, box.getSize());
      assertEquals(Mp4Box.SIZE_COMPACT_BOX_HEADER, box.getHeaderSize());
      assertEquals(0x1234 - 8, box.getPayloadSize());
      assertArrayEquals(new byte[]{0, 0, 0x12, 0x34, 'm', 'o', 'o', 'v'}, box.getHeaderBlob());
      assertEquals(-1, box.getFileOffset());
      box.setFileOffset(3421);
      assertEquals(3421, box.getFileOffset());

      assertNull(box.getChildren());
      box.addChild(new Mp4Box(0x12, Mp4Box.FOURCC_META));
      box.addChild(new Mp4Box(0x12, Mp4Box.FOURCC_FREE));
      assertNotNull(box.getChildren());
      assertEquals(2, box.getChildren().size());
      assertNotNull(box.getChildAt(0));
      assertEquals(Mp4Box.FOURCC_META, box.getChildAt(0).fourCc);
      assertNotNull(box.getChildAt(1));
      assertEquals(Mp4Box.FOURCC_FREE, box.getChildAt(1).fourCc);
      try {
        box.getChildAt(2);
        fail();
      } catch (IndexOutOfBoundsException ignored) {
        // expected
      }
      assertEquals(0, box.indexOfChild(Mp4Box.FOURCC_META));
      assertEquals(1, box.indexOfChild(Mp4Box.FOURCC_FREE));
      assertEquals(-1, box.indexOfChild(Mp4Box.FOURCC_EDVD));
    }
  }

  // long[] boxInfo: // {fourCc, size, header size, offset, shouldCloseParent}
  private static void validateParsingNextBox(Mp4BoxParser parser, long[] boxInfo)
      throws IOException {
    int fourCc = (int) boxInfo[0];
    long boxSize = boxInfo[1];
    long headerSize = boxInfo[2];
    long payloadSize = boxSize - headerSize;
    long boxOffset = boxInfo[3];
    boolean shouldCloseParent = (boxInfo[4] != 0);

    assertEquals(boxOffset, parser.getCurrentOffset());
    Mp4Box box = parser.nextBox();
    assertNotNull(box);
    assertEquals(fourCc, box.fourCc);
    assertEquals(boxSize, box.getSize());
    assertEquals(headerSize, box.getHeaderSize());
    assertEquals(payloadSize, box.getPayloadSize());
    assertEquals(payloadSize, parser.closeBox());
    assertEquals(shouldCloseParent, parser.shouldCloseParent());
  }

  @Test
  public void testMp4BoxParser() {
    try (RandomAccessFile inputFile = new RandomAccessFile(origClipWithNoDepth, "r")) {
      long[][] boxesInfo = new long[][] {
          // {fourCc, size, header size, offset, shouldCloseParent}
          new long[] {Mp4Utils.toFourCc("ftyp"), 0x18, 8, 0, 0},
          new long[] {Mp4Utils.toFourCc("moov"), 0x062E, 8, 0x18, 0},
          new long[] {Mp4Utils.toFourCc("free"), 0x062852, 8, 0x18 + 0x062E, 0},
          new long[] {Mp4Utils.toFourCc("mdat"), 0x20C28E, 16, 0x18 + 0x062E + 0x062852, 0},
      };

      Mp4BoxParser parser = new Mp4BoxParser(inputFile);
      for (long[] boxInfo : boxesInfo) {
        validateParsingNextBox(parser, boxInfo);
      }
      Mp4Box noMoreBox = parser.nextBox();
      assertNull(noMoreBox);
    } catch (IOException e) {
      fail();
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testMp4BoxParserNested() {
    try (RandomAccessFile inputFile = new RandomAccessFile(origClipWithNoDepth, "r")) {
      Mp4BoxParser parser = new Mp4BoxParser(inputFile);
      parser.nextBox(); // ftyp box
      parser.closeBox();
      parser.nextBox(); // moov box

      long[][] moovSubBoxesInfo = new long[][] {
          // {fourCc, size, header size, offset, shouldCloseParent}
          new long[] {Mp4Utils.toFourCc("mvhd"), 0x6C, 8, 0x18 + 8, 0},
          new long[] {Mp4Utils.toFourCc("meta"), 0x76, 8, 0x18 + 8 + 0x6C, 0},
          new long[] {Mp4Utils.toFourCc("trak"), 0x0281, 8, 0x18 + 8 + 0x6C + 0x76, 0},
          new long[] {Mp4Utils.toFourCc("trak"), 0x02C3, 8, 0x18 + 8 + 0x6C + 0x76 + 0x0281, 1},
      };
      // check nested boxes in moov
      for (long[] boxInfo : moovSubBoxesInfo) {
        validateParsingNextBox(parser, boxInfo);
      }
      parser.closeBox(); // close moov box

      long[][] boxesInfo = new long[][] {
          // {fourCc, size, header size, offset, shouldCloseParent}
          new long[] {Mp4Utils.toFourCc("free"), 0x062852, 8, 0x18 + 0x062E, 0},
          new long[] {Mp4Utils.toFourCc("mdat"), 0x20C28E, 16, 0x18 + 0x062E + 0x062852, 0},
      };

      for (long[] boxInfo : boxesInfo) {
        validateParsingNextBox(parser, boxInfo);
      }
      Mp4Box noMoreBox = parser.nextBox();
      assertNull(noMoreBox);
    } catch (IOException e) {
      fail();
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testMp4DepthMetaEditor() {
    long modifiedOuterFileSize;
    long modifiedInnerFileSize;
    try (RandomAccessFile outerF = new RandomAccessFile(outerClip, "rw");
         RandomAccessFile innerF = new RandomAccessFile(innerClip, "rw")) {
      Mp4Box outerRoot = Mp4BoxTreeParser.parseForMeta(outerF);
      assertNotNull(outerRoot);
      assertNotNull(outerRoot.getChildren());
      assertEquals(4, outerRoot.getChildren().size()); // ftyp, moov, free, mdat
      assertSame(Mp4DepthMetaEditor.EditType.moovBeforeFree,
          Mp4DepthMetaEditor.howToEdit(outerRoot));

      Mp4Box moovBox = outerRoot.findChild(Mp4Box.FOURCC_MOOV);
      assertNotNull(moovBox);
      assertEquals(4, moovBox.getChildren().size());

      Mp4Box metaBox = moovBox.findChild(Mp4Box.FOURCC_META);
      assertNotNull(metaBox);
      assertTrue(metaBox instanceof Mp4InMemBox);
      Mp4InMemBox metaBoxInMem = (Mp4InMemBox) metaBox;
      assertEquals(0x76, metaBoxInMem.getSize());
      assertEquals(8, metaBoxInMem.getHeaderSize());
      assertEquals(0x76 - 8, metaBoxInMem.getPayload().length);

      outerF.seek(0);
      innerF.seek(0);
      Mp4DepthMetaEditor editor = new Mp4DepthMetaEditor(outerF, innerF);
      assertTrue(editor.editOuterClip());
      modifiedOuterFileSize = outerF.length();
      assertTrue(editor.editInnerClip(new byte[]{0, 1}));
      modifiedInnerFileSize = innerF.length();
      assertTrue(editor.mergeClip());

    } catch (IOException e) {
      fail();
      throw new RuntimeException(e);
    }

    long origFileLength = origClipWithNoDepth.length();
    assertEquals(origFileLength * 2 + 8, outerClip.length());
    assertEquals(origFileLength, innerClip.length());
    // check modified outer and inner clips
    try (RandomAccessFile outerF = new RandomAccessFile(outerClip, "r");
         RandomAccessFile innerF = new RandomAccessFile(innerClip, "r")) {

      long[] edvdBoxInfo = Mp4DepthMetaParser.parseOuterClipMeta(outerF);
      assertNotNull(edvdBoxInfo);
      assertEquals(3, edvdBoxInfo.length);
      final long edvdOffset = edvdBoxInfo[0];
      final long edvdSize = edvdBoxInfo[1];
      final long edvdHeaderSize = edvdBoxInfo[2];
      assertEquals(modifiedOuterFileSize, edvdOffset);
      assertEquals(8, edvdHeaderSize);
      assertEquals(modifiedInnerFileSize, edvdSize - edvdHeaderSize);

      byte[] innerStaticMetadata = Mp4DepthMetaParser.parseInnerClipMeta(innerF);
      assertArrayEquals(new byte[]{ 1, // version
          2, // track count
          0, 1 // track types
          }, innerStaticMetadata);

    } catch (IOException e) {
      fail();
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testMetaBoxCreation() {
    Mp4InMemBox hdlrBox = Mp4MetaUtils.createHdlrBox();
    Mp4InMemBox keysBox = Mp4MetaUtils.createKeysBox("com.android.version");
    Mp4InMemBox dataBox = Mp4MetaUtils.createMdtaDataBox(1, // 1: value type UTF-8
        "13".getBytes(StandardCharsets.UTF_8));
    Mp4InMemBox ilstBox = Mp4MetaUtils.createIlstBox(dataBox);
    Mp4InMemBox metaBox = Mp4MetaUtils.createMetaBox(hdlrBox, keysBox, ilstBox);
    assertEquals(sampleMetaBoxHeader.length, metaBox.getHeaderSize());
    assertEquals(sampleMetaBoxPayload.length, metaBox.getPayloadSize());
    byte[] headerBlob = metaBox.getHeaderBlob();
    byte[] payload = metaBox.getPayload();
    Assert.assertArrayEquals(sampleMetaBoxHeader, headerBlob);
    Assert.assertArrayEquals(sampleMetaBoxPayload, payload);
  }

  @Test
  public void testDepthMuxerAndExtractor() {
    MediaExtractor extractor = new MediaExtractor();
    try {
      final int inVideoTrack = 0;
      final int inDepthTrack = 1;
      final int inAudioTrack = 2;
      final int inMetadataTrack = 3;

      extractor.setDataSource(videoDepthAudioMetaClip.getPath());
      assertEquals(4, extractor.getTrackCount());
      MediaFormat inVideoFormat = extractor.getTrackFormat(inVideoTrack);
      MediaFormat inDepthFormat = extractor.getTrackFormat(inDepthTrack);
      MediaFormat inAudioFormat = extractor.getTrackFormat(inAudioTrack);
      MediaFormat inMetadataFormat = extractor.getTrackFormat(inMetadataTrack);
      assertEquals("video/avc", inVideoFormat.getString(MediaFormat.KEY_MIME));
      assertEquals("video/hevc", inDepthFormat.getString(MediaFormat.KEY_MIME));
      assertEquals("audio/mp4a-latm", inAudioFormat.getString(MediaFormat.KEY_MIME));
      assertEquals("application/octet-stream", inMetadataFormat.getString(MediaFormat.KEY_MIME));
      extractor.selectTrack(inVideoTrack);
      extractor.selectTrack(inDepthTrack);
      extractor.selectTrack(inAudioTrack);
      extractor.selectTrack(inMetadataTrack);

      final int outVideoTrack;
      MediaFormat outVideoFormat = new MediaFormat(inVideoFormat);
      final int outAudioTrack;
      MediaFormat outAudioFormat = new MediaFormat(inAudioFormat);

      final int outInnerVideoTrack;
      MediaFormat outInnerVideoFormat = new MediaFormat(inVideoFormat);
      outInnerVideoFormat.setInteger(DepthFormat.KEY_TRACK_TYPE,
          DepthFormat.TRACK_TYPE_SHARP_VIDEO);
      final int outInnerDepthTrack;
      MediaFormat outInnerDepthFormat = new MediaFormat(inDepthFormat);
      outInnerDepthFormat.setInteger(DepthFormat.KEY_TRACK_TYPE,
          DepthFormat.TRACK_TYPE_DEPTH_LINEAR);
      final int outInnerMetadataTrack;
      MediaFormat outInnerMetadataFormat = new MediaFormat(inMetadataFormat);
      outInnerMetadataFormat.setInteger(DepthFormat.KEY_TRACK_TYPE,
          DepthFormat.TRACK_TYPE_METADATA);

      File outputF = new File(externalFileDir, "output-" + testId + ".mp4");
      DepthMuxer muxer = new DepthMuxer(outputF.getPath());

      outAudioTrack = muxer.addTrack(outAudioFormat);
      assertEquals(0, outAudioTrack);
      final DepthMuxer.TrackInfo outAudioTrackInfo = muxer.getTrackInfo(outAudioTrack);
      assertEquals(0, outAudioTrackInfo.internalTrackId);

      outInnerVideoTrack = muxer.addTrack(outInnerVideoFormat);
      assertEquals(1, outInnerVideoTrack);
      final DepthMuxer.TrackInfo outInnerVideoTrackInfo = muxer.getTrackInfo(outInnerVideoTrack);
      assertEquals(0, outInnerVideoTrackInfo.internalTrackId);

      outInnerDepthTrack = muxer.addTrack(outInnerDepthFormat);
      assertEquals(2, outInnerDepthTrack);
      final DepthMuxer.TrackInfo outInnerDepthTrackInfo = muxer.getTrackInfo(outInnerDepthTrack);
      assertEquals(1, outInnerDepthTrackInfo.internalTrackId);

      outInnerMetadataTrack = muxer.addTrack(outInnerMetadataFormat);
      assertEquals(3, outInnerMetadataTrack);
      final DepthMuxer.TrackInfo outInnerMetadataTrackInfo
          = muxer.getTrackInfo(outInnerMetadataTrack);
      assertEquals(2, outInnerMetadataTrackInfo.internalTrackId);

      outVideoTrack = muxer.addTrack(outVideoFormat);
      assertEquals(4, outVideoTrack);
      final DepthMuxer.TrackInfo outVideoTrackInfo = muxer.getTrackInfo(outVideoTrack);
      assertEquals(1, outVideoTrackInfo.internalTrackId);

      muxer.start();

      ByteBuffer buf = ByteBuffer.allocate(8 * 1024 * 1024);
      MediaCodec.BufferInfo bufInfo = new MediaCodec.BufferInfo();

      while (extractor.getSampleTrackIndex() != -1) {
        buf.clear();
        buf.limit(0);
        extractor.readSampleData(buf, 0);

        int flags = extractor.getSampleFlags();
        int offset = 0;
        long pts = extractor.getSampleTime();
        int size = (int) extractor.getSampleSize();
        bufInfo.set(offset, size, pts, flags);

        int inTrackId = extractor.getSampleTrackIndex();
        int[] outTracks;
        switch (inTrackId) {
          case inVideoTrack:
            outTracks = new int[] {outVideoTrack, outInnerVideoTrack};
            break;
          case inDepthTrack:
            outTracks = new int[] {outInnerDepthTrack};
            break;
          case inAudioTrack:
            outTracks = new int[] {outAudioTrack};
            break;
          case inMetadataTrack:
            outTracks = new int[] {outInnerMetadataTrack};
            break;
          default:
            fail("unknown input track id " + inTrackId);
            throw new RuntimeException();
        }
        for (int track : outTracks) {
          muxer.writeSampleData(track, buf, bufInfo);
        }
        extractor.advance();
      }
      muxer.stop();
      muxer.release();

      printBoxInfo(outputF);
      subtestBoxTreeParser(outputF);
      subtestDepthExtractor(outputF);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // called by testDepthMuxerAndExtractor
  private void subtestBoxTreeParser(File mergedClip) throws IOException {
    Mp4Box root;
    try (RandomAccessFile inputFile = new RandomAccessFile(mergedClip, "r")) {
      root = Mp4BoxTreeParser.parseForMeta(inputFile);
    }

      {
        Mp4Box moovBox = root.findChild(Mp4Box.FOURCC_MOOV);
        assertNotNull(moovBox);
        Mp4Box metaBox = moovBox.findChild(Mp4Box.FOURCC_META);
        assertNotNull(metaBox);
        assertTrue(metaBox instanceof Mp4InMemBox);
      }
      {
        Mp4Box edvdBox = root.findChild(Mp4Box.FOURCC_EDVD);
        assertNotNull(edvdBox);
        Mp4Box moovBox = edvdBox.findChild(Mp4Box.FOURCC_MOOV);
        assertNotNull(moovBox);
        Mp4Box metaBox = moovBox.findChild(Mp4Box.FOURCC_META);
        assertNotNull(metaBox);
        assertTrue(metaBox instanceof Mp4InMemBox);
      }
  }

  private static void printBoxInfo(File clip) {
    try (RandomAccessFile inputFile = new RandomAccessFile(clip, "r")) {
      Mp4Box root = Mp4BoxTreeParser.parseForMeta(inputFile);
      printBoxTree("", root);
    } catch (IOException e) {
      fail();
      throw new RuntimeException(e);
    }
  }

  private static void printBoxTree(String baseName, Mp4Box box) {
    List<Mp4Box> children = box.getChildren();
    if (children == null || children.size() == 0) {
      return;
    }
    for (Mp4Box child : children) {
      String boxName = baseName + "/" + Mp4Utils.fourCcToStr(child.fourCc);
      String msg = String.format("Box info: %s offset [%d] size [%d] header size [%d]",
          boxName,
          child.getFileOffset(), child.getSize(), child.getHeaderSize());
      Log.v(TAG, msg);
      printBoxTree(boxName, child);
    }
  }

  @Test
  public void testMetaBoxParse() {
    Mp4InMemBox metaBox
        = new Mp4InMemBox.Builder(Mp4Box.FOURCC_META)
        .putBytes(sampleMetaBoxPayload)
        .build();
    Map<String, Pair<Integer, byte[]>> map = Mp4MetaUtils.parseMetaBoxWithMdtaHandler(metaBox);
    assertNotNull(map);
    assertEquals(1, map.size());
    assertTrue(map.containsKey("com.android.version"));
    Pair<Integer, byte[]> v = map.get("com.android.version");
    assertNotNull(v);
    assertEquals(1, (int) v.first);
    assertArrayEquals(new byte[]{'1', '3'}, v.second);
  }

  // called by testDepthMuxerAndExtractor
  private void subtestDepthExtractor(File mergedClip) throws IOException {
    DepthExtractor extractor = new DepthExtractor();
    extractor.setDataSource(mergedClip);
    assertEquals(5, extractor.getTrackCount());

    // tracks order depends on the order of adding tracks to muxer

    MediaFormat outerAudioFormat = extractor.getTrackFormat(0);
    assertNotNull(outerAudioFormat);
    assertFalse(outerAudioFormat.containsKey(DepthFormat.KEY_TRACK_TYPE));

    MediaFormat outerVideoFormat = extractor.getTrackFormat(1);
    assertNotNull(outerVideoFormat);
    assertFalse(outerVideoFormat.containsKey(DepthFormat.KEY_TRACK_TYPE));

    MediaFormat innerVideoFormat = extractor.getTrackFormat(2);
    assertNotNull(innerVideoFormat);
    assertTrue(innerVideoFormat.containsKey(DepthFormat.KEY_TRACK_TYPE));
    assertEquals(DepthFormat.TRACK_TYPE_SHARP_VIDEO,
        innerVideoFormat.getInteger(DepthFormat.KEY_TRACK_TYPE));

    MediaFormat innerDepthFormat = extractor.getTrackFormat(3);
    assertNotNull(innerDepthFormat);
    assertTrue(innerDepthFormat.containsKey(DepthFormat.KEY_TRACK_TYPE));
    assertEquals(DepthFormat.TRACK_TYPE_DEPTH_LINEAR,
        innerDepthFormat.getInteger(DepthFormat.KEY_TRACK_TYPE));

    MediaFormat innerMetadataFormat = extractor.getTrackFormat(4);
    assertNotNull(innerMetadataFormat);
    assertTrue(innerMetadataFormat.containsKey(DepthFormat.KEY_TRACK_TYPE));
    assertEquals(DepthFormat.TRACK_TYPE_METADATA,
        innerMetadataFormat.getInteger(DepthFormat.KEY_TRACK_TYPE));

    extractor.selectTrack(0);
    extractor.selectTrack(1);
    extractor.selectTrack(2);
    extractor.selectTrack(3);
    extractor.selectTrack(4);
    int[] frameCounts = new int[5];

    ByteBuffer buf = ByteBuffer.allocate(8 * 1024 * 1024);

    while (extractor.getSampleTrackIndex() != -1) {
      int track = extractor.getSampleTrackIndex();
      frameCounts[track]++;

      buf.clear();
      buf.limit(0);
      extractor.readSampleData(buf, 0);

      /*
      int flags = extractor.getSampleFlags();
      int offset = 0;
      long pts = extractor.getSampleTime();
      int size = (int)extractor.getSampleSize();
      int inTrackId = extractor.getSampleTrackIndex();
       */

      extractor.advance();
    }

    assertArrayEquals(new int[] {551, 382, 382, 382, 382}, frameCounts);
  }
}
