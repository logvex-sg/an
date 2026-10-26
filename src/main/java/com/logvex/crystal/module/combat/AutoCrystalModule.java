package com.logvex.crystal.module.combat;

import com.logvex.crystal.combat.PlacementEvaluator;
import com.logvex.crystal.combat.PlacementEvaluator.Candidate;
import com.logvex.crystal.combat.PlacementEvaluator.CandidateInput;
import com.logvex.crystal.combat.PlacementEvaluator.Plan;
import com.logvex.crystal.combat.WorldInteract;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.player.InventoryUtil;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * AutoCrystal: the classic anarchy crystal-PvP loop — place an obsidian block beside the
 * target, place a crystal on it, then hit the crystal to detonate it — with a self-damage
 * guard so it does not kill its own player.
 *
 * <p>Every action goes through the vanilla interaction manager: the same block placement,
 * item use and attack a player performs by hand. It reads only what the client already knows
 * (the target from {@link com.logvex.crystal.combat.CombatService} and the crystal-damage
 * prediction), and refuses any action whose predicted self-damage would be lethal.
 */
public final class AutoCrystalModule extends Module {

    /** What the module is doing this tick, shown by the HUD and diagnostics. */
    public enum State {
        IDLE, PLACING_CRYSTAL, PLACING_OBSIDIAN, ATTACKING, NO_TARGET
    }

    private final IntSetting placeRange = add("PlaceRange", "Maximum distance to place a crystal.", 5, 2, 6);
    private final IntSetting breakRange = add("BreakRange", "Maximum distance to hit a crystal.", 5, 2, 6);
    private final IntSetting targetRange = add("TargetRange", "Only target players within this range.", 7, 3, 12);
    private final IntSetting minTargetDamage = add("MinTargetDamage",
            "Only act when the blast would deal at least this much to the target.", 6, 1, 20);
    private final BooleanSetting place = add("Place", "Place obsidian and crystals.", true);
    private final BooleanSetting breakCrystals = add("Break", "Detonate crystals near the target.", true);
    private final BooleanSetting antiSuicide = add("AntiSuicide",
            "Never act when the blast could kill you.", true);
    private final IntSetting selfDamageLimit = add("SelfDamageLimit",
            "Never take more than this from your own blast.", 8, 1, 20);
    private final IntSetting rotationPriority = add("RotationPriority",
            "0 = place first, 1 = break first.", 0, 0, 1);
    private final IntSetting interval = add("Interval", "Minimum ticks between actions.", 1, 1, 10);

    private State state = State.IDLE;
    private double lastTargetDamage;
    private double lastSelfDamage;
    /** Why the last placement attempt was rejected, or "" when the last attempt succeeded. */
    private String lastRejection = "";
    private int ticks;

    public AutoCrystalModule() {
        super(Category.COMBAT, "AutoCrystal", "Places, detonates and manages end crystals against a target.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.interactionManager == null
                || client.currentScreen != null) {
            state = State.IDLE;
            return;
        }
        var target = context().combatService().currentTarget();
        if (target == null || target.distance() > targetRange.value()) {
            state = State.NO_TARGET;
            return;
        }
        if (++ticks < interval.value()) {
            return;
        }
        ticks = 0;
        if (rotationPriority.value() == 1 && breakCrystals.value() && tryBreak(client, target.entity())) {
            return;
        }
        if (place.value() && tryPlace(client, target.entity())) {
            return;
        }
        if (rotationPriority.value() == 0 && breakCrystals.value() && tryBreak(client, target.entity())) {
            return;
        }
        state = State.IDLE;
    }

    @Override
    protected void onDisable() {
        state = State.IDLE;
        WorldInteract.cancelMining(MinecraftClient.getInstance());
    }

    /** Places a crystal on the best evaluated base beside the target, or obsidian first. */
    private boolean tryPlace(MinecraftClient client, Entity target) {
        Plan plan = planPlacement(client, target);
        if (!plan.hasPlacement()) {
            lastRejection = plan.rejectedSummary();
            return false;
        }
        lastRejection = "";
        BlockPos base = new BlockPos(plan.best().x(), plan.best().y(), plan.best().z());
        BlockPos crystalSpot = base.up();
        lastSelfDamage = plan.best().selfDamage();
        lastTargetDamage = plan.best().targetDamage();
        if (client.world.getBlockState(crystalSpot).isAir()) {
            if (InventoryUtil.findInHotbar(client, Items.END_CRYSTAL) < 0) {
                lastRejection = "no crystal in hotbar";
                return false;
            }
            WorldInteract.lookAt(client, Vec3d.ofCenter(crystalSpot));
            if (WorldInteract.placeOnTop(client, base, Items.END_CRYSTAL)) {
                state = State.PLACING_CRYSTAL;
                return true;
            }
        }
        if (client.world.getBlockState(crystalSpot).isReplaceable()) {
            WorldInteract.lookAt(client, Vec3d.ofCenter(crystalSpot));
            if (WorldInteract.placeAt(client, crystalSpot, Items.OBSIDIAN)) {
                state = State.PLACING_OBSIDIAN;
                return true;
            }
        }
        return false;
    }

