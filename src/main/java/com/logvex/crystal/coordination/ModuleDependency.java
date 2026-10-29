package com.logvex.crystal.coordination;

/**
 * One typed requirement a module declares, and the reason it could not be satisfied.
 *
 * <p>The legacy {@code dependsOn("Name")} edge only expresses "another module must be enabled".
 * A module in practice needs more: a shared service, an optional integration, a runtime
 * capability, or a mutually-exclusive mode. This value type carries that richer meaning and,
 * crucially, a human-readable explanation of why it is unmet, so the client can tell the user
 * exactly what is missing rather than failing silently.
 *
 * <p>A dependency is data only; it never enables anything. Evaluating it is the job of
 * {@link ModuleDependencyGraph}, and honouring it is the job of the caller.
 */
public final class ModuleDependency {

    /** The kind of requirement. */
    public enum Kind {
        /** Another module must be installed and enabled. */
        MODULE("module"),
        /** A shared service must be present (always true in the shipping client). */
        SERVICE("service"),
        /** An optional integration must be present and responsive (e.g. Baritone). */
        INTEGRATION("integration"),
        /** A runtime capability must be present. */
        CAPABILITY("capability"),
        /** A mutually-exclusive mode must currently be the active one. */
        MODE("mode");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final Kind kind;
    private final String name;
    private final boolean required;
    private final String reason;

    private ModuleDependency(Kind kind, String name, boolean required, String reason) {
        this.kind = kind;
        this.name = name == null ? "" : name.trim();
        this.required = required;
        this.reason = reason == null ? "" : reason;
    }

    // ------------------------------------------------------------------ factories

    /** A module that must be enabled. */
    public static ModuleDependency module(String name) {
        return new ModuleDependency(Kind.MODULE, name, true, "requires module '" + name + "'");
    }

    /** An optional module that, when present, should be enabled first. */
    public static ModuleDependency optionalModule(String name) {
        return new ModuleDependency(Kind.MODULE, name, false, "works best with module '" + name + "'");
    }

    /** A shared service that must be available. */
    public static ModuleDependency service(String name) {
        return new ModuleDependency(Kind.SERVICE, name, true, "requires the '" + name + "' service");
    }

    /** An optional integration (present but not mandatory). */
    public static ModuleDependency optionalIntegration(String name) {
        return new ModuleDependency(Kind.INTEGRATION, name, false, "uses the '" + name + "' integration when present");
    }

    /** An integration that must be available. */
    public static ModuleDependency integration(String name) {
        return new ModuleDependency(Kind.INTEGRATION, name, true, "requires the '" + name + "' integration");
    }

    /** A runtime capability that must be present. */
    public static ModuleDependency capability(String name) {
        return new ModuleDependency(Kind.CAPABILITY, name, true, "requires the '" + name + "' capability");
    }

    /** A mode that must currently be active. */
    public static ModuleDependency mode(String name) {
        return new ModuleDependency(Kind.MODE, name, true, "requires the '" + name + "' mode to be active");
    }

    /** A dependency with a custom explanation, for anything the factories do not cover. */
    public static ModuleDependency custom(Kind kind, String name, boolean required, String reason) {
        return new ModuleDependency(kind, name, required, reason);
    }

    // ------------------------------------------------------------------ accessors

    public Kind kind() {
        return kind;
    }

    public String name() {
        return name;
    }

    /** True when the module cannot operate at all without this dependency. */
    public boolean required() {
        return required;
    }

    /** A human-readable explanation of the requirement. */
    public String reason() {
        return reason;
    }

    /** True when this edge refers to another module by name. */
    public boolean isModuleEdge() {
        return kind == Kind.MODULE;
    }

    /**
     * Whether this dependency is currently satisfied in {@code context}.
     *
     * <p>A module edge is satisfied only when the module is <em>enabled</em>, matching the
     * existing {@code ModuleManager} semantics: depending on a disabled module is not useful.
     */
    public boolean satisfied(DependencyContext context) {
        if (context == null || name.isEmpty()) {
            return false;
        }
        return switch (kind) {
            case MODULE -> context.moduleEnabled(name);
            case SERVICE -> context.serviceAvailable(name);
            case INTEGRATION -> context.integrationAvailable(name);
            case CAPABILITY -> context.capabilityAvailable(name);
            case MODE -> context.modeActive(name);
        };
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ModuleDependency d)) {
            return false;
        }
        return kind == d.kind && required == d.required && name.equals(d.name);
    }

    @Override
    public int hashCode() {
        return (kind.hashCode() * 31 + name.hashCode()) * 31 + Boolean.hashCode(required);
    }

    @Override
    public String toString() {
        return kind.label() + ":" + name + (required ? "" : " (optional)");
    }
}
