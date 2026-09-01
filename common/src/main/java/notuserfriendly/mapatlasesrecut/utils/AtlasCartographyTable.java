package notuserfriendly.mapatlasesrecut.utils;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface AtlasCartographyTable {

    int mapatlasesrecut$getSelectedMapIndex();

    void mapatlasesrecut$setSelectedMapIndex(int index);

    void mapatlasesrecut$removeSelectedMap(ItemStack atlas);

    @Nullable
    Slice mapatlasesrecut$getSelectedSlice();
}
