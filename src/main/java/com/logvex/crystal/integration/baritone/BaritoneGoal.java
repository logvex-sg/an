package com.logvex.crystal.integration.baritone;

/**
 * A validated navigation goal the client can ask Baritone to pursue.
 *
 * <p>Only goal kinds that Baritone's public API exposes are listed; each carries the
 * coordinates it needs. The record validates its own inputs so an invalid goal can never reach
 * the bridge: coordinates are bounded to the world limit, and {@code GOAL_NEAR} requires a
 * positive radius.
 *
 * @param kind  which Baritone goal to build
 * @param x     block x
 * @param y     block y (ignored by {@code GOAL_XZ})
 * @param z     block z
 * @param range follow distance for {@code GOAL_NEAR} (ignored by other kinds)
 */
public record BaritoneGoal(Kind kind, int x, int y, int z, int range) {

    /** The supported goal kinds, mapped to Baritone's public {@code Goal*} classes. */
    public enum Kind {
        /** Go to an exact block (Baritone {@code GoalBlock}). */
        GOAL_BLOCK("GoalBlock", true, true),
        /** Get near a block within {@code range} (Baritone {@code GoalNear}). */
        GOAL_NEAR("GoalNear", true, true),
        /** Go to an x/z column, any y (Baritone {@code GoalXZ}). */
        GOAL_XZ("GoalXZ", true, false),
        /** Go to the current dimension's spawn point (Baritone {@code GoalBlock} on spawn). */
        GOAL_SPAWN("GoalBlock", false, false);

        private final String baritoneClass;
        private final boolean needsXZ;
        private final boolean needsY;

        Kind(String baritoneClass, boolean needsXZ, boolean needsY) {
            this.baritoneClass = baritoneClass;
            this.needsXZ = needsXZ;
            this.needsY = needsY;
        }

        /** The simple name of the Baritone goal class this kind builds. */
        public String baritoneClass() {
            return baritoneClass;
        }

        public boolean needsXZ() {
            return needsXZ;
        }

        public boolean needsY() {
            return needsY;
        }
    }

    public BaritoneGoal {
        if (kind == null) {
            throw new IllegalArgumentException("goal kind must not be null");
        }
        if (kind.needsXZ() && (Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000)) {
            throw new IllegalArgumentException("coordinates out of world bounds");
        }
        if (kind == Kind.GOAL_NEAR && range < 1) {
            throw new IllegalArgumentException("follow range must be at least 1");
        }
        if (range < 1) {
            range = 1;
        }
    }

    /** A block goal. */
    public static BaritoneGoal block(int x, int y, int z) {
        return new BaritoneGoal(Kind.GOAL_BLOCK, x, y, z, 1);
    }

    /** A near-a-block goal with a follow radius. */
    public static BaritoneGoal near(int x, int y, int z, int range) {
        return new BaritoneGoal(Kind.GOAL_NEAR, x, y, z, range);
    }

    /** An x/z column goal. */
    public static BaritoneGoal xz(int x, int z) {
        return new BaritoneGoal(Kind.GOAL_XZ, x, 0, z, 1);
    }

    /** A short human description, e.g. {@code "block 120 64 -40"}. */
    public String describe() {
        return switch (kind) {
            case GOAL_BLOCK -> "block " + x + " " + y + " " + z;
            case GOAL_NEAR -> "near " + x + " " + y + " " + z + " r" + range;
            case GOAL_XZ -> "xz " + x + " " + z;
            case GOAL_SPAWN -> "spawn";
        };
    }
}
