package dev.skylite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.skylite.core.module.ItemScaleAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	private float mainHandHeight;

	@Shadow
	private float oMainHandHeight;

	@Shadow
	private float offHandHeight;

	@Shadow
	private float oOffHandHeight;

	@Shadow
	private ItemStack mainHandItem;

	@Shadow
	private ItemStack offHandItem;

	@WrapOperation(
		method = "submitArmWithItem",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0)
	)
	private void skylite$applyViewmodel(
		PoseStack pose,
		Operation<Void> original,
		@Local(argsOnly = true) InteractionHand hand,
		@Local(argsOnly = true) ItemStack itemStack
	) {
		original.call(pose);
		if (!ItemScaleAnimation.isActive() || itemStack.isEmpty() || itemStack.has(DataComponents.MAP_ID)) {
			return;
		}
		float x = ItemScaleAnimation.getX();
		pose.translate(hand == InteractionHand.MAIN_HAND ? x : -x, ItemScaleAnimation.getY(), ItemScaleAnimation.getZ());
		pose.mulPose(Axis.XP.rotationDegrees(ItemScaleAnimation.getPitch()));
		pose.mulPose(Axis.YP.rotationDegrees(ItemScaleAnimation.getYaw()));
		pose.mulPose(Axis.ZP.rotationDegrees(ItemScaleAnimation.getRoll()));
	}

	@Inject(
		method = "submitArmWithItem",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"
		)
	)
	private void skylite$applySize(
		AbstractClientPlayer player,
		float partialTick,
		float pitch,
		InteractionHand hand,
		float swingProgress,
		ItemStack itemStack,
		float equipProgress,
		PoseStack poseStack,
		SubmitNodeCollector collector,
		int light,
		CallbackInfo ci
	) {
		if (!ItemScaleAnimation.isActive() || itemStack.isEmpty() || itemStack.has(DataComponents.MAP_ID)) {
			return;
		}
		float size = ItemScaleAnimation.getSize();
		poseStack.scale(size, size, size);
	}

	@Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
	private void skylite$noSwing(float swingProgress, PoseStack poseStack, int armSign, HumanoidArm arm, CallbackInfo ci) {
		if (ItemScaleAnimation.shouldStopSwing()) {
			ci.cancel();
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void skylite$noEquipReset(CallbackInfo ci) {
		if (!ItemScaleAnimation.shouldSkipEquipReset()) {
			return;
		}
		LocalPlayer player = this.minecraft.player;
		if (player != null) {
			this.mainHandItem = player.getMainHandItem();
			this.offHandItem = player.getOffhandItem();
		}
		this.mainHandHeight = 1f;
		this.oMainHandHeight = 1f;
		this.offHandHeight = 1f;
		this.oOffHandHeight = 1f;
	}
}
