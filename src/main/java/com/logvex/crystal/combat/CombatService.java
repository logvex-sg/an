package com.logvex.crystal.combat;

import com.logvex.crystal.client.CrystalClient;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Tracks the current combat target and answers damage questions for the HUD and modules.
 *
 * <p>Target selection is deliberately simple and client-side only: the nearest other
 * player within range, preferring one the crosshair is on. This is a read-only observer of
 * world state — it never attacks, moves, or interacts; modules that do act go through
 * vanilla's interaction manager.
 *
 * <p>The service caches its damage predictions per tick so several HUD elements reading
 * them in the same frame cost nothing extra.
 */
public final class CombatService {

    private static final double MAX_TARGET_RANGE = 24.0;

    private CombatTarget currentTarget;
    private double crystalDamageToTarget;
    private double crystalDamageToSelf;
    private boolean targetLethal;
    private long lastQueryMillis;

    /** Recomputes the target once per client tick. */
    public void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            currentTarget = null;
            return;
        }
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof PlayerEntity player) || player == client.player) {
                continue;
            }
            if (player.isSpectator() || player.isDead()) {
                continue;
            }
            double distance = client.player.distanceTo(player);
            if (distance > MAX_TARGET_RANGE) {
                continue;
            }
            // Prefer whoever the crosshair is on, then the closest.
            boolean lookedAt = isLookedAt(client, player);
            double score = distance - (lookedAt ? 12.0 : 0.0);
            if (score < bestScore) {
                bestScore = score;
                best = player;
            }
        }
        if (best == null) {
            currentTarget = null;
            return;
        }
        double distance = client.player.distanceTo(best);
        boolean los = client.player.canSee(best);
        currentTarget = new CombatTarget(best, best.getName().getString(),
                best.getHealth(), best.getMaxHealth(), best.getArmor(), distance, los);
        recomputeDamage(client);
    }

    private void recomputeDamage(MinecraftClient client) {
        crystalDamageToTarget = 0;
        crystalDamageToSelf = 0;
        targetLethal = false;
        if (currentTarget == null) {
            return;
        }
        Vec3d crystalPos = currentTarget.entity().getEntityPos();
        DamageResult toTarget = predict(crystalPos, currentTarget.entity().getEntityPos(),
                currentTarget.health(), currentTarget.armor(), currentTarget.hasLineOfSight());
        DamageResult toSelf = predict(crystalPos, client.player.getEntityPos(),
                client.player.getHealth(), client.player.getArmor(), client.player.canSee(currentTarget.entity()));
        crystalDamageToTarget = toTarget.applied();
        crystalDamageToSelf = toSelf.applied();
        targetLethal = toTarget.lethal();
        lastQueryMillis = System.currentTimeMillis();
    }

    /**
     * Predicts crystal damage from a blast at {@code crystalPos} to an entity at
     * {@code entityPos} with the given stats. The distance used is the entity's own
     * distance to the blast, which is what the vanilla explosion formula consumes.
     */
    public DamageResult predict(Vec3d crystalPos, Vec3d entityPos, double health, int armor, boolean lineOfSight) {
        double distance = entityPos.distanceTo(crystalPos);
        return CrystalDamage.evaluate(distance, armor, 2.0, health, CrystalDamage.exposureFor(lineOfSight));
    }

    /** Damage the current crystal placement would deal to the target. */
    public double crystalDamageToTarget() {
        return crystalDamageToTarget;
    }

    /** Damage the current crystal placement would deal to the local player. */
    public double crystalDamageToSelf() {
        return crystalDamageToSelf;
    }

    public boolean targetLethal() {
        return targetLethal;
    }

    public CombatTarget currentTarget() {
        return currentTarget;
    }

    public long lastQueryMillis() {
        return lastQueryMillis;
    }

    private boolean isLookedAt(MinecraftClient client, PlayerEntity player) {
        Vec3d eyes = client.player.getCameraPosVec(1.0f);
        Vec3d look = client.player.getRotationVec(1.0f);
        Vec3d toTarget = player.getEntityPos().add(0, player.getHeight() / 2, 0).subtract(eyes);
        double dot = look.normalize().dotProduct(toTarget.normalize());
        return dot > 0.985; // roughly a 10-degree cone
    }

    /** Convenience accessor; null before the client has bootstrapped. */
    public static CombatService get() {
        var bootstrap = CrystalClient.get();
        return bootstrap == null ? null : bootstrap.combat();
    }

    /** Names of all players within range, nearest first (used by diagnostics). */
    public List<String> nearbyPlayers(MinecraftClient client) {
        List<String> names = new ArrayList<>();
        if (client.player == null || client.world == null) {
            return names;
        }
        for (Entity entity : client.world.getEntities()) {
            if (entity instanceof PlayerEntity player && player != client.player) {
                names.add(player.getName().getString());
            }
        }
        return names;
    }
}
