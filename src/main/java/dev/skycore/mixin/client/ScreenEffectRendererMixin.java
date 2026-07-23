package dev.skycore.mixin.client;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.skycore.core.module.general.NoRender;

@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {

	@Inject(method = "buildFireQuad", at = @At("HEAD"), cancellable = true)
	private static void skycore$hideFire(CallbackInfo ci) {
		if (NoRender.INSTANCE.shouldHideFireOverlay()) {
			ci.cancel();
		}
	}
}
