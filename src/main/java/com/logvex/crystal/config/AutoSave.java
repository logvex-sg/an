package com.logvex.crystal.config;

import java.util.Objects;

/**
 * A debounced autosave gate. Callers mark the configuration dirty when something changes;
 * {@link #tick(long)} performs at most one save once a burst of changes has settled, and
 * {@link #flush(long)} forces a pending save out immediately (used on shutdown).
 *
 * <p>The debounce window is measured from the <em>first</em> change in a burst, not the
 * last, so a client that keeps mutating state every frame still writes to disk regularly
 * instead of being starved. The class holds no clock of its own: every method takes the
 * current time, which keeps it deterministic and unit-testable without a running client.
 */
public final class AutoSave {

    private final long debounceMillis;
    private final Runnable saveAction;

    private boolean dirty;
    private long dirtySinceMillis;
    private long lastSaveMillis;
    private int saveCount;

    public AutoSave(long debounceMillis, Runnable saveAction) {
        if (debounceMillis < 0) {
            throw new IllegalArgumentException("debounceMillis must be >= 0");
        }
        this.debounceMillis = debounceMillis;
        this.saveAction = Objects.requireNonNull(saveAction, "saveAction");
    }

    /** Records a change at the current time. */
    public void markDirty() {
        markDirty(System.currentTimeMillis());
    }

    /** Records a change at {@code now}. The debounce timer starts at the first change. */
    public void markDirty(long now) {
        if (!dirty) {
            dirty = true;
            dirtySinceMillis = now;
        }
    }

    /**
     * Saves when a change is pending and the debounce window has elapsed.
     *
     * @return true when a save was performed
     */
    public boolean tick(long now) {
        if (dirty && now - dirtySinceMillis >= debounceMillis) {
            save(now);
            return true;
        }
        return false;
    }

    /** Forces a pending save immediately, ignoring the debounce window. */
    public void flush(long now) {
        if (dirty) {
            save(now);
        }
    }

    private void save(long now) {
        dirty = false;
        lastSaveMillis = now;
        saveCount++;
        saveAction.run();
    }

    public boolean isDirty() {
        return dirty;
    }

    public long lastSaveMillis() {
        return lastSaveMillis;
    }

    /** How many saves have run; used by tests and the diagnostics command. */
    public int saveCount() {
        return saveCount;
    }
}
