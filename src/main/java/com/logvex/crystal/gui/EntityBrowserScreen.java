package com.logvex.crystal.gui;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.entity.EntityBrowserModel;
import com.logvex.crystal.entity.EntityCategory;
import com.logvex.crystal.entity.EntityInfo;
import com.logvex.crystal.entity.EntityQuery;
import com.logvex.crystal.entity.EntityTracker;
import com.logvex.crystal.render.RenderUtil;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.theme.ThemeManager;
import com.logvex.crystal.util.ColorUtil;
import com.logvex.crystal.util.Format;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * The entity browser: a live, filterable view of the entities the client can currently see.
 *
 * <p>It reads the shared {@link EntityQuery} snapshot (never the world directly) and the
 * {@link EntityTracker} history for the inspector. Because every row is an immutable
 * {@link EntityInfo}, an entity that despawns while the browser is open simply drops out of
 * the next snapshot - there is no dangling reference and no crash. A captured selection is
 * matched by UUID and shows a clear "no longer tracked" notice rather than stale data.
 *
 * <p>All state is local to the screen; opening and closing it registers no listeners and
 * holds no references to entities.
 */
public final class EntityBrowserScreen extends Screen {

    private static final int ROW_HEIGHT = 18;

    private final ThemeManager theme;
    private final EntityBrowserModel model = new EntityBrowserModel();

    private double scroll;
    private int maxScroll;
    private UUID selected;
    private String status = "";
    private long statusAt;

