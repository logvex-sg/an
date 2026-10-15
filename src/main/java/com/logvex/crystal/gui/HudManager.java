package com.logvex.crystal.gui;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.gui.hud.CoordsHudElement;
import com.logvex.crystal.gui.hud.CrystalDamageHudElement;
import com.logvex.crystal.gui.hud.ModuleListHudElement;
import com.logvex.crystal.gui.hud.NotificationHudElement;
import com.logvex.crystal.gui.hud.PerformanceHudElement;
import com.logvex.crystal.gui.hud.TargetHudElement;
import com.logvex.crystal.gui.hud.WatermarkHudElement;
import com.logvex.crystal.module.ModuleManager;
import com.logvex.crystal.render.RenderUtil;
import com.logvex.crystal.render.esp.WorldEspProvider;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.theme.ThemeManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Owns the HUD: the set of elements, their layout, and how they stack.
 *
 * <p>Elements render on the render thread through the Fabric HUD callback. An element
 * with a custom position is drawn exactly where the user dragged it; otherwise it stacks
 * top-down in its column. The manager is also the authority used by the drag editor and
 * by commands that toggle individual elements.
 */
public final class HudManager {

    private final Map<String, HudElement> elements = new LinkedHashMap<>();
    private final List<OverlayRenderer> overlays = new ArrayList<>();
    private final List<WorldEspProvider> worldEspProviders = new ArrayList<>();
    private final ModuleManager modules;
    private final ThemeManager theme;
    private final HudLayout layout = new HudLayout();

    private boolean hudVisible = true;
    private int leftMargin = 4;
    private int rightMargin = 4;
    private float currentOpacity = 1f;

    public HudManager(ModuleManager modules, ThemeManager theme) {
        this.modules = modules;
        this.theme = theme;
        register(new WatermarkHudElement());
        register(new ModuleListHudElement(modules));
        register(new TargetHudElement());
        register(new CrystalDamageHudElement());
        register(new CoordsHudElement());
        register(new PerformanceHudElement());
        register(new NotificationHudElement());
    }

    public void register(HudElement element) {
        elements.put(element.id(), element);
        layout.entry(element.id());
    }

    public HudLayout layout() {
        return layout;
    }

    public boolean isHudVisible() {
        return hudVisible;
    }

    public void toggleHud() {
        hudVisible = !hudVisible;
    }

    public void setHudVisible(boolean visible) {
        this.hudVisible = visible;
    }

    public void setElementVisible(String id, boolean value) {
        if (elements.containsKey(id)) {
            layout.entry(id).visible = value;
        }
    }

    public boolean isElementVisible(String id) {
        return layout.entry(id).visible;
    }

    public HudElement element(String id) {
        return elements.get(id);
    }

    public List<HudElement> elements() {
        return new ArrayList<>(elements.values());
    }

    /** Registers a screen-space overlay drawn after the panels. */
    public void registerOverlay(OverlayRenderer overlay) {
        overlays.add(overlay);
    }

    /** Removes a previously registered overlay. */
    public void removeOverlay(OverlayRenderer overlay) {
        overlays.remove(overlay);
    }

    // ------------------------------------------------------------ world ESP

    /**
     * Registers a world-space ESP provider. The client's world-render hook collects every
     * provider into one buffer and draws it in a single pass, so a module only has to say
     * what to show. Guarded against double registration so a module that is toggled twice
     * cannot accumulate duplicate providers.
     */
    public void registerWorldEsp(WorldEspProvider provider) {
        if (!worldEspProviders.contains(provider)) {
            worldEspProviders.add(provider);
        }
    }

    public void removeWorldEsp(WorldEspProvider provider) {
        worldEspProviders.remove(provider);
    }

    public boolean hasWorldEsp() {
        return !worldEspProviders.isEmpty();
    }

    /**
     * Fills {@code out} from every registered provider. Each provider is isolated so one
     * failing ESP module cannot stop the others or corrupt the frame.
     */
    public void collectWorldEsp(com.logvex.crystal.render.esp.WorldEspCollector out, MinecraftClient client) {
        for (WorldEspProvider provider : worldEspProviders) {
            try {
                provider.collectEsp(out, client);
            } catch (Throwable t) {
                CrystalClient.LOG.error("World ESP provider failed", t);
            }
        }
    }

