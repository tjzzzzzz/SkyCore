package dev.skylite.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.skylite.core.module.general.NoRender;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {

	@Inject(method = "addDestroyBlockEffect", at = @At("HEAD"), cancellable = true)
	private void skylite$hideBreakParticles(BlockPos pos, BlockState state, CallbackInfo ci) {
		if (NoRender.INSTANCE.shouldHideBreakParticles()) {
			ci.cancel();
		}
	}
}
