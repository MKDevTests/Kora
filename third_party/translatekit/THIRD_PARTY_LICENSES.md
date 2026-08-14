# Third-party licenses

`translate-kit` is licensed under Apache-2.0 (see [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE)).
It statically links a number of third-party components into its native library
(`libtranslate-kit.so`). This file lists every such component, its license, and its
copyright holders.

The component list was derived from the symbols actually linked into the built
library, not merely from what the source tree checks out — components that are
vendored but **not** compiled into the shipped artifact (e.g. `fbgemm`, the
`simple-websocket-server`, the WASM/CUDA/ONNX paths) are intentionally omitted.

Each license was read from the component's own license file at the path given in
the table. The full, verbatim texts live in those paths inside the submodules; the
common permissive license bodies are reproduced once below, and the longer
Apache-2.0 and MPL-2.0 texts are referenced where they already exist in-tree.

## Components linked into the published library

| Component | Role | License | Copyright holder(s) | License file |
|-----------|------|---------|---------------------|--------------|
| Bergamot translation engine (`mozilla/translations`, inference layer) | NMT engine wrapper / orchestration | MPL-2.0 | Mozilla Corporation and contributors | [`third_party/translations/LICENSE`](third_party/translations/LICENSE) |
| Marian NMT (`marian-fork`) | Neural MT inference core | MIT | Marcin Junczys-Dowmunt, the University of Edinburgh, Adam Mickiewicz University, and contributors | `third_party/translations/inference/marian-fork/LICENSE.md` |
| SentencePiece | Subword tokenization | Apache-2.0 | Google LLC; marian fork © University of Edinburgh and contributors | `.../marian-fork/src/3rd_party/sentencepiece/LICENSE` |
| ssplit-cpp | Sentence splitting | Apache-2.0 | University of Edinburgh | `third_party/translations/inference/3rd_party/ssplit-cpp/LICENSE.md` |
| PCRE2 10.39 | Regex (ssplit-cpp dependency, built internally) | BSD-3-Clause (the "PCRE2 Licence") | University of Cambridge / Philip Hazel; JIT © Zoltan Herczeg and Tilera Corporation | upstream (fetched at build time; see note below) |
| intgemm | int8 GEMM kernels (x86_64) | MIT | University of Edinburgh and contributors | `.../marian-fork/src/3rd_party/intgemm/LICENSE` |
| ruy | float / int8 GEMM kernels (arm64; x86 float sgemm) | Apache-2.0 | Google LLC | `.../marian-fork/src/3rd_party/ruy/LICENSE` |
| simd_utils | SIMD helper routines | BSD-2-Clause | JishinMaster (Antoine Foucault) | `.../marian-fork/src/3rd_party/simd_utils/LICENSE` |
| FAISS (subset) | Similarity-search primitives | MIT | Facebook, Inc. and its affiliates | `.../marian-fork/src/3rd_party/faiss/LICENSE` |
| yaml-cpp | YAML config parsing | MIT | Jesse Beder | `.../marian-fork/src/3rd_party/yaml-cpp/LICENSE` |
| pathie-cpp | Filesystem path handling | BSD-2-Clause | Marvin Gülker | `.../marian-fork/src/3rd_party/pathie-cpp/LICENSE` |
| CLI11 | Command-line / option parsing | BSD-3-Clause | University of Cincinnati, Henry Schreiner | `.../marian-fork/src/3rd_party/CLI/LICENSE` |
| spdlog | Logging | MIT | Gabi Melman | `.../marian-fork/src/3rd_party/spdlog/LICENSE` |
| zlib | Compression | Zlib | Jean-loup Gailly and Mark Adler | `.../marian-fork/src/3rd_party/zlib/README` |
| zstr | zlib C++ stream wrapper | MIT | Matei David, Ontario Institute for Cancer Research | `.../marian-fork/src/3rd_party/zstr/LICENSE` |
| half_float | Half-precision (fp16) type | BSD-3-Clause | Christian Maiwald, Alexander Gessler | `.../marian-fork/src/3rd_party/half_float/umHalf.h` |
| cnpy | NumPy `.npz` I/O | MIT | Carl Rogers | `.../marian-fork/src/3rd_party/cnpy/LICENSE` |
| mio | Memory-mapped file I/O | MIT | https://github.com/mandreyel/mio contributors | `.../marian-fork/src/3rd_party/mio/LICENSE` |
| phf | Perfect-hash function | MIT | William Ahern | `.../marian-fork/src/3rd_party/phf/LICENSE` |
| CLD2 (Compact Language Detector 2) | Offline language detection | Apache-2.0 | Google Inc. | [`third_party/cld2/LICENSE`](third_party/cld2/LICENSE) |

