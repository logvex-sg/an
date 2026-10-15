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
  starting with `;` (the legacy `.lvx` / `.crystal` still work) is intercepted before it is
  sent and never reaches the server.

## Building

Requires only a **JDK** on your `PATH`; Gradle provisions everything else.

```bash
./gradlew build          # compiles, runs the unit tests, produces the jars
./gradlew test           # unit tests only
./gradlew runClient      # launch a dev client
```

Fabric Loom 1.18.4 must run the build on **Java 25**, while the mod compiles against the
**Java 21** toolchain Minecraft 1.21.11 targets. Both are declared in the build, so even if
your default `java` is 17 or 21, `gradle/gradle-daemon-jvm.properties` makes Gradle download
and use a Java 25 JVM for itself, and the Foojay resolver in `settings.gradle` fetches the
Java 21 toolchain. No manual `JAVA_HOME` juggling is needed.

If your network blocks the JDK download (or you are offline), fetch a JDK 25 once with the
helper and point Gradle at it:

```bash
./scripts/setup-jdk.sh
export JAVA_HOME="$HOME/.local/logvex-jdk-25"
```

Output jars land in `build/libs/`:

* `logvex-crystal-1.21.11-<version>.jar` — the mod
* `logvex-crystal-1.21.11-<version>-sources.jar` — sources

### Headless GUI smoke test

A dev-only, opt-in driver exercises the ClickGUI, Appearance tab, theme switching, the HUD
editor and config persistence without a human at the keyboard:

```bash
xvfb-run -a ./gradlew runClient -Psmoke=440
```

It fails loudly (and stops the client) if a screen does not install, a palette fails to
rebuild, or a config file does not round-trip.

## Commands

All commands are local-only. Prefix `;` or `.crystal`.

| Command | Purpose |
| --- | --- |
| `; gui` / `; clickgui` | Open the ClickGUI |
| `; category <name>` | Open the ClickGUI on a category |
| `; appearance` / `; favorites` / `; recent` | Open the ClickGUI on that page |
| `; keybinds` | Open the keybind editor |
| `; profile <list\|save\|overwrite\|load\|delete\|starters\|gui>` | Manage configuration profiles |
| `; favorite <module>` | Star/unstar a module |
| `; toggle <module>` | Enable/disable a module |
| `; bind <module> <key>` | Set a module keybind |
| `; set <module> <setting> <value>` | Change any module setting |
| `; preset <name>` / `theme` / `variant <dark\|light>` | Theme selection |
| `; accent <#RRGGBB\|reset>` / `secondary ...` | Accent colours |
| `; opacity\|gradient\|glow\|scale\|animation <0..1>` | Appearance sliders |
| `; radius <0..12>` | Corner rounding |
| `; decorations\|cat\|motion\|blur\|cursor\|sounds\|splash <on\|off>` | Feature toggles |
| `; easteregg <on\|off>` | Toggle the hidden cosmetic |
| `; waypoint <add\|remove\|list\|clear> [name] [x y z]` | Manage waypoints |
| `; friend <add\|remove\|list> [name]` | Manage the friend list |
| `; hud <element> <on\|off>` / `hudedit` / `gui` | HUD control |
| `; save` / `load` / `list [category]` / `version` | Config & info |
| `; assets [list\|reload]` | Report the asset pack, or clear the texture cache |

## ClickGUI

The ClickGUI is the control surface for the entire client — every module, setting,
keybind, HUD element, appearance knob and config profile. Open it with `; gui` or the
`Right Shift` keybind.

* **Pages:** Modules, Appearance, Favourites and Recent, selected from the sidebar.
* **Modules:** rounded cards per module with animated toggles, a favourite star, a
  right-click context menu (toggle, bind, favourite, open the category) and hover tooltips.
  Settings expand inline; sliders, dropdowns, keybind capture, colour pickers and toggles
  are all editable in place. Shift-click a setting to reset it to its default.
* **Appearance:** theme preset grid, accent colour pickers, opacity/gradient/glow/radius/
  scale/animation sliders, decorative + cat-cosmetic toggles, reduced motion, blur, custom
  cursor, GUI sounds, splash text, light/dark variant and the easter-egg toggle. Every
  change applies live, with no restart, and persists to disk.
* **Profiles:** save, load, rename, duplicate, delete, export and import named
  configuration snapshots (see below).

Module keybinds are handled by a small mixin on the vanilla keyboard: a bound key toggles
its module while no screen is open, so it never steals input from the GUI or chat.

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

## ESP and world modules

The **World** category groups read-only world tools. A shared world-query layer caches the
block entities of loaded chunks once per tick (`world/WorldQuery`), and the ESP filter and
block-label helpers are shared by every render module, so the ESP set stays small and
consistent. The block-entity ESP modules never force chunks to load and never read
container contents — only the block-entity type and position.

