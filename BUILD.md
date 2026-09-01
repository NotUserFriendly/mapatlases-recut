# Map Atlases Recut — build order

Ordered objectives, testable invariants, and playtest gates.
**Prose, rationale and open decisions live in [`DESIGN.md`](DESIGN.md)** — this file says
*what to do and in what order*, and points there for *why*.

Written 2026-08-29. Upstream baseline `MehVahdJukaar/mapatlases-neoforge` @ `dbc5e24`,
cloned to `~/workingdir/gits/mapatlases-neoforge`.

---

## Module split

Three pillars, each a layer over the last. **Every pillar must be complete and enjoyable
without the one above it.**

| Pillar | Contents | Depends on | Stands alone? |
|---|---|---|---|
| **I — Cartography** | atlas, layers, paper economy, Atlas Cutter, enchantability, landmark pins | upstream fork only | **Yes.** A better Map Atlases, and nothing else. |
| **II — Leyline** | Tuned Iron, Boots, Waystones, glyph travel, Leyline Map | I (needs a Leyline Atlas) | **Yes**, as a travel mod on top of the map |
| **III — Charge** | energy, sinks, chaos table, buffers, FE dispersal | II | The overcoat. Removable. |

**Two hard interface rules, both from the operator:**

- **Pillar I ships with third-party Waystones support** (via landmark pinning, §2.7) so a
  player who only wants the map gets waypoint integration from a mod that already exists.
- **Pillar II must not require Pillar III.** Waystone travel is complete and fun with charge
  disabled; charge is decoration on a working system, never a prerequisite.

**Implication for config:** a "core" mode that runs Pillar I (+II) with the magical layer off
should be a supported configuration, not an accident.

---

## Phase 0 — Environment

- [x] Run `./setup-jdk.sh` — installs **two** JDKs into `~/mc-build-sandbox`, no sudo.

  **Two JDKs are required, and this is not optional.** Upstream's build plugins
  (`com.possible-triangle:core|common|fabric|neoforge`, currently `1.4.234`, from
  `maven.muon.rip`) publish Gradle module metadata declaring
  `org.gradle.jvm.version >= 25`. Resolving the buildscript classpath on Java 21 fails with:

  > `Could not resolve com.possible-triangle:core:1.4.234.`
  > `Dependency requires at least JVM runtime version 25. This build uses a Java 21 JVM.`

  | JDK | Role |
  |---|---|
  | **25** | Runs Gradle. `JAVA_HOME` points here. |
  | **21** | Compile toolchain — Minecraft 1.21.1 targets Java 21. |

  Gradle does not auto-detect JDKs in a non-standard prefix, so the script declares both in
  `$GRADLE_USER_HOME/gradle.properties` via `org.gradle.java.installations.paths`.

  *Note the plugin version floats:* `settings.gradle.kts` pins `com.possible-triangle.helper`
  to `"1.4"`, which resolves to the newest `1.4.x`. Its JVM floor can rise again — if a future
  build fails the same way, the fix is another JDK bump in `setup-jdk.sh`, not a downgrade.
- [x] `git fetch --unshallow` the upstream clone — *done by the script*.
- [x] **Standalone repo, not a GitHub fork** — `NotUserFriendly/mapatlases-recut`, public.
      Deliberately not a fork: a GitHub fork would surface in upstream's fork list and
      notifications for a project that may not pan out. Upstream is a plain `upstream`
      remote instead, with its **push URL disabled** so nothing can go the wrong way.
      Working copy at `~/workingdir/mapatlases-recut`; the pristine upstream clone
      stays at `~/workingdir/gits/mapatlases-neoforge` for cutting PR branches.
