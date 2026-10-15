package com.logvex.crystal.gui;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.config.ProfileManager;
import com.logvex.crystal.config.ProfileService;
import com.logvex.crystal.config.StarterProfiles;
import com.logvex.crystal.render.RenderUtil;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.theme.ThemeManager;
import com.logvex.crystal.util.ColorUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * The profile manager screen.
 *
 * <p>Left column lists saved profiles and the built-in starters; the right column offers
 * load, rename, duplicate, delete, export and "new from current". Destructive actions
 * (delete, overwrite) require an explicit confirmation, and the manager never silently
 * overwrites a valid profile - the underlying {@link ProfileService} enforces that too.
 *
 * <p>All work goes through the service against the live configuration, so a load here is
 * identical to loading from a command and takes effect immediately.
 */
public final class ProfileScreen extends Screen {

    /** A pending yes/no confirmation before a destructive action runs. */
    private record Pending(String prompt, Runnable action) {
    }

    private final ProfileService service;
    private final ThemeManager theme;

    private final List<String> starters = StarterProfiles.names();
    private List<String> saved = new ArrayList<>();
    private int selected = -1;
    private boolean selectingStarter;

    private Pending pending;
    private boolean enteringName;
    private String nameBuffer = "";
    private Runnable nameCommit;
    private String status = "";
    private long statusAt;

