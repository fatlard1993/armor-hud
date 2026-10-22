# Armor HUD - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Installation

Install client-side only. Nothing goes on the server, and a server never knows it is there.
Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json`
(Java).

## Key Files

| File | Responsibility |
|------|---------------|
| `Main.java` | Entry point; puts the element in vanilla's armor slot and polls the config |
| `ArmorElement.java` | The row above the hearts, and the stack beside the hotbar with each piece's icon and durability bar |
| `ArmorWatch.java` | Notices a piece, or the offhand, taking a hit, on the client tick, for the animations |
| `mixin/HudMixin.java` | Hides vanilla's offhand slot while the shield is drawn with the armor |
| `mixin/LivingEntityMixin.java` | Hears the server's break event, so taking a piece off is not mistaken for it breaking |
| `Protection.java` | The share of a hit your armor, toughness and Protection stop, by `CombatRules`' formula |
| `ArmorConfig.java` | `config/armor-hud.json` |
| `integration/PandoricalSettings.java` | The mod menu page, when Pandorical is there |

## Building

Pandorical is **optional at runtime and required at build time**. `PandoricalSettings` names its
types directly, so javac needs its jar; at runtime it sits behind `FabricLoader.isModLoaded`, which
is why `fabric.mod.json` only suggests it.

```sh
git clone https://github.com/fatlard1993/pandorical ../pandorical
mc-build pandorical      # or: ./gradlew build -p ../pandorical
./gradlew build
```

## Where it goes

The element replaces Fabric's `ARMOR_BAR`, which Fabric calls from inside vanilla's health pass:
that is what makes it disappear exactly when vanilla's would.

Three things are copied from vanilla rather than read from it, and go stale silently on a version
that changes them:

- `ArmorElement.rowTop`: where `Hud.extractPlayerHealth` puts the armor row. Fabric does not pass
  vanilla's coordinates on, so the row's height is worked out again from the hearts.
- `HOTBAR_HALF` and `OFFHAND_SLOT`: the hotbar's and offhand slot's widths, which anchor the stack.
- `Protection.blocked`: `CombatRules.getDamageAfterAbsorb` and `getDamageAfterMagicAbsorb`. Check
  them with `javap -c` on a bump.

## What a green build does not tell you

The mixins bind at runtime against exact names - `Hud.extractItemHotbar` and the
`Player.getOffhandItem` call inside it, `LivingEntity.handleEntityEvent` and
`entityEventForEquipmentBreak` - and `"required": true` with `defaultRequire: 1` makes a stale one
a crash at launch rather than a quiet degrade. The gametest launching at all proves them.

`ArmorElement.takesShield` has to be false whenever this element is not drawn, or a shield vanishes
from both places. The hotbar draws in creative and the health pass does not; the
`canHurtPlayer` check is that gate, copied from vanilla, and goes stale like the rest.

## The pictures

`Showcase` is the gametest: it dresses the player in a mixed set with a shield in the offhand,
hits that set hard enough to break the boots and shoots it mid-animation, swaps the shield for a
torch to show vanilla's slot coming back, then an elytra with no helmet, then full netherite with
Protection IV. A shield breaking is not shot: it only breaks blocking, and the harness cannot hold
the use key. The row's numbers are worth checking by hand against the formula: 56-31%,
48-23% once the boots are gone, 16-3% and 93-87%.

The hit animations are timed in game ticks, not wall-clock time, which is what lets the test
catch them: it steps the game a tick at a time, and a frame-timed animation would have finished,
or never started, by the time the screenshot was taken.

```sh
xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew runClientGameTest
```

The frames land in `build/run/clientGameTest/screenshots`. The README's pictures are crops of
them, 760 by 260 from the bottom edge, 660 left of centre: `armor-row.png` of `mixed-set.png`,
`armor-hit.png` of `hit.png`, `armor-netherite.png` of `netherite.png`.

`generate_textures.py` draws the toughness icon (vanilla's armor icon in netherite's tones) and the
mod menu icon, both from the game's own textures read out of the client jar Loom caches, so it
needs one build first.
