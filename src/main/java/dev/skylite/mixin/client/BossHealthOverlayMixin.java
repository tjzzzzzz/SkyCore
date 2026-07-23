package dev.skylite.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.skylite.core.module.general.NoRender;

@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void skylite$hideBossBar(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (NoRender.INSTANCE.shouldHideBossBar()) {
			ci.cancel();
		}
	}
}
