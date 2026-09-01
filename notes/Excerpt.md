# Atlas Reborn — design

A reworked Map Atlases: a layered, multi-resolution atlas plus the surrounding
features that make it worth carrying. Split out of the Aeromons pack repo on
2026-08-29; the analysis below was written 2026-08-23.

Depends on: neoforge, minecraft 1.21.1, map_atlases, moonlight, waystones, balm.

**Everything below was read from the actual jars and upstream sources, not from
mod pages.** Citations are to the classes so a future session can re-check.

Status: **only feature 1 is built.** It ships as `atlaspatches` (a separate mixin
mod, live in production since 2026-08-23) — see `patches/map-atlases/` in the
Aeromons repo for the upstream report and patch. Everything else here is design.

---

## Scope

| # | Feature | Where it lives |
|---|---|---|
| 1 | Map Atlases duplicate-entry fix | mod (mixin) — see `../patches/map-atlases/` |
| 2 | Activating a waystone pins it to the held atlas | mod (event listener) + datapack |
| 3 | Paper sheaf counts as 9 empty maps | KubeJS (item + recipe) + mod (mixin) |
| 4 | Cartography table atlas handling, rewritten | mod (mixin) |
| 5 | Waystone proximity notification | mod (server tick) |
| 6 | Empty-map debt ("the loan") | mod (mixin) |
| 7 | Dissolve an atlas back into its maps | mod (cartography table) |

Explicitly **out of scope**: pin renaming, recolouring, and any other atlas screen
UI work. Dropped 2026-08-23 — it is client-side surgery inside another mod's
screen, the most breakage-prone kind of mixin, and the complexity was growing
faster than the value.

---

## 1. Map Atlases duplicate-entry fix

Already written and proven as a source patch (`../patches/map-atlases/`), deployed
as a whole rebuilt jar for a live trial on 2026-08-23 and verified working. Port
the same three changes to mixins so the pack can ship the official jar again:

- `MapCollection#populateInDataStructure` — record duplicates instead of logging
  each at ERROR
- `MapCollection#addAndAssigns` — filter ids already held or whose grid cell is
  covered; return `this` when nothing is left to add
- `MapAtlasItem#getMaps` — call the repair on first initialize

`ids`, `maps` and `scale` are `protected` so `@Shadow` reaches them; the new
duplicate-tracking state is a `@Unique` field with a duck interface for the repair
method. Send the source patch upstream in parallel — if it lands, delete this.

## 2. Waystone activation pins the atlas

`WaystoneActivatedEvent(Player, Waystone)` is a Balm event
(`net.blay09.mods.waystones.api.event`). Balm is already a pack dependency.

- `Waystone#getEffectiveName()` — real display name, generated names included
- `Waystone#getPos()`, `#getDimension()`, `#getWaystoneUid()`
- `MapAtlasesAccessUtils.getAtlasFromPlayerByConfig(player)` already honours the
  `activation_locations` config, which is `HOTBAR_AND_HANDS` here, and Curios
- `MoonlightCompat.addDecoration(level, data, pos, id, name)` places it

No mixin. Roughly 30 lines, and immune to either mod's internals changing.

The datapack half is one file,
`data/aeromons/moonlight/map_marker/waystone.json`. `MLJsonMapDecorationType` is
data-driven with `target` (a vanilla `RuleTest`), `name` and `defaultMapColor`.
Giving the type a `target` matching `waystones:waystone` also makes the existing
pin keybind work on waystones as a fallback path — one file, both jobs. Needs a
sprite in the served resource pack.

Note the pin keybind is **N** on this server, not the B the Map Atlases page
documents; it was rebound during the keybind-conflict pass.

## 3. Paper sheaf

KubeJS: a `sheaf` item and a shaped `9 paper -> sheaf` recipe.

Mod: `MapAtlasesAccessUtils.isValidEmptyMapIngredient` and `getMapCountToAdd` are
hardcoded to `Items.PAPER` and count `bottomItem.getCount()` at 1:1. Two small
mixins to accept the sheaf at 9x.

