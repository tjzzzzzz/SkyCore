package dev.skycore.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.skycore.core.module.general.InfoTooltips;
import dev.skycore.core.module.general.ItemProtection;
import dev.skycore.core.module.general.NoRender;
import dev.skycore.core.module.general.SlotBinding;
import dev.skycore.core.module.mining.CommissionHighlight;

import java.util.ArrayList;
import java.util.List;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin<T extends AbstractContainerMenu> {

	@Shadow
	@Final
	protected T menu;

	@Shadow
	protected Slot hoveredSlot;

	@Unique
	private String skycore$title() {
		return ((Screen) (Object) this).getTitle().getString();
	}

	@Inject(
		method = "extractContents",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractSlotHighlightBack(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"
		)
	)
	private void skycore$beforeHighlight(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
		CommissionHighlight.INSTANCE.onScreenRender(context, this.skycore$title(), this.menu);
		SlotBinding.INSTANCE.onRender(context, this.menu, this.hoveredSlot);
	}

	@Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V", at = @At("HEAD"), cancellable = true)
	private void skycore$onSlotClick(Slot slot, int slotId, int button, ContainerInput actionType, CallbackInfo ci) {
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
		if (ItemProtection.INSTANCE.shouldCancelClick(stack, this.skycore$title(), throwing)) {
			ci.cancel();
		}
	}

	@Inject(method = "containerTick", at = @At("HEAD"))
	private void skycore$slotBindingTick(CallbackInfo ci) {
		SlotBinding.INSTANCE.tickKeyState(this.hoveredSlot);
	}

	@Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
	private void skycore$hideEmptyTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
		if (NoRender.INSTANCE.shouldHideEmptyTooltips(this.hoveredSlot, this.skycore$title())) {
			ci.cancel();
		}
	}

	@ModifyExpressionValue(
		method = "extractTooltip",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;getTooltipFromContainerItem(Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;"
		)
	)
	private List<Component> skycore$infoTooltips(List<Component> original, @Local ItemStack itemStack) {
		if (itemStack.isEmpty()) return original;
		List<Component> lines = new ArrayList<>(original);
		InfoTooltips.INSTANCE.appendLines(itemStack, lines);
		return lines;
	}
}