    /** Draws every visible element, honouring custom positions, then auto-stacked columns. */
    public void render(DrawContext context, MinecraftClient client, float tickDelta) {
        if (!hudVisible || client.player == null) {
            return;
        }
        Palette palette = theme.palette();
        int screenWidth = context.getScaledWindowWidth();
        int leftY = leftMargin;
        int rightY = rightMargin;
        for (HudElement element : elements.values()) {
            HudLayout.Entry entry = layout.entry(element.id());
            if (!entry.visible) {
                continue;
            }
            try {
                boolean right = element.column() == HudElement.Column.RIGHT;
                int x;
                int y;
                if (entry.customPosition) {
                    x = entry.x;
                    y = entry.y;
                } else if (right) {
                    x = screenWidth - rightMargin;
                    y = rightY;
                } else {
                    x = leftMargin;
                    y = leftY;
                }
                renderScaled(context, element, entry, client, tickDelta, x, y, right);
                if (!entry.customPosition) {
                    int advance = element.height(client) + 2;
                    if (right) {
                        rightY += advance;
                    } else {
                        leftY += advance;
                    }
                }
            } catch (Throwable t) {
                // A broken HUD element must never crash the frame.
                CrystalClient.LOG.error("HUD element {} failed", element.id(), t);
                entry.visible = false;
            }
        }
        for (OverlayRenderer overlay : overlays) {
            try {
                overlay.renderOverlay(context, client, tickDelta);
            } catch (Throwable t) {
                CrystalClient.LOG.error("HUD overlay failed", t);
            }
        }
    }

    /**
     * Computes the on-screen anchor of every element using the same stacking rules as
     * {@link #render}. The editor overlay and its hit-testing use this so their frames sit
     * where the elements are (or would be) drawn.
     *
     * <p>Unlike {@link #render}, hidden elements are included and still occupy a slot, so
     * the layout editor can always reach an element to re-show it and positions do not jump
     * when visibility is toggled.
     */
    public java.util.Map<String, int[]> anchors(MinecraftClient client, int screenWidth) {
        java.util.Map<String, int[]> result = new java.util.LinkedHashMap<>();
        int leftY = leftMargin;
        int rightY = rightMargin;
        for (HudElement element : elements.values()) {
            HudLayout.Entry entry = layout.entry(element.id());
            boolean right = element.column() == HudElement.Column.RIGHT;
            int x;
            int y;
            if (entry.customPosition) {
                x = entry.x;
                y = entry.y;
            } else if (right) {
                x = screenWidth - rightMargin;
                y = rightY;
            } else {
                x = leftMargin;
                y = leftY;
            }
            result.put(element.id(), new int[]{x, y});
            if (!entry.customPosition) {
                int advance = element.height(client) + 2;
                if (right) {
                    rightY += advance;
                } else {
                    leftY += advance;
                }
            }
        }
        return result;
    }

    /**
     * Renders one element with its per-element scale applied about its anchor, and exposes
     * its opacity for the shared text helpers. The matrix is pushed/popped in a finally so
     * a throwing element can never leave the render matrix stack unbalanced.
     */
    private void renderScaled(DrawContext context, HudElement element, HudLayout.Entry entry,
                              MinecraftClient client, float tickDelta, int x, int y, boolean right) {
        float scale = entry.scale <= 0.05f ? 1f : entry.scale;
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        try {
            if (scale != 1f) {
                matrices.translate(x, y);
                matrices.scale(scale, scale);
                matrices.translate(-x, -y);
            }
            this.currentOpacity = clamp01(entry.opacity);
            element.render(context, client, tickDelta, x, y, right);
        } finally {
            this.currentOpacity = 1f;
            matrices.popMatrix();
        }
    }

    /** Opacity of the element currently being drawn; read by the shared text helpers. */
    public float currentOpacity() {
        return currentOpacity;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    /** Draws the drag-editor overlay: a soft frame and label around each element. */
    public void renderEditorOverlay(DrawContext context, MinecraftClient client, Palette palette,
                                    int mouseX, int mouseY) {
        int screenWidth = context.getScaledWindowWidth();
        java.util.Map<String, int[]> anchors = anchors(client, screenWidth);
        for (HudElement element : elements.values()) {
            int[] anchor = anchors.getOrDefault(element.id(), new int[]{leftMargin, leftMargin});
            int x = anchor[0];
            int y = anchor[1];
            int w = Math.max(40, element.width(client));
            int h = Math.max(12, element.height(client));
            boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
            int border = hovered ? palette.primary() : com.logvex.crystal.util.ColorUtil.scaleAlpha(palette.border(), 0.6f);
            RenderUtil.roundedOutline(context, x - 2, y - 2, w + 4, h + 4, palette.radius(4), border);
            context.drawTextWithShadow(client.textRenderer, element.id(), x, y - 9, palette.textDim());
        }
    }

    public ModuleManager modules() {
        return modules;
    }
}
