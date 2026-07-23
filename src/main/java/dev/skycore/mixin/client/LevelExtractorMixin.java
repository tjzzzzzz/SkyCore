package dev.skycore.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import dev.skycore.core.module.general.NoRender;

@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {

	@ModifyReturnValue(method = "isEntityVisible", at = @At("RETURN"))
	private boolean skycore$noRenderEntity(
			boolean original,
			Entity entity,
			Frustum frustum,
			double camX,
			double camY,
			double camZ) {
		if (original && NoRender.INSTANCE.shouldCancelEntity(entity)) {
			return false;
		}
		return original;
	}
}
