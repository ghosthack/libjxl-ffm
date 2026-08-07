#!/bin/sh
# Generate Java 22 FFM stubs for libjxl's public C API, then route symbol
# resolution through LibjxlLibs so applications can use bundled natives.
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JX="${JEXTRACT_HOME:-$ROOT/tools/jextract-22}/bin/jextract"
PREFIX="${LIBJXL_PREFIX:-$ROOT/build/prefix-macos-arm64}"
OUT="$ROOT/core/src/main/java"
PKG="io.github.ghosthack.libjxlffm.libjxl"
SDK="$(xcrun --show-sdk-path)"

[ -x "$JX" ] || { echo "jextract not found at $JX"; exit 1; }
[ -f "$PREFIX/include/jxl/decode.h" ] || {
  echo "libjxl headers not found under $PREFIX (set LIBJXL_PREFIX or build natives first)"; exit 1;
}

GEN_DIR="$OUT/$(echo "$PKG" | tr . /)"
rm -rf "$GEN_DIR"

set --
while IFS=' ' read -r option symbol; do
  case "$option" in
    --include-function|--include-constant|--include-struct|--include-typedef|--include-union|--include-var)
      set -- "$@" "$option" "$symbol" ;;
    *) echo "ERROR: invalid option in jextract/includes.txt: $option"; exit 1 ;;
  esac
done < "$ROOT/jextract/includes.txt"

"$JX" --output "$OUT" -t "$PKG" \
  --header-class-name LibJxl \
  -I "$SDK/usr/include" \
  -I "$PREFIX/include" \
  -l ":$PREFIX/lib/libjxl.dylib" \
  -l ":$PREFIX/lib/libjxl_threads.dylib" \
  "$@" \
  "$ROOT/jextract/libjxl_api.h"

LOOKUP_FILE="$(grep -rl 'static final SymbolLookup SYMBOL_LOOKUP' "$GEN_DIR")"
[ -n "$LOOKUP_FILE" ] || { echo "ERROR: generated symbol lookup not found"; exit 1; }
perl -0777 -pi -e \
  's/static final SymbolLookup SYMBOL_LOOKUP = .*?;/static final SymbolLookup SYMBOL_LOOKUP = io.github.ghosthack.libjxlffm.LibjxlLibs.lookup();/s' \
  "$LOOKUP_FILE"

if grep -rn "$PREFIX" "$GEN_DIR" >/dev/null; then
  echo "ERROR: build-prefix paths survived in generated stubs"; exit 1
fi
grep -q "LibjxlLibs.lookup()" "$LOOKUP_FILE" || {
  echo "ERROR: lookup patch did not apply"; exit 1;
}

echo "Generated $(find "$GEN_DIR" -name '*.java' | wc -l | tr -d ' ') files into $GEN_DIR"
