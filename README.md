# libjxl-ffm

Java 22 Panama FFM bindings for the complete public libjxl shared-library C API, with bundled
from-source native libraries. No JNI and no user-installed libjxl required.

| Artifact | Contents | License |
|---|---|---|
| `io.github.ghosthack:libjxl-ffm` | jextract-generated decoder, encoder, metadata, statistics, and thread-runner bindings | MIT |
| `io.github.ghosthack:libjxl-ffm-natives` (`macos-arm64`, `windows-x64`, `linux-x64`) | libjxl 0.12.0, Brotli, and platform runtime libraries | BSD-3-Clause and dependency licenses; see [THIRD-PARTY.md](THIRD-PARTY.md) |

The native jars include Brotli decoder, encoder, and common libraries. Highway
and skcms are linked into the libjxl libraries. Every jar contains the license
texts, source revisions, and a `BUILD-INFO.txt` manifest.

## Use

```xml
<dependency>
  <groupId>io.github.ghosthack</groupId>
  <artifactId>libjxl-ffm</artifactId>
  <version>0.12.0-0.1.0-SNAPSHOT</version>
</dependency>
<dependency>
  <groupId>io.github.ghosthack</groupId>
  <artifactId>libjxl-ffm-natives</artifactId>
  <version>0.12.0-0.1.0-SNAPSHOT</version>
  <classifier>macos-arm64</classifier> <!-- windows-x64 or linux-x64 -->
  <scope>runtime</scope>
</dependency>
```

On the module path, declare `requires libjxl.ffm;` and launch with
`--enable-native-access=libjxl.ffm`. On the class path, use
`--enable-native-access=ALL-UNNAMED`.

The generated entry point is `io.github.ghosthack.libjxlffm.libjxl.LibJxl`:

```java
import io.github.ghosthack.libjxlffm.libjxl.LibJxl;

int decoderVersion = LibJxl.JxlDecoderVersion(); // 12000 for libjxl 0.12.0
```

See [`SmokeTest.java`](core/src/test/java/io/github/ghosthack/libjxlffm/SmokeTest.java)
for a complete multithreaded lossless encode/decode example.

### Library resolution

1. `-Dlibjxlffm.libdir=<dir>` or `LIBJXL_FFM_LIBDIR` loads a compatible local
   installation. If the directory contains `manifest.txt`, its order is used.
2. The current platform's native jar is extracted once under
   `~/.cache/libjxl-ffm/<version>-<platform>/` and loaded in dependency order.
3. Libraries already loaded by the host application are used as a fallback.

## Build

The build scripts fetch libjxl 0.12.0 and its pinned dependencies, build shared
libraries, verify their runtime dependency closure, and stage a classifier jar:

```sh
build-natives/macos-arm64.sh
jextract/gen-bindings.sh
mvn clean install
```

Use `linux-x64.sh` on x86-64 Linux or `windows-x64.sh` from an MSYS2 MINGW64
shell. Generated native resources and downloaded build tools/sources are ignored
by Git. `jextract/gen-bindings.sh` expects jextract 22 under
`tools/jextract-22`, or at `$JEXTRACT_HOME`.

libjxl's C ABI is platform-portable for the supported targets, so bindings are
generated once on macOS and compiled/tested everywhere with JDK 22 or newer.
