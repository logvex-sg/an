package com.logvex.crystal.event;

import com.logvex.crystal.combat.CombatTarget;
import com.logvex.crystal.combat.CombatService;
import com.logvex.crystal.entity.EntityInfo;
import com.logvex.crystal.entity.EntityTracker;
import com.logvex.crystal.integration.baritone.BaritoneService;
import com.logvex.crystal.integration.baritone.BaritoneStatus;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.module.ModuleManager;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;

/**
 * Turns observable client-side state into {@link ClientEvent}s on the {@link ClientEventBus}.
 *
 * <p>This is the single producer for the shared stream. It is driven once per client tick and
 * only <em>diffs</em> state the client already has, so it never scans beyond what other
 * services already scanned:
 *
 * <ul>
 *   <li>world/dimension transitions, from the loaded world's registry key;</li>
 *   <li>entity spawn/despawn, drained from the shared {@link EntityTracker};</li>
 *   <li>tracked-entity health and armour changes, diffed against the previous snapshot;</li>
 *   <li>combat-target changes, from {@link CombatService};</li>
 *   <li>Baritone navigation-state changes and goal arrival, from {@link BaritoneService};</li>
 *   <li>the player's own health, and module enable/disable transitions (polled on an interval
 *       because modules have no change hook of their own).</li>
 * </ul>
 *
 * <p>Deduplication and boundedness live in the bus; this class only reports what it observed.
 */
public final class ClientEventFeed {

    private final ClientEventBus bus;
    private final ModuleManager modules;
    private final EntityTracker entityTracker;
    private final CombatService combat;
    private final BaritoneService baritone;

    /** How often the enabled-set diff runs, in ms. Module toggles do not need per-tick polling. */
    private static final long MODULE_POLL_MS = 250L;

    private boolean wasInWorld;
    private String lastDimension = "";
    private String lastTargetUuid = "";
    private String lastBaritoneState = "";
    private float lastPlayerHealth = -1;
    private final Set<String> enabledModules = new LinkedHashSet<>();
    private final Map<UUID, float[]> lastLiving = new HashMap<>();
    private long lastModulePollMillis;
    private boolean moduleSnapshotTaken;

    public ClientEventFeed(ClientEventBus bus, ModuleManager modules, EntityTracker entityTracker,
                           CombatService combat, BaritoneService baritone) {
        this.bus = bus;
        this.modules = modules;
        this.entityTracker = entityTracker;
        this.combat = combat;
        this.baritone = baritone;
    }

    /** Advances the feed one step. Call once per client tick, on the client thread. */
    public void tick(MinecraftClient client) {
        long now = System.currentTimeMillis();
        boolean inWorld = client != null && client.player != null && client.world != null;

        if (wasInWorld && !inWorld) {
            bus.publish(ClientEvent.of(EventKind.WORLD_LEFT, now).build());
            reset();
            wasInWorld = false;
            return;
        }
        if (!inWorld) {
            return;
        }
        if (!wasInWorld) {
            bus.publish(ClientEvent.of(EventKind.WORLD_JOINED, now)
                    .put("dimension", dimension(client)).build());
            wasInWorld = true;
        }

        String dimension = dimension(client);
        if (!dimension.equals(lastDimension)) {
            if (!lastDimension.isEmpty()) {
                bus.publish(ClientEvent.of(EventKind.DIMENSION_CHANGED, now)
                        .put("from", lastDimension).put("to", dimension).build());
            }
            lastDimension = dimension;
            lastLiving.clear();
        }

        feedEntityLifecycle(now);
        feedEntityState(now);
        feedTarget(now);
        feedPlayerHealth(client, now);
        feedBaritone(now);
        feedModules(now);
    }

    private void feedEntityLifecycle(long now) {
        for (EntityTracker.EntityEvent event : entityTracker.drainEvents()) {
            EventKind kind = event.kind() == EntityTracker.Kind.SPAWN
                    ? EventKind.ENTITY_SPAWNED : EventKind.ENTITY_DESPAWNED;
            bus.publish(ClientEvent.of(kind, event.atMillis() > 0 ? event.atMillis() : now)
                    .put("uuid", event.info().uuid().toString())
                    .put("name", event.info().name())
                    .put("category", event.info().category().name()).build());
        }
    }

