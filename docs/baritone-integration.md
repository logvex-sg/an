# LOGVEX CRYSTAL — Baritone Integration

> Verified against the source tree at commit `49b84bd` (branch `feat/femboy-product-depth`)
> and against Baritone's public API source (`cabaletta/baritone`, `master`). Baritone is an
> **optional** dependency: the client launches, builds and runs fully without it. Nothing in
> this document is copied from another version's documentation.

## 1. What this integration is

Baritone is treated as a first-class subsystem behind one narrow interface, never as a
hardcoded command wrapper and never as a compile-time dependency. The client reaches Baritone
only through `com.logvex.crystal.integration.baritone`:

| Type | Role |
| --- | --- |
| `BaritoneBridge` | The seam. Every Baritone call the client makes is declared here. Two implementations: the production reflective bridge and a test double. |
| `ReflectiveBaritoneBridge` | The production bridge. Baritone is not a compile dependency, so it reaches the API through **validated reflection**, isolated to this class. |
| `BaritoneReflection` | Defensive reflection helpers that name the missing member on failure, so a future Baritone rename surfaces as a clear "unsupported" diagnostic, not a `NoSuchMethodError`. |
| `BaritoneService` | The client's single point of contact. Owns the probe, the polling cadence (500 ms), the navigation model and notifications. |
| `BaritoneNavigationState` | Minecraft-free state machine: records transitions, detects goal completion. Fully unit-tested. |
| `BaritoneStatus` | Immutable snapshot with explicit `UNKNOWN` / `NO_COORD` sentinels — never a guessed value. |
| `BaritoneHistory` | Bounded (64-entry) log of navigation transitions and integration errors. |
| `BaritoneGoal` | Validated goal value type (coordinates bounded to the world limit; `GOAL_NEAR` requires radius ≥ 1). |
| `BaritoneWaypointBridge` | Turns a client waypoint into a goal, rejecting cross-dimension waypoints with a reason. |

## 2. Compatibility architecture

- **Detection.** `BaritoneService.ensureProbed()` starts a single daemon worker that runs
  `ReflectiveBaritoneBridge.probe()`. The probe loads `baritone.api.BaritoneAPI`,
  `baritone.api.IBaritone` and the primary `IBaritone` instance. Class loading is the only
  Baritone operation that can be slow (jar/disk scan), which is why it — and only it — runs
  off the client thread. The result is published through `AtomicBoolean`s; a probe is started
  at most once and never in parallel.
- **Version detection.** Best-effort read of the Fabric mod container `baritone` →
  `getVersion().getFriendlyString()`; falls back to `"unknown"` rather than guessing.
- **Failure policy.** A missing or incompatible Baritone degrades to an explicit
  `unavailable(reason)` everywhere (command, GUI, HUD). It never throws into the game loop and
  never blocks startup.
- **Reflection policy.** Reflection is used because Baritone is not a compile dependency. It
  is isolated to one package, every member is resolved through `BaritoneReflection` (which
  throws a descriptive `BaritoneException` naming the member), and every read/write is
  validated. No member is assumed to exist because "another version had it".

### 2.1 Verified API surface

Read directly from Baritone's API interfaces before this integration was written:

```
baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone()   -> IBaritone
IBaritone.getPathingBehavior()    -> IPathingBehavior
    isPathing() / hasPath() / getGoal() / getCurrent() / getInProgress()
    estimatedTicksToGoal() : Optional<Double>
IBaritone.getPathingControlManager() -> IPathingControlManager
    mostRecentCommand() : Optional<PathingCommand>   (commandType == REQUEST_PAUSE => paused)
IBaritone.getCommandManager()     -> ICommandManager.execute(String) : boolean
IBaritone.getCustomGoalProcess()  -> ICustomGoalProcess.setGoal(Goal) / path()
IBaritone.getFollowProcess()      -> IFollowProcess.follow(Predicate) / cancel()
baritone.api.pathing.goals.GoalBlock(int,int,int) / GoalNear(BlockPos,int) / GoalXZ(int,int)
baritone.api.Settings.byLowerName : Map<String, Setting<?>>   (Setting.value is a public field)
baritone.api.utils.SettingsUtil.settingValueToString(setting) / parseAndApply(settings, name, value)
baritone.api.process.PathingCommandType.{SET_GOAL_AND_PATH, REQUEST_PAUSE, CANCEL_AND_SET_GOAL, ...}
```

## 3. Interrogation and diagnostics

The `; baritone` command and the Baritone screen (`; baritone gui`) answer every question the
diagnostics spec requires, showing `Unknown` / `Unavailable` / not-exposed instead of inventing
values:

- Is Baritone installed? → `service.available()` / `service.unavailableReason()`
- Version → `status().version()`
- Is a goal active, and what type/detail/position → `status().hasGoal()`, `goalType()`,
  `goalDetail()`, `goalX/Y/Z()`
- Is a path being calculated / available / how long → `calculating()`, `hasPath()`,
  `pathLength()`, `ticksToGoal()`
- Is navigation running, paused, safe to cancel → `pathing()`, `paused()`, `safeToCancel()`
- Recent transitions and errors → `service.history().recent(n)`
- Why an action failed → `service.lastActionError()`

Diagnostics refresh at the fixed 500 ms poll cadence, never per render frame. `safeToCancel()`
is read from `PathingBehavior.isSafeToCancel()` (present on the concrete class but not on the
`IPathingBehavior` interface); when unavailable it is reported as unknown rather than assumed.

## 4. Controls

