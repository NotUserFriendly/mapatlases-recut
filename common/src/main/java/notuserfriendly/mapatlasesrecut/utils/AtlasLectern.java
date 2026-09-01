package notuserfriendly.mapatlasesrecut.utils;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public interface AtlasLectern {

    boolean mapatlasesrecut$hasAtlas();

    boolean mapatlasesrecut$setAtlas(Player player, ItemStack atlas);

    ItemStack mapatlasesrecut$removeAtlas();
}
