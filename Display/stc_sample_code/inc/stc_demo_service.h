/*
 * Changes from Qualcomm Innovation Center are provided under the following license:
 * Copyright (c) 2023-2024 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

#ifndef __STC_OEM_SERVICE_H__
#define __STC_OEM_SERVICE_H__

#include <stdint.h>
#include <sys/types.h>
#include <utils/Errors.h>
#include <utils/RefBase.h>
#include <binder/IInterface.h>
#include <binder/IServiceManager.h>
#include <binder/IBinder.h>
#include <binder/Parcel.h>
#include <IQClient.h>
#include <IQService.h>

using android::BpInterface;
using android::IBinder;
using android::Parcel;
using android::sp;
using android::status_t;
using qClient::IQClient;
using qService::IQService;

namespace snapdragoncolor {

class IOEMService : public android::IInterface {
 public:
  DECLARE_META_INTERFACE(OEMService);
  enum {
    COMMAND_LIST_START = android::IBinder::FIRST_CALL_TRANSACTION,
    CONNECT_OEM_CLIENT = 2,
    GET_IGC_HW_CAP = 3,
    GET_GC_HW_CAP = 4,
    SET_IGC_CONFIG = 5,
    SET_GC_CONFIG = 6,
    SET_PCC_CONFIG = 7,
    SET_GAMUT_CONFIG = 8,
    COMMAND_LIST_END = 0xFF,
  };

  virtual void connect(const android::sp<qClient::IQClient> &client) = 0;
  virtual android::status_t dispatch(uint32_t command, const android::Parcel *inParcel,
                                     android::Parcel *outParcel) = 0;
};

class BnOEMService : public android::BnInterface<IOEMService> {
 public:
  virtual android::status_t onTransact(uint32_t code, const android::Parcel &data,
                                       android::Parcel *reply, uint32_t flags = 0);
};

class OEMService : public BnOEMService {
 public:
  virtual ~OEMService(){};
  virtual void connect(const android::sp<qClient::IQClient> &client);
  virtual android::status_t dispatch(uint32_t command, const android::Parcel *data,
                                     android::Parcel *reply);
  static void init();

 private:
  OEMService(){};
  android::sp<qClient::IQClient> mClient;
  static OEMService *sOEMService;
};

class BpOEMService : public BpInterface<IOEMService> {
 public:
  BpOEMService(const sp<IBinder> &impl) : BpInterface<IOEMService>(impl) {}

  virtual void connect(const sp<IQClient> &client) {
    Parcel data, reply;
    data.writeInterfaceToken(IOEMService::getInterfaceDescriptor());
    data.writeStrongBinder(IInterface::asBinder(client));
    remote()->transact(CONNECT_OEM_CLIENT, data, &reply);
  }

  virtual android::status_t dispatch(uint32_t command, const Parcel *inParcel, Parcel *outParcel) {
    status_t err = (status_t)android::FAILED_TRANSACTION;
    Parcel data;
    Parcel *reply = outParcel;
    data.writeInterfaceToken(IOEMService::getInterfaceDescriptor());
    if (inParcel && inParcel->dataSize() > 0)
      data.appendFrom(inParcel, 0, inParcel->dataSize());
    err = remote()->transact(command, data, reply);
    return err;
  }
};

}  // namespace snapdragoncolor

#endif  // __STC_OEM_SERVICE_H__
