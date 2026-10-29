# LOGVEX CRYSTAL - Phase 3 Readiness (Phase 0 verification)

> Verified against the actual source tree at commit `64a6f38` on branch
> `feat/femboy-product-depth`, with a real `./gradlew build` (JDK 25 build JVM / Java 21
> toolchain). Build result: **BUILD SUCCESSFUL, 389 tests, 0 failures, 0 errors**.
> Nothing in this document is copied from a previous plan; every claim was re-checked
> against the files named.

## 0. How this was verified

| Check | Command | Result |
| --- | --- | --- |
| Full build + tests | `JAVA_HOME=~/.local/logvex-jdk-25 ./gradlew build` | BUILD SUCCESSFUL |
| Test totals | parse `build/test-results/test/*.xml` | 389 tests, 0 failures, 0 errors |
| Source size | `find src/main/java -name '*.java' \| wc -l` | 218 main classes |
| Test size | `find src/test -name '*.java' \| wc -l` | 53 test classes |
| Registered modules | `ModuleRegistry.EXPECTED_MODULE_COUNT` | 68, asserted by `ModuleRegistryTest` |

## 1. Subsystem state

### 1.1 Modules and lifecycle — **Verified**

- `module/Module.java` (358 lines): identity, typed settings, dependency/conflict metadata,
  a strict lifecycle (`onEnable`/`onDisable`/`onTick`/`onShutdown`), exception isolation in
  `tickSafely()`, throttled error reporting, and `enabledMillis()`.
- `module/ModuleManager.java`: registration with duplicate detection, ordered ticking,
  dependency resolution (`firstMissingDependency`), conflict detection
  (`firstActiveConflict`), keybind dispatch, and keybind-conflict resolution
  (`resolveKeybindConflict`, last-assignment-wins).
- `module/ModuleContext.java`: the injected service surface (module manager, combat, theme,
  HUD, notifications, config). Services never depend on modules, so the graph is acyclic.
- 68 concrete modules across combat/movement/player/render/hud/misc/world. `ModuleRegistryTest`
  pins the count.

**Gaps for Phase 3:** dependencies are declared as loose name strings with a single
"must be enabled" meaning; there is no typed service requirement, no mutually-exclusive mode,
no initialization-order or capability requirement, no circular-dependency detection, and no
runtime re-evaluation of a dependency when its target is disabled. Conflicts are likewise
symmetric-name only. There is no consolidated conflict *inspector* (only keybind conflicts
are surfaced).

### 1.2 Typed settings and persistence — **Verified**

- `setting/` has Boolean/Int/Double/String/Color/Enum/Keybind settings with validators,
  `visibleWhen` conditions, a global change hook (`Setting.onAnyChange`), and JSON codecs.
- `config/ConfigIo` gives atomic writes (temp + `ATOMIC_MOVE`), a `.bak` copy, malformed-file
  tolerance and a recovery listener. `ConfigManager` writes `modules.json` with a schema
  version and refuses a newer schema.
- Stores: appearance, hud, friends, waypoints, favorites, notifications, context, profiles.

**Gaps:** there is no single versioned "schema migration" path (each store carries its own
`SCHEMA_VERSION` and only refuses-newer); imported profiles/rules are validated per-store but
there is no cross-store migration or an explicit migration registry.

### 1.3 Entity tracking and target selection — **Verified**

- `entity/EntityQuery` (180 lines): one shared, interval-rebuilt, copy-on-write snapshot of
  immutable `EntityInfo` values; clears itself on disconnect. Threading is documented and
  correct (volatile publish, no live `Entity` retained).
- `entity/EntityTracker` (221 lines): spawn/despawn diffing, bounded per-entity history
  (access-ordered eviction), bounded event ring buffer, last-observed stamps, `clear()` on
  world change.
- `entity/EntityFilter`, `EntityCategory`, `EntityBrowserModel`: reusable visibility rules
  and a browser model.
- `combat/CombatService`: nearest/looked-at target selection, cached per-tick crystal damage.

**Gaps for Phase 3:** there is no unified *event stream* — `EntityTracker` produces
spawn/despawn events but nothing consumes them as a subscription; health/equipment change
events, target-changed events and inventory-completion events do not exist. History is not
configurable at runtime (fixed 512/16) and has no export. `EntityInfo` lacks an explicit
"observed vs estimated" marker beyond `lineOfSight`.

### 1.4 Rendering, HUD and ClickGUI — **Verified**

- `render/esp/` has a shared collect/render pipeline (`WorldEspCollector`,
  `WorldEspRenderer`, `WorldEspProvider`) with `Frustum` culling; ESP modules are providers,
  not independent scanners.
- `gui/HudManager` + `gui/hud/*` (18 elements) with anchor-based stacking, drag layout,
  persistence via `HudLayoutStore`, and `HudToggleModule` ownership of visibility.
- `gui/ClickGui.java` (1479 lines): Meteor-style category navigation, search, module cards,
  expandable settings, sliders, color picker, keybind screen.
- `theme/` (6 files): presets, variants, `ThemeSettings` with 20 live knobs, palette engine.

**Gaps for Phase 3:** the ClickGUI has no dependency/conflict indicators, no recent-activity
panel, no automation-rule editor, no entity browser entry (the browser is a separate screen),
no performance dashboard, no HUD layout presets, and no keyboard navigation. Some of these are
Phase 8 scope.

### 1.5 Baritone integration — **Verified**

