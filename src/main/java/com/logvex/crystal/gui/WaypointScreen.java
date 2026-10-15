package com.logvex.crystal.gui;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.render.RenderUtil;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.theme.ThemeManager;
import com.logvex.crystal.util.ColorUtil;
import com.logvex.crystal.util.Format;
import com.logvex.crystal.util.waypoint.Navigation;
import com.logvex.crystal.util.waypoint.Waypoint;
import com.logvex.crystal.util.waypoint.WaypointStore;
import com.logvex.crystal.world.WorldInfo;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * The waypoint navigation screen: every saved waypoint with its bearing and distance, a
 * search box, and safe deletion (a delete needs a second click to confirm).
 *
 * <p>Waypoints in another dimension are shown but clearly marked as not reachable from here,
 * rather than given a misleading straight-line distance. Deleting only ever removes the entry
 * the user confirmed, and every change is persisted immediately.
 */
public final class WaypointScreen extends Screen {

    private static final int ROW_HEIGHT = 20;

    private final ThemeManager theme;

    private double scroll;
    private int maxScroll;
    private String search = "";
    private String pendingDelete = "";
    private String status = "";
    private long statusAt;

    public WaypointScreen(ThemeManager theme) {
        super(Text.literal("Waypoints"));
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
        CrystalClient bootstrap = CrystalClient.get();
        Palette palette = theme.palette();
        context.fill(0, 0, width, height, ColorUtil.withAlpha(0xFF000000, 170));

        int left = 40;
        int top = 30;
        int right = width - 40;
        int bottom = height - 30;
        RenderUtil.softShadow(context, left, top, right - left, bottom - top, palette.radius(10), 110);
        RenderUtil.roundedGradientPanel(context, left, top, right - left, bottom - top, palette.radius(10),
                palette.panel(), palette.panelAlt(), palette.border());

        context.drawTextWithShadow(textRenderer, "\u2661 Waypoints", left + 14, top + 12, palette.primary());
        context.drawTextWithShadow(textRenderer, "type to search \u00B7 click a row to delete (confirm) \u00B7 Esc closes",
                left + 14, top + 24, palette.textDim());

        int searchY = top + 38;
        RenderUtil.roundedPanel(context, left + 10, searchY, 180, 16, palette.radius(4), palette.card(),
                palette.borderSoft());
        String searchText = search.isEmpty() ? "\u2315 search\u2026" : "\u2315 " + search;
        context.drawTextWithShadow(textRenderer, searchText, left + 16, searchY + 4,
                search.isEmpty() ? palette.textDim() : palette.text());

        int listTop = top + 62;
        int listBottom = bottom - 22;
        List<Waypoint> rows = bootstrap == null ? List.of() : visible(bootstrap.waypoints());
        context.enableScissor(left + 6, listTop, right - 6, listBottom);
        int y = listTop - (int) scroll;
        for (Waypoint waypoint : rows) {
            if (y + ROW_HEIGHT > listTop - ROW_HEIGHT && y < listBottom) {
                drawRow(context, palette, bootstrap, waypoint, left + 10, y, right - 10, mouseX, mouseY);
            }
            y += ROW_HEIGHT;
        }
        maxScroll = Math.max(0, rows.size() * ROW_HEIGHT - (listBottom - listTop));
        if (scroll > maxScroll) {
            scroll = maxScroll;
        }
        context.disableScissor();

        if (!status.isEmpty() && System.currentTimeMillis() - statusAt < 4000) {
            context.drawTextWithShadow(textRenderer, status, left + 14, bottom - 16, palette.primary());
        } else {
            context.drawTextWithShadow(textRenderer, rows.size() + " waypoint(s)", left + 14, bottom - 16,
                    palette.textDim());
        }
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private List<Waypoint> visible(WaypointStore store) {
        if (search.isBlank()) {
            return store.all();
        }
        return store.search(search);
    }

    private void drawRow(DrawContext context, Palette palette, CrystalClient bootstrap, Waypoint waypoint,
                         int x, int y, int right, int mouseX, int mouseY) {
        int w = right - x;
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + ROW_HEIGHT - 1;
        boolean confirming = waypoint.name().equalsIgnoreCase(pendingDelete);
        int border = confirming ? palette.danger() : palette.borderSoft();
        RenderUtil.roundedPanel(context, x, y, w, ROW_HEIGHT - 1, palette.radius(3),
                hovered ? palette.panelHover() : palette.card(), border);
        // A small colour swatch so the row reads as the same waypoint the ESP draws.
        RenderUtil.roundedRect(context, x + 5, y + 5, 6, 9, palette.radius(2), waypoint.color() | 0xFF000000);
        context.drawTextWithShadow(textRenderer, waypoint.name(), x + 16, y + 6, palette.text());
        context.drawTextWithShadow(textRenderer, Format.coords(waypoint.x(), waypoint.y(), waypoint.z()),
                x + 16 + 130, y + 6, palette.textDim());

        String nav = navigationText(bootstrap, waypoint);
        int navColor = confirming ? palette.danger() : palette.secondary();
        context.drawTextWithShadow(textRenderer, nav, right - 10 - textRenderer.getWidth(nav), y + 6, navColor);
    }

    private String navigationText(CrystalClient bootstrap, Waypoint waypoint) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            return waypoint.dimension().isEmpty() ? "any" : waypoint.dimension();
        }
        String dimension = WorldInfo.dimensionId(mc);
        return Navigation.describe(waypoint, mc.player.getX(), mc.player.getY(), mc.player.getZ(), dimension);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int left = 40;
        int right = width - 40;
        int top = 30;
        int bottom = height - 30;
        int searchY = top + 38;
        if (click.y() >= searchY && click.y() <= searchY + 16 && click.x() >= left + 10
                && click.x() <= left + 190) {
            if (click.button() == 1) {
                search = "";
            }
            return true;
        }
        CrystalClient bootstrap = CrystalClient.get();
        int listTop = top + 62;
        int listBottom = bottom - 22;
        if (bootstrap != null && click.y() >= listTop && click.y() <= listBottom
                && click.x() >= left + 6 && click.x() <= right - 6) {
            List<Waypoint> rows = visible(bootstrap.waypoints());
            int index = (int) ((click.y() - listTop + scroll) / ROW_HEIGHT);
            if (index >= 0 && index < rows.size()) {
                Waypoint waypoint = rows.get(index);
                if (waypoint.name().equalsIgnoreCase(pendingDelete)) {
                    bootstrap.waypoints().remove(waypoint.name());
                    bootstrap.saveAll();
                    pendingDelete = "";
                    setStatus("Deleted '" + waypoint.name() + "'");
                } else {
                    pendingDelete = waypoint.name();
                    setStatus("Click '" + waypoint.name() + "' again to confirm delete");
                }
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
            if (!search.isEmpty()) {
                search = "";
                return true;
            }
            if (!pendingDelete.isEmpty()) {
                pendingDelete = "";
                return true;
            }
            close();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        String typed = input.asString();
        if (!typed.isEmpty() && typed.charAt(0) >= ' ') {
            search += typed;
            scroll = 0;
            pendingDelete = "";
            return true;
        }
        return super.charTyped(input);
    }
}