- [x] Clean `./gradlew build` on the **unmodified** tree, both loaders. *(1m32s, green.)*
- [x] Identity set **before the first commit** (`cd500bd`) — renaming later would conflict
      every cherry-pick:
  - mod id `map_atlases_recut`, name `Map Atlases Recut`, version `1.21.1-0.1.0`
  - package `pepjebs.mapatlases` → `notuserfriendly.mapatlasesrecut`
  - resource namespaces, mixin configs, accesswidener
  - **mixin `@Unique` prefix `mapatlases$` → `mapatlasesrecut$`** — required, because T3.6
    needs both mods loaded at once and identical `@Unique` members in the same target class
    would collide
  - upstream CurseForge/Modrinth project ids **cleared**
  - `.gitignore`: root `/build/` was never ignored, and `../build/` pointed outside the repo
  - Rebuild after rename: green in 39s, both loaders
- [ ] **Add the stored-energy integer to the Waystone data model reservation now** — free
  today, save-breaking in Phase 4. (D26)

**Gate:** the mod builds and boots into a world before anything else starts.

---

## Phase 1 — Fork hygiene and upstream PRs

- [ ] **First commit: Paper Block (T1.4).** Registration, recipe, loot table, shears drop.
      Deliberately trivial — it proves the toolchain end to end and risks nothing.
- [x] Cut three branches from `upstream/1.21.1` (Fabric intact) and open PRs:
  - [x] **PR A — `EmptyMaps` merge duplication (T0.2)** → **#297 MERGED**, unchanged
  - [x] **PR B — duplicate map entries (T0.1)** → **#298 MERGED**, unchanged, closed issue #250
  - [x] **PR C — atlas stacking (T0.3)** → **#299 open**

**Worth offering on #299:** we have since found the cause that actually bites on a vanilla
server, and it is not the ids ordering that PR fixes. `EmptyMaps` holding `{VANILLA: 0}`
against a fresh `{}`, and `MapCollection` holding `{VANILLA: []}`, both block stacking and
both arise in ordinary play (`528ac11`). PR C's fix is still real, but a maintainer weighing
whether it fixes anything visible deserves to know about the other two. Either a comment on
the PR or a fourth PR.
- [ ] Strip `fabric/` from the fork branch **only after** the PRs are open.

### Testable now (pure JUnit, no game)

- [ ] `getBlockWidthFromScale(s) == 128 << s` for s ∈ 0..4
- [ ] Each coarse cell is tiled **exactly** by four next-finer cells — no gaps, no overlap
- [ ] `MapCollection.getCount()` equals the deduplicated map count, not the raw id count
- [ ] Merging two atlases with *n* empty maps each yields **one** atlas with 2*n*, or two with *n*
- [ ] Two atlases with identical coverage in different id order compare equal

---

## PILLAR I — Cartography

### 2.1 Correctness — **done**

- [x] Port PRs A/B/C onto the fork branch (`3abcbc3`, `9835737`, `3fa5a88`). Applied as
      path-rewritten patches, so authorship and messages match what upstream received.
- [x] Sort `ids` canonically (T0.3) — carried in PR C.
- [x] ~~Strip degenerate `SELECTED_SLICES` behind an `isLoaded` guard~~ → **dropped, it was a
      no-op.** `setSelectedSlice` already refuses to store a `(VANILLA, no height)` slice, so
      a vanilla-only atlas never writes the component, and the guard would have disabled the
      fix exactly when Supplementaries or Twilight Forest make slices real.
- [x] **Three identity bugs blocking stacking** (`528ac11`, `085b52c`). `EmptyMaps`
      `{VANILLA: 0}` vs `{}` and `MapCollection` `{VANILLA: []}` vs `{}` are the
      vanilla-reachable ones and explain the originally reported failure. All fixed by
      normalising `equals`, since `PatchedDataComponentMap.set` drops the patch entry
      once a value matches the prototype.
- [x] **And the SELECTED_SLICES one** (`085b52c`): `SelectedSlices.removeAndAssigns` stored an
      *empty* component instead of removing it. The atlas's only default components are
      `EMPTY_MAPS` and `MAP_COLLECTION`, so an emptied `SELECTED_SLICES` leaves a data patch a
      fresh atlas lacks, and the two stop stacking. Needs no mod guard. Not sent upstream
      since it surfaced after the PRs went out; worth offering later.

