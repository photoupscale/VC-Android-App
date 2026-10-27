# Desktop build of the voice engine for tests and preset tuning.
# The Android app builds the same sources from app/src/main/cpp/CMakeLists.txt.
cmake_minimum_required(VERSION 3.18)
project(voice_engine_desktop CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
if(NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE Release)
endif()

add_library(voice_engine STATIC src/voice_engine.cpp src/voice_match.cpp)
target_include_directories(voice_engine PUBLIC src)

add_executable(vc_wav tools/vc_wav.cpp)
target_link_libraries(vc_wav voice_engine)

add_executable(engine_test tools/engine_test.cpp)
target_link_libraries(engine_test voice_engine)

enable_testing()
add_test(NAME engine_test COMMAND engine_test)

add_executable(match_test tools/match_test.cpp)
target_link_libraries(match_test voice_engine)
add_test(NAME match_test COMMAND match_test)
