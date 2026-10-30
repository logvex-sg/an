package com.logvex.crystal.commandcenter;

/**
 * The host callbacks the standard {@link CommandCenterAction} values need.
 *
 * <p>Rather than let every action reach into the live {@code CrystalClient}, the actions talk
 * to this small interface. That keeps them independent of the client singleton, so the whole
 * registry is unit testable with a stub host, and it documents in one place exactly what the
 * Command Center is allowed to do to the client.
 */
public interface CommandCenterHost {

    /** Prints a line to the user (chat/notification). */
    void notify(String message, boolean error);

    /** Opens the ClickGUI, optionally focused on a named view ({@code appearance}, {@code favorites}, ...). */
    void openGui(String view);

    /** Opens the drag-and-drop HUD editor. */
    void openHudEditor();

    /** Saves all configuration immediately. */
    void saveAll();

    /** Reloads all configuration from disk. */
    void reloadAll();

    /** Toggles a module by name; returns false when no such module exists. */
    boolean toggleModule(String name);

    /** Enables or disables a module by name; returns false when no such module exists. */
    boolean setModule(String name, boolean enabled);

    /** Renders a multi-line diagnostic report. */
    void printDiagnostics();

    /** Renders the module-coordination conflict report. */
    void printConflicts();

    /** Renders the accumulated module error log. */
    void printErrors(boolean clear);

    /** The current status snapshot, for the "status" action. */
    ClientStatusModel status();

    /** The current performance report, for the "performance" action. */
    PerformanceReport performance();
}
