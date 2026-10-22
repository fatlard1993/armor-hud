package justfatlard.armor_hud.integration;

import java.util.function.Consumer;
import java.util.function.Supplier;

import justfatlard.armor_hud.ArmorConfig;
import justfatlard.armor_hud.Main;
import net.fabricmc.loader.api.FabricLoader;

/**
 * This mod's settings in Pandorical's mod menu, when Pandorical is there.
 *
 * <p>The config file stays the source of truth: each setting reads the live config and writes a
 * change straight back to the file, and a file edit is pushed to the menu when the poll notices it.
 */
public final class PandoricalSettings {
	private PandoricalSettings() {}

	// Suggested rather than depended on, so nothing pins the version: a Pandorical whose API has
	// moved would throw out of onInitializeClient and take the whole mod down with it.
	private static boolean available = FabricLoader.getInstance().isModLoaded("pandorical");

	public static boolean present() { return available; }

	public static void register() {
		if (!available) return;
		try {
			Reach.register();
		} catch (LinkageError incompatible) {
			available = false;
			Main.LOGGER.warn("Pandorical is present but its settings API does not match; "
				+ "the mod menu page is off and the config file still works", incompatible);
		}
	}

	public static void changed() {
		if (!available) return;
		try {
			Reach.changed();
		} catch (LinkageError incompatible) {
			available = false;
		}
	}

	private static final class Reach {
		static void changed() {
			justfatlard.pandorical.client.api.PandoricalClientApi.settings().changed();
		}

		static void register() {
			var group = justfatlard.pandorical.client.api.PandoricalClientApi.settings()
				.group(Main.MOD_ID, "Armor HUD");

			toggle(group, "enabled", "Armor beside the hotbar", "Off brings back vanilla's row of chestplates above the hearts",
				() -> c().enabled, v -> c().enabled = v);
			toggle(group, "showProtection", "Armor and toughness", "Above the hearts: your armor points, toughness, and a bar showing how many hearts a big hit would leave you",
				() -> c().showProtection, v -> c().showProtection = v);
			toggle(group, "shieldWithArmor", "Shield with the armor", "A shield in your offhand sits beside your armor, durability bar and all, instead of in its own slot",
				() -> c().shieldWithArmor, v -> c().shieldWithArmor = v);
			toggle(group, "showEmptySlots", "Empty slots", "A faint outline where a piece could be worn and is not",
				() -> c().showEmptySlots, v -> c().showEmptySlots = v);
			group.number("lowDurabilityPercent", "Blink below, percent durability", "0 never blinks", 0, 50, 5,
				() -> c().lowDurabilityPercent, saving(v -> c().lowDurabilityPercent = v));
		}

		private static ArmorConfig c() { return ArmorConfig.get(); }

		private static void toggle(justfatlard.pandorical.client.api.ClientSettingsApi.Group group, String key,
				String label, String description, Supplier<Boolean> get, Consumer<Boolean> set) {
			group.toggle(key, label, description, get, saving(set));
		}

		/** Every change goes to the file, which is what the rest of the mod reads. */
		private static <T> Consumer<T> saving(Consumer<T> set) {
			return value -> {
				set.accept(value);
				ArmorConfig.save();
			};
		}
	}
}