> Paths abbreviated `.../marian-fork/...` are rooted at
> `third_party/translations/inference/marian-fork/...`.

### PCRE2

PCRE2 is not vendored in the source tree; ssplit-cpp downloads and statically
builds **PCRE2 10.39** when configured with `SSPLIT_USE_INTERNAL_PCRE2=ON` (which
`translate-kit` sets). PCRE2 is distributed under the "PCRE2 Licence", a
BSD-3-Clause-style license: Copyright © University of Cambridge; the original
author is Philip Hazel, and the just-in-time compiler is Copyright © Zoltan
Herczeg and Tilera Corporation. The full text ships with the PCRE2 source as its
`LICENCE` file (https://www.pcre.org/licence.txt).

### ssplit-cpp `nonbreaking_prefixes` (LGPL-2.1) — not distributed

ssplit-cpp's C++ and CMake code is Apache-2.0. Its `nonbreaking_prefixes/` data
files (copied from the Moses decoder) are LGPL-2.1. As ssplit-cpp's own license
states, **these files are read by the library at runtime and are not compiled
into it.** `translate-kit` runs ssplit in `paragraph` mode and bundles no runtime
prefix data, so no LGPL-2.1 material is distributed in the published artifact.

## Test data — not distributed in the published artifact

The following is used only by the test suite. It is `.gitignore`d (fetched, not
committed) and is **not** packaged into the released AAR (it is bundled into the
instrumented-test APK only, never the `main` source set).

| Artifact | License | Curator / source |
|----------|---------|------------------|
| `bergamot/ende.student.tiny11` (English→German tiny student model) | CC-BY-SA-4.0 | Roman Grundkiewicz, University of Edinburgh — http://statmt.org/bergamot/ |

## License texts

### Apache-2.0

Applies to `translate-kit` itself, SentencePiece, ssplit-cpp, ruy, and CLD2. The
full Apache License 2.0 text is in [`LICENSE`](LICENSE).

### MPL-2.0

Applies to the Bergamot inference layer. MPL-2.0 is file-level copyleft: the
covered source files remain under MPL-2.0 and may be combined with this project's
Apache-2.0 code. The full text is in
[`third_party/translations/LICENSE`](third_party/translations/LICENSE).

### MIT License

Applies to Marian NMT, yaml-cpp, zstr, cnpy, mio, phf, and spdlog (copyright
holders per the table above):

```
Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in the
Software without restriction, including without limitation the rights to use,
copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

### BSD-2-Clause

Applies to simd_utils and pathie-cpp (copyright holders per the table above):

```
Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice, this
   list of conditions and the following disclaimer in the documentation and/or
   other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

### BSD-3-Clause

Applies to CLI11, half_float, and PCRE2 (the PCRE2 Licence) (copyright holders per
the table above). Same terms as BSD-2-Clause, plus:

```
3. Neither the name of the copyright holder nor the names of its contributors may
   be used to endorse or promote products derived from this software without
   specific prior written permission.
```

### Zlib License

Applies to zlib (© Jean-loup Gailly and Mark Adler):

```
This software is provided 'as-is', without any express or implied warranty. In no
event will the authors be held liable for any damages arising from the use of this
software.

Permission is granted to anyone to use this software for any purpose, including
commercial applications, and to alter it and redistribute it freely, subject to
the following restrictions:

1. The origin of this software must not be misrepresented; you must not claim that
   you wrote the original software. If you use this software in a product, an
   acknowledgment in the product documentation would be appreciated but is not
   required.
2. Altered source versions must be plainly marked as such, and must not be
   misrepresented as being the original software.
3. This notice may not be removed or altered from any source distribution.
```

### CC-BY-SA-4.0

Applies to the test model only (not distributed; see above). Full text:
https://creativecommons.org/licenses/by-sa/4.0/legalcode

## Trademarks

The names "Mozilla", "Firefox", "Bergamot", and "Google" are trademarks of their
respective owners and are used here only to describe the provenance of the
components above. `translate-kit` is not a product of, and is not endorsed by,
Mozilla or Google. See [`NOTICE`](NOTICE).
