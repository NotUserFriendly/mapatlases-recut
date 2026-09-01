package notuserfriendly.mapatlasesrecut.utils;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Whether the pixels {@code MapItem.update} would write from the player's current position
 * have all been painted already.
 * <p>
 * This mirrors vanilla's write condition exactly, including the checkerboard thinning of the
 * outermost ring. Mirroring matters: vanilla never writes those excluded pixels from this
 * position, so demanding them would mean the answer is always "no" and nothing is ever
 * skipped.
 */
public final class ScanRegion {

    private ScanRegion() {
    }

    public static boolean fullyPainted(Player viewer, MapItemSavedData data, boolean hasCeiling) {
        int i = 1 << data.scale;
        int l = Mth.floor(viewer.getX() - (double) data.centerX) / i + 64;
        int i1 = Mth.floor(viewer.getZ() - (double) data.centerZ) / i + 64;
        int j1 = 128 / i;
        if (hasCeiling) {
            j1 /= 2;
        }

        byte[] colors = data.colors;
        for (int k1 = l - j1 + 1; k1 < l + j1; k1++) {
            if (k1 < 0 || k1 >= 128) continue;
            for (int l1 = i1 - j1 - 1; l1 < i1 + j1; l1++) {
                if (l1 < 0 || l1 >= 128) continue;
                int i2 = Mth.square(k1 - l) + Mth.square(l1 - i1);
                if (i2 >= j1 * j1) continue;
                boolean thinnedRing = i2 > (j1 - 2) * (j1 - 2);
                if (thinnedRing && (k1 + l1 & 1) == 0) continue;
                if (colors[k1 + l1 * 128] == 0) return false;
            }
        }
        return true;
    }
}
