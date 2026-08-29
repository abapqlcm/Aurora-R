cmake_minimum_required(VERSION 3.22.1)
project("aurora_r" C CXX)

set(CMAKE_C_STANDARD 17)
set(CMAKE_CXX_STANDARD 17)

# کتابخانه‌های پیش‌ساخته که توسط GitHub Actions تولید شده‌اند
# (در workflow به src/main/jniLibs/<abi>/ کپی می‌شوند)
set(AETHER_LIB ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libaether.so)
set(HEV_LIB    ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libhev-socks5-tunnel.so)

add_library(aether SHARED IMPORTED)
set_target_properties(aether PROPERTIES
        IMPORTED_LOCATION ${AETHER_LIB}
        # cargo-ndk بدون soname می‌سازد؛ اگر ست نشود CMake مسیر مطلق رانر را در
        # DT_NEEDED می‌نویسد و روی گوشی لینکر کرش می‌کند. اینجا soname را دستی مشخص می‌کنیم.
        IMPORTED_SONAME "libaether.so")

add_library(hev_socks5_tunnel SHARED IMPORTED)
set_target_properties(hev_socks5_tunnel PROPERTIES
        IMPORTED_LOCATION ${HEV_LIB}
        IMPORTED_SONAME "libhev-socks5-tunnel.so")

# پل JNI ما
add_library(aurora_jni SHARED
        aurora_jni.c
        aether.h
        hev-socks5-tunnel.h)

target_include_directories(aurora_jni PRIVATE
        ${CMAKE_SOURCE_DIR}
        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/include)

find_library(log-lib log)

target_link_libraries(aurora_jni
        aether
        hev_socks5_tunnel
        ${log-lib})
