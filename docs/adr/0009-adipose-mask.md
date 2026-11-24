# 0009 — The adipose layer as a raster mask

**Date:** 2026-11-24
**Phase:** B4 follow-up (live QA on the owner's phone)
**Status:** Accepted; supersedes `0008` item 4

The contract has not changed. `DESIGN.md` §9.1.1 still asks for a band that sits outside
every muscle, is thicker at the waist than at a forearm, and is never drawn under a
muscle. What changed is how the renderer produces it.

## What the phone showed

A profile with a recorded body-fat figure *and* neck, chest and waist tape (the normal
case for anyone who uses the Vitals module) drew the layer as a **filled gold figure** on
the back view, and the band arrived four to eight seconds after the card. The golden
screenshots had never covered a tape-scaled input, so the suite was green.

## The cause, measured

`AdiposeGeometry.layer` built the band with `android.graphics.Path` boolean operations.
Skia's `Path.op` **returns false** for this geometry and leaves the destination path
untouched: 12 of the 21 operations failed on the back view at 22 % body fat with
neck 38 / chest 100 / waist 90, and the first difference — rim minus silhouette — failed
already. Each failure silently kept the un-cut ring, so the band grew into the body. The
same call on the front view succeeded, which is why only the back looked wrong.

Flattening the paths to polygons first (`Path.approximate(0.5f)`) did not fix the
failures and made the build 100× slower (116 s front, 291 s back on the host), so the
path route was abandoned rather than patched.

## The decision

1. **The band is a bitmap, not a path.** `AdiposeMask` rasterises the muscles at a fixed
   height (720 px over a 1280-unit viewBox, about two mask pixels per drawn pixel), then
   derives three fields from it:
   - a chamfer distance transform to the nearest muscle, carrying **which region** seeded
     it, so the thickness can differ by region;
   - the body's **interior**, from a box-blurred copy of the muscle mask cut at 62 %: a
     blur bridges every seam narrower than its radius and a cut below half coverage keeps
     the band a pixel under each muscle, so no notch appears where a muscle's own path
     bulges past the smoothed silhouette;
   - a **thickness field**, the nearest region's thickness blurred across boundaries, so
     the band steps from arm to waist over a few pixels instead of jumping at one edge.
   A band pixel is one outside the interior, further than one pixel from a muscle and no
   further from one than the field says, with a one-pixel alpha ramp for the edge.
2. **Bounds and cost.** 720 px, a six-entry LRU per (view, quantised thickness), built on
   `Dispatchers.Default` and cached across rotation. Measured cold on the owner's phone in
   a debug build: **371 ms front, 479 ms back**, against 4–8 s before; a release build is
   faster. The mask is never built for a mini map.
3. **The exit test is a raster test.** `BodyGeometryTest` builds the mask for all four
   views at both a plain and a tape-scaled layer, asserts the band exists, asserts it
   covers no muscle pixel (drawn separately, one mask pixel of tolerance), and asserts it
   is a ring: the middle of the figure at three heights is not band. Those four
   assertions fail on the old path output.
4. **The goldens were re-recorded** (174 unchanged in count) with the new rendering, in
   dark, light, 200 % text and RTL.

## What this costs

- The band's edge is rasterised, so it is soft by one mask pixel (about 0.4 dp on screen)
  rather than vector-crisp. At 720 px it reads as a drawn layer, not as a blur.
- The thickness blend means a region boundary tapers over about 24 viewBox units instead
  of stepping. That is closer to anatomy than the step was.
- `synchronousLayers` remains as the test-only switch that builds the mask inline.

## Rejected

- **Patching the path code** (retry after failure, chunked unions, pre-simplified paths):
  the failure is in Skia's operator, not in the call order, and every retry is seconds.
- **A per-region stroke drawn separately and clipped:** needs the same union and the same
  operators to cut against the muscles.
- **A bigger seam-closing radius in the path version:** it hid the leak at 35 % body fat
  and left it at 22 % with tape, which is what the goldens had been showing.
