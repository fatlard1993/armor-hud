package justfatlard.armor_hud;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Watches the armor slots and the offhand tick by tick for a piece taking a hit, and hears from
 * {@code LivingEntityMixin} when one breaks, so the HUD can make something of it.
 *
 * <p>On the tick rather than in the frame: a frame is not drawn on every tick - minimized, paused,
 * or a test stepping the game - and an event that is only seen when a frame happens is missed by
 * the frames that did not. Timed in ticks for the same reason, as vanilla's heart blink is.
 */
public final class ArmorWatch {
	private ArmorWatch() {}

	public static final EquipmentSlot[] HEAD_TO_FOOT = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	/** The armor, then the offhand, for a shield. Indexes into everything here. */
	public static final EquipmentSlot[] WATCHED = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND };
	public static final int OFFHAND = 4;

	/** How long a hit and a break are shown for. */
	public static final int HIT_TICKS = 14;
	public static final int BREAK_TICKS = 30;

	private static final int NEVER = Integer.MIN_VALUE / 2;

	private static int ticks;
	private static Entity watching;
	private static final Item[] lastItem = new Item[WATCHED.length];
	private static final int[] lastDamage = new int[WATCHED.length];
	private static final int[] hitAt = { NEVER, NEVER, NEVER, NEVER, NEVER };
	private static final int[] brokeAt = { NEVER, NEVER, NEVER, NEVER, NEVER };
	private static final float[] beforeHit = new float[WATCHED.length];

	/**
	 * A piece that is the same item with more damage than last tick took a hit. Anything else - a
	 * swap, a repair, putting one on, a new world - is quiet.
	 */
	public static void tick(Minecraft mc) {
		ticks++;
		if (!(mc.getCameraEntity() instanceof Player player)) {
			watching = null;
			return;
		}
		boolean same = player == watching;
		watching = player;
		for (int slot = 0; slot < WATCHED.length; slot++) {
			ItemStack piece = player.getItemBySlot(WATCHED[slot]);
			Item item = piece.isEmpty() ? null : piece.getItem();
			int damage = piece.isEmpty() ? 0 : piece.getDamageValue();
			if (same && item != null && item == lastItem[slot] && damage > lastDamage[slot]) {
				// A hit landing mid-animation carries on from what the bar showed before the first.
				if (ticks - hitAt[slot] >= HIT_TICKS) beforeHit[slot] = remaining(piece.getMaxDamage(), lastDamage[slot]);
				hitAt[slot] = ticks;
			}
			lastItem[slot] = item;
			lastDamage[slot] = damage;
		}
	}

	public static void broke(int slot) {
		brokeAt[slot] = ticks;
	}

	/** Ticks since the piece in this slot was last hit, with the frame's fraction of a tick. */
	public static float sinceHit(int slot, float partialTick) {
		return ticks - hitAt[slot] + partialTick;
	}

	public static float sinceBreak(int slot, float partialTick) {
		return ticks - brokeAt[slot] + partialTick;
	}

	/** The durability the piece had before its last hit, 0 to 1. */
	public static float beforeHit(int slot) {
		return beforeHit[slot];
	}

	public static float remaining(ItemStack piece) {
		if (!piece.isDamageableItem()) return 1F;
		return remaining(piece.getMaxDamage(), piece.getDamageValue());
	}

	private static float remaining(int maxDamage, int damage) {
		return maxDamage <= 0 ? 1F : 1F - (float) damage / maxDamage;
	}
}
