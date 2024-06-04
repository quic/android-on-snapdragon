/*
 * Changes from Qualcomm Innovation Center are provided under the following license:
 * Copyright (c) 2023-2024 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

#include <fcntl.h>
#include <stdint.h>
#include <android/log.h>
#include <sys/types.h>
#include <binder/Parcel.h>
#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/IPCThreadState.h>
#include <cutils/android_filesystem_config.h>
#include <stc_demo_service.h>

#define LOGI(format, ...)                                                                 \
  (__android_log_print(ANDROID_LOG_DEBUG, "OEMService", "%s: " format "\n", __FUNCTION__, \
                       ##__VA_ARGS__))
#define LOGE(format, ...)                                                                 \
  (__android_log_print(ANDROID_LOG_ERROR, "OEMService", "%s: " format "\n", __FUNCTION__, \
                       ##__VA_ARGS__))

using namespace android;
using namespace qClient;

namespace snapdragoncolor {

IMPLEMENT_META_INTERFACE(OEMService, "android.display.IOEMService");

status_t BnOEMService::onTransact(uint32_t code, const Parcel &data, Parcel *reply,
                                  uint32_t flags) {
  LOGI("code: %d", code);
  // IPC should be from certain processes only
  IPCThreadState *ipc = IPCThreadState::self();
  const int callerPid = ipc->getCallingPid();
  const int callerUid = ipc->getCallingUid();

  const bool permission =
      (callerUid == AID_MEDIA || callerUid == AID_GRAPHICS || callerUid == AID_ROOT ||
       callerUid == AID_CAMERASERVER || callerUid == AID_AUDIO || callerUid == AID_SYSTEM ||
       callerUid == AID_MEDIA_CODEC);

  if (code == CONNECT_OEM_CLIENT) {
    CHECK_INTERFACE(IOEMService, data, reply);
    if (callerUid != AID_GRAPHICS) {
      LOGE("display.qservice CONNECT_OEM_CLIENT access denied: pid=%d uid=%d", callerPid,
           callerUid);
      return PERMISSION_DENIED;
    }
    sp<IQClient> client = interface_cast<IQClient>(data.readStrongBinder());
    connect(client);
    return NO_ERROR;
  } else if (code > COMMAND_LIST_START && code < COMMAND_LIST_END) {
    if (!permission) {
      LOGE("display.qservice access denied: command=%d pid=%d uid=%d", code, callerPid, callerUid);
      return PERMISSION_DENIED;
    }
    CHECK_INTERFACE(IOEMService, data, reply);
    dispatch(code, &data, reply);
    return NO_ERROR;
  } else {
    return BBinder::onTransact(code, data, reply, flags);
  }
}

OEMService *OEMService::sOEMService = NULL;

void OEMService::connect(const sp<qClient::IQClient> &client) {
  LOGI("client connected");
  mClient = client;
}

status_t OEMService::dispatch(uint32_t command, const Parcel *inParcel, Parcel *outParcel) {
  status_t err = (status_t)FAILED_TRANSACTION;
  IPCThreadState *ipc = IPCThreadState::self();
  //Rewind parcel in case we're calling from the same process
  bool sameProcess = (ipc->getCallingPid() == getpid());
  if (sameProcess)
    inParcel->setDataPosition(0);
  if (mClient.get()) {
    LOGI("Dispatching command: %d", command);
    err = mClient->notifyCallback(command, inParcel, outParcel);
    //Rewind parcel in case we're calling from the same process
    if (sameProcess)
      outParcel->setDataPosition(0);
  }
  return err;
}

void OEMService::init() {
  if (!sOEMService) {
    sOEMService = new OEMService();

    LOGI("Creating defaultServiceManager");
    sp<IServiceManager> sm = defaultServiceManager();
    LOGI("Creating defaultServiceManager...done!");

    LOGI("Adding display.oemservice to defaultServiceManager");
    sm->addService(String16("display.oemservice"), sOEMService);
    LOGI("Adding display.oemservice to defaultServiceManager...done!");

    if (sm->checkService(String16("display.oemservice")) != NULL)
      LOGI("Adding display.oemservice succeeded");
    else
      LOGI("Adding display.oemservice failed");
  }
}
}  // namespace snapdragoncolor
