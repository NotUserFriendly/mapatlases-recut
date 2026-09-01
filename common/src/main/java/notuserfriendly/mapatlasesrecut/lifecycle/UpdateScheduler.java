package notuserfriendly.mapatlasesrecut.lifecycle;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.phys.Vec2;
import notuserfriendly.mapatlasesrecut.MapAtlasesMod;
import notuserfriendly.mapatlasesrecut.config.MapAtlasesConfig;
import notuserfriendly.mapatlasesrecut.utils.ChunkChangeIndex;
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

        // A scan paints what is reachable from where the player stands, so moving even one
        // pixel's worth of blocks exposes new ground and restarts the sweep.
        int px = player.getBlockX();
        int pz = player.getBlockZ();
        for (MapDataHolder holder : visible) {
            ScanState st = state(holder);
            int pixel = 1 << holder.data.scale;
            if (st.anchorX == Integer.MIN_VALUE
                    || Math.abs(px - st.anchorX) >= pixel
                    || Math.abs(pz - st.anchorZ) >= pixel) {
                st.anchorX = px;
                st.anchorZ = pz;
                st.sweepsAtAnchor = 0;
            }
        }

        while (accumulator >= 1f) {
            MapDataHolder next = poll(player);
            accumulator -= 1f;
            if (next == null) {
                SKIPPED.incrementAndGet();
                break;
            }
            SCANNED.incrementAndGet();
            next.updateMapColorsAndMarkers(player);
            ScanState scanned = state(next);
            scanned.lastScan = player.level().getGameTime();
            scanned.sweepsAtAnchor++;
        }
        maybeReport(player.level().getGameTime());
    }

    // --- measurement, for the profiling gate ---------------------------------------

    private static final AtomicLong SCANNED = new AtomicLong();
    private static final AtomicLong SKIPPED = new AtomicLong();
    private static volatile long lastReportTick = 0L;

    /** Columns MapItem.update samples per call, constant across map scales. */
    private static final long COLUMNS_PER_SCAN = 4096L;
    private static final long REPORT_PERIOD = 200L;

    private static void maybeReport(long gameTime) {
        if (!MapAtlasesConfig.debugUpdate.get()) return;
        if (gameTime - lastReportTick < REPORT_PERIOD) return;
        lastReportTick = gameTime;
        long scanned = SCANNED.getAndSet(0);
        long skipped = SKIPPED.getAndSet(0);
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
                "map scans in last {}s: {} performed, {} skipped ({}%), ~{} column samples avoided",
                REPORT_PERIOD / 20, scanned, skipped, (100 * skipped) / total, skipped * COLUMNS_PER_SCAN);
    }

    /**
     * False when scanning again would repaint identical pixels.
     * <p>
     * MapItem.update paints one x-strip in sixteen per call, chosen by a step counter, and
     * only within reach of the viewer. So {@value #STRIPS} consecutive scans from one spot
     * paint everything that spot can reach; past that, only the player moving or the world
     * changing can produce a different result.
     * <p>
     * Note this deliberately does <em>not</em> ask whether the map still has blank pixels.
     * A map is only ever fully painted once someone has walked its whole cell, and pixels
     * out of reach cannot be filled from here anyway, so that test skips nothing in practice.
     */
    protected boolean needsUpdate(ServerPlayer player, MapDataHolder holder) {
        if (!MapAtlasesConfig.skipUnchangedMaps.get()) return true;
        ScanState state = state(holder);
        if (state.lastScan < 0) return true;
        if (state.sweepsAtAnchor < STRIPS) return true;
        if (latestNearbyChange < 0) {
            latestNearbyChange = ChunkChangeIndex.latestChangeNear(
                    player.level(), player.getX(), player.getZ());
        }
        return state.lastScan <= latestNearbyChange;
    }

    /** Strip classes in MapItem.update's step cycle: (k1 & 15) == (step & 15). */
    private static final int STRIPS = 16;

    protected ScanState state(MapDataHolder holder) {
        return scanStates.computeIfAbsent(holder.id, i -> new ScanState());
    }

    protected static class ScanState {
        long lastScan = -1L;
        /** Where the player stood when the current sweep began. */
        int anchorX = Integer.MIN_VALUE;
        int anchorZ;
        /** Scans performed without the player leaving that spot. */
        int sweepsAtAnchor = 0;
    }

    protected abstract MapDataHolder poll(ServerPlayer player);
}