### 2.2 Performance — **implemented, awaiting the gate** (`c4b8f95`, `4e8a…`)

- [x] Track block changes per chunk — `LevelChunkMixin` stamps the game time on
      `setBlockState` through the `ChunkChangeStamp` duck interface.
- [x] Skip the scan entirely when a map has no blank pixels and nothing near the player
      has changed since it was last scanned. Both schedulers honour it.
- [x] `ChunkChangeIndex` queries the 16×16 chunks around the player, computed **lazily**
      so a player filling in new territory never pays for it.
- [x] Config off-switch `skip_unchanged_maps`, default on.
- [x] **`map_updates_per_tick` default raised 1 → 10.** The scan *ceiling* was the real limit
      on responsiveness, not wasted work. Skipping is what makes the higher ceiling safe: a
      raised ceiling costs nothing where the ground is already painted, so the two changes
      are complements rather than alternatives. **Untested on a dedicated server** — cost
      multiplies by player count, and `multithreaded_update` defaults to single player only,
      so server scans run on the server thread.
- [x] Counters behind `debug_map_updates`, logging performed/skipped every 30s.
- [ ] ~~Rescan only *pixels* covering dirty chunks~~ — deferred. Skipping whole scans is
      the large win; per-pixel granularity means rewriting the scan loop itself and only
      helps a map that is partly dirty, which is the rarer case.

**Correction recorded in DESIGN.md §5.6:** one `MapItem.update` costs **4,096** column
samples, not 65,536 — the strip loop processes one x-strip in sixteen, so 65,536 is the
cost of a full 16-step sweep. The conclusion is unchanged; the waste is that the sweep
repeats forever over unchanged ground.

### Verified

- [x] Mixin applies at runtime — `./verify-boot.sh` boots a headless server and checks
      for `Done (`. Mixins fail at *runtime*, so a compile is not evidence.
- [ ] Stationary player and mapped-terrain crossing cost ≈ 0 — **needs the gate below**

### ✅ PLAYTEST GATE 1 — *Profile* — **PASSED 2026-09-01**

Measured in game with `log_scan_stats`, not a profiler. A teleporter ring gave a
repeatable loop so the same ground could be flown twice.

| Case | Scans / 10s | Opportunities skipped | Region already painted |
|---|---|---|---|
| Parked, settled | **9** (was 20) | 55% | 95% |
| Flying **painted** ground | 18–85 | **65–90%** | 83–94% |
| Flying painted ground, fastest segment | 176 | 52% | 53% |
| Flying **new** ground | 111–187 | ~5% | 61–79% |

Best confirmed-motion window: 171 avoided against 18 performed, roughly **700,000 column
samples avoided per 10s**.

**Report timestamps are window ends, not starts** — a row labelled 11:47:52 covers
11:47:42 to 11:47:52. A window showing 96% was discarded from the figures above because it
straddled the operator stopping, so part of it was parked. `computeUpdateRate` reads the
position delta each tick with no smoothing, so the rate collapses to 0.1 the instant a
player stops; a decay across several windows means the stop fell inside one, not that the
rate ramps down.

**Reading the numbers later:** `considered` is polls x maps-in-view, and the poll rate is the
speed-driven accumulator, so it doubles as a movement proxy. Every parked window reads
exactly 180 (20 polls x 9 maps at 0.1/tick); the 96% window read 1881, about ten times that,
so it was recorded in motion rather than after parking.

**Skip rate degrades with speed.** The fastest window (1.8 polls/tick) skipped 52% while
moderate ones skipped 65-90%. Faster travel crosses more pixel boundaries per second and
sweeps the 48 block window over more chunks. Noisy, since paintedness varies around a loop,
but directional. **Worth remembering for Pillar II:** the Thousand League Boots at 80 m/s
will see the least benefit of anything, so the feature that makes travel fast is also the one
that makes map upkeep expensive.

