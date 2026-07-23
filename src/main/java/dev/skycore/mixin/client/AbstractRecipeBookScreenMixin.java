package dev.skycore.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.skycore.core.module.general.NoRender;

@Mixin(AbstractRecipeBookScreen.class)
public abstract class AbstractRecipeBookScreenMixin {

	@Inject(method = "initButton", at = @At("HEAD"), cancellable = true)
	private void skycore$hideRecipeBook(CallbackInfo ci) {
		if (NoRender.INSTANCE.shouldHideRecipeBook()) {
			ci.cancel();
		}
	}
}
