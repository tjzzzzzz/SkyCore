package dev.skylite.mixin.client;

import dev.skylite.core.module.ServerPackControl;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * unpins hypixel's server pack from its forced top priority.
 *
 * vanilla marks server packs as fixed-position so they always sit above the
 * player's own packs and win every texture. we only want the legacy pack to win
 * where it has a texture, and hypixel's pack to keep filling in everything else,
 * so the server pack has to become a normal reorderable pack that can sit below
 * the legacy one. this is the same approach Server-Pack-Unlocker uses.
 *
 * gated on the feature being on, so with the toggle off vanilla ordering is
 * completely untouched.
 */
@Mixin(Pack.class)
public abstract class PackFixedPositionMixin {

	@Inject(method = "isFixedPosition", at = @At("HEAD"), cancellable = true)
	private void skylite$unpinServerPack(CallbackInfoReturnable<Boolean> cir) {
		Pack self = (Pack) (Object) this;
		if (self.getPackSource() == PackSource.SERVER && ServerPackControl.INSTANCE.isReordering()) {
			cir.setReturnValue(false);
		}
	}
}
