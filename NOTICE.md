# Notices and licences

**openHealth** is licensed under the **Apache License, Version 2.0** (`LICENSE`), except
`server/`, which is licensed **AGPL-3.0** and is built in B13 (`pr.md` §12.4, `Tech-Stack.md` §13).
Nothing in this repository is licensed under a GPL/AGPL/LGPL-family licence except that later
`server/` tree; `gradle/client-licences.txt` records the licence of every dependency that ships and
`checkLicences` fails the build otherwise.

This file carries the notices the licences require. It is updated by the phase that first uses an
asset, font, data set or code system.

## MuscleMap body geometry (MIT)

The 2D muscle map's path geometry is derived from **MuscleMap** by Melih Colpan and is used under
the MIT licence. The paths reach openHealth through `references/openGym/NOTICE.md`, which recorded
that MuscleMap ships the paths as Swift source, that they were converted to a JSON module with the
sub-group shapes dropped, and that nothing else about the artwork was changed. openHealth imports
only that path geometry, in B4 (`DESIGN.md` §9.1, Appendix C); the renderer, layers and colouring
are openHealth's own. No openGym code, catalogue, muscle-map data or media is used.

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

## Typeface

No typeface is bundled. DESIGN.md §3.1 makes the platform UI font the brand (Roboto on Android
through `FontFamily.Default`), so the app ships no font file and no font licence applies. Inter
(OFL) is the only approved alternative and is not used; shipping it would be a dated decision in
DESIGN.md first.

## Dependencies

The shipped client classpath is Apache-2.0 throughout at B0 (AndroidX, Jetpack Compose, Kotlin and
kotlinx). The reviewed list, module by module, is `gradle/client-licences.txt`. The B1 screenshot
and contrast tooling (Roborazzi, Robolectric, JUnit 4 and the JUnit vintage engine) is test-only and
never ships; it is recorded in `docs/adr/0003-pinned-versions.md`.

## Pending notices

These arrive with the phases that add them and must be recorded here before that phase exits:

| Asset | Phase | Status |
|---|---|---|
| UI typeface (§3.1) | B1 | **Resolved:** none bundled; the platform UI font is used, so no font licence applies. |
| USDA FoodData Central (CC0) food index | B6 | not yet bundled |
| Open Food Facts (ODbL) attribution | B6 | not yet used |
| Exercise library source | B4 | not yet chosen |
| LOINC / UCUM code systems | B12 | not yet used |
| Garmin FIT SDK terms | B8 | not yet read |
| Recovery map colours and other design assets | B1, B4 | own work, no notice needed |
