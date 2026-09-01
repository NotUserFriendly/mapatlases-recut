package notuserfriendly.mapatlasesrecut.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import notuserfriendly.mapatlasesrecut.utils.ChunkChangeStamp;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin implements ChunkChangeStamp {

    @Shadow public abstract net.minecraft.world.level.Level getLevel();

    // Long.MIN_VALUE would overflow the comparison in DirtyChunks; 0 reads as
    // "changed at the dawn of the world", which is the safe default: a map that has
    // never scanned will scan once.
    @Unique
    private long mapatlasesrecut$lastBlockChange = 0L;

    @Override
    public long mapatlasesrecut$lastBlockChange() {
        return this.mapatlasesrecut$lastBlockChange;
    }

    @Override
    public void mapatlasesrecut$markBlockChanged(long gameTime) {
        this.mapatlasesrecut$lastBlockChange = gameTime;
    }

    // hot path: one field store, no allocation, no branching beyond the null check
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void mapatlasesrecut$stampBlockChange(BlockPos pos, BlockState state, boolean isMoving,
                                                  CallbackInfoReturnable<BlockState> cir) {
        if (cir.getReturnValue() != null) {
            this.mapatlasesrecut$lastBlockChange = this.getLevel().getGameTime();
        }
    }
}
