package justfatlard.armor_hud.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import justfatlard.armor_hud.ArmorElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Vanilla's offhand slot stands down while the shield in it is drawn with the armor instead. */
@Mixin(Hud.class)
abstract class HudMixin {

	// The hotbar reads the offhand once and skips both the slot frame and the item when it is empty.
	@ModifyExpressionValue(method = "extractItemHotbar", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/player/Player;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack armorHud$shieldGoesWithTheArmor(ItemStack offhand) {
		Minecraft mc = Minecraft.getInstance();
		return mc.getCameraEntity() instanceof Player player && ArmorElement.takesShield(mc, player) ? ItemStack.EMPTY : offhand;
	}
}
