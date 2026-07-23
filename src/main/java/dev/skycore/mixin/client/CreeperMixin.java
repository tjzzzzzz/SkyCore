package dev.skycore.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import dev.skycore.core.module.mining.GhostVision;

@Mixin(Creeper.class)
public class CreeperMixin {

	@ModifyReturnValue(method = "isPowered", at = @At("RETURN"))
	private boolean skycore$ghostAura(boolean original) {
		if (GhostVision.INSTANCE.isGhost((Creeper) (Object) this)) {
			return false;
		}
		return original;
	}
}
