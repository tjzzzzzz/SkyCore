package dev.skycore.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import dev.skycore.core.module.general.NoRender;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

	@ModifyExpressionValue(
		method = "extractVisibleEntities",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z"
		)
	)
	private boolean skycore$noRenderEntity(boolean original, @Local Entity entity) {
		if (original && NoRender.INSTANCE.shouldCancelEntity(entity)) {
			return false;
		}
		return original;
	}
}
