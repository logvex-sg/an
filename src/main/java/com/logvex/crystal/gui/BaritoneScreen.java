package com.logvex.crystal.gui;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.integration.baritone.BaritoneGoal;
import com.logvex.crystal.integration.baritone.BaritoneService;
import com.logvex.crystal.integration.baritone.BaritoneStatus;
import com.logvex.crystal.render.RenderUtil;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.theme.ThemeManager;
import com.logvex.crystal.util.ColorUtil;
import java.util.ArrayList;
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
 * The Baritone control screen: the current navigation status, the recent history, and buttons
 * to issue the common actions without typing a command.
 *
 * <p>Like every other screen in the client it is themed by the active palette, so it matches the
 * ClickGUI and the HUD. Every action is delegated to the shared
 * {@link com.logvex.crystal.integration.baritone.BaritoneService}, which reports failure as a
 * message rather than an exception - so this screen can never crash the client when Baritone is
 * absent.
 */
public final class BaritoneScreen extends Screen {

    private static final int ROW_HEIGHT = 20;

    private final ThemeManager theme;
    private final BaritoneService service;

    private boolean showSettings;
    private String status = "";
    private long statusAt;

    // Settings-editing state: the selected setting row and the text being typed into it.
    private static final int SETTINGS_ROW_H = 14;
    private String editName;
    private String editValue;
    private int settingsScroll;

    // Hit boxes captured during render so clicks stay in step with what was drawn.
    private int btnGotoX;
    private int btnGotoY;
    private int btnGotoW;
    private int btnCancelX;
    private int btnCancelY;
    private int btnCancelW;
    private int btnPauseX;
    private int btnPauseY;
    private int btnPauseW;
    private int btnSettingsX;
    private int btnSettingsY;
    private int btnSettingsW;
    private static final int BTN_H = 16;

