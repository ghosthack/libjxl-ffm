#!/bin/sh
# Build libjxl 0.12 with Brotli for windows-x64 and stage its DLL closure.
# Run from an MSYS2 MINGW64 shell with cmake, ninja, and the x86_64 toolchain.
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION=0.12.0
SRC="${1:-$ROOT/build/libjxl-$VERSION}"
PREFIX="$ROOT/build/prefix-windows-x64"
DEST="$ROOT/natives/src/main/resources/natives/windows-x64"

mkdir -p "$ROOT/build"
if [ ! -d "$SRC" ]; then
  curl -sfL "https://github.com/libjxl/libjxl/archive/refs/tags/v$VERSION.tar.gz" \
    | tar xz -C "$ROOT/build" --exclude='*/tools/benchmark/*'
fi
(cd "$SRC" && ./deps.sh)

cmake -S "$SRC" -B "$SRC/build-ffm-windows-x64" -G Ninja \
  -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON -DBUILD_TESTING=OFF \
  -DJPEGXL_ENABLE_TOOLS=OFF -DJPEGXL_ENABLE_EXAMPLES=OFF \
  -DJPEGXL_ENABLE_MANPAGES=OFF -DJPEGXL_ENABLE_BENCHMARK=OFF \
  -DJPEGXL_ENABLE_JNI=OFF -DJPEGXL_ENABLE_SJPEG=OFF \
  -DJPEGXL_ENABLE_OPENEXR=OFF -DJPEGXL_ENABLE_DEVTOOLS=OFF \
  -DJPEGXL_ENABLE_DOXYGEN=OFF -DJPEGXL_ENABLE_FUZZERS=OFF \
  -DJPEGXL_ENABLE_PLUGINS=OFF -DJPEGXL_ENABLE_VIEWERS=OFF \
  -DJPEGXL_ENABLE_TCMALLOC=OFF \
  -DCMAKE_INSTALL_PREFIX="$PREFIX" -DCMAKE_INSTALL_LIBDIR=lib
cmake --build "$SRC/build-ffm-windows-x64" -j"$(nproc)" --target install

rm -rf "$DEST"
mkdir -p "$DEST"
: > "$DEST/manifest.txt"
for runtime in libwinpthread-1.dll libgcc_s_seh-1.dll libstdc++-6.dll; do
  cp "/mingw64/bin/$runtime" "$DEST/$runtime"
  echo "$runtime" >> "$DEST/manifest.txt"
done
for library in libbrotlicommon.dll libbrotlidec.dll libbrotlienc.dll \
    libjxl_cms.dll libjxl.dll libjxl_threads.dll; do
  source_file="$(find "$PREFIX/bin" -maxdepth 1 -iname "$library" -print -quit)"
  [ -n "$source_file" ] || { echo "ERROR: $library was not installed"; exit 1; }
  base="$(basename "$source_file")"
  cp "$source_file" "$DEST/$base"
  echo "$base" >> "$DEST/manifest.txt"
done

ALLOWED='libjxl|libbrotli|libwinpthread|libgcc_s_seh|libstdc\+\+|KERNEL32|USER32|ADVAPI32|bcrypt|msvcrt|ntdll|api-ms-'
for library in "$DEST"/*.dll; do
  if objdump -p "$library" | awk '/DLL Name:/{print $3}' | grep -viE "^($ALLOWED)"; then
    echo "ERROR: $library imports an unexpected DLL (above)"; exit 1
  fi
done

cp "$SRC/LICENSE" "$DEST/LICENSE.libjxl"
cp "$SRC/PATENTS" "$DEST/PATENTS.libjxl"
cp "$SRC/third_party/brotli/LICENSE" "$DEST/LICENSE.brotli"
cp "$SRC/third_party/highway/LICENSE" "$DEST/LICENSE.highway"
cp "$SRC/third_party/skcms/LICENSE" "$DEST/LICENSE.skcms"
{
  echo "libjxl $VERSION (windows-x64), built from unmodified source with MSYS2 mingw-w64"
  echo "Source: https://github.com/libjxl/libjxl/archive/refs/tags/v$VERSION.tar.gz"
  echo "License: BSD-3-Clause (see LICENSE.libjxl and PATENTS.libjxl)"
  echo "Bundled dependencies from libjxl deps.sh:"
  echo "  Brotli 1.2.0 / 028fb5a23661f123017c060daa546b55cf4bde29 (MIT)"
  echo "  Highway 457c891775a7397bdb0376bb1031e6e027af1c48 (Apache-2.0)"
  echo "  skcms 96d9171c94b937a1b5f0293de7309ac16311b722 (BSD-3-Clause)"
} > "$DEST/BUILD-INFO.txt"

echo "Staged $(find "$DEST" -maxdepth 1 -type f | wc -l) files into $DEST"
