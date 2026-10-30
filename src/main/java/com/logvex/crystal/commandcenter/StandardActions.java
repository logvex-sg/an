package com.logvex.crystal.commandcenter;

import java.util.List;

/**
 * The standard set of {@link CommandCenterAction} values, built over a
 * {@link CommandCenterHost}.
 *
 * <p>These are the "verbs" of the Command Center: report status, print conflicts, open the
 * GUI, save, toggle a module. Each one is a small wrapper over an existing client operation,
 * so the Command Center is a discovery layer over capabilities the client already has rather
 * than a second implementation of them.
 *
 * <p>Minecraft-independent: the host is the only seam, so the whole set is unit testable with
 * a recording stub.
 */
public final class StandardActions {

    private final CommandCenterHost host;
    private final CommandRegistry registry;

    public StandardActions(CommandCenterHost host, CommandRegistry registry) {
        this.host = host;
        this.registry = registry;
    }

    /** Registers the whole standard set into the registry. */
    public void registerAll() {
        registry.registerAll(
                simple("status.report", "Report Status", "diagnostics",
                        "Print the rolled-up client status.",
                        () -> {
                            ClientStatusModel model = host.status();
                            host.notify(model.summary(), false);
                            for (StatusSection section : model.sections()) {
                                if (section.worst().needsAttention()) {
                                    host.notify("  " + section.title() + ": " + section.worst().label(), false);
                                }
                            }
                            return ActionResult.success("Status reported");
                        }),
                simple("diagnostics.conflicts", "Show Conflicts", "diagnostics",
                        "Print the module-coordination conflict report.", () -> {
                            host.printConflicts();
                            return ActionResult.success("Conflicts reported");
                        }),
                simple("diagnostics.errors", "Show Errors", "diagnostics",
                        "Print the accumulated module error log.", () -> {
                            host.printErrors(false);
                            return ActionResult.success("Errors reported");
                        }),
                simple("diagnostics.clear-errors", "Clear Errors", "diagnostics",
                        "Clear the module error log.", () -> {
                            host.printErrors(true);
                            return ActionResult.success("Errors cleared");
                        }),
                simple("diagnostics.dump", "Full Diagnostics", "diagnostics",
                        "Print the full diagnostic report.", () -> {
                            host.printDiagnostics();
                            return ActionResult.success("Diagnostics printed");
                        }),
                simple("performance.report", "Report Performance", "diagnostics",
                        "Print the performance intelligence verdict.", () -> {
                            PerformanceReport report = host.performance();
                            host.notify(report.summary(), false);
                            for (PerformanceFinding finding : report.findings()) {
                                host.notify("  " + finding.describe(), false);
                            }
                            return ActionResult.success("Performance reported");
                        }),
                simple("interface.clickgui", "Open ClickGUI", "interface",
                        "Open the module interface.",
                        () -> open("", "ClickGUI opened")),
                simple("interface.appearance", "Appearance", "interface",
                        "Open the appearance controls.",
                        () -> open("appearance", "Appearance opened")),
                simple("interface.hud-editor", "HUD Editor", "interface",
                        "Open the drag-and-drop HUD layout editor.", () -> {
                            host.openHudEditor();
                            return ActionResult.success("HUD editor opened");
                        }),
                simple("interface.favorites", "Favourites", "interface",
                        "Open the favourites view.",
                        () -> open("favorites", "Favourites opened")),
                simple("interface.recent", "Recently Used", "interface",
                        "Open the recently-used view.",
                        () -> open("recent", "Recent modules opened")),
                simple("config.save", "Save Config", "config",
                        "Write all configuration to disk.", () -> {
                            host.saveAll();
                            return ActionResult.success("Configuration saved");
                        }),
                simple("config.reload", "Reload Config", "config",
                        "Re-read all configuration from disk.", () -> {
                            host.reloadAll();
                            return ActionResult.success("Configuration reloaded");
                        })
        );
    }

    private ActionResult open(String view, String message) {
        host.openGui(view);
        return ActionResult.success(message);
    }

    private CommandCenterAction simple(String id, String label, String category, String description,
                                       java.util.function.Supplier<ActionResult> body) {
        return new CommandCenterAction() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String label() {
                return label;
            }

            @Override
            public String category() {
                return category;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public ActionResult run() {
                try {
                    return body.get();
                } catch (RuntimeException e) {
                    return ActionResult.failure(label + " failed: " + e.getMessage());
                }
            }
        };
    }

    /**
     * Registers a per-module toggle action for every named module, so the Command Center can
     * toggle modules without the caller needing the module object.
     */
    public void registerModuleToggles(List<String> moduleNames) {
        if (moduleNames == null) {
            return;
        }
        for (String name : moduleNames) {
            registry.register(new CommandCenterAction() {
                @Override
                public String id() {
                    return "module.toggle." + name.toLowerCase(java.util.Locale.ROOT);
                }

                @Override
                public String label() {
                    return "Toggle " + name;
                }

                @Override
                public String category() {
                    return "modules";
                }

                @Override
                public String description() {
                    return "Enable or disable the " + name + " module.";
                }

                @Override
                public ActionResult run() {
                    boolean ok = host.toggleModule(name);
                    return ok ? ActionResult.success(name + " toggled")
                            : ActionResult.failure("No module named " + name);
                }
            });
        }
    }
}
