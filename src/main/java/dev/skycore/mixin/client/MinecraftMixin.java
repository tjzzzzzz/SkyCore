package dev.skycore.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.skycore.core.module.general.ItemProtection;
import dev.skycore.core.module.mining.CorpseHighlight;

@Mixin(Minecraft.class)
public class MinecraftMixin {

	@Inject(
		method = "startUseItem",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;interact(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/EntityHitResult;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"
		)
	)
	private void skycore$interactEntity(CallbackInfo ci, @Local Entity entity, @Local EntityHitResult entityHitResult) {
		CorpseHighlight.INSTANCE.onInteract(entity);
	}

	@Inject(method = "setScreen", at = @At("HEAD"))
	private void skycore$screenChange(Screen screen, CallbackInfo ci) {
		if (screen == null) {
			ItemProtection.INSTANCE.onScreenClose();
		} else {
			ItemProtection.INSTANCE.onScreenOpen(screen.getTitle().getString());
		}
	}
}
