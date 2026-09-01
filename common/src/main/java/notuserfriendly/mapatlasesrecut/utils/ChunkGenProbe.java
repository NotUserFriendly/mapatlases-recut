package notuserfriendly.mapatlasesrecut.utils;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import notuserfriendly.mapatlasesrecut.MapAtlasesMod;

/**
 * PROBE -- delete once the cost of reduced-status chunk generation is known.
 * <p>
 * Decides whether the coarse layer can reach past the loaded chunk set. A map needs surface
 * heightmap and top block, which is far less than FULL, and the ladder is
 * NOISE, SURFACE, CARVERS, FEATURES, ..., FULL. Carvers cut ravines and cave mouths, so
 * SURFACE alone misses terrain that shows from above; FEATURES adds trees.
 * <p>
 * Runs once, on the main thread, against chunks far enough away to be certainly ungenerated.
 * Each status gets its own region: asking for SURFACE on a chunk already taken to FEATURES
 * returns instantly and would measure nothing.
 */
public final class ChunkGenProbe {

    private static boolean done = false;
    private static final int CHUNKS_PER_STATUS = 5;

    private ChunkGenProbe() {
    }

    public static void runOnce(ServerLevel level) {
        if (done) return;
        done = true;

        ChunkStatus[] ladder = {ChunkStatus.NOISE, ChunkStatus.SURFACE,
                ChunkStatus.CARVERS, ChunkStatus.FEATURES, ChunkStatus.FULL};

        MapAtlasesMod.LOGGER.info("PROBE chunk generation cost, {} chunks per status, main thread",
                CHUNKS_PER_STATUS);

        int region = 0;
        for (ChunkStatus status : ladder) {
            // a fresh block of world per status, far from spawn and from each other
            int baseX = 5000 + region * 500;
            int baseZ = 5000 + region * 500;
            region++;

            long total = 0;
            long worst = 0;
            int ok = 0;
            for (int n = 0; n < CHUNKS_PER_STATUS; n++) {
                long t0 = System.nanoTime();
                ChunkAccess chunk = level.getChunkSource().getChunk(baseX + n, baseZ, status, true);
                long dt = System.nanoTime() - t0;
                if (chunk != null) ok++;
                total += dt;
                if (dt > worst) worst = dt;
            }
            MapAtlasesMod.LOGGER.info(String.format(
                    "PROBE   %-10s avg %6.1f ms   worst %6.1f ms   (%d of %d returned)",
                    status, total / 1_000_000.0 / CHUNKS_PER_STATUS,
                    worst / 1_000_000.0, ok, CHUNKS_PER_STATUS));
        }

        MapAtlasesMod.LOGGER.info("PROBE for reference: one map scan is 4096 column samples, "
                + "and a tick is 50 ms");
    }
}
