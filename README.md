# Map Atlases Recut

A fork of [Map Atlases](https://github.com/MehVahdJukaar/mapatlases-neoforge) by
MehVahdJukaar and Pepperoni\_\_Jabroni\_\_, reworking the atlas into a **layered,
multi-resolution** map and building a travel system on top of it.

**Status: early.** The fork has been renamed and builds clean; feature work has not started.

- **Minecraft** 1.21.1 · **NeoForge** 21.1.248
- **License** GPL-3, inherited from upstream

## What it aims to be

Three layers, each intended to stand alone without the ones above it:

1. **Cartography** — an atlas holding several scale layers at once, compositing fine detail
   over a cheap coarse base. Paper priced on vanilla's ladder, so detail is earned.
2. **Leyline** — Waystones as rings of inscribed stone, linked into a non-crossing network
   you learn rather than search, and boots that run the routes you have mapped.
3. **Charge** — leylines as energy, with sinks that pay out in world effects.

## Building

Requires **two JDKs**: 25 to run Gradle (upstream's build plugins demand it) and 21 as the
compile toolchain. Then:

```
./gradlew build
```

## Credit

Upstream did the hard part. This fork keeps their history, their licence, and their
copyright notices intact; everything it changes is listed in the commit log.
