package justfatlard.armor_hud.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.player.ChatVisiblity;

/**
 * The mod loads, a world comes up with it, and the pictures: a mixed set of armor - trimmed, dyed,
 * worn, nearly broken - the same set mid-hit, then an elytra with a gap where the helmet goes, then full netherite with
 * Protection IV, where toughness and the enchantment carry the heavy-hit share up near the light one.
 *
 * <p>Run it under xvfb-run; the frames land in build/run/clientGameTest/screenshots.
 */
public final class Showcase implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!FabricLoader.getInstance().isModLoaded("armor-hud-justfatlard")) {
			throw new AssertionError("armor-hud-justfatlard is not loaded: the test is testing nothing");
		}

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			world.getConnection().waitForChunksRender();

			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("time set noon");
			// The armor row is part of the survival HUD; creative has none.
			server.runCommand("gamemode survival @a");
			server.runCommand("effect give @a resistance infinite 255 true");
			// Putting on armor earns advancements, and their lines land in chat right over the row.
			context.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));

			server.runCommand("item replace entity @a armor.head with minecraft:diamond_helmet"
				+ "[trim={material:\"minecraft:gold\",pattern:\"minecraft:sentry\"}]");
			server.runCommand("item replace entity @a armor.chest with minecraft:leather_chestplate[dyed_color=3847130]");
			server.runCommand("item replace entity @a armor.legs with minecraft:netherite_leggings"
				+ "[damage=280,trim={material:\"minecraft:amethyst\",pattern:\"minecraft:rib\"}]");
			server.runCommand("item replace entity @a armor.feet with minecraft:iron_boots[damage=190]");
			server.runCommand("item replace entity @a weapon.offhand with minecraft:shield");
			server.runCommand("item replace entity @a hotbar.0 with minecraft:diamond_sword");
			context.waitTicks(100);
			shoot(context, "mixed-set");

			// A hit, caught mid-shake: every piece loses a chunk, and the boots, one point from
			// gone, break. Resistance soaks the hearts but armor wears before it is applied.
			server.runCommand("item replace entity @a armor.feet with minecraft:iron_boots[damage=194]");
			context.waitTicks(5);
			server.runCommand("damage @p 30 minecraft:mob_attack");
			context.waitTicks(3);
			shoot(context, "hit");
			context.waitTicks(40);

			// Something other than a shield in the offhand keeps vanilla's slot.
			server.runCommand("item replace entity @a weapon.offhand with minecraft:torch");
			context.waitTicks(5);
			shoot(context, "offhand-torch");
			server.runCommand("item replace entity @a weapon.offhand with minecraft:shield");

			server.runCommand("item replace entity @a armor.head with minecraft:air");
			server.runCommand("item replace entity @a armor.chest with minecraft:elytra[damage=300]");
			server.runCommand("item replace entity @a armor.legs with minecraft:golden_leggings");
			server.runCommand("item replace entity @a armor.feet with minecraft:chainmail_boots"
				+ "[trim={material:\"minecraft:redstone\",pattern:\"minecraft:coast\"}]");
			context.waitTicks(20);
			shoot(context, "elytra-gap");

			for (String slot : new String[] { "head", "chest", "legs", "feet" }) {
				String piece = switch (slot) {
					case "head" -> "helmet";
					case "chest" -> "chestplate";
					case "legs" -> "leggings";
					default -> "boots";
				};
				server.runCommand("item replace entity @a armor." + slot + " with minecraft:netherite_" + piece
					+ "[enchantments={\"minecraft:protection\":4}]");
			}
			context.waitTicks(20);
			shoot(context, "netherite");
		}
		// Closing a world still coming out of a pause deadlocks the harness's own tick lock.
		context.waitTicks(10);
	}

	private static void shoot(ClientGameTestContext context, String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name).withSize(1920, 1080).disableCounterPrefix());
	}
}
