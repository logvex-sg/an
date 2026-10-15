package com.logvex.crystal.module.render;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.render.esp.HoleFinder;
import com.logvex.crystal.render.esp.WorldEspCollector;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import com.logvex.crystal.theme.Palette;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * HoleEsp: highlights safe pockets (bedrock/obsidian holes) a player can stand in, colour
 * coded by how strong the hole is.
 *
 * <p>All classification lives in {@link HoleFinder}, which is Minecraft-free and unit
 * tested; this module only adapts the live world into the finder's {@link HoleFinder.BlockGrid}
 * view. The scan is a bounded cube around the player and only touches loaded chunks, so it
 * cannot stall the render thread on a large world.
 */
public final class HoleEspModule extends WorldEspModule {

    private final IntSetting range = add("Range", "Cube radius to scan for holes.", 12, 4, 32);
    private final BooleanSetting labels = add("Labels", "Show the hole type as a label.", false);
    private final IntSetting maxHoles = add("MaxHoles", "Cap on holes highlighted per frame.", 128, 8, 512);

    public HoleEspModule() {
        super(Category.RENDER, "HoleEsp", "Highlights safe holes, colour coded by strength.");
    }

    @Override
    public void collectEsp(WorldEspCollector out, MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        CrystalClient bootstrap = CrystalClient.get();
        Palette palette = bootstrap == null ? null : bootstrap.theme().palette();
        WorldGrid grid = new WorldGrid(client.world);
        var player = client.player.getBlockPos();
        var holes = HoleFinder.find(grid, player.getX(), player.getY(), player.getZ(), range.value());
        int cap = maxHoles.value();
        int drawn = 0;
        for (HoleFinder.Hole hole : holes) {
            if (drawn >= cap) {
                break;
            }
            int color = colorFor(hole.type(), palette);
            out.addBox(hole.x(), hole.y(), hole.z(), hole.x() + 1, hole.y() + 1, hole.z() + 1, color, 1.6f);
            drawn++;
            if (labels.value()) {
                out.addLabel(hole.x() + 0.5, hole.y() + 1.3, hole.z() + 0.5, hole.type().name().toLowerCase(), color);
            }
        }
    }

    private int colorFor(HoleFinder.HoleType type, Palette palette) {
        int bedrock = palette == null ? 0xFF8FE3FF : palette.crystalSelf();
        int obsidian = palette == null ? 0xFFB39DFF : palette.tertiary();
        int mixed = palette == null ? 0xFFFF9EC4 : palette.primary();
        int single = palette == null ? 0xFFFFC46B : palette.warning();
        return switch (type) {
            case BEDROCK -> bedrock;
            case OBSIDIAN -> obsidian;
            case MIXED -> mixed;
            case SINGLE, NONE -> single;
        };
    }

    /** A {@link HoleFinder.BlockGrid} view over a live world, reusing one mutable position. */
    private static final class WorldGrid implements HoleFinder.BlockGrid {
        private final World world;
        private final BlockPos.Mutable pos = new BlockPos.Mutable();

        WorldGrid(World world) {
            this.world = world;
        }

        private BlockState state(int x, int y, int z) {
            return world.getBlockState(pos.set(x, y, z));
        }

        @Override
        public boolean isAir(int x, int y, int z) {
            return state(x, y, z).isAir();
        }

        @Override
        public boolean isSolid(int x, int y, int z) {
            return state(x, y, z).isSolidBlock(world, pos.set(x, y, z));
        }

        @Override
        public float blastResistance(int x, int y, int z) {
            return state(x, y, z).getBlock().getBlastResistance();
        }
    }
}