**The controlled loop mattered.** An earlier uncontrolled flight over the same edge showed
only ~5% skipped and nearly led to the wrong conclusion, because that ground had not
actually been painted yet. Same speeds, same path; the only difference was whether the
terrain was already mapped. *Credit to the operator for catching the confound.*

**Answers to the decisions this gate exists to settle:**

- **D2 — layers, confirmed.** A coarse layer settles quickly once painted, because at coarse
  scales the reach circle is small relative to the map, so it satisfies the painted test far
  sooner than a fine one. The marginal cost of a second layer is therefore well below 2x,
  and layers are affordable without a custom tile format.
- **§5.3's sample-density cap is not needed for CPU reasons.** The invasive mixin that
  redirects vanilla's inner sampling loop can stay unbuilt. *Note this does not close §5.3
  entirely: the scale-dependent range multiplier was about how fast a coarse layer **fills**,
  which is a gameplay question and still open.*
- **`map_updates_per_tick` headroom exists** but was not needed to reach these numbers.

**What this is not.** Not the "scan each chunk once" ideal. Vanilla's scan is player-centred
and monolithic, so it cannot be aimed at just the dirty part, and in a living world something
is always changing somewhere. What landed is throttling: a painted map refreshes on an
interval, and anything within 48 blocks still updates immediately.

### 2.3 Layered atlas — **built, needs judging**

- [x] `MapCollection` holds several scales; the `d.scale != scale` rejection is gone
      (`e04fe89`). **No codec change needed** — scale was never serialised.
- [x] `selectBest` walks finest → coarsest; `selectAtScale` asks about one layer.
- [x] `MapGridKey.scale()` recovers a key's own scale, so creation fills the right layer.
- [x] One `MapsNeighborhood` per layer; `layersToMaintain` decides which (`935dfeb`).
- [x] `drawAtlas` composites coarsest-first, positioned from each map's **world centre** so
      layers align despite different cell sizes (`5643c4e`).
- [x] Order forced by `endBatch()` between layers, not depth (`30f2d0c`).
- [x] `MapAtlasesHUD` and `MapWidget` updated; hover uses `selectBest`.
- [x] Per-scale toggles `draw_layer_scale_0..4`, plus `draw_layer_overlay` reserved (`12c55e3`).
- [x] Separator layer chosen by zoom, and empty cells gridded (`de10d75`).
- [x] Crafted scale removed, so any layer set is legal.
- [ ] Scale-dependent `map_range_multiplier` — **see the note below before building this.**
- [ ] Explorer/treasure maps composite free-form as their own quads (§5.5).
- [ ] Toggles as UI checkboxes with an eye symbol, rather than config lines (D34 warning too).

**§5.3's fill rate may have solved itself.** The whole argument for a scale-dependent range was
that a coarse cell takes 64x more travel to fill than a fine one. Raising
`map_updates_per_tick` from 1 to 10 raises the ceiling on *every* layer, so the coarse layer
now fills roughly ten times faster than when the patchiness was observed. **Look at it again
before building the invasive sample-density mixin** — it may already be adequate, and that
mixin is the most invasive thing left in Pillar I.

### 2.4 Paper economy

- [ ] Paper ladder 8/16/24/32/40 per cell by scale (T1.1)
- [ ] **Decide D31 first: does the atlas store paper at all?** If not (recommended), the
      `EMPTY_MAPS` component is deleted rather than reworked, which makes the stacking bug
      class impossible instead of fixed, and `pity_activation_map_count` goes with it.
      Everything below changes shape depending on this answer, so settle it before starting.
  - [ ] Draw from inventory on demand, smallest denomination first, breaking down one larger
        unit at a time so change stays bounded
  - [ ] **Red outline on the minimap when no paper is found** — glanceable, persistent, and
        cheap; forgetting is a UI problem and this is the UI answer
  - [ ] Optional: draw from sub-inventories where a mod provides them (Sophisticated
        Backpacks first), behind `isLoaded`
  - [ ] Rehome the loan (T1.3) as its own small component
