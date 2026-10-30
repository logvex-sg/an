package com.logvex.crystal.commandcenter;

import java.util.List;

/**
 * Why a module can or cannot be used, in one value.
 *
 * <p>It unifies the three questions a user asks about a module - "is anything blocking it",
 * "what would it clash with", and "what does it need" - into a single verdict the GUI, the
 * command surface and the AI context can all render. Nothing here is invented: the
 * blockers come from the existing coordination inspector and the module's own declared
 * metadata, so the verdict can never disagree with what enabling the module would do.
 *
 * @param module          the module name
 * @param status          the rolled-up health
 * @param operable        true when the module has no blocking problem
 * @param blockers        human-readable reasons it cannot operate (empty when operable)
 * @param warnings        non-blocking concerns (active conflicts, keybind clashes)
 * @param requirements    declared dependencies, services, integrations, capabilities, modes
 */
public record CompatibilityVerdict(
        String module,
        HealthStatus status,
        boolean operable,
        List<String> blockers,
        List<String> warnings,
        List<String> requirements) {

    public CompatibilityVerdict {
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
        status = status == null ? HealthStatus.OK : status;
    }

    /** A one-line summary suitable for a tooltip or a chat line. */
    public String describe() {
        if (operable && warnings.isEmpty()) {
            return module + ": ready";
        }
        StringBuilder sb = new StringBuilder(module).append(": ");
        if (!operable) {
            sb.append("cannot operate \u2014 ").append(String.join("; ", blockers));
        } else {
            sb.append("operable");
        }
        if (!warnings.isEmpty()) {
            sb.append(operable ? " (" : "; ").append(String.join("; ", warnings)).append(')');
        }
        return sb.toString();
    }
}
