package com.logvex.crystal.world;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Small, pure helpers over the live world and player so modules do not each re-derive the
 * same facts (which way the player faces, the current dimension, the local light level).
 *
 * <p>Everything here reads state the client already has. Nothing scans beyond what the
 * caller asks for, and nothing mutates the world.
 */
public final class WorldInfo {

    private WorldInfo() {
    }

    /** The compass direction a yaw points to, as a short label. */
    public static String facing(float yaw) {
        // Vanilla yaw: 0 = south, 90 = west, 180 = north, -90 = east.
        float wrapped = MathHelper.wrapDegrees(yaw);
        String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        int index = Math.round(wrapped / 45f) & 7;
        return names[index];
    }

    /** The dimension id of the loaded world, or {@code "unknown"}. */
    public static String dimension(MinecraftClient client) {
        if (client == null || client.world == null) {
            return "unknown";
        }
        var key = client.world.getRegistryKey().getValue();
        return switch (key.getPath()) {
            case "overworld" -> "Overworld";
            case "the_nether" -> "Nether";
            case "the_end" -> "End";
            default -> key.getPath();
        };
    }

    /** A stable id for the loaded dimension (e.g. {@code minecraft:overworld}). */
    public static String dimensionId(MinecraftClient client) {
        return client == null || client.world == null ? ""
                : client.world.getRegistryKey().getValue().toString();
    }

    /**
     * The local block light level at the player's position (0-15). Reads the loaded chunk
     * only; returns -1 when no world is loaded.
     */
    public static int blockLight(MinecraftClient client) {
        if (client == null || client.world == null || client.player == null) {
            return -1;
        }
        BlockPos pos = client.player.getBlockPos();
        return client.world.getLightLevel(net.minecraft.world.LightType.BLOCK, pos);
    }

    /** The sky light level at the player's position (0-15), or -1. */
    public static int skyLight(MinecraftClient client) {
        if (client == null || client.world == null || client.player == null) {
            return -1;
        }
        BlockPos pos = client.player.getBlockPos();
        return client.world.getLightLevel(net.minecraft.world.LightType.SKY, pos);
    }

    /** The world time of day in ticks (0-24000), or -1. */
    public static long timeOfDay(MinecraftClient client) {
        if (client == null || client.world == null) {
            return -1;
        }
        return client.world.getTimeOfDay() % 24000L;
    }

    /** A coarse day-phase label derived from the world time. */
    public static String dayPhase(MinecraftClient client) {
        long time = timeOfDay(client);
        if (time < 0) {
            return "?";
        }
        if (time < 6000) {
            return "Morning";
        }
        if (time < 12000) {
            return "Day";
        }
        if (time < 13000) {
            return "Dusk";
        }
        if (time < 23000) {
            return "Night";
        }
        return "Dawn";
    }

    /** The biome id at the player's position, or {@code "?"}. */
    public static String biome(MinecraftClient client) {
        if (client == null || client.world == null || client.player == null) {
            return "?";
        }
        BlockPos pos = client.player.getBlockPos();
        return client.world.getBiome(pos).getKey()
                .map(key -> key.getValue().getPath().replace('_', ' '))
                .orElse("?");
    }

    /** The player's ping in milliseconds, or -1 when unknown (singleplayer). */
    public static int ping(MinecraftClient client) {
        if (client == null || client.player == null || client.getNetworkHandler() == null) {
            return -1;
        }
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        return entry == null ? -1 : entry.getLatency();
    }

    /** True when the world is currently raining or thundering. */
    public static boolean raining(MinecraftClient client) {
        return client != null && client.world != null && client.world.isRaining();
    }

    /** The player's position, or null when no world is loaded. */
    public static Vec3d playerPos(MinecraftClient client) {
        return client == null || client.player == null ? null : client.player.getEntityPos();
    }

    /** True when the given block position is inside a loaded chunk. */
    public static boolean isLoaded(World world, int x, int z) {
        return world != null && world.isChunkLoaded(x >> 4, z >> 4);
    }
}
