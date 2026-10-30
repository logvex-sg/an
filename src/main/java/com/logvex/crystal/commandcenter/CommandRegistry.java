package com.logvex.crystal.commandcenter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The central registry of {@link CommandCenterAction} values.
 *
 * <p>It is the one place that knows what the client can be asked to do, keyed by a stable id
 * and grouped by category. The command surface, the GUI and the AI context all read it, so a
 * new action appears everywhere at once. Registration rejects a duplicate id rather than
 * silently replacing, so a programming mistake surfaces immediately.
 *
 * <p>Minecraft-independent and cheap: lookups are O(1) and iteration preserves insertion
 * order, so the GUI shows actions in the order they were registered.
 */
public final class CommandRegistry {

    private final Map<String, CommandCenterAction> byId = new LinkedHashMap<>();

    /** Registers an action. Throws when the id is blank or already taken. */
    public void register(CommandCenterAction action) {
        if (action == null) {
            return;
        }
        String id = action.id() == null ? "" : action.id().toLowerCase(Locale.ROOT);
        if (id.isBlank()) {
            throw new IllegalArgumentException("Action id must not be blank");
        }
        if (byId.containsKey(id)) {
            throw new IllegalStateException("Duplicate action id: " + action.id());
        }
        byId.put(id, action);
    }

    /** Convenience for registering several actions at once. */
    public void registerAll(CommandCenterAction... actions) {
        if (actions == null) {
            return;
        }
        for (CommandCenterAction action : actions) {
            register(action);
        }
    }

    public CommandCenterAction byId(String id) {
        return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT));
    }

    public boolean contains(String id) {
        return byId.containsKey(id == null ? "" : id.toLowerCase(Locale.ROOT));
    }

    /** Every action, in registration order. */
    public List<CommandCenterAction> all() {
        return List.copyOf(byId.values());
    }

    /** The actions in one category, in registration order. */
    public List<CommandCenterAction> byCategory(String category) {
        List<CommandCenterAction> result = new ArrayList<>();
        if (category == null) {
            return result;
        }
        for (CommandCenterAction action : byId.values()) {
            if (category.equalsIgnoreCase(action.category())) {
                result.add(action);
            }
        }
        return result;
    }

    /** The distinct category keys, in first-seen order. */
    public List<String> categories() {
        List<String> result = new ArrayList<>();
        for (CommandCenterAction action : byId.values()) {
            if (!result.contains(action.category())) {
                result.add(action.category());
            }
        }
        return result;
    }

    public int size() {
        return byId.size();
    }

    /**
     * Runs an action by id and returns its result. An unknown id is a failure rather than an
     * exception, so a caller (the command surface) can report it like any other problem.
     */
    public ActionResult run(String id) {
        CommandCenterAction action = byId(id);
        if (action == null) {
            return ActionResult.failure("No such action: " + id);
        }
        if (!action.available()) {
            String reason = action.unavailableReason();
            return ActionResult.failure(action.label() + " is unavailable"
                    + (reason.isBlank() ? "" : " (" + reason + ")"));
        }
        try {
            return action.run();
        } catch (RuntimeException e) {
            return ActionResult.failure(action.label() + " failed: " + e.getMessage());
        }
    }
}
