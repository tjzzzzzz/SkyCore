package dev.skylite.mixin.client;

import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.skylite.core.module.general.ChatTweaks;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void skylite$copyChat(MouseButtonEvent event, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && ChatTweaks.INSTANCE.tryCopyAt(event.x(), event.y())) {
			cir.setReturnValue(true);
		}
	}
}
