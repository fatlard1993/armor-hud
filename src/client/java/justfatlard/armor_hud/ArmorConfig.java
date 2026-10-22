package justfatlard.armor_hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import justfatlard.armor_hud.integration.PandoricalSettings;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The settings, in {@code config/armor-hud.json}.
 *
 * <p>Written out in full whenever the file on disk is missing anything, so every knob is in the
 * file, and re-read whenever the file changes.
 */
public final class ArmorConfig {

	/** False hands the armor back to vanilla's row of ten chestplates. */
	public boolean enabled = true;
	/** Above the hearts: armor points, toughness, and the bar showing what they are worth in hearts. */
	public boolean showProtection = true;
	/** A shield in the offhand is drawn beside the armor, in the same style, instead of in vanilla's offhand slot. */
	public boolean shieldWithArmor = true;
	/** The inventory's faint outline where a piece could be and is not, so a missing helmet reads as missing. */
	public boolean showEmptySlots = true;
	/** A piece with less durability left than this, in percent, blinks red. 0 never blinks. */
	public int lowDurabilityPercent = 10;

	// --- loading ---

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "armor-hud.json";

	private static ArmorConfig current = new ArmorConfig();
	private static long lastModified = Long.MIN_VALUE;

	public static ArmorConfig get() { return current; }

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	/** Re-read the file if it has been touched since the last look. */
	public static void reloadIfChanged() {
		Path path = path();
		try {
			if (!Files.exists(path)) {
				current = new ArmorConfig();
				write(path, current);
				return;
			}

			long modified = Files.getLastModifiedTime(path).toMillis();
			if (modified == lastModified) return;
			lastModified = modified;

			String text = Files.readString(path);
			ArmorConfig loaded = GSON.fromJson(text, ArmorConfig.class);
			if (loaded == null) return;
			current = loaded;

			// An upgrade adds keys the old file has never heard of; write them in so they can be found.
			String canonical = GSON.toJson(loaded);
			if (!canonical.equals(text)) write(path, loaded);

			PandoricalSettings.changed();
		} catch (Exception e) {
			Main.LOGGER.warn("Could not read {} - keeping the settings already loaded", FILE_NAME, e);
		}
	}

	/** Write the current settings out, for a change made in the mod menu. The file stays the source of truth. */
	public static void save() {
		try {
			write(path(), current);
		} catch (Exception e) {
			Main.LOGGER.warn("Could not write {}", FILE_NAME, e);
		}
	}

	private static void write(Path path, ArmorConfig config) throws java.io.IOException {
		Files.createDirectories(path.getParent());
		Files.writeString(path, GSON.toJson(config));
		lastModified = Files.getLastModifiedTime(path).toMillis();
	}
}
