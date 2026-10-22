package justfatlard.armor_hud;

import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * What your armor does to a hit, worked out the way {@code CombatRules} does it.
 *
 * <p>Armor blocks up to four percent a point, at most eighty, and a harder hit wears that down;
 * toughness is how much of it survives a hard hit. Protection then takes four percent a level off
 * what is left, at most eighty. Only plain Protection is counted: fire, blast and projectile
 * protection each guard against one kind of hit, so no single number can carry them.
 */
public record Protection(int armor, float toughness, int protectionLevels) {

	/** The hard hit the range is quoted against: a full health bar. */
	public static final float HEAVY_HIT = 20F;

	public static Protection of(Player player) {
		int levels = 0;
		for (EquipmentSlot slot : ArmorWatch.HEAD_TO_FOOT) {
			ItemEnchantments enchantments = player.getItemBySlot(slot).getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
			for (var entry : enchantments.entrySet()) {
				if (entry.getKey().is(Enchantments.PROTECTION)) levels += entry.getIntValue();
			}
		}
		return new Protection(player.getArmorValue(), (float) player.getAttributeValue(Attributes.ARMOR_TOUGHNESS), levels);
	}

	/** The share of a hit of this size that never reaches your hearts, 0 to 1. */
	public float blocked(float damage) {
		float byArmor = Mth.clamp(armor - damage / (2F + toughness / 4F), armor * 0.2F, 20F) / 25F;
		float byEnchantment = Mth.clamp(protectionLevels, 0, 20) / 25F;
		return 1F - (1F - byArmor) * (1F - byEnchantment);
	}

	public boolean any() {
		return armor > 0 || protectionLevels > 0;
	}
}