- [ ] Paper Sheaf ×9 — **both** entry points (cartography table *and* `MapAtlasesAddRecipe`)
- [ ] Paper Block as refuel source, 72 paper
- [ ] Empty-map debt (T1.3): `setAndAssign` for the debt, clamp removed from the repayment
      path, merge/duplicate refused while in debt

### Testable

- [ ] Cost table matches the ladder at every scale
- [ ] Partial repayment does **not** erase a debt
- [ ] Merging two −1 atlases cannot launder to 0
- [ ] Sheaf and paper give identical results per unit of paper

### 2.5 Atlas Cutter, dissolve, absorption

**Two blocks, split by what they move: the Surveyor moves knowledge, the Cutter moves items.**

- [ ] **Atlas Surveyor (T3.1a)** — the shared repository. No item slots.
  - [ ] Holds a `MapCollection`; right-click holding an atlas syncs both ways in one gesture
  - [ ] Takes everything free; gives back only what the atlas's empty-map pool affords,
        nearest-first. **This paper rule is what keeps T1.1 intact** — it sells the walking,
        not the paper
  - [ ] Dedupe silently on the way in, reusing T0.1
  - [ ] Absorb an upstream Map Atlases atlas through the same gesture (T3.6)
  - [ ] Lockable (D29)
  - [ ] Optional read-only coverage view when empty-handed
- [ ] **Atlas Cutter (T3.1b)** — the workbench. Slot an atlas and change it.
  - [ ] Withdraw individual maps as items
  - [ ] Universal paper sink, datapack-driven, byproducts returned
  - [ ] Curse removal, merge/clone, manual dedupe
  - [ ] Dissolve (T3.2), the uninstall path

*Build the Surveyor first.* It is the cooperative core, it needs no screen, and the Cutter's
paper sink only matters once there is something to spend paper on.
- [ ] Universal paper sink — datapack table of item → (paper, byproducts); book returns leather
- [ ] Curse removal (it cannot reach a Binding-locked atlas — that asymmetry is intended)
- [ ] **Dissolve** (T3.2) — the uninstall path. Complete and lossless; may be slow and gated
- [ ] **Absorb upstream Map Atlases atlases** (T3.6) via
      `BuiltInRegistries.DATA_COMPONENT_TYPE.get("map_atlases:map_collection")` → codec → NBT.
      **No compile-time dependency, no mixin.**

### Testable

- [ ] Dump→load round-trip loses no maps and no ids
- [ ] Dissolve of an *n*-map atlas yields exactly *n* vanilla filled maps
- [ ] Foreign-NBT parse against a captured sample of upstream's component

### 2.6 Enchantability

- [ ] Enchantment value **1** (book tier)
- [ ] Register **our own** paper-thrift enchantment (Mending and Unbreaking share
      `#minecraft:enchantable/durability`, so we take neither — D25)
- [ ] Join `sharp_weapon`, `fire_aspect`, `sword`, `vanishing`, `equippable`
- [ ] Curse of Binding interacts with the inherited Curios `hands` slot

### 2.7 Landmark pinning — *and third-party Waystones support*

- [ ] Datapack registry of notable blocks → auto-pin + one-off chime on discovery
- [ ] Phase 1 detection: **block entities only** (spawner, trial spawner, vault, beacon,
      conduit, bell) — walk the BE maps of ~9 chunks every N ticks
- [ ] Attach the marker at the **landmark's** position, never the player's (`MapAtlasItem:150`
      does the wrong thing for banners)
- [ ] **Third-party Waystones as one datapack entry**, not an integration. This is the
      interface rule: the map mod works with the waypoints mod people already have.
