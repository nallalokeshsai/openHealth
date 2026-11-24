# openHealth — notices

openHealth is licensed **Apache-2.0** (see [`LICENSE`](LICENSE)), except the optional
server: `server/` is **AGPL-3.0** when it is built in B13 (`pr.md` §12.4,
`Tech-Stack.md` §13). Nothing under `server/` exists yet, so this repository is
currently Apache-2.0 throughout.

It is not a medical device (`pr.md` §12.1).

## Third-party notices

### Typefaces — none bundled

openHealth ships no typeface at 1.0 (`DESIGN.md` §3.1). Type is the platform UI font
(Roboto on Android) through `FontFamily.Default`, so there is no font file to licence.
If a later brand pass bundles Inter (OFL) it is recorded here first.

### Icons — drawn for openHealth

The icon set in `ui/…/icons/` is original line art for this product (`DESIGN.md` §10).
Nothing is copied from Material Icons, SF Symbols or openGym's set.

### MuscleMap — body diagram geometry (MIT)

The body-map path geometry used from B4 onward is the MIT-licensed MuscleMap set by
Melih Colpan. MuscleMap ships its paths as Swift source; openGym converted them to the
JSON module `references/openGym/frontend/src/lib/body-paths.js` (openGym 1.4.5, commit
`e4a68a94c`), and that converted copy is what was copied, with only the path data, into
`tools/bodygeometry/upstream/body-paths.js`. `tools/bodygeometry/convert.mjs` converts it
to `ui/src/main/res/raw/body_paths.tsv`. The loader, the renderer, the layers, the recovery
engine and the interaction are openHealth's own; openGym's own AGPL code, its rendering,
and its Gym visual exercise media are not used or copied.

```
MIT License

Copyright (c) 2026 Melih Colpan

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### Free Exercise DB — exercise library text (Unlicense, public domain)

The bundled exercise library is Free Exercise DB by the yuhonas project and its
contributors (`https://github.com/yuhonas/free-exercise-db`, commit
`f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5`), released into the public domain under the
Unlicense. Its `dist/exercises.json` is copied verbatim into
`tools/exerciseindex/upstream/exercises.json`; `tools/exerciseindex` maps its 17 muscle
names to openHealth's whole muscles, adds the body-part tag it implies, and writes
`core/src/main/resources/app/openhealth/core/exercise/exercises.tsv`. **Only text is
used** (name, muscles, equipment, level, mechanic, force, instructions). The vendored JSON
keeps the dataset's `images` keys and `category`; no image file is copied, referenced at
runtime, hotlinked or derived from, and the generated library carries no image path or
URL. The dataset's images are Everkinetic material with its own share-alike terms, which
is why they are not used. The instructions are redistributed as they are, as the Unlicense
permits; the enrichment of four muscles with no name in the dataset, the alternatives
ranking and the body-part tags are openHealth's own.

```
This is free and unencumbered software released into the public domain.

Anyone is free to copy, modify, publish, use, compile, sell, or distribute this
software, either in source code form or as a compiled binary, for any purpose,
commercial or non-commercial, and by any means.

In jurisdictions that recognize copyright laws, the author or authors of this
software dedicate any and all copyright interest in the software to the public
domain. We make this dedication for the benefit of the public at large and to
the detriment of our heirs and successors. We intend this dedication to be an
overt act of relinquishment in perpetuity of all present and future rights to
this software under copyright law.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

For more information, please refer to <https://unlicense.org>
```

### SQLCipher for Android (BSD-3-Clause)

The encrypted database uses the SQLCipher Community Edition
(`net.zetetic:sqlcipher-android`, BSD-3-Clause). The licence requires this notice and
the conditions below to be reachable from the app itself; the About screen in B3
reproduces them, as `docs/adr/0005-core-and-data.md` records.

```
Copyright (c) 2008-2023, ZETETIC LLC
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of the ZETETIC LLC nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY ZETETIC LLC ''AS IS'' AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL ZETETIC LLC BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

### Bouncy Castle (Bouncy Castle Licence, MIT-style)

Argon2id key derivation for the encrypted export uses
`org.bouncycastle:bcprov-jdk18on`. The Bouncy Castle Licence is to be read in the same
way as the MIT licence; copyright (c) 2000–2026 The Legion of the Bouncy Castle Inc.
(`https://www.bouncycastle.org/licence.html`). Bouncy Castle is used as a library and is
not modified.

### UCUM — unit codes (attribution)

Values are stored with Unified Code for Units of Measure codes (`pr.md` §7.2). UCUM is
published by the Regenstrief Institute (`https://ucum.org/ucum`); the app uses the
codes and conversion factors as identifiers and cites the specification. No UCUM text is
reproduced in the product.

### Compendium of Physical Activities (cited MET values)

The MET values in `core/…/formula/MetTable.kt` are a curated selection of numeric values
from the 2024 Adult Compendium of Physical Activities (Herrmann et al., *Journal of Sport
and Health Science* 13(1):6-12, 2024; `https://doi.org/10.1016/j.jshs.2023.10.005`). MET
values are cited facts; the Compendium's text and tables are not reproduced. The
citation table in `core/…/citation/Citations.kt` records the source, and every exercise
energy estimate names it.

### Test-only dependencies

Kotest, Robolectric and Roborazzi run on the JVM only and never enter the APK. Their
licences and every transitive are recorded in `gradle/licences/allowed.tsv`.

### Data and content licences

Recorded with the phase that bundles each source: Free Exercise DB (Unlicense / public
domain) in B4, USDA FoodData Central (CC0) in B6, cited clinical reference ranges in B7,
and LOINC/UCUM terms in B12. A source whose licence cannot be established is not bundled.

### Build dependencies

Every resolved dependency of a client module is checked against
`gradle/licences/allowed.tsv` by the `checkLicences` task; the GPL family is never
allowed in the client.
