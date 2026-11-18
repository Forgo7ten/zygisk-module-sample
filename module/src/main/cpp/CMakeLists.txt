cmake_minimum_required(VERSION 3.22.1)

if (NOT DEFINED MODULE_NAME OR MODULE_NAME STREQUAL "")
    message(FATAL_ERROR "MODULE_NAME is not set")
endif ()

project("${MODULE_NAME}" LANGUAGES C CXX)

message(STATUS "Build type: ${CMAKE_BUILD_TYPE}, ABI: ${ANDROID_ABI}")

add_library(${MODULE_NAME} SHARED
    main.cpp
)

# C++ standard
target_compile_features(${MODULE_NAME} PRIVATE cxx_std_20)

set_target_properties(${MODULE_NAME} PROPERTIES
    CXX_EXTENSIONS OFF
)

# Compile options
target_compile_options(${MODULE_NAME} PRIVATE
    -Werror=format
    -fdata-sections
    -ffunction-sections

    $<$<COMPILE_LANGUAGE:CXX>:-fno-exceptions>
    $<$<COMPILE_LANGUAGE:CXX>:-fno-rtti>

    $<$<CONFIG:Debug>:-O0>
    $<$<NOT:$<CONFIG:Debug>>:-O2>

    $<$<NOT:$<CONFIG:Debug>>:-fvisibility=hidden>
    $<$<AND:$<NOT:$<CONFIG:Debug>>,$<COMPILE_LANGUAGE:CXX>>:-fvisibility-inlines-hidden>
)

# Link options
target_link_options(${MODULE_NAME} PRIVATE
    -Wl,--hash-style=both
    $<$<NOT:$<CONFIG:Debug>>:-Wl,--exclude-libs,ALL>
    $<$<NOT:$<CONFIG:Debug>>:-Wl,--gc-sections>
)

target_link_libraries(${MODULE_NAME} PRIVATE log)
