# Handoff

State of Map Atlases Recut as of 2026-09-01, written for whoever picks this up next.
Read this, then `BUILD.md` for the ordered plan, then `DESIGN.md` for reasoning.

---

## Where everything is

| | |
|---|---|
| **This repo** | `~/workingdir/mapatlases-recut` — the operator's project, branch `1.21.1` |
| **Upstream, pristine** | `~/workingdir/gits/mapatlases-neoforge` — for diffing and cutting PR branches |
| **GitHub** | `NotUserFriendly/mapatlases-recut`, public, **not a GitHub fork** (deliberate) |
| **Test instance** | `/home/locad/snap/prismlauncher-alpo/common/instances/1.21.1 Test Platform` |
| **Toolchain** | `~/mc-build-sandbox` — `rm -rf` removes every trace |

`~/workingdir/gits` is **other people's** repos. Direct subdirectories of `~/workingdir` are the
operator's own. A fork they maintain belongs in the latter.

## Working the code

```
./setup-jdk.sh          # two JDKs: 25 runs Gradle, 21 is the compile toolchain
source ~/mc-build-sandbox/env.sh
./gradlew build
./verify-boot.sh        # headless server boot -- mixins only fail at RUNTIME
./deploy-test.sh        # build + copy into the Prism instance (--no-build to skip)
```

**Two JDKs is not optional.** Upstream's build plugins (`com.possible-triangle:*`) publish
metadata demanding `org.gradle.jvm.version >= 25`, while Minecraft 1.21.1 targets Java 21.

**Run `verify-boot.sh` after touching anything under `mixin/`.** A green compile is not evidence
a mixin applied.

**Never add Claude attribution to commits.** No `Co-Authored-By`, no generated-with footers.
The operator does not want their projects surfacing in AI-generated repo lists.

## Conventions

- Config we add lives under a **`[recut]`** section in both config files, not scattered through
  vanilla-named ones.
- **Probes are encouraged.** Add logging freely to answer a question, mark it `// PROBE`, and
  remove it once answered. Minecraft exposes little; measuring beats reasoning.
- Commit messages are plain prose explaining *why*, including what was tried and rejected.

---

## What is done

**Pillar I, sections 2.1 to 2.3.** Three upstream PRs (`#297` and `#298` merged unchanged,
`#299` open), three stacking-identity fixes, scan throttling, and the layered atlas: multi-scale
`MapCollection`, automatic coarse base layer, compositing renderer, per-scale draw toggles,
zoom-selected separators with a dark underlay for empty cells.

**Gate 1 passed.** Crossing already-painted ground avoids 65 to 90 percent of scans; parked
drops from 20 scans per 10s to 9.

## What is unresolved, and it matters

**The layered atlas does not deliver its headline promise.** The premise was a coarse base
giving a wide rough overview. Measurement says otherwise:

- A scan reaches a fixed **256 blocks at every scale**, so a 2048-block coarse cell fills no
  faster per unit area than a 128-block fine one. Raising `map_updates_per_tick` fills the same
  footprint faster, not wider. *Rate and reach are different knobs; do not confuse them.*
- Reach is then capped by **loaded chunks**. At the default render distance of 12 the scan
  already reaches as far as chunks load, so the range multiplier saturates around 1.5x.
- Generating terrain ourselves is dead: `ChunkStatus.SURFACE` costs ~50ms a chunk, a whole tick,
  and `ServerChunkCache.getChunk` bounces to the main thread. Reading *already generated* chunks
  is 1.2ms and viable, but at default settings there is almost nothing to find.

**So layers are currently a storage win** (a 6144² world needs 2304 scale-0 cells against
`max_map_count` of 512, or 9 coarse ones) **and not a vision win.** Say so plainly rather than
repeating the original claim.

**The live proposal is Distant Horizons as an optional dependency** (§5.6b). Its API was
verified against the real jar and is better than expected: LOD queries at a chosen detail level,
per-column reads, a soft cache, and a **raycast primitive** T3.2a can be built on. The friction is that DH gives block state wrappers, not map colours, so a serial
string to `BlockState` lookup with a cache is needed. The open question is sidedness: DH is a
renderer and its data is client side, which is why §5.6a (cheap layer draws, vanilla layer
records) unparks alongside it.

## Immediate loose ends

- **`ChunkGenProbe` and its `ServerStartedEvent` hook are still in.** Delete both; they answered
  their question. Config `measure_chunk_generation` goes with them.
- **D32** — crafted scale was removed from the data model but `MapAtlasCreateRecipe` still
  derives scale from the crafted-in map, and the cartography table still refuses differing
  scales.
- **D31** — decide whether the atlas stores paper at all before starting the paper economy.
  Drawing from inventory deletes `EMPTY_MAPS`, which makes a whole bug class impossible rather
  than fixed. Everything in 2.4 changes shape depending on the answer.

## How the operator works, and what they are right about

They test in game and report precisely. Several times they identified a cause faster than
reasoning did, and the pattern is consistent: **when something looks wrong on screen, check what
is drawn over it before theorising about the data.**

Three specific corrections worth inheriting:

- A pale bar was chased through four wrong explanations, all about map *content*. It was the
  coarse layer's own separator sprite, scaled by the layer transform.
- A skip rate of 0 percent looked like a bug in the check; it was an all-or-nothing metric
  hiding partial progress.
- A performance ceiling was blamed on wasted work for hours. The default `map_updates_per_tick`
  was 1. **Read the configuration before optimising the code it throttles.**

Also from them, and worth keeping: **this is a mapping mod, not a map mod** — the verb, not the
noun. If a change improves the act of mapping it is in scope, and cost is then an engineering
problem to measure rather than grounds for dismissal.
