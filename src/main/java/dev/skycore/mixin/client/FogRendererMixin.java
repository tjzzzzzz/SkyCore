package dev.skycore.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.environment.FogEnvironment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.skycore.core.module.general.NoRender;

import java.util.List;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {

	@Shadow
	@Final
	private static List<FogEnvironment> FOG_ENVIRONMENTS;

	@Inject(
		method = "setupFog",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/client/renderer/fog/FogData;renderDistanceEnd:F",
			shift = At.Shift.AFTER,
			ordinal = 0
		)
	)
	private void skycore$clearFog(
		Camera camera,
		int renderDistanceInChunks,
		DeltaTracker deltaTracker,
		float darkenWorldAmount,
		ClientLevel level,
		CallbackInfoReturnable<FogData> cir,
		@Local LocalRef<FogData> fogRef
	) {
		if (!NoRender.INSTANCE.shouldHideFog()) return;
		FogType type = camera.getFluidInCamera();
		Entity entity = camera.entity();
		for (FogEnvironment modifier : FOG_ENVIRONMENTS) {
			if (modifier.isApplicable(type, entity)) {
				return;
			}
		}
		fogRef.set(NoRender.INSTANCE.emptyFog(fogRef.get()));
	}
}
