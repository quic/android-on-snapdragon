LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
STC_INCLUDES                  := $(TOP)/vendor/qcom/proprietary/display-noship/snapdragoncolor/include/
LOCAL_MODULE                  := libstc-demo
LOCAL_MODULE_TAGS             := optional
LOCAL_CFLAGS                  := -Wno-missing-field-initializers -Wconversion\
                                 -DLOG_TAG=\"STC_OEM_SAMPLE\" -DUNIX_OS

LOCAL_SHARED_LIBRARIES        := liblog libcutils libutils libdisplaydebug libsdm-color libbinder libtinyxml2_1
LOCAL_SHARED_LIBRARIES        += libsdmutils libqservice

LOCAL_C_INCLUDES              += $(LOCAL_PATH)/inc
LOCAL_C_INCLUDES              += $(STC_INCLUDES)

LOCAL_HEADER_LIBRARIES        := display_headers display_proprietary_intf_headers

LOCAL_LDLIBS                  := -L$(SYSROOT)/usr/lib -llog

LOCAL_SRC_FILES               := src/stc_demo_service.cpp src/stc_demo_imp.cpp

LOCAL_PROPRIETARY_MODULE      := true
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE                  := stc_demo_test
LOCAL_MODULE_TAGS             := optional
LOCAL_CFLAGS                  := -Wno-missing-field-initializers -Wconversion\
                                  -DLOG_TAG=\"STC_OEM_TEST\" -DUNIX_OS

LOCAL_SHARED_LIBRARIES        := liblog libcutils libutils libbinder
LOCAL_SHARED_LIBRARIES        += libqservice libstc-demo

LOCAL_C_INCLUDES              := $(TOP)/vendor/qcom/proprietary/commonsys-intf/display/include/ \
LOCAL_C_INCLUDES              += $(LOCAL_PATH)/inc

LOCAL_HEADER_LIBRARIES        := display_headers

LOCAL_SRC_FILES               := test/stc_demo_test.cpp
LOCAL_PROPRIETARY_MODULE      := true
include $(BUILD_EXECUTABLE)
