package com.logvex.crystal.gui;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.render.RenderUtil;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.util.ColorUtil;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * A dedicated HUD layout editor.
 *
 * <p>Drag any element to reposition it, right-click to hide/show it. Layout changes write
 * straight into the shared {@link HudLayout}, so the effect is visible immediately and is
 * persisted with the module config.
 */
public final class HudEditorScreen extends Screen {

    private final HudManager hud;
    private final com.logvex.crystal.theme.ThemeManager theme;

    private String dragging;
    private int dragOffsetX;
    private int dragOffsetY;
    private double mouseX;
    private double mouseY;
    private java.util.Map<String, int[]> anchors = java.util.Map.of();

    public HudEditorScreen(HudManager hud, com.logvex.crystal.theme.ThemeManager theme) {
        super(Text.literal("HUD Editor"));
        this.hud = hud;
        this.theme = theme;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** Asks the client to persist the layout through the debounced autosave. */
    private static void markDirty() {
        CrystalClient bootstrap = CrystalClient.get();
        if (bootstrap != null) {
            bootstrap.requestSave();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        if (textRenderer == null) {
            return;
        }
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        Palette palette = theme.palette();
        anchors = hud.anchors(client, width);
        hud.renderEditorOverlay(context, client, palette, mouseX, mouseY);
        RenderUtil.roundedPanel(context, 8, 8, 240, 26, 6, ColorUtil.withAlpha(palette.panel(), 220), palette.border());
        context.drawTextWithShadow(textRenderer, "\u2661 HUD editor", 14, 12, palette.primary());
        context.drawTextWithShadow(textRenderer, "drag \u00B7 right-click hide \u00B7 scroll scale \u00B7 shift-scroll fade \u00B7 Esc done",
                14, 22, palette.textDim());
        if (dragging != null) {
            HudLayout.Entry entry = hud.layout().entry(dragging);
            entry.customPosition = true;
            entry.x = mouseX - dragOffsetX;
            entry.y = mouseY - dragOffsetY;
        }
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        for (HudElement element : hud.elements()) {
            int[] bounds = boundsOf(element);
            if (click.x() >= bounds[0] - 2 && click.x() <= bounds[0] + bounds[2] + 2
                    && click.y() >= bounds[1] - 2 && click.y() <= bounds[1] + bounds[3] + 2) {
                HudLayout.Entry entry = hud.layout().entry(element.id());
                if (click.button() == 1) {
                    // Toggle the owning module when there is one, so the element's
                    // visibility and the module's enabled state can never disagree;
                    // an unowned element (e.g. notifications) toggles directly.
                    var owner = hud.moduleForElement(element.id());
                    if (owner != null) {
                        owner.toggle(hud.modules());
                    } else {
                        entry.visible = !entry.visible;
                    }
                } else {
                    dragging = element.id();
                    dragOffsetX = (int) click.x() - bounds[0];
                    dragOffsetY = (int) click.y() - bounds[1];
                }
                markDirty();
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging != null) {
            markDirty();
        }
        dragging = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (HudElement element : hud.elements()) {
            int[] bounds = boundsOf(element);
            if (mouseX >= bounds[0] - 2 && mouseX <= bounds[0] + bounds[2] + 2
                    && mouseY >= bounds[1] - 2 && mouseY <= bounds[1] + bounds[3] + 2) {
                HudLayout.Entry entry = hud.layout().entry(element.id());
                // Plain scroll scales the element; Shift+scroll changes its opacity.
                if (shiftDown()) {
                    entry.opacity = clamp01(entry.opacity + (float) verticalAmount * 0.05f);
                } else {
                    entry.scale = clamp(entry.scale + (float) verticalAmount * 0.05f, 0.5f, 2f);
                }
                markDirty();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean shiftDown() {
        var window = client == null ? null : client.getWindow();
        return window != null && net.minecraft.client.util.InputUtil.isKeyPressed(window,
                org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            CrystalClient bootstrap = CrystalClient.get();
            if (bootstrap != null) {
                bootstrap.saveNow();
            }
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    /** Returns {x, y, width, height} for an element in its current placement. */
    private int[] boundsOf(HudElement element) {
        int[] anchor = anchors.getOrDefault(element.id(), new int[]{4, 4});
        return new int[]{anchor[0], anchor[1], Math.max(40, element.width(client)), Math.max(12, element.height(client))};
    }
}
