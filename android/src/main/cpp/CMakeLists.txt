cmake_minimum_required(VERSION 3.22.1)
project(blcknative C)
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
get_filename_component(BLCKROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../.." ABSOLUTE)
add_library(blcknative SHARED
    native.c
    sms_jni.c
    "${BLCKROOT}/src/blcksms_frame.c"
    "${BLCKROOT}/src/blckui.c")
target_include_directories(blcknative PRIVATE "${BLCKROOT}/include")
target_compile_options(blcknative PRIVATE -Wall -Wextra -Werror -pedantic)
