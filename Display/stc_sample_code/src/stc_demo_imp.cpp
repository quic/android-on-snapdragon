/*
 * Changes from Qualcomm Innovation Center are provided under the following license:
 * Copyright (c) 2023-2024 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

#include <cstring>
#include <iostream>
#include <algorithm>
#include <fstream>
#include <android/log.h>
#include "stc_demo_imp.h"

#ifndef STCLIB_ON_LINUX
#include <binder/Parcel.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#endif

#define kPccCoefficientsNum 11  // Number of PCC coefficients for each component

#define LOGI(format, ...)                                                                 \
  (__android_log_print(ANDROID_LOG_DEBUG, "StcOemImpl", "%s: " format "\n", __FUNCTION__, \
                       ##__VA_ARGS__))
#define LOGE(format, ...)                                                                 \
  (__android_log_print(ANDROID_LOG_ERROR, "StcOemImpl", "%s: " format "\n", __FUNCTION__, \
                       ##__VA_ARGS__))

using namespace std;

namespace snapdragoncolor {

ScPostBlendInterface *GetScPostBlendInterface(uint32_t major_version, uint32_t minor_version) {
  ScPostBlendInterface *stc_intf = nullptr;
  if (major_version == 2 && minor_version == 0) {
    LOGI("StcOemImpl created");
    stc_intf = new StcOemImpl;
  } else {
    LOGE("Invalid major_version %d minor_version %d", major_version, minor_version);
  }

  return stc_intf;
}

StcOemImpl::StcOemImpl() {
  set_prop_funcs_[kPostBlendInverseGammaHwConfig] = &StcOemImpl::SetPostBlendInvGammaConfig;
  set_prop_funcs_[kPostBlendGammaHwConfig] = &StcOemImpl::SetPostBlendGammaConfig;
  set_prop_funcs_[kPostBlendGamutHwConfig] = &StcOemImpl::SetPostBlendGamutConfig;
  set_prop_funcs_[kSetColorTransform] = &StcOemImpl::SetColorTransform;

  get_prop_funcs_[kModeList] = &StcOemImpl::GetModeList;
  get_prop_funcs_[kNeedsUpdate] = &StcOemImpl::GetNeedsUpdate;
  get_prop_funcs_[kSupportToneMap] = &StcOemImpl::GetSupportTonemap;

  process_op_funcs_[kScModeRenderIntent] = &StcOemImpl::ProcessModeRenderIntent;
  process_op_funcs_[kScModeSwAssets] = &StcOemImpl::ProcessModeSwAssets;
}

int StcOemImpl::Init(const std::string &panel_name) {
  std::lock_guard<std::mutex> guard(lock_);
  init_done_ = true;

  // Creates fake mode with kOemModulateHw
  ColorMode mode;
  mode.intent = kOemModulateHw;
  modes_list_.list.push_back(mode);

  // Start OEMService and connect to it.
  LOGI("Initializing OEMService");
  OEMService::init();
  LOGI("Initializing OEMService...done!");

  LOGI("Getting IQService");
  android::sp<IOEMService> ioemservice = android::interface_cast<IOEMService>(
      android::defaultServiceManager()->getService(android::String16("display.oemservice")));
  LOGI("Getting IOEMService...done!");

  if (ioemservice.get()) {
    ioemservice->connect(android::sp<qClient::IQClient>(this));
    oemservice_ = reinterpret_cast<OEMService *>(ioemservice.get());
    LOGI("Acquired display.oemservice");
  } else {
    LOGE("Failed to acquire display.oemservice");
    return -EINVAL;
  }

  // Get QService: to trigger screen update
  LOGI("Getting IQService for screen refreshing");
  qservice_ = android::interface_cast<qService::IQService>(
      android::defaultServiceManager()->getService(android::String16("display.qservice")));
  if (qservice_ == NULL) {
    LOGE("Failed to acquire display.qservice");
  } else {
    LOGI("Getting IQService...done!");
  }

  return 0;
}

int StcOemImpl::DeInit() {
  std::lock_guard<std::mutex> guard(lock_);
  dirty_feature_.clear();
  init_done_ = false;
  return 0;
}

int StcOemImpl::SetProperty(const ScPayload &payload) {
  std::lock_guard<std::mutex> guard(lock_);

  if (init_done_ == false || payload.version != sizeof(ScPayload))
    return -EINVAL;

  auto it = set_prop_funcs_.find(payload.prop);
  if (it == set_prop_funcs_.end() || !it->second)
    return -EINVAL;

  return (this->*(it->second))(payload);
}

int StcOemImpl::GetProperty(ScPayload *payload) {
  std::lock_guard<std::mutex> guard(lock_);

  if (init_done_ == false || !payload || payload->version != sizeof(ScPayload))
    return -EINVAL;

  auto it = get_prop_funcs_.find(payload->prop);
  if (it == get_prop_funcs_.end() || !it->second)
    return -EINVAL;

  return (this->*(it->second))(payload);
}

int StcOemImpl::ProcessOps(ScOps op, const ScPayload &input, ScPayload *output) {
  std::lock_guard<std::mutex> guard(lock_);

  LOGI("Process Ops called, op = %d", op);
  if (init_done_ == false || !output) {
    LOGE("Invalid params init_done_ %d output %pK", init_done_, output);
    return -EINVAL;
  }

  auto it = process_op_funcs_.find(op);
  if (it == process_op_funcs_.end() || !it->second) {
    LOGE("Invalid ops %d", op);
    return -EINVAL;
  }

  return (this->*(it->second))(input, output);
}

int StcOemImpl::SetPostBlendGamutConfig(const ScPayload &payload) {
  if (payload.len != sizeof(PostBlendGamutHwConfig) || !payload.payload) {
    LOGE("len of Input params act %zd exp %d", payload.len, sizeof(PostBlendGamutHwConfig));
    return -EINVAL;
  }

  PostBlendGamutHwConfig *config = reinterpret_cast<PostBlendGamutHwConfig *>(payload.payload);
  LOGI("PostBlend Gamut HW Config: num_of_grid_entires %d, grid_entries_width %d",
       config->num_of_grid_entries, config->grid_entries_width);

  return 0;
}

int StcOemImpl::SetPostBlendGammaConfig(const ScPayload &payload) {
  if (payload.len != sizeof(PostBlendGammaHwConfig) || !payload.payload) {
    LOGE("len of Input params act %zd exp %d", payload.len, sizeof(PostBlendGammaHwConfig));
    return -EINVAL;
  }

  PostBlendGammaHwConfig *config = reinterpret_cast<PostBlendGammaHwConfig *>(payload.payload);
  gc_cap_ = *config;
  LOGI("PostBlend Gamma HW Config: num_of_entries %d, entries_width %d, hw_cap size %zu",
       config->num_of_entries, config->entries_width, config->hw_caps.size());
  return 0;
}

int StcOemImpl::SetPostBlendInvGammaConfig(const ScPayload &payload) {
  if (payload.len != sizeof(PostBlendInverseGammaHwConfig) || !payload.payload) {
    LOGE("len of Input params act %zd exp %d", payload.len, sizeof(PostBlendInverseGammaHwConfig));
    return -EINVAL;
  }

  PostBlendInverseGammaHwConfig *config =
      reinterpret_cast<PostBlendInverseGammaHwConfig *>(payload.payload);
  LOGI("PostBlend InverseGamma HW Config: num_of_entries %d, entries_width %d, hw_cap size %zu",
       config->num_of_entries, config->entries_width, config->hw_caps.size());

  igc_cap_ = *config;
  return 0;
}

int StcOemImpl::GetModeList(ScPayload *payload) {
  if (payload->len != sizeof(ColorModeList) || !payload->payload)
    return -EINVAL;

  ColorModeList *mode_list = reinterpret_cast<ColorModeList *>(payload->payload);
  mode_list->list = modes_list_.list;
  return 0;
}

int StcOemImpl::GetNeedsUpdate(ScPayload *payload) {
  bool *ptr;
  if (payload->len != sizeof(bool) || !payload->payload)
    return -EINVAL;

  ptr = reinterpret_cast<bool *>(payload->payload);
  if (dirty_feature_.empty()) {
    *ptr = false;
  } else {
    *ptr = true;
  }
  return 0;
}

int StcOemImpl::GetSupportTonemap(ScPayload *payload) {
  bool *ptr;
  if (payload->len != sizeof(bool) || !payload->payload)
    return -EINVAL;

  ptr = reinterpret_cast<bool *>(payload->payload);
  // Hard coding for false in default implementation.
  *ptr = false;
  return 0;
}

int StcOemImpl::SetColorTransform(const ScPayload &payload) {
  return 0;
}

int StcOemImpl::ProcessModeRenderIntent(const ScPayload &in, ScPayload *out) {
  if (dirty_feature_.empty()) {
    return 0;
  }

  for (auto feature : dirty_feature_) {
    switch (feature) {
      case kFeaturePcc:
        // apply PCC
        OverridePcc(in, out);
        break;
      case kFeatureIgc:
        // apply IGC
        OverrideIgc(in, out);
        break;
      case kFeatureGc:
        // apply GC
        OverrideGc(in, out);
        break;
      case kFeatureGamut:
        // apply Gamut
        OverrideGamut(in, out);
        break;
      default:
        LOGI("Unsupported feature %d", feature);
        break;
    }
  }

  dirty_feature_.clear();
  return 0;
}

int StcOemImpl::ProcessModeSwAssets(const ScPayload &in, ScPayload *out) {
  // No need to support the operation. Simply return 0
  return 0;
}

void StcOemImpl::OverridePcc(const ScPayload &in, ScPayload *out) {
  HwConfigOutputParams *output = reinterpret_cast<HwConfigOutputParams *>(out->payload);

  auto cur_hw = output->payload.begin();
  for (; cur_hw != output->payload.end(); cur_hw++) {
    if (cur_hw->hw_asset == kPbPCC) {
      if (!cur_hw->hw_payload.get()) {
        cur_hw->hw_payload = std::make_shared<PccConfig>();
      }
      PccConfig *out_cfg = reinterpret_cast<PccConfig *>(cur_hw->hw_payload.get());
      out_cfg->enabled = true;
      // overwrite existed PCC config in output payload
      std::memcpy(out_cfg, &pcc_config_override_, sizeof(pcc_coeff_data));
      LOGI("Overwrite PCC config in output payload");
    }
  }

  if (cur_hw == output->payload.end()) {
    HwConfigPayload new_payload = {};
    new_payload.hw_asset = kPbPCC;
    new_payload.hw_payload_len = sizeof(PccConfig);
    new_payload.hw_payload = std::make_shared<PccConfig>();
    PccConfig *out_cfg = reinterpret_cast<PccConfig *>(new_payload.hw_payload.get());
    out_cfg->enabled = true;
    std::memcpy(out_cfg, &pcc_config_override_, sizeof(pcc_coeff_data));
    // push new created PCC config into output payload
    output->payload.push_back(new_payload);
    LOGI("Push new PCC config into output payload");
  }
}

void StcOemImpl::OverrideIgc(const ScPayload &in, ScPayload *out) {
  OverrideCommon(in, out, kPbIgc, &igc_config_override_);
}

void StcOemImpl::OverrideGc(const ScPayload &in, ScPayload *out) {
  OverrideCommon(in, out, kPbGC, &gc_config_override_);
}

void StcOemImpl::OverrideCommon(const ScPayload &in, ScPayload *out, string feature_type,
                                GammaPostBlendConfig *cfg_override) {
  HwConfigOutputParams *output = reinterpret_cast<HwConfigOutputParams *>(out->payload);
  auto cur_hw = output->payload.begin();

  for (; cur_hw != output->payload.end(); cur_hw++) {
    if (cur_hw->hw_asset == feature_type) {
      GammaPostBlendConfig *cfg =
          reinterpret_cast<GammaPostBlendConfig *>(cur_hw->hw_payload.get());
      std::memcpy(cfg, cfg_override, sizeof(GammaPostBlendConfig));
      LOGI("Overwrite config for %s in output payload", feature_type.c_str());
      break;
    }
  }

  if (cur_hw == output->payload.end()) {
    HwConfigPayload new_payload = {};
    new_payload.hw_asset = kPbIgc;
    new_payload.hw_payload_len = sizeof(GammaPostBlendConfig);
    new_payload.hw_payload = std::make_shared<GammaPostBlendConfig>(cfg_override->r.size());
    GammaPostBlendConfig *cfg =
        reinterpret_cast<GammaPostBlendConfig *>(new_payload.hw_payload.get());
    std::memcpy(cfg, cfg_override, sizeof(GammaPostBlendConfig));
    output->payload.push_back(new_payload);
    LOGI("Push new config for %s into output payload", feature_type.c_str());
  }
}

void StcOemImpl::OverrideGamut(const ScPayload &in, ScPayload *out) {
  HwConfigOutputParams *output = reinterpret_cast<HwConfigOutputParams *>(out->payload);

  auto cur_hw = output->payload.begin();
  for (; cur_hw != output->payload.end(); cur_hw++) {
    if (cur_hw->hw_asset == kPbGamut) {
      if (!cur_hw->hw_payload.get()) {
        cur_hw->hw_payload = std::make_shared<GamutConfig>();
      }
      GamutConfig *out_cfg = reinterpret_cast<GamutConfig *>(cur_hw->hw_payload.get());
      out_cfg->enabled = true;
      // overwrite existed Gamut config in output payload
      std::memcpy(out_cfg, &gamut_config_override_, sizeof(GamutConfig));
      LOGI("Overwrite GAMUT config in output payload");
    }
  }

  if (cur_hw == output->payload.end()) {
    HwConfigPayload new_payload = {};
    new_payload.hw_asset = kPbGamut;
    new_payload.hw_payload_len = sizeof(GamutConfig);
    new_payload.hw_payload = std::make_shared<GamutConfig>();
    GamutConfig *out_cfg = reinterpret_cast<GamutConfig *>(new_payload.hw_payload.get());
    out_cfg->enabled = true;
    std::memcpy(out_cfg, &gamut_config_override_, sizeof(GamutConfig));
    // push new created Gamut config into output payload
    output->payload.push_back(new_payload);
    LOGI("Push new GAMUT config into output payload");
  }
}

#ifndef STCLIB_ON_LINUX
// Qclient methods
android::status_t StcOemImpl::notifyCallback(uint32_t command, const android::Parcel *input_parcel,
                                             android::Parcel *output_parcel) {
  android::status_t status = -EINVAL;
  uint32_t disp_id, size;
  const char *file_name = nullptr;
  vector<double> pcc_config;
  bool enable = false;

  switch (command) {
    case IOEMService::SET_PCC_CONFIG:
      if (!input_parcel) {
        LOGE("OEMService command = %d: input_parcel needed.", command);
        break;
      }
      enable = input_parcel->readBool();
      if (enable) {
        size = input_parcel->readUint32();
        for (int i = 0; i < size; i++) {
          pcc_config.push_back(input_parcel->readDouble());
        }
      }
      status = SetPccConfig(enable, pcc_config);
      break;

    case IOEMService::GET_IGC_HW_CAP:
      if (!output_parcel) {
        LOGE("OEMService command = %d: output_parcel needed.", command);
        break;
      }
      status = GetIgcHwCap(output_parcel);
      break;

    case IOEMService::GET_GC_HW_CAP:
      if (!output_parcel) {
        LOGE("OEMService command = %d: output_parcel needed.", command);
        break;
      }
      status = GetGcHwCap(output_parcel);
      break;

    case IOEMService::SET_IGC_CONFIG:
      if (!input_parcel) {
        LOGE("OEMService command = %d: input_parcel needed.", command);
        break;
      }
      enable = input_parcel->readBool();
      if (enable) {
        file_name = input_parcel->readCString();
      }
      status = SetIgcConfig(enable, file_name);
      break;

    case IOEMService::SET_GC_CONFIG:
      if (!input_parcel) {
        LOGE("OEMService command = %d: input_parcel needed.", command);
        break;
      }
      enable = input_parcel->readBool();
      if (enable) {
        file_name = input_parcel->readCString();
      }
      status = SetGcConfig(enable, file_name);
      break;

    case IOEMService::SET_GAMUT_CONFIG:
      if (!input_parcel) {
        LOGE("OEMService command = %d: input_parcel needed.", command);
        break;
      }
      enable = input_parcel->readBool();
      file_name = input_parcel->readCString();
      status = SetGamutConfig(enable, file_name);
      break;

    default:
      LOGI("OEMService command = %d is not supported.", command);
      break;
  }

  return status;
}

void StcOemImpl::TriggerScreenUpdate() {
  // trigger screen refresh
  LOGI("Trigger screen refresh");
  android::Parcel inParcel, outParcel;
  inParcel.writeInt32(1);
  inParcel.setDataPosition(0);
  if (qservice_ != NULL) {
    int err =
        qservice_->dispatch(qService::IQService::TOGGLE_SCREEN_UPDATES, &inParcel, &outParcel);
    if (err) {
      LOGE("Failed to dispatch screen refresh");
    }
  }
}
#endif

int StcOemImpl::SetPccConfig(bool enable, vector<double> config) {
  LOGI("Entering, enable %d", enable);

  if (!enable) {
    pcc_config_override_.enabled = false;
  } else {
    int size = config.size() / 3;
    if (size > kPccCoefficientsNum) {
      size = kPccCoefficientsNum;  // Max number of coefficients for one component
    }
    std::memcpy(&pcc_config_override_.pcc_info.r.c, config.data(), size * sizeof(double));
    std::memcpy(&pcc_config_override_.pcc_info.g.c, config.data() + size, size * sizeof(double));
    std::memcpy(&pcc_config_override_.pcc_info.b.c, config.data() + size * 2, size * sizeof(double));
    pcc_config_override_.enabled = true;

    LOGI("Cache PCC R: [%f, %f, %f, %f, %f, %f, %f, %f, %f, %f, %f]", pcc_config_override_.pcc_info.r.c,
        pcc_config_override_.pcc_info.r.r, pcc_config_override_.pcc_info.r.g, pcc_config_override_.pcc_info.r.b,
        pcc_config_override_.pcc_info.r.rr, pcc_config_override_.pcc_info.r.gg, pcc_config_override_.pcc_info.r.bb,
        pcc_config_override_.pcc_info.r.rg, pcc_config_override_.pcc_info.r.gb, pcc_config_override_.pcc_info.r.rb,
        pcc_config_override_.pcc_info.r.rgb);
    LOGI("Cache PCC G: [%f, %f, %f, %f, %f, %f, %f, %f, %f, %f, %f]", pcc_config_override_.pcc_info.g.c,
        pcc_config_override_.pcc_info.g.r, pcc_config_override_.pcc_info.g.g, pcc_config_override_.pcc_info.g.b,
        pcc_config_override_.pcc_info.g.rr, pcc_config_override_.pcc_info.g.gg, pcc_config_override_.pcc_info.g.bb,
        pcc_config_override_.pcc_info.g.rg, pcc_config_override_.pcc_info.g.gb, pcc_config_override_.pcc_info.g.rb,
        pcc_config_override_.pcc_info.g.rgb);
    LOGI("Cache PCC B: [%f, %f, %f, %f, %f, %f, %f, %f, %f, %f, %f]", pcc_config_override_.pcc_info.b.c,
        pcc_config_override_.pcc_info.b.r, pcc_config_override_.pcc_info.b.g, pcc_config_override_.pcc_info.b.b,
        pcc_config_override_.pcc_info.b.rr, pcc_config_override_.pcc_info.b.gg, pcc_config_override_.pcc_info.b.bb,
        pcc_config_override_.pcc_info.b.rg, pcc_config_override_.pcc_info.b.gb, pcc_config_override_.pcc_info.b.rb,
        pcc_config_override_.pcc_info.b.rgb);
  }

  // mark dirty
  dirty_feature_.push_back(kFeaturePcc);
  TriggerScreenUpdate();
  return 0;
}

int StcOemImpl::GetIgcHwCap(android::Parcel *output_parcel) {
  LOGI("Entering");

  if (igc_cap_.hw_caps.empty()) {
    LOGI("Default mode: number of entries: %d, entry_width: %d", igc_cap_.num_of_entries,
         igc_cap_.entries_width);
    output_parcel->writeUint32(1);  // number of configs
    output_parcel->writeCString("default mode");
    output_parcel->writeUint32(igc_cap_.num_of_entries);
    output_parcel->writeUint32(igc_cap_.entries_width);
  } else {
    uint32_t valid_config = 0;
    bool default_mode_included = false;
    for (auto cap : igc_cap_.hw_caps) {
      bool valid = std::get<1>(cap);
      if (valid) {
        valid_config++;
        if (igc_cap_.num_of_entries == std::get<2>(cap).first &&
            igc_cap_.entries_width == std::get<2>(cap).second) {
          default_mode_included = true;
        }
      }
    }
    if (!default_mode_included) {
      valid_config++;
    }
    output_parcel->writeUint32(valid_config);  // number of configs
    LOGI("number of configs %d", valid_config);

    if (!default_mode_included) {
      // write default mode
      output_parcel->writeCString("default mode");
      output_parcel->writeUint32(igc_cap_.num_of_entries);
      output_parcel->writeUint32(igc_cap_.entries_width);
      LOGI("mode name: default mode, number of entries: %d, entry_width: %d",
           igc_cap_.num_of_entries, igc_cap_.entries_width);
    }
    for (auto cap : igc_cap_.hw_caps) {
      if (std::get<1>(cap)) {
        output_parcel->writeCString(std::get<0>(cap).c_str());
        output_parcel->writeUint32((uint32_t)(std::get<2>(cap).first));
        output_parcel->writeUint32((uint32_t)(std::get<2>(cap).second));
        LOGI("mode name: %s, number of entries: %d, entry_width: %d", std::get<2>(cap).first,
             std::get<2>(cap).second);
      }
    }
  }

  return 0;
}

int StcOemImpl::GetGcHwCap(android::Parcel *output_parcel) {
  LOGI("Entering");

  if (gc_cap_.hw_caps.empty()) {
    LOGI("Default mode: number of entries: %d, entry_width: %d", gc_cap_.num_of_entries,
         gc_cap_.entries_width);
    output_parcel->writeUint32(1);  // number of configs
    output_parcel->writeCString("default mode");
    output_parcel->writeUint32(gc_cap_.num_of_entries);
    output_parcel->writeUint32(gc_cap_.entries_width);
  } else {
    uint32_t valid_config = 0;
    bool default_mode_included = false;
    for (auto cap : gc_cap_.hw_caps) {
      bool valid = std::get<1>(cap);
      if (valid) {
        valid_config++;
        if (gc_cap_.num_of_entries == std::get<2>(cap).first &&
            gc_cap_.entries_width == std::get<2>(cap).second) {
          default_mode_included = true;
        }
      }
    }
    if (!default_mode_included) {
      valid_config++;
    }
    output_parcel->writeUint32(valid_config);  // number of configs
    LOGI("number of configs %d", valid_config);

    if (!default_mode_included) {
      // write default mode
      output_parcel->writeCString("default mode");
      output_parcel->writeUint32(gc_cap_.num_of_entries);
      output_parcel->writeUint32(gc_cap_.entries_width);
      LOGI("mode name: default mode, number of entries: %d, entry_width: %d",
           gc_cap_.num_of_entries, gc_cap_.entries_width);
    }
    for (auto cap : gc_cap_.hw_caps) {
      if (std::get<1>(cap)) {
        output_parcel->writeCString(std::get<0>(cap).c_str());
        output_parcel->writeUint32((uint32_t)(std::get<2>(cap).first));
        output_parcel->writeUint32((uint32_t)(std::get<2>(cap).second));
        LOGI("mode name: %s, number of entries: %d, entry_width: %d", std::get<2>(cap).first,
             std::get<2>(cap).second);
      }
    }
  }

  return 0;
}

int StcOemImpl::SetIgcConfig(bool enable, const char *file_name) {
  LOGI("Entering, enable %d", enable);
  if (!enable) {
    igc_config_override_.enabled = false;
  } else {
    int ret = SetConfigCommon(file_name, igc_config_override_.r, igc_config_override_.g, igc_config_override_.b);
    if (ret) {
      LOGE("Failed to set IGC config, ret %d", ret);
      return ret;
    }
    igc_config_override_.enabled = true;
  }

  // mark dirty
  dirty_feature_.push_back(kFeatureIgc);
  TriggerScreenUpdate();
  return 0;
}

int StcOemImpl::SetGcConfig(bool enable, const char *file_name) {
  LOGI("Entering, enable %d", enable);
  if (!enable) {
    gc_config_override_.enabled = false;
  } else {
    int ret = SetConfigCommon(file_name, gc_config_override_.r, gc_config_override_.g,
                            gc_config_override_.b);
    if (ret) {
      LOGE("Failed to set GC config, ret %d", ret);
      return ret;
    }
    gc_config_override_.enabled = true;
  }

  // mark dirty
  dirty_feature_.push_back(kFeatureGc);
  TriggerScreenUpdate();
  return 0;
}

int StcOemImpl::SetConfigCommon(const char *file_name, std::vector<uint32_t> &r,
                                std::vector<uint32_t> &g, std::vector<uint32_t> &b) {
  LOGI("Entering");

  ifstream in;
  in.open(file_name, ios::in);  // Open the file and read only the file
  if (in.fail()) {
    LOGE("Failed to open file %s", file_name);
    return -EINVAL;
  }

  // Read first line and parse the number of entries
  string first_line;
  int num_entries = 0;

  getline(in, first_line);
  LOGI("First line: %s", first_line.c_str());
  auto pos = first_line.find(':');
  if (pos != -1) {
    string tmp = first_line.substr(pos);
    num_entries = atoi(first_line.substr(pos + 1).c_str());
  }
  if (!num_entries) {
    LOGI("Failed to get number of entries");
    return -EINVAL;
  }

  LOGI("Number of entries is %d", num_entries);
  r.clear();
  g.clear();
  b.clear();
  string tmp;

  while (r.size() < num_entries && !in.eof()) {
    getline(in, tmp, ',');
    r.push_back((uint32_t)atoi(tmp.c_str()));
  }

  while (g.size() < num_entries && !in.eof()) {
    getline(in, tmp, ',');
    g.push_back((uint32_t)atoi(tmp.c_str()));
  }

  while (b.size() < num_entries && !in.eof()) {
    getline(in, tmp, ',');
    b.push_back((uint32_t)atoi(tmp.c_str()));
  }

  if (r.size() != num_entries || g.size() != num_entries || b.size() != num_entries) {
    LOGE("Size checking failure, exp num of entries %d, r, g, b entry size: %zu, %zu, %zu",
         num_entries, r.size(), g.size(), b.size());
    r.clear();
    g.clear();
    b.clear();
    return -EINVAL;
  }

  return 0;
}

int StcOemImpl::SetGamutConfig(bool enable, const char *file_name) {
  LOGI("Entering, enable %d", enable);

  if (!enable) {
    gamut_config_override_.enabled = false;
  } else {
    ifstream in;
    in.open(file_name, ios::in);  // Open the file and read only the file
    if (in.fail()) {
      LOGE("Failed to open file %s", file_name);
      return -EINVAL;
    }

    string line;
    char *value;
    for (int i = 0; i < LUT3D_ENTRIES_SIZE; i++) {
      getline(in, line);
      if (line.empty()) {
        LOGE("Invalid value for entry %d", i);
        return -EINVAL;
      }
      auto pos1 = line.find(",");
      auto pos2 = line.find(",", pos1 + 1);
      if (pos1 != std::string::npos && pos2 != std::string::npos) {
        string r_value = line.substr(0, pos1);
        string g_value = line.substr(pos1 + 1, pos2);
        string b_value = line.substr(pos2 + 1, line.length());
        gamut_config_override_.gamut_info.entries[i].out.r = stoul(r_value);
        gamut_config_override_.gamut_info.entries[i].out.g = stoul(g_value);
        gamut_config_override_.gamut_info.entries[i].out.b = stoul(b_value);
      } else {
        LOGE("Invalid value for entry %d", i);
        return -EINVAL;
      }
    }

    gamut_config_override_.gamut_info.uniform = 1;
    gamut_config_override_.gamut_info.num_entries = LUT3D_ENTRIES_SIZE;
    gamut_config_override_.enabled = true;
  }

  // mark dirty
  dirty_feature_.push_back(kFeatureGamut);
  TriggerScreenUpdate();
  return 0;
}

}  //namespace snapdragoncolor