    /**
     * Evaluates every base beside the target and returns the best accepted placement, with
     * the rejection reasons when none is usable. This is the shared {@link PlacementEvaluator}
     * the debug HUD reads, so the reasons shown always match what the module actually did.
     */
    public Plan planPlacement(MinecraftClient client, Entity target) {
        List<CandidateInput> inputs = new ArrayList<>();
        for (BlockPos base : baseCandidates(client, target)) {
            BlockPos crystalSpot = base.up();
            double selfDamage = predictSelf(client, crystalSpot);
            double targetDamage = predictTarget(client, target, crystalSpot);
            Candidate candidate = new Candidate(base.getX(), base.getY(), base.getZ(), targetDamage, selfDamage);
            boolean spotFree = client.world.getBlockState(crystalSpot).isAir();
            boolean baseSolid = !client.world.getBlockState(base).isReplaceable();
            boolean inRange = inPlaceRange(client, base);
            inputs.add(new CandidateInput(candidate, spotFree, baseSolid, inRange));
        }
        return PlacementEvaluator.plan(inputs, target != null, minTargetDamage.value(),
                client.player == null ? 0 : client.player.getHealth(), selfDamageLimit.value(),
                antiSuicide.value());
    }

    /** Candidate base positions: solid blocks under the target and its four neighbours. */
    private List<BlockPos> baseCandidates(MinecraftClient client, Entity target) {
        BlockPos targetPos = target.getBlockPos();
        List<BlockPos> bases = new ArrayList<>();
        Map<Long, BlockPos> seen = new HashMap<>();
        List<BlockPos> spots = List.of(targetPos, targetPos.north(), targetPos.south(),
                targetPos.east(), targetPos.west());
        for (BlockPos spot : spots) {
            BlockPos base = spot.down();
            if (client.world.getBlockState(base).isReplaceable()) {
                continue;
            }
            if (!client.world.getBlockState(spot).isAir()
                    && !client.world.getBlockState(spot).isReplaceable()) {
                continue;
            }
            if (seen.putIfAbsent(base.asLong(), base) == null) {
                bases.add(base);
            }
        }
        return bases;
    }

    /** Attacks the closest crystal near the target, if the blast is safe and worth it. */
    private boolean tryBreak(MinecraftClient client, Entity target) {
        EndCrystalEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity crystal)) {
                continue;
            }
            double distance = client.player.distanceTo(crystal);
            if (distance > breakRange.value() || distance >= bestDistance) {
                continue;
            }
            bestDistance = distance;
            best = crystal;
        }
        if (best == null) {
            return false;
        }
        double selfDamage = predictSelf(client, best.getBlockPos());
        double targetDamage = predictTarget(client, target, best.getBlockPos());
        lastSelfDamage = selfDamage;
        lastTargetDamage = targetDamage;
        if (!safe(selfDamage) || targetDamage < minTargetDamage.value()) {
            return false;
        }
        WorldInteract.lookAt(client, best.getEntityPos());
        client.interactionManager.attackEntity(client.player, best);
        client.player.swingHand(Hand.MAIN_HAND);
        state = State.ATTACKING;
        return true;
    }

    private double predictSelf(MinecraftClient client, BlockPos crystal) {
        Vec3d blast = Vec3d.ofCenter(crystal);
        return context().combatService().predict(blast, client.player.getEntityPos(),
                client.player.getHealth(), client.player.getArmor(), true).applied();
    }

    private double predictTarget(MinecraftClient client, Entity target, BlockPos crystal) {
        Vec3d blast = Vec3d.ofCenter(crystal);
        if (target instanceof PlayerEntity player) {
            return context().combatService().predict(blast, player.getEntityPos(),
                    player.getHealth(), player.getArmor(), player.canSee(client.player)).applied();
        }
        return 0;
    }

    private boolean safe(double selfDamage) {
        if (!antiSuicide.value()) {
            return true;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return false;
        }
        return selfDamage < client.player.getHealth() && selfDamage <= selfDamageLimit.value();
    }

    private boolean inPlaceRange(MinecraftClient client, BlockPos pos) {
        return client.player != null && client.player.getEntityPos().distanceTo(Vec3d.ofCenter(pos))
                <= placeRange.value() + 1.0;
    }

    /** Current action, for the HUD/diagnostics. */
    public State state() {
        return state;
    }

    /** Damage the last considered blast would deal to the target. */
    public double lastTargetDamage() {
        return lastTargetDamage;
    }

    /** Damage the last considered blast would deal to the local player. */
    public double lastSelfDamage() {
        return lastSelfDamage;
    }

    /** Why the last placement was rejected (empty when it succeeded); for the debug HUD. */
    public String lastRejection() {
        return lastRejection;
    }
}
