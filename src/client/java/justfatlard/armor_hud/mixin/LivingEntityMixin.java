package justfatlard.armor_hud.mixin;

import justfatlard.armor_hud.ArmorWatch;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The server says so when a piece breaks, with an entity event per slot: that, rather than a piece
 * going missing, which taking one off looks exactly like.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {

	@Shadow
	private static byte entityEventForEquipmentBreak(EquipmentSlot slot) {
		throw new AssertionError();
	}

	@Inject(method = "handleEntityEvent", at = @At("HEAD"))
	private void armorHud$noticeBreak(byte id, CallbackInfo ci) {
		if ((Object) this != Minecraft.getInstance().getCameraEntity()) return;
		for (int slot = 0; slot < ArmorWatch.WATCHED.length; slot++) {
			if (id == entityEventForEquipmentBreak(ArmorWatch.WATCHED[slot])) ArmorWatch.broke(slot);
		}
	}
}
