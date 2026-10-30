package com.logvex.crystal.integration.baritone;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The production {@link BaritoneBridge}, reaching Baritone's public API through reflection.
 *
 * <p>Baritone is not a compile dependency, so the integration cannot link against it directly.
 * This class isolates every reflective access to Baritone and validates each member before use;
 * if Baritone changes, the failure is reported as an "unsupported" diagnostic rather than a
 * crash. The verified API surface (Baritone master, Minecraft 1.21.x Fabric/Forge build) is:
 *
 * <pre>
 *   baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone()   -> IBaritone
 *   IBaritone.getCustomGoalProcess()  -> ICustomGoalProcess
 *       setGoal(Goal) / path() / getGoal()
 *   IBaritone.getPathingBehavior()    -> IPathingBehavior
 *       isPathing() / hasPath() / getGoal() / getCurrent() / getInProgress()
 *       estimatedTicksToGoal() : Optional&lt;Double&gt;
 *   IBaritone.getPathingControlManager() -> IPathingControlManager
 *       mostRecentCommand() : Optional&lt;PathingCommand&gt;  (commandType == REQUEST_PAUSE =&gt; paused)
 *   IBaritone.getCommandManager() -> ICommandManager.execute(String) : boolean
 *       (the supported way to run Baritone's own {@code pause}/{@code resume} commands)
 *   IBaritone.getFollowProcess()      -> IFollowProcess.follow(Predicate) / cancel()
 *   baritone.api.pathing.goals.GoalBlock(int,int,int) / GoalNear(BlockPos,int) / GoalXZ(int,int)
 *   baritone.api.Settings.byLowerName : Map&lt;String, Setting&lt;?&gt;&gt;  (Setting.value is a public field)
 *   SettingsUtil.settingValueToString(setting) / parseAndApply(settings, name, value)
 * </pre>
 *
 * <p>All calls must happen on the client thread. The bridge caches nothing beyond the probe
 * result; the {@link BaritoneService} owns polling cadence and state history.
 */
public final class ReflectiveBaritoneBridge implements BaritoneBridge {

    private static final String API = "baritone.api.";
    private static final String BARITONE_API_CLASS = API + "BaritoneAPI";
    private static final String IBARITONE_CLASS = API + "IBaritone";
    private static final String SETTINGS_CLASS = API + "Settings";
    private static final String SETTINGS_UTIL_CLASS = API + "utils.SettingsUtil";
    private static final String GOAL_BLOCK_CLASS = API + "pathing.goals.GoalBlock";
    private static final String GOAL_NEAR_CLASS = API + "pathing.goals.GoalNear";
    private static final String GOAL_XZ_CLASS = API + "pathing.goals.GoalXZ";
    private static final String BLOCK_POS_CLASS = "net.minecraft.util.math.BlockPos";

    /** Cached probe result; the probe runs at most once per bridge instance. */
    private Availability availability;

    @Override
    public Availability probe() {
        if (availability != null) {
            return availability;
        }
        if (!BaritoneReflection.classPresent(BARITONE_API_CLASS)) {
            availability = Availability.no("Baritone is not installed");
            return availability;
        }
        if (!BaritoneReflection.classPresent(IBARITONE_CLASS)) {
            availability = Availability.no("Baritone API present but IBaritone is missing");
            return availability;
        }
        try {
            Object baritone = primaryBaritone();
            if (baritone == null) {
                availability = Availability.no("Baritone provider returned no active instance");
                return availability;
            }
            availability = Availability.ok(detectVersion());
        } catch (RuntimeException e) {
            availability = Availability.no(e.getMessage());
        }
        return availability;
    }

    private Object primaryBaritone() {
        Class<?> api = BaritoneReflection.loadClass(BARITONE_API_CLASS);
        Object provider = BaritoneReflection.call(BaritoneReflection.method(api, "getProvider"), null);
        if (provider == null) {
            return null;
        }
        Method getPrimary = BaritoneReflection.method(provider.getClass(), "getPrimaryBaritone");
        return BaritoneReflection.call(getPrimary, provider);
    }

    /** Best-effort version string; falls back to {@code "unknown"} rather than guessing. */
    private String detectVersion() {
        try {
            Class<?> loader = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object instance = BaritoneReflection.call(BaritoneReflection.method(loader, "getInstance"), null);
            Method getModContainer = BaritoneReflection.method(instance.getClass(), "getModContainer", String.class);
            Object optional = BaritoneReflection.call(getModContainer, instance, "baritone");
            Object container = BaritoneReflection.optionalOrNull(optional);
            if (container == null) {
                return "unknown";
            }
            Object metadata = BaritoneReflection.call(
                    BaritoneReflection.method(container.getClass(), "getMetadata"), container);
            Object version = BaritoneReflection.call(
                    BaritoneReflection.method(metadata.getClass(), "getVersion"), metadata);
            Object friendly = BaritoneReflection.call(
                    BaritoneReflection.method(version.getClass(), "getFriendlyString"), version);
            return friendly == null ? "unknown" : friendly.toString();
        } catch (RuntimeException | LinkageError | ClassNotFoundException e) {
            return "unknown";
        }
    }

    // ------------------------------------------------------------------ status

    @Override
    public BaritoneStatus status() {
        Availability probe = probe();
        if (!probe.available()) {
            return BaritoneStatus.unavailable(probe.reason());
        }
        try {
            Object baritone = primaryBaritone();
            Object pathing = BaritoneReflection.call(
                    BaritoneReflection.method(baritone.getClass(), "getPathingBehavior"), baritone);

            boolean isPathing = (Boolean) BaritoneReflection.call(
                    BaritoneReflection.method(pathing.getClass(), "isPathing"), pathing);
            boolean hasPath = (Boolean) BaritoneReflection.call(
                    BaritoneReflection.method(pathing.getClass(), "hasPath"), pathing);

            Object goal = BaritoneReflection.call(
                    BaritoneReflection.method(pathing.getClass(), "getGoal"), pathing);
            boolean hasGoal = goal != null;
            String goalType = hasGoal ? BaritoneReflection.simpleName(goal) : "none";
            String goalDetail = hasGoal ? describeGoal(goal) : "";
            int[] goalPos = hasGoal ? goalPosition(goal) : new int[]{BaritoneStatus.NO_COORD,
                    BaritoneStatus.NO_COORD, BaritoneStatus.NO_COORD};

            boolean calculating = pathFinderInProgress(pathing);
            int pathLength = pathLength(pathing);
            int ticksToGoal = estimatedTicksToGoal(pathing);
            boolean paused = isPaused(baritone);
            boolean safeToCancel = isSafeToCancel(baritone);

            String statusText = buildStatusText(hasGoal, goalType, goalDetail, isPathing, calculating, paused,
                    ticksToGoal);
            return new BaritoneStatus(true, probe.version(), "", hasGoal, goalType, goalDetail,
                    goalPos[0], goalPos[1], goalPos[2],
                    isPathing, calculating, hasPath, pathLength, ticksToGoal, paused, safeToCancel, statusText);
        } catch (RuntimeException e) {
            // A status read must never break the caller; report it as unavailable with the reason.
            return BaritoneStatus.unavailable("Baritone status read failed: " + e.getMessage());
        }
    }

    private boolean pathFinderInProgress(Object pathing) {
        try {
            Object inProgress = BaritoneReflection.call(
                    BaritoneReflection.method(pathing.getClass(), "getInProgress"), pathing);
            return BaritoneReflection.optionalOrNull(inProgress) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private int pathLength(Object pathing) {
        try {
            Object executor = BaritoneReflection.call(
                    BaritoneReflection.method(pathing.getClass(), "getCurrent"), pathing);
            if (executor == null) {
                return BaritoneStatus.UNKNOWN;
            }
            Object path = BaritoneReflection.call(
                    BaritoneReflection.method(executor.getClass(), "getPath"), executor);
            if (path == null) {
                return BaritoneStatus.UNKNOWN;
            }
            Object positions = BaritoneReflection.call(
                    BaritoneReflection.method(path.getClass(), "positions"), path);
            if (positions instanceof List<?> list) {
                return list.size();
            }
            return BaritoneStatus.UNKNOWN;
        } catch (RuntimeException e) {
            return BaritoneStatus.UNKNOWN;
        }
    }

    private int estimatedTicksToGoal(Object pathing) {
        try {
            Object optional = BaritoneReflection.call(
                    BaritoneReflection.method(pathing.getClass(), "estimatedTicksToGoal"), pathing);
            Object value = BaritoneReflection.optionalOrNull(optional);
            if (value instanceof Number number) {
                return (int) Math.round(number.doubleValue());
            }
            return BaritoneStatus.UNKNOWN;
        } catch (RuntimeException e) {
            return BaritoneStatus.UNKNOWN;
        }
    }

    private boolean isPaused(Object baritone) {
        try {
            Object control = BaritoneReflection.call(
                    BaritoneReflection.method(baritone.getClass(), "getPathingControlManager"), baritone);
            Object optional = BaritoneReflection.call(
                    BaritoneReflection.method(control.getClass(), "mostRecentCommand"), control);
            Object command = BaritoneReflection.optionalOrNull(optional);
            if (command == null) {
                return false;
            }
            Field typeField = BaritoneReflection.field(command.getClass(), "commandType");
            Object type = BaritoneReflection.get(typeField, command);
            return type != null && "REQUEST_PAUSE".equals(type.toString());
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Reads a goal's block position when the goal exposes one, else {@link BaritoneStatus#NO_COORD}. */
    private int[] goalPosition(Object goal) {
        String name = BaritoneReflection.simpleName(goal);
        try {
            if (name.equals("GoalBlock") || name.equals("GoalNear") || name.equals("GoalTwoBlocks")
                    || name.equals("GoalGetToBlock")) {
                Object pos = BaritoneReflection.call(
                        BaritoneReflection.method(goal.getClass(), "getGoalPos"), goal);
                if (pos != null) {
                    Method getX = BaritoneReflection.method(pos.getClass(), "getX");
                    Method getY = BaritoneReflection.method(pos.getClass(), "getY");
                    Method getZ = BaritoneReflection.method(pos.getClass(), "getZ");
                    return new int[]{
                            ((Number) BaritoneReflection.call(getX, pos)).intValue(),
                            ((Number) BaritoneReflection.call(getY, pos)).intValue(),
                            ((Number) BaritoneReflection.call(getZ, pos)).intValue()};
                }
            } else if (name.equals("GoalXZ")) {
                Object x = BaritoneReflection.call(BaritoneReflection.method(goal.getClass(), "getX"), goal);
                Object z = BaritoneReflection.call(BaritoneReflection.method(goal.getClass(), "getZ"), goal);
                return new int[]{((Number) x).intValue(), BaritoneStatus.NO_COORD, ((Number) z).intValue()};
            }
        } catch (RuntimeException e) {
            // Not introspectable: report no coordinates rather than a wrong marker.
        }
        return new int[]{BaritoneStatus.NO_COORD, BaritoneStatus.NO_COORD, BaritoneStatus.NO_COORD};
    }

    private String buildStatusText(boolean hasGoal, String goalType, String goalDetail, boolean pathing,
                                   boolean calculating, boolean paused, int ticksToGoal) {
        if (!hasGoal) {
            return "idle";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(goalType.replace("Goal", "").toLowerCase());
        if (!goalDetail.isEmpty()) {
            sb.append(' ').append(goalDetail);
        }
        sb.append(" \u00B7 ");
        if (paused) {
            sb.append("paused");
        } else if (calculating) {
            sb.append("calculating");
        } else if (pathing) {
            sb.append("pathing");
        } else {
            sb.append("waiting");
        }
        if (ticksToGoal >= 0) {
            sb.append(" \u00B7 ").append(String.format("%.1fs", ticksToGoal / 20.0));
        }
        return sb.toString();
    }

    private String describeGoal(Object goal) {
        String name = BaritoneReflection.simpleName(goal);
        try {
            if (name.equals("GoalBlock")) {
                Object pos = BaritoneReflection.call(
                        BaritoneReflection.method(goal.getClass(), "getGoalPos"), goal);
                return describeBlockPos(pos);
            }
            if (name.equals("GoalNear")) {
                Object pos = BaritoneReflection.call(
                        BaritoneReflection.method(goal.getClass(), "getGoalPos"), goal);
                Field rangeField = BaritoneReflection.field(goal.getClass(), "range");
                Object range = BaritoneReflection.get(rangeField, goal);
                return describeBlockPos(pos) + " r" + range;
            }
            if (name.equals("GoalXZ")) {
                Object x = BaritoneReflection.call(BaritoneReflection.method(goal.getClass(), "getX"), goal);
                Object z = BaritoneReflection.call(BaritoneReflection.method(goal.getClass(), "getZ"), goal);
                return x + " " + z;
            }
        } catch (RuntimeException e) {
            // A goal we can name but not introspect: still report the type.
            return "";
        }
        return "";
    }

    private String describeBlockPos(Object pos) {
        if (pos == null) {
            return "";
        }
        try {
            Method getX = BaritoneReflection.method(pos.getClass(), "getX");
            Method getY = BaritoneReflection.method(pos.getClass(), "getY");
            Method getZ = BaritoneReflection.method(pos.getClass(), "getZ");
            return BaritoneReflection.call(getX, pos) + " " + BaritoneReflection.call(getY, pos)
                    + " " + BaritoneReflection.call(getZ, pos);
        } catch (RuntimeException e) {
            return "";
        }
    }

    // ------------------------------------------------------------------ actions

    @Override
    public void gotoBlock(int x, int y, int z) {
        gotoGoal(BaritoneGoal.Kind.GOAL_BLOCK, x, y, z);
    }

    @Override
    public void gotoGoal(BaritoneGoal.Kind kind, int x, int y, int z) {
        requireAvailable();
        Object goal = buildGoal(kind, x, y, z, 1);
        setGoalAndPath(goal);
    }

    @Override
    public void follow(BaritoneGoal.Kind kind, int x, int y, int z, int range) {
        requireAvailable();
        Object goal = buildGoal(kind, x, y, z, range);
        setGoalAndPath(goal);
    }

    private void setGoalAndPath(Object goal) {
        Object baritone = primaryBaritone();
        Object custom = BaritoneReflection.call(
                BaritoneReflection.method(baritone.getClass(), "getCustomGoalProcess"), baritone);
        BaritoneReflection.call(
                BaritoneReflection.method(custom.getClass(), "setGoal",
                        BaritoneReflection.loadClass(API + "pathing.goals.Goal")), custom, goal);
        BaritoneReflection.call(BaritoneReflection.method(custom.getClass(), "path"), custom);
    }

    private Object buildGoal(BaritoneGoal.Kind kind, int x, int y, int z, int range) {
        try {
            Class<?> blockPos = BaritoneReflection.loadClass(BLOCK_POS_CLASS);
            switch (kind) {
                case GOAL_XZ -> {
                    Class<?> xz = BaritoneReflection.loadClass(GOAL_XZ_CLASS);
                    return xz.getConstructor(int.class, int.class).newInstance(x, z);
                }
                case GOAL_NEAR -> {
                    Class<?> near = BaritoneReflection.loadClass(GOAL_NEAR_CLASS);
                    // GoalNear's constructor takes a BlockPos; build one reflectively so this
                    // package stays free of compile-time Minecraft types.
                    return near.getConstructor(blockPos, int.class).newInstance(newBlockPos(blockPos, x, y, z), range);
                }
                case GOAL_BLOCK, GOAL_SPAWN -> {
                    Class<?> block = BaritoneReflection.loadClass(GOAL_BLOCK_CLASS);
                    return block.getConstructor(int.class, int.class, int.class).newInstance(x, y, z);
                }
                default -> throw new BaritoneException("Unsupported goal kind: " + kind);
            }
        } catch (ReflectiveOperationException e) {
            throw new BaritoneException("Could not build Baritone goal " + kind + ": " + e, e);
        }
    }

    /** Constructs a {@code BlockPos} reflectively so this package needs no Minecraft compile dep. */
    private Object newBlockPos(Class<?> blockPos, int x, int y, int z) {
        try {
            return blockPos.getConstructor(int.class, int.class, int.class).newInstance(x, y, z);
        } catch (ReflectiveOperationException e) {
            throw new BaritoneException("Could not construct BlockPos: " + e, e);
        }
    }

    @Override
    public void cancel() {
        if (!probe().available()) {
            return;
        }
        try {
            Object baritone = primaryBaritone();
            Object pathing = BaritoneReflection.call(
                    BaritoneReflection.method(baritone.getClass(), "getPathingBehavior"), baritone);
            BaritoneReflection.call(
                    BaritoneReflection.method(pathing.getClass(), "cancelEverything"), pathing);
            // Clearing the custom goal too avoids a re-path on the next tick from a stale goal.
            Object custom = BaritoneReflection.call(
                    BaritoneReflection.method(baritone.getClass(), "getCustomGoalProcess"), baritone);
            BaritoneReflection.call(
                    BaritoneReflection.method(custom.getClass(), "setGoal",
                            BaritoneReflection.loadClass(API + "pathing.goals.Goal")), custom, (Object) null);
        } catch (RuntimeException e) {
            // Cancel is best-effort: a failure must not break the caller's command.
        }
    }

    @Override
    public boolean pause() {
        requireAvailable();
        return executeBaritoneCommand("pause");
    }

    @Override
    public boolean resume() {
        requireAvailable();
        return executeBaritoneCommand("resume");
    }

    /**
     * Runs one of Baritone's own chat commands through its command manager
     * ({@code ICommandManager.execute(String)}), which is the supported way to reach behaviour
     * that is not on the public behaviour/process interfaces - notably {@code pause}/{@code resume}.
     * The command string is a fixed literal supplied by this bridge; nothing here is user input,
     * so no arbitrary command is ever executed from configuration or the GUI.
     *
     * @return true when Baritone accepted and ran the command
     * @throws BaritoneException when the command could not be dispatched
     */
    private boolean executeBaritoneCommand(String command) {
        try {
            Object baritone = primaryBaritone();
            Object manager = BaritoneReflection.call(
                    BaritoneReflection.method(baritone.getClass(), "getCommandManager"), baritone);
            Object accepted = BaritoneReflection.call(
                    BaritoneReflection.method(manager.getClass(), "execute", String.class), manager, command);
            return Boolean.TRUE.equals(accepted);
        } catch (RuntimeException e) {
            throw new BaritoneException("Baritone could not run '" + command + "': " + e.getMessage(), e);
        }
    }

    /**
     * Best-effort read of {@code PathingBehavior.isSafeToCancel()}. The method exists on the
     * concrete class but not on the {@code IPathingBehavior} interface, so it is looked up on the
     * runtime type and treated as {@code true} when unavailable: the caller uses this only to
     * decide whether a pause can take effect immediately, never to gate a destructive action.
     */
    private boolean isSafeToCancel(Object baritone) {
        try {
            Object pathing = BaritoneReflection.call(
                    BaritoneReflection.method(baritone.getClass(), "getPathingBehavior"), baritone);
            if (!BaritoneReflection.hasMethod(pathing.getClass(), "isSafeToCancel")) {
                return true;
            }
            Object safe = BaritoneReflection.call(
                    BaritoneReflection.method(pathing.getClass(), "isSafeToCancel"), pathing);
            return !(safe instanceof Boolean b) || b;
        } catch (RuntimeException e) {
            return true;
        }
    }

    // ------------------------------------------------------------------ settings

    @Override
    public boolean setSetting(String name, String value) {
        requireAvailable();
        Object settings = baritoneSettings();
        Object setting = settingByName(settings, name);
        if (setting == null) {
            throw new BaritoneException("Baritone has no setting named '" + name + "'");
        }
        try {
            Class<?> settingsUtil = BaritoneReflection.loadClass(SETTINGS_UTIL_CLASS);
            Class<?> settingsClass = BaritoneReflection.loadClass(SETTINGS_CLASS);
            Method parse = settingsUtil.getMethod("parseAndApply", settingsClass, String.class, String.class);
            parse.invoke(null, settings, name, value);
            return true;
        } catch (ReflectiveOperationException e) {
            throw new BaritoneException("Could not set Baritone '" + name + "' to '" + value + "': " + e, e);
        }
    }

    @Override
    public String getSetting(String name) {
        if (!probe().available()) {
            return null;
        }
        Object settings = baritoneSettings();
        Object setting = settingByName(settings, name);
        if (setting == null) {
            return null;
        }
        try {
            Class<?> settingsUtil = BaritoneReflection.loadClass(SETTINGS_UTIL_CLASS);
            Method toString = settingsUtil.getMethod("settingValueToString",
                    BaritoneReflection.loadClass(API + "Settings$Setting"));
            Object result = toString.invoke(null, setting);
            return result == null ? null : result.toString();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> settingNames() {
        if (!probe().available()) {
            return Collections.emptyList();
        }
        Object settings = baritoneSettings();
        try {
            Field byLowerName = BaritoneReflection.field(settings.getClass(), "byLowerName");
            Object map = BaritoneReflection.get(byLowerName, settings);
            if (map instanceof Map<?, ?> m) {
                List<String> names = new ArrayList<>(m.keySet().size());
                for (Object key : m.keySet()) {
                    names.add(String.valueOf(key));
                }
                Collections.sort(names);
                return names;
            }
        } catch (RuntimeException e) {
            // Fall through to empty: settings listing is a diagnostic, not critical.
        }
        return Collections.emptyList();
    }

    private Object baritoneSettings() {
        Class<?> api = BaritoneReflection.loadClass(BARITONE_API_CLASS);
        return BaritoneReflection.call(BaritoneReflection.method(api, "getSettings"), null);
    }

    @SuppressWarnings("unchecked")
    private Object settingByName(Object settings, String name) {
        try {
            Field byLowerName = BaritoneReflection.field(settings.getClass(), "byLowerName");
            Object map = BaritoneReflection.get(byLowerName, settings);
            if (map instanceof Map<?, ?> m) {
                return m.get(name.toLowerCase(java.util.Locale.ROOT));
            }
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }

    @Override
    public String commandFor(String action) {
        if (action == null) {
            return null;
        }
        return switch (action.toLowerCase(java.util.Locale.ROOT)) {
            case "goto" -> "#goto <x> <y> <z>";
            case "goal" -> "#goal <x> <y> <z>";
            case "cancel" -> "#cancel";
            case "pause" -> "#pause / #resume";
            case "thisway" -> "#thisway <blocks>";
            case "explore" -> "#explore";
            case "set" -> "#set <setting> <value>";
            case "version" -> "#version";
            default -> null;
        };
    }

    private void requireAvailable() {
        Availability probe = probe();
        if (!probe.available()) {
            throw new BaritoneException(probe.reason().isEmpty() ? "Baritone is unavailable" : probe.reason());
        }
    }
}
