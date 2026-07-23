package dev.skycore.mixin.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.skycore.core.module.mining.GemstoneDesyncFix;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {

	@Shadow
	public abstract BlockState getBlockState(BlockPos pos);

	@Inject(method = "setBlockState", at = @At("HEAD"))
	private void skycore$blockUpdate(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir) {
		GemstoneDesyncFix.INSTANCE.onBlockUpdate(pos, this.getBlockState(pos), state);
	}
}
