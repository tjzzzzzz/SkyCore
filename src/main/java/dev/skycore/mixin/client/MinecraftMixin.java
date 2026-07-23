package dev.skycore.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.skycore.core.module.dungeon.CroesusSolver;
import dev.skycore.core.module.dungeon.DeviceSolvers;
import dev.skycore.core.module.dungeon.DungeonChestValue;
import dev.skycore.core.module.dungeon.LeapOverlay;
import dev.skycore.core.module.dungeon.MelodyMessage;
import dev.skycore.core.module.dungeon.QuickClose;
import dev.skycore.core.module.dungeon.TerminalSolvers;
import dev.skycore.core.module.general.ItemProtection;
import dev.skycore.core.module.mining.CorpseHighlight;

@Mixin(Minecraft.class)
public class MinecraftMixin {

	@Inject(
		method = "startUseItem",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;interact(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/EntityHitResult;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"
		),
		cancellable = true
	)
	private void skycore$interactEntity(CallbackInfo ci, @Local Entity entity, @Local EntityHitResult entityHitResult) {
		CorpseHighlight.INSTANCE.onInteract(entity);
		if (DeviceSolvers.INSTANCE.shouldCancelEntityInteract(entity)) {
			ci.cancel();
		}
	}

	@Inject(method = "setScreenAndShow", at = @At("HEAD"))
	private void skycore$screenChange(Screen screen, CallbackInfo ci) {
		Minecraft client = (Minecraft) (Object) this;
		Screen previous = client.gui.screen();
		if (previous != null) {
			ItemProtection.INSTANCE.onScreenClose();
			TerminalSolvers.INSTANCE.onScreenClose();
			LeapOverlay.INSTANCE.onScreenClose();
			DungeonChestValue.INSTANCE.onScreenClose();
			CroesusSolver.INSTANCE.onScreenClose();
			MelodyMessage.INSTANCE.onScreenClose();
		}
		if (screen == null) {
			ItemProtection.INSTANCE.onScreenClose();
		} else {
			String title = screen.getTitle().getString();
			ItemProtection.INSTANCE.onScreenOpen(title);
			LeapOverlay.INSTANCE.onScreenOpen(title);
			DungeonChestValue.INSTANCE.onScreenOpen(title);
			CroesusSolver.INSTANCE.onScreenOpen();
			MelodyMessage.INSTANCE.onScreenOpen(title);
		}
	}

	@Inject(method = "handleKeybinds", at = @At("HEAD"))
	private void skycore$quickCloseAndLeapKeys(CallbackInfo ci) {
		Minecraft client = (Minecraft) (Object) this;
		Screen current = client.gui.screen();
		if (!(current instanceof AbstractContainerScreen<?>)) return;
		String title = current.getTitle().getString();
		if (LeapOverlay.INSTANCE.isLeapMenu(title)) {
			return;
		}
		QuickClose.INSTANCE.tick(current, title);
	}
}