There are **two** entry points and both need it or the sheaf behaves
inconsistently: the cartography table (`CartographyTableMenuMixin`, "add empty"
branch) and the crafting recipe (`MapAtlasesAddRecipe`). Note that if #4 rewrites
the cartography table path anyway, the sheaf handling there comes for free and
only the crafting path needs the mixin.

## 4. Cartography table, rewritten

**The hook.** Map Atlases injects into `CartographyTableMenu#setupResultSlot` at
HEAD and cancels, so racing its injector is fragile. `slotsChanged` calls
`setupResultSlot` (verified in the 1.21.1 bytecode, offset 85), so injecting at
RETURN of `slotsChanged` always runs, after the cancelled method and outside its
scope. From there the result slot can be corrected or replaced wholesale. No
ordering games.

**The duplication bug.** The merge branch sums both atlases' empty maps and then
calls `result.grow(1)`, so two atlases with 10 empty each produce two atlases with
20 each — 20 in, 40 out. Split instead of doubling.

**Why atlases stack unpredictably.** The item is `stacksTo(16)`, so stacking needs
every component equal: `MAP_COLLECTION`, `EMPTY_MAPS`, `SELECTED_SLICES`,
`HEIGHT`, `LOCKED`. Two causes of identical-looking atlases refusing to stack:

- `MapCollection#equals` compares `ids`, an `EnumMap<MapType, List<MapId>>`. List
  equality is **order-sensitive**, so two atlases covering exactly the same
  territory do not stack unless their ids are in the same order.
- `SELECTED_SLICES` remembers the dimension and slice last viewed. Open one atlas
  and not the other and they stop stacking.

Sorting `ids` is safe once the dedup guarantees one entry per cell, and fixes the
first cause.

The second turns out to be free to fix here. `SelectedSlices` is
`Map<ResourceKey<Level>, Slice>` and `Slice` is `(MapType, Optional<Integer> height,
dimension)` — **no x/z at all**, so it has nothing to do with where the atlas opens.
And the other `MapType` values come from mods this pack does not have: MAGIC, MAZE
and ORE_MAZE are Twilight Forest, SLICED is Supplementaries. So on this server a
Slice is always `(VANILLA, empty, dim)` and the component degenerates to "which
dimensions has this atlas been carried through" — information nothing reads.

Stripping degenerate entries normalises it to absent and lets identical atlases
stack. **Guard it on `!isLoaded("supplementaries") && !isLoaded("twilightforest")`**
so it disables itself if either is ever added, rather than silently discarding real
slice state.

**The lectern retains no view state** (checked 2026-08-23 after a challenge). The
atlas has exactly five components — `MAP_COLLECTION`, `LOCKED`, `EMPTY_MAPS`,
`HEIGHT`, `SELECTED_SLICES` — and none holds an x/z position, so there is nowhere
for "where it was opened to" to live. `LecternBlockEntityMixin` only stores the
atlas as the lectern's `book`. The lectern's whole effect is the renderer swapping
in an atlas texture (`entity/lectern_atlas`, `_nether`, `_end`, `_unknown`), the
screen using `lectern_scale` for `globalScale`, and a take-book button.

Note the author's own TODO at `MapAtlasesMod.java:119` lists `//lectern marker` as
unimplemented — that is "show the lectern on the map", and is probably what the mod
page advertises. The same block lists **`//auto waystone marker`**, so feature #2 is
something the maintainer already wants; worth offering upstream with the dedup fix.

Also worth knowing: the atlas screen already opens centred on the player —
`AtlasOverviewScreen:127` calls `currentMaps.getClosest(player, selectedSlice)`.
And no lectern class reads `getSelectedSlice` or `SELECTED_SLICES`, so lectern
behaviour does not depend on it.

## 5. Waystone proximity notification

Server side, so no client mod is needed and a stale client still works:
`WaystonesAPI.getAllWaystones(server)` and `isWaystoneActivated(player, waystone)`.
No block entity scanning.

- Range: **64 blocks**
- Sound: **`block.bell.resonate`** — verified present in 1.21.1. Chosen because it
  never plays on its own, so it cannot be confused with anything, and it is
  notification-shaped without `bell.use`'s raid and villager connotations
- On detection: add the pin and play the sound once. No directional or
  level-changing audio — a single notification is the whole mechanic, and reading
  your own map is the intended challenge