| Surface | Command | Notes |
| --- | --- | --- |
| Status | `; baritone status` | one-line state + goal + path length |
| GUI | `; baritone gui` | live status, history, buttons, editable settings |
| Goal | `; baritone goto <x y z>` | `GoalBlock` |
| Goal | `; baritone near <x y z> [r]` | `GoalNear`, validated radius |
| Goal | `; baritone wp <name>` | waypoint → goal, cross-dimension rejected |
| Goal | `; baritone here` | `GoalBlock` at the player |
| Cancel | `; baritone cancel` | `IPathingBehavior.cancelEverything()` + clears the custom goal |
| Pause | `; baritone pause` | runs Baritone's own `pause` command (resumable) |
| Resume | `; baritone resume` | runs Baritone's own `resume` command |
| Query | `; baritone paused` | reports the live paused state |
| Settings | `; baritone set <name> <value>` | through `SettingsUtil.parseAndApply` |
| Settings | `; baritone settings` | lists writable setting names |

### 4.1 Pause vs cancel (important)

Baritone's **pause** is a *registered process* that returns `PathingCommandType.REQUEST_PAUSE`
while a flag is set. There is **no public method** on `IPathingBehavior` to trigger it — the
concrete `requestPause()` exists on `PathingBehavior` but is not exposed on the API interface.
The supported way to pause, and the one Baritone's own `#pause` command uses, is to run that
command through Baritone's command manager. This integration therefore implements `pause()` /
`resume()` via `ICommandManager.execute("pause")` / `execute("resume")`.

This corrects an earlier defect where `pause()` called `cancelEverything()` — i.e. a pause was
actually an irreversible cancel. A pause is now resumable; a cancel is not. The command string
is a fixed literal supplied by the bridge, never user input, so no arbitrary command is ever
executed from configuration or the GUI.

### 4.2 Settings editing

The Baritone screen's settings panel lists every writable setting. Clicking a row selects it and
shows the current value; typing edits it; Enter applies it through `BaritoneService.setSetting`
(which routes to `SettingsUtil.parseAndApply`). Invalid values are rejected by Baritone and the
error is shown inline. Baritone-owned settings are only ever changed through this validated
path — the client never silently mutates Baritone state.

## 5. Pathfinding assistance

- **Waypoints.** `BaritoneWaypointBridge.goalFor(waypoint, dimension)` builds a `GoalBlock`
  from a client waypoint, or reports a rejection reason when the waypoint is in another
  dimension.
- **Route overlay.** `BaritoneRouteModule` draws the active goal marker in the world pass.
- **HUD readout.** `BaritoneHudElement` shows the one-line status; `BaritoneModule` owns its
  visibility and raises a single arrival notification (`service.consumeGoalReached()`) when a
  goal the client issued completes.
- **No goal spam.** The client issues a goal only on an explicit user action; it never
  re-issues goals every tick and never reacts to its own state changes in a loop. `cancel()`
  also clears the custom goal so a stale goal cannot re-path on the next tick.

## 6. Testing

`src/test/java/com/logvex/crystal/integration/baritone/` (6 classes) drives the whole subsystem
through a fake `BaritoneBridge`, so navigation-state handling is exercised without a live game
or a Baritone jar:

| Scenario | Where |
| --- | --- |
| Baritone absent → reported, not thrown | `BaritoneServiceTest.unavailableBaritoneIsReportedNotThrown` |
| Baritone available → version + status exposed | `BaritoneServiceTest.availableBaritoneExposesVersionAndStatus` |
| Goal issued + marked | `goToIssuesGoalAndMarksIt`, `followNearIssuesRadiusGoal` |
| Invalid destination / radius | `invalidFollowRadiusIsRejectedWithoutBridgeCall` |
| Navigation started / completed | `goalCompletionIsReportedOnceThenConsumed` |
| Navigation cancelled | `cancelClearsIssuedGoalAndSuppressesArrival` |
| Pause / resume (and that pause ≠ cancel) | `pauseDelegatesToBridgeAndReportsSuccess`, `resumeDelegatesToBridgeAndReportsSuccess` |
| Rejected request | `rejectedPauseIsReportedAsError` |
| Unsupported capability (unavailable) | `pauseAndResumeAreUnavailableWithoutBaritone` |
| API exception | `statusReadFailureDegradesToUnavailableAndLogsError` |
| Paused state interpretation | `pausedReflectsLatestStatus` |
| Transition history | `historyRecordsTransitionsAcrossPolls` |
| Goal/status/waypoint value logic | `BaritoneGoalTest`, `BaritoneStatusTest`, `BaritoneHistoryTest`, `BaritoneNavigationStateTest`, `BaritoneWaypointBridgeTest` |

### 6.1 Scenarios that still require manual in-game validation

These need a live Baritone jar and Minecraft, which the pure-logic test source set cannot load:

1. A real installed Baritone is detected and its version reported.
2. A goal actually produces movement, and arrival fires once.
3. Pause halts movement and resume continues it (the `REQUEST_PAUSE` process path).
4. `setSetting` changes real Baritone behaviour.
5. Baritone present but incompatible → clean "unsupported" diagnostics, no crash.
6. Client reload/shutdown leaves no Baritone task or listener behind.

## 7. Known limitations

- **Reflection, not linkage.** Because Baritone is not a compile dependency, a future Baritone
  rename could break a call. This is reported as an `unsupported` diagnostic, not a crash, and
  the API surface above is the single place to update.
- **Version-specific reads.** `safeToCancel()` and `requestPause()` live on concrete classes,
  not the public interfaces; both are read defensively and degrade to "unknown".
- **No forced chunk loads.** Navigation uses Baritone's own pathfinder; the client never
  force-loads chunks or runs unbounded searches to populate a display.
- **Not a pathfinder.** The client does not reimplement Baritone's algorithm; it only issues
  goals and reads state.
