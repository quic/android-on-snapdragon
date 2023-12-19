
/*==============================================================================
 *  Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 *   SPDX-License-Identifier: Apache-2.0
 *===============================================================================
 */

package com.example.VideoSampleApp;

import static com.example.VideoSampleApp.MainActivity.TIMEOUT_USEC;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import android.view.Surface;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import qti.video.QMediaExtensions;

public class QpOverrideEncode {
  private final static String TAG = "QP_Override_Encoder";
  private final MainActivity mainActivity;
  private final MediaCodec.BufferInfo Decodecinfo = new MediaCodec.BufferInfo();
  private final MediaCodec.BufferInfo Encodecinfo = new MediaCodec.BufferInfo();
  List<String> supportedExtensions;
  boolean CodecError = false, mDecodeoutputDone = false, mDecodeinputDone = false;
  private final int flag = 0;
  private int D_InputFrame = 0;
  private int D_OutputFrame = 0;
  private int E_OutputFrame = 0;
  private int mBitrate;
  private int I_FRAME_INTERVAL;
  private int mTrackID = -1;
  private String mime = null, mInput;
  private Surface D_Surface = null;
  private MediaCodec mEncodec = null, mDecodec = null;
  private MediaExtractor extractor;
  private MediaFormat format = null, EncoderFormat, DecoderFormat;
  private MediaMuxer mMuxer;
  private GFXSurface D_GFXSource;

  QpOverrideEncode(MainActivity activity) {
    mainActivity = activity;
  }

  void runQP_Override_Encoder(String fileInput, String fileOut,int Bitrate, int FRAME_INTERVAL)
      throws Exception {
    this.mInput = fileInput;
    mBitrate = Bitrate;
    I_FRAME_INTERVAL = FRAME_INTERVAL;
    int muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
    mMuxer = new MediaMuxer(fileOut, muxerFormat);
    createFormat();
    createCodec();
    start();
    startTrascode();
    release();
  }

  void setQP_Override() {
    // Enabling initial Encoder QP value override feature
    EncoderFormat.setInteger(QMediaExtensions.KEY_INIT_QP_I_FRAME_ENABLE, 1);
    EncoderFormat.setInteger(QMediaExtensions.KEY_INIT_QP_P_FRAME_ENABLE, 1);
    EncoderFormat.setInteger(QMediaExtensions.KEY_INIT_QP_B_FRAME_ENABLE, 1);

    // Overriding the QP value for intial encoder for i, p and b frames
    EncoderFormat.setInteger(QMediaExtensions.KEY_INIT_QP_I_FRAME, 40);
    EncoderFormat.setInteger(QMediaExtensions.KEY_INIT_QP_P_FRAME, 40);
    EncoderFormat.setInteger(QMediaExtensions.KEY_INIT_QP_B_FRAME, 40);
  }

