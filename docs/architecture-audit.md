# LOGVEX CRYSTAL - Architecture Audit

> Generated as part of the advanced-engineering pass. Every claim here was verified against
> the source tree and a real `./gradlew build` at commit `984b3f8`.

## 1. Toolchain and version matrix (verified)

| Component | Version | Source of truth |
| --- | --- | --- |
| Minecraft | 1.21.11 | `gradle.properties` `minecraft_version`, `fabric.mod.json` |
| Mappings | Yarn `1.21.11+build.6` (v2) | `gradle.properties` `yarn_mappings` |
| Fabric Loader | 0.19.5 | `gradle.properties` `loader_version` |
| Fabric API | 0.141.6+1.21.11 | `gradle.properties` `fabric_version` |
| Fabric Loom | 1.18.4 | `gradle.properties` `loom_version` |
| Gradle | 9.8.1 (wrapper) | `gradle/wrapper` |
| Build JVM | Java 25 (Loom requirement) | `gradle/gradle-daemon-jvm.properties`, `scripts/setup-jdk.sh` |
| Mod toolchain | Java 21 | `gradle.properties` `java_version`, `build.gradle` toolchain |
| Mod id / package | `logvex-crystal` / `com.logvex.crystal` | `fabric.mod.json` |

Baseline verification (this machine, JDK 25 restored via `scripts/setup-jdk.sh`):

```
./gradlew build   ->  BUILD SUCCESSFUL
tests             ->  47 classes / 345 tests / 0 failures / 0 errors / 0 skipped
artifacts         ->  build/libs/logvex-crystal-1.21.11-1.0.0.jar (+ -sources.jar)
```

## 2. Entry points and initialization order

Single client entrypoint `com.logvex.crystal.client.CrystalClient` (`ClientModInitializer`).
`onInitializeClient()` builds the service graph in a fixed order and then wires Fabric callbacks:

1. `AutoSave` (debounced, 1500 ms) + global `Setting.onAnyChange` hook + `ConfigIo.onRecovery`.
2. `ThemeManager` + `AppearanceStore` (appearance loaded **before** anything reads a palette).
3. Resource reloader for the asset pack; `FriendList`, `WaypointStore`.
4. `NotificationManager` + `NotificationStore`.
5. `ModuleManager`, `CombatService`, `CosmeticService`, `ModuleRegistry.registerAll` (66 modules).
6. `ConfigManager`, `HudManager` + `HudLayoutStore`, `ModuleContext` injection, `config.load()`.
7. `FavoritesStore`, `ProfileService`, `ContextStore`/`ContextProfileService`.
8. `CommandManager`, keybinds, cosmetics, Fabric callbacks, shutdown hook.

Fabric hooks used:

| Hook | Purpose |
| --- | --- |
| `ClientTickEvents.END_CLIENT_TICK` | combat tick, world/entity query tick, entity tracker, session stats, module tick, notifications, world-transition + context-profile handling, autosave |
| `ClientSendMessageEvents.ALLOW_CHAT` / `ALLOW_COMMAND` | intercept `;` / `.crystal` / `.lvx` lines **locally** so they never reach the server |
| `HudElementRegistry.attachElementAfter(MISC_OVERLAYS, ...)` | the whole Crystal HUD as one layer |
| `WorldRenderEvents.BEFORE_DEBUG_RENDER` | collect + draw world-space ESP in the world pass |
| `ClientLifecycleEvents.CLIENT_STOPPING` | flush autosave, `modules.shutdownAll()` |
| `KeyBindingHelper` | ClickGUI (Right Shift) + waypoint bookmark keys |
| `LivingEntityFeatureRendererRegistrationCallback` | optional player cosmetic feature renderer |
| `EntityModelLayerRegistry` | cosmetic model layer |
| `ResourceLoader.registerReloader` | asset pack reload |

Mixins (`logvex-crystal.mixins.json`, `required: true`, `compatibilityLevel: JAVA_21`):
`KeyboardMixin` (module keybind dispatch when no screen is open), `MinecraftClientAccessor`
(item-use cooldown), `SplashOverlayMixin` + `TitleScreenMixin` (splash/branding).

## 3. Core subsystems

