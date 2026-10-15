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
    private static final long LIFETIME_MILLIS = 5200L;

    private final Deque<Notification> entries = new ArrayDeque<>();
    private final List<Listener> listeners = new ArrayList<>();

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
        Notification notification = new Notification(text, level, System.currentTimeMillis(), actionBar);
        synchronized (entries) {
            entries.addLast(notification);
            while (entries.size() > MAX_ENTRIES) {
                entries.removeFirst();
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
        long cutoff = System.currentTimeMillis() - LIFETIME_MILLIS;
        synchronized (entries) {
            while (!entries.isEmpty() && entries.peekFirst().postedAtMillis() < cutoff) {
                entries.removeFirst();
            }
        }
    }

    /** Snapshot of live entries, oldest first. Safe to call from the render thread. */
    public List<Notification> active() {
        synchronized (entries) {
            return new ArrayList<>(entries);
        }
    }

    /** 1.0 when freshly posted, falling to 0.0 at expiry. */
    public static float alpha(Notification notification) {
        long age = System.currentTimeMillis() - notification.postedAtMillis();
        float remaining = 1.0f - (float) age / LIFETIME_MILLIS;
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