  private void createFormat() throws IOException {
    extractor = new MediaExtractor();
    Log.i(TAG, "Input File Path : " + mInput);
    extractor.setDataSource(mInput);
    Log.i(TAG, "getTrackCount: " + extractor.getTrackCount());
    for (int i = 0; i < extractor.getTrackCount(); i++) {
      format = extractor.getTrackFormat(i);
      mime = format.getString(MediaFormat.KEY_MIME);
      if (mime.startsWith("video/")) {
        extractor.selectTrack(i);
        break;
      }
    }
    Log.i(TAG, "hasCacheReachedEndOfStream: " + extractor.hasCacheReachedEndOfStream());
    EncoderFormat = DecoderFormat = format;
    EncoderFormat.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
    EncoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitrate);
    EncoderFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
    EncoderFormat.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain);
    EncoderFormat.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel4);
    setQP_Override();
  }

  private void createCodec() throws IOException {
    Log.i(TAG, "mime: " + mime);
    mDecodec = MediaCodec.createDecoderByType(mime);
    mEncodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC);
    supportedExtensions = mEncodec.getSupportedVendorParameters();
  }

  private void startTrascode() throws Exception {
    while (!CodecError) {
      Log.i(TAG, D_InputFrame + " D_InputFrame :: D_OutputFrame " + D_OutputFrame + " :: E_OutputFrame :: " + E_OutputFrame);
      if (!mDecodeinputDone) Decodeinput();
      if (!mDecodeoutputDone) Decodeoutput();
      if ((Encodecinfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
        Log.w(TAG, "Encodecinfo OutputBuffer BUFFER_FLAG_END_OF_STREAM");
        break;
      } else
        Encodeoutput();
    }
    mainActivity.updateUI("\n" + D_InputFrame + " : D_InputFrame\n" + D_OutputFrame + " : D_OutputFrame\n" + E_OutputFrame + " : E_OutputFrame");
  }

  private void start() throws Exception {
    mEncodec.configure(EncoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    Surface e_Surface = mEncodec.createInputSurface();
    if (!e_Surface.isValid()) throw new Exception("Encodec Surface is not valid");
    GFXSurface e_GFXSource = new GFXSurface(e_Surface);
    e_GFXSource.makeCurrent();

    D_GFXSource = new GFXSurface();
    D_Surface = D_GFXSource.createInputSurface();
    mDecodec.configure(DecoderFormat, D_Surface, null, flag);
    mDecodec.start();
    mEncodec.start();

    mainActivity.updateUI("\n Encoder Config Format : " + EncoderFormat);
    mainActivity.updateUI("\n Decoder InputFormat : " + mDecodec.getInputFormat());
    mainActivity.updateUI("\n Encoder InputFormat : " + mEncodec.getInputFormat());
  }

  private void release() {
    mainActivity.updateUI("\n Decoder Output Format : " + mDecodec.getOutputFormat());
    mainActivity.updateUI("\n Encoder output Format : " + mEncodec.getOutputFormat());

    mDecodec.stop();
    mEncodec.stop();
    mMuxer.stop();
    mDecodec.release();
    mEncodec.release();
    mMuxer.release();
    extractor.release();
  }

  private void Encodeoutput() {
    int mEncodecoutIndex = mEncodec.dequeueOutputBuffer(Encodecinfo, TIMEOUT_USEC);
    if (mEncodecoutIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
      Log.d(TAG, "no output from encoder available");
    } else if (mEncodecoutIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
      Log.d(TAG, "encoder output buffers changed");
    } else if (mEncodecoutIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
      EncoderFormat = mEncodec.getOutputFormat();
    } else if (mEncodecoutIndex < 0) {
      Log.e(TAG, "unexpected result from encoder.dequeueOutputBuffer: " + mEncodecoutIndex);
    } else {
      ByteBuffer encodedData = mEncodec.getOutputBuffer(mEncodecoutIndex);
      if (encodedData == null) {
        Log.e(TAG, "encoderOutputBuffer " + mEncodecoutIndex + " was null");
      } else {
        if (mTrackID == -1) {
          mTrackID = mMuxer.addTrack(EncoderFormat);
          mMuxer.start();
        }
        mMuxer.writeSampleData(mTrackID, encodedData, Encodecinfo);
        E_OutputFrame++;
      }
      mEncodec.releaseOutputBuffer(mEncodecoutIndex, false);
    }
  }

  private void Decodeoutput() throws Exception {
    int mDecodecoutIndex = mDecodec.dequeueOutputBuffer(Decodecinfo, TIMEOUT_USEC);
    switch (mDecodecoutIndex) {
      case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
        Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
        break;
      case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
        DecoderFormat = mDecodec.getOutputFormat();
        break;
      case MediaCodec.INFO_TRY_AGAIN_LATER:
        Log.d(TAG, "dequeueOutputBuffer timed out!");
        break;
      default:
        mDecodec.getOutputBuffer(mDecodecoutIndex);
        D_OutputFrame++;
        boolean doRender = (Decodecinfo.size != 0);
        mDecodec.releaseOutputBuffer(mDecodecoutIndex, D_Surface != null);
        if (doRender) {
          SurfaceTexture texture = D_GFXSource.awaitNewImage();
          D_GFXSource.drawImage();
          GFXSurface.setPresentationTime(Decodecinfo.presentationTimeUs * 1000);
          GFXSurface.swapBuffers();
        }
        break;
    }
    if ((Decodecinfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
      Log.w(TAG, " Decodecinfo OutputBuffer BUFFER_FLAG_END_OF_STREAM");
      mEncodec.signalEndOfInputStream();
      mDecodeoutputDone = true;
    }
  }

  private void Decodeinput() {
    int flag = 0;
    int inputBufIndex = mDecodec.dequeueInputBuffer(TIMEOUT_USEC);
    if (inputBufIndex < 0) {
      Log.d(TAG, "input buffer not available");
      return;
    }
    ByteBuffer dstBuf = mDecodec.getInputBuffer(inputBufIndex);
    int sampleSize = extractor.readSampleData(dstBuf, 0);
    D_InputFrame++;
    if (sampleSize < 0) {
      Log.w(TAG, "Sending EOS on input last frame");
      flag = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
      mDecodeinputDone = true;
      sampleSize = 0;
    }
    mDecodec.queueInputBuffer(inputBufIndex, 0, sampleSize, extractor.getSampleTime(), flag);
    if (!mDecodeinputDone) {
      extractor.advance();
    }
  }
}