    /** Diffs health and armour of tracked living entities against the previous snapshot. */
    private void feedEntityState(long now) {
        Map<UUID, float[]> current = new HashMap<>();
        for (EntityInfo info : entityTracker.tracked()) {
            if (!info.living()) {
                continue;
            }
            current.put(info.uuid(), new float[]{info.health(), info.armor()});
            float[] previous = lastLiving.get(info.uuid());
            if (previous == null) {
                continue;
            }
            if (previous[0] != info.health()) {
                bus.publish(ClientEvent.of(EventKind.ENTITY_HEALTH_CHANGED, now)
                        .put("uuid", info.uuid().toString())
                        .put("name", info.name())
                        .put("from", previous[0])
                        .put("to", info.health())
                        .put("delta", info.health() - previous[0]).build());
            }
            if ((int) previous[1] != info.armor()) {
                bus.publish(ClientEvent.of(EventKind.ENTITY_ARMOR_CHANGED, now)
                        .put("uuid", info.uuid().toString())
                        .put("name", info.name())
                        .put("to", info.armor()).build());
            }
        }
        lastLiving.clear();
        lastLiving.putAll(current);
    }

    private void feedTarget(long now) {
        CombatTarget target = combat == null ? null : combat.currentTarget();
        String uuid = target == null ? "" : target.entity().getUuid().toString();
        if (!uuid.equals(lastTargetUuid)) {
            String previous = lastTargetUuid;
            lastTargetUuid = uuid;
            if (!uuid.isEmpty()) {
                bus.publish(ClientEvent.of(EventKind.TARGET_CHANGED, now)
                        .put("uuid", uuid)
                        .put("name", target.name())
                        .put("previous", previous).build());
            } else if (!previous.isEmpty()) {
                bus.publish(ClientEvent.of(EventKind.TARGET_CHANGED, now)
                        .put("name", "")
                        .put("previous", previous).build());
            }
        }
    }

    private void feedPlayerHealth(MinecraftClient client, long now) {
        float health = client.player.getHealth();
        if (lastPlayerHealth < 0) {
            lastPlayerHealth = health;
            return;
        }
        if (health != lastPlayerHealth) {
            bus.publish(ClientEvent.of(EventKind.PLAYER_HEALTH_CHANGED, now)
                    .put("from", lastPlayerHealth).put("to", health)
                    .put("delta", health - lastPlayerHealth).build());
            lastPlayerHealth = health;
        }
    }

    private void feedBaritone(long now) {
        if (baritone == null) {
            return;
        }
        if (baritone.consumeGoalReached()) {
            bus.publish(ClientEvent.of(EventKind.BARITONE_GOAL_REACHED, now).build());
        }
        BaritoneStatus status = baritone.status();
        String state = status.stateKey();
        if (!state.equals(lastBaritoneState)) {
            lastBaritoneState = state;
            bus.publish(ClientEvent.of(EventKind.BARITONE_STATE_CHANGED, now)
                    .put("state", state)
                    .put("goal", status.hasGoal() ? status.goalDetail() : "")
                    .put("pathing", status.pathing()).build());
        }
    }

    private void feedModules(long now) {
        if (modules == null || now - lastModulePollMillis < MODULE_POLL_MS) {
            return;
        }
        lastModulePollMillis = now;
        Set<String> current = new LinkedHashSet<>();
        for (Module module : modules.all()) {
            if (module.isEnabled()) {
                current.add(module.name());
            }
        }
        if (moduleSnapshotTaken) {
            for (String name : current) {
                if (!enabledModules.contains(name)) {
                    bus.publish(ClientEvent.of(EventKind.MODULE_STATE_CHANGED, now)
                            .put("module", name).put("enabled", true).build());
                }
            }
            for (String name : enabledModules) {
                if (!current.contains(name)) {
                    bus.publish(ClientEvent.of(EventKind.MODULE_STATE_CHANGED, now)
                            .put("module", name).put("enabled", false).build());
                }
            }
        }
        moduleSnapshotTaken = true;
        enabledModules.clear();
        enabledModules.addAll(current);
    }

    private static String dimension(MinecraftClient client) {
        return client.world.getRegistryKey().getValue().toString();
    }

    /** Clears the diffed state after leaving a world so nothing outlives it. */
    private void reset() {
        lastDimension = "";
        lastTargetUuid = "";
        lastBaritoneState = "";
        lastPlayerHealth = -1;
        lastLiving.clear();
        enabledModules.clear();
        moduleSnapshotTaken = false;
    }
}