- [ ] Death waypoint

### Testable

- [ ] Marker resolves non-null iff within ±64 blocks of its map's centre
- [ ] Chime fires once on entering range, again only after leaving and returning

### 🚦 PLAYTEST GATE 2 — **PILLAR I COMPLETE**

*The map mod must be shippable here, on its own, with no leyline content whatsoever.*

- [ ] Craft a scale-2 atlas, explore, confirm layers composite correctly at every zoom
- [ ] Coarse layer fills visibly faster than fine
- [ ] Paper economy feels earned, not punitive, over a full session
- [ ] Atlas Cutter workflow: explore → dump → second player loads → they get an updated atlas
- [ ] Absorb an upstream atlas from a real save
- [ ] Waystones (third-party) pin correctly with the mod installed, and nothing breaks without it
- [ ] Dissolve, then uninstall the mod, and confirm the maps still work

---

## PILLAR II — Leyline

*Nothing in this pillar may require Pillar III.*

### 3.1 Tuned Iron

- [ ] Untuned Iron: iron block + redstone block + ender pearl
- [ ] Dual tuning path — **2000 to a marked point, or 3200 free-form** (§T3.5)
- [ ] Both counters run simultaneously; reaching the mark short-circuits the remainder
- [ ] On-foot distance only (`walk_one_cm`, `sprint_one_cm`) — kills vehicle and AFK farms (D22)
- [ ] One at a time; progress on the item stack
- [ ] Tuned Iron → 9 Tuned Coin → smelt → Tuned Dust

### Testable

- [ ] Boat, minecart, horse and elytra distance contribute **nothing**
- [ ] A second Untuned Iron in inventory accrues nothing
- [ ] Abandoning the mark banks progress; no path is ever punished
- [ ] Coin value lands in 222–355 blocks (2000/9, 3200/9)

### 3.2 Boots

- [ ] Base: iron boots + 2 diamonds. Variants: poison (pufferfish/2 spider eyes/2 prismarine),
      wither (wither skeleton skull), blindness (3 echo shards), fire (ancient debris)
- [ ] Tier 2: **smithing table** + netherite upgrade template + 1 netherite ingot
- [ ] Attributes: step height **flat 10**, knockback resistance 1.0, fall immunity, leaves passage
- [ ] Speed ladder by the Leyline Atlas's scale (33 → ~80 m/s)
- [ ] **Decelerate at the frontier** — cap speed by whether chunks ahead are loaded
- [ ] Handle vanilla's "moved too quickly!" as elytra/riptide are handled
- [ ] Water and lava walking **at full ramp only**, never from a standing start; lava ignites
- [ ] Blocking-effect datapack tag (hunger, poison, and each Tier-1 element). **No Nausea, no
      Darkness** — client-side disableable (D3e)

### 3.3 Hunger debt

- [ ] Debt accrues per metre × scale multiplier (4.0 → 0.9)
- [ ] Deceleration = **our own ramp tier reaching zero**, never a velocity threshold (D3d)
- [ ] Flat unavoidable hit = `min(0.5 × debt, 10)`
- [ ] Remainder as Hunger, target **5 s**; clamp Str at 255 and **stretch time to conserve
      `Str × Time`** (D3g)
- [ ] HUD gauge: fills with debt, **sparks at full ramp** (D13)
- [ ] Settle debt on logout

### Testable — *the richest math in the project*

- [ ] `Sat = 0.013 × Str × Time(s)` against the measured table (§8): Str 40→5.25, 75→9.5, 100→13.0
- [ ] Flat hit is 50% of debt below 20 and 25% at 40
- [ ] `Str × Time` conserved under the 255 clamp: debt 40 → Str 255, ~9.1 s
- [ ] Scale-0 over 1600 m costs ~36 points vs sprinting's 40
- [ ] Speed ladder and debt ladder stay proportional at every scale

### 3.4 Waystones

