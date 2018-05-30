cmake_minimum_required(VERSION 3.10)

project(equihash VERSION 0.1 DESCRIPTION "jni binds for refernce equihash implementation")

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -msse -msse2 -msse3")

find_package(JNI REQUIRED)
include_directories(${JNI_INCLUDE_DIRS})

include_directories(.)
include_directories(blake)



add_library(equihash SHARED equihash.cpp net_bigtangle_equihash_EquihashSolver.h pow.cc blake/blake2b.cpp)
