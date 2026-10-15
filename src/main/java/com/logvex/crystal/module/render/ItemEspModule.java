package com.logvex.crystal.module.render;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.render.esp.EspFilter;
import com.logvex.crystal.render.esp.WorldEspCollector;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.util.Format;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * ItemEsp: outlines dropped items, optionally only items the player would care about
 * (a configurable name filter) so a full inventory does not hide the one drop that matters.
 *
 * <p>Item names come from the stack the client already received; nothing hidden is read.
 */
public final class ItemEspModule extends WorldEspModule {

    private final IntSetting range = add("Range", "Maximum distance to outline an item.", 32, 4, 96);
    private final BooleanSetting names = add("Names", "Show the item name.", true);
    private final BooleanSetting count = add("Count", "Show the stack size when above one.", true);
    private final BooleanSetting distance = add("Distance", "Show a distance label.", false);
    private final BooleanSetting filter = add("Filter", "Only outline items whose name contains the filter.", false);
    private final com.logvex.crystal.setting.StringSetting filterText =
            add("FilterText", "Case-insensitive name filter.", "shulker");
    private final IntSetting maxItems = add("MaxItems", "Cap on items outlined per frame.", 64, 4, 256);

    public ItemEspModule() {
        super(Category.RENDER, "ItemEsp", "Outlines dropped items with a name label.");
    }

    @Override
    public void collectEsp(WorldEspCollector out, MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        CrystalClient bootstrap = CrystalClient.get();
        Palette palette = bootstrap == null ? null : bootstrap.theme().palette();
        int color = palette == null ? 0xFFFFC46B : palette.warning();
        int dimColor = palette == null ? 0xFFB0B0C0 : palette.textDim();
        double maxDistance = range.value();
        String needle = filterText.value() == null ? "" : filterText.value().toLowerCase();
        int cap = maxItems.value();
        int drawn = 0;
        for (Entity entity : client.world.getEntities()) {
            if (drawn >= cap) {
                break;
            }
            if (!(entity instanceof ItemEntity item)) {
                continue;
            }
            if (EspFilter.skip(item, client.player, maxDistance)) {
                continue;
            }
            ItemStack stack = item.getStack();
            if (filter.value() && !needle.isEmpty()
                    && !stack.getName().getString().toLowerCase().contains(needle)) {
                continue;
            }
            var box = item.getBoundingBox().expand(0.12);
            out.addBox(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, color, 1.3f);
            drawn++;
            double midX = (box.minX + box.maxX) / 2;
            double midZ = (box.minZ + box.maxZ) / 2;
            if (names.value()) {
                String label = stack.getName().getString();
                if (count.value() && stack.getCount() > 1) {
                    label += " x" + stack.getCount();
                }
                out.addLabel(midX, box.maxY + 0.3, midZ, label, color);
            }
            if (distance.value()) {
                out.addLabel(midX, box.minY - 0.3, midZ,
                        Format.distance(client.player.distanceTo(item)), dimColor);
            }
        }
    }
}
