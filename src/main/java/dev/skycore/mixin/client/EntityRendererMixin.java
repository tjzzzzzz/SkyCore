package dev.skycore.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import dev.skycore.core.module.general.NoRender;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<S extends EntityRenderState> {

	@ModifyExpressionValue(
		method = "extractRenderState",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;displayFireAnimation()Z"
		)
	)
	private boolean skycore$hideEntityFire(boolean original) {
		if (NoRender.INSTANCE.shouldHideEntityFire()) {
			return false;
		}
		return original;
	}
}