    public EntityBrowserScreen(ThemeManager theme) {
        super(Text.literal("Entities"));
        this.theme = theme;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void setStatus(String message) {
        this.status = message;
        this.statusAt = System.currentTimeMillis();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        if (textRenderer == null) {
            return;
        }
        Palette palette = theme.palette();
        context.fill(0, 0, width, height, ColorUtil.withAlpha(0xFF000000, 170));

        int left = 30;
        int top = 24;
        int right = width - 30;
        int bottom = height - 24;
        RenderUtil.softShadow(context, left, top, right - left, bottom - top, palette.radius(10), 110);
        RenderUtil.roundedGradientPanel(context, left, top, right - left, bottom - top, palette.radius(10),
                palette.panel(), palette.panelAlt(), palette.border());

        context.drawTextWithShadow(textRenderer, "\u2661 Entities", left + 14, top + 10, palette.primary());
        context.drawTextWithShadow(textRenderer, "type to search \u00B7 click a column header to sort \u00B7 "
                + "click a row to inspect \u00B7 Esc closes", left + 14, top + 22, palette.textDim());

        drawToolbar(context, palette, left, top + 38, right);

        int listTop = top + 74;
        int listBottom = bottom - 22;
        List<EntityInfo> rows = model.apply(EntityQuery.get().all());
        drawHeader(context, palette, left + 10, listTop - 14, right - 10);
        context.enableScissor(left + 6, listTop, right - 6, listBottom);
        int y = listTop - (int) scroll;
        for (EntityInfo info : rows) {
            if (y + ROW_HEIGHT > listTop - ROW_HEIGHT && y < listBottom) {
                drawRow(context, palette, info, left + 10, y, right - 10, mouseX, mouseY);
            }
            y += ROW_HEIGHT;
        }
        int contentHeight = rows.size() * ROW_HEIGHT;
        maxScroll = Math.max(0, contentHeight - (listBottom - listTop));
        if (scroll > maxScroll) {
            scroll = maxScroll;
        }
        context.disableScissor();

        if (!status.isEmpty() && System.currentTimeMillis() - statusAt < 4000) {
            context.drawTextWithShadow(textRenderer, status, left + 14, bottom - 16, palette.primary());
        } else {
            context.drawTextWithShadow(textRenderer, rows.size() + " shown / " + EntityQuery.get().size()
                    + " visible", left + 14, bottom - 16, palette.textDim());
        }
        drawInspector(context, palette, right, top, bottom);
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private void drawToolbar(DrawContext context, Palette palette, int left, int y, int right) {
        int searchWidth = 160;
        RenderUtil.roundedPanel(context, left + 10, y, searchWidth, 16, palette.radius(4),
                palette.card(), palette.borderSoft());
        String text = model.search().isEmpty() ? "\u2315 search\u2026" : "\u2315 " + model.search();
        context.drawTextWithShadow(textRenderer, text, left + 16, y + 4,
                model.search().isEmpty() ? palette.textDim() : palette.text());

        int x = left + 10 + searchWidth + 8;
        for (EntityCategory category : EntityCategory.values()) {
            int w = textRenderer.getWidth(category.label()) + 12;
            boolean active = model.categories().contains(category);
            RenderUtil.roundedPanel(context, x, y, w, 16, palette.radius(4),
                    active ? palette.panelHover() : palette.card(),
                    active ? palette.primary() : palette.borderSoft());
            context.drawTextWithShadow(textRenderer, category.label(), x + 6, y + 4,
                    active ? palette.primary() : palette.textDim());
            x += w + 4;
            if (x > right - 20) {
                break;
            }
        }
    }

    private void drawHeader(DrawContext context, Palette palette, int x, int y, int right) {
        int cx = x;
        for (EntityBrowserModel.Column column : EntityBrowserModel.Column.values()) {
            if (!model.shows(column)) {
                continue;
            }
            String label = column.label();
            if (model.sort().name().startsWith(column.name()) || (column == EntityBrowserModel.Column.NAME
                    && model.sort() == EntityBrowserModel.Sort.NAME)) {
                label += model.ascending() ? " \u25B2" : " \u25BC";
            }
            context.drawTextWithShadow(textRenderer, label, cx, y, palette.secondary());
            cx += columnWidth(column);
        }
    }

    private void drawRow(DrawContext context, Palette palette, EntityInfo info, int x, int y, int right,
                         int mouseX, int mouseY) {
        int w = right - x;
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + ROW_HEIGHT - 1;
        boolean isSelected = selected != null && selected.equals(info.uuid());
        int fill = isSelected ? palette.panelHover() : (hovered ? palette.panelHover() : palette.card());
        RenderUtil.roundedPanel(context, x, y, w, ROW_HEIGHT - 1, palette.radius(3), fill,
                isSelected ? palette.primary() : palette.borderSoft());
        int cx = x + 4;
        for (EntityBrowserModel.Column column : EntityBrowserModel.Column.values()) {
            if (!model.shows(column)) {
                continue;
            }
            String value = cellText(column, info);
            int color = cellColor(column, info, palette);
            context.drawTextWithShadow(textRenderer, Format.truncate(value, 22), cx, y + 5, color);
            cx += columnWidth(column);
        }
    }

    private static String cellText(EntityBrowserModel.Column column, EntityInfo info) {
        return switch (column) {
            case NAME -> info.name();
            case CATEGORY -> info.category().label();
            case DISTANCE -> String.format("%.1f", info.distance());
            case HEALTH -> info.living() ? (int) info.health() + "/" + (int) info.maxHealth() : "-";
            case ARMOR -> info.living() ? Integer.toString(info.armor()) : "-";
            case FRIEND -> info.friend() ? "\u2661" : "";
            case SPEED -> String.format("%.1f", info.horizontalSpeed());
        };
    }

    private static int cellColor(EntityBrowserModel.Column column, EntityInfo info, Palette palette) {
        if (info.friend()) {
            return palette.primary();
        }
        return switch (column) {
            case HEALTH -> info.healthFraction() < 0.34 ? palette.danger() : palette.text();
            case DISTANCE -> palette.textDim();
            default -> palette.text();
        };
    }

    private int columnWidth(EntityBrowserModel.Column column) {
        return switch (column) {
            case NAME -> 150;
            case CATEGORY -> 70;
            case DISTANCE -> 54;
            case HEALTH -> 70;
            case ARMOR -> 54;
            case FRIEND -> 46;
            case SPEED -> 60;
        };
    }

    private void drawInspector(DrawContext context, Palette palette, int right, int top, int bottom) {
        if (selected == null) {
            return;
        }
        CrystalClient bootstrap = CrystalClient.get();
        EntityInfo info = null;
        for (EntityInfo candidate : EntityQuery.get().all()) {
            if (candidate.uuid().equals(selected)) {
                info = candidate;
                break;
            }
        }
        int panelWidth = 190;
        int x = right - panelWidth - 8;
        int y = top + 74;
        int h = bottom - 22 - y;
        RenderUtil.roundedPanel(context, x, y, panelWidth, h, palette.radius(6), palette.card(), palette.border());
        context.drawTextWithShadow(textRenderer, "Inspector", x + 8, y + 6, palette.primary());
        if (info == null) {
            context.drawTextWithShadow(textRenderer, "entity no longer tracked", x + 8, y + 22, palette.danger());
            if (bootstrap != null) {
                EntityTracker tracker = bootstrap.entityTracker();
                List<EntityInfo> history = tracker.history(selected);
                if (!history.isEmpty()) {
                    EntityInfo last = history.get(history.size() - 1);
                    context.drawTextWithShadow(textRenderer, "last seen " + last.name(), x + 8, y + 34,
                            palette.textDim());
                }
            }
            return;
        }
        int ty = y + 22;
        ty = inspectorLine(context, palette, x + 8, ty, "name", info.name());
        ty = inspectorLine(context, palette, x + 8, ty, "type", info.category().label());
        ty = inspectorLine(context, palette, x + 8, ty, "distance", Format.distance(info.distance()));
        ty = inspectorLine(context, palette, x + 8, ty, "pos", Format.coords(info.x(), info.y(), info.z()));
        ty = inspectorLine(context, palette, x + 8, ty, "velocity",
                String.format("%.2f, %.2f, %.2f", info.velocityX(), info.velocityY(), info.velocityZ()));
        ty = inspectorLine(context, palette, x + 8, ty, "speed", String.format("%.1f b/s", info.horizontalSpeed()));
        ty = inspectorLine(context, palette, x + 8, ty, "box",
                String.format("%.2f x %.2f x %.2f", info.box().getLengthX(), info.box().getLengthY(),
                        info.box().getLengthZ()));
        if (info.living()) {
            ty = inspectorLine(context, palette, x + 8, ty, "health",
                    (int) info.health() + "/" + (int) info.maxHealth() + " (" + Format.percent(info.healthFraction()) + ")");
            ty = inspectorLine(context, palette, x + 8, ty, "armor", Integer.toString(info.armor()));
        }
        ty = inspectorLine(context, palette, x + 8, ty, "friend", info.friend() ? "yes" : "no");
        ty = inspectorLine(context, palette, x + 8, ty, "invisible", info.invisible() ? "yes" : "no");
        ty = inspectorLine(context, palette, x + 8, ty, "line of sight", info.lineOfSight() ? "yes" : "no");
        if (bootstrap != null) {
            long observed = bootstrap.entityTracker().lastObservedMillis(info.uuid());
            if (observed > 0) {
                inspectorLine(context, palette, x + 8, ty, "last seen",
                        ((System.currentTimeMillis() - observed) / 1000) + "s ago");
            }
        }
    }

    private int inspectorLine(DrawContext context, Palette palette, int x, int y, String label, String value) {
        context.drawTextWithShadow(textRenderer, label, x, y, palette.textDim());
        context.drawTextWithShadow(textRenderer, Format.truncate(value, 20), x + 66, y, palette.text());
        return y + 11;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int left = 30;
        int right = width - 30;
        int top = 24;
        int bottom = height - 24;
        int searchY = top + 38;
        // Search field focuses/clears on click.
        if (click.y() >= searchY && click.y() <= searchY + 16 && click.x() >= left + 10
                && click.x() <= left + 170) {
            if (click.button() == 1) {
                model.setSearch("");
            }
            return true;
        }
        // Category chips.
        int x = left + 10 + 160 + 8;
        for (EntityCategory category : EntityCategory.values()) {
            int w = textRenderer.getWidth(category.label()) + 12;
            if (click.y() >= searchY && click.y() <= searchY + 16 && click.x() >= x && click.x() <= x + w) {
                model.toggleCategory(category);
                return true;
            }
            x += w + 4;
            if (x > right - 20) {
                break;
            }
        }
        // Column header: sort.
        int listTop = top + 74;
        if (click.y() >= listTop - 14 && click.y() <= listTop - 2) {
            int cx = left + 10;
            for (EntityBrowserModel.Column column : EntityBrowserModel.Column.values()) {
                if (!model.shows(column)) {
                    continue;
                }
                int cw = columnWidth(column);
                if (click.x() >= cx && click.x() <= cx + cw) {
                    EntityBrowserModel.Sort target = switch (column) {
                        case NAME -> EntityBrowserModel.Sort.NAME;
                        case CATEGORY -> EntityBrowserModel.Sort.CATEGORY;
                        case HEALTH, ARMOR -> EntityBrowserModel.Sort.HEALTH;
                        default -> EntityBrowserModel.Sort.DISTANCE;
                    };
                    if (model.sort() == target) {
                        model.toggleDirection();
                    } else {
                        model.setSort(target);
                    }
                    return true;
                }
                cx += cw;
            }
            return true;
        }
        // Row selection.
        int listBottom = bottom - 22;
        if (click.y() >= listTop && click.y() <= listBottom && click.x() >= left + 6 && click.x() <= right - 6) {
            List<EntityInfo> rows = model.apply(EntityQuery.get().all());
            int index = (int) ((click.y() - listTop + scroll) / ROW_HEIGHT);
            if (index >= 0 && index < rows.size()) {
                selected = rows.get(index).uuid();
                setStatus("Inspecting " + rows.get(index).name());
            }
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, Math.min(maxScroll, scroll - verticalAmount * 18));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (!model.search().isEmpty()) {
                model.setSearch("");
                return true;
            }
            close();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            String search = model.search();
            if (!search.isEmpty()) {
                model.setSearch(search.substring(0, search.length() - 1));
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        String typed = input.asString();
        if (!typed.isEmpty() && typed.charAt(0) >= ' ') {
            model.setSearch(model.search() + typed);
            scroll = 0;
            return true;
        }
        return super.charTyped(input);
    }
}
