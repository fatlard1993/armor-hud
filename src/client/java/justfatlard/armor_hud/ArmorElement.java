package justfatlard.armor_hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Your armor, in two places. Above the hearts, where vanilla's row was: armor points, toughness,
 * and a bar over the hearts showing how many of them a hit would cost. Beside the hotbar on the
 * left: what you are wearing, stacked head to foot, each piece's own icon over its durability bar,
 * shaking when it takes a hit, and a shield in the offhand beside the boots in the same style.
 *
 * <p>Sits in vanilla's armor slot so both are drawn exactly when vanilla's row would be - not in
 * creative, not with the HUD hidden. The stack always hugs whatever is beside it: the hotbar, or
 * vanilla's offhand slot while that holds something other than a shield. An empty offhand gets the
 * shield's outline, so the column is there either way.
 */
public final class ArmorElement implements HudElement {
	private static final Identifier ARMOR_ICON = Identifier.withDefaultNamespace("hud/armor_full");
	private static final Identifier TOUGHNESS_ICON = Identifier.fromNamespaceAndPath(Main.MOD_ID, "hud/toughness");
	/** Vanilla's empty-slot outlines from the inventory, in {@link ArmorWatch#WATCHED}'s order. */
	private static final Identifier[] EMPTY_SLOT = {
		Identifier.withDefaultNamespace("container/slot/helmet"),
		Identifier.withDefaultNamespace("container/slot/chestplate"),
		Identifier.withDefaultNamespace("container/slot/leggings"),
		Identifier.withDefaultNamespace("container/slot/boots"),
		Identifier.withDefaultNamespace("container/slot/shield") };

	private static final int HOTBAR_HALF = 91;
	private static final int OFFHAND_SLOT = 29;
	private static final int GAP = 3;
	/** Items are drawn at sixteen pixels; these at three quarters of that. */
	private static final float ICON_SCALE = 0.75F;
	private static final int ICON = 12;
	private static final int BAR_HEIGHT = 2;
	/**
	 * The bar sits over the icon's bottom pixel row, which armor art mostly leaves clear, and the
	 * next icon starts right under the bar.
	 */
	private static final int BAR_OFFSET = ICON - 1;
	private static final int PITCH = BAR_OFFSET + BAR_HEIGHT;
	/** Lines the bottom of the stack up with the bottom of the hotbar's items. */
	private static final int BOTTOM_MARGIN = 3;

	/** Vanilla's armor row is ten icons eight apart: 81 wide, one heart to an icon. */
	private static final int ROW_WIDTH = 81;
	private static final int HEART_PITCH = 8;
	private static final int SHIELD_HEIGHT = 3;
	/** The numbers over the bar, smaller than the hotbar's text so the row stays short. */
	private static final float NUMBERS_SCALE = 0.75F;
	private static final int SHIELD = 0xFFC6C7D2;
	private static final int SHIELD_LIGHT_HITS = 0x70C6C7D2;
	private static final int HEART_NOTCH = 0x70000000;
	private static final int SHARE_COLOR = 0xFFB0B0B0;

	private static final int TRACK = 0xC0000000;
	private static final int EMPTY_SLOT_TINT = 0x80FFFFFF;
	private static final int BROKEN_TINT = 0xFFFF4040;
	/** The empty part of a nearly broken piece's bar flashes pale: a red flash would merge with its red fill and read as full. */
	private static final int WARNING = 0xB0FFFFFF;
	private static final int BLINK_MILLIS = 400;

	private static final float SHAKE_PIXELS = 1.5F;

	private final HudElement vanilla;

	public ArmorElement(HudElement vanilla) {
		this.vanilla = vanilla;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		ArmorConfig config = ArmorConfig.get();
		if (!config.enabled || !(mc.getCameraEntity() instanceof Player player)) {
			vanilla.extractRenderState(graphics, delta);
			return;
		}

		float partialTick = delta.getGameTimeDeltaPartialTick(false);
		ItemStack[] pieces = new ItemStack[ArmorWatch.HEAD_TO_FOOT.length];
		boolean show = false;
		for (int i = 0; i < pieces.length; i++) {
			pieces[i] = player.getItemBySlot(ArmorWatch.HEAD_TO_FOOT[i]);
			// A piece that just broke keeps the stack up while it says so, even if it was the last one.
			show |= !pieces[i].isEmpty() || ArmorWatch.sinceBreak(i, partialTick) < ArmorWatch.BREAK_TICKS;
		}

		// The shield's column stays a moment after it breaks, to say so.
		boolean shield = takesShield(mc, player) || (showsShield(mc, config)
			&& ArmorWatch.sinceBreak(ArmorWatch.OFFHAND, partialTick) < ArmorWatch.BREAK_TICKS);

		if (config.showProtection) protectionRow(graphics, mc.font, player);
		// An empty offhand still gets the column, as the shield's outline; anything else in it keeps vanilla's slot.
		boolean shieldColumn = shield
			|| (showsShield(mc, config) && config.showEmptySlots && player.getOffhandItem().isEmpty());
		if (show || shield) stack(graphics, config, player, pieces, shieldColumn, partialTick);
	}