- `integration/baritone/` (9 classes): a reflective bridge (no compile dependency), a
  Minecraft-free `BaritoneNavigationState` machine, bounded `BaritoneHistory`, immutable
  `BaritoneStatus` with explicit "unknown" sentinels, `BaritoneService` with a background
  probe and a client-thread poller, waypoint bridge, command, module, HUD element and screen.
- 6 Baritone test classes. `BaritoneServiceTest` drives the state machine against a fake bridge.

**Gaps for Phase 3 (Phase 5):** no navigation *dashboard* aggregation, no goal/destination
inspector, no configurable timeout/cancellation rules, no arrival-detection rule object (only
a `consumeGoalReached()` flag), no navigation profiles, no capability-discovery report beyond
availability, and no error-history surfacing in the UI.

### 1.6 Context-aware configuration — **Partial**

- `context/` (5 classes): `ContextRule` (dimension / server substring / low-health),
  `ContextSnapshot`, `ContextProfileService` (cooldown + in-combat guard + first-match-wins),
  `ContextStore` (versioned JSON), and a per-tick evaluation in `CrystalClient`.
- `ContextProfileServiceTest`, `ContextStoreTest`.

**Gaps:** contexts are limited to dimension/server/health. The mission asks for single-player
vs multiplayer, current GUI screen, player availability, available integrations and explicit
play mode. There is no profile *inheritance*, no per-setting override, no manual switch
history with revert-to-previous, and no profile *preview/validation* UI. `ProfileScreen`
exists but is minimal.

### 1.7 Diagnostics and crash recovery — **Partial**

- `logging/ModuleErrorLog` (bounded ring + per-module counts), `util/BestEffort` (report a
  distinct failure once), `Module.recordFailure`, `DiagnosticsModule` (`; diag`), and a
  smoke-test harness in `CrystalClient` (`-Dlogvex.smoke=true`).
- `ModuleErrorLogTest`, `BestEffortTest`.

**Gaps:** diagnostics are spread across these helpers; there is no single bounded, searchable,
severity-filterable log with export and retention limits, no scheduler/queue-depth counters,
no render-timing or cache-size counters, and no per-module diagnostics view.

### 1.8 Performance and resource management — **Partial**

- Shared `EntityQuery`/`WorldQuery` snapshots, `UpdateClock` rate limiting, bounded maps in
  `EntityTracker`, `Frustum` culling, `ArrayDeque`-bounded notification/error buffers.

**Gaps:** no diagnostic counters for expensive operations, no explicit cache-invalidation
registry, no measured baseline recorded in-repo, no shared task scheduler (each feature rolls
its own clock/thread).

### 1.9 Release protection and integrity — **Partial**

- `build.gradle` produces `remapJar` + `withSourcesJar()`; mixin metadata and Fabric
  entrypoints are standard. No obfuscation, no checksum generation in the build, no
  dependency audit output.

### 1.10 Automation engine — **Not implemented**

- There is **no `automation/` package**. The technical-debt register lists "combat and player
  automation tier" as a historical commit, but there is no rule engine (trigger → conditions →
  actions → verification), no rule editor, no rule persistence. Phase 4 is greenfield.

## 2. Critical defects found in Phase 0

None blocking; the build and all 389 tests pass. The following are the highest-value items
carried into Phase 3:

1. **No typed dependency model** (High, Phase 1): a module can only say "module X must be
   enabled". Services/integrations/capabilities and mutual exclusion cannot be expressed, and
   disabling a dependency does not re-evaluate its dependents.
2. **No shared action coordinator** (High, Phase 1): modules that act (AutoCrystal, Surround,
   Burrow, AutoMine, AutoPearl, FastPlace, ...) each request actions independently; there is no
   ownership/priority/cancellation/timeout layer, so two modules can drive the same input in
   one tick.
3. **No event stream** (Medium, Phase 3): `EntityTracker` events are drained only if someone
   polls; there is no subscription model, so automation cannot be event-driven.
4. **Automation absent** (High, Phase 4): the whole subsystem is missing.
5. **Context coverage is narrow** (Medium, Phase 2): only dimension/server/health.

## 3. Plan of record (vertical slices, each tested)

Build a small, complete, tested slice first, then expand — per the mission's final
instructions.

1. **Phase 1 slice:** `coordination/` — a typed `ModuleDependency` graph (services,
   integrations, modes, capabilities, circular detection) and a shared `ActionCoordinator`
   (owner, priority, precondition, cancellation, timeout, completion, error). Wire a conflict
   inspector and expose it via command + tests.
2. **Phase 3 slice:** `event/` — a bounded, deduplicated client event bus fed from
   `EntityTracker`/`CombatService`/`WorldQuery`, consumed by automation.
3. **Phase 4 slice:** `automation/` — rule model (trigger/conditions/actions/cooldown),
   deterministic evaluator with loop and rate guards, bounded execution history, JSON store,
   editor screen, command, and tests.
4. Then Phase 2 (context expansion), Phase 5 (Baritone intelligence), 6-13 as capacity allows,
   each with tests and honest status in the Phase 14 docs.

Each subsystem is classified **Verified / Implemented-unverified / Partial / Unsupported /
Not implemented** in the final deliverable docs.

## 4. Environment note

This container was reset between sessions: the JDK 25 and Gradle caches were gone. Recovery is
`./scripts/setup-jdk.sh` then `JAVA_HOME=~/.local/logvex-jdk-25 ./gradlew build`. There is no
display server, so the headless GUI smoke run (`-Psmoke`) cannot execute here; runtime
verification is limited to the pure-logic test source set.
