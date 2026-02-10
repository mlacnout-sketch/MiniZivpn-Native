LOCAL_PATH := $(call my-dir)

#########################################################################
# 1. PREBUILT LIBRARIES (libuz, libload, libpdnsd)
#    Diambil dari folder jni/libs/<abi>/
#########################################################################

include $(CLEAR_VARS)
LOCAL_MODULE := libuz
LOCAL_SRC_FILES := libs/$(TARGET_ARCH_ABI)/libuz.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libload
LOCAL_SRC_FILES := libs/$(TARGET_ARCH_ABI)/libload.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libpdnsd_prebuilt
LOCAL_SRC_FILES := libs/$(TARGET_ARCH_ABI)/libpdnsd.so
include $(PREBUILT_SHARED_LIBRARY)

#########################################################################
# 2. HELPER LIBRARY: libancillary
#########################################################################

include $(CLEAR_VARS)
LOCAL_MODULE := libancillary
LOCAL_CFLAGS += -O2 -I$(LOCAL_PATH)/libancillary
LOCAL_SRC_FILES := libancillary/fd_recv.c libancillary/fd_send.c
include $(BUILD_STATIC_LIBRARY)

#########################################################################
# 3. EXECUTABLE: xsock (packaged as libxsock.so)
#########################################################################

include $(CLEAR_VARS)
LOCAL_MODULE := xsock

# Define flags
LOCAL_CFLAGS := -O2 -Wall -DANDROID -D_GNU_SOURCE -std=c99

# Include paths
LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/xSocks/src \
    $(LOCAL_PATH)/xSocks/3rd/libuv/include \
    $(LOCAL_PATH)/xSocks/3rd/libsodium/src/libsodium/include \
    $(LOCAL_PATH)/xSocks/3rd/c-ares/include

