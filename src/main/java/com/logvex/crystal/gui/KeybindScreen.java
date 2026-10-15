package com.logvex.crystal.gui;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.render.RenderUtil;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.theme.ThemeManager;
import com.logvex.crystal.util.ColorUtil;
import com.logvex.crystal.util.KeyNames;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * The dedicated keybind manager.
 *
 * <p>Lists every bindable module with its current key, lets the user capture a new key or
 * clear it, and surfaces conflicts (two modules sharing a key) with a one-click resolve.
 * Bindings write straight into each module's {@link com.logvex.crystal.setting.KeybindSetting},
 * so a change here is identical to binding it in the ClickGUI and is persisted the next time
 * the config is saved.
 */
public final class KeybindScreen extends Screen {

    private final com.logvex.crystal.module.ModuleManager modules;
    private final ThemeManager theme;
    private final Runnable onChanged;

    private Module capturing;
    private double scroll;
    private int maxScroll;
    private String status = "";
    private long statusAt;

    public KeybindScreen(com.logvex.crystal.module.ModuleManager modules, ThemeManager theme, Runnable onChanged) {
        super(Text.literal("Keybinds"));
        this.modules = modules;
        this.theme = theme;
        this.onChanged = onChanged;
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

        int left = 40;
        int top = 30;
        int right = width - 40;
        int bottom = height - 30;
        RenderUtil.softShadow(context, left, top, right - left, bottom - top, palette.radius(10), 110);
        RenderUtil.roundedGradientPanel(context, left, top, right - left, bottom - top, palette.radius(10),
                palette.panel(), palette.panelAlt(), palette.border());

        context.drawTextWithShadow(textRenderer, "\u2661 Keybinds", left + 14, top + 12, palette.primary());
        context.drawTextWithShadow(textRenderer, "click a row to bind \u00B7 right-click to clear \u00B7 Esc cancels",
                left + 14, top + 24, palette.textDim());
        RenderUtil.horizontalGradient(context, left + 10, top + 38, right - left - 20, 2,
                palette.accentGradientStart(), palette.accentGradientEnd());

        int listTop = top + 46;
        int listBottom = bottom - 24;
        context.enableScissor(left + 6, listTop, right - 6, listBottom);
        List<Module> bindable = bindable();
        int y = listTop - (int) scroll;
        for (Module module : bindable) {
            if (y + 18 > listTop - 20 && y < listBottom) {
                drawRow(context, palette, module, left + 10, y, right - 10, mouseX, mouseY);
            }
            y += 20;
        }
        int contentHeight = y - (listTop - (int) scroll);
        maxScroll = Math.max(0, contentHeight - (listBottom - listTop));
        if (scroll > maxScroll) {
            scroll = maxScroll;
        }
        context.disableScissor();

        if (!status.isEmpty() && System.currentTimeMillis() - statusAt < 4000) {
            context.drawTextWithShadow(textRenderer, status, left + 14, bottom - 16, palette.primary());
        } else {
            context.drawTextWithShadow(textRenderer, bindable.size() + " bindable modules", left + 14,
                    bottom - 16, palette.textDim());
        }
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private void drawRow(DrawContext context, Palette palette, Module module, int x, int y, int right,
                         int mouseX, int mouseY) {
        int w = right - x;
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 17;
        List<Module> conflict = conflictsOf(module);
        int border = !conflict.isEmpty() ? palette.warning() : palette.borderSoft();
        RenderUtil.roundedPanel(context, x, y, w, 17, palette.radius(4),
                hovered ? palette.panelHover() : palette.card(), border);
        context.drawTextWithShadow(textRenderer, module.category().icon() + " " + module.name(),
                x + 7, y + 5, module.isEnabled() ? palette.primary() : palette.text());
        String key = capturing == module ? "<press a key>"
                : (module.keybind().isBound() ? KeyNames.name(module.keybind().key()) : "unbound");
        int keyColor = capturing == module ? palette.secondary()
                : (module.keybind().isBound() ? palette.text() : palette.textDim());
        context.drawTextWithShadow(textRenderer, key, right - 90, y + 5, keyColor);
        if (!conflict.isEmpty()) {
            StringBuilder names = new StringBuilder("\u26A0 ");
            for (Module other : conflict) {
                names.append(other.name()).append(' ');
            }
            context.drawTextWithShadow(textRenderer, names.toString().trim(), right - 250, y + 5, palette.warning());
        }
    }

    /** Other modules sharing this module's key, when it is bound. Empty otherwise. */
    private List<Module> conflictsOf(Module module) {
        List<Module> result = new ArrayList<>();
        if (!module.keybind().isBound()) {
            return result;
        }
        for (Module other : modules.all()) {
            if (other != module && other.keybind().isBound()
                    && other.keybind().key() == module.keybind().key() && !other.isAlwaysEnabled()) {
                result.add(other);
            }
        }
        return result;
    }

    private List<Module> bindable() {
        List<Module> result = new ArrayList<>();
        for (Module module : modules.all()) {
            if (!module.isAlwaysEnabled()) {
                result.add(module);
            }
        }
        return result;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (capturing != null) {
            return true;
        }
        int left = 40 + 10;
        int right = width - 40 - 10;
        int listTop = 30 + 46;
        int listBottom = height - 30 - 24;
        if (click.x() < left || click.x() > right || click.y() < listTop || click.y() > listBottom) {
            return super.mouseClicked(click, doubled);
        }
        int y = listTop - (int) scroll;
        for (Module module : bindable()) {
            if (click.y() >= y && click.y() <= y + 17) {
                if (click.button() == 1) {
                    module.keybind().unbind();
                    setStatus("Cleared " + module.name());
                    changed();
                } else {
                    capturing = module;
                    setStatus("Press a key for " + module.name() + " (Esc to cancel)");
                }
                return true;
            }
            y += 20;
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
        if (capturing != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                setStatus("Binding cancelled");
            } else {
                Module target = capturing;
                // Give the key to the module being bound, clearing it from any other holder
                // so a single press never fires two modules. Centralised in the manager so
                // the GUI and the ; bind command behave identically.
                int cleared = modules.resolveKeybindConflict(target, key);
                target.keybind().set(key);
                setStatus(cleared > 0
                        ? "Bound " + target.name() + " to " + KeyNames.name(key) + " (cleared " + cleared + ")"
                        : "Bound " + target.name() + " to " + KeyNames.name(key));
                changed();
            }
            capturing = null;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    private void changed() {
        if (onChanged != null) {
            onChanged.run();
        }
    }

    @Override
    public void close() {
        CrystalClient bootstrap = CrystalClient.get();
        if (bootstrap != null) {
            bootstrap.saveNow();
        }
        super.close();
    }
}
