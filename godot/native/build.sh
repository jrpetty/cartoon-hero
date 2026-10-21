#!/usr/bin/env bash
# Build BBNative against a prebuilt godot-cpp (tag godot-4.5-stable), for
# Linux and Windows x86_64, into ../bin/. Same flags godot-cpp uses for
# template_release, plus strict IEEE floating point (no FMA contraction), so
# results match the GDScript versions bit for bit.
#   GODOT_CPP=/path/to/godot-cpp bash build.sh
# (Build godot-cpp first: scons platform=linux target=template_release, and
#  scons platform=windows target=template_release use_mingw=yes.)
set -euo pipefail
cd "$(dirname "$0")"
G="${GODOT_CPP:-godot-cpp}"
INC="-I$G/include -I$G/gen/include -I$G/gdextension -Isrc"
DEFS="-DTHREADS_ENABLED -DNDEBUG -DGDEXTENSION"
FLAGS="-std=c++17 -O2 -fno-exceptions -ffp-contract=off -fno-fast-math -fvisibility=hidden"
mkdir -p ../bin
g++ -shared -fPIC -m64 -march=x86-64 $FLAGS $DEFS -DLINUX_ENABLED -DUNIX_ENABLED $INC src/*.cpp \
  "$G/bin/libgodot-cpp.linux.template_release.x86_64.a" -static-libgcc -static-libstdc++ \
  -o ../bin/libbbnative.linux.template_release.x86_64.so
x86_64-w64-mingw32-g++ -shared $FLAGS $DEFS -DWINDOWS_ENABLED $INC src/*.cpp \
  "$G/bin/libgodot-cpp.windows.template_release.x86_64.a" -static-libgcc -static-libstdc++ -static \
  -Wl,--no-undefined -o ../bin/libbbnative.windows.template_release.x86_64.dll
ls -la ../bin
