# Third-party notices

## libjxl

The native classifier jars contain shared libraries built from unmodified
[libjxl 0.12.0 source](https://github.com/libjxl/libjxl/releases/tag/v0.12.0),
licensed under the BSD 3-Clause License. `LICENSE.libjxl`, `PATENTS.libjxl`, and
the exact source URL ship in every native jar.

## Brotli

Brotli 1.2.0 (`028fb5a23661f123017c060daa546b55cf4bde29`) is built from the
revision pinned by libjxl. `libbrotlicommon`, `libbrotlidec`, and
`libbrotlienc` are included as shared libraries and loaded before libjxl.
Brotli is MIT licensed; `LICENSE.brotli` ships in every native jar.

## Highway and skcms

The libjxl build statically incorporates Highway revision
`457c891775a7397bdb0376bb1031e6e027af1c48` (Apache-2.0) and skcms revision
`96d9171c94b937a1b5f0293de7309ac16311b722` (BSD-3-Clause). Their license texts
ship as `LICENSE.highway` and `LICENSE.skcms`.

Windows artifacts also include the MinGW-w64 GCC and pthread runtime DLLs
needed by the native build. Those runtimes retain their upstream licenses and
the GCC Runtime Library Exception where applicable.

The Java binding and loader code contains no libjxl source and is MIT licensed.