| Subsystem | Location | Notes |
| --- | --- | --- |
| Module lifecycle | `module/Module.java`, `ModuleManager` | enable/disable/tick/shutdown, dependency + conflict resolution, keybind conflict resolution, per-module error isolation + throttled reporting |
| Typed settings | `setting/` (9 classes) | Boolean/Int/Double/String/Enum/Color/Keybind, groups, validator, visibility predicate, reset-to-default, global change hook |
| Config persistence | `config/` (10 classes) | schema-versioned JSON stores, atomic writes, corrupt-file quarantine (`*.json.corrupt`), newer-schema refusal, backups, profiles, starters, debounced autosave |
| Combat | `combat/` (8 classes) | `CombatService` target tracking, `CrystalDamage` model, `TargetProtection`, `PlacementEvaluator`, `CrystalPlanner`, `WorldInteract` |
| Entity intelligence | `entity/` (6 classes) | `EntityTracker` (spawn/despawn diff, bounded history), `EntityQuery` (per-tick snapshot), `EntityFilter`, `EntityInfo`, browser model |
| World queries | `world/` (4), `scan/` (8) | `WorldQuery` cached block-entity snapshot, `BlockScanner`, `SessionStats`, local chest cluster scan |
| Rendering | `render/` (10), `render/esp/` | `RenderUtil`, `ScreenProjector`, `ViewProjection`, `Frustum`, `WorldEspCollector`/`WorldEspRenderer`, shared ESP base classes |
| GUI | `gui/` (28) | `ClickGui` (Modules/Appearance/Favourites/Recent/Profiles), `HudManager`, `HudEditorScreen`, `KeybindScreen`, `EntityBrowserScreen`, `WaypointScreen`, reusable setting widgets |
| HUD | `gui/hud/` | watermark, module list, target HUD, crystal damage, 20+ readouts, notifications |
| Theme | `theme/` (6) | presets, variants, immutable `Palette`, `ThemeManager` with listeners, live apply |
| Commands | `command/` (3) | local-only `;` prefix, tokenizer, ~50 subcommands, tab-completion helper |
| Cosmetics / notifications | `cosmetic/`, `notification/` | paw particles, GUI sounds, cat accessories; bounded notification queue + toasts |
| Diagnostics | `logging/ModuleErrorLog`, `module/misc/DiagnosticsModule` | bounded error ring, FPS/tick/file/version report |

## 4. Module implementation matrix (66 registered modules)

Each row is a **real** implementation wired into the tick/render path; none is a placeholder.
"Settings" counts constructor `add(...)` declarations. Deps/Conflicts are declared via
`dependsOn` / `conflictsWith`.

### Combat

| Module | File | Settings | Deps | Conflicts | Description |
| --- | --- | ---: | --- | --- | --- |
| AutoCrystal | `combat/AutoCrystalModule.java` | 10 | - | - | Places, detonates and manages end crystals against a target. |
| AutoTotem | `combat/AutoTotemModule.java` | 2 | - | - | Reports totem count and the nearest threat. |
| HoverTotem | `combat/HoverTotemModule.java` | 2 | - | - | Predicts self-crystal damage at your feet. |
| CritAssist | `combat/CritAssistModule.java` | 2 | - | - | Shows when you are falling for a critical hit. |
| Surround | `combat/SurroundModule.java` | 3 | - | - | Walls the player in with obsidian against crystals. |
| Burrow | `combat/BurrowModule.java` | 1 | - | - | Places a block in your own space to escape. |
| AutoMine | `combat/AutoMineModule.java` | 4 | - | - | Mines blocks around a target to open them up. |
| CityMiner | `combat/CityMinerModule.java` | 3 | - | - | Mines the blocks trapping a target. |
| WebAura | `combat/WebAuraModule.java` | 4 | - | - | Places cobwebs on a target to slow them. |
| AutoPearl | `combat/AutoPearlModule.java` | 4 | - | - | Throws an ender pearl to escape lethal damage. |

### Movement

