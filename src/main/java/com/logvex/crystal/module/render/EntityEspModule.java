package com.logvex.crystal.module.render;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.entity.EntityCategory;
import com.logvex.crystal.entity.EntityFilter;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.render.esp.WorldEspCollector;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

/**
 * EntityEsp: outlines nearby players, mobs and dropped items with a health/armour/distance
 * label, so a fight reads at a glance.
 *
 * <p>Everything comes from data the client already has (loaded entities and their synced
 * health/armour); nothing hidden is inferred. Friends are coloured differently so they are
 * never mistaken for a target, and the scan is distance-limited and capped per frame.
 *
 * <p>This is purely a visual overlay: it draws boxes and labels and changes no game state.
 */
public final class EntityEspModule extends WorldEspModule {

    private final IntSetting range = add("Range", "Maximum distance to outline an entity.", 48, 8, 128);
    private final BooleanSetting players = add("Players", "Outline other players.", true);
    private final BooleanSetting mobs = add("Mobs", "Outline hostile mobs.", false);
    private final BooleanSetting items = add("Items", "Outline dropped items.", false);
    private final BooleanSetting health = add("Health", "Show a health label.", true);
    private final BooleanSetting armor = add("Armor", "Show an armour label.", true);
    private final BooleanSetting distance = add("Distance", "Show a distance label.", true);
    private final BooleanSetting friendColor = add("FriendColor", "Use a distinct colour for friends.", true);
    private final IntSetting maxEntities = add("MaxEntities", "Cap on entities outlined per frame.", 64, 8, 256);

    public EntityEspModule() {
        super(Category.RENDER, "EntityEsp", "Outlines entities with health, armour and distance.");
    }

    @Override
    public void collectEsp(WorldEspCollector out, MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        Palette palette = CrystalClient.get() != null
                ? CrystalClient.get().theme().palette() : null;
        int cap = maxEntities.value();
        int drawn = 0;
        // The same visibility rules the HUD entity counter uses, so the two never disagree
        // about what counts as a visible player/mob/item. ESP reads live entities (not the
        // interval snapshot) so boxes stay frame-accurate during fast movement.
        EntityFilter filter = buildFilter();
        for (Entity entity : client.world.getEntities()) {
            if (drawn >= cap) {
                break;
            }
            if (entity == client.player || entity.isRemoved()) {
                continue;
            }
            boolean friend = friendColor.value() && entity instanceof PlayerEntity
                    && CrystalClient.get() != null
                    && CrystalClient.get().friends().isFriend(entity.getName().getString());
            double dist = client.player.distanceTo(entity);
            if (!filter.accepts(EntityCategory.of(entity), entity == client.player, friend,
                    entity.isInvisible(), entity instanceof LivingEntity, dist, entity.getName().getString())) {
                continue;
            }
            Box box = entity.getBoundingBox();
            int color = colorFor(entity, palette, friend);
            out.addBox(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, color, 1.5f);
            drawn++;
            if (health.value() && entity instanceof LivingEntity living) {
                out.addLabel((box.minX + box.maxX) / 2, box.maxY + 0.5, (box.minZ + box.maxZ) / 2,
                        String.format("%.1f", living.getHealth()), palette == null ? 0xFFFF9EC4 : palette.health());
            }
            if (armor.value() && entity instanceof LivingEntity living && living.getArmor() > 0) {
                out.addLabel((box.minX + box.maxX) / 2, box.maxY + 0.9, (box.minZ + box.maxZ) / 2,
                        "\u26E8 " + living.getArmor(), palette == null ? 0xFFA0D8FF : palette.armor());
            }
            if (distance.value()) {
                out.addLabel((box.minX + box.maxX) / 2, box.minY - 0.4, (box.minZ + box.maxZ) / 2,
                        String.format("%.1fm", dist), palette == null ? 0xFFFFFFFF : palette.text());
            }
        }
    }

    /** Translates the module's category toggles into the shared filter. */
    private EntityFilter buildFilter() {
        EntityFilter.Builder builder = EntityFilter.builder().range(range.value()).invisible(false);
        if (players.value()) {
            builder.categories(EntityCategory.PLAYER);
        }
        if (mobs.value()) {
            builder.categories(EntityCategory.HOSTILE, EntityCategory.PASSIVE);
        }
        if (items.value()) {
            builder.categories(EntityCategory.ITEM);
        }
        return builder.build();
    }

    private int colorFor(Entity entity, Palette palette, boolean friend) {
        if (friend) {
            return palette == null ? 0xFF8FE3FF : palette.crystalSelf();
        }
        if (entity instanceof PlayerEntity) {
            return palette == null ? 0xFFFF7FB0 : palette.danger();
        }
        return palette == null ? 0xFFD4B0F0 : palette.tertiary();
    }
}