    public ProfileScreen(ProfileService service, ThemeManager theme) {
        super(Text.literal("Profiles"));
        this.service = service;
        this.theme = theme;
        refresh();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void refresh() {
        saved = new ArrayList<>(service.names());
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
        context.drawTextWithShadow(textRenderer, "\u2727 Profiles", left + 14, top + 12, palette.primary());
        context.drawTextWithShadow(textRenderer, "select a profile, then choose an action",
                left + 14, top + 24, palette.textDim());
        RenderUtil.horizontalGradient(context, left + 10, top + 38, right - left - 20, 2,
                palette.accentGradientStart(), palette.accentGradientEnd());

        int listTop = top + 48;
        int listBottom = bottom - 60;
        int listRight = left + (right - left) / 2 - 6;

        context.drawTextWithShadow(textRenderer, "Saved", left + 14, listTop - 11, palette.tertiary());
        context.drawTextWithShadow(textRenderer, "Starters", listRight + 12, listTop - 11, palette.tertiary());
        context.enableScissor(left + 8, listTop, right - 8, listBottom);
        drawColumn(context, palette, saved, left + 12, listTop, listRight, mouseX, mouseY, false);
        drawColumn(context, palette, starters, listRight + 12, listTop, right - 12, mouseX, mouseY, true);
        context.disableScissor();

        drawButtons(context, palette, left + 12, bottom - 52, right - 12, mouseX, mouseY);

        if (pending != null) {
            drawConfirm(context, palette, mouseX, mouseY);
        } else if (enteringName) {
            drawNameEntry(context, palette);
        } else if (!status.isEmpty() && System.currentTimeMillis() - statusAt < 4500) {
            context.drawTextWithShadow(textRenderer, status, left + 14, bottom - 16, palette.primary());
        }
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private void drawColumn(DrawContext context, Palette palette, List<String> names, int x, int y, int right,
                            int mouseX, int mouseY, boolean starter) {
        int w = right - x;
        int rowY = y;
        for (int i = 0; i < names.size(); i++) {
            boolean isSelected = starter == selectingStarter && selected == i;
            boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= rowY && mouseY <= rowY + 18;
            RenderUtil.roundedPanel(context, x, rowY, w, 18, palette.radius(4),
                    isSelected ? ColorUtil.withAlpha(palette.primary(), 70)
                            : (hovered ? palette.panelHover() : palette.card()),
                    isSelected ? palette.border() : palette.borderSoft());
            String label = starter ? "\u2726 " + names.get(i) : "\u2661 " + names.get(i);
            context.drawTextWithShadow(textRenderer, label, x + 7, rowY + 5,
                    isSelected ? palette.text() : palette.textDim());
            rowY += 20;
        }
        if (names.isEmpty()) {
            context.drawTextWithShadow(textRenderer, starter ? "" : "no saved profiles",
                    x + 4, y + 4, palette.textDim());
        }
    }

    private void drawButtons(DrawContext context, Palette palette, int x, int y, int right, int mouseX, int mouseY) {
        String[] labels = {"New from current", "Load", "Rename", "Duplicate", "Delete", "Export", "Import"};
        int gap = 4;
        int w = (right - x - gap * (labels.length - 1)) / labels.length;
        for (int i = 0; i < labels.length; i++) {
            int bx = x + i * (w + gap);
            boolean hovered = mouseX >= bx && mouseX <= bx + w && mouseY >= y && mouseY <= y + 18;
            boolean danger = labels[i].equals("Delete");
            RenderUtil.roundedPanel(context, bx, y, w, 18, palette.radius(4),
                    hovered ? palette.panelHover() : palette.card(),
                    danger ? palette.warning() : palette.borderSoft());
            context.drawTextWithShadow(textRenderer, labels[i], bx + 6, y + 5,
                    danger ? palette.warning() : palette.text());
        }
    }

    private void drawConfirm(DrawContext context, Palette palette, int mouseX, int mouseY) {
        int w = 280;
        int h = 70;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        RenderUtil.softShadow(context, x, y, w, h, palette.radius(8), 120);
        RenderUtil.roundedPanel(context, x, y, w, h, palette.radius(8), palette.panel(), palette.border());
        context.drawTextWithShadow(textRenderer, pending.prompt(), x + 12, y + 14, palette.text());
        context.drawTextWithShadow(textRenderer, "[ Enter confirm ]   [ Esc cancel ]", x + 12, y + 42, palette.textDim());
    }

    private void drawNameEntry(DrawContext context, Palette palette) {
        int w = 280;
        int h = 70;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        RenderUtil.softShadow(context, x, y, w, h, palette.radius(8), 120);
        RenderUtil.roundedPanel(context, x, y, w, h, palette.radius(8), palette.panel(), palette.border());
        context.drawTextWithShadow(textRenderer, "Profile name:", x + 12, y + 12, palette.text());
        RenderUtil.roundedPanel(context, x + 12, y + 28, w - 24, 18, palette.radius(4),
                palette.panelAlt(), palette.border());
        String shown = nameBuffer.isEmpty() ? "type a name..." : nameBuffer + "_";
        context.drawTextWithShadow(textRenderer, shown, x + 18, y + 33,
                nameBuffer.isEmpty() ? palette.textDim() : palette.text());
        context.drawTextWithShadow(textRenderer, "[ Enter save ]   [ Esc cancel ]", x + 12, y + 52, palette.textDim());
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (pending != null) {
            return true;
        }
        if (enteringName) {
            return true;
        }
        int left = 40;
        int top = 30;
        int right = width - 40;
        int bottom = height - 30;
        int listTop = top + 48;
        int listBottom = bottom - 60;
        int listRight = left + (right - left) / 2 - 6;

        if (click.y() >= listTop && click.y() <= listBottom) {
            int index = (int) ((click.y() - listTop) / 20);
            if (click.x() >= left + 12 && click.x() <= listRight && index < saved.size()) {
                selected = index;
                selectingStarter = false;
                return true;
            }
            if (click.x() >= listRight + 12 && click.x() <= right - 12 && index < starters.size()) {
                selected = index;
                selectingStarter = true;
                return true;
            }
        }
        if (click.y() >= bottom - 52 && click.y() <= bottom - 34) {
            handleButtonClick(click.x(), left + 12, right - 12, bottom - 52);
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    private void handleButtonClick(double mouseX, int x, int right, int y) {
        String[] labels = {"New from current", "Load", "Rename", "Duplicate", "Delete", "Export", "Import"};
        int gap = 4;
        int w = (right - x - gap * (labels.length - 1)) / labels.length;
        for (int i = 0; i < labels.length; i++) {
            int bx = x + i * (w + gap);
            if (mouseX >= bx && mouseX <= bx + w) {
                activate(labels[i]);
                return;
            }
        }
    }

    private void activate(String action) {
        String name = selectedName();
        switch (action) {
            case "New from current" -> beginNameEntry("Save current as:", () -> {
                String typed = nameBuffer.trim();
                if (typed.isEmpty()) {
                    setStatus("Name cannot be empty");
                    return;
                }
                try {
                    boolean written = service.save(typed, false);
                    if (!written) {
                        String target = typed;
                        pending = new Pending("Overwrite '" + target + "'?", () -> overwrite(target));
                    } else {
                        setStatus("Saved profile '" + typed + "'");
                        refresh();
                    }
                } catch (IOException e) {
                    setStatus("Save failed: " + e.getMessage());
                }
            });
            case "Load" -> {
                if (name == null) {
                    setStatus("Select a profile first");
                    return;
                }
                applyProfile(name);
            }
            case "Rename" -> {
                if (name == null || selectingStarter) {
                    setStatus("Select a saved profile first");
                    return;
                }
                String from = name;
                beginNameEntry("Rename '" + from + "' to:", () -> {
                    String typed = nameBuffer.trim();
                    if (typed.isEmpty()) {
                        setStatus("Name cannot be empty");
                        return;
                    }
                    try {
                        boolean ok = service.profiles().rename(from, typed);
                        setStatus(ok ? "Renamed to '" + typed + "'" : "Rename failed (name taken?)");
                        refresh();
                    } catch (IOException e) {
                        setStatus("Rename failed: " + e.getMessage());
                    }
                });
            }
            case "Duplicate" -> {
                if (name == null) {
                    setStatus("Select a profile first");
                    return;
                }
                String from = name;
                beginNameEntry("Duplicate '" + from + "' as:", () -> {
                    String typed = nameBuffer.trim();
                    if (typed.isEmpty()) {
                        setStatus("Name cannot be empty");
                        return;
                    }
                    try {
                        boolean ok = service.profiles().duplicate(from, typed);
                        setStatus(ok ? "Duplicated to '" + typed + "'" : "Duplicate failed (name taken?)");
                        refresh();
                    } catch (IOException e) {
                        setStatus("Duplicate failed: " + e.getMessage());
                    }
                });
            }
            case "Delete" -> {
                if (name == null || selectingStarter) {
                    setStatus("Select a saved profile first");
                    return;
                }
                String target = name;
                pending = new Pending("Delete profile '" + target + "'?", () -> {
                    boolean ok = service.profiles().delete(target);
                    setStatus(ok ? "Deleted '" + target + "'" : "Delete failed");
                    selected = -1;
                    refresh();
                });
            }
            case "Export" -> export(name);
            case "Import" -> importProfiles();
            default -> {
            }
        }
    }

    private void applyProfile(String name) {
        if (StarterProfiles.names().contains(name) && !service.profiles().exists(name)) {
            ProfileManager.Profile starter = StarterProfiles.build(name);
            if (starter != null) {
                service.applyProfile(starter);
                CrystalClient bootstrap = CrystalClient.get();
                if (bootstrap != null) {
                    bootstrap.theme().update();
                }
                setStatus("Applied starter '" + name + "'");
            }
            return;
        }
        boolean ok = service.apply(name);
        CrystalClient bootstrap = CrystalClient.get();
        if (bootstrap != null) {
            bootstrap.theme().update();
        }
        setStatus(ok ? "Loaded '" + name + "'" : "Could not load '" + name + "'");
    }

    private void overwrite(String name) {
        try {
            service.save(name, true);
            setStatus("Overwrote '" + name + "'");
            refresh();
        } catch (IOException e) {
            setStatus("Save failed: " + e.getMessage());
        }
    }

    private void export(String name) {
        if (name == null) {
            setStatus("Select a profile first");
            return;
        }
        try {
            var document = service.export(name);
            if (document == null) {
                setStatus("Nothing to export for '" + name + "'");
                return;
            }
            var path = service.profiles().directory().resolve(name + ProfileManager.EXPORT_SUFFIX);
            java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.writeString(path, new com.google.gson.GsonBuilder().setPrettyPrinting().create()
                    .toJson(document));
            setStatus("Exported to " + path.getFileName());
        } catch (IOException e) {
            setStatus("Export failed: " + e.getMessage());
        }
    }

    /**
     * Imports every {@code *.export.json} document sitting in the profiles directory. Each
     * is parsed and stored under a unique name via the service, so an import never
     * overwrites an existing profile. The files are left in place so the user can re-import.
     */
    private void importProfiles() {
        var dir = service.profiles().directory();
        int imported = 0;
        int failed = 0;
        try (var stream = java.nio.file.Files.list(dir)) {
            for (var path : stream.filter(p -> p.getFileName().toString().endsWith(".export.json")).toList()) {
                try {
                    var text = java.nio.file.Files.readString(path);
                    var document = com.google.gson.JsonParser.parseString(text).getAsJsonObject();
                    String stored = service.importProfile(document);
                    if (stored != null) {
                        imported++;
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    failed++;
                }
            }
        } catch (IOException e) {
            setStatus("Import failed: " + e.getMessage());
            return;
        }
        refresh();
        setStatus(imported == 0 && failed == 0
                ? "No *.export.json files found in the profiles folder"
                : "Imported " + imported + (failed > 0 ? ", skipped " + failed : ""));
    }

    private void beginNameEntry(String prompt, Runnable commit) {
        enteringName = true;
        nameBuffer = "";
        nameCommit = commit;
        status = prompt;
        statusAt = System.currentTimeMillis();
    }

    private String selectedName() {
        if (selected < 0) {
            return null;
        }
        List<String> list = selectingStarter ? starters : saved;
        return selected < list.size() ? list.get(selected) : null;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (pending != null) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                Runnable action = pending.action();
                pending = null;
                action.run();
            } else if (key == GLFW.GLFW_KEY_ESCAPE) {
                pending = null;
                setStatus("Cancelled");
            }
            return true;
        }
        if (enteringName) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                Runnable commit = nameCommit;
                enteringName = false;
                nameCommit = null;
                if (commit != null) {
                    commit.run();
                }
            } else if (key == GLFW.GLFW_KEY_ESCAPE) {
                enteringName = false;
                nameCommit = null;
                setStatus("Cancelled");
            } else if (key == GLFW.GLFW_KEY_BACKSPACE && !nameBuffer.isEmpty()) {
                nameBuffer = nameBuffer.substring(0, nameBuffer.length() - 1);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (enteringName) {
            String typed = input.asString();
            if (!typed.isEmpty() && typed.charAt(0) >= ' ' && nameBuffer.length() < 40) {
                nameBuffer += typed;
            }
            return true;
        }
        return super.charTyped(input);
    }
}
