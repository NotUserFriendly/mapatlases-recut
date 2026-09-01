package notuserfriendly.mapatlasesrecut.lifecycle;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.phys.Vec2;
import notuserfriendly.mapatlasesrecut.MapAtlasesMod;
import notuserfriendly.mapatlasesrecut.config.MapAtlasesConfig;
import notuserfriendly.mapatlasesrecut.utils.ChunkChangeIndex;
import notuserfriendly.mapatlasesrecut.utils.ScanRegion;
import notuserfriendly.mapatlasesrecut.utils.MapDataHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public abstract class UpdateScheduler {

    /**
     * Accumulator for fractional updates per tick
     */
    protected float accumulator = 0f;
    protected Vec2 lastPlayerPos = null;

    private final Map<MapId, ScanState> scanStates = new HashMap<>();

    /**
     * Latest change near the player, or -1 when it has not been needed this tick.
     * Computed lazily: a player filling in new territory never pays for the region scan,
     * because a map with blank pixels always wants updating anyway.
     */
    private long latestNearbyChange = -1L;

    protected float computeUpdateRate(Player player) {
        Vec2 currentPos = new Vec2((float) player.getX(), (float) player.getZ());
        if (lastPlayerPos == null) {
            lastPlayerPos = currentPos;
        }
        double speed = new Vec2(currentPos.x - lastPlayerPos.x, currentPos.y - lastPlayerPos.y).length();
        lastPlayerPos = currentPos;
        //magic numbers yay
        return Mth.clamp(Mth.map((float) speed, 0.001f, 1.2f,
                0.1f, 2f), 0.1f, 2f) * MapAtlasesConfig.mapUpdatePerTick.get();
    }


    public void performUpdate(ServerPlayer player, List<MapDataHolder> visible) {
        accumulator += computeUpdateRate(player);
        latestNearbyChange = -1L;
        scanStates.keySet().retainAll(visible.stream().map(m -> m.id).toList());


        while (accumulator >= 1f) {
            MapDataHolder next = poll(player);
            accumulator -= 1f;
            if (next == null) {
                SKIPPED.incrementAndGet();
                // The colour scan is what we are avoiding, not the markers. Entity radar and
                // pins ride on the same call, so keep them ticking or they freeze wherever
                // the terrain happened to settle.
                for (MapDataHolder holder : visible) {
                    holder.updateMarkersOnly(player);
                }
                break;
            }
            SCANNED.incrementAndGet();
            next.updateMapColorsAndMarkers(player);
            ScanState scanned = state(next);
            scanned.lastScan = player.level().getGameTime();
            scanned.paintedCache = ScanState.UNKNOWN;
        }
        maybeReport(player.level().getGameTime());
        maybeProbe(player, visible);
    }

    // PROBE -- delete once the skip rate is understood. Dumps, per map in view, how much of
    // the region a scan would write is still blank, so "the check is wrong" can be told apart
    // from "the map genuinely is not painted yet".
    private static long lastProbeTick = 0L;

    private void maybeProbe(ServerPlayer player, List<MapDataHolder> visible) {
        if (!MapAtlasesConfig.logScanStats.get()) return;
        long now = player.level().getGameTime();
        if (now - lastProbeTick < REPORT_PERIOD) return;
        lastProbeTick = now;
        boolean ceiling = player.level().dimensionType().hasCeiling();
        long gt = player.level().getGameTime();
        long oldestScan = visible.stream().mapToLong(h -> state(h).lastScan).min().orElse(0);
        var rep = ChunkChangeIndex.report(player.level(), player.getX(), player.getZ(), oldestScan);
        MapAtlasesMod.LOGGER.info(
                "PROBE gameTime={} player=({}, {}) maps={} | chunks loaded={} changed-since-oldest-scan={} "
                        + "newestChange={} (age {} ticks) at chunk ({}, {}) = block ({}, {})",
                gt, (int) player.getX(), (int) player.getZ(), visible.size(),
                rep.loaded(), rep.changedSince(), rep.latest(), gt - rep.latest(),
                rep.chunkX(), rep.chunkZ(), rep.chunkX() * 16, rep.chunkZ() * 16);
        for (MapDataHolder h : visible) {
            ScanState st = state(h);
            var d = ScanRegion.diagnose(player, h.data, ceiling);
            MapAtlasesMod.LOGGER.info("PROBE   map {} scale={} center=({}, {}) {} lastScan={} nearbyChange={}",
                    h.id.id(), h.data.scale, h.data.centerX, h.data.centerZ, d,
                    st.lastScan, latestNearbyChange);
        }
    }

    // --- measurement, for the profiling gate ---------------------------------------

    private static final AtomicLong SCANNED = new AtomicLong();
    private static final AtomicLong SKIPPED = new AtomicLong();
    // per map, not per tick: SKIPPED only moves when every map in view is settled at once,
    // which hides the case where eight of nine are
    private static final AtomicLong CONSIDERED = new AtomicLong();
    private static final AtomicLong SETTLED = new AtomicLong();
    private static volatile long lastReportTick = 0L;

    /** Columns MapItem.update samples per call, constant across map scales. */
    private static final long COLUMNS_PER_SCAN = 4096L;
    private static final long REPORT_PERIOD = 200L;

    private static void maybeReport(long gameTime) {
        if (!MapAtlasesConfig.logScanStats.get()) return;
        if (gameTime - lastReportTick < REPORT_PERIOD) return;
        lastReportTick = gameTime;
        long scanned = SCANNED.getAndSet(0);
        long skipped = SKIPPED.getAndSet(0);
        long considered = CONSIDERED.getAndSet(0);
        long settled = SETTLED.getAndSet(0);
        long total = scanned + skipped;
        if (total == 0) {
            // Silence here is ambiguous: it could mean the feature works, or that map
            // updating never ran. Say which, so the absence is diagnosable.
            MapAtlasesMod.LOGGER.info(
                    "map scans in last {}s: none attempted (needs an unlocked atlas in hands "
                            + "or hotbar, holding at least one map, in a tracked dimension)",
                    REPORT_PERIOD / 20);
            return;
        }
        MapAtlasesMod.LOGGER.info(
                "map scans in last {}s: {} performed, {} whole ticks skipped. "
                        + "per map: {} considered, {} already painted ({}%)",
                REPORT_PERIOD / 20, scanned, skipped, considered, settled,
                considered == 0 ? 0 : (100 * settled) / considered);
    }

    /**
     * False when scanning would repaint pixels that are already painted and still correct.
     * <p>
     * Two questions, and both must hold. Are the pixels this position would write already
     * painted, and has nothing near the player changed since this map was last scanned.
     * <p>
     * This covers the case that actually matters: <em>walking through explored terrain</em>.
     * Standing still is rare; crossing ground somebody already mapped is constant, and every
     * one of those scans repaints identical pixels.
     */
    protected boolean needsUpdate(ServerPlayer player, MapDataHolder holder) {
        CONSIDERED.incrementAndGet();
        boolean needed = computeNeedsUpdate(player, holder);
        if (!needed) SETTLED.incrementAndGet();
        return needed;
    }

    private boolean computeNeedsUpdate(ServerPlayer player, MapDataHolder holder) {
        if (!MapAtlasesConfig.skipUnchangedMaps.get()) return true;
        ScanState state = state(holder);
        if (state.lastScan < 0) return true;
        long now = player.level().getGameTime();

        // anything the player is close enough to have done themselves scans at once
        if (latestNearbyChange < 0) {
            latestNearbyChange = ChunkChangeIndex.latestChangeNear(
                    player.level(), player.getX(), player.getZ(), ChunkChangeIndex.NEAR_RADIUS);
        }
        if (state.lastScan <= latestNearbyChange) return true;

        // still filling in: never throttle a map that is actually gaining pixels
        if (!state.regionPainted(player, holder)) return true;

        // Must comfortably exceed (maps in view / scan budget), or every map is permanently
        // overdue and poll always finds work. Nine maps at a 60 tick interval demand 0.15
        // scans a tick against a stationary budget of 0.1, which is why nothing was skipped.
        return now - state.lastScan >= MapAtlasesConfig.paintedRefreshTicks.get();
    }

    protected ScanState state(MapDataHolder holder) {
        return scanStates.computeIfAbsent(holder.id, i -> new ScanState());
    }

    protected static class ScanState {
        static final int UNKNOWN = Integer.MIN_VALUE;

        long lastScan = -1L;
        /** Player pixel the paint check was last answered for, and its answer. */
        private int paintedCache = UNKNOWN;
        private int cachedPixelX = UNKNOWN;
        private int cachedPixelZ;

        /**
         * Cached because the answer only changes when the player crosses a pixel boundary or
         * a scan paints more. Pixels never revert to blank, so nothing else can alter it.
         */
        boolean regionPainted(ServerPlayer player, MapDataHolder holder) {
            int pixel = 1 << holder.data.scale;
            int px = Mth.floor(player.getX()) / pixel;
            int pz = Mth.floor(player.getZ()) / pixel;
            if (paintedCache != UNKNOWN && px == cachedPixelX && pz == cachedPixelZ) {
                return paintedCache == 1;
            }
            boolean painted = ScanRegion.fullyPainted(player, holder.data,
                    player.level().dimensionType().hasCeiling());
            cachedPixelX = px;
            cachedPixelZ = pz;
            paintedCache = painted ? 1 : 0;
            return painted;
        }
    }

    protected abstract MapDataHolder poll(ServerPlayer player);
}
