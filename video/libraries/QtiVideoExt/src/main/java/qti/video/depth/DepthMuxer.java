/*
 **************************************************************************************************
 * Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 **************************************************************************************************
 */

package qti.video.depth;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Muxer for video with depth tracks. It is similar to {@link MediaMuxer}, but also supports depth
 *     container.
 * Refer to google's "Media container file format for depth" for the container specifications.
 * To muxer tracks in inner clip, the track format must contain key
 * {@link DepthFormat#KEY_TRACK_TYPE}.
 */
public class DepthMuxer {
  private static final String TAG = "DepthMuxer";

  private static final int MAX_TRACK_COUNT_IN_A_CLIP = 8;
  private final MediaMuxer outerMuxer;
  private final MediaMuxer innerMuxer;
  private final String outerClipPath;
  private final String tmpInnerClipPath;
  private final ArrayList<TrackInfo> publicTracks = new ArrayList<>(MAX_TRACK_COUNT_IN_A_CLIP);
  private boolean clipsMerged = false;

  // non-private only for unit test
  static class TrackInfo {
    final MediaMuxer muxer;
    final int internalTrackId;
    final MediaFormat format;

    TrackInfo(MediaMuxer muxer, int trackId, MediaFormat format) {
      this.muxer = muxer;
      internalTrackId = trackId;
      this.format = format;
    }
  }

  public DepthMuxer(String path) throws IOException {
    outerClipPath = path;
    outerMuxer = new MediaMuxer(outerClipPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    tmpInnerClipPath = Files.createTempFile("depth-tmp-", ".mp4").toString();
    innerMuxer = new MediaMuxer(tmpInnerClipPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
  }

  // for testing only
  DepthMuxer(String outerClipPath, String depthClipPath) throws IOException {
    this.outerClipPath = outerClipPath;
    outerMuxer = new MediaMuxer(this.outerClipPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    tmpInnerClipPath = depthClipPath;
    innerMuxer = new MediaMuxer(tmpInnerClipPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    clipsMerged = true; // do not merge clips
  }

  /**
   * Add a track with the specified format.
   * A track whose format has {@link DepthFormat#KEY_TRACK_TYPE} will be added to inner clip.
   * A track whose format doesn't have {@link DepthFormat#KEY_TRACK_TYPE} will be added to outer
   * clip.
   *
   * @param format MediaFormat: The format for the track.
   * @return int The track index for this newly added track, and it should be used in the
   *     {@link #writeSampleData(int, ByteBuffer, MediaCodec.BufferInfo)}.
   */
  public int addTrack(MediaFormat format) {
    boolean isInnerTrack = format.containsKey(DepthFormat.KEY_TRACK_TYPE);
    MediaMuxer muxer = isInnerTrack ? innerMuxer : outerMuxer;
    int trackId = muxer.addTrack(format);
    publicTracks.add(new TrackInfo(muxer, trackId, new MediaFormat(format)));
    return publicTracks.size() - 1;
  }

  public void release() {
    outerMuxer.release();
    innerMuxer.release();
    try {
      mergeClipIfNecessary();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void setLocation(float latitude,
                          float longitude) {
    outerMuxer.setLocation(latitude, longitude);
    innerMuxer.setLocation(latitude, longitude);
  }

  public void setOrientationHint(int degrees) {
    outerMuxer.setOrientationHint(degrees);
    innerMuxer.setOrientationHint(degrees);
  }

  public void start() {
    outerMuxer.start();
    innerMuxer.start();
  }

  // Long-blocking call
  // Stops the muxer.
  // Once the muxer stops, it can not be restarted.
  public void stop() {
    outerMuxer.stop();
    innerMuxer.stop();
    try {
      mergeClipIfNecessary();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void mergeClipIfNecessary() throws IOException {
    if (!clipsMerged && hasInnerClip()) {
      editAndMerge();
      clipsMerged = true;
      boolean result = new File(tmpInnerClipPath).delete();
      assert (result);
    }
  }

  @Override
  protected void finalize() throws Throwable {
    if (!clipsMerged && hasInnerClip()) {
      Log.wtf(TAG, "Outer/Inner clips are not merged");
    }
    super.finalize();
  }

  private boolean hasInnerClip() {
    for (TrackInfo trackInfo : publicTracks) {
      if (trackInfo.muxer == innerMuxer) {
        return true;
      }
    }
    return false;
  }

  private void editAndMerge() throws IOException {
    final long startTimeMs = System.currentTimeMillis();
    try (RandomAccessFile outerF = new RandomAccessFile(outerClipPath, "rw");
         RandomAccessFile innerF = new RandomAccessFile(tmpInnerClipPath, "rw")) {
      Mp4DepthMetaEditor editor = new Mp4DepthMetaEditor(outerF, innerF);
      editor.editInnerClip(getInnerTrackTypes());
      final long timeMs1 = System.currentTimeMillis();
      Log.v(TAG, "edit inner clip latency ms: " + (timeMs1 - startTimeMs));
      editor.editOuterClip();
      final long timeMs2 = System.currentTimeMillis();
      Log.v(TAG, "edit outer clip latency ms: " + (timeMs2 - timeMs1));
      editor.mergeClip();
      final long timeMs3 = System.currentTimeMillis();
      Log.v(TAG, "merge clip latency ms: " + (timeMs3 - timeMs2));
      Log.v(TAG, "inner clip size: " + innerF.length());
      Log.v(TAG, "total edit and merge latency ms: " + (timeMs3 - startTimeMs));
    }
  }

  private byte[] getInnerTrackTypes() {
    List<Integer> types = new ArrayList<>(MAX_TRACK_COUNT_IN_A_CLIP);
    for (TrackInfo trackInfo : publicTracks) {
      if (trackInfo.format.containsKey(DepthFormat.KEY_TRACK_TYPE)) {
        assert (types.size() == trackInfo.internalTrackId);
        int trackType = trackInfo.format.getInteger(DepthFormat.KEY_TRACK_TYPE);
        types.add(trackType);
      }
    }
    // convert List<Integer> to byte[]
    int size = types.size();
    byte[] byteTypes = new byte[size];
    for (int index = 0; index < size; ++index) {
      byteTypes[index] = (byte) (int) types.get(index);
    }
    return byteTypes;
  }

  public void writeSampleData(int trackIndex,
                              ByteBuffer byteBuf,
                              MediaCodec.BufferInfo bufferInfo) {
    TrackInfo trackInfo = publicTracks.get(trackIndex);
    trackInfo.muxer.writeSampleData(trackInfo.internalTrackId, byteBuf, bufferInfo);
  }

  // non-private only for unit test
  TrackInfo getTrackInfo(int pubTrackIndex) {
    return publicTracks.get(pubTrackIndex);
  }
}
