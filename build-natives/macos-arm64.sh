#!/bin/sh
# Build libjxl 0.12 and its bundled Brotli dependency for macos-arm64, then
# stage relocatable dylibs for the native classifier jar.
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION=0.12.0
SRC="${1:-$ROOT/build/libjxl-$VERSION}"
PREFIX="$ROOT/build/prefix-macos-arm64"
DEST="$ROOT/natives/src/main/resources/natives/macos-arm64"

mkdir -p "$ROOT/build"
if [ ! -d "$SRC" ]; then
  curl -sfL "https://github.com/libjxl/libjxl/archive/refs/tags/v$VERSION.tar.gz" | tar xz -C "$ROOT/build"
fi
(cd "$SRC" && ./deps.sh)

cmake -S "$SRC" -B "$SRC/build-ffm-macos-arm64" \
  -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON -DBUILD_TESTING=OFF \
  -DJPEGXL_ENABLE_TOOLS=OFF -DJPEGXL_ENABLE_EXAMPLES=OFF \
  -DJPEGXL_ENABLE_MANPAGES=OFF -DJPEGXL_ENABLE_BENCHMARK=OFF \
  -DJPEGXL_ENABLE_JNI=OFF -DJPEGXL_ENABLE_SJPEG=OFF \
  -DJPEGXL_ENABLE_OPENEXR=OFF -DJPEGXL_ENABLE_DEVTOOLS=OFF \
  -DJPEGXL_ENABLE_DOXYGEN=OFF -DJPEGXL_ENABLE_FUZZERS=OFF \
  -DJPEGXL_ENABLE_PLUGINS=OFF -DJPEGXL_ENABLE_VIEWERS=OFF \
  -DJPEGXL_ENABLE_TCMALLOC=OFF \
  -DCMAKE_INSTALL_PREFIX="$PREFIX" -DCMAKE_INSTALL_LIBDIR=lib
cmake --build "$SRC/build-ffm-macos-arm64" -j"$(sysctl -n hw.ncpu)" --target install

rm -rf "$DEST"
mkdir -p "$DEST"
: > "$DEST/manifest.txt"
for library in \
    libbrotlicommon.1.dylib libbrotlidec.1.dylib libbrotlienc.1.dylib \
    libjxl_cms.0.12.dylib libjxl.0.12.dylib libjxl_threads.0.12.dylib; do
  source_file="$(readlink -f "$PREFIX/lib/$library")"
  cp "$source_file" "$DEST/$library"
  chmod 644 "$DEST/$library"
  echo "$library" >> "$DEST/manifest.txt"
done

# Resolve the staged closure relative to itself after extraction from the jar.
for library in "$DEST"/*.dylib; do
  base="$(basename "$library")"
  install_name_tool -id "@loader_path/$base" "$library"
  otool -L "$library" | tail -n +2 | awk '{print $1}' | while IFS= read -r dependency; do
    case "$dependency" in
      @rpath/*) install_name_tool -change "$dependency" "@loader_path/${dependency#@rpath/}" "$library" ;;
    esac
  done
done

for library in "$DEST"/*.dylib; do
  if otool -L "$library" | tail -n +2 | grep -vE '@loader_path/|/usr/lib/|/System/Library/'; then
    echo "ERROR: $library has a non-relocatable dependency (above)"; exit 1
  fi
done

cp "$SRC/LICENSE" "$DEST/LICENSE.libjxl"
cp "$SRC/PATENTS" "$DEST/PATENTS.libjxl"
cp "$SRC/third_party/brotli/LICENSE" "$DEST/LICENSE.brotli"
cp "$SRC/third_party/highway/LICENSE" "$DEST/LICENSE.highway"
cp "$SRC/third_party/skcms/LICENSE" "$DEST/LICENSE.skcms"
{
  echo "libjxl $VERSION (macos-arm64), built from unmodified source"
  echo "Source: https://github.com/libjxl/libjxl/archive/refs/tags/v$VERSION.tar.gz"
  echo "License: BSD-3-Clause (see LICENSE.libjxl and PATENTS.libjxl)"
  echo "Bundled dependencies from libjxl deps.sh:"
  echo "  Brotli 1.2.0 / 028fb5a23661f123017c060daa546b55cf4bde29 (MIT)"
  echo "  Highway 457c891775a7397bdb0376bb1031e6e027af1c48 (Apache-2.0)"
  echo "  skcms 96d9171c94b937a1b5f0293de7309ac16311b722 (BSD-3-Clause)"
} > "$DEST/BUILD-INFO.txt"

echo "Staged $(find "$DEST" -maxdepth 1 -type f | wc -l | tr -d ' ') files into $DEST"
