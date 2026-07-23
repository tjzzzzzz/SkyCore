package dev.skycore.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import dev.skycore.core.module.general.NoRender;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

	@ModifyExpressionValue(
		method = "renderLevel",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;oPortalEffectIntensity:F")
	)
	private float skycore$noNauseaLast(float original) {
		if (NoRender.INSTANCE.shouldHideNausea()) {
			return 0.0f;
		}
		return original;
	}

	@ModifyExpressionValue(
		method = "renderLevel",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;portalEffectIntensity:F")
	)
	private float skycore$noNausea(float original) {
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
	private float skycore$noNauseaBlend(float original) {
		if (NoRender.INSTANCE.shouldHideNausea()) {
			return 0.0f;
		}
		return original;
	}
}