| Module | File | Settings | Deps | Conflicts | Description |
| --- | --- | ---: | --- | --- | --- |
| Sprint | `movement/SprintModule.java` | 1 | - | - | Automatically sprint while moving. |
| AutoWalk | `movement/AutoWalkModule.java` | 2 | - | - | Walks forward continuously in the facing direction. |
| FastStop | `movement/FastStopModule.java` | 1 | - | - | Stops movement immediately when keys are released. |

### Player

| Module | File | Settings | Deps | Conflicts | Description |
| --- | --- | ---: | --- | --- | --- |
| AutoEat | `player/AutoEatModule.java` | 3 | - | - | Eats food automatically when hunger is low. |
| FastPlace | `player/FastPlaceModule.java` | 1 | - | - | Reduces the local item-use cooldown. |
| Offhand | `player/OffhandModule.java` | 3 | - | - | Keeps a chosen item in the offhand slot. |
| AutoArmor | `player/AutoArmorModule.java` | 3 | - | - | Equips the best armour carried into empty or worn slots. |
| AutoRefill | `player/AutoRefillModule.java` | 7 | - | - | Tops up the hotbar with obsidian, crystals, gapples and pearls. |
| AutoGapple | `player/AutoGappleModule.java` | 4 | - | - | Eats a golden apple when health is low. |
| AutoMend | `player/AutoMendModule.java` | 3 | - | - | Mends damaged armour with experience bottles when safe. |

### Render

| Module | File | Settings | Deps | Conflicts | Description |
| --- | --- | ---: | --- | --- | --- |
| Tracers | `render/TracersModule.java` | 4 | - | - | Directional indicators toward players and crystals. |
| Search | `render/SearchModule.java` | 4 | - | - | Finds nearby blocks matching a name fragment. |
| Fullbright | `render/FullbrightModule.java` | 1 | - | - | Raises local brightness. |
| EntityEsp | `render/EntityEspModule.java` | 9 | - | - | Outlines entities with health, armour and distance. |
| CrystalEsp | `render/CrystalEspModule.java` | 4 | - | - | Highlights end crystals with damage estimates. |
| StorageEsp | `render/StorageEspModule.java` | 4 | - | - | Outlines nearby storage containers. |
| HoleEsp | `render/HoleEspModule.java` | 3 | - | - | Highlights safe holes, colour coded by strength. |
| WaypointEsp | `render/WaypointEspModule.java` | 3 | - | - | Marks saved waypoints in the world. |
| PlayerEsp | `render/PlayerEspModule.java` | 8 | - | - | Outlines other players with name and health labels. |
| MobEsp | `render/MobEspModule.java` | 6 | - | - | Outlines nearby mobs with a health label. |
| ItemEsp | `render/ItemEspModule.java` | 7 | - | - | Outlines dropped items with a name label. |
| ProjectileEsp | `render/ProjectileEspModule.java` | 5 | - | - | Outlines projectiles in flight. |
| ChestEsp | `render/ChestEspModule.java` | 1 | - | - | Outlines nearby storage containers with a type label. |
| EnderChestEsp | `render/EnderChestEspModule.java` | 0 | - | - | Outlines ender chests. |
| ShulkerEsp | `render/ShulkerEspModule.java` | 0 | - | - | Outlines shulker boxes. |
| SpawnerEsp | `render/SpawnerEspModule.java` | 1 | - | - | Outlines spawners (and optionally beacons, signs and lecterns). |
| BedEsp | `render/BedEspModule.java` | 4 | - | - | Highlights nearby beds. |
| PortalEsp | `render/PortalEspModule.java` | 4 | - | - | Highlights nether portal blocks. |
| BlockEsp | `render/BlockEspModule.java` | 5 | - | - | Highlights blocks matching a name fragment. |
| PlayerCosmetics | `render/PlayerCosmeticsModule.java` | 3 | - | - | Optional pastel accessories on players. |

### HUD