    public BaritoneScreen(ThemeManager theme, BaritoneService service) {
        super(Text.literal("Baritone"));
        this.theme = theme;
        this.service = service;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void setStatus(String message) {
        this.status = message == null ? "" : message;
        this.statusAt = System.currentTimeMillis();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        if (textRenderer == null) {
            return;
        }
        Palette palette = theme.palette();
        context.fill(0, 0, width, height, ColorUtil.withAlpha(0xFF000000, 170));

        int left = 40;
        int top = 30;
        int right = width - 40;
        int bottom = height - 30;
        RenderUtil.softShadow(context, left, top, right - left, bottom - top, palette.radius(10), 110);
        RenderUtil.roundedGradientPanel(context, left, top, right - left, bottom - top, palette.radius(10),
                palette.panel(), palette.panelAlt(), palette.border());

        context.drawTextWithShadow(textRenderer, "\u2661 Baritone", left + 14, top + 12, palette.primary());
        context.drawTextWithShadow(textRenderer, "pathing status \u00B7 history \u00B7 controls \u00B7 Esc closes",
                left + 14, top + 24, palette.textDim());

        BaritoneStatus snapshot = service.status();

        int infoTop = top + 40;
        int y = infoTop;
        for (String line : infoLines(snapshot)) {
            context.drawTextWithShadow(textRenderer, line, left + 14, y, palette.text());
            y += 11;
        }

        // Action buttons along the bottom of the info block.
        int bx = left + 14;
        int by = infoTop + infoLines(snapshot).size() * 11 + 8;
        boolean enabled = service.available();
        btnGotoX = bx;
        btnGotoY = by;
        btnGotoW = 78;
        bx += btnGotoW + 6;
        btnCancelX = bx;
        btnCancelY = by;
        btnCancelW = 62;
        bx += btnCancelW + 6;
        // Pause toggles to Resume based on the live paused state, so one button expresses both.
        boolean paused = snapshot.paused();
        btnPauseX = bx;
        btnPauseY = by;
        btnPauseW = 64;
        bx += btnPauseW + 6;
        btnSettingsX = bx;
        btnSettingsY = by;
        btnSettingsW = 76;

        button(context, palette, btnGotoX, btnGotoY, btnGotoW, "Go to here", enabled, mouseX, mouseY);
        button(context, palette, btnCancelX, btnCancelY, btnCancelW, "Cancel", enabled, mouseX, mouseY);
        button(context, palette, btnPauseX, btnPauseY, btnPauseW, paused ? "Resume" : "Pause", enabled,
                mouseX, mouseY);
        button(context, palette, btnSettingsX, btnSettingsY, btnSettingsW, "Settings", true, mouseX, mouseY);

        int listTop = by + BTN_H + 12;
        int listBottom = bottom - 22;
        if (showSettings) {
            renderSettings(context, palette, left + 14, listTop, right - 14, listBottom);
        } else {
            renderHistory(context, palette, left + 14, listTop, right - 14, listBottom);
        }

        if (!status.isEmpty() && System.currentTimeMillis() - statusAt < 4000) {
            context.drawTextWithShadow(textRenderer, status, left + 14, bottom - 16, palette.primary());
        } else {
            context.drawTextWithShadow(textRenderer, "Baritone " + snapshot.version(), left + 14, bottom - 16,
                    palette.textDim());
        }
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private List<String> infoLines(BaritoneStatus snapshot) {
        List<String> lines = new ArrayList<>();
        if (!service.probed()) {
            lines.add("probing for Baritone\u2026");
            return lines;
        }
        if (!snapshot.available()) {
            lines.add("unavailable: " + snapshot.reason());
            return lines;
        }
        lines.add("version: " + snapshot.version());
        lines.add("state: " + snapshot.statusText());
        if (snapshot.hasGoal()) {
            lines.add("goal: " + snapshot.goalType() + " " + snapshot.goalDetail());
            if (snapshot.hasGoalPosition()) {
                lines.add("target: " + snapshot.goalX() + " " + snapshot.goalY() + " " + snapshot.goalZ());
            }
            if (snapshot.pathLength() > 0) {
                lines.add("path: " + snapshot.pathLength() + " nodes"
                        + (snapshot.ticksToGoal() > 0 ? " \u00B7 ~" + (snapshot.ticksToGoal() / 20) + "s" : ""));
            }
            if (snapshot.paused()) {
                lines.add("paused");
            }
        } else {
            lines.add("no active goal");
        }
        return lines;
    }

    private void renderHistory(DrawContext context, Palette palette, int x, int top, int right, int bottom) {
        context.drawTextWithShadow(textRenderer, "history", x, top - 10, palette.secondary());
        List<com.logvex.crystal.integration.baritone.BaritoneHistory.Entry> entries =
                service.history().recent(12);
        if (entries.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "no transitions recorded yet", x, top, palette.textDim());
            return;
        }
        int y = top;
        for (var entry : entries) {
            if (y + ROW_HEIGHT > bottom) {
                break;
            }
            RenderUtil.roundedPanel(context, x, y, right - x, ROW_HEIGHT - 2, palette.radius(3),
                    palette.card(), palette.borderSoft());
            context.drawTextWithShadow(textRenderer, entry.detail(), x + 6, y + 5, palette.text());
            String kind = entry.kind();
            context.drawTextWithShadow(textRenderer, kind,
                    right - 6 - textRenderer.getWidth(kind), y + 5,
                    "error".equals(kind) ? palette.danger() : palette.textDim());
            y += ROW_HEIGHT;
        }
    }

    private void renderSettings(DrawContext context, Palette palette, int x, int top, int right, int bottom) {
        context.drawTextWithShadow(textRenderer, "Baritone settings \u00B7 click to edit \u00B7 Enter applies",
                x, top - 10, palette.secondary());
        List<String> names = service.settingNames();
        if (names.isEmpty()) {
            context.drawTextWithShadow(textRenderer,
                    service.available() ? "no settings exposed" : "Baritone unavailable", x, top, palette.textDim());
            return;
        }
        // Keep the selected row inside the visible window when the list scrolls.
        int rows = Math.max(1, (bottom - top) / SETTINGS_ROW_H);
        settingsScroll = Math.max(0, Math.min(settingsScroll, Math.max(0, names.size() - rows)));

        for (int i = settingsScroll; i < names.size(); i++) {
            int y = top + (i - settingsScroll) * SETTINGS_ROW_H;
            if (y + SETTINGS_ROW_H > bottom) {
                break;
            }
            String name = names.get(i);
            boolean selected = name.equals(editName);
            RenderUtil.roundedPanel(context, x, y, right - x, SETTINGS_ROW_H - 2, palette.radius(3),
                    selected ? palette.panelHover() : palette.card(),
                    selected ? palette.primary() : palette.borderSoft());
            context.drawTextWithShadow(textRenderer, name, x + 6, y + 3, palette.text());

            String value = selected && editValue != null ? editValue : service.getSetting(name);
            if (value != null) {
                String shown = selected ? value + "_" : value;
                context.drawTextWithShadow(textRenderer, shown,
                        right - 8 - textRenderer.getWidth(shown), y + 3,
                        selected ? palette.primary() : palette.textDim());
            }
        }
    }

    private void button(DrawContext context, Palette palette, int x, int y, int w, String label,
                        boolean enabled, int mouseX, int mouseY) {
        boolean hovered = enabled && mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + BTN_H;
        int fill = !enabled ? palette.card() : hovered ? palette.panelHover() : palette.panel();
        int border = enabled ? palette.primary() : palette.borderSoft();
        RenderUtil.roundedPanel(context, x, y, w, BTN_H, palette.radius(4), fill, border);
        int textX = x + (w - textRenderer.getWidth(label)) / 2;
        context.drawTextWithShadow(textRenderer, label, textX, y + 4,
                enabled ? palette.text() : palette.textDim());
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int x = (int) click.x();
        int y = (int) click.y();
        if (inButton(x, y, btnGotoX, btnGotoY, btnGotoW) && service.available()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) {
                setStatus("Not in a world");
            } else {
                var pos = mc.player.getBlockPos();
                String error = service.goTo(BaritoneGoal.block(pos.getX(), pos.getY(), pos.getZ()));
                setStatus(error == null ? "Pathing to your position" : error);
            }
            return true;
        }
        if (inButton(x, y, btnCancelX, btnCancelY, btnCancelW) && service.available()) {
            service.cancel();
            setStatus("Cancelled");
            return true;
        }
        if (inButton(x, y, btnPauseX, btnPauseY, btnPauseW) && service.available()) {
            boolean paused = service.status().paused();
            String error = paused ? service.resume() : service.pause();
            setStatus(error == null ? (paused ? "Resumed" : "Paused") : error);
            return true;
        }
        if (inButton(x, y, btnSettingsX, btnSettingsY, btnSettingsW)) {
            showSettings = !showSettings;
            if (!showSettings) {
                editName = null;
                editValue = null;
            }
            return true;
        }
        if (showSettings && service.available() && y >= settingsListTop() && y <= settingsListBottom()) {
            int index = settingsScroll + (y - settingsListTop()) / SETTINGS_ROW_H;
            List<String> names = service.settingNames();
            if (index >= 0 && index < names.size()) {
                selectSetting(names.get(index));
            }
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    /** The top y of the settings/history list region, recomputed to match render(). */
    private int settingsListTop() {
        return 30 + 40 + infoLines(service.status()).size() * 11 + 8 + BTN_H + 12;
    }

    private int settingsListBottom() {
        return height - 30 - 22;
    }

    private void selectSetting(String name) {
        editName = name;
        String current = service.getSetting(name);
        editValue = current == null ? "" : current;
        setStatus("Editing " + name + " \u2014 type a value, Enter applies");
    }

    /** Applies the value in the edit field through the service, reporting the result inline. */
    private void applySetting() {
        if (editName == null) {
            return;
        }
        String error = service.setSetting(editName, editValue);
        if (error == null) {
            setStatus("Set " + editName + " = " + editValue);
        } else {
            setStatus(error);
        }
    }

    private static boolean inButton(int mx, int my, int x, int y, int w) {
        return mx >= x && mx <= x + w && my >= y && my <= y + BTN_H;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (editName != null) {
                editName = null;
                editValue = null;
                return true;
            }
            close();
            return true;
        }
        if (editName != null) {
            if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
                applySetting();
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (editValue != null && !editValue.isEmpty()) {
                    editValue = editValue.substring(0, editValue.length() - 1);
                }
                return true;
            }
            // While editing, swallow other keys so they do not trigger the screen's shortcuts.
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (editName != null) {
            String typed = input.asString();
            if (!typed.isEmpty() && typed.charAt(0) >= ' ') {
                editValue = (editValue == null ? "" : editValue) + typed;
            }
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (showSettings) {
            int rows = Math.max(1, (settingsListBottom() - settingsListTop()) / SETTINGS_ROW_H);
            int max = Math.max(0, service.settingNames().size() - rows);
            settingsScroll = Math.max(0, Math.min(max, settingsScroll - (int) Math.signum(verticalAmount)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
}
