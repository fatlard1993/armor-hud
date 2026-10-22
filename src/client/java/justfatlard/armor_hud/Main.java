package justfatlard.armor_hud;

import justfatlard.armor_hud.integration.PandoricalSettings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The armor bar, redrawn as one bar per piece.
 *
 * <p>Client-side and nothing else: what is worn, how worn it is, its dye and its trim all reach the
 * client with the item, so a vanilla server has nothing to add.
 */
public class Main implements ClientModInitializer {

	public static final String MOD_ID = "armor-hud-justfatlard";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** How often to look at the config file's timestamp. */
	private static final int CONFIG_POLL_TICKS = 20;

	private static int ticksSinceConfigCheck = 0;

	@Override
	public void onInitializeClient() {
		ArmorConfig.reloadIfChanged();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (++ticksSinceConfigCheck < CONFIG_POLL_TICKS) return;
			ticksSinceConfigCheck = 0;
			ArmorConfig.reloadIfChanged();
		});
		ClientTickEvents.END_CLIENT_TICK.register(ArmorWatch::tick);

		PandoricalSettings.register();

		// Vanilla's slot rather than an overlay of our own, so it is drawn exactly when vanilla's
		// would be: not in creative, not with the HUD hidden, not on a mount that shows its hearts.
		HudElementRegistry.replaceElement(VanillaHudElements.ARMOR_BAR, ArmorElement::new);

		LOGGER.info("Armor HUD loaded (pandorical: {})", PandoricalSettings.present());
	}
}