### Settled 2026-08-23

**Memory is per player, not per atlas.** The chime is the player sensing the
waystone, so it belongs to the player. A waystone chimes when it *enters* range
while still unactivated, and is forgotten when it leaves — so walking past gives
one chime, standing still gives one chime, and coming back later gives one more.
Once the player activates it, it never chimes again. Held in memory rather than
persisted; a relog next to an unactivated waystone will chime again, which is
consistent with sensing.

**The pin carries the waystone's real name.** They are generated nonsense names,
so they give nothing away.

**Pins cannot go in blank space.** A marker lives on one specific
`MapItemSavedData`, and `MLMapMarker#createDecorationFromMarker` returns null when
the marker is more than ±64 blocks from that map's centre — i.e. outside the map's
own square. There is no arbitrary-coordinate waypoint.

In practice this rarely bites: at 64 blocks the waystone is almost always inside
the 3×3 neighbourhood the atlas fills automatically (384 blocks across). It only
fails when the atlas is out of empty maps and cannot map the cell.

Resolution needs no extra state: chime always (the sensing happened), write the pin
if a map covers the spot. If not, the recurrence rule above *is* the retry — the
player gets both next time they come back with paper.

**Implementation trap.** Map Atlases' own banner handling attaches the marker to the
map at the **player's** position (`MapAtlasItem:150`, `MapGridKey.atEntityPosition`).
Copying that for waystones would be wrong: at up to 64 blocks the waystone can fall
in a different map, the decoration would resolve to null, and the pin would silently
never render. Select the map by the **waystone's** position.


---

## 6. Empty-map debt ("the loan")

Pinning a waystone into a cell the atlas has not mapped is allowed to create that
map on credit, taking the empty-map count to **-1**. Nothing else may borrow, and
the floor is -1 — not 8 or 16, because the arithmetic below only stays simple
while a debt is a single unit.

The debt enforces itself for free: `maybeCreateNewMapEntry` gates on
`emptyCount > 0`, so a negative balance already stops normal exploration from
mapping anything until it is repaid.

Two obstacles, both in `EmptyMaps`:

- **Both `addAndAssigns` overloads clamp** with `Math.max(0, current + amount)`.
  That blocks going negative at all, and worse, makes partial repayment *erase*
  the debt. Set the debt through `setAndAssign`, which does not clamp, and mixin
  the clamp out of the repayment path so paper sums honestly.
- **The clamp is also the laundering hole.** Merging two -1 atlases yields
  `Math.max(0, -1 + -1)` = 0 and the debt vanishes. Combining a pile of
  indebted atlases into one free map is the real abuse, not discarding them.

**Rule: an atlas carrying a debt cannot be merged or duplicated.** The cartography
table accepts only the add-empty-maps path until the balance is back to zero.
Simpler than reasoning about how debts combine, trivial to enforce since #4
rewrites that path anyway, and easy to explain: pay it off first.

## Architectural constraints of Map Atlases

Verified 2026-08-23. These bound what can be built on top of it without a fork.

- **One scale per atlas.** `populateInDataStructure` rejects any map whose
  `scale != collection.scale`, silently. Stitching requires uniform scale.
- **Grid alignment is mandatory.** `MapGridKey.at(scale, slice, x, z)` snaps to a
  grid, and `select` is keyed on the cell. World-generated maps — shipwreck,
  buried treasure, explorer — are centred on their structure at arbitrary
  coordinates and will never align, so they cannot be stitched in. Wrong scale too.
- **One map per cell.** The selection model is `maps.get(key)`. Duplicates have no
  defined render order, which is why they are rejected rather than kept.
- **An atlas's scale is fixed at craft time, forever.** `MapAtlasCreateRecipe`
  builds a blank atlas and adds the crafted-in filled map; `populateInDataStructure`
  then sets `scale` from that first map, and every map the atlas creates afterwards
  uses `maps.getScale()`. There is no way to change it later. Crafting with a
  scale-0 map — the default if you just make a map and use it — gives the most
  detailed and least practical world atlas: 1 block per pixel, 128 blocks per cell.
  Crafting with a scale-2 map covers 16x the area per cell, which would have turned
  this server's 331-map atlas into roughly 21.
