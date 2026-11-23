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

The body-map path geometry used from B4 onward is the MIT-licensed MuscleMap set,
reached through `references/openGym/frontend/src/lib/body-paths.js`. Only that path
data crosses into this project; openGym's own AGPL code, its rendering, and its
Gym visual exercise media are not used or copied.

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

### Data and content licences

Recorded with the phase that bundles each source: USDA FoodData Central (CC0) in B6,
cited clinical reference ranges in B7, public-domain or CC0 exercise data in B4, and
LOINC/UCUM terms in B12. A source whose licence cannot be established is not bundled.

### Build dependencies

Every resolved dependency of a client module is checked against
`gradle/licences/allowed.tsv` by the `checkLicences` task; the GPL family is never
allowed in the client.
