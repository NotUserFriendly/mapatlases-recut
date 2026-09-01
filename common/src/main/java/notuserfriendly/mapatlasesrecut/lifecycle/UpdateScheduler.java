package notuserfriendly.mapatlasesrecut.lifecycle;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.phys.Vec2;
import notuserfriendly.mapatlasesrecut.config.MapAtlasesConfig;
import notuserfriendly.mapatlasesrecut.utils.ChunkChangeIndex;
import notuserfriendly.mapatlasesrecut.utils.MapDataHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            if (next == null) break;
            next.updateMapColorsAndMarkers(player);
            state(next).lastScan = player.level().getGameTime();
        }
    }

    /**
     * False when this map's picture is already complete and nothing near the player has
     * changed since it was last scanned. Rescanning it would repaint identical pixels.
     */
    protected boolean needsUpdate(ServerPlayer player, MapDataHolder holder) {
        if (!MapAtlasesConfig.skipUnchangedMaps.get()) return true;
        ScanState state = state(holder);
        if (state.lastScan < 0) return true;
        if (state.hasBlankPixels(holder)) return true;
        if (latestNearbyChange < 0) {
            latestNearbyChange = ChunkChangeIndex.latestChangeNear(
                    player.level(), player.getX(), player.getZ());
        }
        return state.lastScan <= latestNearbyChange;
    }

    protected ScanState state(MapDataHolder holder) {
        return scanStates.computeIfAbsent(holder.id, i -> new ScanState());
    }

    protected static class ScanState {
        long lastScan = -1L;
        private int blankCursor = 0;
        private boolean blank = true;

        /** Incremental: the cursor never rewinds, so this is amortised O(1). */
        boolean hasBlankPixels(MapDataHolder holder) {
            if (!blank) return false;
            byte[] colors = holder.data.colors;
            for (; blankCursor < colors.length; blankCursor++) {
                if (colors[blankCursor] == 0) return true;
            }
            blank = false;
            return false;
        }
    }

    protected abstract MapDataHolder poll(ServerPlayer player);
}
