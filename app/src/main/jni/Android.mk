LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE:= libterminal_jni
LOCAL_SRC_FILES:= terminal_jni.c env.c regex_str.c
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)

#include $(CLEAR_VARS)
#LOCAL_MODULE:= libmain
#LOCAL_SRC_FILES:= main.cpp
#LOCAL_LDLIBS := -lpthread
#include $(BUILD_SHARED_LIBRARY)
