package com.example.VideoSampleApp;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;


public class MainActivity extends AppCompatActivity {
  private static final int REQUEST_EXTERNAL_STORAGE = 1;
  private static final String[] PERMISSIONS_STORAGE = {
      Manifest.permission.READ_EXTERNAL_STORAGE,
      Manifest.permission.WRITE_EXTERNAL_STORAGE
  };
  public static int TIMEOUT_USEC;
  private final String TAG = "MainActivity";
  TextView display_text;
  String InputFileName, OutFileName, Feature, path_folder;
  Button StartBtn;
  Spinner spinner;
  private int mBitrate, I_FRAME_INTERVAL;

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
    verifyStoragePermissions(this);
    Setup();
    StartBtn.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        try {
          Runtime.getRuntime().exec("logcat -c");
          display_text.setText("");
          Feature = spinner.getSelectedItem().toString();
          StartBtn.setEnabled(false);
          OutFileName = path_folder + "/" + Feature.replace(" ", "_") + "_out.mp4";
          new File(OutFileName).delete();
          new Thread(new Runnable() {
            @Override
            public void run() {
              Run();
              logging();
            }
          }).start();
          updateUI("\nInput File : " + InputFileName);
          updateUI("\nOutput File : " + OutFileName);
        } catch (Exception e) {
          updateUI("\nRun Error " + e.getMessage());
        }
      }
    });
  }

  private void Setup() {
    path_folder = "/sdcard/Download";
    display_text = findViewById(R.id.display_text);
    StartBtn = findViewById(R.id.StartBtn);
    spinner = findViewById(R.id.spinner_QAIP);
    mBitrate = 10000000;
    I_FRAME_INTERVAL = 1;
    TIMEOUT_USEC = 100;
    updateUI("\nPlease check File SampleVideo_HDRplus.mp4 SampleVideo_10bit.mp4 and SampleVideo_8bit.mp4\nShould be present in Download folder");
    updateUI("\n\nPlease Run command in shell:: ");
    updateUI("  $pm grant com.example.VideoSampleApp android.permission.READ_LOGS");
    updateUI("  $setprop vendor.qc2.log.msg 0xff0000ff ");
    updateUI("  $echo 0x103F130F > /sys/module/msm_video/parameters/msm_vidc_debug");
  }

  private void Run() {
    updateUI(".....STARTED " + Feature + " ....");
    switch (Feature) {
      case "HDR10 to SDR":
        InputFileName = path_folder + "/SampleVideo_10bit.mp4";
        qcSdrDecoder();
        break;
      case "HDR10+ to HDR10":
        InputFileName = path_folder + "/SampleVideo_HDRplus.mp4";
        qcHdr10Transoder();
        break;
      case "HDR10 to HDR10+":
        InputFileName = path_folder + "/SampleVideo_10bit.mp4";
        qcHdr10PlusTransoder();
        break;
      case "ROI":
        InputFileName = path_folder + "/SampleVideo_8bit.mp4";
        qcRoiEncoder();
        break;
      case "QP Override":
        InputFileName = path_folder + "/SampleVideo_8bit.mp4";
        qcQpOverrideEncoder();
        break;
      case "QP Control":
        InputFileName = path_folder + "/SampleVideo_8bit.mp4";
        qcQPControlTransoder();
        break;
      case "Slice and Rsync":
        InputFileName = path_folder + "/SampleVideo_8bit.mp4";
        qcSliceRsyncTransoder();
        break;
      case "LTR":
        InputFileName = path_folder + "/SampleVideo_8bit.mp4";
        qcLtrTransoder();
        break;
    }
  }

  private void qcRoiEncoder() {
    RoiEncode qcROI = new RoiEncode(this);
    try {
      qcROI.runROI_Encoder(InputFileName, OutFileName, I_FRAME_INTERVAL);
      updateUI("\nROI_ENCODER Done and saved to file : " + OutFileName);
    } catch (Exception e) {
      updateUI("\nROI_ENCODER Error  : " + e.getMessage());
      e.getStackTrace();
    }
    BtnEnabled();
  }

  private void qcQpOverrideEncoder() {
    QpOverrideEncode qcQP_Override = new QpOverrideEncode(this);
    try {
      qcQP_Override.runQP_Override_Encoder(InputFileName, OutFileName, I_FRAME_INTERVAL);
      updateUI("\nQC_EIQP_ENCODER Done and saved to file : " + OutFileName);
    } catch (Exception e) {
      updateUI("\nQC_EIQP_ENCODER Error  : " + e.getMessage());
      e.getStackTrace();
    }
    BtnEnabled();
  }

  private void qcSdrDecoder() {
    SdrDecode qcSDR = new SdrDecode(this);
    try {
      qcSDR.runSDR_Decoder(InputFileName, OutFileName, mBitrate, I_FRAME_INTERVAL);
      updateUI("\nSDR_Decode Done and saved to file : " + OutFileName);
    } catch (Exception e) {
      updateUI("\nTRANSFER_SDR_DECODER Error: " + e.getMessage());
      e.printStackTrace();
    }
    BtnEnabled();
  }


  private void qcHdr10Transoder() {
    Hdr10Encode qcHDR10 = new Hdr10Encode(this);
    try {
      qcHDR10.runHDR10_Encode(InputFileName, OutFileName, mBitrate, I_FRAME_INTERVAL);
      updateUI("\nHDR10_Encode Done and saved to file : " + OutFileName);
    } catch (Exception e) {
      updateUI("\nQcHdr10Transoder Error: " + e.getMessage());
      e.printStackTrace();
    }
    BtnEnabled();
  }

  private void qcHdr10PlusTransoder() {
    Hdr10PlusEncode qcHDR10_Plus = new Hdr10PlusEncode(this);
    try {
      qcHDR10_Plus.runHDR10_Plus_Encode(InputFileName, OutFileName, mBitrate, I_FRAME_INTERVAL);
      updateUI("\nHDR10_Plus_Encode Done and saved to file : " + OutFileName);
    } catch (Exception e) {
      updateUI("\nQcHdr10PlusTransoder Error: " + e.getMessage());
      e.printStackTrace();
    }
    BtnEnabled();
  }

  private void qcQPControlTransoder() {
    QpControlEncode qcQP_Control = new QpControlEncode(this);
    try {
      qcQP_Control.runQP_Control(InputFileName, OutFileName, I_FRAME_INTERVAL);
      updateUI("\nQP_Control_Encode Done and saved to file : " + OutFileName);
    } catch (Exception e) {
      updateUI("\nQcQPControlTransoder Error: " + e.getMessage());
      e.printStackTrace();
    }
    BtnEnabled();
  }

  private void qcSliceRsyncTransoder() {
    SliceRsyncEncode qcSlice_Rsync = new SliceRsyncEncode(this);
    try {
      qcSlice_Rsync.runSlice_Rsync(InputFileName, OutFileName, I_FRAME_INTERVAL);
      updateUI("\nSlice_Rsync_Encode Done and saved to file : " + OutFileName);
    } catch (Exception e) {
      updateUI("\nQcSliceRsyncTransoder Error: " + e.getMessage());
      e.printStackTrace();
    }
    BtnEnabled();
  }

  private void qcLtrTransoder() {
    LtrEncode qcLTR = new LtrEncode(this);
    try {
      qcLTR.runLTR(InputFileName, OutFileName, I_FRAME_INTERVAL);
      updateUI("\nLTR_Encode Done and saved to file : " + OutFileName);
    } catch (Exception e) {
      updateUI("\nQcLtrTransoder Error: " + e.getMessage());
      e.printStackTrace();
    }
    BtnEnabled();
  }

  public void BtnEnabled() {
    MainActivity.this.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        updateUI("\n.....STOPPED " + Feature + " ....");
        StartBtn.setEnabled(true);
      }
    });
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