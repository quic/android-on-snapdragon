/*
 * Changes from Qualcomm Innovation Center are provided under the following license:
 * Copyright (c) 2023-2024 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

#ifndef __STC_OEM_IMP_HW_MODULATE_H__
#define __STC_OEM_IMP_HW_MODULATE_H__

#include <vector>
#include <map>
#include <mutex>
#include <utils/sys.h>
#include <private/snapdragon_color_intf.h>
#include <snapdragon_color_intf_priv.h>

#ifndef STCLIB_ON_LINUX
#include <IQClient.h>
#include <stc_demo_service.h>
#endif

using namespace std;

namespace snapdragoncolor {

class StcOemImpl;

typedef int (StcOemImpl::*SetPropFunc)(const ScPayload &);
typedef int (StcOemImpl::*GetPropFunc)(ScPayload *);
typedef int (StcOemImpl::*ProcessOpsFunc)(const ScPayload &, ScPayload *);

ScPostBlendInterface *GetScPostBlendInterface(uint32_t major_version, uint32_t minor_version);

enum FeatureType {
  kFeaturePcc,
  kFeatureIgc,
  kFeatureGc,
  kFeatureGamut,
};

#ifndef STCLIB_ON_LINUX
class StcOemImpl : public ScPostBlendInterface, public qClient::BnQClient {
#else
class StcOemImpl : public ScPostBlendInterface {
#endif
 public:
  StcOemImpl();
  int Init(const std::string &panel_name);
  int DeInit();
  int SetProperty(const ScPayload &payload);
  int GetProperty(ScPayload *payload);
  int ProcessOps(ScOps op, const ScPayload &input, ScPayload *output);

 private:
  int ProcessModeRenderIntent(const ScPayload &in, ScPayload *out);
  int ProcessModeSwAssets(const ScPayload &in, ScPayload *out);
  int GetNeedsUpdate(ScPayload *payload);
  int GetSupportTonemap(ScPayload *payload);
  int GetModeList(ScPayload *payload);
  int SetPostBlendGamutConfig(const ScPayload &payload);
  int SetPostBlendGammaConfig(const ScPayload &payload);
  int SetPostBlendInvGammaConfig(const ScPayload &payload);
  int SetColorTransform(const ScPayload &payload);

#ifndef STCLIB_ON_LINUX
  void TriggerScreenUpdate();
  android::status_t notifyCallback(uint32_t command, const android::Parcel *input_parcel,
                                   android::Parcel *output_parcel);
  android::sp<IQService> qservice_;
  OEMService *oemservice_ = nullptr;
#endif

  void OverridePcc(const ScPayload &in, ScPayload *out);
  void OverrideIgc(const ScPayload &in, ScPayload *out);
  void OverrideGc(const ScPayload &in, ScPayload *out);
  void OverrideCommon(const ScPayload &in, ScPayload *out, string feature_type, GammaPostBlendConfig *cfg_override);
  void OverrideGamut(const ScPayload &in, ScPayload *out);

  int GetIgcHwCap(android::Parcel *output_parcel);
  int GetGcHwCap(android::Parcel *output_parcel);

  int SetConfigCommon(const char *file_name, std::vector<uint32_t> &r, std::vector<uint32_t> &g,
                      std::vector<uint32_t> &b);
  int SetIgcConfig(bool enable, const char *file_name);
  int SetGcConfig(bool enable, const char *file_name);
  int SetPccConfig(bool enable, vector<double> config);
  int SetGamutConfig(bool enable, const char *file_name);

  std::map<ScProperty, SetPropFunc> set_prop_funcs_;
  std::map<ScProperty, GetPropFunc> get_prop_funcs_;
  std::map<ScOps, ProcessOpsFunc> process_op_funcs_;
  std::mutex lock_;
  bool init_done_ = false;
  ColorModeList modes_list_ = {};
  vector<FeatureType> dirty_feature_;

  // HW cap
  PostBlendInverseGammaHwConfig igc_cap_;
  PostBlendGammaHwConfig gc_cap_;

  // PCC override
  PccConfig pcc_config_override_;

  // IGC override
  GammaPostBlendConfig igc_config_override_ = GammaPostBlendConfig(LUT1D_ENTRIES_SIZE);

  // GC override
  GammaPostBlendConfig gc_config_override_ = GammaPostBlendConfig(LUT3D_GC_ENTRIES_SIZE);

  // Gamut override
  GamutConfig gamut_config_override_;
};

}  // namespace snapdragoncolor
#endif  // __STC_OEM_IMP_HW_MODULATE_H__
