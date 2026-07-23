package dev.skylite.mixin.client;

import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.skylite.core.module.general.ChatTweaks;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {

	@ModifyConstant(method = "addMessageToQueue", constant = @Constant(intValue = 100))
	private int skylite$queueLimit(int original) {
		if (ChatTweaks.INSTANCE.extraLines()) {
			return ChatTweaks.INSTANCE.lineLimit();
		}
		return original;
	}

	@ModifyConstant(method = "addMessageToDisplayQueue", constant = @Constant(intValue = 100))
	private int skylite$displayLimit(int original) {
		if (ChatTweaks.INSTANCE.extraLines()) {
			return ChatTweaks.INSTANCE.lineLimit();
		}
		return original;
	}

	@Inject(method = "clearMessages", at = @At("HEAD"), cancellable = true)
	private void skylite$keepHistory(boolean clearSent, CallbackInfo ci) {
		if (clearSent && ChatTweaks.INSTANCE.keepHistory()) {
			ci.cancel();
		}
	}
}
