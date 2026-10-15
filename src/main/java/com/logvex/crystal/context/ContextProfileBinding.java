package com.logvex.crystal.context;

import java.util.ArrayList;
import java.util.List;

/**
 * A named binding: apply {@code profileName} while {@code rule} matches.
 */
public record ContextProfileBinding(String profileName, ContextRule rule, boolean enabled) {

    public ContextProfileBinding {
        profileName = profileName == null ? "" : profileName;
        rule = rule == null ? new ContextRule("", "", 0) : rule;
    }

    public static ContextProfileBinding of(String profileName, ContextRule rule) {
        return new ContextProfileBinding(profileName, rule, true);
    }

    public boolean usable() {
        return enabled && !profileName.isBlank() && rule.hasConditions();
    }
}
