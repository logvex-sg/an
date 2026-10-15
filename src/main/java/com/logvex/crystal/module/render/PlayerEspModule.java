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
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * PlayerEsp: a focused outline for other players, with configurable name, health, armour
 * and distance labels and a distinct colour for friends.
 *
 * <p>Only loaded entities the client already tracks are considered, and the scan is
 * distance-limited and capped per frame. Nothing hidden is inferred; this is a visual
 * overlay that changes no game state.
 */
public final class PlayerEspModule extends WorldEspModule {

    private final IntSetting range = add("Range", "Maximum distance to outline a player.", 64, 8, 256);
    private final BooleanSetting names = add("Names", "Show the player's name.", true);
    private final BooleanSetting health = add("Health", "Show a health label.", true);
    private final BooleanSetting armor = add("Armor", "Show an armour label.", false);
    private final BooleanSetting distance = add("Distance", "Show a distance label.", true);
    private final BooleanSetting friendColor = add("FriendColor", "Colour friends differently.", true);
    private final BooleanSetting ignoreInvisible = add("IgnoreInvisible", "Skip invisible players.", true);
    private final IntSetting maxPlayers = add("MaxPlayers", "Cap on players outlined per frame.", 32, 4, 128);

    public PlayerEspModule() {
        super(Category.RENDER, "PlayerEsp", "Outlines other players with name and health labels.");
    }

    @Override
    public void collectEsp(WorldEspCollector out, MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        CrystalClient bootstrap = CrystalClient.get();
        Palette palette = bootstrap == null ? null : bootstrap.theme().palette();
        int normal = palette == null ? 0xFFFF7FB0 : palette.danger();
        int friend = palette == null ? 0xFF8FE3FF : palette.crystalSelf();
        int textColor = palette == null ? 0xFFFFFFFF : palette.text();
        int healthColor = palette == null ? 0xFFFF9EC4 : palette.health();
        int armorColor = palette == null ? 0xFFA0D8FF : palette.armor();
        int dimColor = palette == null ? 0xFFB0B0C0 : palette.textDim();
        double maxDistance = range.value();
        int cap = maxPlayers.value();
        int drawn = 0;
        for (Entity entity : client.world.getEntities()) {
            if (drawn >= cap) {
                break;
            }
            if (!(entity instanceof PlayerEntity player) || player == client.player) {
                continue;
            }
            if (ignoreInvisible.value() && player.isInvisible()) {
                continue;
            }
            if (EspFilter.skip(player, client.player, maxDistance)) {
                continue;
            }
            var box = player.getBoundingBox();
            int color = friendColor.value() ? EspFilter.colorFor(player, normal, friend) : normal;
            out.addBox(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, color, 1.6f);
            drawn++;
            double midX = (box.minX + box.maxX) / 2;
            double midZ = (box.minZ + box.maxZ) / 2;
            double labelY = box.maxY + 0.5;
            if (names.value()) {
                out.addLabel(midX, labelY, midZ, player.getName().getString(), color);
                labelY += 0.42;
            }
            if (health.value()) {
                out.addLabel(midX, labelY, midZ, String.format("%.1f\u2661", player.getHealth()), healthColor);
                labelY += 0.42;
            }
            if (armor.value() && player.getArmor() > 0) {
                out.addLabel(midX, labelY, midZ, "\u26E8 " + player.getArmor(), armorColor);
            }
            if (distance.value()) {
                out.addLabel(midX, box.minY - 0.4, midZ,
                        Format.distance(client.player.distanceTo(player)), dimColor);
            }
        }
    }
}
