# Map Atlases — Recut

Design document for a fork of [Map Atlases](https://github.com/MehVahdJukaar/mapatlases-neoforge).

- **Written:** 2026-08-29
- **Status:** design only. No code written, no build environment yet.
- **Upstream baseline:** `MehVahdJukaar/mapatlases-neoforge` @ `dbc5e24` (branch `1.21.1`),
  cloned to `~/workingdir/gits/mapatlases-neoforge`.

Sources folded in: `notes/Excerpt.md` (prior design work from another machine, against
commit `b9ca8cd` — five commits behind HEAD) and `notes/QuickNotes` (new items).

Everything in **§8 Verified upstream facts** was read out of the cloned source
today. Everything else is proposal. Where the Excerpt and the code disagree, the
code wins and the disagreement is called out.

---

## 1. Upstream baseline

| | |
|---|---|
| Mod id / version | `map_atlases` / `1.21-6.7.1` |
| Minecraft | 1.21.1 |
| Loaders | NeoForge `21.1.248` **and** Fabric `0.19.3` — multiloader (`common/`, `neoforge/`, `fabric/`) |
| | *The repo is named `-neoforge`, but `settings.gradle.kts` is `include("common", "fabric", "neoforge")` and `gradle.properties` carries two CurseForge and two Modrinth project ids. One repo, two published mods.* |
| **License** | **GPL-3** |
| Size | 125 `.java` files, ~10.9k LOC; 109 files in `common/` |
| Hard dep | Moonlight `1.21.1-3.3.4` (map decorations, config, platform, networking) |
| Soft deps | Supplementaries, Twilight Forest, Curios, Trinkets, Xaero's Minimap |
| Package root | `pepjebs.mapatlases` (note: `maven_group` says `pebjebs` — upstream typo) |

Two things follow immediately.

**GPL-3 means the fork is GPL-3.** Not a problem — but it forecloses ever going
closed, and it means any code lifted from elsewhere has to be license-compatible.
Upstream itself carries a file forked from AntiqueAtlas under GPL-3
(`CartographyTableMenuMixin`), so the lineage is already copyleft.

**10.9k LOC is small enough to own.** This is the single most important fact for
the fork decision. A layered-atlas rewrite touches maybe 1.5k of those lines. This
is not a codebase that will outrun us.

---

## 2. Fork posture

**Settled: hard fork, new mod id, `upstream` kept as a git remote.**

**Identity — `Map Atlases Recut`.** Chosen so it sorts directly beside Map Atlases in
mod lists. Mod id `map_atlases_recut`, which also sorts adjacent as a namespace.

**Package root: `notuserfriendly.mapatlasesrecut`.** This is not cosmetic. T3.6
requires *both mods installed simultaneously*, and the fork copies every class — so
`pepjebs.mapatlases.map_collection.MapCollection` would otherwise exist twice on one
classloader and collide. Set it before the first commit; renaming later is whole-tree
churn that makes every upstream cherry-pick conflict. `maven_group` in
`gradle.properties` moves with it.

*(Matches upstream's style — `pepjebs.mapatlases` has no TLD prefix either. If the
project ever publishes to Maven Central, the conventional form would be
`io.github.notuserfriendly`; nothing about Minecraft modding requires it.)*

Reasoning:

- The headline feature (§5, layered multi-resolution atlas) changes `MapCollection`'s
  central invariant — one scale per atlas. That is not a mixin and it is not a PR
  upstream would take. Once you're changing that, you are maintaining a fork whether
  you call it one or not.
- Everything in QuickNotes is UI and core work — the Atlas Cutter's screen, the Drawn
  Layer, minimap parity. The Excerpt explicitly ruled that out *as a mixin author*
  ("client-side surgery inside another mod's screen, the most breakage-prone kind of
  mixin"). As the fork, that objection evaporates. This is the main thing the fork buys.
- Keeping `upstream` as a remote costs nothing and lets us cherry-pick their fixes.
  They are actively committing (recent perf work on `MapItemMixin`, Curios 9.5.1).

**But split the small correctness fixes out and send them upstream as PRs anyway.**
Items T0.1 and T0.2 below are bugs in upstream that are worth fixing for everyone,
are independently valuable, and cost us nothing to contribute. `MapAtlasesMod.java`
carries the author's own TODO list including `//auto waystone marker` — some of what
we want, they want too.

**Loader scope — settled: NeoForge only.** Upstream is multiloader despite the repo
name; `common/` holds 109 of the 125 files and the loader dirs hold only platform
glue (9 files in `fabric/`, 7 in `neoforge/`). Dropping `fabric/` costs almost
nothing and removes the Fabric Loom half of the Gradle setup.

**One caveat that matters for §4 below:** upstream PRs must still build both loaders.
So keep the PR branches cut from `upstream/1.21.1` with Fabric intact, and strip
Fabric only on our own fork branch. Do the PRs first, then strip.

---

## 3. Sending the fixes upstream

T0.1–T0.3 (§5) are bugs in upstream worth fixing for everyone. Upstream merges
outside PRs — `#296` (perf) and `#210` are both merged contributions — and there is
no CONTRIBUTING file or CLA, so the process is ordinary: fork on GitHub, branch,
open a PR.

**Send three separate PRs, not one.** They differ in file, severity and reviewer
burden, and a duplication bug merges much faster on its own than bundled with a
refactor.

| PR | Content | Severity framing |
|---|---|---|
| A | `EmptyMaps` merge duplication (T0.2) | **Item duplication.** One-line-ish fix, obviously correct, merges fastest. Lead with this one. |
| B | Duplicate map entries (T0.1) | Log spam + silent map loss + `getCount()` inflation. |
| C | Atlas stacking (T0.3) | Quality-of-life; most likely to attract discussion. Send last. |

**What we need before any of them:**

1. **JDK 21 and a green build.** Non-negotiable — we cannot open a PR on code we have
   not compiled. This is the §9 blocker.
2. **Both loaders building.** `./gradlew build` must pass for `fabric` and `neoforge`.
   This is the reason not to strip Fabric yet.
3. **Branches cut from `upstream/1.21.1`**, not from our fork's main, so the diffs stay
   clean of fork changes.
4. **A written reproduction per PR.** For A: two atlases with 10 empty maps each into a
   cartography table → two atlases with 20 each out. For B: merge two atlases whose
   coverage overlaps → ERROR per collision in the log, maps silently dropped, and the
   tooltip count still counts them.
5. **Deepen the clone** — it is currently `--depth 50`. `git fetch --unshallow` before
   branching.

The Excerpt reports that B's fix was already written and ran in production on another
server for a live trial, so the design is proven even though the code is not on this
machine. It will need rewriting against `dbc5e24`.

GPL-3 both directions, so licensing is a non-issue.

---

## 4. What the atlas is competing against

QuickNotes frames the goal as *minimap parity*: "Other minimaps, that are 'free',
have a lot more features than this one, which is not free."

Having read the client config, **the gap is smaller than that framing assumes.**
Upstream already ships:

| Feature | Status upstream |
|---|---|
| HUD minimap, rotating, follow-player | ✅ `MapAtlasesHUD`, `rotate_with_player`, `follow_player` |
| Coordinates + chunk coords + biome text | ✅ `coordinate_text`, `chunk_coordinate_text`, `biome_text` |
| Cardinal directions | ✅ `cardinal_directions` |
| Fullscreen map, smooth pan/zoom | ✅ `AtlasOverviewScreen`, `smooth_panning`, `smooth_zooming` |
| Entity/mob radar | ✅ `EntityRadar`, `radar_radius` (default 64) |
| Waypoints/pins on map | ✅ Moonlight `PinMarker`, pin keybind |
| Cave/slice mapping | ⚠️ `Slice` exists, but vertical slices need **Supplementaries** |
| Import Xaero waypoints | ✅ `XaeroMinimapCompat.parseXaeroWaypoints` |
| Death waypoint | ❌ |
| In-world waypoint beam/indicator | ❌ → **Leyline Sensor** (§5) |
| Route/path drawing | ❌ → **Drawn Layer** (§5) |
| Waypoint teleport | ⚠️ `creative_teleport` only |
| Share map with another player | ⚠️ physically, by handing over the atlas |

So parity is roughly four features, two of which QuickNotes already names. The real
competitive weakness is not the feature list — it is that **a free minimap reveals
the world instantly and the atlas charges paper per cell and fills slowly.** That is
the thing §5 is actually about.

---

## 5. The spine: layered multi-resolution atlas

This is the reason to fork. Everything else is additive.

**The proposal.** An atlas holds several scale layers at once. It auto-writes a
coarse base layer cheaply, and composites finer layers on top wherever they exist.
Zooming out shows the coarse layer; zooming in reveals detail where you have walked.

### 5.1 Why the geometry cooperates

`MapGridKey.getBlockWidthFromScale(scale)` is `128 * (1 << scale)`, and
`MapType.getCenter(px, pz, width)` snaps a position to that width. So cells are
128 / 256 / 512 / 1024 / 2048 blocks wide, and **each coarse cell is tiled exactly
by four of the next finer ones.** No fractional offsets, no seam blending, no
resampling. Compositing is placing quads. This is the strongest argument for the
design and it is confirmed in the source.

### 5.2 How much has to change — revised down from the Excerpt

The Excerpt called this "a core rewrite of the class" and said the renderer was
"the larger half." Reading the code, **both are overstated.**

**`MapGridKey` already carries `gridWidth` as part of its identity** — it is
`(mapX, mapZ, slice, gridWidth)`, and `equals`/`hashCode` include the width. So
`MapCollection.maps`, a `HashMap<MapGridKey, MapDataHolder>`, can *already* hold
multiple scales without key collision. The blockers are narrower than "rewrite":

1. `populateInDataStructure` hard-rejects `d.scale != scale` (one condition).
2. `scale` is a single `byte` set from the first map ever added (one field).
3. `select(int x, int z, Slice)` calls `MapGridKey.at(scale, ...)` — it needs to
   become `select(x, z, slice, scale)` plus a `selectBest(x, z, slice)` that walks
   from finest to coarsest and returns the first hit.

That is a real change to the class's contract, but it is a change of maybe 60 lines
against a 333-line file, not a rewrite.

**The renderer is likewise better shaped than expected.** `AbstractAtlasDisplay`
already routes every tile lookup through one abstract method:

```java
@Nullable public abstract MapDataHolder getMapWithCenter(int centerX, int centerZ);
```

and `drawAtlas` draws a grid of fixed 128px quads whose world size comes from a
single `mapBlocksSize` field. Compositing = **run the existing loop once per layer**,
coarsest first, each pass with its own `mapBlocksSize` and its own poseStack scale.
`drawMap` itself is already scale-agnostic. The work is in `drawAtlas`'s loop
structure and in per-layer scissoring, not in the quad drawing.

**Honest remaining cost.** Three places still assume one grid and need touching:
`MapAtlasesHUD`, `MapWidget` (pan/zoom/hover→cell math), and the lectern renderer.
Plus `MapsNeighborhood.around(player, scale, slice)` becomes per-layer. Call it a
week of focused work, not a month.

### 5.3 The problem the layers have to solve: fill speed

The layered design's premise is *quick rough pass, slow sharp pass*. **As things
stand that does not work**, and this is the crux.

`MapItem.update` computes its pixel scan radius as `k = 128 / (1 << scale)` — the
radius shrinks by exactly the factor that per-pixel sampling grows. Every update
scans about a 256×256 block region for ~65k column samples, **at every scale**.
Vanilla normalised this deliberately.

Consequences, both true:

- **Scale does not make updates more expensive.** (The Excerpt corrects an earlier
  draft that claimed 256×; the corrected version is right.)
- **A coarse map therefore reveals world no faster than a fine one.** A scale-4
  cell is 2048×2048 and one update paints 256×256 of it, so completing it takes
  ~64× more walking. Coarse maps are not fast, they are just fewer.

**The fix, and the hook already exists.** `MapItemMixin` already captures vanilla's
`range` local as `@Local(ordinal = 5) LocalIntRef` and multiplies it by the
`map_range_multiplier` config. Make that multiplier **a function of the map's scale**
instead of a flat value. The plumbing is written; the change is the expression.

**It is not free.** Work goes as `range²`, so 4× range is 16× the column samples.
The honest framing: *CPU cost tracks world area revealed, not detail.* This buys
discovery speed with tick time rather than with paper.

**The saving that pays for it.** Vanilla samples `(1<<scale)²` columns per pixel and
takes the majority colour — 256 columns per pixel at scale 4, to produce one pixel of
a deliberately coarse base layer. Capping that at ~16 samples is a 16× saving, which
buys 4× range at unchanged cost. The colour gets noisier, which for a base layer is
invisible. More invasive than the range tweak — it means redirecting the inner
sampling loop rather than one local — but **it is what turns the layered design from
"same speed, less detail" into "fast and rough over slow and sharp."**

→ Profile before committing. **Open question Q2.**

### 5.4 Cheaper intermediate, if §5.3 proves too costly

**Multi-scale *switching* instead of compositing.** The atlas holds several scale
layers and zooming *swaps* which single grid is stitched, rather than overlaying
them. The renderer stays a uniform grid — most of the saving — and you still get one
atlas covering everything at several detail levels. Loses the fine-inset-on-coarse
look. Roughly a third of the work. Worth keeping in the pocket as a fallback.

### 5.5 What layers fix for free

- **Explorer maps stop being a special case.** Shipwreck/buried-treasure maps are
  centred on their structure at arbitrary coordinates and can never align to the
  grid, so today they cannot be stitched in at all. Once the renderer composites
  free-form in world space, an arbitrarily-centred scale-1 treasure map is just
  another quad. The grid-alignment complaint dissolves as a side effect.
- **The `max_map_count` ceiling stops biting.** Default is 512. A scale-0 atlas
  covering a 6144² pregen needs 2304 cells — impossible at any price. Scale 2 needs
  144 and fits comfortably.
- **The Drawn Layer (§5) has somewhere to live.** It is just another layer.

---

### 5.6 Can we cheat for performance? — the Xaero question

*Operator's question: minimaps that don't use vanilla maps are very performant and
draw insanely high detail. Would queuing the map writes, while faking it to the
player, help?*

Short answer: **queuing alone, no. But the question points at the right problem, and
there are two real wins behind it — one of which is bigger than the one proposed.**

**Why Xaero and JourneyMap are fast.** Not because they're client-side per se. Because
they **scan each chunk once, when it loads or changes**, and cache the result to disk.
Vanilla does the opposite: `MapItem.update` **rescans the same region on every
update** — 65,536 column samples, every time, over terrain that has not changed since
the last pass. (Derivation: pixels scanned is `(256 >> scale)²` and columns per pixel
is `(1 << scale)²`; the product is `256²` at every scale. §5.3.) That is why
`map_updates_per_tick` defaults to **1**.

So the gap is *rescan vs. scan-on-change*, and that reframes all three options:

**Option 1 — Queue the writes. Doesn't work.** Deferring work does not reduce it. If
the player explores faster than the queue drains it grows without bound and the "fake"
never resolves into real data. You would have traded a throughput problem for a
latency problem and kept the throughput problem. Reject.

**Option 2 — Dirty-tracking instead of rescanning. The biggest win, and it is
server-side.** Track which chunks have changed (block updates) and which are newly
loaded, and only rescan the map pixels covering those. A player standing still, or
walking back across terrain they already mapped, costs approximately **zero** instead
of 65k samples per update. This is the change that closes most of the distance to
Xaero, and it costs us none of the properties we care about — the server stays
authoritative, map data stays vanilla, a client without the mod still works.

Upstream already gestures at this: `WeightedUpdateScheduler` deprioritises maps with
no blank pixels left. But that is whole-map granularity. Per-chunk granularity is the
real version. **Do this before anything more exotic.**

**Option 3 — Client-side speculative fill. Real, and it is not actually a fake.** The
client already has every chunk within render distance; computing map colours locally
from those chunks produces *the same bytes the server would*, because vanilla's
colour derivation is deterministic given chunk data. So the client can paint the tile
instantly and the server can confirm at its own pace.

Two things make this safer than it sounds: it leaks nothing (you only draw chunks the
client was already sent — it is not an x-ray vector), and it is self-correcting
(server data overwrites speculation on arrival).

The genuine question is whether speculation ever becomes *ownership*. Either:
- **Display-only** — speculation is never persisted, discarded on relog. Honest, but
  confusing: you saw it and don't have it.
- **Client-computed, server-committed** — the client uploads tiles and the server
  trusts them. Cheat ceiling is low (map pixels are cosmetic, and the client can only
  derive them from chunks it holds), but a modified client could upload tiles for
  regions it never visited. Gate behind a config flag and a sanity check against the
  player's recent position history.

**The tension nobody can design away.** Every one of these wins moves us further from
`MapItemSavedData`, and that class *is* the mod's identity — "a vanilla-friendly
minimap using vanilla Maps." It is what makes maps shareable, frameable, lectern-able,
and readable by an unmodded client. Options 2 and 3 preserve it. A custom tile format
does not.

**On "insanely high levels of detail" specifically:** this is the one thing that cannot
be bought cheaply. `MapItemSavedData` is structurally **128×128 bytes on a fixed
palette**. There is no more detail to extract from it — not with faster updates, not
with caching. Higher resolution requires our own tile format, which means the data no
longer lives in a vanilla map, which costs every interop property above.

**Which is precisely what §5's layers are for.** Layers get detail the vanilla-compatible
way — *more* 128×128 tiles at finer scales, rather than higher-resolution tiles. That
is the fork's answer to detail, and it is why the layered design and the Xaero-style
custom-tile design are alternatives rather than complements. **Pick one.** The
recommendation is layers, because it keeps vanilla interop, and Option 2 to make it
fast.

**Where a custom format *would* be complementary rather than alternative.** One
shape survives the objection: a client-side high-detail cache used as a **pure display
overlay**, never as storage. `MapItemSavedData` stays authoritative — it is what is
saved, shared, framed and lecterned. The cache only makes the same cells render
*sharper on this client*, and when it is absent (unmodded client, an atlas just handed
to you, a fresh install) the renderer falls back to the vanilla 128×128 with nothing
lost but crispness.

That is genuinely additive: no interop is surrendered, because the vanilla data never
stops being the source of truth. The costs are real but bounded — two render paths, a
cache-invalidation story, and the fact that your sharp view does not transfer when you
hand the atlas over. That last one is arguably correct fiction: you know the terrain,
the book is only a book.

**Not a phase-1 item.** But it is the answer to "only if it buys us something
complementary" — this is the version that does. Revisit after layers and dirty-tracking
are in and we can see what detail is actually missing.

**Revised recommendation for §5.3.** Order of attack on fill speed:
1. Dirty-chunk tracking (Option 2) — biggest win, no architectural cost.
2. Cap per-pixel sample density at coarse scales — 16× saving at scale 4, one mixin.
3. Scale-dependent `range` multiplier — the hook already exists.
4. Client-side speculation (Option 3) — only if 1–3 leave a visible gap.


---

## 6. Feature catalogue

Ordered by dependency, not by desirability. Tier 0 is upstreamable and independent;
Tier 2 is the fork's reason to exist; Tiers 3–4 sit on top of it.

### Tier 0 — correctness (small, independent, send upstream)

**T0.1 — Duplicate map entries.** `MapCollection.addAndAssigns` appends ids blindly
with no check for ids already held or grid cells already covered.
`populateInDataStructure` then logs each collision at ERROR and drops it. Merging two
atlases that overlap spams the log and silently loses maps. Worse: `size` is computed
from `ids`, not `maps`, so **duplicates inflate `getCount()`** — the tooltip lies and
`max_map_count` triggers early. (This last part the Excerpt does not mention.)

Fix: filter in `addAndAssigns`, return `this` when nothing survives; record duplicates
rather than logging each; repair on first initialize. Proven working in the Excerpt's
prior deployment.

**T0.2 — Merge duplicates empty maps.** `CartographyTableMenuMixin`, merge branch:

```java
emptyMaps.addAndAssigns(result, MapAtlasItem.getEmptyMaps(bottomItem).getAll());
result.grow(1);
```

It sums both atlases' empty maps into `result`, *then* grows the stack to 2 — so two
atlases with 10 empties each produce **two** atlases with 20 each. 20 in, 40 out.
Free duplication. Fix: split rather than double.

**T0.3 — Atlases refuse to stack.** `MAP_ATLAS` is `stacksTo(16)`, so stacking needs
every component equal. Two causes:

- `MapCollection.equals` compares `ids`, an `EnumMap<MapType, List<MapId>>`. **List
  equality is order-sensitive**, so two atlases covering identical territory don't
  stack unless the ids are in the same order. Sorting `ids` is safe once T0.1
  guarantees one entry per cell.
- **Components that carry state describing nothing.** *(Corrected 2026-09-01 after
  reading the code; the earlier `SELECTED_SLICES` explanation was wrong for the
  vanilla case, which is where the failure was originally observed.)*
  - `EmptyMaps.addAndAssigns` never drops a key, so an atlas that spent its last paper
    holds `{VANILLA: 0}` where a fresh one holds `{}`. The pity-count write in
    `maybeCreateNewMapEntry` does the same to *any* atlas holding no maps on the first
    tick a player carries it. **This is the vanilla-reachable cause.**
  - `MapCollection` kept a key whose list had been emptied, so `{VANILLA: []}` versus
    `{}`.
  - `SelectedSlices.removeAndAssigns` stored an empty component rather than removing
    it. Only reachable with Supplementaries or Twilight Forest, since
    `setSelectedSlice` refuses to store a `(VANILLA, no height)` slice at all — so the
    once-planned "strip degenerate entries behind an `isLoaded` guard" was a no-op in
    both directions and was dropped.

  All three are fixed by normalising identity, because
  **`PatchedDataComponentMap.set` deletes the patch entry outright once the value
  compares equal to the item's prototype.** No call site needed changing.

### Tier 1 — economy and ergonomics

**T1.1 — Paper ladder.** Today the atlas charges **1 paper per cell at any scale**
(when `accept_paper_for_empty_maps` is on — note it defaults to `false` upstream, so
this is a server choice). Vanilla charges 8 paper + 1 compass for an empty map, plus
8 per zoom step. So the atlas is 8–40× cheaper than mapping by hand, with no iron,
while also being automatic and stitched. Nobody would ever map manually.

Proposal: **adopt vanilla's paper ladder, drop the compass.** A compass to found an
atlas makes sense once; iron per map does not.

| scale | paper/cell | cell size | cells over a 6144² pregen | paper for full coverage |
|---|---|---|---|---|
| 0 | 8 | 128² | 2304 | 18,432 |
| 1 | 16 | 256² | 576 | 9,216 |
| 2 | 24 | 512² | 144 | 3,456 |
| 3 | 32 | 1024² | 36 | 1,152 |
| 4 | 40 | 2048² | 9 | **360** |

Coarse ends up ~51× cheaper per unit area than fine rather than 256×. Full detail
everywhere becomes a mega-project; a coarse world overview costs 360 paper — an
afternoon of sugar cane. This prices the §4 layers correctly: **the base layer is
nearly free, detail is earned.**

*Implementation note:* the empty-map pool must be counted in **paper**, not maps, or
scale pricing needs fractional map costs. `EmptyMaps` holds `Map<MapType, Integer>`;
reinterpreting that as paper units is a semantic change touching the tooltip, the
loan (T1.3) and the sheaf (T1.2).

**T1.2 — Paper Sheaf.** 9 paper → 1 sheaf, counts as 9 when fed to an atlas. Solves
carrying capacity, which with T1.1's numbers becomes a real problem (18,432 paper is
288 stacks). `isValidEmptyMapIngredient` and `getMapCountToAdd` hardcode `Items.PAPER`
and count `bottomItem.getCount()` 1:1 — there are **two** entry points and both need
it or the sheaf behaves inconsistently: the cartography table and `MapAtlasesAddRecipe`.

**T1.3 — Empty-map debt ("the loan").** Pinning into an unmapped cell may create that
map on credit, taking the empty count to −1 (or −8…−40 under T1.1's ladder: one map's
worth at this atlas's scale). Nothing else may borrow.

The debt enforces itself for free — `maybeCreateNewMapEntry` gates on `emptyCount > 0`,
so a negative balance already stops normal exploration until it is repaid. Two
obstacles, both in `EmptyMaps`:

- **Both `addAndAssigns` overloads clamp** with `Math.max(0, current + amount)`. That
  blocks going negative at all, and makes partial repayment *erase* the debt. Set the
  debt via `setAndAssign`, which does not clamp, and remove the clamp from the
  repayment path so paper sums honestly.
- **The clamp is also a laundering hole.** Merging two −1 atlases yields
  `Math.max(0, -1 + -1)` = 0 and the debt vanishes. Combining a pile of indebted
  atlases into one free map is the real abuse, not discarding them.

**Rule: an atlas carrying a debt cannot be merged or duplicated.** The cartography
table accepts only the add-empty-maps path until the balance is zero. Simpler than
reasoning about how debts combine, and easy to explain: pay it off first.

**T1.4 — Paper Block.** 8 paper sheaves + 1 string → a placeable, flammable block.
Breaking with any tool drops the component parts; breaking with shears drops 8×9 = 72
paper.

**It is also an atlas refuelling source**, not just storage — feeding a paper block to
an atlas (or to the Atlas Cutter, T3.1) credits 72 paper in one action. Under T1.1's
ladder that is 9 cells at scale 0 or 1.8 cells at scale 4, so it is a meaningful unit
of "top up before heading out" rather than a token. It also gives bulk paper a
placeable form, which matters when full scale-0 coverage costs 288 stacks.

Still the right first task once the build works: it touches registration, recipes,
loot tables and one item interaction, and risks nothing.

### Tier 2 — the layered atlas

See §5. This is the gate for T3.1 and T3.2.

### Tier 3 — new content

**T3.1 — Atlas Cutter (block).** Stores maps in bulk, outside any atlas.

- *Dump*: pull every map out of an atlas into the block's storage.
- *Load*: push storage back into an atlas, consuming supplied paper.
- The workflow it enables: explore, return to base, dump into the table; the next
  player supplies paper and pulls out a fully up-to-date atlas. **This is the mod's
  answer to shared-map-state in free minimaps** — a physical, in-world sync point.
- Individual maps can be withdrawn as items; identical maps stack in storage.
- **Dedup**: if map #2 and #302 are the same scale, type and cell, cut #302 — both the
  item and the entry — and fold it into #2. This is T0.1's logic applied as a
  deliberate player-facing action rather than a silent repair.
- Anything better homed here than at the vanilla cartography table moves here.
- **It is the universal paper sink.** Every paper-bearing item can be fed to it for
  credit, with byproducts returned rather than destroyed:

  | Input | Paper credited | Returned |
  |---|---|---|
  | Paper | 1 | — |
  | Paper Sheaf (T1.2) | 9 | — |
  | Paper Block (T1.4) | 72 | — |
  | Book | 3 | 1 leather |
  | Book and Quill | 3 | 1 leather |

  Implement as a **datapack-driven map of item → (paper value, byproducts)** so packs
  can add their own. Exclude written books and enchanted books — they carry content a
  player would not expect to lose.

*Why this is Tier 3:* the storage model must know about scales and layers, so it
wants §5 settled first. It also needs a screen — the first genuinely new UI in the
fork, and the piece the Excerpt would have refused to build as a mixin author.

**T3.2 — Dissolve an atlas. *Retained — it is the uninstall path.*** T3.1's "dump"
covers the in-game ergonomics, so dissolve is not needed as a convenience. It is
needed as an **exit**: if a player uninstalls the mod, every map they own is trapped
inside an item that will no longer exist. Dissolve converts an atlas back into plain
vanilla filled maps that survive the mod's removal.

That reframing changes its design constraints entirely. It does not have to be
elegant, fast, or convenient — it has to be **complete and lossless**. It may be
gated, slow, confirmation-heavy, or restricted to a cartography table. It must not
silently drop maps.

Pair it with T3.6: **T3.6 is the entrance, T3.2 is the exit.** Both exist so the
player's data is never held hostage by the mod's install state, and they should be
designed and tested together.

Shears are already the atlas's cutting tool (`PlatStuff.isShear`, extracting the
single selected map). Dissolve is the same gesture applied to everything.

Two known constraints: (a) **the output does not fit anywhere** — each map is a
distinct non-stacking item and a well-travelled atlas holds hundreds, ~nine double
chests, so this cannot be a crafting recipe with one output slot; (b) refuse while in
debt, consistent with T1.3.

Proposed guard: **two-stage shear.** Shearing an atlas that holds empty maps throws
out paper equal to the empties. Shearing one with no empties dissolves it. You cannot
destroy a working atlas by accident, because the first cut only refunds paper.

*(The Excerpt parked this asking whether dissolve solves a real problem or is a
workaround for clunky multi-atlas management. Settled 2026-08-29: neither. It solves
the uninstall problem, which nothing else does.)*

**T3.3 — Leyline Sensor (item).** While held, the nearest three defined waypoints
render in the world. Closes one of the four real parity gaps in §3, and is the
in-world half of the pin system that already exists on the map.

Note a hard constraint discovered in the Excerpt: **pins cannot go in blank space.**
A marker lives on one specific `MapItemSavedData`, and Moonlight's
`MLMapMarker#createDecorationFromMarker` returns null when the marker is more than
±64 blocks from that map's centre. There is no arbitrary-coordinate waypoint. The
Sensor reads existing pins, so it inherits this — it can only point at places you
have mapped. That is arguably correct for the mod's fiction.

**T3.4 — Drawn Layer.** A player-drawable layer even with the decoration layer, for
routes and annotations. Explicitly low priority in QuickNotes. Under §5 it is
structurally cheap — it is one more layer in a renderer that now composites layers —
but the *input* half (drawing tools, a stroke format, syncing strokes) is a real
feature on its own. Revisit after §5 lands.


**T3.5 — Leyline Atlas + Thousand League Boots.** *The mod's best idea, and the one
that names it.*

**Settled 2026-08-29: Leyline is an upgrade, not an enchantment.** An ordinary atlas
plus an upgrade item becomes a **Leyline Atlas** — a fixed, frozen, **single-scale,
unlayered** snapshot. It cannot be written to: no new cells, no absorption, no
merging. **Thousand League Boots** are an ordinary item that lets you *use* a Leyline
Atlas; they carry no Leyline enchantment either.

This split matters more than it looks. Because neither piece consumes the item's
enchantment slot, **both stay open to ordinary enchanting** — the boots take
Protection, Feather Falling, Mending; the atlas takes the paper-thrift enchant in T3.8,
and Soulbound later if a provider is picked. That was the whole reason to split it out, and it works.

**The fiction has converged.** Leylines are the routes you have mapped. The Leyline
Sensor (T3.3) shows waypoints in the world; the Leyline Atlas freezes a route network;
the Boots let you run it. The QuickNotes name and this feature turned out to be the
same idea arriving twice.

**Effects, while wearing the Boots inside the bounds of a carried Leyline Atlas:**

- speed, scaled by the atlas's map scale (below)
- step height up to **10**
- knockback immunity and fall-damage immunity, at full ramp
- passage **through leaves**

### Speed: detail buys velocity

The boots go faster over more detailed maps — they "know where to place the next
step." **Because the Leyline Atlas is scale-locked and unlayered, speed is uniform
across the whole atlas**, decided once at the moment you freeze it. No per-cell
variation, no accelerating and decelerating as you cross cell boundaries.

Five scales spread evenly between "elytra cruise" at the bottom and the practical
engine ceiling at the top:

| atlas scale | cell | paper/cell (T1.1) | speed |
|---|---|---|---|
| 4 | 2048² | 40 | elytra cruise (~33 m/s) |
| 3 | 1024² | 32 | ~45 m/s |
| 2 | 512² | 24 | ~57 m/s |
| 1 | 256² | 16 | ~69 m/s |
| 0 | 128² | 8 | practical ceiling (~80 m/s) |

*Numbers are a starting point for tuning, not a claim.* The elytra figure is
rocket-assisted cruise; the ceiling is bounded by chunk loading, not by any attribute.

**This self-balances, and that is the elegant part.** The fastest atlas is the one
whose cells are smallest, so covering ground with it costs the most walking and the
most paper. A scale-4 Leyline Atlas spans a continent slowly; a scale-0 one is fast
but local. Speed and coverage trade against each other automatically, in paper.

**And the paper-efficient shape for a fast atlas is a corridor** — which means the
mechanic organically produces **roads**. You map a route at high detail, freeze it,
and it becomes a highway 128 blocks wide that you must stay inside. That is a better
emergent behaviour than anything explicitly designed here, and it should be protected
in tuning.

**The balance point to watch is the *coarse* end, not the fine one.** 360 paper for
world-spanning elytra-speed ground travel is strong. Mitigations in order of
preference: (a) the upgrade item's own cost — see below; (b) lower the floor from
elytra cruise to something nearer sprint-jumping if it proves too strong. My earlier
Q3 concern about layers making the whole world a highway **is resolved by the atlas
being unlayered** — the operator's answer is both simpler and safer than the
per-cell scheme I proposed.

### Hunger debt: pay on arrival, not as you go

*Operator's mechanic, and the best structural idea in the feature.* Rather than
draining food continuously, travel accrues a **hunger debt counter** — more per metre
on a coarse atlas — and the bill lands **when you decelerate**, as the vanilla Hunger
effect with duration and strength scaled off the debt accrued. **The Boots require the
player to not have the Hunger effect**, so paying the bill locks you out until it
clears.

**Why this is better than continuous drain.** It removes the "pause and eat" beat
mid-journey, and replaces it with a commitment decision made *before* setting off. You
can over-commit and cause yourself real problems. Most importantly it **rewards long
straight shots and punishes bursts and jumps**, because every stop-start settles a debt
and triggers a lockout — so hopping the boots on and off for short bursts is the worst
possible way to use them, and a single long committed run is the best.

That is a rhythm no other movement item in the game has, and it falls out of two rules.

**Debt accrual, per metre, as a multiple of sprinting's exhaustion:**

| atlas scale | speed | debt per metre |
|---|---|---|
| 4 | elytra cruise | **4.0×** |
| 3 | ~45 m/s | 3.0× |
| 2 | ~57 m/s | 2.0× |
| 1 | ~69 m/s | 1.4× |
| 0 | ceiling | **0.9×** — a fine-atlas run costs slightly less than sprinting it |

*Starting points.* Vanilla sprinting is 0.1 exhaustion/metre and 4.0 exhaustion drains
one point, so a full bar plus saturation (~160 exhaustion) is about **1600 m
sprinting**. At 4.0× a coarse run banks that much debt in **~400 m** — twelve seconds
of travel, then a full food bar to settle. Verify the 0.1 figure against decompiled
source; it is from memory.

### Mapping debt onto the effect — calibrated from measurement

**Settled: fixed 30-second duration, amplifier carries the debt.** This reverses my
earlier "split the dials" proposal, and for a good reason — see the milk problem below.

The operator measured the vanilla Hunger effect in-game with a saturation-readout mod
(table at the end of `notes/QuickNotes`, reproduced in §8). Fitting it:

```
Sat points drained  =  0.013 × Str × Time(seconds)
```

The fit is essentially exact across the whole measured range — predicted vs. observed
at 10 s: Str 40 → 5.20 / 5.25, Str 75 → 9.74 / 9.50, Str 100 → 12.99 / 13.00. *(Str is
the amplifier argument as passed to `/effect give`, not the display level.)*

**So over a 30-second effect:**

| Str | points drained |
|---|---|
| 10 | 3.9 |
| 25 | 9.7 |
| 50 | 19.5 |
| 75 | 29.2 |
| **103** | **39.0 — a full bar plus full saturation** |

The operator's instinct of "hunger 80 or 100" lands almost exactly on a full drain.

**Calibration.** Take debt as `Σ (metres × scale multiplier)` and convert with:

```
Str = 0.064 × debt        (cap at ~103; beyond that there is nothing left to take)
```

Anchored so a scale-4 run of 400 m costs everything. The whole ladder then falls out:

| scale | speed | debt ×/m | distance to full drain | flight time | cost of 1600 m |
|---|---|---|---|---|---|
| 4 | 33 m/s | 4.0 | **400 m** | 12 s | 160 pts |
| 3 | 45 m/s | 3.0 | 533 m | 12 s | 120 pts |
| 2 | 57 m/s | 2.0 | 800 m | 14 s | 80 pts |
| 1 | 69 m/s | 1.4 | 1143 m | 17 s | 56 pts |
| 0 | 80 m/s | 0.9 | **1778 m** | 22 s | **36 pts** |

*(Sprinting 1600 m costs 40 points. Scale 0 costs 36 — "slightly better than
sprinting," exactly as specified. The model is self-consistent with the T3.5 ladder by
construction.)*

**A framing worth keeping: detail buys distance per food bar, not airtime.** Because
speed and debt rate climb together, the *time* you get per bar is nearly constant
across scales — 12 s at scale 4, 22 s at scale 0. What changes is how far that carries
you: 400 m versus 1778 m. A fine atlas isn't a longer flight, it's a further one.

### The milk problem — settled: split payment, short blast

**Milk is purely a reaction-time test, and a 30-second effect fails that test.** Even a
slow player gets a bucket out in two seconds and negates 93% of it. Severity does not
help — Str only changes what an *unprepared* player suffers, never what a prepared one
pays. Two changes fix it together.

**1. A flat, unavoidable hit at deceleration.** `flat = min(50% of debt, 10 points)`,
applied as immediate exhaustion, before any effect is applied. Milk cannot touch it.

**The ceiling is what makes this reward long runs:**

| debt | flat hit | as % of debt | left to the effect |
|---|---|---|---|
| 4 | 2 | **50%** | 2 |
| 10 | 5 | **50%** | 5 |
| 20 | 10 | 50% | 10 |
| 30 | 10 | 33% | 20 |
| 40 | 10 | **25%** | 30 |

Below a debt of 20 the flat hit is half of everything you owe; above it the fraction
falls away. So a burst costs 5 unavoidable points for a hop, and a long haul costs 10
unavoidable points for four times the distance — **the long run is twice as efficient
per metre in the portion milk cannot save you from.** The rule the operator wanted,
achieved with one clamp.

It also does what the operator described at both ends of the food bar: a well-fed
player watches the bar start moving the instant they stop, and **a player already
running half-starved takes starvation damage immediately**, because the flat hit lands
whether or not there is food left to absorb it.

**2. A short, distressing blast rather than a long bleed.** Duration governs how much
of the *remainder* a fast hand can escape:

| duration | milk at 0 s | at 1 s | at 2 s | at 5 s | no milk |
|---|---|---|---|---|---|
| 30 s | 25% | 28% | 30% | 38% | 100% |
| 10 s | 25% | 32% | 40% | 62% | 100% |
| **5 s** | 25% | **33%** | **42%** | 66% | 100% |

Five seconds roughly doubles what a fast reaction costs compared to thirty, and the
floor never drops below the flat hit's 25%.

**The amplifier cap, and the fallback — settled.** Amplifier can be set arbitrarily
high programmatically, but **gains no additional effect past 255**, so asking for Str
462 just silently becomes Str 255 and under-delivers. *(Operator's recollection;
confirm once the build works, but design for it either way since the fallback is
correct regardless.)*

**Conserve the product, not the duration.** The required delivery is `Str × Time =
remainder / 0.013`. Target 5 seconds; if that needs more than Str 255, clamp Str and
**ratio the time up to compensate**:

| debt | flat | remainder | Str | duration | paid, milk at 1 s | at 2 s |
|---|---|---|---|---|---|---|
| 4 | 2 | 2.0 | 31 | 5.0 s | 2.4 (60%) | 2.8 (70%) |
| 10 | 5 | 5.0 | 77 | 5.0 s | 6.0 (60%) | 7.0 (70%) |
| 20 | 10 | 10.0 | 154 | 5.0 s | 12.0 (60%) | 14.0 (70%) |
| 27 | 10 | 17.0 | 255 | 5.1 s | 13.3 (49%) | 16.6 (62%) |
| 30 | 10 | 20.0 | 255 | 6.0 s | 13.3 (44%) | 16.6 (55%) |
| 40 | 10 | 30.0 | 255 | **9.1 s** | 13.3 (**33%**) | 16.6 (42%) |

Nothing is lost to truncation, and **the long haul is rewarded a second time**: a
1-second reaction escapes 80% of a small debt's remainder but 89% of a maximal one,
because the effect is stretched thinner. A prepared long-haul player pays 33% of a
40-point debt where a burst costs 60% of a 10-point one. The flat-hit ceiling and the
time fallback push the same direction, which is the direction the whole feature is
aimed.

*(This supersedes the truncation proposal — it was the worse answer to the same
problem.)*

### Deceleration, defined so mods can't break it

*Operator: deceleration is "back at normal speeds," and we must account for mods that
add walk-faster effects.*

**Do not measure absolute velocity.** Speed potions, Depth Strider, Soul Speed and any
third-party movement enchant would all shift the threshold and either strand the debt
or settle it early.

**Measure our own ramp instead.** T3.5 already tracks a ramp tier as internal state;
deceleration is simply *that tier reaching zero*. The Boots' own contribution is the
only thing consulted, so the check is immune to whatever else is modifying the player's
speed. One mechanic serving both ends of the feature, and mod-proof for free.

This also gives the operator's desired feel: **the ramp unwinds gradually**, so you can
stutter, or shed just enough speed to dodge an obstacle, dropping a tier or two without
settling. Only a sustained stop takes the tier to zero and calls in the debt.

### Impairment: the Boots refuse unclean passengers

The Boots require the player to be free of **Hunger** — that is what creates the
post-run lockout. The operator is tempted to extend this to **Poison**, and it is worth
doing: it reads as the Boots rejecting someone taking poor care of themselves, and it
reinforces the milk-in-the-kit pattern above, since milk cures both.

**Implement as a datapack tag of blocking effects**, not a hardcoded list, so packs can
extend it.

**Nausea is ruled out, on two independent grounds.** First and sufficient: it makes a
meaningful share of players physically unwell, and no mechanic is worth that. Second,
and a rule worth generalising — **Nausea's impact is a client-side accessibility
setting.** A player who turns down Distortion Effects would get the same boots without
the drawback, which makes the cheap tier as good as the upgraded one for anyone who
happens to have that slider down.

> **Design rule: never gate a mechanic on an effect the player can disable
> client-side.** Check every candidate against it. Darkness is suspect for the same
> reason; Blindness, Poison, Wither and fire are not.

*(My earlier suggestion of Nausea was wrong on both counts and is withdrawn.)*

*Note the incidental consequence:* eating rotten flesh, or a cave-spider bite, grounds
you until it clears or you drink milk. Flavourful rather than broken, but document it
or it will be reported as a bug.

### Boot tiers: elemental drawbacks, removed by upgrade

*Operator's idea, and it resolves the cost-split problem (D16) as a side effect.* The
list of candidate blocking effects — Wither, Poison, Blindness, fire — reads less like
a lockout list and more like a set of **variants**.

**The structure:**

- **Tier 1 — elemental boots.** Cheap to make. On deceleration they inflict the hunger
  debt **plus a secondary effect**, one per variant.
- **Tier 2 — Thousand League Boots.** A netherite smithing upgrade, exactly as armour
  and tools work. Strips the secondary effect, leaving only the hunger debt.

**Why this is better than a flat cost gate.** The drawback is not a tax, it is a
**build choice**: each variant asks you to bring the counter for its element. Fire
Resistance for the fire variant, milk for poison, a plan for blindness. You pick the
drawback you are already equipped to answer, and the netherite upgrade is the moment you
stop having to. That is a real progression rather than a price tag.

**It compounds with the blocking-effect rule**, and this is the neat part: the
secondary effect is *also* on the blocking tag, so **cheap boots lock themselves out
for longer.** The Tier-1 cooldown is hunger plus the element; the Tier-2 cooldown is
hunger alone. No new rule needed — the two mechanics already written produce the tier
difference on their own.

### The secondary effects are too weak — the fix is time, not damage

*Operator is right, and the diagnosis matters more than it first looks.* As sketched,
Tier 1 barely differs from Tier 2: a full-health player survives the wither, milk
cancels everything, and only an already-starving player is in real danger. If the
drawback is that weak, the netherite upgrade has nothing to buy.

**Verified behaviour of the candidates:**

| Element | Can it kill? | Counter |
|---|---|---|
| Poison | **Never** — floors at 1 HP on every difficulty | Milk |
| Wither | Yes, on any difficulty, and it blocks regeneration | Milk |
| Fire | Yes | Fire Resistance, **or ending the run in water** |
| Starvation (from the hunger hit) | **Difficulty-dependent** — Easy stops at 10 HP, Normal at 1 HP, **Hard kills** | Food |

So the only genuinely lethal combination is wither-or-fire landing on an over-committed
player who is also out of food on Hard. That is a narrow enough case to be a good
consequence rather than a design.

**The fix: the secondary effect's cost is time, not HP.** It is on the blocking tag, so
its *duration* is a lockout — you cannot set off again until it clears. Make that
duration **scale with the debt**, and the gap between tiers becomes large and legible:

- **Tier 2** after a maximal run: ~9 seconds of Hunger, then go.
- **Tier 1** after the same run: ~9 seconds of Hunger *plus* a wither or burn measured
  in **tens of seconds**, all of it unable-to-travel time.

**And that makes Tier 1's real running cost a bucket of milk per trip.** Milk clears
both, so a prepared Tier-1 player trades a consumable for the lockout — a genuine
ongoing cost in buckets, cows and an inventory slot. Tier 2 makes milk optional rather
than mandatory. That is precisely the "cheap boots pay per trip, the upgrade ends it"
structure D3f was aiming for, arriving as a consumable cost instead of a damage number.

*Note the honest consequence:* a Tier-1 player who always carries milk plays close to
Tier 2. That is fine — they are paying for it every single run, and forgetting once is
expensive.

### Water walking at full ramp

*Operator's addition, and it earns its place by creating risk rather than removing it.*

**At full ramp the player runs on water. Below full ramp they sink.** You cannot start a
run from water, so this is never a way to leave a boat — it is only ever a way to
*continue*.

- Hit an ocean at speed and it is a highway.
- Stop mid-ocean and you are **swimming, unable to ramp, with no way out but swimming.**

That is real commitment: crossing water means not stopping, and the hunger debt is
building the whole way. It also interacts with the bounds check — you need mapped ocean
cells to cross at all, which at 8 paper per 128-block cell is punishing and at coarse
scale is cheap. **Ocean highways favour coarse atlases**, which gives the cheap end of
the ladder something it is uniquely good at.

**The best interaction is with the fire variant.** Ending a run in water douses the
burn — but while water-walking at full ramp you are *not in* the water. To use water as
your counter you have to actually stop and sink, which strands you mid-ocean. So the
fire variant makes ocean crossings genuinely dangerous, and nothing had to be written
to make that true.

*Implementation notes:* provide a solid collision at the water surface while at full
ramp, rather than a Frost Walker-style freeze. *(Correction: frosted ice does melt on
its own — but only while its chunk is ticking. At 80 m/s you outrun the melt, so a
single crossing lays a permanent ice bridge behind you. The conclusion holds, for that
reason rather than the one I gave.)* Cutting out the instant the ramp drops below
maximum is the intended punishment, and since the ramp unwinds gradually the player
gets a beat of warning.

**Lava is included, and it ignites you.** *Operator's call, and better than my
"exclude".* You run on lava at full ramp exactly as on water, and it sets you alight
just as standing in it would — **lava fire burns roughly twice as long as ordinary
fire**, which is the punishment built in. A player with Fire Resistance can therefore
cross the Nether's lava seas at speed, which is a genuinely great destination for the
ability and needs no special case to allow.

Note how this closes the loop on the fire variant: fire boots ignite you on arrival
*and* lava ignites you en route, so Fire Resistance is the single counter for the
whole kit. The most expensive Tier-1 boots end up with the most coherent playstyle.
→ **D21.**

**This resolves D16.** The cost split is no longer "expensive boots vs. moderate
upgrade" — it is **cheap boots, expensive upgrade**, with the drawback carrying the
early-game cost instead of a material price. Players get into the system early and pay
per trip; the netherite upgrade is the one-time investment that ends that. The Leyline
upgrade item (D6a) stays on the atlas, so the two progression axes remain independent:
**better boots remove the drawback, a finer atlas makes you faster.**

### The gauge

*Operator: "a gauge that slowly fills then starts sparking when you're going max speed
feels fast."* Exactly right, and it does two jobs at once:

- **Legibility (the hard requirement).** Debt has to be visible while it accrues, or
  the bill on arrival reads as arbitrary punishment.
- **Feedback for speed.** The sparking state at full ramp is what sells the sensation —
  it is the only readout the player gets that says *this is as fast as this goes*.

Design it as one element carrying both: fill for accrued debt, spark for full ramp.
They are different quantities, but they peak together on a good run, which is the whole
feeling being aimed for.

### Terrain: what elytra has that this does not

*Correction to my earlier framing — I undersold the gap.* Comparing the scale-4 floor
to "elytra cruise" made the floor sound stronger than it is, because **elytra travels
*above* the terrain and the Boots travel *through* it.** That is a categorical
advantage, not a speed number.

Step height 10, fall immunity and leaves-passage blunt terrain but do not remove it.
A coarse atlas still drops you into ravines deeper than 10, sinks you in lava, and
walls you at cliffs — and at 33 m/s you meet those hazards without much warning. The
hazard rate is itself a function of detail, in exactly the same fiction: the boots
place your feet badly because the map told them little.

So the coarse end has three costs against elytra's one advantage, and the earlier
balance worry (D8) largely dissolves: coarse Leyline travel is fast, hungry, and
dangerous, which is a fair trade for cheap and world-spanning.

**Step height stays a flat 10 — settled, and for a better reason than scaling it.**
The steps *are* the bumps in the road. At low speed a 10-block hop is disorienting and
annoying; at high speed it is just more blur. So a constant step height **already
differentiates the scales for free**: the same mechanic reads as lurching on a coarse
atlas and as smoothness on a fine one, with no extra rule and nothing to tune. Scaling
it would have bought a worse version of an effect the flat value gives away.

### Cost of the Boots

*Also a tunable I had treated as free.* The Boots gate entry to the entire system, so
their cost sets when Leyline travel enters a playthrough at all.

**Recommend: expensive Boots, moderate upgrade item.** The Boots are a one-time
purchase; the upgrade is spent once per frozen network, and players should be
*encouraged* to freeze several — different regions, different scales — because that
experimentation is where the fun is. Loading the cost onto the repeatable half
discourages exactly the behaviour the system is for.

### Tuned Iron — the Leyline currency

**Settled: the Leyline half is paid for in Tuned Iron, not paper.** This replaces the
lodestone proposal.

- **Untuned Iron** — crafted from 1 iron block + 1 redstone block + 1 ender pearl.
- **Tuned Iron** — an Untuned Iron becomes Tuned by distance carried **on foot**, by
  either of two routes:
  - **Follow it.** On crafting, it marks a point roughly **2000 blocks** away. Reach
    that block and it tunes **instantly**.
  - **Or just walk.** **3200 blocks** of ordinary travel tunes it wherever you go.
- **Only one may be tuning at a time.** Extra Untuned Irons in the inventory make no
  progress.

**The two paths are the good part.** Following the mark is 37% cheaper, but it sends you
somewhere you had no reason to go — which is the mod generating its own adventure hooks,
and incidentally mapping the corridor you travel to get there. Free-form tuning is the
fallback that stops it ever being a hard gate: if the mark points across an ocean you
have no way to cross, you can ignore it and walk.

**Both counters run at once.** Distance accumulates toward 3200 *while* you head for the
mark, so setting out toward it and giving up banks the progress instead of wasting it.
Reaching the mark short-circuits whatever remains. No decision is ever punished.

*Presentation:* the Untuned Iron should point at its mark — iron, redstone and an ender
pearl is already a compass-shaped recipe, and it echoes the Leyline Sensor (T3.3). A pin
on the atlas would work too, and gives the atlas one more job.

**Why this is a better currency than a material cost.** It cannot be farmed, bought,
traded for or automated — it is priced in *travel*, which is the exact thing the mod is
about. It also makes every Leyline artifact a discrete, remembered investment rather
than a line on a shopping list.

**And it bootstraps.** Once you own Boots, tuning goes as fast as you can run, so the
Leyline half accelerates itself — but the *first* one has to be walked, and the
one-at-a-time rule means the constraint is always wall-clock time rather than resources.
Note the Boots make the *free-form* 3200 cheap while the marked point stays a fixed
destination, so late-game the two paths converge in cost and the mark becomes a
suggestion rather than a saving. That is the right kind of gate: it cannot be shortcut, only earned.

**Two implementation details that decide whether it can be cheesed:**

- **Count on-foot distance only.** Vanilla already tracks `walk_one_cm` and
  `sprint_one_cm` separately from boat, minecart, horse and elytra distance. Using only
  the on-foot statistics kills ice-boat loops, minecart farms and AFK vehicles in one
  stroke, with no anti-cheat logic of our own.
- **Progress lives on the item stack** as a data component counter, with only the first
  Untuned Iron found per player accumulating each tick. → **D22.**

**What it buys:** the atlas→Leyline Atlas conversion, and the Leyline Node (T3.9).
Whether the Boots themselves cost Tuned Iron is open — their recipes above do not
currently use it.

### Two economies, deliberately not tuned against each other

**Settled.** The mod has two halves and they are priced in different currencies:

| Half | Buys | Currency |
|---|---|---|
| **Cartography** — atlases, cells, detail, coverage | knowing where things are | **Paper**, on the vanilla ladder (T1.1) |
| **Leyline** — Boots, frozen atlases, Nodes | moving fast through what you know | **Tuned Iron**, priced in distance walked |

They meet at exactly one point: the Leyline Atlas inherits the scale you paid paper
for, and scale sets your speed. Beyond that they do not need balancing against each
other, which removes a whole class of tuning problem — **paper inflation cannot buy
speed, and travel cannot buy map coverage.**

**Open, and pleasantly so: should a Leyline Atlas wear out?** It would give Tuned Iron a
recurring sink rather than a one-time one, and make a frozen network something you
maintain. It is also very plausibly overtuning a system that already has three costs.
Parked rather than rejected. → **D23.**

### Engineering limits, honestly### Engineering limits, honestly

- **Speed is capped by chunk loading, not by the attribute.** Outrunning generation
  means stutter, or falling through the world. *Mitigation that is also good design:*
  **decelerate at the frontier** — cap speed by whether chunks ahead are loaded, so
  you fly over known terrain and slow at its edge. This synergises with the scale
  ladder: a detailed atlas is one you walked slowly, so its chunks are more likely
  already generated. The fastest atlases sit over the best-loaded terrain by
  construction.
- **Server movement validation.** Vanilla's "moved too quickly!" check will fire and
  needs handling, the way elytra and riptide are special-cased.
- **Step height 10** matches the ceiling I believe `Attributes.STEP_HEIGHT` allows in
  1.21.1 (added 1.20.5, range 0–10) — **verify against decompiled source once the
  build works.** I could not check it here.
- **Leaves passthrough** needs client and server running the same collision predicate
  or the player rubber-bands.
- **Knockback** is `KNOCKBACK_RESISTANCE` at 1.0; **fall damage** is a cancelled event
  or a reset `fallDistance`. Both trivial.
- **The bounds check is free**: `maps.containsKey(MapGridKey.at(scale, slice, x, z))`,
  one hash lookup per tick. Scale-locked and unlayered makes this the simple form.

**Ramp.** Effects tier in over ~2–3 seconds of continuous in-bounds movement, with
immunities engaging at full tier and a short spin-down grace on leaving the bounds, so
one unmapped cell in a corridor does not dump you at speed. The ramp also softens the
movement-validation problem by making acceleration gradual and predictable.

**T3.6 — Absorb and dedupe upstream Map Atlases atlases.** A migration path: our
atlas, in the Atlas Cutter or cartography table, accepts an *upstream* Map Atlases
atlas as input, pulls its maps in, and dedupes with T0.1's logic. The upstream atlas
comes back empty or is consumed.

**This is cheaper than it looks — it needs no hard dependency and no mixin.** Their
map collection is a registered data component whose payload is plain NBT. We can fetch
the type generically:

```java
BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.parse("map_atlases:map_collection"))
```

then round-trip the value through `type.codec()` to NBT and read it with our own
parser — **without ever compiling against their jar or casting to their classes.**
Their format is `Codec.simpleMap(MapType.CODEC, MapId.CODEC.listOf(), …)`, i.e.
`{vanilla: [1, 2, 3], …}`. And because `MapId`s are global level map data, their maps
are directly usable by us the moment we hold the ids.

Practical notes: their atlas is single-scale, so it lands as exactly one layer of ours;
respect `max_map_count` on absorb; run the dedupe on the way in, not after. Guard the
whole path on `ModList.get().isLoaded("map_atlases")` so the recipe simply doesn't
appear otherwise.

**T3.6 is the entrance, T3.2 is the exit** (see T3.2) — together they guarantee the
player's maps are never trapped by the mod's install state. Design and test the pair
together.

Worth doing precisely *because* we are a fork with a different mod id — the two mods
can be installed side by side, and this is the bridge that lets an existing world
move over without losing hundreds of hand-walked maps.

**T3.7 — Landmark auto-pinning.** *Unifies the waystone feature with the operator's
request for vanilla-block waypoints — they are one feature.*

A **data-driven registry of notable blocks** that pin themselves to the atlas when
discovered, with a one-off chime. Waystones stops being a bespoke integration and
becomes **one datapack entry**, which is the right shape for a seam we want to leave
open.

The machinery is already here. Moonlight's `MLJsonMapDecorationType` is data-driven
with a `target` (a vanilla `RuleTest`), a `name` and a `defaultMapColor`, and the mod
already ships `data/map_atlases/moonlight/map_marker/`. What we add is the
**auto-discovery** half — proximity detection that places the pin without the player
pressing the pin key.

**Vanilla candidates:** monster spawner, trial spawner, vault, beacon, conduit, bell,
respawn anchor, lodestone, end portal frame, dragon egg, sculk catalyst / shrieker.

**Restrict phase 1 to block entities.** Spawner, trial spawner, vault, beacon,
conduit, bell — and Waystones' waystone — are all block entities, so detection is
iterating the block-entity maps of the ~9 chunks around the player every N ticks.
Cheap and bounded. Non-BE blocks (end portal frame, lodestone) need real block
scanning; defer them.

**Carried over from the Excerpt, still believed true but unverified here:** range 64
blocks; sound `block.bell.resonate`, chosen because it never plays on its own so it
cannot be mistaken for anything else; memory is **per player, not per atlas** (the
chime is the player sensing the landmark), a landmark chimes when it *enters* range
and is forgotten when it leaves, and never chimes again once activated/visited.

**Two hard constraints, both verified upstream:**

- **Pins cannot go in blank space.** `MLMapMarker#createDecorationFromMarker` returns
  null when a marker is more than ±64 blocks from its map's centre. A landmark only
  pins if the atlas already covers that cell. This is exactly what T1.3's loan exists
  to paper over — chime always (the sensing happened), write the pin if a map covers
  the spot, otherwise the recurrence rule *is* the retry.
- **Attach the marker to the map at the landmark's position, not the player's.** Map
  Atlases' own banner handling uses `MapGridKey.atEntityPosition` (`MapAtlasItem:150`).
  Copying that would be a silent bug: at up to 64 blocks the landmark can fall in a
  different cell, the decoration resolves to null, and the pin never renders.

*Correction accepted:* my earlier reason for deferring the waystone work — that it was
niche to one server's modpack — was wrong. Waystones is in most popular packs. The
real reason to defer is sequencing, and the real fix is that as a datapack-driven
registry it is not a special case at all.

**T3.8 — Atlas enchantability.** Consequence of T3.5's split: because Leyline is an
upgrade rather than an enchantment, the atlas's enchantment slot is free. Make it
enchantable and let it carry real enchantments.

**Settled: Mending is excluded from the atlas, and that decides the paper-thrift
enchantment too.** The two are the same decision, because **Unbreaking and Mending share
one tag** — both declare `supported_items: #minecraft:enchantable/durability`. Adding
the atlas to that tag takes both or neither, and a datapack cannot subtract from a tag
without `"replace": true`, which would break the tag for every other mod.

**To be unambiguous, because the earlier phrasing was not: the paper-thrift *effect*
does not go away.** Chance not to consume paper when drawing a map, on the atlas,
obtained by enchanting it — unchanged, and still the entire reason the atlas is
enchantable at all. **Only the registry entry changes.** Rather than `minecraft:
unbreaking` applying to the atlas, we register our own enchantment declaring the atlas
in its own `supported_items`.

To a player this is nearly invisible: it appears in the enchanting table, books hold it,
anvils apply it, grindstones remove it, and it has a level curve we control — which we
wanted regardless, since vanilla's `1/(level+1)` was far too generous against T1.1's
ladder.

**The one genuine cost:** a vanilla Unbreaking book found in a loot chest will not apply
to the atlas. The player must obtain ours. That is the whole price of excluding Mending.

Consequences, all of them simplifying:

- The atlas never joins `#minecraft:enchantable/durability`.
- Our enchantment declares the atlas directly in its own `supported_items`, so we
  control weight, level range and cost without touching vanilla data.
- **XP-to-paper is dropped** with Mending — the operator's read is right that it was a
  strange conversion, and it was only ever a rescue for an enchantment we are no longer
  including.
- The trash pool loses nothing that mattered: Mending is treasure-only and never
  appeared in table offers anyway.

Naming is open — something cartographic rather than mechanical. → **D25.**

### Enchantability is the tuning lever### Enchantability is the tuning lever

In 1.21 this is the `minecraft:enchantable` data component. For reference: book 1,
diamond 10, netherite 15, iron 14, gold 22 — higher means more and better enchantments
per level spent.

**Set the atlas very low — book-tier, 1.** A cheap enchantment value makes the table
stingy, so Unbreaking III costs a genuine pile of levels and several rerolls rather
than one lucky 30. That is the "burn 20 levels for the good one" behaviour, achieved
with a vanilla stat rather than a custom gate.

### Trash enchants are a feature

*Operator's idea, and the right one.* Without competition in the pool, a low
enchantment value only makes the good roll slower, not rarer. Diluting the pool makes
it genuinely uncertain — and some of the clutter is funny rather than merely annoying.

Add the atlas to these vanilla tags — note `#minecraft:enchantable/durability` is
**not** among them, per the Mending decision above; paper thrift is our own
enchantment with its own `supported_items`:

| Tag | Brings in | Effect on an atlas |
|---|---|---|
| `#minecraft:enchantable/sharp_weapon` | Sharpness, Smite, Bane of Arthropods | Genuinely functional — these add attack damage on any held item. |
| `#minecraft:enchantable/fire_aspect` | Fire Aspect | Also functional. |
| `#minecraft:enchantable/sword` | Looting, Knockback | Functional. **Reversed from my earlier recommendation — see below.** |
| `#minecraft:enchantable/vanishing` | Curse of Vanishing | Actively bad, and directly opposes Soulbound. |
| `#minecraft:enchantable/equippable` | **Curse of Binding** | **The best entry in the table — see below.** |

### Curse of Binding, and why Curios makes it vicious

Upstream already registers the atlas to the Curios **`hands`** slot
(`data/curios/tags/item/hands.json`, plus a Trinkets `hand/atlas` equivalent), so we
inherit that compat rather than building it.

Which means a bound atlas **welds itself into your hands slot**. You cannot remove it,
so you cannot put it in a cartography table or the Atlas Cutter, so **you cannot
reload it with paper.** The atlas is frozen at whatever charge it had, forever, and the
only exit is dying.

Two things sharpen it further, both emergent rather than designed:

- **Grindstones do not remove curses.** There is no cheap undo.
- **Curse of Binding plus Soulbound is permanent.** Soulbound keeps the atlas through
  death, and death was the only escape. The pool contains one enchantment that saves
  your atlas, one that destroys it, and a third that — combined with the first — welds
  it to you for the rest of the world.

That is a genuinely excellent trap: legible on the tooltip, escapable if you notice in
time, and catastrophic if you don't.

**It also gives the enchanting system a shape.** With Binding and Vanishing in the
table pool, **the enchanting table becomes the cheap gamble and anvil-plus-book becomes
the expensive certainty.** That is a good structure and it arrives for free — no rule
needs to be written for it.

### The Atlas Cutter removes curses — and cannot save you from Binding

*Operator's ruling:* the Cutter unbinds and rebinds the atlas every time it is used, so
it should logically strip a curse. Accepted. It gives the Cutter a third job beyond
storage and dedupe, and it is the only curse removal in the game that isn't "throw the
item away," which suits a mod about maintaining a book.

**And it does not defuse the Binding trap, which is the elegant part.** A bound atlas
cannot be removed from the Curios slot — so it cannot be *put into* the Cutter. The
Cutter cures exactly the curses you can carry to it, and Binding is precisely the one
you cannot. The asymmetry falls out of the rules with nothing written for it.

The escape path stays what it was: die, recover the dropped atlas, run it through the
Cutter, re-equip. That costs a death, which is the right price.

*(Unless Soulbound keeps it equipped through death, in which case it is permanent. The
operator believes most Soulbound implementations already treat Binding as mutually
exclusive for this reason. Since Soulbound is a third-party mod, this resolves itself
when we pick one — nothing to chase yet. See D7a.)*

### Looting — reversing my earlier recommendation

I said to skip `#minecraft:enchantable/sword` because Looting would push the atlas from
"marginally useful" into "worth doing." **That was wrong, and the operator's read is
better.**

A held item with no attack-damage modifier deals the player's base 1 damage. Sharpness
V brings that to roughly 4 — **less than a bare stone sword's 5.** So a fully-enchanted
atlas is a *worse* weapon than the cheapest real one, and Looting III on it is
strictly worse than Looting III on a stone sword.

And the ergonomics finish the argument: **an atlas in a Curios slot or off the hotbar
has to be fumbled for**, which is exactly when you don't want to be swapping weapons.

*One thing to check rather than assume:* a non-weapon item uses the player's fast base
attack speed rather than a sword's slow one, which partially offsets the low damage.
Mob invulnerability frames should cap that back down to roughly parity, but it is worth
measuring rather than trusting — the numbers above are from memory. → **D15.**

Include the tag. It is a novelty, not a power option.

### Soulbound

**Deferred — not in play yet.** Several mods provide it; we would only ever be making
ourselves a valid target (non-zero enchantment value, plus membership in whatever tag
the provider declares in `supported_items`). Nothing to build until a specific mod is
picked. Filed as a future item.

**Curse of Vanishing and Curse of Binding above are deliberate anti-synergy with this**
— the pool holds the enchantment that saves your atlas on death, the one that destroys
it, and the one that (with Soulbound) welds it to you permanently.

**Tune the paper-thrift curve down.** Vanilla Unbreaking's `1/(level+1)` gives level
III a 75% saving, which against T1.1's ladder cuts full scale-0 coverage from 18,432
paper to 4,608 and guts the economy the ladder exists to create. → **D9.**

**A Leyline Atlas should be enchantable too** — that is the point of the split, and
there is no conflict, since Leyline is not an enchantment. Worth confirming the frozen
state does not accidentally block it.

**T3.9 — Leyline Node and Leyline Waystones.** *Our own waypoint system, which makes
third-party waystone compat a nicety rather than a need.*

A **Leyline Atlas interred in a Leyline Node** (a block, cost in Tuned Iron) becomes a
**Leyline Waystone**. Wearing the Boots, a player can jump from one Waystone to another.

### No pathfinding, no travel — a jump

*Settled, and it replaces my A\* proposal, which solved a problem this design does not
have.* You point at a linked Waystone and **the boots do the walking**: a straight line,
the player **intangible and invulnerable** for the duration, rendered as a warp-speed
run between jump gates.

Everything awkward about the routed version disappears with it. **D24's terrain
question is closed** — there is no terrain to defeat, so no aborts, no detours, no
"broken link" reporting, and no pathfinder to write.

It also makes **cross-dimension jumps possible for free**, since nothing has to be
walkable: a fade to black at the midpoint covers the dimension swap.

### The cost relocates instead of vanishing

Two changes make jumping *better* travel than running:

- **Tier-1 boots behave like Tier-2 during a jump** — no drawback inflicted on the
  traveller.
- **Hunger is a flat charge per jump**, not distance-scaled.

**But the drawback does not disappear — it is conferred to the Waystone.** After a short
countdown, the boots discharge their element **at both ends**:

| Variant | Discharge at both Waystones |
|---|---|
| Fire | Sets fires |
| Poison / Wither / Blindness | A lingering cloud centred on the stone |

*(Implement as `AreaEffectCloud`, not literal potion items — there is no vanilla
lingering wither or blindness.)*

**This is the best idea in the feature, because the cost becomes social.** Travel gets
cleaner and the *process* gets messier. **Someone with fire boots using your Waystone
can burn your house down.** That single consequence generates: a reason to site
Waystones away from anything flammable, a reason to build stone chambers around them, a
reason to care who has access, and — crucially — **a reason to buy the Tier-2 upgrade
that is entirely about other people.** Tier 2's value in this context is that your
network stays clean.

The short countdown before discharge is what makes it fair: bystanders get a beat to
move.

### Link topology

| | Built Waystone | Natural leyline |
|---|---|---|
| Local links | **2** (V, X) — planar, non-crossing | **A dozen or so** |
| Dimensional links | up to **3** (F, K, N) — unconstrained | **A couple**, and not the same couple as the next ring |
| Scale | whatever Leyline Atlas was interred | always **coarsest** — maximum range |
| Removable | yes, but the atlas is destroyed | **no** |
| Cost | 1 Leyline Atlas (= 1 Tuned Iron) | found, not built |

**This gives the world a road hierarchy without designing one.** Natural leylines are
the interstate system — dense hubs reaching every dimension, fixed where the world put
them. Built stones are the local spurs you lay to reach them. Finding a natural leyline
is genuinely valuable, and *siting your base near one* becomes a real consideration.

### Leylines do not cross

**A hard constraint: no two leylines may intersect.** The emergent result is that the
network **loosely tessellates the world** rather than clumping, and no stone takes all its
neighbours in one direction.

**Correcting my previous recommendation.** I claimed non-crossing would eventually starve
crowded regions of links, producing dud waystones. **That is false, and the operator's
geometry is right:** in a triangulation every face is a triangle, so a point placed inside
one can always connect to all three of its corners without crossing anything. The angles get
sharper; nothing ever collides. **Planarity does not limit degree at all** — there is always
room for three links, however dense the region. No dud stones, no privileged early placement.

**So the limit has to be imposed rather than emerge — and two local links is the clean
number.**

**At degree 2 the local network stops being a mesh and becomes chains and rings.** Which is
what a ley line *is*: an alignment of sites, one after another, not a road network. The
mechanic and the folklore arrive at the same shape.

### The glyphs re-split: VX local, FKN dimensional

**Only local links have geometry.** Dimensional links have no distance and no direction, so
they are not subject to the crossing rule at all and can be plentiful.

| Glyph | Link | Constrained by geometry? |
|---|---|---|
| **V, X** | the two local neighbours | yes — planar, non-crossing, tessellating |
| **F, K, N** | three dimensional neighbours | **no** — arbitrary, unconstrained |

*(This inverts the earlier assignment, where FKN were local and VX dimensional.)*

**A stone therefore reaches further sideways through other worlds than it does across its
own**, and that produces the best emergent routing in the design: **the fastest way across
the Overworld is often to leave it.** Drop through a dimensional link, traverse where the
network is unconstrained, and come back. Nether-road logic, arrived at by geometry instead
of by decree — and thematically perfect for a system about travelling the otherworld.

**One consequence to settle:** does the shed-versus-vent degree rule count dimensional links?
Recommend **yes** — a stone with dimensional links can push its excess *elsewhere*, while one
holding only its two local links has nowhere to send it and vents on the spot. Reaching other
worlds then becomes a safety property as well as a travel one. → **D24.**

### Adding is additive; destroying shuffles

**Placing a stone only ever adds links. Destroying one reshuffles the connections it
orphaned.**

- **Growth never breaks a memorised route.** A stone built tomorrow cannot rewire the line
  you learned last week, so routes-as-knowledge survives an expanding network.
- **Demolition does break routes — other people's.** Removing a stone rewires its
  neighbourhood, and everyone who had learned a path through it has to relearn it.

**That gives demolition a social cost on top of the material one.** Tearing down a stone in a
shared network is not merely wasteful, it is *disruptive*, and networks become conservative
by peer pressure as well as by price. It also strengthens the sunk-cost rule from a personal
inconvenience into a community norm.

### A Waystone is a ring, not a stone

**Settled, and it changes the base unit.** A single inscribed block is not a Waystone — **a
*ring* of them is.** The stones join into one logical node and a traveller arrives **in the
centre**.

**The urban image is the best thing in the document:**

> You wander into a back alley and notice runes cut into the corner of a building. You turn
> around — and every corner of the alley has its own mark. You touch one, and you are on
> another plane.

That is what makes inscriptions worth having over a purpose-built plinth. A Waystone can be
four building corners, the pillars of a bridge, the trees around a clearing, the columns of a
cathedral. **It hides in architecture**, and finding one is noticing that a place was already
a place.

**Two forms:**

| Form | Activation |
|---|---|
| **A ring** of inscribed blocks | Stand in the centre |
| **A single block** | Stand *on* it |

**Activation is frictionless: standing in the centre opens the menu.** No right-click, no
item to hold. You step into the ring and the leylines present themselves — which is exactly
the right verb for a system where the boots do the walking.

*(One condition needed: crossing a ring at 80 m/s must not throw a menu in the player's face.
Gate it on being at rest, or on the ramp having unwound. → **D24.**)*

### What a ring buys

Merging is not optional flavour — it is how a Waystone exists at all — but **bigger rings do
more:**

| Property | Effect of a larger ring |
|---|---|
| Neighbours | The union of the members' links |
| Range | Increased |
| **Tolerance** | A difference of 1 reads as *equal*, so ordinary remainders never discharge |
| **Effect magnitude** | Boosted — when it does go off, it goes off properly |

**So a ring is safer most of the time and far worse occasionally**, which is a genuinely
different risk profile rather than a straight upgrade. Everyday travel is frictionless; the
rare discharge is an event.

**And it is a monument, because every stone in it costs a Tuned Iron.** A five-stone ring is
somewhere around 10,000 to 16,000 blocks walked. That is not a build, it is a project, and it
should look like one.

**Proposals for the numbers:** tolerance scaling with member count (say `floor(n/2)`), effect
magnitude scaling with `n`, and a shared charge pool with a correspondingly higher soft cap.
→ **D24.**

**Worth considering for worldgen:** let natural leylines generate as rings — a wild stone
circle standing in a field, holding however much charge it has accumulated since the world
was made. It is the folklore object the whole design has been circling, and it costs one
structure to place.

### Riding the line — the travel interface

**No menu, no list, no map screen. You look where you want to go and lean.**

1. **Step into the ring.** Your vision darkens.
2. **Glyphs appear overlaid on the world, at the real bearing of their destinations.** V and X
   sit where those stones actually are, out past the horizon.
3. **Dimensional links have no bearing, so they get their own slots.** With one or two, an
   arbitrary *up* and *down*. With more, they populate the **1:30, 4:30, 7:30 and 10:30
   positions** of an analogue clock face — the diagonals, deliberately clear of the compass
   points where local links live. Geometry and non-geometry never collide on screen.
4. **At rest, aim at a glyph and press forward.** The world fades out; a purplish haze comes
   up; you rush along a **visible manifestation of the leyline itself**.
5. **Approaching a junction, that ring's glyph fades and its own destinations resolve into
   view**, arriving as you do.
6. **Keep holding forward and you keep going**, taking the outgoing line closest to your
   cursor. Steering is by lean, not by selection.
7. **Press back at any time** and the downramp begins. You always come to rest at a stone.
8. **Cross-dimension hops put the loading screen immediately after the ramp-up**, so you
   arrive already moving with a couple of seconds to either ramp down or pick your next
   glyph.

**Why this is the right interface for this system.** It is entirely diegetic — the navigation
*is* the world, seen the way the leylines see it. It makes a memorised glyph route into
something you *drive* rather than something you enter. And it turns multi-hop travel into one
continuous motion, which is what the "no pathfinding, just ride" decision was reaching for.

**And the best consequence is one nobody has to implement.** Because you never stop at the
stones you pass through, **the chaos you cause by equalising them happens behind you, out of
sight.** You blast across a network at speed and see none of it. You come back through a week
later and find the forest burned, the field flooded, and a great many skeletons.

*(The four diagonal slots are sufficient. **No ring reaches every dimension** — built or
natural, each gets only a couple, and the next ring along has a different couple. So the slots
never overflow, and the earlier concern is closed.)*

**Which makes dimensional routing a genuine puzzle.** Because no stone reaches everywhere, the
quickest way somewhere may be to **dip into the Nether, jump to the End, drop to the moon, and
surface back in the Overworld**. Each ring offers only the couple of doors it happens to have,
so a long journey is a chain of borrowed exits — and *that* is why a memorised glyph string is
worth carrying.

### Purgatory: one load in, one load out

**A loading screen per dimensional hop would wreck the whole thing.** Chaining four dimensions
in one ride means four interruptions, and the continuous-motion interface dies on the first
one.

**So dimensional travel drops the player into a purgatory — once.** Inside it, the dimensional
destinations of every ring you pass are **reflected** as temporary glyphs, and you keep
riding and steering exactly as before, hop after hop, with no further interruption. When you
press back and ramp down, the game resolves **where on the line you actually ended up** and
loads you directly into that dimension.

**One transition in, one out, however many worlds you crossed.**

And it is not a trick to hide a technical seam — it is *true*. You are travelling through the
otherworld, which is a place, and the reason you cannot see the Nether while crossing it is
that you were never really in it.

**Two implementation notes that fall out, both favourable:**

- **Leyline travel needs no chunk loading at all.** The player never occupies the intermediate
  space — position along the line is abstract, the visuals are a haze, and a single teleport
  happens at the end. This also retires a worry from the walking design: chunk streaming caps
  how fast you can *run*, but it does not touch how fast you can *ride*.
- **Events at stones you passed can be deferred until their chunk next loads.** Cheaper than
  force-loading, and it makes the fiction literal: **the calamity does not manifest until
  someone is there to find it.** You come back a week later and *then* the forest is on fire.

### Carrying a token: the resolution to the pool problem

**The operator's instinct — that a player should carry one energy from the stone they entered
to the stone they left — is right, and the worry that it corrupts the event pool is
avoidable.** It only corrupts the pool if the carried token is drawn *separately* from it.

**So take it from the remainder instead.** A hop's leftover token is exactly the thing that
would have become an event. Let the player pick up **the first one they encounter** and
carry it:

- **A hop whose remainder you pocket fires no event.** You absorbed it, the way Tier-2 boots
  do — except you *move* it rather than destroying it.
- **You can carry exactly one.** Every subsequent remainder on the journey fires normally,
  behind you.
- **At your destination, you drop it.**

Nothing new is created and nothing is drawn from a second pool: one token is diverted from
becoming an event into becoming cargo. The concern dissolves.

**What it adds is a role.** A player becomes a **carrier**, moving charge from where the
imbalance was to where they chose to stop — which gives a genuine reason to make the trip out
to a sink cluster despite the risk, and makes "ride the tide" into something you can steer
rather than merely obey.

**And it produces a clean asymmetry between the two travel modes:**

| | Long journeys are… |
|---|---|
| **On foot** (Boots, hunger debt) | **rewarded** — the flat-hit ceiling and stretched effect both favour committing |
| **By leyline** (jump chains) | **destructive** — you pocket the first remainder, and every one after that detonates in your wake |

A single hop is quiet. A ten-stone blast leaves nine events behind it. That is the right price
for the faster, easier mode, and it arrives without a new rule.

*(Open: a Tier-2 player could pocket *and* absorb. Recommend carrying takes the first
remainder and Tier-2 absorption applies to the next, so the upgrade still damps the wake
rather than being made redundant by it. → **D24.**)*

### Dialling: glyph routes as a phone number

The menu is a **point cloud**. You click the nearest hop; that fetches *its* neighbours;
you keep going. Multi-hop is planned in the interface without any pathfinder — the
player does the routing, the UI just shows what is reachable from where they are looking.

**Every connection carries a glyph.** Working names, to be rendered as in-game glyphs
rather than letters:

| Glyph | Link |
|---|---|
| V, X | the two local neighbours |
| F, K, N | the three dimensional neighbours |

**So a route is a string, and a string is shareable.** `abczbbbc` takes you from your
base to a friend's moon base. That is the best social mechanic in the document —
**routes become knowledge that players write down, trade and teach**, which is precisely
what the whole mod is about. Nothing else here turns map knowledge into something you
can hand to another person in a chat message.

**Asymmetry is the content, not a bug — settled.** Nearest-neighbour relations are not
symmetric, so some links genuinely run one way: stone 1 may reach stone 2 while stone 2's
own three nearest lie elsewhere entirely. *I recommended forcing the graph symmetric to
prevent stranding. That was wrong, and the operator's reading is better on both lore and
design grounds.*

Leylines are a folklore object, and **being stranded is part of what the legend is
about.** Jump into a web of stones without scouting and you walk out normally — slower,
but carrying knowledge you did not have. The route home may run through entirely
different country than the route out.

> **The rule is not "roads are one-way." It is "roads are sometimes one-way, and
> assuming otherwise is a risk you took."**

Two things follow that make this strictly better than the safe version:

- **Scouting acquires value.** A *known good round trip* is a real artifact, because it
  is not guaranteed to exist. That is worth writing down in a way a symmetric graph
  never would be.
- **Stranding is a setback, not a soft-lock.** You always have your legs. The cost is a
  long ordinary walk through terrain you did not map — which is the mod's own subject
  matter delivering the punishment.

**So the UI must not offer a computed return route.** It records the outbound string for
transcription and shows the true links available wherever you are standing. Presenting a
reversed string as "the way back" would be a lie the topology cannot support, and would
strand people who trusted it.

### Glyphs: things that could be carved into stone

Five glyphs, transliterated **F K N V X** — **two local links (V, X) and three dimensional
ones (F, K, N)**, per the re-split above.

**All consonants, which is the point.** Routes read as memorable clusters — `FKNVXKKF` —
and consonant strings are far easier to hold in the head and repeat aloud than mixed
vowels or arbitrary symbols would be.

The rendered forms are **futhark- or Old-English-flavoured carved letters**: angular,
made of straight cuts, plausibly chiselled into a standing stone. (Convenient that the
shapes cooperate — ᚠ, ᚲ and ᚾ are already F, K and N in the futhark, and gebo ᚷ is
literally an X.)

**This also solves the accessibility problem I raised, by not creating it.** FKNVX *is*
the transliteration; the carved glyphs are the rendered form of letters the player can
type, say and write down. No separate fallback needed. *(Standard Galactic Alphabet
would have needed one — it has no natural latin reading and is hard to distinguish.)*

**The glyphs are a second coordinate system, not a trap.** *(Correcting my previous
framing — I wrote this up as deliberate misdirection, which was wrong and needlessly
mean.)*

**F means leyline north.** Not compass north — *leyline* north. It is stable, permanent,
learnable knowledge about how the world is wired, it just does not correspond to anything
the sun or a compass will tell you. Take F from a given stone and you always arrive at the
same place; you simply cannot deduce where that is from a bearing.

So the failure mode is real but it is one honest mistake, not a rigged one: a player
assumes leyline north is north, dials F, and comes out 2000 blocks southeast. What they
learn is not "the glyphs lie" but **"leyline space has its own geography"** — which is
true, useful forever, and a far more interesting thing to know than a bearing.

**Dimensional glyphs are arbitrary per stone — no global ordering.****Dimensional glyphs are arbitrary per stone — no global ordering.** *(Correction: I
over-typed this earlier as an up/down dimension stack needing datapack indices. There is
no stack.)* V and X are simply "this stone's first and second dimensional link."
Sometimes the moon is V, sometimes X, depending on which stone you are standing at. This
removes the datapack ordering requirement entirely and reinforces the arbitrariness that
makes dimensional leylines worth learning rather than deducing.

### Idle charge: an unused stone is dangerous

**A Waystone accumulates energy while it goes unused**, and discharges it as the
traveller's boot element on the next jump — **scaled by how long it sat**.

**This makes finding a stone in the wild a genuine risk.** An abandoned leyline that
nobody has touched in weeks is loaded, and hopping home on it in Tier-1 boots means
arriving inside a large discharge. Conversely, **a network in daily use is nearly
harmless**, because it never accumulates. The safety of a route is a function of how
travelled it is, which is a lovely thing to be true and costs one timer to implement.

- **Each stone discharges its own accumulated charge**, so the wild end may erupt while
  your well-used home end barely sputters.
- **Tier-2 boots trigger no discharge at all**, so they can use any stone immediately —
  the single strongest argument for the upgrade, and the one that shows up exactly when
  you are furthest from home.
- **Any use resets the timer, Tier 2 included** — so a Tier-2 player can *safe* a stone
  for Tier-1 companions. Valuable to a group rather than only its owner.
- **And the absorbed charge repairs the Tier-2 boots.** *Operator's addition, and it is
  what makes the whole thing close.* The energy is not discarded, it is **dumped into the
  boots** rather than blasted around the stone — so it has somewhere to go and a reason to
  exist.

  This produces the best loop in the feature: **Tier-2 boots wear out from travel;
  neglected Waystones charge up; using a neglected stone repairs the boots and safes the
  stone.** A hazard becomes a resource for exactly the player equipped to handle it, and
  the Tier-2 owner is incentivised to go *find* the old dangerous stones nobody has
  touched. Network maintenance becomes a playstyle rather than a chore.

  *(Implication: the Boots carry durability, damaged by travel. Tier-1 boots wear the same
  way but cannot absorb — they discharge instead — so conventional anvil repair is their
  only option. One more thing the upgrade buys.)*
- Cap the accumulation, or a stone left for a year becomes a crater.

### The currency chain: Iron, Coin, Dust

**Settled.** Tuned Iron subdivides, and the subdivisions do different jobs:

| Unit | From | Worth, in blocks walked | Buys |
|---|---|---|---|
| **Tuned Iron** | tuning an Untuned Iron | 2000 (directed) – 3200 (free-form) | one Waystone |
| **Tuned Coin** ×9 | crafted from 1 Tuned Iron | **222 – 355** each | currency |
| **Tuned Dust** | smelted from 1 Tuned Coin | 222 – 355 | one Waystone's links, revealed on a Leyline Map |

*(The coin's value falls straight out of the division: 2000/9 = 222, 3200/9 = 356.)*

**The Coin is the most interesting object in the mod's economy, and it happened by
accident.** It is a currency **backed by walking** — and walking is the one thing in
Minecraft that cannot be farmed, automated, duplicated or bought. Every Minecraft server
economy eventually collapses because its currency is farmable; this one structurally
cannot be, because minting it costs a player's own real time on their own two feet.

*Honest caveat:* the Boots accelerate minting considerably, so late-game coins do inflate
— just against wall-clock travel time rather than against a farm. Worth watching, not
worth pre-solving.

### The Leyline Map

**A map layer showing the network rather than the terrain.** Loaded into an atlas as a
special paper, it draws leylines over whatever you have already mapped — **including
Waystones you have never visited** — showing each stone's connections, their direction,
and their names.

**In §5's terms this is simply another layer**, which is the layered atlas paying for
itself in a way that was not anticipated when it was designed: the renderer already
composites layers, so the network overlay costs the drawing, not the architecture.

**One Tuned Dust reveals one Waystone's links.** So a fully mapped network is a real
investment, and — the good part — **someone who has walked the network can hand another
player a readable map of it.** Combined with glyph route strings, that makes network
knowledge fully transferable: a veteran can guide a newcomer through a leyline system
without either of them being online at the same time.

Direction matters on this layer more than anywhere else, because **the links are
asymmetric.** A leyline map that draws arrows rather than lines is the difference between
a useful chart and a trap.

### Any stone can be a Waystone

*Operator's addition, and it is the best flavour decision in the document.*

Craft a Leyline Atlas into a **Waystone Inscription**, then right-click it onto a stone
block. Runes appear in the stone. That block is now a Waystone.

**Urban waystones.** You go down a back alley, find the bricks with runes cut into them,
put your hand on the wall, and fly off. That is a far better image than a purpose-built
plinth, and it is exactly how a folklore leyline should present — the power is in the
land, and all anyone ever did was mark where.

Consequences worth having:

- **Waystones hide in builds.** They are part of a wall, a floor, a cathedral step.
- **Discovery becomes real.** You can *find* someone's network by noticing runes.
- Purpose-built Leyline Nodes still exist for people who want the plinth. Same cost, pure
  aesthetics.

**Implementation note that decides how well this works.** Vanilla blocks cannot carry new
blockstates or block entities, so the right pattern is to **replace the block with our own
inscribed block that stores the original `BlockState`**, rendering the original model plus
a rune overlay. Breaking it returns the original block (and destroys the atlas, per the
sunk-cost rule). This is the standard approach for overlay-on-arbitrary-block features and
it means inscriptions work on **modded** stone too, not just vanilla.

Scope with a block tag — `#map_atlases_recut:inscribable`, defaulting to stone-ish blocks
— so packs can widen or narrow it. → **D27.**

### Waystones are energy reserves — the third pillar

*Operator's model, and it reframes everything above.* A Waystone is a **Forge-Energy-style
buffer** that can be pushed into and drawn out of. That single statement makes them
**power lines, unsafe batteries and solar panels at once**, and every mechanic already
written turns out to be a special case of it.

**The model:**

| Behaviour | What it makes the Waystone |
|---|---|
| Accumulates charge while idle | a **solar panel** — passive generation |
| Holds that charge, and discharges it into travellers | an **unsafe battery** |
| Moves charge along links | a **power line** |
| Drained by an attached device | a **tap** |

**The link asymmetry is the transmission topology.** This is the part that makes the
design cohere rather than merely stack:

- **A one-way link 1 → 2 is a one-way conductor.** Being able to travel that way *is*
  being able to push energy that way.
- **A two-way link is an equilibrium** — no net passive flow in either direction.
- **Idle accumulation cannot overcome an equilibrium**, but **a negative state can.**
  Draining a node creates a pressure gradient that pulls charge toward it across the
  whole network, two-way links included.

So the one-way roads that strand travellers are the same one-way roads that move power.
Nothing new had to be invented; the awkward property became the useful one.

### The Energy Dispersal Device

A block attached to a Waystone that drains it into whatever energy system the pack has.

**Architecture: store leyline energy, convert at the device.** *Operator's steer, and it is
the right call.* A Waystone should hold **its own unit** — leyline charge — rather than
being an FE battery wearing a hat. The Dispersal Device is the **converter**, and FE is
just its first output format.

**But ship FE first.** Exposing `IEnergyStorage` on the device is one capability that every
FE-consuming machine in the ecosystem attaches to for free, and rotational and thermal
systems already ship their own FE converters — so we write no per-mod glue for Create,
Thermal or anyone else.

**The thing to get right on day one is only the internal boundary:** keep the stored value
as leyline charge in the Waystone's own data, never as FE. Then adding a rotational or
thermal output later is a new device rather than a save migration. → **D26.**

**Draining creates the negative state**, so a tap does not merely consume its own stone —
it **pulls from the network toward itself**, across links, gathering from stones the
player never visits.

### Two consequences worth building around

**1. A receive-only stone is a battery — and a trap.** A stone with incoming links and no
outgoing accumulates without bleeding off. A **crafted limiter** that severs a stone's
outgoing links would let a player create one deliberately.

But the link graph *is* the road network, so **your battery is a place you can jump to and
not jump out of.** Building power storage means building a one-way door. That is the
sharpest tension in the whole document, and it emerges from two rules that were written
for unrelated reasons.

**2. Travel risk comes from *imbalance*, not from charge — and that reconciles the two
playstyles into a skill.** *(This supersedes two earlier claims of mine: first that a
drained network is a safe one, then that power and travel are simply opposed. Both were
wrong; the real rule is better than either.)*

**A balanced network is safe at any charge.** A traveller is equally fine crossing a
network sitting at 0 and one sitting at 10 — what hurts is the *step* between two stones,
not the height of either.

So the tech player can have exactly what they want. Run everything hot at 10, keep it
level with drains and sinks, and it is **perfectly safe to walk through — while sitting one
misplaced waystone away from going off like a bomb.** That is a far better place to arrive
than a standoff: the industrial and travelling uses are compatible, but only for someone
competent enough to keep the network level, and the failure mode is spectacular rather than
merely annoying.

**Transmission with gain.** Because every stone also generates passively, a long chain both
carries power and adds to it along the way. A leyline running to a distant tap is a
generator distributed across the landscape.

### Charge is small integers, spent on an effect table

**Settled, and it makes the whole pillar tractable.** A Waystone's charge is a **small
integer count of tokens**, not a large energy number. Every effect has a token price, and a
discharge spends down the pool.

| Charge | Meaning |
|---|---|
| **0** | safe |
| **1** | will start a fire or two |
| **n** | *n* tokens of chaos waiting to happen |

**Example effect costs** — a datapack table of `{effect, cost, weight}`:

| Cost | Effect |
|---|---|
| 1 | Nearby sheep turn highlighter colours |
| 1 | Start a fire or two |
| 2 | Plant half a dozen jungle trees nearby |
| 2 | A rain of fish and a brief thunderstorm |
| 3 | Immediately turn to night |
| 3 | Spawn skeletons |
| ? | **Fully repair the player's armour and held weapon** |
| 1 | **Add 6 energy**, distributed across this node and all its neighbours |

**That last one is deliberately a net gain, and it is the system's engine.** The fiction is
that the power was always in the leylines and *disturbing* them wakes it up — so
manipulating a network makes it grow. It is also why the soft cap below is not optional.

**A charge of 10 might fire ten small events or three genuinely dangerous ones**, decided
at random among whatever is affordable. That single rule gives an enormous behavioural
range from one integer and one weighted table.

**Travel itself costs nothing.** The leylines carry power enough that running the Boots
between two stones is priced at **zero** — the only thing ever spent is the imbalance left
over when two stones equalise, which is the next section.

### The table is chaos, not punishment

**The most important property, and it is easy to lose in tuning:** some outcomes are
*good*. Fully repairing a player's armour and weapon sits in the same table as spawning
skeletons.

That turns a discharge from a penalty into **a gamble**, and it licenses a whole playstyle
nobody has to design: deliberately overcharging a remote stone and setting it off to see
what happens. A slot machine that costs real time to spin, sited well away from anything
you care about.

*Tuning caution:* keep the beneficial outcomes rare or modest enough that farming them
cannot dominate. But **do not remove them** — they are what makes the mechanic exciting
rather than merely hazardous. → **D26.**

### Flow: a jump equalises the two stones, and the remainder is the discharge

**This is the mechanic the whole pillar turns on.** Travelling a link sums the two stones'
charge and splits it evenly between them. Because charge is integers, an odd total leaves
**one token over — and that leftover token is the discharge.**

| From | To | After | Remainder | Result |
|---|---|---|---|---|
| 10 | 10 | 10, 10 | 0 | **nothing happens** |
| 10 | 9 | 9, 9 (+1 spent) | 1 | one 1-cost effect, bought from the hotter stone |
| 20 | 4 | 12, 12 | 0 | **nothing happens** — 8 tokens quietly moved |
| 21 | 4 | 12, 12 (+1 spent) | 1 | one 1-cost effect |

**So parity is the safety rule.** Two stones at the same charge, or at charges summing
even, discharge nothing at all. This is what makes a *level* network safe to cross however
hot it is running.

**And it makes travel the transmission mechanism.** Jumping from a never-touched 20-charge
wild stone to your 4-charge mainline leaves both at 12: **you just imported 8 tokens into
your network by walking.** Wild leylines are batteries, and harvesting them is a journey
rather than a machine.

> **Riding the tide, not harnessing the ocean.** The leylines are a natural force you move
> *with*. Power flows because someone travelled, not because someone built a pump.

**Stones silo when nobody visits — recommend no passive flow at all.** Making equalisation
purely traversal-driven means an unvisited stone holds its charge indefinitely, which is
what makes wild stones worth finding and what lets a tech player's stones hold distinct
levels without constantly bleeding into each other.

It also deletes the per-tick network simulation entirely: there is no diffusion timer, no
graph solve, nothing to profile. Two stones touch only when a player rides between them.

*(This retires the earlier "power line" framing in favour of something stranger and better:
a leyline conducts **only while being ridden.** If a slow passive settle is wanted later, it
should be slow enough that siloing over a play session is still real — but the design does
not need it, and is cleaner without.)* → **D26.**

**Tier-2 boots absorb the remainder.** An 8-and-10 pair equalising to 9-and-9 leaves one
token; Tier-1 boots spend it on the effect table at both stones, **Tier-2 boots sink it into
their own durability instead.** The upgrade converts every leftover token into repair rather
than chaos.

**And when the boots are already fully repaired, the token comes out as XP orbs.** *(Operator's
addition, and it is the right terminus.)* The energy always has somewhere to go, so a
Tier-2 player is never in a position where absorbing is pointless — which means they keep
wanting to clear imbalances even with pristine boots.

It also closes a loop across the whole mod: XP feeds enchanting, enchanting feeds the
atlas's paper-thrift enchantment (T3.8), and paper thrift feeds the cartography half. **Riding
the network pays for mapping it.**

### The soft cap at 12, and why topology becomes safety

**Leylines amplify.** Between idle accumulation and the amplification effect above, an
active network trends upward on its own, so it needs a ceiling. **The ceiling behaves
differently depending on how connected a node is**, and that single distinction does an
enormous amount of work.

**On any interaction with a node holding more than 12:**

| Node degree | Behaviour over cap |
|---|---|
| **3+ neighbours** | **Sheds** the excess outward, distributed to its neighbours |
| **1–2 neighbours** | **Vents** the excess into the chaos table, on the spot |

**So excess charge migrates to the edges of a network and erupts there.** Interior hubs
pass it along; dead ends have nowhere to pass it and detonate. **The shape of your network
is its safety model**, with no separate system governing it.

**"End of the line" nodes become sinks whether or not the player wanted them to.** Which
converges neatly with the buffer design: a deliberate dead-end stone with a 1-buffer on it
*is* the designated vent, and now there is a topological reason for it to exist rather than
just a mechanical one. A well-built network has planned dead ends, sited in a pit, far from
the house.

**The cascade is the bomb.** Shedding pushes neighbours over the cap too, so touching an
overcharged hub propagates outward and erupts **at every dead end at once**. That is
literally the "one misplaced waystone away from a nuclear bomb" scenario from the balance
discussion, arriving as a consequence of two rules rather than as a designed set piece.

**And it restores the danger of wild stones, better motivated than before.** The parity
rule made an untouched 20-charge stone harmless to jump from. The cap rule makes an
untouched *dead-end* stone that has been climbing for months into something that vents
everything above 12 the moment you finally touch it. Neglect is dangerous again, and this
time it is dangerous because of where the stone sits rather than an arbitrary timer.

**Two implementation notes.** Mark nodes as visited during a cascade so a cyclic network
cannot resolve forever — a cascade is `O(n)` over a few hundred stones, which is fine, but
only if it terminates. And define "interaction" once and broadly: arriving, departing, or
handling the stone should all trigger the check, so there is no way to peek at a loaded
stone without setting it off.

### Overbuy: the runaway roll

**A stone with no dedicated sink can overspend.** When it burns tokens, it rolls to see
whether it may **overbuy** — reach past its budget for a more expensive event (a stone one
token over cap buying a 3-cost event instead of a 1-cost one) — and then rolls again to see
if it buys another. The chain does not stop when the stone empties: **it borrows from the
next stone down the line.**

**A sink is insurance.** A stone with a dedicated sink never overbuys, which gives sinks a
second job beyond power extraction and means a maintained network is predictable as well as
balanced. That is a genuinely good reward for infrastructure.

**The maths, and one problem with a flat probability.** A chain of Bernoulli(*p*) rolls is
geometric — expected extra buys `p/(1-p)`, and `P(length ≥ n) = pⁿ`:

| *p* | E[extra buys] | P(≥5) | P(≥10) | P(≥20) |
|---|---|---|---|---|
| 0.3 | 0.43 | 0.24% | ~0 | ~0 |
| 0.5 | 1.00 | 3.1% | 0.1% | ~0 |
| 0.8 | 4.00 | 33% | 11% | 1.2% |
| 0.9 | 9.00 | 59% | 35% | 12% |

**"All 200 energy dumps at once" is not merely unlikely at a flat *p* — it is impossible.**
Draining 200 tokens at ~2 per purchase needs a hundred consecutive successes: 8 × 10⁻³¹ at
*p* = 0.5, and still 2 × 10⁻⁵ at a wildly unstable *p* = 0.9. The catastrophe the mechanic
promises would never once occur on any server.

**Recommend scaling *p* with the stone's charge instead** — `p = min(0.9, charge / 50)` as a
starting point. Instability then becomes a *property of neglect*, which is what the fiction
already says:

| charge | *p* | E[extra] | P(≥20 buys) | ≈ tokens dumped |
|---|---|---|---|---|
| 13 | 0.26 | 0.35 | ~0 | 2.7 |
| 20 | 0.40 | 0.67 | ~0 | 3.3 |
| 30 | 0.60 | 1.50 | 0.004% | 5 |
| 40 | 0.80 | 4.0 | 1.2% | 10 |
| 60+ | 0.90 | 9.0 | 12% | 20 |

**This makes the soft cap mean something.** A stone kept near 12 is boring and safe; a stone
that has been climbing untouched for months is a genuine hazard, and the disaster scenario
becomes reachable *precisely when the player has earned it*. Same die, better curve.

**Roll per token spent, not per purchase.** *(Operator's refinement: the chance to continue
falls with the cost of what was just bought — a 5-cost overbuy tends to fire and stop, while
a 2-cost is more likely to keep going.)* The cleanest reading of "per energy dumped" is
literal: **the continuation roll happens once for each token spent**, so continuing after a
cost-*C* event needs *C* successes — `p^C`.

Continue-chance after a purchase, under `p = min(0.9, charge/50)`:

| charge | *p* | after cost 1 | cost 2 | cost 3 | cost 5 |
|---|---|---|---|---|---|
| 13 | 0.26 | 0.260 | 0.068 | 0.018 | 0.001 |
| 40 | 0.80 | 0.800 | 0.640 | 0.512 | 0.328 |
| 60 | 0.90 | 0.900 | 0.810 | 0.729 | 0.590 |

**Simulated total tokens dumped** (200k trials against a representative effect table):

| charge | flat *p* | **`p^cost`** | gentle `2p/(c+1)` |
|---|---|---|---|
| 13 | 3.0 (max 28) | **2.6 (max 13)** | 2.8 (max 20) |
| 30 | 5.6 (max 61) | **3.6 (max 26)** | 3.9 (max 29) |
| 40 | 11.2 (max 122) | **6.1 (max 54)** | 5.2 (max 40) |
| 60 | 22.4 (max 371) | **11.0 (max 136)** | 6.2 (max 65) |

**Recommend `p^cost`.** It behaves exactly as described — expensive events terminate chains,
cheap ones ramble — and it keeps a fat tail at high charge (a neglected 60-charge stone can
still dump 136 tokens) while tightly bounding a well-kept one. The gentler curve flattens
the tail too much and loses the catastrophe.

*(Borrowing down the line needs the same visited-marking as the shed cascade, or a cyclic
network will drain itself forever.)*

### Proximity

*Fully superseded by rings.* Stones close enough to interact are now close enough to merge,
and merging is the base unit rather than a hazard. The earlier proximity-pooling rule — where
adjacent stones dumped their excess into a shared event pool, making clusters dangerous to
arrive at — is **retired**: it described a problem that rings solve by absorbing it.

*(What it was protecting against — free, risk-less sink farms — is handled instead by the
sink cluster still needing to be somewhere charge can reach it. See neighbour flow below.)*

### Neighbour flow: how sinks get fed without anyone visiting

**A jump moves charge one hop further than the jump itself.** Travelling A → B equalises
A's other links and B's other links too — **silently.** No events fire at stones that were
not themselves travelled to.

**This is what makes remote sink clusters work.** You ride your mainline; charge seeps
outward into the branch stones your sinks sit on; the sinks draw it down. Nobody ever has to
travel *to* the dangerous cluster, which is precisely the arrangement the proximity rule
above pushes you toward.

It also restores transmission without abandoning the principle. The leylines still conduct
**only when ridden** — they simply conduct one hop wider than the ride.

*(Secondary equalisations move whole tokens and leave any remainder in place. Remainders only
become events at the two stones actually travelled between.)*

### Collisions: a busy waystone is a dangerous one

**Discharges do not resolve instantly — they resolve after a few seconds**, reusing the same
countdown that already gives bystanders a beat to move. One number, two jobs.

**Remainders arriving inside that window pool.** Two players hitting the same hub within a
couple of seconds do not produce two harmless boot discharges; their leftovers **combine into
one budget** and buy a correspondingly larger event.

> A = 7, B = 6, C = 9. One token lands at B from the A jump, two from the C jump. B is now
> holding **3 tokens ready to spend** — and 3 buys skeletons or instant night, not a
> flicker of flame.

**So rush hour is hazardous, and the risk scales with how popular a hub is.** A busy
interchange in a well-used network is exactly the stone most likely to go off, which is a
lovely inversion — the successful part of your infrastructure is the dangerous part.

It also makes travel a thing groups must **coordinate**: going through one at a time is
safe, piling in is not.

**Tier-2 boots absorb exactly 1 token — no more.** *(Correcting my previous phrasing, which
implied they soak their whole share.)* A Tier-2 player removes one token from the pool into
durability or XP; anything beyond that still pools and still fires.

So the upgrade **damps** collisions rather than preventing them. In a high-differential
network two Tier-2 players colliding can still leave 2 or 3 tokens on the table — roughly
what a single Tier-1 traveller would have produced alone. Upgraded boots make a crowd
safer; they do not make it safe.

### Sinks and buffers

**Sinks are how the pillar works without a tech mod at all.** Forge Energy was the first
idea, but it makes the whole system dependent on a pack having machines in it. The sink
types below give leyline charge somewhere to go in **vanilla**, and the FE tap becomes one
option among several rather than the only one.

| Sink | Behaviour | Note |
|---|---|---|
| **Growth** | Bonemeals everything nearby, then plants saplings | Widens once its area is dense |
| **Reaper** | Destroys leaves in a radius | — |
| **Molten** | Converts stone to lava in a widening ring | 1 energy = 1 block |
| **Faucet** | Floods the area with water up to sea level | 1 energy = 1 block |
| **Frost** | Turns lava to obsidian and water to ice | — |
| **Violent** | Damages the 4 nearest entities | Acts as *no sink* if none are in range |
| **Undead** | Spawns zombies and skeletons to absorb charge — **armoured if exposed to sunlight** | — |
| **Lure Stone** | Draws in nearby animals. With none present, summons up to 4 **and fires a 1-cost event** | — |
| **Dispersal** | Converts to Forge Energy for machines | The tech-mod option |

### Producers and consumers

**The set divides cleanly, and that division is the design.** Some sinks *make* something;
others *consume* what the first kind makes.

| Producer | Consumer |
|---|---|
| Growth → leaves and trees | **Reaper** → destroys leaves |
| Molten → lava | **Frost** → lava to obsidian |
| Faucet → water | **Frost** → water to ice |
| Undead → hostile mobs | **Violent** → damages the 4 nearest |
| Lure Stone → passive animals | **Violent** → damages the 4 nearest |

**So a mature network pairs them, and the pairings are all discoverable rather than
documented.** Violent is the universal entity consumer, Frost the universal fluid consumer,
Reaper the leaf consumer. Every producer has an answer, and finding the answer is the
engineering.

Combinations that will be found in a day, all of which should be allowed to work:

- **Undead + Violent** — self-sustaining, and it drops mob loot as a by-product.
- **Lure + Violent** — the same trick with passive animals, so it farms food and leather
  instead.
- **Molten + Frost** — an obsidian generator.
- **Faucet + Frost** — an ice farm.
- **Growth + Reaper** — a closed loop that consumes charge and produces nothing but sticks
  and saplings. The tidy option for someone who wants throughput without a footprint.

**Every one of them terraforms, and that is the point.** A large network permanently marks
the land around its sinks: overgrown jungle, a spreading lava ring, a rising lake, a
haunted field. **Infrastructure leaves a footprint**, which is a far better cost than a
number on a screen and needs no balancing to be felt.

**The expansion clauses are load-bearing.** A sink that saturates stops sinking, and a
network with nowhere to dump climbs into the danger zone. Growth, Molten and Faucet all
widen rather than filling up, so they keep working indefinitely — at the price of taking
more of the map each time.

**Violent's failure mode is the best-designed thing here: with no entities in range it acts
as if no sink were attached at all**, which re-enables overbuy on that stone. It is a sink
you have to *feed*. Leave it and your network quietly becomes unstable again.

**Two combinations that will be found immediately, and both should be allowed to work:**

- **Undead + Violent.** The Undead sink spawns the entities the Violent sink needs, so the
  pair is self-sustaining — and it produces mob drops as a by-product. A genuinely rewarding
  bit of engineering that falls out of two independent sinks.
- **Molten + Faucet.** Lava meeting water is a stone and obsidian generator, which is the
  oldest trick in Minecraft arriving here for free.

**Spread is rate-limited, not capped — settled, and it is the better answer.** Molten and
Faucet convert **1 block per 1 energy**. They widen forever in principle, but only as fast
as charge actually arrives, so a sink stays whatever size its throughput and your
housekeeping agree on. Clear it out occasionally and it never grows past that; ignore it
for a month and you have a lake.

*(This retires my "cap the radius" concern. A cap would have been a number nobody could
feel; a conversion rate is self-evident.)*

**And it makes the landscape the meter.** One energy is one block, so the size of the lava
ring or the lake *is* a readout of how much charge your network has processed. No UI needed
— you can see your throughput from a hilltop.

**A sink block draws one token roughly every minute** into a buffer. What happens next
depends on whether the buffer has room:

- **Buffer has space** — the token is stored, available to whatever machine is drawing.
- **Buffer is full** — the token has nowhere to go, so it **fires a 1-cost event
  immediately**, right there at the buffer.

That makes the buffer a **pressure-relief valve**, and gives overflow a defined, *local*
place to happen rather than erupting somewhere across the network.

**Three sizes, doing genuinely different jobs:**

| Buffer | Capacity | Purpose |
|---|---|---|
| Small | 1 | **A chaos tap.** Deliberately overflows, converting energy into world events at a steady, predictable rate — in a place you chose. |
| Medium | 5 | General-purpose smoothing. |
| Large | 10 | **Powers a city.** A real industrial buffer. |

**The 1-buffer is the interesting one**, because it inverts the danger into a tool. It is
how a player *farms* the chaos table on purpose, in a walled pit far from anything, instead
of having events fire at random across their network. It also directly supports the
gambling playstyle the effect table licenses.

Buffer types beyond these three are mod-compat plumbing and can wait. → **D26.**

### Efficiency scales with charge

**Power is drawn from a high-charge system more efficiently** — a token cashed out of a
charge-10 stone is worth far more than one drawn from a charge-1 stone.

This is the tuning lever that makes the pillar interesting rather than a flat generator,
and it is the source of the tension in point 2 above: **the efficient operating point is
also the dangerous one.** Running your leylines hot is good industry and bad citizenship.

### Don't touch the live wire

**Breaking a charged Waystone discharges it into you.** One of its effects fires, and
lightning strikes the player.

**The warning is escalating and needs no tooltip:**

- **First left-click** — a small damage tick. It stings to whack it.
- **Actually mining it** — severe injury, plus the discharge and the lightning.

**And the severity scales with charge, via one clean rule: the lightning strike is a
guaranteed purchase.** It costs 3 tokens and is always bought first.

- A stone holding exactly **3** discharges as *lightning only*.
- A stone holding **10** buys the lightning, then spends the remaining **7** from the same
  chaos table as any other discharge.

So mining a hot stone can kill you outright — **and the rest of its charge still spends
while you are dead.** You respawn, walk back, and find every sheep for a hundred blocks is
lime green and pink. That image is the entire mechanic in one sentence, and it is worth
protecting in tuning.

A player learns the rule in one hit, at a survivable cost, which is the right way to teach
it. It also adds a sensible procedure to the sunk-cost destruction rule: **drain a stone
before you remove it.**

Death messages, which should be written now while the tone is fresh:

> *Player returned to a low energy state.*
> *Player acted as a discharge route.*

### Why this does not break packs

**The Tuned Iron gate is the balance, and it is unusually robust.** Every Waystone costs
one Tuned Iron — 2000 blocks walked to a marked point, or 3200 free-form, one at a time.
**Walking cannot be automated, farmed, traded for, or bought.** So the size of a player's
energy network is bounded by hours they personally spent travelling, which is the one
currency no tech mod can inflate.

Passive generation rates should still be modest, but the structural protection is already
there — and it is worth noticing that it was not designed for this. The travel currency
turned out to be the right currency for a power system too.

**Scaling proposal:** let generation scale with the **interred atlas's coverage** — a
bigger atlas is a bigger collector. It reuses the scale ladder a third time and makes
natural leylines (always coarsest scale, a dozen links, unbreakable) into the **power
plants** of the network, which is exactly the right shape for something the world placed
rather than the player.

### Honest scoping note

**This is a third pillar, not a feature.** Cartography, travel, and now power transmission.
It is a good idea and it unifies the design rather than bolting onto it — but it should be
**phased last**, after the map and travel halves are real.

**Do one thing early, though: put the energy value in the Waystone's data model from the
first version.** A stored integer costs nothing now and avoids a save-breaking retrofit
later. → **D26.**

### The atlas is consumed — and that is what prices a network

**Interring consumes a Leyline Atlas**, which means **every Waystone costs one Tuned
Iron**: roughly 2000 blocks walked, one at a time. A ten-stone network is a genuine
project rather than a shopping trip, and it cannot be rushed with resources.

Duplication is the pressure valve, and it should stay on the *cheap* side of the split:
clone the plain atlas at a cartography table for paper, then spend a Tuned Iron to
Leyline each copy. **Paper buys the copies, Tuned Iron buys the waystones** — the two
economies stay separate exactly as D3-era decisions intended.

**Breaking a built Node destroys the atlas — settled.** *(I recommended the jukebox
model, where the atlas comes back out. Overruled, and the operator's reason is the
better one.)* Relocating or replacing a Waystone costs a fresh Tuned Iron, which means
roughly 2000–3200 blocks walked.

**The consequence is the point: players keep imperfect Waystones.** Tearing one down to
optimise a network costs more than living with it, so networks **accrete** rather than
getting designed — they end up historical, full of stones placed for reasons that no
longer apply, which is exactly how a real road system looks and exactly what a folklore
leyline network should feel like.

### Dimensional links are deliberately arbitrary

**A cross-dimension link goes somewhere unrelated.** A Waystone at 0, 0 on the moon may
link to 1000, 2500 in the Overworld. Leylines do not respect geography, and travelling
between dimensions on foot *should* be disorienting.

**One thing this needs to be usable: the link must be stable.** Roll it once when the
Waystone is created and persist it — a link that re-rolls per use is noise rather than
mystery, and no route could ever be learned. → **D24.**

Note what this preserves: Nether roads keep their purpose for *precise* dimensional
travel, while leyline hops get you somewhere *approximate*. Two systems that do not
compete.

### Tier 4 — parity gaps not yet claimed

From §4, still unaddressed: **death waypoint** (trivial, high value, no dependencies —
possibly promote to Tier 1), and **waypoint teleport** beyond creative mode (a
gameplay/balance decision, not a technical one).

---

## 7. Suggested order of work

1. **Build environment (§9).** JDK 21. Nothing else is possible without it.
2. **Settle the package root (D5a)** before the first commit.
3. **T1.4 Paper Block** as a first commit. Trivial, touches registration/recipes/loot/
   one interaction, proves the toolchain end to end without risking anything.
4. **T0.1–T0.3** correctness fixes, cut as three branches off `upstream/1.21.1` and
   sent as the three PRs in §3. Independently valuable, and they get you fluent in
   `MapCollection` — the exact class §5 changes.
5. **Strip Fabric** from the fork branch, once the PRs are open.
6. **§5.6 Option 2 — dirty-chunk tracking (D1).** Before layers, not after: biggest
   single win, and it changes whether §5.3's invasive mitigations are needed at all.
7. **Profile.** Re-measure with dirty-tracking in before committing to layers.
8. **§5 layered atlas (D2)** — data model first (`MapCollection`; `MapGridKey` already
   cooperates), then `AbstractAtlasDisplay`, then HUD / `MapWidget` / lectern.
9. **T1.1–T1.3 paper economy**, once layers exist to be priced.
10. **T3.5 Leyline Atlas + Thousand League Boots.** Soon after the economy — it is the
    payoff that makes the ladder feel like a mechanic instead of a tax. Note it does
    *not* depend on layers (the Leyline Atlas is unlayered), so it could move earlier
    if you want the fun part sooner.
11. **T3.6 absorption and T3.2 dissolve, together.** The migration pair. Early enough
    that people can move over before their upstream atlas grows further.
12. **T3.1 Atlas Cutter.**
13. **T3.7 Landmark auto-pinning**, vanilla entries first, Waystones as a datapack
    entry after.
14. **T3.8 enchantability**, **T3.3 Leyline Sensor**, death waypoint, then reassess
    T3.4 Drawn Layer.
15. **T3.9 energy pillar** — last, after cartography and travel are real. *But add the
    stored-energy field to the Waystone data model in step 13, not here.*

*Two things that moved and why:* performance work leads because it is load-bearing for
the design decision behind it; T3.5 sits right after the economy because it is what
makes Tier 1 worth paying for. T3.5 is also the most movable item on the list — it has
no hard dependency on §5.

---

## 8. Verified upstream facts

Read from `~/workingdir/gits/mapatlases-neoforge` @ `dbc5e24`, 2026-08-29. These bound
what is possible; re-check after any upstream merge.

**Grid and scale**
- `MapGridKey.getBlockWidthFromScale(int scale)` → `128 * (1 << scale)`.
- `MapGridKey` identity is `(mapX, mapZ, slice, gridWidth)` — **width is already part
  of the key.**
- `MapType.getCenter(px, pz, width)` snaps to that grid. World-generated maps
  (shipwreck, buried treasure, explorer) are centred on their structure at arbitrary
  coordinates and will never align.
- `MapCollection` has one `byte scale`, set from the first map added, and
  `populateInDataStructure` silently rejects `d.scale != scale`. **An atlas's scale is
  fixed at craft time, forever** — `MapAtlasCreateRecipe` builds a blank atlas and
  adds the crafted-in filled map, which sets the scale.
- One map per cell: the selection model is `maps.get(key)`; duplicates have no defined
  render order, which is why they're rejected.
- `MapItemSavedData.scaled()` calls `createFresh(...)` — **vanilla zoom-out discards
  the explored area.** Any "compress the atlas" feature has to downsample the `colors`
  byte arrays itself: four maps at scale N, each halved to 64×64, composited into the
  quadrants of one scale N+1 map. Ours to write, and lossy.

**Counting and components**
- The atlas has exactly five components: `MAP_COLLECTION`, `LOCKED`, `EMPTY_MAPS`,
  `HEIGHT`, `SELECTED_SLICES`. **None holds an x/z position** — so there is nowhere
  for "where the atlas was last opened" to live, and the lectern retains no view state.
- `MapCollection.size` is summed from `ids` in the constructor, **not** from the
  deduplicated `maps`. Duplicates inflate `getCount()`.
- `MapCollection.equals` compares `ids` only — an `EnumMap<MapType, List<MapId>>`,
  order-sensitive.
- `Slice` is `record Slice(MapType type, Optional<Integer> height, ResourceKey<Level>
  dimension)` — no coordinates.
- `EmptyMaps.addAndAssigns` (both overloads) clamps with `Math.max(0, ...)`;
  `setAndAssign` does not.

**Rendering**
- `AbstractAtlasDisplay.drawAtlas` iterates a square/circular neighbourhood of cells
  at one `mapBlocksSize`, resolving each through the abstract
  `getMapWithCenter(int centerX, int centerZ)`. `drawMap` draws a fixed 128px quad and
  is otherwise scale-agnostic.
- `AtlasOverviewScreen` already opens centred on the player via
  `currentMaps.getClosest(player, selectedSlice)`.
- No lectern class reads `getSelectedSlice` or `SELECTED_SLICES`.

**Map updates**
- `MapItemMixin` captures vanilla's scan range as `@Local(ordinal = 5) LocalIntRef
  range` and multiplies by `MapAtlasesConfig.mapRange` (`map_range_multiplier`,
  default 1, range 0.0001–10). **This is the hook for scale-dependent range.**
- `MapAtlasesServerEvents.maybeCreateNewMapEntry` gates on
  `emptyCount > 0 || isCreative || bypassEmptyMaps`, and decrements by 1 on success.
- `MapsNeighborhood.around(player, scale, slice)` computes the centre cell plus up to
  8 neighbours, included only when the player's `discoveryReach` actually crosses the
  cell boundary — so it is not a blind 3×3.
- `WeightedUpdateScheduler` weights by distance and deprioritises maps with no blank
  pixels left.

**Cartography table**
- Map Atlases injects at `setupResultSlot` **HEAD** and cancels. Four branches:
  shears (cut selected map), atlas+atlas (merge), valid empty-map ingredient (add
  empties), filled map (insert). The merge and insert branches both bail on scale
  mismatch.
- `slotsChanged` calls `setupResultSlot`, so injecting at **RETURN of `slotsChanged`**
  runs after the cancelled method and outside its scope — the reliable hook if we ever
  need to correct the result slot without racing the existing injector. *(As the fork
  we can simply edit the method, but this is worth keeping for any mixin-shaped work.)*

**Config defaults worth knowing**
- `max_map_count` = 512 · `accept_paper_for_empty_maps` = **false** ·
  `require_empty_maps_to_expand` = true · `pity_activation_map_count` = 0 ·
  `map_updates_per_tick` = 1 · `map_range_multiplier` = 1 ·
  `activation_locations` = `HOTBAR_AND_HANDS` · `radar_radius` = 64.

**Hunger effect, measured in-game 2026-08-29** (operator, with a saturation-readout
mod; raw table at the end of `notes/QuickNotes`). All rows at 10 s duration; `Str` is the
amplifier argument passed to `/effect give`:

| Str | 10 | 40 | 50 | 75 | 100 |
|---|---|---|---|---|---|
| points drained | 1.5 | 5.25 | 6.75 | 9.5 | 13.0 |

Least-squares fit through the origin: **`Sat = 0.013 × Str × Time(s)`**, accurate to
within measurement noise across the range. Duration is linear, as expected. Used to
calibrate T3.5's hunger debt.

**Upstream's own TODO** (`MapAtlasesMod.java`) lists `//lectern marker` and
`//auto waystone marker` as unimplemented — so a waystone-pinning feature is something
the maintainer already wants.

### Carried over from the Excerpt but NOT verified here

The Excerpt describes a waystone integration (activation pins the atlas; a 64-block
proximity chime on `block.bell.resonate`; Balm's `WaystoneActivatedEvent`). **None of
that is checkable against this clone** — Waystones and Balm are not dependencies of
this repo and are not present on this machine. It was designed for a specific server's
modpack. Treat it as a candidate integration to re-verify from scratch if you want it,
not as settled work. It is deliberately left out of §5 for that reason. → **Q4.**

---

## 9. Environment

**The build needs two JDKs, which is not obvious from the mod's own version.** Verified
2026-08-31 by reproducing the failure.

- **JDK 25 runs Gradle.** Upstream's build plugins — `com.possible-triangle:core|common|
  fabric|neoforge` at `1.4.234`, from `maven.muon.rip` — publish module metadata declaring
  `org.gradle.jvm.version >= 25`, so buildscript classpath resolution fails outright on 21
  with *"Dependency requires at least JVM runtime version 25."*
- **JDK 21 is the compile toolchain**, because Minecraft 1.21.1 targets Java 21.

This is the ordinary modern arrangement (newer Gradle runtime, older compile target), it is
just undocumented upstream. `setup-jdk.sh` in this folder installs both into a sandbox with
no sudo and declares them via `org.gradle.java.installations.paths`.

Gradle wrapper is 9.6.1, and `org.gradle.jvmargs = -Xmx5G` means the build wants ~5GB of
headroom.

Nothing else is missing — `git` is available and the upstream clone is in place at
`~/workingdir/gits/mapatlases-neoforge` (shallow, `--depth 50`; deepen it if you want
full history for `git blame`).

First run will be slow: NeoForge decompiles and remaps Minecraft, and Parchment
mappings (`2024.11.17`) are downloaded on top.

---

## 10. Decisions and remaining opens

### Settled 2026-08-29

**D1 — Dirty-chunk tracking is the first performance task.** Before layers, because it
changes what a layer costs. (§5.6 Option 2.)

**D2 — Layers, not a custom tile format.** Layers were the original design vision. A
custom format is worth it only if it buys something *complementary* — §5.6 documents
the one shape that qualifies: a client-side high-detail **display overlay** over
authoritative vanilla data, losing no interop. Deferred until layers and dirty-tracking
land and we can see what detail is actually missing.

**D3 — Boots effects scale with the atlas's map scale, uniformly.** The Leyline Atlas
is scale-locked and unlayered, so everything is decided once at freeze time — no
per-cell variation. **Three axes, all running the same direction:** speed (elytra
cruise → engine ceiling), hunger debt (4× sprinting → slightly better, per metre), and
terrain risk. Self-balances speed against coverage in paper. (T3.5.)

**D3a — Hunger is a debt settled on arrival, not a continuous drain.** Travel banks a
counter; decelerating converts it into the vanilla Hunger effect; the Boots refuse to
work while Hunger is active. Removes the mid-journey eating beat, makes over-committing
a real decision, and **rewards long straight shots while punishing bursts and jumps.**
(T3.5.)

**D3c — Split payment: a flat unavoidable hit plus a short, high-intensity effect.**
`flat = min(50% of debt, 10 points)` as immediate exhaustion, remainder as Hunger over
**~5 seconds** at whatever Str delivers it. Calibrated from the operator's in-game
measurements (§8): `Sat = 0.013 × Str × Time(s)`. The flat hit's ceiling is what
rewards long runs — 50% of a burst is unavoidable against 25% of a long haul, so the
long run is twice as efficient in the portion milk cannot save you from. *Supersedes
both my "split the dials" proposal and the earlier 30-second figure.* (T3.5.)

**D3e — Nausea is ruled out, and it establishes a general rule.** It makes real players
unwell, which is sufficient on its own. It is also **client-side disableable**, so it
would make the cheap boot tier equal to the upgraded one for anyone with Distortion
Effects turned down. **General rule: never gate a mechanic on an effect the player can
disable client-side.** Darkness is suspect for the same reason. *(Withdraws my own
suggestion.)* (T3.5.)

**D3g — Amplifier clamps at 255; conserve `Str × Time` instead of truncating.** Setting
a higher amplifier is possible but has no additional effect, so a 5-second target that
would need Str 462 clamps to 255 and **stretches to 9.1 seconds** rather than
under-delivering. Nothing is lost, and it rewards long hauls a second time: a 1-second
milk reaction escapes 80% of a small debt's remainder but 89% of a maximal one. *(Both
this and the flat-hit ceiling push the same direction.)* (T3.5.)

**D3h — Tier 1's cost is a lockout, not damage.** The secondary effects are too weak as
threats — poison cannot kill at all, milk cancels everything, and only an out-of-food
player on Hard is in danger. Since the secondary is on the blocking tag, **scale its
duration with debt**: Tier 2 waits ~9 s after a maximal run, Tier 1 waits that plus tens
of seconds. **Tier 1's real running cost becomes a bucket of milk per trip**, which is
the per-trip cost D3f wanted, delivered as a consumable rather than a damage number.
(T3.5.)

**D3i — Water walking at full ramp only.** Solid footing at maximum ramp, sinking below
it, never available from a standing start — so oceans are highways if you arrive at
speed and a trap if you stop. Favours coarse atlases (mapped ocean is cheap per area),
and creates the feature's best emergent tension: the fire variant's counter is water,
but using it means stopping, which strands you mid-ocean. (T3.5.)

**D3f — Boots come in cheap elemental tiers, upgraded with netherite.** Tier 1 inflicts
the hunger debt plus a secondary effect (fire, poison, wither, blindness); a netherite
smithing upgrade strips it. Because the secondary effect is also on the blocking tag,
**cheap boots lock themselves out for longer with no new rule.** The drawback is a build
choice — bring the counter for your element — rather than a price tag. *Resolves D16:
cheap boots, expensive upgrade.* (T3.5.)

**D3d — Deceleration is our own ramp tier reaching zero, never a velocity threshold.**
Immune to Speed potions, Depth Strider, Soul Speed and third-party movement enchants,
and it lets the player stutter or shed speed to dodge without settling the debt. (T3.5.)

**D3b — Step height stays a flat 10.** Not scaled. The steps are the bumps in the road,
and a constant value already differentiates the scales for free — disorienting at low
speed, mere blur at high speed. Scaling it would buy a worse version of what the flat
value gives away. *(Closes the former D11.)*

**D4 — Dissolve is retained as the uninstall path.** Not subsumed by the Atlas Cutter.
It exists so maps are not trapped in an item that stops existing. Paired with T3.6 as
entrance/exit. (T3.2.)

**D5 — Leyline is an upgrade item, not an enchantment.** Atlas + upgrade → fixed
Leyline Atlas. Boots are an ordinary item that unlocks their use. Both keep their
enchantment slots free, which is what makes T3.8 possible. (T3.5, T3.8.)

**D6 — Waystones: seam left open, compat later.** Not because it is niche — *correction
accepted, Waystones is in most popular packs* — but because T3.7 makes it a datapack
entry rather than an integration, so there is nothing to build twice.

**D7 — Name and package.** `Map Atlases Recut`, mod id `map_atlases_recut`, package
root **`notuserfriendly.mapatlasesrecut`**. Set the package before the first commit
(§2).

**D8 — The coarse-atlas balance worry is resolved, not by cost but by cost-of-use.**
Hunger drain and terrain hazard do the work that a price tag would have done badly.
Coarse Leyline travel is fast, hungry and dangerous; elytra keeps its categorical
advantage of flying *over* the terrain rather than through it. (T3.5.)

**D9 — Paper thrift uses vanilla Unbreaking**, via a datapack tag addition plus our own
effect implementation. *Correction: my claim that this required a custom enchantment
was wrong.* Enchantability set book-tier (1), pool deliberately cluttered. Table
enchanting allowed. (T3.8.)

**D9a — Curse of Binding is in the pool, and Curios makes it vicious.** A bound atlas
welds into the Curios `hands` slot (compat already exists upstream), so it cannot be
removed, so it cannot be reloaded with paper. Grindstones don't remove curses; with
Soulbound it is permanent. This also gives enchanting its shape for free: **the table
is the cheap gamble, anvil-plus-book the expensive certainty.** (T3.8.)

**D9b — Looting is in. *Reversal of my earlier recommendation.*** An atlas with
Sharpness V deals ~4 damage against a bare stone sword's 5, so a fully-enchanted atlas
is a worse weapon than the cheapest real one — and one in a Curios slot has to be
fumbled for. Novelty, not a power option. (T3.8.)

### Remaining opens

*(D6a — Leyline upgrade item — settled as **Tuned Iron**, see D22. The lodestone
proposal is withdrawn.)* Either accept that, or move the netherite gate to the Boots and
pick something cheaper here.

*(D7a — Soulbound — deferred. Nothing to build until a provider mod is chosen.)*

**D8a — All numeric tuning, as one pass.** Deferred deliberately: speed ladder, hunger
debt rates, flat-hit ceiling, Tier-1 lockout duration (D19), Tuned Iron distance,
paper-thrift curve (D9c), boot recipe costs (D20). Numbers are cheap to change once the
mod runs; doing them together against a real save beats guessing them individually now.
The verification items ride along: sprint exhaustion 0.1/m, `Attributes.STEP_HEIGHT`
max 10, the Str-255 ceiling, atlas melee damage (D15).

**D22 — Tuned Iron anti-cheese.** Count only vanilla's on-foot distance statistics
(`walk_one_cm`, `sprint_one_cm`), never boat/minecart/horse/elytra — this kills ice-boat
loops and AFK vehicle farms without any anti-cheat logic of our own. Progress lives on
the item stack; only the first Untuned Iron found accumulates per tick.

**D23 — Should a Leyline Atlas wear out?** Would turn Tuned Iron into a recurring sink
and make a frozen network something you maintain. Plausibly overtuning a system that
already has three costs. Parked, not rejected.

**D24 — Leyline Waystone open items.** Topology is settled (3 local + up to 2
dimensional for built stones; a dozen-plus and every dimension for natural ones,
immovable and always coarsest scale). Remaining:

*Settled since:* asymmetric links are deliberate and stranding is content; no computed
return route; glyphs are FKNVX with arbitrary per-stone dimensional assignment and no
global ordering; breaking a built Node destroys the atlas.

Remaining:

- **Does any use reset the idle-charge timer, or only Tier-1 use?** Recommend any use,
  so a Tier-2 player can *safe* a stone for Tier-1 companions — it makes the upgrade
  valuable to a group rather than only its owner.
- **Cap on accumulated idle charge**, or a long-abandoned stone becomes a crater.
- **Cross-dimension links rolled once and persisted**, never re-rolled per use.
- **Can the Untuned Iron's mark land somewhere unreachable?** Free-form tuning makes it
  survivable either way; open whether to constrain the roll to land.
- Numbers for the tuning pass: flat hunger charge per jump, jump duration, idle-charge
  rate and cap, natural-leyline link count and spawn density.

**D26 — Energy pillar tuning.** Model is settled: integer tokens, threshold-2 diffusion,
efficiency scaling with charge, chaos table with beneficial entries. Open numbers: idle
accumulation rate; soft cap (12 proposed) and the degree threshold (3+ proposed); sink draw
interval (~1 min); the efficiency curve's shape; the amplification effect's cost and payout
(+6 proposed) — it is a net gain and therefore the loop that most needs watching; how much
each beneficial effect gives before overcharge-farming becomes optimal.

**Overbuy curve:** confirm `p = min(0.9, charge/50)` or similar. A flat probability makes
the promised catastrophe mathematically unreachable; scaling with charge makes it reachable
exactly for neglected stones.

**Sink rates:** 1 energy = 1 block is settled for Molten and Faucet. Open: draw rate per
sink type — they need not all be one token a minute, and varying it is free
characterisation.

**Ring radius and minimum size:** how far apart may stones be and still merge, and how few
count as a ring. Sets both the architectural freedom of an urban Waystone and the cost floor
of building one.

**Collision window:** confirm it reuses the bystander countdown rather than introducing a
second timer.

**Bootstrapping:** amplification only grows a network that already has charge, so idle
accumulation still has to exist to seed from zero. Confirm both mechanisms ship, with idle
as the slow baseline and amplification as the fast, chaotic growth.

**Also open:** whether any slow passive diffusion exists at all, or whether equalisation is
purely traversal-driven — **recommend purely traversal-driven**, since it makes stones silo,
makes wild stones worth finding, and deletes the per-tick network simulation entirely. Keep the stored
value as **leyline charge** in the Waystone's own data from version one — never as FE —
so a rotational or thermal converter is a new device rather than a save migration.

**D27 — Waystone Inscription scope.** Which blocks are inscribable (recommend a
`#map_atlases_recut:inscribable` tag defaulting to stone-ish, so packs can adjust); does
the inscribed block store and restore the original `BlockState` (recommend yes — it is what
makes modded stone work); do naturally-generated leylines use the same inscribed-block form.

**D25 — Name the paper-thrift enchantment.** Something cartographic rather than
mechanical. It is our own enchantment with its own `supported_items`, so weight, level
range and cost are all ours to set.

*(D10 — Atlas Cutter and paper blocks — settled: it takes every paper-bearing item,
including books, returning leather. Datapack-driven table in T3.1.)*

**D13 — Hunger-debt gauge: settled in principle, open in detail.** Confirmed wanted.
Fills with accrued debt, **sparks at full ramp** — one element doing legibility and
speed-feedback at once, and the two peak together on a good run. Part of T3.5, not a
follow-up.

*(D17 — amplifier cap — settled as D3g below. The truncation proposal is withdrawn;
conserving Str × Time is strictly better.)*

**D18 — Which effects ground the Boots?** Hunger is load-bearing; Poison confirmed.
Remaining candidates are the Tier-1 drawback elements themselves (fire, wither,
blindness), which want to be on the tag so cheap boots self-limit. Nausea and Darkness
excluded by D3e. Implement as a datapack tag so packs can extend it.

**D19 — How steeply does the Tier-1 lockout scale with debt?** The gap between tiers is
now carried entirely by lockout duration, so this number *is* the value of the netherite
upgrade. Needs to be long enough that forgetting milk hurts, short enough that Tier 1 is
playable without it.

**D20 — Is fire mis-costed?** All four variants and their recipes are settled, but
difficulty runs poison < wither < blindness < fire *and fire is also the best variant* —
lava interaction, one clean counter, strongest theme. A player who has ancient debris is
one ingot from Tier 2 anyway. Fine if the variants are a menu; re-cost fire if they
should be a ladder.

**D21 — Fluid-walking edge cases.** Water and lava both settled (solid at full ramp,
sink below it, never from a standing start; lava ignites you with its longer burn). Open:
any grace period at all — recommend none, the ramp already unwinds gradually.

*(D14 — Mending — settled as excluded; see D25. XP-to-paper dropped with it.)*

**D15 — Verify atlas melee numbers.** A non-weapon item uses the player's fast base
attack speed, which partially offsets its low damage; mob invulnerability frames should
cap it back to roughly stone-sword parity. Measure rather than trust — D9b's reasoning
depends on it, though not heavily.

*(D16 — cost split between Boots and upgrade — resolved by D3f: cheap elemental boots
carry their cost as a per-trip drawback, the netherite upgrade is the one-time
investment, and the Leyline upgrade item stays on the atlas as its own axis.)*

## Appendix A — operator responses, 2026-08-29 (verbatim)

*Raw answers to the Q1–Q7 that §10 replaced. Kept because the reasoning behind
several decisions lives here and not in the summarised form above.*

1. Yep. Seems like a clean way to make this more powerful
2. Layers were the original design vision. Custom format is only worth it if it buys us something in the complementary sense.
3. Mentally easiest solution is that they are faster if the map is more detailed. Kind of like the boots "Know" where to place their next step. I think it's fractional. Min is "elytra max speed" and max is "Max possible speed in game", and we spread the map scales evenly among it. This does mean the Leyline Atlas is scale locked and unlayered, but that works for a discrete item.
4. Mostly. Dissolve needs to be kept for if the mod is uninstalled so players can get all their maps converted to a vanilla format before uninstalling.
5. Compat patch later. Leave the seam open. Although waystones is in almost every popular modpack, so your justification doesn't work there.
6. Hence why I'd like to split it out from vanilla enchanting. An atlas, upgraded with an item, becomes a fixed, Leyline Atlas. Normal Thousand League Boots let you use Leyline Atlases. But the boots can also be vanilla enchanted. This raises another item, detailed at 8.
7. I want it to sort with Map Atlases, so whatever combo of Map Atlases Recut works.
8. Soulbound Compat. I don't remember the mod, but an enchantment on the atlas that lets you keep it after dying. Because of six this means the vanilla enchant system has to be split out from the item, so you can enchant a regular Atlas and a Leyline Atlas with Soulbound. Maybe Unbreaking? Chance of not using paper when drawing a map?
9. You didn't ask this, but the waystone compat is a vanilla feature I'd like as well. There are a few, highly unique blocks in the game, monster spawners come to mind, but those wouldn't be the limit. Having these, vanilla items, also able to make waypoints would be nice.
