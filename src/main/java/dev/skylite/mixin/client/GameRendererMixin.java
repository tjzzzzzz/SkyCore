package dev.skylite.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import dev.skylite.core.module.general.NoRender;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

	@ModifyExpressionValue(
		method = "renderLevel",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;oPortalEffectIntensity:F")
	)
	private float skylite$noNauseaLast(float original) {
		if (NoRender.INSTANCE.shouldHideNausea()) {
			return 0.0f;
		}
		return original;
	}

	@ModifyExpressionValue(
		method = "renderLevel",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;portalEffectIntensity:F")
	)
	private float skylite$noNausea(float original) {
		if (NoRender.INSTANCE.shouldHideNausea()) {
			return 0.0f;
		}
		return original;
	}

	@ModifyExpressionValue(
		method = "renderLevel",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/player/LocalPlayer;getEffectBlendFactor(Lnet/minecraft/core/Holder;F)F"
		)
	)
	private float skylite$noNauseaBlend(float original) {
		if (NoRender.INSTANCE.shouldHideNausea()) {
			return 0.0f;
		}
		return original;
	}
}
