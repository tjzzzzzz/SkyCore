package dev.skycore.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import dev.skycore.core.module.mining.GemstoneDesyncFix;

@Mixin(IronBarsBlock.class)
public abstract class IronBarsBlockMixin {

	@ModifyReturnValue(method = "updateShape", at = @At("RETURN"))
	private BlockState skycore$gemstonePane(BlockState original) {
		if (GemstoneDesyncFix.INSTANCE.active() && GemstoneDesyncFix.INSTANCE.isDefaultPane(original)) {
			return GemstoneDesyncFix.INSTANCE.asFullPane(original);
		}
		return original;
	}
}
