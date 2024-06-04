project(STC-SAMPLE)
cmake_minimum_required(VERSION 3.12)
add_compile_options(-std=c++11 -Wno-unused-parameter)
add_definitions("-DSTCLIB_ON_LINUX")
include_directories (./inc
                     ../../../../../../../hardware/qcom/display/sdm/include/
                     ../../../../display-noship/snapdragoncolor/include/
                     ../../../../../../../hardware/qcom/display/
					 ../../../../../../../hardware/qcom/display/libqservice/
                     ../../../../../opensource/commonsys-intf/display/include/
                     ../../../../../proprietary/commonsys-intf/display/include/)

add_library(stc-oem-HW-modulate SHARED
           ./src/stc_oem_imp_HW_modulate.cpp)

target_link_libraries(stc-oem-HW-modulate dl)
set(CMAKE_BUILD_TYPE Debug)
set(CMAKE_SKIP_BUILD_RPATH true)