package com.logvex.crystal.notification;

import com.logvex.crystal.util.BestEffort;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Central toast queue shared by modules, the GUI and commands.
 *
 * <p>Instead of every module printing to chat, producers push short notices here and the
 * HUD renders the queue with a fade. Producers run on the client thread and the HUD reads
 * on the render thread, so the deque is synchronized and entries are immutable. A
 * {@link Listener} hook lets the cosmetic layer play a sound when something is posted.
 *
 * <p>Filtering and suppression live here so they are applied uniformly: a level the user has
 * hidden is dropped, an identical message posted again within the configured window is
 * suppressed (so a module reporting the same event every tick cannot flood the screen), and
 * a bounded history is kept for the history view. The preferences themselves are held in
 * {@link NotificationSettings}.
 */
public final class NotificationManager {

    /** Notified whenever a notification is posted (used for cute UI sounds). */
    public interface Listener {
        void onNotification(Notification notification);
    }

    /** A single notification with its post time and severity. */
    public record Notification(String text, NotificationLevel level, long postedAtMillis, boolean actionBar) {
    }

    private static final int MAX_ENTRIES = 24;
    private static final int MAX_HISTORY = 64;
    private static final long DEFAULT_LIFETIME = 5200L;

    private final Deque<Notification> entries = new ArrayDeque<>();
    private final Deque<Notification> history = new ArrayDeque<>();
    private final List<Listener> listeners = new ArrayList<>();
    private NotificationSettings settings = new NotificationSettings();

    private String lastText;
    private long lastTextAt;

    /** Replaces the preferences. A null argument restores the defaults. */
    public void setSettings(NotificationSettings settings) {
        this.settings = settings == null ? new NotificationSettings() : settings;
    }

    public NotificationSettings settings() {
        return settings;
    }

    public void addListener(Listener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void notify(String text, NotificationLevel level) {
        post(text, level, false);
    }

    /** Posts a notification and mirrors it to the action bar. */
    public void actionBar(String text, NotificationLevel level) {
        post(text, level, true);
    }

    private void post(String text, NotificationLevel level, boolean actionBar) {
        if (text == null || text.isBlank()) {
            return;
        }
        NotificationLevel severity = level == null ? NotificationLevel.INFO : level;
        NotificationSettings prefs = settings;
        if (prefs != null && !prefs.shows(severity)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (prefs != null && prefs.suppressDuplicates && isDuplicate(text, now, prefs.duplicateWindowMillis)) {
            return;
        }
        lastText = text;
        lastTextAt = now;
        Notification notification = new Notification(text, severity, now, actionBar);
        synchronized (entries) {
            entries.addLast(notification);
            while (entries.size() > MAX_ENTRIES) {
                entries.removeFirst();
            }
        }
        synchronized (history) {
            history.addLast(notification);
            while (history.size() > MAX_HISTORY) {
                history.removeFirst();
            }
        }
        List<Listener> snapshot;
        synchronized (listeners) {
            snapshot = new ArrayList<>(listeners);
        }
        for (Listener listener : snapshot) {
            BestEffort.run("notification-listener", "notification listener",
                    () -> listener.onNotification(notification));
        }
    }

    /** True when an identical message was posted within the suppression window. */
    private boolean isDuplicate(String text, long now, long windowMillis) {
        return windowMillis > 0 && text.equals(lastText) && now - lastTextAt < windowMillis;
    }

    public void info(String text) {
        notify(text, NotificationLevel.INFO);
    }

    public void success(String text) {
        notify(text, NotificationLevel.SUCCESS);
    }

    public void warn(String text) {
        notify(text, NotificationLevel.WARN);
    }

    public void error(String text) {
        notify(text, NotificationLevel.ERROR);
    }

    /** A deliberately cute notice, used by cosmetic features. */
    public void cute(String text) {
        notify(text, NotificationLevel.CUTE);
    }

    /** Drops expired entries. Called once per client tick. */
    public void prune() {
        long cutoff = System.currentTimeMillis() - lifetime();
        synchronized (entries) {
            while (!entries.isEmpty() && entries.peekFirst().postedAtMillis() < cutoff) {
                entries.removeFirst();
            }
        }
    }

    private long lifetime() {
        return settings == null ? DEFAULT_LIFETIME : settings.durationMillis;
    }

    /** Snapshot of live entries, oldest first. Safe to call from the render thread. */
    public List<Notification> active() {
        synchronized (entries) {
            return new ArrayList<>(entries);
        }
    }

    /** Recent history, oldest first, including entries that have since expired. */
    public List<Notification> history() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    public void clearHistory() {
        synchronized (history) {
            history.clear();
        }
    }

    /** 1.0 when freshly posted, falling to 0.0 at expiry, using the configured lifetime. */
    public float alpha(Notification notification) {
        return alpha(notification, lifetime());
    }

    /** Alpha against an explicit lifetime, so a caller with custom settings stays correct. */
    public static float alpha(Notification notification, long lifetimeMillis) {
        if (lifetimeMillis <= 0) {
            return 1f;
        }
        long age = System.currentTimeMillis() - notification.postedAtMillis();
        float remaining = 1.0f - (float) age / lifetimeMillis;
        return Math.max(0f, Math.min(1f, remaining));
    }

    public void clear() {
        synchronized (entries) {
            entries.clear();
        }
    }

    public int size() {
        synchronized (entries) {
            return entries.size();
        }
    }
}
