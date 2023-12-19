
/*==============================================================================
 *  Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 *   SPDX-License-Identifier: Apache-2.0
 *===============================================================================
 */

package com.example.VideoSampleApp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity {
  private static final int REQUEST_EXTERNAL_STORAGE = 1;
  private static final String[] PERMISSIONS_STORAGE = {
          android.Manifest.permission.READ_EXTERNAL_STORAGE,
          Manifest.permission.WRITE_EXTERNAL_STORAGE
  };
  TextView display_text;
  public static int TIMEOUT_USEC;
  String TAG = "MainActivity", InputFileName, OutFileName, Feature, path_folder;
  Button StartBtn;
  private int mBitrate, I_FRAME_INTERVAL;
  final List<Button> myButtonlist = new ArrayList<Button>();

  public static void verifyStoragePermissions(Activity activity) {
    int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

    if (permission != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(
              activity,
              PERMISSIONS_STORAGE,
              REQUEST_EXTERNAL_STORAGE
      );
    }
  }
  private void logging() {
    try {
      new File(OutFileName + "_log.txt").delete();
      BufferedWriter bw = new BufferedWriter(new FileWriter(OutFileName + "_log.txt"));
      Process process = Runtime.getRuntime().exec("logcat -d");
      BufferedReader bufferedReader = new BufferedReader(
          new InputStreamReader(process.getInputStream()));
      String line;
      while ((line = bufferedReader.readLine()) != null) {
        bw.write(line + "\n");
      }
      updateUI("\nLogging Completed \nSaved to file::  " + OutFileName + "_log.txt");
      bw.close();
    } catch (Exception e) {
      updateUI("\nLogging Error " + e.getMessage());
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    Setup();
    StartBtn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        try {
            Runtime.getRuntime().exec("logcat -c");
            view2();
            Resources res = getResources();
            buildInboxItems(res.getStringArray(R.array.QAIP_extensions), (GridLayout) findViewById(R.id.extensionsLayout));
            buildInboxItems(res.getStringArray(R.array.QAIP_feature), (GridLayout) findViewById(R.id.featureLayout));
        } catch(Exception e) {
          updateUI("\nRun Error " + e.getMessage());
        }
      }
    });
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    view4();
    display_text.setText(data.getData().getPath());
    InputFileName = "/storage/emulated/0/" + data.getData().getPath().split(":")[1];
    Log.i(TAG, "Input File Path : " + InputFileName);
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          logging();
          Run();
        } catch (Exception e) {
          updateUI("\nError while Run  : " + e.getMessage());
        }
      }
    }).start();
    StartBtn.setVisibility(View.VISIBLE);
    super.onActivityResult(requestCode, resultCode, data);
  }

  private void buildInboxItems(String[] buttonsInfoList, GridLayout scrViewButLay) {
    scrViewButLay.removeAllViews();
    scrViewButLay.setColumnCount(2);
    scrViewButLay.setRowCount(buttonsInfoList.length);

    for (int index = 0; index < buttonsInfoList.length ; index++) {
        final Button myButton = new Button(this); //initialize the button here
        myButton.setText(buttonsInfoList[index]);
        myButton.setId(index);
        scrViewButLay.addView(myButton);
        myButton.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            display_text.setText("");
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            startActivityForResult(intent, 0);
            try {
              Button b = (Button) v;
              Feature = b.getText().toString();
              Runtime.getRuntime().exec("logcat -c");
              display_text.setText("  Logs : \n\n");
              OutFileName = path_folder + "/" + Feature.replace(" ", "_") + "_out.mp4";
              new File(OutFileName).delete();
            } catch (Exception e) {
              updateUI("\nRun Error " + e.getMessage());
            }
          }
        });
        myButtonlist.add(myButton);
      }

  }

  private void view1(){
    display_text = findViewById(R.id.display_text);
    StartBtn = findViewById(R.id.StartBtn);
    findViewById(R.id.feature_text).setVisibility(View.GONE);
    findViewById(R.id.featureLayout).setVisibility(View.GONE);
    findViewById(R.id.extensions_text).setVisibility(View.GONE);
    findViewById(R.id.extensionsLayout).setVisibility(View.GONE);
    findViewById(R.id.videoView1).setVisibility(View.GONE);
    findViewById(R.id.videoView2).setVisibility(View.GONE);
    findViewById(R.id.secound_display).setVisibility(View.VISIBLE);
  }
  private void view2(){
    findViewById(R.id.videoView1).setVisibility(View.GONE);
    findViewById(R.id.videoView2).setVisibility(View.GONE);
    findViewById(R.id.secound_display).setVisibility(View.GONE);
    StartBtn.setVisibility(View.GONE);

    findViewById(R.id.feature_text).setVisibility(View.VISIBLE);
    findViewById(R.id.featureLayout).setVisibility(View.VISIBLE);
    findViewById(R.id.extensions_text).setVisibility(View.VISIBLE);
    findViewById(R.id.extensionsLayout).setVisibility(View.VISIBLE);

    TextView myTextView = findViewById(R.id.feature_text);
    myTextView.setText("Feature!");
    myTextView.setTextSize(20);
    myTextView = findViewById(R.id.extensions_text);
    myTextView.setText("Extensions!");
    myTextView.setTextSize(20);
  }
  private void view3(){
    findViewById(R.id.videoView1).setVisibility(View.VISIBLE);
    findViewById(R.id.videoView2).setVisibility(View.VISIBLE);
    findViewById(R.id.feature_text).setVisibility(View.VISIBLE);
    findViewById(R.id.extensions_text).setVisibility(View.VISIBLE);
    StartBtn.setVisibility(View.VISIBLE);

    TextView myTextView = findViewById(R.id.feature_text);
    myTextView.setText("Input Video!");
    myTextView.setTextSize(15);
    myTextView = findViewById(R.id.extensions_text);
    myTextView.setText("Output Video!");
    myTextView.setTextSize(15);
  }
  private void view4() {
    findViewById(R.id.feature_text).setVisibility(View.GONE);
    findViewById(R.id.featureLayout).setVisibility(View.GONE);
    findViewById(R.id.extensions_text).setVisibility(View.GONE);
    findViewById(R.id.extensionsLayout).setVisibility(View.GONE);
    findViewById(R.id.secound_display).setVisibility(View.VISIBLE);
  }

  private void view5() {
    MainActivity.this.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Display();
      }
    });
  }
  private void Setup() {
    path_folder = "/sdcard/Download";
    I_FRAME_INTERVAL = 1;
    TIMEOUT_USEC = 100;
    view1();
    updateUI("\n\nPlease Run command in shell:: ");
    updateUI("  $pm grant com.example.VideoSampleApp android.permission.READ_LOGS");
    updateUI("  $setprop vendor.qc2.log.msg 0xff0000ff ");
    updateUI("  $echo 0x103F130F > /sys/module/msm_video/parameters/msm_vidc_debug");
    if(!Environment.isExternalStorageManager())
    {
      Intent permissionIntent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
      startActivity(permissionIntent);
    }
  }

  private void Display() {
    try {
      view3();
      VideoView videoView1 = (VideoView) findViewById(R.id.videoView1);
      MediaController mediaController1 = new MediaController(this);
      mediaController1.setAnchorView(videoView1);
      Uri uri = Uri.parse(InputFileName);
      videoView1.setMediaController(mediaController1);
      videoView1.setVideoURI(uri);
      videoView1.requestFocus();

      VideoView videoView2 = (VideoView) findViewById(R.id.videoView2);
      MediaController mediaController2 = new MediaController(this);
      mediaController2.setAnchorView(videoView2);
      uri = Uri.parse(OutFileName);
      videoView2.setMediaController(mediaController2);
      videoView2.setVideoURI(uri);
      videoView2.requestFocus();
      videoView1.start();
      videoView2.start();
    }
    catch(Exception e) {
      e.getStackTrace();
    }
  }

  private boolean video(int CodecProfileLevel) throws IOException {
    MediaExtractor extractor = new MediaExtractor();
    MediaFormat format = null;
    extractor.setDataSource(InputFileName);
    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
    retriever.setDataSource(InputFileName);
    mBitrate = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));
    Log.i(TAG, "MediaExtractor KEY_BIT_RATE : " + mBitrate);
    retriever.release();
    for (int i = 0; i < extractor.getTrackCount(); i++) {
      format = extractor.getTrackFormat(i);
      String mime = format.getString(MediaFormat.KEY_MIME);
      if (mime.startsWith("video/")) {
        Log.i(TAG, "MediaExtractor KEY_PROFILE : " + format.getInteger(MediaFormat.KEY_PROFILE));
        if (CodecProfileLevel == format.getInteger(MediaFormat.KEY_PROFILE))
          return true;
      }
    }
    return false;
  }

  private void Run() throws Exception {
    try {
      Boolean issucessfull = false;
      MainActivity.this.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          StartBtn.setVisibility(View.INVISIBLE);
        }
      });
      if (video(MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10)) {
        updateUI(".....STARTED " + Feature + " ....");
        switch (Feature) {
          case "HDR10 to SDR":
            issucessfull = qcSdrDecoder();
            break;
          case "HDR10 to HDR10+":
            issucessfull = qcHdr10PlusTransoder();
            break;
          case "Pro Sight":
            issucessfull = ProSightEncode();
            break;
        }
      } else if (video(MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus)) {
        updateUI(".....STARTED " + Feature + " ....");
        switch (Feature) {
          case "HDR10+ to HDR10":
            issucessfull = qcHdr10Transoder();
            break;
        }
      } else if (video(MediaCodecInfo.CodecProfileLevel.HEVCProfileMain) ||
              video(MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10)) {
        updateUI(".....STARTED " + Feature + " ....");
        switch (Feature) {
          case "Pro Sight":
            issucessfull = ProSightEncode();
            break;
          case "ROI":
            issucessfull = qcRoiEncoder();
            break;
          case "QP Override":
            issucessfull = qcQpOverrideEncoder();
            break;
          case "QP Control":
            issucessfull = qcQPControlTransoder();
            break;
          case "Slice and Rsync":
            issucessfull = qcSliceRsyncTransoder();
            break;
          case "LTR":
            issucessfull = qcLtrTransoder();
            break;
          case "MBROI":
            issucessfull = qcMBRoiEncoder();
            break;
        }
      }
      if (!issucessfull) {
        throw new Exception("MediaExtractor KEY_PROFILE not supported");
      }
      updateUI("\nInput File : " + InputFileName);
      updateUI("\n.....End " + Feature + " ....");
      view5();
    }
    catch (Exception e){
      MainActivity.this.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          StartBtn.setVisibility(View.VISIBLE);
          StartBtn.setText("Start Again");
        }
      });
      throw new Exception(e.getMessage());
    }
  }

  private boolean qcRoiEncoder() throws Exception {
    RoiEncode qcROI = new RoiEncode(this);
      qcROI.runROI_Encoder(InputFileName, OutFileName, mBitrate, I_FRAME_INTERVAL);
      updateUI("\nROI_ENCODER Done and saved to file : " + OutFileName);
      return true;
  }

  private boolean qcMBRoiEncoder() throws Exception {
    MbRoiEncode qcROI = new MbRoiEncode(this);
      qcROI.runMbROIEncoder(InputFileName, OutFileName, mBitrate, I_FRAME_INTERVAL);
      updateUI("\nMBROI_ENCODER Done and saved to file : " + OutFileName);
      return true;
  }

  private boolean qcQpOverrideEncoder() throws Exception {
    QpOverrideEncode qcQP_Override = new QpOverrideEncode(this);
      qcQP_Override.runQP_Override_Encoder(InputFileName, OutFileName, mBitrate, I_FRAME_INTERVAL);
      updateUI("\nQC_EIQP_ENCODER Done and saved to file : " + OutFileName);
      return true;
  }

  private boolean qcSdrDecoder() throws Exception {
    SdrDecode qcSDR = new SdrDecode(this);
      qcSDR.runSDR_Decoder(InputFileName, OutFileName, mBitrate, I_FRAME_INTERVAL);
      updateUI("\nSDR_Decode Done and saved to file : " + OutFileName);
      return true;
  }


  private boolean qcHdr10Transoder() throws Exception {
    Hdr10Encode qcHDR10 = new Hdr10Encode(this);
      qcHDR10.runHDR10_Encode(InputFileName, OutFileName, mBitrate, I_FRAME_INTERVAL);
      updateUI("\nHDR10_Encode Done and saved to file : " + OutFileName);
      return true;
  }

  private boolean qcHdr10PlusTransoder() throws Exception {
    Hdr10PlusEncode qcHDR10_Plus = new Hdr10PlusEncode(this);
      qcHDR10_Plus.runHDR10_Plus_Encode(InputFileName, OutFileName, mBitrate, I_FRAME_INTERVAL);
      updateUI("\nHDR10_Plus_Encode Done and saved to file : " + OutFileName);
      return true;
  }

  private boolean qcQPControlTransoder() throws Exception {
    QpControlEncode qcQP_Control = new QpControlEncode(this);
      qcQP_Control.runQP_Control(InputFileName, OutFileName, mBitrate, I_FRAME_INTERVAL);
      updateUI("\nQP_Control_Encode Done and saved to file : " + OutFileName);
    return true;
  }

  private boolean qcSliceRsyncTransoder() throws Exception {
    SliceRsyncEncode qcSlice_Rsync = new SliceRsyncEncode(this);
      qcSlice_Rsync.runSlice_Rsync(InputFileName, OutFileName, mBitrate, I_FRAME_INTERVAL);
      updateUI("\nSlice_Rsync_Encode Done and saved to file : " + OutFileName);
    return true;
  }

  private boolean qcLtrTransoder() throws Exception {
    LtrEncode qcLTR = new LtrEncode(this);
      qcLTR.runLTR(InputFileName, OutFileName, mBitrate, I_FRAME_INTERVAL);
      updateUI("\nLTR_Encode Done and saved to file : " + OutFileName);
      return true;
  }

  private boolean ProSightEncode() throws Exception {
    ProSightEncode ProSight = new ProSightEncode(this);
      ProSight.runProSightEncode(InputFileName, OutFileName, mBitrate, I_FRAME_INTERVAL);
      updateUI("\nPro-Sight Done and saved to file : " + OutFileName);
      return true;
  }

  public void updateUI(String Text) {
    MainActivity.this.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        display_text.append(Text + "\n ");
      }
    });
  }
}