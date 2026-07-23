package dev.skylite.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import dev.skylite.core.module.mining.AbilityAlert;
import dev.skylite.core.module.mining.BreakResetFix;
import dev.skylite.core.module.mining.EndNodeHighlight;
import dev.skylite.core.module.mining.GhostVision;
import dev.skylite.core.module.mining.ScathaMining;
import dev.skylite.core.skyblock.TabListCache;
import dev.skylite.core.stats.ServerStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

	@Inject(method = "handleSetTime", at = @At("TAIL"))
	private void skylite$trackTps(ClientboundSetTimePacket packet, CallbackInfo ci) {
		ServerStats.INSTANCE.onTimeSync(packet.gameTime());
	}

	@Inject(method = "handlePongResponse", at = @At("TAIL"))
	private void skylite$trackPing(ClientboundPongResponsePacket packet, CallbackInfo ci) {
		ServerStats.INSTANCE.onPong(packet.time());
	}

	@Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
	private void skylite$inventory(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
		if (Minecraft.getInstance().gui.screen() == null) {
			AbilityAlert.INSTANCE.onInventory(packet.getItem());
			BreakResetFix.INSTANCE.onInventory(packet.getSlot(), packet.getItem());
		}
	}

	@Inject(
		method = "handleParticleEvent",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
			shift = At.Shift.AFTER
		),
		cancellable = true
	)
	private void skylite$particle(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
		if (dev.skylite.core.module.general.NoRender.INSTANCE.shouldCancelParticle(packet)) {
			ci.cancel();
			return;
		}
		EndNodeHighlight.INSTANCE.onParticle(packet);
	}

	@SuppressWarnings("unchecked")
	@Inject(
		method = "handleSetEntityData",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/network/syncher/SynchedEntityData;assignValues(Ljava/util/List;)V",
			shift = At.Shift.AFTER
		)
	)
	private void skylite$entityData(ClientboundSetEntityDataPacket packet, CallbackInfo ci, @Local Entity ent) {
		if (ent instanceof ArmorStand) {
			for (SynchedEntityData.DataValue<?> entry : packet.packedItems()) {
				if (entry.serializer().equals(EntityDataSerializers.OPTIONAL_COMPONENT) && entry.value() != null) {
					((Optional<Component>) entry.value()).ifPresent(value -> ScathaMining.INSTANCE.onNamed(ent, value));
					break;
				}
			}
		}
		if (ent instanceof LivingEntity || ent instanceof ItemEntity) {
			GhostVision.INSTANCE.onEntity(ent);
		}
	}

	@Inject(method = "handlePlayerInfoUpdate", at = @At("TAIL"))
	private void skylite$tabList(ClientboundPlayerInfoUpdatePacket packet, CallbackInfo ci) {
		TabListCache.INSTANCE.markDirty();
	}
}
