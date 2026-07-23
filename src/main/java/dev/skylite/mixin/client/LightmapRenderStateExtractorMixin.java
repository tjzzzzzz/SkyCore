package dev.skylite.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.util.ARGB;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import dev.skylite.core.module.general.Fullbright;

@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapRenderStateExtractorMixin {

	@Unique
	@Final
	private static final Vector3f SKYLITE$AMBIENT = new Vector3f(1.0f, 1.0f, 1.0f);

	@Redirect(
		method = "extract",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/util/ARGB;vector3fFromRGB24(I)Lorg/joml/Vector3f;",
			ordinal = 2
		)
	)
	private static Vector3f skylite$ambientLight(int color) {
		if (Fullbright.INSTANCE.isActive() && Fullbright.INSTANCE.mode() == Fullbright.Mode.AMBIENT) {
			return SKYLITE$AMBIENT;
		}
		return ARGB.vector3fFromRGB24(color);
	}

	@ModifyExpressionValue(method = "extract", at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 0))
	private static float skylite$gamma(float original) {
		if (Fullbright.INSTANCE.isActive() && Fullbright.INSTANCE.mode() == Fullbright.Mode.GAMMA) {
			return 1600.0f;
		}
		return original;
	}
}
