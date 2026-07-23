package dev.skycore.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.skycore.core.module.general.ChatTweaks;
import dev.skycore.core.module.general.NoRender;

@Mixin(Hud.class)
public abstract class HudMixin {

	@Inject(method = "extractArmor", at = @At("HEAD"), cancellable = true)
	private static void skycore$hideArmor(
		GuiGraphicsExtractor graphics,
		Player player,
		int yLineBase,
		int numHealthRows,
		int healthRowHeight,
		int xLeft,
		CallbackInfo ci
	) {
		if (NoRender.INSTANCE.shouldHideArmorBar()) {
			ci.cancel();
		}
	}

	@Inject(method = "extractFood", at = @At("HEAD"), cancellable = true)
	private void skycore$hideFood(GuiGraphicsExtractor graphics, Player player, int yLineBase, int xRight, CallbackInfo ci) {
		if (NoRender.INSTANCE.shouldHideFoodBar()) {
			ci.cancel();
		}
	}

	@Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
	private void skycore$hideEffects(GuiGraphicsExtractor graphics, DeltaTracker tickCounter, CallbackInfo ci) {
		if (NoRender.INSTANCE.shouldHideEffectDisplay()) {
			ci.cancel();
		}
	}

	@Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true)
	private void skycore$hideSelectedItem(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (NoRender.INSTANCE.shouldHideSelectedItemName()) {
			ci.cancel();
		}
	}

	@Inject(method = "extractConfusionOverlay", at = @At("HEAD"), cancellable = true)
	private void skycore$hideNausea(GuiGraphicsExtractor graphics, float strength, CallbackInfo ci) {
		if (NoRender.INSTANCE.shouldHideNausea()) {
			ci.cancel();
		}
	}

	@WrapOperation(
		method = "onDisconnected",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/ChatComponent;clearMessages(Z)V"
		)
	)
	private void skycore$keepChatHistory(ChatComponent instance, boolean history, Operation<Void> original) {
		if (ChatTweaks.INSTANCE.keepHistory()) {
			return;
		}
		original.call(instance, history);
	}
}