* **Render:** PlayerEsp, MobEsp, ItemEsp, ProjectileEsp, ChestEsp, EnderChestEsp, ShulkerEsp,
  SpawnerEsp, BedEsp, PortalEsp and BlockEsp, on a shared block-entity ESP base.
* **World readouts:** `WorldInfo` supplies biome, dimension, time, light level and ping to
  the HUD; `BlockScanner` finds nearby blocks for the ESP modules.

## HUD

HUD elements are toggled by matching modules and laid out by the HUD editor (position,
scale and opacity per element). Alongside the watermark, module list, target HUD and crystal
damage indicator, the readouts include biome, time, light level, dimension/ping, entity
counts, armour durability, potion effects, speed and a WASD keystroke overlay. Compact
readouts are built from a shared `SimpleHudElement`, so they share the same rounded panel,
spacing and opacity handling.

## Themes

Presets: **Default, Femboy, Sakura Pink, Cotton Candy, Lavender Dream, Midnight Kitty,
Soft Boy, Sakura Femboy, Sakura Cyber**, each in a **dark** or **light** pastel variant.
Every appearance knob — accent colours, background opacity, gradient/glow intensity, corner
radius, UI scale, animation speed, decorations, cat cosmetics, reduced motion, blur, custom
cursor, GUI sounds, splash text — is persisted to `config/logvex-crystal/appearance.json`
and applied live, with no restart.

## Configuration profiles

A profile is a named snapshot of all three configuration layers (modules, HUD layout and
appearance). Manage them from the **Profiles** screen in the ClickGUI or the `; profile`
command:

```
; profile save <name>       # snapshot the current setup (refuses to clobber)
; profile overwrite <name>  # replace an existing profile
; profile load <name>       # apply a saved profile or a starter
; profile delete <name>
; profile list              # saved profiles and the starter names
; profile starters
; profile gui               # open the Profiles screen
```

Rename, duplicate, export and import are available from the **Profiles** screen.

Four starter profiles ship with the client — **Combat**, **Anarchy Survival**, **Minimal
HUD** and **Pastel Aesthetics**. They are templates you load on demand and are never applied
automatically. Profile files are schema-versioned, written atomically, and a corrupt file is
moved aside (`*.json.corrupt`) rather than silently dropped; a profile written by a newer
build is refused rather than misread. Export documents use the `.export.json` suffix and are
kept out of the profile list.

## Assets

The interface ships with an original, reproducible PNG pack (78 files, ~47 KiB) generated by
the Python toolchain in `tools/`:

```
tools/assetlib.py    canvas primitives (blur, rounded shapes, SVG export)
tools/icons.py       the shape library and the icon name map
tools/branding.py    wordmark, logo, cat ears, sparkles
tools/gen_assets.py  driver: writes the pack + a contact sheet
```

Regenerate with `python3 tools/gen_assets.py`; the contact sheet lands in
`docs/assets-contact-sheet.png`. `AssetManifest` is the single source of truth for the pack,
`AssetRegistry` loads and caches textures lazily from the mod's own resources, and `Assets`
draws them — every call falls back to the equivalent procedural motif in `RenderUtil` when a
texture is missing, so the client is complete with or without the pack. Tinted icons follow
the active accent and are re-tinted on a theme change.

## Configuration files

Written under `<game>/config/logvex-crystal/`:

| File | Contents |
| --- | --- |
| `appearance.json` | Theme + appearance settings |
| `modules.json` | Module enabled state, keybinds and settings |
| `hud.json` | HUD element layout (position, scale, opacity) |
| `friends.json` | Friend list |
| `waypoints.json` | Saved waypoints |
| `favorites.json` | Favourite modules and recently-used list |
| `profiles/*.json` | Named configuration profiles |

All stores are schema-versioned, written atomically, skip malformed entries on load, and
refuse a newer schema so a future version's data is never silently downgraded.

## Project layout

```
src/main/java/com/logvex/crystal/
  client/     entrypoint and composition root
  module/     feature modules by category (combat, movement, player, world, render, hud, misc)
  render/     drawing helpers, screen projection, world-space ESP
  world/      cached block-entity snapshot, world readouts and block scanning
  gui/        ClickGUI, HUD manager, HUD editor, keybind editor, profile manager
  assets/     asset manifest, lazy texture registry and fallback draw helpers
  theme/      presets, settings, engine, immutable palette
  config/     JSON stores, configuration profiles, favourites, starters
  mixin/      keyboard keybind dispatch, splash/title tweaks
  combat/     crystal damage model and target tracking
  cosmetic/   paw-print particles and UI sounds
  command/    the ; command system
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
