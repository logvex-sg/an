package com.logvex.crystal.entity;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * A reusable entity-visibility decision, shared by every entity ESP and entity-reading
 * module so the "should I show this entity" rules live in one place instead of drifting
 * across a dozen collect methods.
 *
 * <p>The decision takes plain values (category, flags, distance, name) rather than an
 * {@code Entity}, so it is Minecraft-independent and unit-testable; the caller extracts
 * those values from the live entity. A builder keeps construction readable and a module
 * builds one filter per frame from its own settings.
 */
public final class EntityFilter {

    private final Set<EntityCategory> categories;
    private final double minDistance;
    private final double maxDistance;
    private final boolean includeFriends;
    private final boolean includeNonFriends;
    private final boolean includeInvisible;
    private final boolean livingOnly;
    private final boolean requireLoadedWorld;
    private final String nameContains;

    private EntityFilter(Builder b) {
        this.categories = b.categories.isEmpty() ? EnumSet.noneOf(EntityCategory.class)
                : EnumSet.copyOf(b.categories);
        this.minDistance = Math.max(0, b.minDistance);
        this.maxDistance = b.maxDistance <= 0 ? Double.MAX_VALUE : b.maxDistance;
        this.includeFriends = b.includeFriends;
        this.includeNonFriends = b.includeNonFriends;
        this.includeInvisible = b.includeInvisible;
        this.livingOnly = b.livingOnly;
        this.requireLoadedWorld = b.requireLoadedWorld;
        this.nameContains = b.nameContains == null ? "" : b.nameContains.trim().toLowerCase(Locale.ROOT);
    }

    /** A builder with permissive defaults (everything in range). */
    public static Builder builder() {
        return new Builder();
    }

    public Set<EntityCategory> categories() {
        return EnumSet.copyOf(categories);
    }

    public double maxDistance() {
        return maxDistance;
    }

    /**
     * Whether an entity passes the filter.
     *
     * @param category   the entity's category
     * @param isSelf     true for the local player (always excluded)
     * @param isFriend   true when the entity is on the friend list
     * @param invisible  true when the entity is invisible
     * @param living     true when the entity is a living entity
     * @param distance   distance from the local player
     * @param name       display name, used by the name filter
     */
    public boolean accepts(EntityCategory category, boolean isSelf, boolean isFriend,
                           boolean invisible, boolean living, double distance, String name) {
        if (category == null || isSelf) {
            return false;
        }
        if (!categories.isEmpty() && !categories.contains(category)) {
            return false;
        }
        if (livingOnly && !living) {
            return false;
        }
        if (invisible && !includeInvisible) {
            return false;
        }
        if (isFriend && !includeFriends) {
            return false;
        }
        if (!isFriend && !includeNonFriends) {
            return false;
        }
        if (distance < minDistance || distance > maxDistance) {
            return false;
        }
        if (!nameContains.isEmpty() && (name == null || !name.toLowerCase(Locale.ROOT).contains(nameContains))) {
            return false;
        }
        return true;
    }

    public boolean requireLoadedWorld() {
        return requireLoadedWorld;
    }

    /** Fluent builder; every field has a permissive default so only overrides are set. */
    public static final class Builder {

        private final Set<EntityCategory> categories = EnumSet.noneOf(EntityCategory.class);
        private double minDistance;
        private double maxDistance = Double.MAX_VALUE;
        private boolean includeFriends = true;
        private boolean includeNonFriends = true;
        private boolean includeInvisible;
        private boolean livingOnly;
        private boolean requireLoadedWorld = true;
        private String nameContains = "";

        public Builder categories(EntityCategory... values) {
            for (EntityCategory value : values) {
                categories.add(value);
            }
            return this;
        }

        public Builder range(double maxDistance) {
            this.maxDistance = maxDistance;
            return this;
        }

        public Builder minDistance(double minDistance) {
            this.minDistance = minDistance;
            return this;
        }

        public Builder friends(boolean include) {
            this.includeFriends = include;
            return this;
        }

        public Builder nonFriends(boolean include) {
            this.includeNonFriends = include;
            return this;
        }

        public Builder invisible(boolean include) {
            this.includeInvisible = include;
            return this;
        }

        public Builder livingOnly(boolean only) {
            this.livingOnly = only;
            return this;
        }

        public Builder nameContains(String text) {
            this.nameContains = text;
            return this;
        }

        public EntityFilter build() {
            return new EntityFilter(this);
        }
    }
}
