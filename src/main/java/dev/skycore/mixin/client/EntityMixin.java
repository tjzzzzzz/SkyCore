package dev.skycore.mixin.client;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.skycore.core.dungeon.DungeonEvents;

@Mixin(Entity.class)
public class EntityMixin {

	@Inject(method = "setRemoved", at = @At("HEAD"))
	private void skycore$removed(Entity.RemovalReason reason, CallbackInfo ci) {
		DungeonEvents.INSTANCE.fireRemoved((Entity) (Object) this);
	}
}