# Source files
# Note: Manually listing files is safer than wildcards in some NDK versions, 
# but for brevity/maintenance we use wildcard here if supported, or list key files.
# We exclude android.c if it conflicts or is not needed for the CLI executable.
XSOCK_SRC_FILES := $(wildcard $(LOCAL_PATH)/xSocks/src/*.c)
LOCAL_SRC_FILES := $(XSOCK_SRC_FILES:$(LOCAL_PATH)/%=%)

# Link against prebuilt libs (libuz = libuv variant?, libload)
# and system libs
LOCAL_SHARED_LIBRARIES := libuz libload
LOCAL_LDLIBS := -llog -ldl

include $(BUILD_EXECUTABLE)

#########################################################################
# 4. EXECUTABLE: tun2socks (packaged as libtun2socks.so)
#########################################################################

include $(CLEAR_VARS)
LOCAL_MODULE := tun2socks
LOCAL_CFLAGS := -std=gnu99 -DBADVPN_THREADWORK_USE_PTHREAD -DBADVPN_LINUX -DBADVPN_BREACTOR_BADVPN -D_GNU_SOURCE -DBADVPN_USE_SELFPIPE -DBADVPN_USE_EPOLL -DBADVPN_LITTLE_ENDIAN -DBADVPN_THREAD_SAFE -DNDEBUG -DANDROID
LOCAL_STATIC_LIBRARIES := libancillary
LOCAL_C_INCLUDES := $(LOCAL_PATH)/libancillary $(LOCAL_PATH)/badvpn/lwip/src/include/ipv4 $(LOCAL_PATH)/badvpn/lwip/src/include/ipv6 $(LOCAL_PATH)/badvpn/lwip/src/include $(LOCAL_PATH)/badvpn/lwip/custom $(LOCAL_PATH)/badvpn/
LOCAL_LDLIBS := -ldl -llog

# Daftar source file badvpn/tun2socks dipersingkat untuk kerapihan, 
# tapi tetap mengambil dari folder badvpn/
LOCAL_SRC_FILES := \
    badvpn/base/BLog_syslog.c \
    badvpn/system/BReactor_badvpn.c \
    badvpn/system/BSignal.c \
    badvpn/system/BConnection_unix.c \
    badvpn/system/BTime.c \
    badvpn/system/BUnixSignal.c \
    badvpn/system/BNetwork.c \
    badvpn/flow/StreamRecvInterface.c \
    badvpn/flow/PacketRecvInterface.c \
    badvpn/flow/PacketPassInterface.c \
    badvpn/flow/StreamPassInterface.c \
    badvpn/flow/SinglePacketBuffer.c \
    badvpn/flow/BufferWriter.c \
    badvpn/flow/PacketBuffer.c \
    badvpn/flow/PacketStreamSender.c \
    badvpn/flow/PacketPassConnector.c \
    badvpn/flow/PacketProtoFlow.c \
    badvpn/flow/PacketPassFairQueue.c \
    badvpn/flow/PacketProtoEncoder.c \
    badvpn/flow/PacketProtoDecoder.c \
    badvpn/socksclient/BSocksClient.c \
    badvpn/tuntap/BTap.c \
    badvpn/lwip/src/core/timers.c \
    badvpn/lwip/src/core/udp.c \
    badvpn/lwip/src/core/memp.c \
    badvpn/lwip/src/core/init.c \
    badvpn/lwip/src/core/pbuf.c \
    badvpn/lwip/src/core/tcp.c \
    badvpn/lwip/src/core/tcp_out.c \
    badvpn/lwip/src/core/netif.c \
    badvpn/lwip/src/core/def.c \
    badvpn/lwip/src/core/mem.c \
    badvpn/lwip/src/core/tcp_in.c \
    badvpn/lwip/src/core/stats.c \
    badvpn/lwip/src/core/inet_chksum.c \
    badvpn/lwip/src/core/ipv4/icmp.c \
    badvpn/lwip/src/core/ipv4/igmp.c \
    badvpn/lwip/src/core/ipv4/ip4_addr.c \
    badvpn/lwip/src/core/ipv4/ip_frag.c \
    badvpn/lwip/src/core/ipv4/ip4.c \
    badvpn/lwip/src/core/ipv4/autoip.c \
    badvpn/lwip/src/core/ipv6/ethip6.c \
    badvpn/lwip/src/core/ipv6/inet6.c \
    badvpn/lwip/src/core/ipv6/ip6_addr.c \
    badvpn/lwip/src/core/ipv6/mld6.c \
    badvpn/lwip/src/core/ipv6/dhcp6.c \
    badvpn/lwip/src/core/ipv6/icmp6.c \
    badvpn/lwip/src/core/ipv6/ip6.c \
    badvpn/lwip/src/core/ipv6/ip6_frag.c \
    badvpn/lwip/src/core/ipv6/nd6.c \
    badvpn/lwip/custom/sys.c \
    badvpn/tun2socks/tun2socks.c \
    badvpn/base/DebugObject.c \
    badvpn/base/BLog.c \
    badvpn/base/BPending.c \
    badvpn/system/BDatagram_unix.c \
    badvpn/flowextra/PacketPassInactivityMonitor.c \
    badvpn/tun2socks/SocksUdpGwClient.c \
    badvpn/udpgw_client/UdpGwClient.c

include $(BUILD_EXECUTABLE)

#########################################################################
# 5. EXECUTABLE: pdnsd (packaged as libpdnsd.so)
#########################################################################

include $(CLEAR_VARS)
LOCAL_MODULE := pdnsd
LOCAL_CFLAGS := -Wall -O2 -I$(LOCAL_PATH)/pdnsd
# Menggunakan wildcard untuk source files pdnsd
PDNSD_SOURCES := $(wildcard $(LOCAL_PATH)/pdnsd/src/*.c)
LOCAL_SRC_FILES := $(PDNSD_SOURCES:$(LOCAL_PATH)/%=%)
include $(BUILD_EXECUTABLE)

#########################################################################
# 6. MAIN JNI LIBRARY: system
#    Ini yang dipanggil oleh Flutter/Java
#########################################################################

include $(CLEAR_VARS)
LOCAL_MODULE:= system
LOCAL_C_INCLUDES:= $(LOCAL_PATH)/libancillary
LOCAL_SRC_FILES:= system.cpp
LOCAL_LDLIBS := -ldl -llog
LOCAL_STATIC_LIBRARIES := cpufeatures libancillary

# Link library prebuilt jika system.cpp membutuhkannya secara langsung
# LOCAL_SHARED_LIBRARIES := libuz libload

include $(BUILD_SHARED_LIBRARY)

$(call import-module,android/cpufeatures)