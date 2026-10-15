package com.logvex.crystal.combat;

import com.logvex.crystal.player.InventoryUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Shared world-interaction helpers: reach checks, block placement and block breaking.
 *
 * <p>All of these go through the vanilla {@code ClientPlayerInteractionManager}, so the
 * server still validates reach and placement exactly as it does for a normal player click.
 * Nothing here forges packets or bypasses the interaction manager; a module built on top of
 * it plays the same game the player does, just without a human aiming.
 */
public final class WorldInteract {

    private WorldInteract() {
    }

    /** The player's current block interaction reach, from the vanilla attribute. */
    public static double blockReach(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        return player == null ? 4.5 : player.getBlockInteractionRange();
    }

    /** Whether {@code pos} is within block reach of the player's eyes. */
    public static boolean inReach(MinecraftClient client, BlockPos pos) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return false;
        }
        double reach = blockReach(client);
        Vec3d eyes = player.getEyePos();
        // Measure to the nearest point of the block, not its centre, so edges are fair.
        double dx = Math.max(0, Math.abs(eyes.x - (pos.getX() + 0.5)) - 0.5);
        double dy = Math.max(0, Math.abs(eyes.y - (pos.getY() + 0.5)) - 0.5);
        double dz = Math.max(0, Math.abs(eyes.z - (pos.getZ() + 0.5)) - 0.5);
        return Math.sqrt(dx * dx + dy * dy + dz * dz) <= reach;
    }

    /**
     * Breaks the block at {@code pos} by driving the vanilla breaking progress, which is what
     * a held-down left click does. Returns false if the block is out of reach or the action
     * was not accepted.
     */
    public static boolean mine(MinecraftClient client, BlockPos pos, Direction face) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.interactionManager == null) {
            return false;
        }
        if (!inReach(client, pos)) {
            return false;
        }
        boolean started = client.interactionManager.updateBlockBreakingProgress(pos, face);
        if (started) {
            player.swingHand(Hand.MAIN_HAND);
        }
        return started;
    }

    /** Cancels any in-progress block breaking (used on disable). */
    public static void cancelMining(MinecraftClient client) {
        if (client.interactionManager != null) {
            client.interactionManager.cancelBlockBreaking();
        }
    }

    /**
     * Places {@code item} by right-clicking the given face of {@code againstPos}, after
     * selecting a hotbar slot that holds it. Returns false if the item is not in the hotbar
     * or the placement was rejected.
     */
    public static boolean place(MinecraftClient client, BlockPos againstPos, Direction face, Item item) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.interactionManager == null) {
            return false;
        }
        int slot = InventoryUtil.findInHotbar(client, item);
        if (slot < 0) {
            return false;
        }
        if (!inReach(client, againstPos)) {
            return false;
        }
        InventoryUtil.selectSlot(client, slot);
        if (!InventoryUtil.heldStack(client).isOf(item)) {
            return false;
        }
        Vec3d hit = Vec3d.ofCenter(againstPos).add(
                face.getVector().getX() * 0.5, face.getVector().getY() * 0.5, face.getVector().getZ() * 0.5);
        BlockHitResult hitResult = new BlockHitResult(hit, face, againstPos, false);
        ActionResult result = client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResult);
        if (result.isAccepted()) {
            player.swingHand(Hand.MAIN_HAND);
            return true;
        }
        return false;
    }

    /**
     * Places {@code item} onto the block above {@code belowPos} by right-clicking the block's
     * top face. Convenience for the common "put a crystal/obsidian/web on this block" case.
     */
    public static boolean placeOnTop(MinecraftClient client, BlockPos belowPos, Item item) {
        return place(client, belowPos, Direction.UP, item);
    }

    /**
     * Places {@code item} on the given face of {@code againstPos} only if the resulting
     * position ({@code againstPos} offset by {@code face}) is currently replaceable, so a
     * module cannot overwrite an existing block.
     */
    public static boolean placeIfReplaceable(MinecraftClient client, BlockPos againstPos, Direction face, Item item) {
        if (client.world == null) {
            return false;
        }
        BlockPos target = againstPos.offset(face);
        if (!client.world.getBlockState(target).isReplaceable()) {
            return false;
        }
        return place(client, againstPos, face, item);
    }

    /** Whether the block at {@code pos} is air or a replaceable material. */
    public static boolean isReplaceable(MinecraftClient client, BlockPos pos) {
        return client.world != null && client.world.getBlockState(pos).isReplaceable();
    }

    /**
     * Places {@code item} into the replaceable block at {@code target} by right-clicking an
     * adjacent solid block's face. Tries each of the six faces so it does not matter which
     * side the player is on. Returns false if no adjacent solid block is in reach.
     */
    public static boolean placeAt(MinecraftClient client, BlockPos target, Item item) {
        if (client.world == null || !isReplaceable(client, target)) {
            return false;
        }
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = target.offset(dir.getOpposite());
            if (client.world.getBlockState(neighbor).isReplaceable()) {
                continue;
            }
            if (!inReach(client, neighbor)) {
                continue;
            }
            if (place(client, neighbor, dir, item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Points the player at a world position, the way the vanilla client sets its look each
     * frame. Used before a placement so the server accepts the interaction as aimed.
     */
    public static void lookAt(MinecraftClient client, Vec3d target) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        Vec3d eyes = player.getEyePos();
        double dx = target.x - eyes.x;
        double dy = target.y - eyes.y;
        double dz = target.z - eyes.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx))) - 90.0f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
        player.setYaw(yaw);
        player.setPitch(Math.max(-90f, Math.min(90f, pitch)));
    }

    /** Whether a hotbar slot holds {@code item} right now. */
    public static boolean hasInHotbar(MinecraftClient client, Item item) {
        return InventoryUtil.findInHotbar(client, item) >= 0;
    }

    /** The held stack, or an empty stack when no player exists. */
    public static ItemStack held(MinecraftClient client) {
        return InventoryUtil.heldStack(client);
    }
}
