/*
 **************************************************************************************************
 * Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 **************************************************************************************************
 */

package qti.video.depth;

import android.media.AudioPresentation;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Depth extractor. It is similar to {@link MediaExtractor}, but also supports depth container.
 * For the tracks from depth inner clip, the track format will contain key
 * {@link DepthFormat#KEY_TRACK_TYPE}.
 */
public class DepthExtractor {

  private static final int MAX_TRACK_COUNT_IN_EACH_CONTAINER = 8;
  private final ExtractorInfo outerExtractorInfo;

  // the ArrayList indices are exposed to client as track id.
  private final ArrayList<TrackInfo> pubTracks =
      new ArrayList<>(MAX_TRACK_COUNT_IN_EACH_CONTAINER);
  private ExtractorInfo innerExtractorInfo;
  private byte[] innerTrackTypes;
  private TrackInfo currentTrack;

  public DepthExtractor() {
    outerExtractorInfo = new ExtractorInfo(true);
  }

  /**
   * Set the data source as a File.
   *
   * @param file input file
   * @throws IOException if any IO error happens
   * @apiNote Only a File can be set to DepthExtractor data source.
   */
  public void setDataSource(File file) throws IOException {

    boolean hasInnerClip;
    long innerClipOffset = -1;
    long innerClipLength = -1;
    try (RandomAccessFile accessFile = new RandomAccessFile(file, "r")) {
      Mp4DepthMetaParser parser = new Mp4DepthMetaParser(accessFile);
      parser.parse();
      hasInnerClip = parser.isDepthClip();
      if (hasInnerClip) {
        innerClipOffset = parser.getInnerClipOffset();
        innerClipLength = parser.getInnerClipLength();
        innerTrackTypes = parser.getInnerClipTrackTypes();
        assert (innerTrackTypes != null);
      }
    }

    try (FileInputStream input = new FileInputStream(file)) {
      outerExtractorInfo.extractor.setDataSource(input.getFD());
    }

    if (hasInnerClip) {
      innerExtractorInfo = new ExtractorInfo(false);
      try (FileInputStream input = new FileInputStream(file)) {
        innerExtractorInfo.extractor.setDataSource(input.getFD(), innerClipOffset, innerClipLength);
      }
    }

    initTracksInfo();
  }

  // for testing only
  void setDataSource(File outerClip, File innerClip)
      throws IOException {

    try (FileInputStream input = new FileInputStream(outerClip)) {
      outerExtractorInfo.extractor.setDataSource(input.getFD());
    }

    try (RandomAccessFile accessFile = new RandomAccessFile(innerClip, "r")) {
      innerTrackTypes = Mp4DepthMetaParser.parseInnerClipMeta(accessFile);
    }

    innerExtractorInfo = new ExtractorInfo(false);
    try (FileInputStream input = new FileInputStream(innerClip)) {
      innerExtractorInfo.extractor.setDataSource(input.getFD());
    }

    initTracksInfo();
  }

  private void initTracksInfo() {
    final List<ExtractorInfo> extractorsInfo = new ArrayList<>(2);
    extractorsInfo.add(outerExtractorInfo);
    if (innerExtractorInfo != null) {
      extractorsInfo.add(innerExtractorInfo);
    }

    for (ExtractorInfo extractorInfo : extractorsInfo) {
      for (int trackId = 0; trackId < extractorInfo.extractor.getTrackCount(); ++trackId) {
        final int pubId = pubTracks.size();
        MediaFormat trackFormat = extractorInfo.extractor.getTrackFormat(trackId);
        if (!extractorInfo.isOuterExtractor) {
          int trackType = innerTrackTypes[trackId];
          trackFormat.setInteger(DepthFormat.KEY_TRACK_TYPE, trackType);
        }
        TrackInfo trackInfo = new TrackInfo(pubId, trackId, extractorInfo, trackId, trackFormat);
        extractorInfo.trackIdToPubIdMap[trackId] = pubId;
        pubTracks.add(trackInfo);
      }
    }
  }

  public void release() {
    outerExtractorInfo.extractor.release();
    if (innerExtractorInfo != null) {
      innerExtractorInfo.extractor.release();
    }
  }

  public int getTrackCount() {
    return pubTracks.size();
  }

  public List<AudioPresentation> getAudioPresentations(int trackIndex) {
    TrackInfo trackInfo = pubTracks.get(trackIndex);
    return trackInfo.extractor.getAudioPresentations(trackInfo.trackId);
  }

  /**
   * Get the track format at the specified index.
   *
   * @param trackIndex track index
   * @return the track format. If the track is contained in inner clip, the track format will
   *     contain key {@link DepthFormat#KEY_TRACK_TYPE}, the value is one of
   *     {@link DepthFormat#TRACK_TYPE_SHARP_VIDEO}, {@link DepthFormat#TRACK_TYPE_DEPTH_LINEAR},
   *     {@link DepthFormat#TRACK_TYPE_DEPTH_INVERSE}, {@link DepthFormat#TRACK_TYPE_METADATA} and
   *     {@link DepthFormat#TRACK_TYPE_TRANSLUCENT_VIDEO}
   */
  public MediaFormat getTrackFormat(int trackIndex) {
    TrackInfo trackInfo = pubTracks.get(trackIndex);
    return trackInfo.format;
  }

  public void selectTrack(int trackIndex) {
    TrackInfo trackInfo = pubTracks.get(trackIndex);
    trackInfo.extractor.selectTrack(trackInfo.trackId);
    trackInfo.selected = true;
    trackInfo.extractorInfo.updateTrackSelection();
    updateCurrentTrack();
  }

  public void unselectTrack(int trackIndex) {
    TrackInfo trackInfo = pubTracks.get(trackIndex);
    trackInfo.extractor.unselectTrack(trackInfo.trackId);
    trackInfo.selected = false;
    trackInfo.extractorInfo.updateTrackSelection();
    updateCurrentTrack();
  }

  private void updateCurrentTrack() {
    // if both outer clip and inner clip have selected available tracks,
    // the one with smaller timestamp is the current track
    final ExtractorInfo currentExtractor =
        (innerExtractorInfo == null
            || !innerExtractorInfo.anyTrackSelected
            || innerExtractorInfo.extractor.getSampleTrackIndex() == -1
            || (outerExtractorInfo.extractor.getSampleTime()
            <= innerExtractorInfo.extractor.getSampleTime()))
            ? outerExtractorInfo : innerExtractorInfo;

    int trackId = currentExtractor.extractor.getSampleTrackIndex();
    currentTrack = (trackId == -1)
        ? null : pubTracks.get(currentExtractor.trackIdToPubIdMap[trackId]);
  }

  /**
   * All selected tracks seek near the requested time according to the specified mode.
   *
   * @param timeUs time us
   * @param mode   Value is SEEK_TO_PREVIOUS_SYNC, SEEK_TO_NEXT_SYNC, or SEEK_TO_CLOSEST_SYNC
   * @apiNote If multiple video or depth tracks are selected, the video and depth tracks are
   *     usually sought to different timestamps because of different I frame timestamps. Client
   *     should drop undecoded video or depth frames until I frame is received, and drop decoded
   *     frames if the timestamp is earlier than the first I frame of other video or depth tracks.
   */
  public void seekTo(long timeUs, int mode) {
    if (outerExtractorInfo.anyTrackSelected) {
      outerExtractorInfo.extractor.seekTo(timeUs, mode);
    }
    if (innerExtractorInfo != null && innerExtractorInfo.anyTrackSelected) {
      innerExtractorInfo.extractor.seekTo(timeUs, mode);
    }
  }

  public boolean advance() {
    if (currentTrack == null) {
      return false;
    }
    final boolean hasMore = currentTrack.extractor.advance();
    updateCurrentTrack();
    return (hasMore || outerExtractorInfo.extractor.getSampleTrackIndex() != -1
        || (innerExtractorInfo != null
            && innerExtractorInfo.extractor.getSampleTrackIndex() != -1));
  }

  public int readSampleData(ByteBuffer byteBuf, int offset) {
    return (currentTrack == null)
        ? -1 : currentTrack.extractor.readSampleData(byteBuf, offset);
  }

  public int getSampleTrackIndex() {
    return (currentTrack == null) ? -1 : currentTrack.publicIndex;
  }

  public long getSampleTime() {
    return (currentTrack == null) ? -1 : currentTrack.extractor.getSampleTime();
  }

  public long getSampleSize() {
    return (currentTrack == null) ? -1 : currentTrack.extractor.getSampleSize();
  }

  public int getSampleFlags() {
    return (currentTrack == null) ? -1 : currentTrack.extractor.getSampleFlags();
  }

  private static class TrackInfo {
    final int publicIndex;
    final int internalIndex;
    final ExtractorInfo extractorInfo;
    final MediaExtractor extractor; // not really necessary, just for convenience
    final int trackId;
    final MediaFormat format;
    boolean selected;

    TrackInfo(
        int publicIndex,
        int internalIndex,
        ExtractorInfo extractorInfo,
        int trackId,
        MediaFormat format) {
      this.publicIndex = publicIndex;
      this.internalIndex = internalIndex;
      this.extractorInfo = extractorInfo;
      extractor = extractorInfo.extractor;
      this.trackId = trackId;
      this.format = format;
    }
  }

  private class ExtractorInfo {
    final MediaExtractor extractor = new MediaExtractor();
    final int[] trackIdToPubIdMap = new int[MAX_TRACK_COUNT_IN_EACH_CONTAINER];
    boolean isOuterExtractor;
    boolean anyTrackSelected = false;

    ExtractorInfo(boolean isOuterExtractor) {
      this.isOuterExtractor = isOuterExtractor;
      Arrays.fill(trackIdToPubIdMap, -1);
    }

    void updateTrackSelection() {
      for (int pubTrackId : trackIdToPubIdMap) {
        if (pubTracks.get(pubTrackId).selected) {
          anyTrackSelected = true;
          return;
        }
      }
      anyTrackSelected = false;
    }
  }
}
