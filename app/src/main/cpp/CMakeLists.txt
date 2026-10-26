cmake_minimum_required(VERSION 3.22.1)
project(voiceengine CXX)

set(ENGINE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../engine/src)

add_library(voiceengine SHARED
        jni_bridge.cpp
        ${ENGINE_DIR}/voice_engine.cpp)

target_include_directories(voiceengine PRIVATE ${ENGINE_DIR})
target_link_libraries(voiceengine log)
