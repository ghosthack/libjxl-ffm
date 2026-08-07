#!/bin/sh
# Build libjxl 0.12 with Brotli for linux-x64 and stage relocatable shared libs.
# Requires: cmake, a C/C++ toolchain, patchelf, curl, and tar.
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION=0.12.0
SRC="${1:-$ROOT/build/libjxl-$VERSION}"
PREFIX="$ROOT/build/prefix-linux-x64"
DEST="$ROOT/natives/src/main/resources/natives/linux-x64"

mkdir -p "$ROOT/build"
if [ ! -d "$SRC" ]; then
  curl -sfL "https://github.com/libjxl/libjxl/archive/refs/tags/v$VERSION.tar.gz" | tar xz -C "$ROOT/build"
fi
(cd "$SRC" && ./deps.sh)

cmake -S "$SRC" -B "$SRC/build-ffm-linux-x64" \
  -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON -DBUILD_TESTING=OFF \
  -DCMAKE_SHARED_LINKER_FLAGS='-static-libstdc++ -static-libgcc' \
  -DJPEGXL_ENABLE_TOOLS=OFF -DJPEGXL_ENABLE_EXAMPLES=OFF \
  -DJPEGXL_ENABLE_MANPAGES=OFF -DJPEGXL_ENABLE_BENCHMARK=OFF \
  -DJPEGXL_ENABLE_JNI=OFF -DJPEGXL_ENABLE_SJPEG=OFF \
  -DJPEGXL_ENABLE_OPENEXR=OFF -DJPEGXL_ENABLE_DEVTOOLS=OFF \
  -DJPEGXL_ENABLE_DOXYGEN=OFF -DJPEGXL_ENABLE_FUZZERS=OFF \
  -DJPEGXL_ENABLE_PLUGINS=OFF -DJPEGXL_ENABLE_VIEWERS=OFF \
  -DJPEGXL_ENABLE_TCMALLOC=OFF \
  -DCMAKE_INSTALL_PREFIX="$PREFIX" -DCMAKE_INSTALL_LIBDIR=lib
cmake --build "$SRC/build-ffm-linux-x64" -j"$(nproc)" --target install

rm -rf "$DEST"
mkdir -p "$DEST"
: > "$DEST/manifest.txt"
for link in \
    libbrotlicommon.so.1 libbrotlidec.so.1 libbrotlienc.so.1 \
    libjxl_cms.so.0.12 libjxl.so.0.12 libjxl_threads.so.0.12; do
  source_file="$(readlink -f "$PREFIX/lib/$link")"
  soname="$(patchelf --print-soname "$source_file")"
  cp "$source_file" "$DEST/$soname"
  chmod 644 "$DEST/$soname"
  patchelf --set-rpath '$ORIGIN' "$DEST/$soname"
  echo "$soname" >> "$DEST/manifest.txt"
done

for library in "$DEST"/*.so.*; do
  if readelf -d "$library" | awk '/NEEDED/ {print $NF}' | tr -d '[]' \
      | grep -vE '^(libjxl|libbrotli)' \
      | grep -vE '^(libc|libm|libpthread|libdl|librt)\.so|^ld-linux'; then
    echo "ERROR: $library has an unexpected dependency (above)"; exit 1
  fi
done

cp "$SRC/LICENSE" "$DEST/LICENSE.libjxl"
cp "$SRC/PATENTS" "$DEST/PATENTS.libjxl"
cp "$SRC/third_party/brotli/LICENSE" "$DEST/LICENSE.brotli"
cp "$SRC/third_party/highway/LICENSE" "$DEST/LICENSE.highway"
cp "$SRC/third_party/skcms/LICENSE" "$DEST/LICENSE.skcms"
{
  echo "libjxl $VERSION (linux-x64), built from unmodified source"
  echo "Source: https://github.com/libjxl/libjxl/archive/refs/tags/v$VERSION.tar.gz"
  echo "License: BSD-3-Clause (see LICENSE.libjxl and PATENTS.libjxl)"
  echo "Bundled dependencies from libjxl deps.sh:"
  echo "  Brotli 1.2.0 / 028fb5a23661f123017c060daa546b55cf4bde29 (MIT)"
  echo "  Highway 457c891775a7397bdb0376bb1031e6e027af1c48 (Apache-2.0)"
  echo "  skcms 96d9171c94b937a1b5f0293de7309ac16311b722 (BSD-3-Clause)"
} > "$DEST/BUILD-INFO.txt"

echo "Staged $(find "$DEST" -maxdepth 1 -type f | wc -l | tr -d ' ') files into $DEST"
