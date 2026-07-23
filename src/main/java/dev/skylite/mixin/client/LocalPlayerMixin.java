package dev.skylite.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.skylite.core.module.general.ItemProtection;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

	@Inject(method = "drop", at = @At("HEAD"), cancellable = true)
	private void skylite$blockProtectedDrop(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
		if (ItemProtection.INSTANCE.shouldBlockDrop()) {
			cir.setReturnValue(false);
		}
	}
}
