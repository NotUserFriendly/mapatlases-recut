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

    /**
     * Radius for "did something change that the player would notice immediately".
     * Deliberately far smaller than the 128 block region a scan writes: in a living world
     * random ticks change something somewhere in that whole region every second or two, so a
     * full-region test can never be satisfied.
     */
    public static final int NEAR_RADIUS = 48;

    private ChunkChangeIndex() {
    }

    // PROBE -- remove with the diagnostic in UpdateScheduler
    public record Report(long latest, int chunkX, int chunkZ, int loaded, int changedSince) {}

    // PROBE -- same walk as latestChangeNear, but says which chunk and how many are recent
    public static Report report(Level level, double x, double z, long since) {
        int minX = SectionPos.blockToSectionCoord((int) x - NEAR_RADIUS);
        int maxX = SectionPos.blockToSectionCoord((int) x + NEAR_RADIUS);
        int minZ = SectionPos.blockToSectionCoord((int) z - NEAR_RADIUS);
        int maxZ = SectionPos.blockToSectionCoord((int) z + NEAR_RADIUS);
        long latest = 0L;
        int bx = 0, bz = 0, loaded = 0, recent = 0;
        var source = level.getChunkSource();
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (!source.hasChunk(cx, cz)) continue;
                loaded++;
                long changed = ((ChunkChangeStamp) level.getChunk(cx, cz)).mapatlasesrecut$lastBlockChange();
                if (changed > since) recent++;
                if (changed > latest) { latest = changed; bx = cx; bz = cz; }
            }
        }
        return new Report(latest, bx, bz, loaded, recent);
    }

    /**
     * Latest game time at which a block changed in a loaded chunk the next scan would touch.
     * Unloaded chunks are skipped: they cannot have changed while unloaded, and asking for
     * them would force a load.
     */
    public static long latestChangeNear(Level level, double x, double z, int radius) {
        int minX = SectionPos.blockToSectionCoord((int) x - radius);
        int maxX = SectionPos.blockToSectionCoord((int) x + radius);
        int minZ = SectionPos.blockToSectionCoord((int) z - radius);
        int maxZ = SectionPos.blockToSectionCoord((int) z + radius);

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