- **Vanilla zoom-out does not keep data.** `MapItemSavedData.scaled()` calls
  `createFresh(...)` — a blank map at the higher scale. The explored area is lost
  and must be re-walked. Any "compress the atlas" feature has to downsample the
  `colors` byte arrays itself: four maps at scale N, each halved to 64x64, composited
  into the quadrants of one scale N+1 map. Straightforward but ours to write and
  lossy. 331 maps becomes 83, then 21, then 6, then 2.

## 7. Dissolving an atlas — UNDER REVIEW

Parked 2026-08-23. The operator's own question was whether dissolve solves the real
problem or is a workaround for multiple atlases feeling clunky to manage. Resolve
that before building. Design so far, if it survives:

Shears are already the atlas's cutting tool — `PlatStuff.isShear` in the
cartography table extracts the single *selected* map (`MapAtlasesCutExistingRecipe`
completes it). Any dissolve must preserve or relocate that behaviour.

**Two-stage shear, which doubles as an accident guard.** Shearing an atlas that
holds empty maps throws out paper equal to the empties. Shearing one with no
empties dissolves it. You cannot destroy a working atlas by accident, because the
first cut only refunds paper.

**The output does not fit anywhere.** Each map is a distinct item that stacks with
nothing, and a real atlas here holds 331 — roughly nine double chests. A crafting
recipe has one output slot, so this cannot be a crafting recipe. Do it in the
cartography table (#4 rewrites it anyway): empty atlas in the result slot, maps drop
as item entities. Gate behind a map-count ceiling.

Refuse to dissolve an atlas carrying a debt, consistent with #6.

Shears are already the atlas's cutting tool — `PlatStuff.isShear` in the
cartography table extracts the single *selected* map (`MapAtlasesCutExistingRecipe`
completes it). Dissolve is the same gesture applied to everything: return the empty
atlas and give back every map it holds.

**The output does not fit anywhere.** Each map is a distinct item that stacks with
nothing, and a real atlas here holds 331 of them — roughly nine double chests. A
crafting recipe has one output slot, so this cannot be a crafting recipe.

Do it in the cartography table, which #4 already rewrites: the empty atlas goes in
the result slot, and the maps drop as item entities at the table. Worth gating
behind a confirmation or a map-count ceiling, because dissolving a well-travelled
atlas will bury the floor.

Open: should dissolving refund the empty-map count, and what happens to an atlas
carrying a debt? Suggest refusing to dissolve while in debt, consistent with #6.

---

## Layered multi-resolution atlas — feasibility, 2026-08-23

Operator's proposal: an atlas auto-writes a coarse base layer (max zoom-out) and
composites finer layers on top where they exist — scale 2 over scale 4, and
world-generated maps (shipwreck, buried treasure) over both. Rendered as stacked
layers rather than one uniform grid.

**The geometry could not be better suited.** `MapGridKey.getBlockWidthFromScale`
is `128 * (1 << scale)`, and `at()` snaps a position to that width. So cells are
128 / 256 / 512 / 1024 / 2048 blocks wide and each coarse cell is tiled *exactly*
by four of the next finer ones. No fractional offsets, no seam blending, no
resampling — compositing is placing quads. This is the strongest argument for the
design.

**Explorer maps get easier, not harder.** Their being centred at arbitrary
coordinates is only a problem for a grid-stitching renderer. Once the renderer
composites free-form in world space, an arbitrarily-centred scale-1 treasure map
is just another quad. The grid-alignment complaint dissolves as a side effect.

**What has to be rewritten.** `MapCollection` is explicitly single-scale: a `scale`
byte, a hard `d.scale == scale` rejection, and `maps` keyed by cell alone. Layers
mean keying by (scale, cell) and a `select` that resolves a query to the best
available layer. That is a core rewrite of the class, not a mixin. The renderer is
the larger half — `AtlasOverviewScreen`, `MapWidget`, the HUD minimap and the
lectern render all assume one uniform grid.

**Scale does NOT make map updates more expensive.** An earlier draft of this
document claimed a scale-4 update costs 256x a scale-0 one. That was wrong.
`MapItem.update` computes its pixel scan radius as `k = 128 / (1 << scale)`
(bytecode offsets 77-83), so the radius shrinks by exactly the factor that
per-pixel sampling grows — each pixel samples a `(1<<scale)²` block of columns and
takes the majority colour via a `LinkedHashMultiset`. The product is constant:
every update scans roughly a 256x256 block region around the player for about 65k
column samples, at every scale. Vanilla normalised this deliberately.

**What is actually true of a coarse atlas: it fills slowly.** A scale-4 cell is
2048x2048 blocks and one update paints only the 256x256 around the player, so
completing a single coarse map takes roughly 64x more walking than a scale-0 one.
That is a gameplay characteristic, not a tick-time one.

A second-order note: at scale 4 the 3x3 neighbourhood spans 6144 blocks, so eight
of its nine maps are too far away for the current scan region to touch. The
`WeightedUpdateScheduler` weights by distance and drops the priority of maps with
no blank pixels left, so this mostly self-corrects, but it is mild waste.

**Cost of the layered design is therefore about 2x, not 256x** — a coarse base
layer plus a detail layer means maintaining two neighbourhoods instead of one.
Modest, and `map_updates_per_tick` absorbs it. Still worth a Spark profile before
committing, per CLAUDE.md, but it is not the blocker the earlier draft suggested.

The standing advice to craft a scale-2 atlas carries no per-update penalty either.
Its only cost is that each cell takes 16x more walking to fill.

**Cheaper intermediate.** Multi-scale *switching* instead of compositing — the
atlas holds several scale layers and zooming swaps which grid is stitched. The
renderer stays a uniform grid, which is most of the saving, and you still get one
atlas covering everything at several detail levels. Loses the fine-inset-on-coarse
look. Perhaps a third of the work.

### Making the coarse layer fill fast

The layered design's premise is a quick rough first pass and a slow sharp second
pass. As things stand it does not work, because the scan region is a fixed 256x256
blocks at every scale — coarse maps reveal world no faster than fine ones, they
just need fewer of them.

**The paper economy is already tiered correctly.** An empty map costs the same
regardless of the atlas's scale, so per *cell* coarse and fine cost the same — but
per unit of *world area* a scale-0 cell costs 256x what a scale-4 cell does. Detail
is already priced steeply. What is missing is only the time asymmetry.

**Extending the scan range for coarse maps is the fix, and the hook already exists.**
`MapItemMixin.mapAtlases$startFromZeroStepFix` already captures vanilla's `range`
local (`@Local(ordinal = 5) LocalIntRef`) and multiplies it by
`map_range_multiplier`. Make that multiplier a function of the map's scale instead
of a flat config value.

**It is not free.** Work goes as `range²`, so 4x the range is 16x the column
samples. The honest framing is that **CPU cost tracks world area revealed, not
detail** — so this buys discovery speed with tick time rather than with paper.

**The saving that pays for it: decouple sample density from scale.** Vanilla samples
`(1<<scale)²` columns per pixel and takes the majority colour — 256 columns per
pixel at scale 4, to produce one pixel of a deliberately coarse base layer. Capping
that at, say, 16 samples is a 16x saving, which buys 4x range at unchanged cost.
The colour gets slightly noisier, which for a base layer is invisible. More invasive
than the range tweak — it means redirecting the inner sampling loop rather than one
local — but it is what turns the layered design from "same speed, less detail" into
"fast and rough over slow and sharp".

### Paper economy — settled 2026-08-23

Verified vanilla costs: an empty map is **8 paper + 1 compass**
(`data/minecraft/recipe/map.json`, 3x3 paper around a compass). Zooming out is
8 paper per step at a crafting table (`crafting_special_mapextending`) or 1 paper
per step at a cartography table.

Today's atlas charges **1 paper per cell at any scale** because
`accept_paper_for_empty_maps` is on and the mod counts paper 1:1. That makes the
atlas 8-40x cheaper than mapping by hand, with no iron, while also being automatic
and stitched. Nobody would ever map manually. It also undercuts BRIEF's "exploration
should cost something" by a factor of eight.

**Decision: adopt vanilla's paper ladder, drop the compass.** A compass to found an
atlas makes sense once; iron and redstone per map does not. This is a deliberate
deviation, not an oversight.

| scale | paper/cell | cell | cells over the 6144² pregen | paper for full coverage |
|---|---|---|---|---|
| 0 | 8 | 128² | 2304 | 18,432 |
| 1 | 16 | 256² | 576 | 9,216 |
| 2 | 24 | 512² | 144 | 3,456 |
| 3 | 32 | 1024² | 36 | 1,152 |
| 4 | 40 | 2048² | 9 | **360** |

Coarse ends up ~51x cheaper per unit area than fine, rather than 256x. Full detail
everywhere becomes a mega-project; a coarse world overview costs 360 paper, which is
an afternoon of sugar cane.

**Note the cap interaction.** `max_map_count` is 512, and a scale-0 atlas covering
the pregen area needs 2304 cells — full detail is impossible in one atlas at any
price. Scale 2 needs 144 and fits comfortably. Coarser scales solve the cap and the
burn rate together; sheaves solve carrying capacity. All three of the operator's
complaints share one fix.

**Implementation note.** The empty-map pool must be counted in **paper**, not maps,
or scale pricing needs fractional map costs. `EmptyMaps` holds `Map<MapType,Integer>`;
reinterpreting that as paper units is a semantic change affecting the tooltip and the
loan. The loan cap of -1 should then mean "one map's worth at this atlas's scale",
i.e. -8 at scale 0 through -40 at scale 4, not -1 paper.

---

## Splitting into separate mods — decided 2026-08-23

`aeromons` started as a bridge to carry the Map Atlases fix until upstream took it,
and has accreted a currency, a shop system and two mods' worth of integration. That
is a monolith by accident. Split it along the dependency lines that already exist:

| Mod | Contents | Hard deps | Optional |
|---|---|---|---|
| **atlas-patches** | Map Atlases dedup fix, sheaf hooks, cartography rework, empty-map loan, dissolve | map_atlases | — |
| **waystone-atlas** | Activation pins the atlas, proximity chime | waystones, balm | map_atlases |
| **chits** | Emerald Chit, prize money, vending machines | — | cobblemon |

The boundaries are real, not cosmetic:

- **atlas-patches is the one with an expiry date.** Every piece of it is a fix to
  someone else's mod, and the dedup half is already headed upstream. It is also the
  piece most likely to become the fork. Keeping it separate means the fork does not
  drag a currency system along with it.
- **waystone-atlas sits between two mods** and should degrade rather than fail.
  Without Map Atlases the chime still works, it just has nowhere to put a pin.
- **chits depends on nothing.** It is playable in a pack with neither Cobblemon nor
  Map Atlases installed, which is the test of whether the seam is real.

### Making the seams work

Optional integration in NeoForge is a declared optional dependency plus
`ModList.get().isLoaded(id)` guards at the call sites. For mixins, which resolve
before that check would run, use a separate mixin config per integration behind an
`IMixinConfigPlugin` whose `shouldApplyMixin` returns the same check — about twenty
lines, and the standard pattern. Do not reach for `"required": false`; that hides
genuine breakage as well as absence, which is the opposite of the loud-failure
property that made the mixin route worth choosing.

---

## Where the shipping code lives

The duplicate-entry fix is built and deployed as **`atlaspatches`**, a standalone
NeoForge mixin mod:

- `~/workingdir/atlas-patches/` — Gradle project. `AtlasPatches.java`,
  `AtlasRepair.java`, and mixins `MapCollectionMixin`, `MapCollectionInvoker`,
  `MapAtlasItemMixin`. Also `build.sh` and `verify-boot.sh`.
- `Aeromons/patches/map-atlases/` — the upstream-facing material: `REPORT.md`,
  the source patch against commit `b9ca8cd`, `deploy-trial.sh`, and the built jar.

It reaches the server through `LOCAL_JARS` in the Aeromons `build-pack.sh`, which
references `atlas-patches/build/libs/` by relative path. **Moving or renaming that
directory breaks the pack build**, so decide deliberately whether this project
absorbs it or sits beside it.

