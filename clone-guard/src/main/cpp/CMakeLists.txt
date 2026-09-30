cmake_minimum_required(VERSION 3.22)
project(cloneguard C)

add_library(cloneguard SHARED clone_guard.c)

# Android 15+ devices may use 16 KB pages, and Google Play requires 16 KB aligned
# native libraries. NDK r28+ does this by default; older NDKs need it explicitly.
target_link_options(cloneguard PRIVATE "-Wl,-z,max-page-size=16384")