- [ ] Waystone Inscription from a Leyline Atlas; right-click onto stone
- [ ] Inscribed block **stores and restores the original `BlockState`** — makes modded stone work
- [ ] `#map_atlases_recut:inscribable` tag
- [ ] **A ring is the unit.** Single-block variant: stand *on* it
- [ ] Links: **2 local (V, X)** planar non-crossing via Delaunay neighbours; **3 dimensional
      (F, K, N)** unconstrained
- [ ] **Adding a stone only adds links. Destroying one shuffles its orphans.**
- [ ] Dimensional links rolled once and **persisted**
- [ ] Breaking a built Node destroys the atlas
- [ ] Natural leylines: worldgen, coarsest scale, a dozen local links, a couple of dimensional
      ones; some generate as rings

### Testable

- [ ] **No two local links ever intersect**, over randomised point sets
- [ ] Local degree ≤ 2; links come from Delaunay neighbours
- [ ] Adding a stone never mutates an existing link
- [ ] Ring detection: *n* stones within radius resolve to one node with the union of their links

### 3.5 Travel

- [ ] Standing in the centre opens it — gated on being at rest (D24)
- [ ] Glyphs overlay at true bearing for local; **1:30 / 4:30 / 7:30 / 10:30** for dimensional
- [ ] Aim and hold forward; steering takes the outgoing line nearest the cursor
- [ ] Back ramps down; you always come to rest at a stone
- [ ] Intangible, invulnerable, purple haze; **no chunk loading — position is abstract**
- [ ] **Purgatory**: one transition in, one out, however many dimensions crossed
- [ ] Jump equalises the two stones; **remainder 0 or 1**
- [ ] Player pockets the **first** remainder and drops it at their destination
- [ ] Tier-2 absorbs 1 into durability, then XP when already repaired
- [ ] Neighbour flow: A's and B's other links equalise **silently**

### Testable

- [ ] Equalisation: (20, 4) → (12, 12) r0; (21, 4) → (12, 12) r1; (10, 9) → (9, 9) r1
- [ ] Parity: same-charge stones never discharge
- [ ] Carried token is diverted from the event pool, never added to it
- [ ] Secondary equalisation generates zero events
- [ ] Glyph route resolution is deterministic; a reversed string is **not** assumed to retrace

### 3.6 Leyline Map

- [ ] A layer in §5's sense, over existing terrain
- [ ] One Tuned Dust reveals one Waystone's links, direction and name
- [ ] **Draws arrows, not lines** — links are asymmetric and a symmetric chart is a trap

### 🚦 PLAYTEST GATE 3 — **PILLAR II COMPLETE**

*Travel must be complete and fun with charge entirely disabled.*

- [ ] Tune an iron the hard way, then the marked way. Both feel worth doing
- [ ] Run 2000 m in Tier-1 boots and arrive: is the debt rhythm right? Is the gauge legible?
- [ ] Long straight run vs. stop-start burst — confirm the first is clearly better
- [ ] Cross an ocean at full ramp; stop halfway and confirm being stranded is *fair*
- [ ] Build a ring in a courtyard. Does it read as architecture?
- [ ] Ride a 5-hop route without stopping; steering must feel like driving, not menu-clicking
- [ ] Cross three dimensions — **one load in, one out**
- [ ] Hand a glyph string to a second player and confirm they can follow it blind
- [ ] Get deliberately stranded by a one-way link. Is walking home a setback or a punishment?

---

## PILLAR III — Charge

*The overcoat. Everything below must be removable without breaking Pillar II.*

### 4.1 Model

- [ ] Charge as a **small integer** in the Waystone's own data — **leyline charge, never FE**
- [ ] Idle accumulation (the slow baseline that seeds from zero)
- [ ] Effect table: datapack `{effect, cost, weight}`, **including beneficial entries** and the
      `+6 distributed` amplifier