| Module | File | Settings | Deps | Conflicts | Description |
| --- | --- | ---: | --- | --- | --- |
| Watermark | `hud/WatermarkModule.java` | 0 | - | - | Shows the LOGVEX CRYSTAL watermark. |
| ModuleList | `hud/ModuleListModule.java` | 0 | - | - | Shows the list of enabled modules. |
| TargetHud | `hud/TargetHudModule.java` | 0 | - | - | Shows the pastel target HUD. |
| CrystalDamage | `hud/CrystalDamageModule.java` | 0 | - | - | Shows crystal damage indicators. |
| Coords | `hud/CoordsModule.java` | 0 | - | - | Shows the coordinates readout. |
| Performance | `hud/PerformanceModule.java` | 0 | - | - | Shows FPS and ping indicators. |
| Biome | `hud/BiomeModule.java` | 0 | - | - | Shows the current biome. |
| Time | `hud/TimeModule.java` | 0 | - | - | Shows the in-game time of day. |
| Light | `hud/LightModule.java` | 0 | - | - | Shows the light level at the player. |
| Network | `hud/NetworkModule.java` | 0 | - | - | Shows the dimension and ping. |
| Entities | `hud/EntitiesModule.java` | 0 | - | - | Shows nearby entity counts. |
| Armor | `hud/ArmorModule.java` | 0 | - | - | Shows armour points and durability. |
| Potion | `hud/PotionModule.java` | 0 | - | - | Shows active potion effects. |
| Speed | `hud/SpeedModule.java` | 0 | - | - | Shows the player's speed in blocks per second. |
| Keystrokes | `hud/KeystrokesModule.java` | 0 | - | - | Shows a WASD/mouse keystroke overlay. |
| CrosshairInfo | `hud/CrosshairModule.java` | 0 | - | - | Shows the block under the crosshair. |
| SessionStats | `hud/SessionModule.java` | 0 | - | - | Shows session time and distance walked. |
| EntityCounter | `hud/EntityCountModule.java` | 0 | - | - | Shows a count of nearby entities by category. |

### Misc

| Module | File | Settings | Deps | Conflicts | Description |
| --- | --- | ---: | --- | --- | --- |
| AntiAfk | `misc/AntiAfkModule.java` | 3 | - | - | Small periodic actions to avoid idle kicks. |
| AutoReconnect | `misc/AutoReconnectModule.java` | 2 | - | - | Reconnects to the last server after a disconnect. |
| Theme | `misc/ThemeModule.java` | 5 | - | - | Live appearance controls for the Crystal interface. |
| HudEditor | `misc/HudEditorModule.java` | 0 | - | - | Open the drag-and-drop HUD layout editor. |
| ChestLogger | `misc/ChestLoggerModule.java` | 10 | - | - | Counts chests in loaded chunks and clusters them locally. |
| Diagnostics | `misc/DiagnosticsModule.java` | 3 | - | - | Reports client health: errors, files, performance, version. |

### Player

| Module | File | Settings | Deps | Conflicts | Description |
| --- | --- | ---: | --- | --- | --- |
| DurabilityAlert | `player/DurabilityAlertModule.java` | 5 | - | - | Warns when an item is nearly broken. |

### World

| Module | File | Settings | Deps | Conflicts | Description |
| --- | --- | ---: | --- | --- | --- |
| WorldInfo | `world/WorldInfoModule.java` | 22 | - | - | A configurable world-information panel and the ; world command. |

## 5. Test coverage map (47 classes / 345 tests)

Pure-logic areas with dedicated tests: theme engine, colour maths, crystal damage + protection
+ placement, config codecs (appearance/modules/hud/friends/waypoints/favorites/notifications/
context), profiles + starters, autosave debounce, command prefix/tokenizer, module
manager + registry count, notifications (manager/store/layout/toast), ESP projection + frustum
+ hole finding, chest clustering/reporting, entity tracker/filter/info/browser, social friend
list, settings, animations, waypoints + navigation, session stats, asset manifest, cosmetics.

Minecraft-runtime paths (rendering, mixins, interaction) are exercised by the dev-only headless
smoke driver (`./gradlew runClient -Psmoke=440`), which opens the ClickGUI, switches every theme,
opens the HUD editor and round-trips the config, failing loudly on any install/render error.

## 6. Baritone integration status

**Absent.** There is no Baritone dependency, detection, command, GUI or test anywhere in the
tree (`grep -ri baritone src build.gradle gradle.properties` returns nothing). This is the
single largest gap against the mission spec and is implemented in the follow-up pass - see
`docs/baritone-integration.md`.
