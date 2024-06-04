/*
 * Changes from Qualcomm Innovation Center are provided under the following license:
 * Copyright (c) 2023-2024 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

#include <iostream>
#include <algorithm>
#include <string>
#include <stc_demo_service.h>

using android::IServiceManager;
using android::Parcel;
using android::sp;
using snapdragoncolor::IOEMService;
using namespace std;

#define __CLASS__ "stc_demo_test"

#define LOGI(format, ...) (printf("%s: %s: " format "\n", __CLASS__, __FUNCTION__, ##__VA_ARGS__))
#define LOGE(format, ...) (printf("%s: %s: " format "\n", __CLASS__, __FUNCTION__, ##__VA_ARGS__))

const char *get_igc_hw_cap_ = "GET_IGC_CAP";
const char *get_gc_hw_cap_ = "GET_GC_CAP";
const char *set_gc_config_ = "SET_GC_CONFIG";
const char *set_igc_config_ = "SET_IGC_CONFIG";
const char *set_pcc_config_ = "SET_PCC_CONFIG";
const char *set_gamut_config_ = "SET_GAMUT_CONFIG";

void SetPccConfig(sp<IOEMService> binder, bool enable, vector<double> config) {
  int ret = 0;
  double r, g, b;
  android::Parcel in_parcel, out_parcel;

  in_parcel.writeBool(enable);
  if (!enable) {
    LOGI("Disable PCC config");
  } else {
    LOGI("Set up PCC config");
    in_parcel.writeUint32(config.size());
    for (int i = 0; i < config.size(); i++) {
      in_parcel.writeDouble(config[i]);
    }
  }

  ret = binder->dispatch(IOEMService::SET_PCC_CONFIG, &in_parcel, &out_parcel);
  if (ret) {
    LOGE("Failed to dispatch PCC");
  }
}

void SetIgcConfig(sp<IOEMService> binder, bool enable, const char *file_name) {
  int ret = 0;
  android::Parcel in_parcel, out_parcel;

  in_parcel.writeBool(enable);
  if (!enable) {
    LOGI("Disable IGC config");
  } else {
    LOGI("Set up IGC config from file %s", file_name);
    in_parcel.writeCString(file_name);
  }

  ret = binder->dispatch(IOEMService::SET_IGC_CONFIG, &in_parcel, &out_parcel);
  if (ret) {
    LOGE("Failed to set IGC config");
  }
}

void SetGcConfig(sp<IOEMService> binder, bool enable, const char *file_name) {
  int ret = 0;
  android::Parcel in_parcel, out_parcel;

  in_parcel.writeBool(enable);
  if (!enable) {
    LOGI("Disable GC config");
  } else {
    LOGI("Set up GC config from file %s", file_name);
    in_parcel.writeCString(file_name);
  }

  ret = binder->dispatch(IOEMService::SET_GC_CONFIG, &in_parcel, &out_parcel);
  if (ret) {
    LOGE("Failed to set GC config");
  }
}

void GetIgcHwCap(sp<IOEMService> binder) {
  int ret = 0;
  android::Parcel in_parcel, out_parcel;

  ret = binder->dispatch(IOEMService::GET_IGC_HW_CAP, &in_parcel, &out_parcel);
  if (ret) {
    LOGE("Failed to get IGC capability");
  }

  int num_configs = out_parcel.readInt32();
  printf("There are %d configs supported:\n", num_configs);
  for (int i = 0; i < num_configs; i++) {
    const char *mode_name = out_parcel.readCString();
    uint32_t num_entries = out_parcel.readUint32();
    uint32_t entry_width = out_parcel.readUint32();
    printf("config name %s: num_entries %u, entry_width %u\n", mode_name, num_entries, entry_width);
  }
}

void GetGcHwCap(sp<IOEMService> binder) {
  int ret = 0;
  android::Parcel in_parcel, out_parcel;

  ret = binder->dispatch(IOEMService::GET_GC_HW_CAP, &in_parcel, &out_parcel);
  if (ret) {
    LOGE("Failed to get GC capability");
  }

  int num_configs = out_parcel.readInt32();
  printf("There are %d configs supported:\n", num_configs);
  for (int i = 0; i < num_configs; i++) {
    const char *mode_name = out_parcel.readCString();
    uint32_t num_entries = out_parcel.readUint32();
    uint32_t entry_width = out_parcel.readUint32();
    printf("config name %s: num_entries %u, entry_width %u\n", mode_name, num_entries, entry_width);
  }
}

void SetGamutConfig(sp<IOEMService> binder, bool enable, const char *file_name) {
  int ret = 0;
  android::Parcel in_parcel, out_parcel;

  in_parcel.writeBool(enable);
  if (enable) {
    in_parcel.writeCString(file_name);
    LOGI("Set up GAMUT config from file %s", file_name);
  } else {
    LOGI("Disable GAMUT config");
  }

  ret = binder->dispatch(IOEMService::SET_GAMUT_CONFIG, &in_parcel, &out_parcel);
  if (ret) {
    LOGE("Failed to set GC config");
  }
}

/*
 * adb shell /vendor/bin/stc_demo_test "GET_IGC_CAP"
 * adb shell /vendor/bin/stc_demo_test "GET_GC_CAP"
 * adb shell /vendor/bin/stc_demo_test "SET_IGC_CONFIG" "enable" "/data/display-tests/IGC_config_sRGB.txt"
 * adb shell /vendor/bin/stc_demo_test "SET_IGC_CONFIG" "disable"
 * adb shell /vendor/bin/stc_demo_test "SET_GC_CONFIG" "enable" "/data/display-tests/GC_config_test.txt"
 * adb shell /vendor/bin/stc_demo_test "SET_GC_CONFIG" "disable"
 * adb shell /vendor/bin/stc_demo_test "SET_PCC_CONFIG" "enable" "0" "0.5" "0" "0" "0" "0" "0.9" "0" "0" "0" "0" "0.1"
 * adb shell /vendor/bin/stc_demo_test "SET_PCC_CONFIG" "disable"
 * adb shell /vendor/bin/stc_demo_test "SET_GAMUT_CONFIG" "enable" "/data/display-tests/GAMUT_config_test.txt"
 * adb shell /vendor/bin/stc_demo_test "SET_GAMUT_CONFIG" "disable"
*/
int main(int32_t argc, char *argv[]) {
  // get binder
  sp<IServiceManager> sm = android::defaultServiceManager();
  sp<IOEMService> binder =
      android::interface_cast<IOEMService>(sm->getService(android::String16("display.oemservice")));
  if (binder == NULL) {
    LOGE("%s: invalid binder object", __FUNCTION__);
    return 0;
  }

  const char *feature_type = argv[1];
  LOGI("---- Begin the test for %s----", feature_type);
  if (!strcmp(feature_type, get_igc_hw_cap_)) {
    GetIgcHwCap(binder);
  } else if (!strcmp(feature_type, get_gc_hw_cap_)) {
    GetGcHwCap(binder);
  } else if (!strcmp(feature_type, set_pcc_config_)) {
    const char *enable = argv[2];
    vector<double> pcc_config;
    if (!strcmp(enable, "disable")) {
      SetPccConfig(binder, false, pcc_config);
    } else if (!strcmp(enable, "enable")) {
      for (int i = 3; i < argc; i++) {
        pcc_config.push_back(atof(argv[i]));
      }
      SetPccConfig(binder, true, pcc_config);
    }
  } else if (!strcmp(feature_type, set_igc_config_)) {
    const char *enable = argv[2];
    if (!strcmp(enable, "disable")) {
      SetIgcConfig(binder, false, nullptr);
    } else if (!strcmp(enable, "enable")) {
      const char *filename = argv[3];
      if (filename) {
        SetIgcConfig(binder, true, filename);
      } else {
        LOGE("Empty IGC config file name");
      }
    }
  } else if (!strcmp(feature_type, set_gc_config_)) {
    const char *enable = argv[2];
    if (!strcmp(enable, "disable")) {
      SetGcConfig(binder, false, nullptr);
    } else if (!strcmp(enable, "enable")) {
      const char *filename = argv[3];
      if (filename) {
      SetGcConfig(binder, true, filename);
      } else {
        LOGE("Empty GC config file name");
      }
    }
  } else if (!strcmp(feature_type, set_gamut_config_)) {
    const char *enable = argv[2];
    if (!strcmp(enable, "disable")) {
      SetGamutConfig(binder, false, nullptr);
    } else if (!strcmp(enable, "enable")) {
      const char *filename = argv[3];
      if (filename) {
        SetGamutConfig(binder, true, filename);
      } else {
        LOGE("Empty GAMUT config file name");
      }
    }
  } else {
    LOGI("Unsupported feature %s", argv[1]);
  }

  LOGI("---- Exit the test ----");

  return 0;
}
