package dev.skylite.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.skylite.core.module.general.InfoTooltips;
import dev.skylite.core.module.general.ItemProtection;
import dev.skylite.core.module.general.SlotBinding;
import dev.skylite.core.module.mining.CommissionHighlight;

import java.util.ArrayList;
import java.util.List;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin<T extends AbstractContainerMenu> {

	@Shadow
	@Final
	protected T menu;

	@Shadow
	protected Slot hoveredSlot;

	@Shadow
	public abstract Component getTitle();

	@Inject(
		method = "extractContents",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractSlotHighlightBack(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"
		)
	)
	private void skylite$beforeHighlight(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
		CommissionHighlight.INSTANCE.onScreenRender(context, this.getTitle().getString(), this.menu);
		SlotBinding.INSTANCE.onRender(context, this.menu, this.hoveredSlot);
	}

	@Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V", at = @At("HEAD"), cancellable = true)
	private void skylite$onSlotClick(Slot slot, int slotId, int button, ContainerInput actionType, CallbackInfo ci) {
		if (SlotBinding.INSTANCE.onMouseClick(slot, slotId, button, actionType)) {
			ci.cancel();
			return;
		}
		ItemStack stack = slot != null ? slot.getItem() : this.menu.getCarried();
		if (ItemProtection.INSTANCE.isSalvageGui()) {
			java.util.ArrayList<ItemStack> stacks = new java.util.ArrayList<>();
			for (Slot s : this.menu.slots) {
				stacks.add(s.getItem());
			}
			if (ItemProtection.INSTANCE.shouldCancelSalvage(stacks)) {
				ci.cancel();
				return;
			}
		}
		boolean throwing = actionType == ContainerInput.THROW;
		if (ItemProtection.INSTANCE.shouldCancelClick(stack, this.getTitle().getString(), throwing)) {
			ci.cancel();
		}
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void skylite$keyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (SlotBinding.INSTANCE.onKey(event.key(), GLFW.GLFW_PRESS, this.hoveredSlot)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "keyReleased", at = @At("HEAD"), cancellable = true)
	private void skylite$keyReleased(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (SlotBinding.INSTANCE.onKey(event.key(), GLFW.GLFW_RELEASE, this.hoveredSlot)) {
			cir.setReturnValue(true);
		}
	}

	@ModifyExpressionValue(
		method = "extractTooltip",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;getTooltipFromContainerItem(Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;"
		)
	)
	private List<Component> skylite$infoTooltips(List<Component> original, @Local ItemStack itemStack) {
		if (itemStack.isEmpty()) return original;
		List<Component> lines = new ArrayList<>(original);
		InfoTooltips.INSTANCE.appendLines(itemStack, lines);
		return lines;
	}
}
