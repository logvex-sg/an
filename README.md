# LOGVEX CRYSTAL

A cute, pastel **crystal-PvP / anarchy** client for Minecraft **1.21.11** on Fabric.

LOGVEX CRYSTAL is a standalone client mod (not an addon or a theme pack). It wraps a
serious, responsive anarchy toolkit — crystal combat aids, world-space ESP and survival
utilities — in an original kawaii interface: pastel pink, baby blue, lavender and soft
purple on deep midnight gradients, with rounded panels, cat ears, paw prints, hearts and
stars.

All artwork, palettes and icons are original to this project. No third-party client's
proprietary interface or theme data is copied.

## What it is (and is not)

* **Is:** a purely client-side mod. It reads world state and draws an interface.
* **Is not:** a cheat that manipulates the server. It performs no external process
  interaction, injects nothing into other programs, and never sends anything to the server
  that the vanilla client would not.
* Commands are handled **locally**. Anarchy servers reject unknown `/` commands, so a line
  starting with `.lvx` (or `.crystal`) is intercepted before it is sent and never reaches
  the server.

## Building

Requires **JDK 21** for the toolchain and **JDK 25** to run Gradle (Loom 1.18.4).

```bash
./gradlew build          # compiles, runs the unit tests, produces the jars
./gradlew test           # unit tests only
./gradlew runClient      # launch a dev client
```

Output jars land in `build/libs/`:

* `logvex-crystal-1.21.11-<version>.jar` — the mod
* `logvex-crystal-1.21.11-<version>-sources.jar` — sources

### Headless GUI smoke test

A dev-only, opt-in driver exercises the ClickGUI, Appearance tab, theme switching, the HUD
editor and config persistence without a human at the keyboard:

```bash
xvfb-run -a ./gradlew runClient -Psmoke=340
```

It fails loudly (and stops the client) if a screen does not install, a palette fails to
rebuild, or a config file does not round-trip.

## Commands

All commands are local-only. Prefix `.lvx` or `.crystal`.

| Command | Purpose |
| --- | --- |
| `.lvx toggle <module>` | Enable/disable a module |
| `.lvx bind <module> <key>` | Set a module keybind |
| `.lvx set <module> <setting> <value>` | Change any module setting |
| `.lvx preset <name>` / `theme` / `variant <dark\|light>` | Theme selection |
| `.lvx accent <#RRGGBB\|reset>` / `secondary ...` | Accent colours |
| `.lvx opacity\|gradient\|glow\|scale\|animation <0..1>` | Appearance sliders |
| `.lvx radius <0..12>` | Corner rounding |
| `.lvx decorations\|cat\|motion\|blur\|cursor\|sounds\|splash <on\|off>` | Feature toggles |
| `.lvx waypoint <add\|remove\|list\|clear> [name] [x y z]` | Manage waypoints |
| `.lvx friend <add\|remove\|list> [name]` | Manage the friend list |
| `.lvx hud <element> <on\|off>` / `hudedit` / `gui` | HUD control |
| `.lvx save` / `load` / `list [category]` / `version` | Config & info |

## Chest coordinate logger (local only)

The `ChestLogger` module counts chests and trapped chests in the chunks the client already
has loaded, groups nearby ones into clusters, and raises a **local** notification when a
cluster reaches the threshold (30 chests by default). Each find records the dimension, the
cluster centre and bounds, the chest count, the discovery time, and the names of players
the client can already see nearby.

This feature is deliberately **local-only**. There is no webhook, no upload, and no network
code of any kind in it: it never transmits positions anywhere, and it has no hidden or
remotely triggered mode. It reads only block-entity *types* in loaded chunks (never chest
contents), and it never forces chunks to load. Settings: scan interval, threshold, cluster
radius, vertical tolerance, minimum cluster size, duplicate radius, cooldown, player radius,
notification toggle and chunk radius.

## Themes

Presets: **Default, Femboy, Sakura Pink, Cotton Candy, Lavender Dream, Midnight Kitty,
Soft Boy**, each in a **dark** or **light** pastel variant. Every appearance knob —
accent colours, background opacity, gradient/glow intensity, corner radius, UI scale,
animation speed, decorations, cat cosmetics, reduced motion, blur, custom cursor, GUI
sounds, splash text — is persisted to `config/logvex-crystal/appearance.json` and applied
live, with no restart.

## Configuration files

Written under `<game>/config/logvex-crystal/`:

| File | Contents |
| --- | --- |
| `appearance.json` | Theme + appearance settings |
| `modules.json` | Module enabled state and settings |
| `hud.json` | HUD element layout (position, scale, opacity) |
| `friends.json` | Friend list |
| `waypoints.json` | Saved waypoints |

All stores are schema-versioned, written atomically, skip malformed entries on load, and
refuse a newer schema so a future version's data is never silently downgraded.

## Project layout

```
src/main/java/com/logvex/crystal/
  client/     entrypoint and composition root
  module/     feature modules by category (combat, movement, player, render, hud, misc)
  render/     drawing helpers, screen projection, world-space ESP
  gui/        ClickGUI, HUD manager, HUD editor, widgets
  theme/      presets, settings, engine, immutable palette
  config/     JSON stores
  combat/     crystal damage model and target tracking
  cosmetic/   paw-print particles and UI sounds
  command/    the .lvx command system
  social/     friend list
  scan/       local chest counting + clustering (ChestLogger)
  util/       maths, animation, waypoints
src/test/java/  pure-logic unit tests (no Minecraft runtime needed)
```

The unit-test source set deliberately exercises only Minecraft-independent logic (the theme
engine, colour maths, the crystal-damage model, config codecs, ESP projection and hole
detection), so the suite runs without a client.

## License

MIT. See [LICENSE](LICENSE).
