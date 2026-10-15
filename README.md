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
  module/     feature modules by category (combat, movement, player, render, hud, misc)
  render/     drawing helpers, screen projection, world-space ESP
  gui/        ClickGUI, HUD manager, HUD editor, keybind editor, profile manager
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
