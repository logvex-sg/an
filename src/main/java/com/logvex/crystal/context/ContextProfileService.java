package com.logvex.crystal.context;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Evaluates opt-in contextual profile rules against a snapshot and decides when a profile
 * should be applied.
 *
 * <p>The decision is separated from the act of applying so the guard rails are testable and
 * can never be bypassed: a switch only happens when the situation matches a different
 * binding than the one currently in force, the client is not in combat, and a cooldown since
 * the last switch has elapsed. The first matching binding in list order wins, so the user's
 * ordering is the priority. A snapshot that matches nothing leaves the current profile alone
 * - the client never reverts on its own, which would fight a manual choice.
 */
public final class ContextProfileService {

    /** Default minimum time between automatic switches, to avoid thrashing. */
    public static final long DEFAULT_COOLDOWN_MILLIS = 8000L;

    private final List<ContextProfileBinding> bindings = new ArrayList<>();
    private long cooldownMillis = DEFAULT_COOLDOWN_MILLIS;
    private boolean enabled;
    private String appliedProfile;
    private long lastSwitchAt = Long.MIN_VALUE / 2;

    public List<ContextProfileBinding> bindings() {
        return bindings;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long cooldownMillis() {
        return cooldownMillis;
    }

    public void setCooldownMillis(long cooldownMillis) {
        this.cooldownMillis = Math.max(0, cooldownMillis);
    }

    /** The profile most recently applied by this service, or null. */
    public String appliedProfile() {
        return appliedProfile;
    }

    /** Records that a profile was applied outside the service (e.g. manually). */
    public void noteApplied(String profile) {
        this.appliedProfile = profile;
    }

    public void clear() {
        bindings.clear();
        appliedProfile = null;
    }

    public void add(ContextProfileBinding binding) {
        if (binding != null) {
            bindings.add(binding);
        }
    }

    /** Removes every binding for a profile name; returns how many were removed. */
    public int remove(String profileName) {
        int before = bindings.size();
        bindings.removeIf(binding -> binding.profileName().equalsIgnoreCase(profileName));
        return before - bindings.size();
    }

    /**
     * Decides which profile, if any, should be applied now.
     *
     * @param snapshot   the current situation
     * @param inCombat   true when a combat guard should block automatic switching
     * @param nowMillis  the current time
     * @return the profile name to apply, or empty when nothing should change
     */
    public Optional<String> decide(ContextSnapshot snapshot, boolean inCombat, long nowMillis) {
        if (!enabled || inCombat || snapshot == null) {
            return Optional.empty();
        }
        for (ContextProfileBinding binding : bindings) {
            if (!binding.usable() || !binding.rule().matches(snapshot)) {
                continue;
            }
            if (binding.profileName().equalsIgnoreCase(appliedProfile)) {
                return Optional.empty();
            }
            if (nowMillis - lastSwitchAt < cooldownMillis) {
                return Optional.empty();
            }
            return Optional.of(binding.profileName());
        }
        return Optional.empty();
    }

    /** Records that {@code profile} was applied by {@link #decide} at {@code nowMillis}. */
    public void recordSwitch(String profile, long nowMillis) {
        this.appliedProfile = profile;
        this.lastSwitchAt = nowMillis;
    }
}
