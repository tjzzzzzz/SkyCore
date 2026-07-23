package dev.skycore.mixin.client;

import dev.skycore.core.module.ServerPackControl;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Function;

@Mixin(Pack.Position.class)
public class PackPositionInsertMixin {

	@Inject(method = "insert", at = @At("HEAD"), cancellable = true)
	private <T> void skycore$positionServerPack(
			List<T> items, T item, Function<T, PackSelectionConfig> profileGetter, boolean listInverted,
			CallbackInfoReturnable<Integer> cir) {

		if (!ServerPackControl.INSTANCE.isReordering()) return;
		if (((Pack) item).getPackSource() != PackSource.SERVER) return;

		int i;
		for (i = 0; i < items.size(); i++) {
			Pack pack = (Pack) items.get(i);
			if (!pack.isFixedPosition() || pack.getDefaultPosition() != Pack.Position.TOP) {
				break;
			}
		}
		i += 1;
		items.add(i, item);
		cir.setReturnValue(i);
	}
}
