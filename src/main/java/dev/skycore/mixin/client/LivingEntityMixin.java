package dev.skycore.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.skycore.core.module.ItemScaleAnimation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

	@Shadow
	public boolean swinging;

	@ModifyExpressionValue(
		method = "updateSwingTime",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getCurrentSwingDuration()I")
	)
	private int skycore$customSwingSpeed(int original) {
		if (ItemScaleAnimation.isActive() && ItemScaleAnimation.getIgnoreEffects()) {
			return ItemScaleAnimation.getSpeed();
		}
		return original;
	}

	@Inject(method = "swing(Lnet/minecraft/world/InteractionHand;)V", at = @At("HEAD"), cancellable = true)
	private void skycore$preventReSwing(InteractionHand hand, CallbackInfo ci) {
		if (ItemScaleAnimation.shouldNotReSwing() && this.swinging) {
			ci.cancel();
		}
	}
}