- [ ] Soft cap 12: **3+ links shed to neighbours, 1–2 links vent locally**
- [ ] Cascade with visited-marking so cyclic networks terminate
- [ ] Ring tolerance: difference of 1 reads as equal; effect magnitude scales with ring size
- [ ] Deferred events — resolve on chunk load, so a calamity manifests when found

### 4.2 Runaway and collisions

- [ ] Overbuy roll, `p = min(0.9, charge/50)`, **one roll per token spent (`p^cost`)**
- [ ] Borrowing down the line, with the same visited-marking
- [ ] **A stone with a dedicated sink never overbuys** — sinks are insurance
- [ ] Collision window reuses the bystander countdown; remainders inside it pool

### Testable — *statistical, over ≥100k trials*

- [ ] `p^cost` mean dump: ~2.6 at charge 13, ~6.1 at 40, ~11.0 at 60
- [ ] Tail survives: a charge-60 stone can still dump >100 tokens, rarely
- [ ] A flat `p` is **rejected** — it makes the catastrophe mathematically unreachable
- [ ] Cascades always terminate on cyclic graphs
- [ ] Two pooled remainders buy one 2-cost event, not two 1-cost events

### 4.3 Sinks

- [ ] Producers: Growth, Molten, Faucet, Undead, Lure Stone
- [ ] Consumers: Reaper (leaves), Frost (fluids), Violent (entities)
- [ ] **Violent with no entities in range acts as no sink** — re-enabling overbuy
- [ ] **Lure with no animals summons up to 4 and fires a 1-cost event**
- [ ] Molten and Faucet convert **1 block per 1 energy** — rate-limited, not capped
- [ ] Buffers 1 / 5 / 10; a full buffer fires a 1-cost event locally
- [ ] Dispersal Device exposes `IEnergyStorage` — the converter, not the waystone

### Testable

- [ ] 1 energy = 1 block conversion, exactly
- [ ] Full buffer always vents; partial buffer always stores
- [ ] Undead+Violent and Lure+Violent are self-sustaining
- [ ] Removing Pillar III leaves Pillar II fully functional

### 🚦 PLAYTEST GATE 4 — **PILLAR III COMPLETE**

- [ ] Neglect a dead-end stone for a long session, then touch it. Is the vent *fun* or just cruel?
- [ ] Build a balanced hot network and confirm it is safe to travel
- [ ] Deliberately unbalance it and confirm the cascade is spectacular
- [ ] Run a 1-buffer chaos tap in a pit. Is farming the table entertaining?
- [ ] Molten+Frost and Faucet+Frost, over an hour — is the footprint acceptable or alarming?
- [ ] Two players collide at a hub. Confirm it is legibly *their* fault
- [ ] Blast a 10-stone chain, come back a week later, and enjoy the wreckage
- [ ] Disable charge in config and confirm Pillar II is untouched

---

## Deferred

Not scheduled. Revisit after Gate 4.

- **Drawn Layer** (T3.4) — cheap under layers, but the input half is its own feature
- **Soulbound** (T3.8) — nothing to build until a provider mod is chosen
- **Client-side high-detail overlay** (§5.6) — the only complementary use for a custom tile
  format; revisit once layers land and we can see what detail is actually missing
- **Leyline Atlas wear** (D23) — would give Tuned Iron a recurring sink; plausibly overtuning

---

## The single tuning pass

Deliberately deferred as one job against a real save (D8a). Numbers are cheap to change once
the mod runs, and tuning them together beats guessing them apart.

Speed ladder · hunger debt rates · flat-hit ceiling · Tier-1 lockout · Tuned Iron distances ·
paper-thrift curve · boot recipe costs · idle accumulation · soft cap · overbuy curve ·
sink draw rates · ring tolerance · proximity/ring radius · flat hunger per jump · jump duration

**Verify against decompiled source at the same time:** sprint exhaustion 0.1/m ·
`Attributes.STEP_HEIGHT` max 10 · the Str-255 ceiling · atlas melee damage (D15).
