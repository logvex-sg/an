package com.logvex.crystal.scan;

/**
 * A single chest or trapped-chest block position observed in a loaded chunk.
 *
 * <p>Deliberately Minecraft-free so the counting and clustering logic can be unit tested
 * without a running client.
 */
public record ChestPoint(int x, int y, int z) {
}
