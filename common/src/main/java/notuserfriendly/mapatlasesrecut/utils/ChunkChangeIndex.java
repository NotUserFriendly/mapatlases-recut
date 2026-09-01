package notuserfriendly.mapatlasesrecut.utils;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Answers "has anything near the player changed lately", so a map whose picture is already
 * complete need not be rescanned.
 * <p>
 * {@code MapItem.update} always scans a 256x256 block region centred on the viewer, at every
 * map scale, so the region checked here is the same regardless of the map being considered.
 */
public final class ChunkChangeIndex {

    /** Half-width of the region MapItem.update touches, in blocks. */
    private static final int SCAN_RADIUS = 128;

    private ChunkChangeIndex() {
    }

    /**
     * Latest game time at which a block changed in a loaded chunk the next scan would touch.
     * Unloaded chunks are skipped: they cannot have changed while unloaded, and asking for
     * them would force a load.
     */
    public static long latestChangeNear(Level level, double x, double z) {
        int minX = SectionPos.blockToSectionCoord((int) x - SCAN_RADIUS);
        int maxX = SectionPos.blockToSectionCoord((int) x + SCAN_RADIUS);
        int minZ = SectionPos.blockToSectionCoord((int) z - SCAN_RADIUS);
        int maxZ = SectionPos.blockToSectionCoord((int) z + SCAN_RADIUS);

        long latest = 0L;
        var source = level.getChunkSource();
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (!source.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                long changed = ((ChunkChangeStamp) chunk).mapatlasesrecut$lastBlockChange();
                if (changed > latest) latest = changed;
            }
        }
        return latest;
    }
}
