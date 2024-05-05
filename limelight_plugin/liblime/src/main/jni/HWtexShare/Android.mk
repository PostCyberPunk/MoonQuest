APP_STL := c++_shared
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := shared-texture
LOCAL_SRC_FILES := JSharedTexture.cpp SharedTexture.cpp
LOCAL_LDLIBS    := -lGLESv2 -lEGL -ljnigraphics -llog -landroid -lEGL
LOCAL_CPPFLAGS  := -std=c++11
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE    := jni-util
LOCAL_SRC_FILES := JNIUtil.cpp JSharedTexture.cpp SharedTexture.cpp
LOCAL_LDLIBS    := -lGLESv2 -llog -landroid -lEGL
LOCAL_CPPFLAGS  := -std=c++11
include $(BUILD_SHARED_LIBRARY)