	/**
	 * Whether the offhand's shield is drawn here, and so not in vanilla's offhand slot: {@code HudMixin}
	 * asks the same question, so the shield is always in exactly one of the two places. The hotbar
	 * draws in creative and this element does not, which is what the game mode check is for.
	 */
	public static boolean takesShield(Minecraft mc, Player player) {
		return showsShield(mc, ArmorConfig.get()) && player.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS);
	}

	private static boolean showsShield(Minecraft mc, ArmorConfig config) {
		return config.enabled && config.shieldWithArmor && mc.gameMode != null && mc.gameMode.canHurtPlayer();
	}

	private static void stack(GuiGraphicsExtractor graphics, ArmorConfig config, Player player, ItemStack[] pieces,
			boolean shield, float partialTick) {
		int right = graphics.guiWidth() / 2 - HOTBAR_HALF - GAP;
		// Vanilla draws its offhand slot only when there is something in it for the slot to hold.
		if (!shield && !player.getOffhandItem().isEmpty() && player.getMainArm().getOpposite() == HumanoidArm.LEFT) {
			right -= OFFHAND_SLOT;
		}
		int shieldX = right - ICON;
		int x = shield ? shieldX - ICON - 1 : shieldX;
		int top = graphics.guiHeight() - BOTTOM_MARGIN - BAR_OFFSET - BAR_HEIGHT - (pieces.length - 1) * PITCH;
		boolean blinkOn = Util.getMillis() / BLINK_MILLIS % 2 == 0;

		for (int i = 0; i < pieces.length; i++) {
			cell(graphics, config, pieces[i], i, x, top + i * PITCH, config.showEmptySlots, partialTick, blinkOn);
		}
		// Beside the boots, where the bottom of the stack meets the hotbar's items.
		if (shield) {
			cell(graphics, config, player.getOffhandItem(), ArmorWatch.OFFHAND, shieldX, top + (pieces.length - 1) * PITCH,
				true, partialTick, blinkOn);
		}
	}

	/** One piece: its icon over its bar, or the slot's outline when it is empty. */
	private static void cell(GuiGraphicsExtractor graphics, ArmorConfig config, ItemStack piece, int slot, int x, int y,
			boolean outlineWhenEmpty, float partialTick, boolean blinkOn) {
		if (piece.isEmpty()) {
			float sinceBreak = ArmorWatch.sinceBreak(slot, partialTick);
			if (sinceBreak < ArmorWatch.BREAK_TICKS) {
				// Broken: the outline comes in red and pales back to the ordinary one.
				int tint = ARGB.srgbLerp(sinceBreak / ArmorWatch.BREAK_TICKS, BROKEN_TINT, EMPTY_SLOT_TINT);
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY_SLOT[slot], x, y, ICON, ICON, tint);
			} else if (outlineWhenEmpty) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY_SLOT[slot], x, y, ICON, ICON, EMPTY_SLOT_TINT);
			}
			return;
		}
		float hit = Math.min(1F, ArmorWatch.sinceHit(slot, partialTick) / ArmorWatch.HIT_TICKS);
		// A hard shake that settles: side to side, three times, smaller each time.
		float shake = hit >= 1F ? 0F : Mth.sin(hit * Mth.PI * 6F) * SHAKE_PIXELS * (1F - hit);
		icon(graphics, piece, x + shake, y);
		bar(graphics, config, piece, x, y + BAR_OFFSET, slot, hit, blinkOn);
	}

	private static void icon(GuiGraphicsExtractor graphics, ItemStack piece, float x, int y) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(ICON_SCALE, ICON_SCALE);
		graphics.item(piece, 0, 0);
		graphics.pose().popMatrix();
	}

	/**
	 * The bar under an item in the inventory, in the same green-to-red, so it means what that bar
	 * has always meant. A hit flashes it white, fading back to its colour. The chunk the hit took
	 * fades out after it too, though on most pieces that is under a pixel: a hit costs armor a few
	 * points of hundreds, which is why the flash is the whole bar.
	 */
	private static void bar(GuiGraphicsExtractor graphics, ArmorConfig config, ItemStack piece, int x, int top, int slot,
			float hit, boolean blinkOn) {
		float remaining = ArmorWatch.remaining(piece);
		boolean low = remaining * 100F < config.lowDurabilityPercent;
		graphics.fill(x, top, x + ICON, top + BAR_HEIGHT, low && blinkOn ? WARNING : TRACK);
		int filled = fillWidth(remaining);
		int color = ARGB.opaque(piece.getBarColor());
		if (hit < 1F) {
			int lostTo = fillWidth(ArmorWatch.beforeHit(slot));
			if (lostTo > filled) graphics.fill(x + filled, top, x + lostTo, top + BAR_HEIGHT, ARGB.white(1F - hit));
			color = ARGB.srgbLerp(hit, 0xFFFFFFFF, color);
		}
		graphics.fill(x, top, x + filled, top + BAR_HEIGHT, color);
	}

	/** A sliver for any durability at all: a piece one hit from breaking is still on. */
	private static int fillWidth(float remaining) {
		return remaining <= 0F ? 0 : Math.max(1, Math.round(remaining * ICON));
	}

	/**
	 * Armor points and toughness, and under them, over the hearts, a bar that is the hearts: a hit
	 * of a full health bar would cost you the hearts it does not cover. Solid is what survives that
	 * heavy hit; the paler run past it is the extra a light hit leaves you, since armor stops a
	 * bigger share of a smaller hit. The gap between them is what toughness closes.
	 */
	private static void protectionRow(GuiGraphicsExtractor graphics, Font font, Player player) {
		Protection protection = Protection.of(player);
		// Vanilla shows no row at zero armor.
		if (!protection.any()) return;

		int x = graphics.guiWidth() / 2 - HOTBAR_HALF;
		int row = rowTop(graphics, player);

		float light = protection.blocked(0F);
		float heavy = protection.blocked(Protection.HEAVY_HIT);
		int shieldTop = row + 9 - SHIELD_HEIGHT;
		graphics.fill(x, shieldTop, x + ROW_WIDTH, shieldTop + SHIELD_HEIGHT, TRACK);
		graphics.fill(x, shieldTop, x + Math.round(light * ROW_WIDTH), shieldTop + SHIELD_HEIGHT, SHIELD_LIGHT_HITS);
		graphics.fill(x, shieldTop, x + Math.round(heavy * ROW_WIDTH), shieldTop + SHIELD_HEIGHT, SHIELD);
		for (int heart = 1; heart < 10; heart++) {
			int notch = x + heart * HEART_PITCH;
			graphics.fill(notch, shieldTop, notch + 1, shieldTop + SHIELD_HEIGHT, HEART_NOTCH);
		}

		// Drawn scaled from the bottom left, two pixels clear of the bar.
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, shieldTop - 2);
		graphics.pose().scale(NUMBERS_SCALE, NUMBERS_SCALE);
		int y = -9;
		int cursor = iconAndValue(graphics, font, ARMOR_ICON, String.valueOf(protection.armor()), 0, y);
		cursor = iconAndValue(graphics, font, TOUGHNESS_ICON, wholeOrTenths(protection.toughness()), cursor + 2, y);

		int lightShare = Math.round(light * 100F);
		int heavyShare = Math.round(heavy * 100F);
		String share = lightShare == heavyShare ? lightShare + "%" : lightShare + "-" + heavyShare + "%";
		// Against the row's right edge, unless big numbers ahead of it push it out into the gap before the food.
		int shareX = Math.max(Math.round(ROW_WIDTH / NUMBERS_SCALE) - font.width(share), cursor + 3);
		graphics.text(font, share, shareX, y + 1, SHARE_COLOR, true);
		graphics.pose().popMatrix();
	}

	/** The icon, then its value; returns where the value ends. */
	private static int iconAndValue(GuiGraphicsExtractor graphics, Font font, Identifier icon, String value, int x, int y) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, x, y, 9, 9);
		graphics.text(font, value, x + 10, y + 1, 0xFFFFFFFF, true);
		return x + 10 + font.width(value);
	}

	/** Toughness comes in halves from some mods; vanilla's is always whole. */
	private static String wholeOrTenths(float value) {
		return value == Math.round(value) ? String.valueOf(Math.round(value)) : String.format(java.util.Locale.ROOT, "%.1f", value);
	}

	/**
	 * Vanilla's armor row, worked out the way {@code Hud.extractPlayerHealth} does: it climbs with
	 * every extra row of hearts, and the rows close up once there are enough of them.
	 */
	private static int rowTop(GuiGraphicsExtractor graphics, Player player) {
		float maxHealth = Math.max((float) player.getAttributeValue(Attributes.MAX_HEALTH), Mth.ceil(player.getHealth()));
		int absorption = Mth.ceil(player.getAbsorptionAmount());
		int heartRows = Mth.ceil((maxHealth + absorption) / 2F / 10F);
		int rowHeight = Math.max(10 - (heartRows - 2), 3);
		return graphics.guiHeight() - 39 - (heartRows - 1) * rowHeight - 10;
	}

}
