package com.logvex.crystal.gui;

import com.logvex.crystal.ai.MiniAIManager;
import com.logvex.crystal.ai.baritone.BaritoneAdvice;
import com.logvex.crystal.ai.crash.CrashRecord;
import com.logvex.crystal.ai.crash.Diagnosis;
import com.logvex.crystal.ai.model.ModelCatalog;
import com.logvex.crystal.ai.model.ModelSpec;
import com.logvex.crystal.ai.report.BugReport;
import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.render.RenderUtil;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.theme.ThemeManager;
import com.logvex.crystal.util.ColorUtil;
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
 * The mini-AI assistant screen: crash intelligence, Baritone diagnostics, the local model
 * controls and the report queue, all in one themed window.
 *
 * <p>It is a thin view over the shared Phase 7 services. Nothing here runs inference, downloads
 * a model or sends a report on the game thread: buttons delegate to the services, which do that
 * work on their own worker threads. The screen re-reads the live state every frame, so a
 * completed analysis or a sent report appears without any refresh action.
 */
public final class AIAssistantScreen extends Screen {

    private enum Page {
        OVERVIEW, CRASHES, BARITONE, MODEL, REPORTS, SETTINGS
    }

    private static final int TAB_H = 18;
    private static final int ROW_H = 16;
    private static final int SETTING_ROW_H = 14;

    /** A boolean preference row on the Settings page. */
    private record ToggleRow(String label, java.util.function.BooleanSupplier get,
                             java.util.function.Consumer<Boolean> set) {
    }

    private final ThemeManager theme;
    private final CrystalClient client;

    private Page page = Page.OVERVIEW;
    private int selectedCrash = -1;
    private int selectedReport = -1;
    private int scroll;
    private String status = "";
    private long statusAt;
    private int settingsTop;

    // Hit boxes captured during render.
    private final int[] tabX = new int[Page.values().length];
    private int tabY;
    private final int[] tabW = new int[Page.values().length];
    private int contentTop;
    private int contentLeft;
    private int contentRight;
    private int contentBottom;
    private int actionY;
    private int actionX;
    private int actionW;
    private static final int BTN_H = 16;
    private int listTop;
    private int listBottom;

    public AIAssistantScreen(ThemeManager theme, CrystalClient client) {
        super(Text.literal("AI Assistant"));
        this.theme = theme;
        this.client = client;
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

        int left = 30;
        int top = 20;
        int right = width - 30;
        int bottom = height - 20;
        RenderUtil.softShadow(context, left, top, right - left, bottom - top, palette.radius(10), 110);
        RenderUtil.roundedGradientPanel(context, left, top, right - left, bottom - top, palette.radius(10),
                palette.panel(), palette.panelAlt(), palette.border());

        context.drawTextWithShadow(textRenderer, "\u2661 AI Assistant", left + 14, top + 10, palette.primary());
        context.drawTextWithShadow(textRenderer,
                "crash intelligence \u00B7 baritone help \u00B7 local diagnostics",
                left + 14, top + 22, palette.textDim());

        renderTabs(context, palette, left + 14, top + 38, right - 14);

        contentTop = top + 38 + TAB_H + 8;
        contentLeft = left + 14;
        contentRight = right - 14;
        contentBottom = bottom - 26;
        actionY = bottom - 22;
        actionX = contentLeft;
        actionW = 96;

        switch (page) {
            case OVERVIEW -> renderOverview(context, palette, mouseX, mouseY);
            case CRASHES -> renderCrashes(context, palette, mouseX, mouseY);
            case BARITONE -> renderBaritone(context, palette, mouseX, mouseY);
            case MODEL -> renderModel(context, palette, mouseX, mouseY);
            case REPORTS -> renderReports(context, palette, mouseX, mouseY);
            case SETTINGS -> renderSettings(context, palette, mouseX, mouseY);
        }

        if (!status.isEmpty() && System.currentTimeMillis() - statusAt < 5000) {
            context.drawTextWithShadow(textRenderer, status, contentRight - textRenderer.getWidth(status),
                    actionY + 4, palette.primary());
        } else {
            context.drawTextWithShadow(textRenderer, "Esc closes", contentRight - textRenderer.getWidth("Esc closes"),
                    actionY + 4, palette.textDim());
        }
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private void renderTabs(DrawContext context, Palette palette, int x, int y, int right) {
        tabY = y;
        Page[] pages = Page.values();
        int cx = x;
        for (int i = 0; i < pages.length; i++) {
            String label = switch (pages[i]) {
                case OVERVIEW -> "\u2661 Overview";
                case CRASHES -> "\u2620 Crashes";
                case BARITONE -> "\u2726 Baritone";
                case MODEL -> "\u2727 Model";
                case REPORTS -> "\u2709 Reports";
                case SETTINGS -> "\u2699 Settings";
            };
            int w = textRenderer.getWidth(label) + 16;
            tabX[i] = cx;
            tabW[i] = w;
            boolean active = page == pages[i];
            RenderUtil.roundedPanel(context, cx, y, w, TAB_H, palette.radius(4),
                    active ? palette.panelHover() : palette.card(),
                    active ? palette.primary() : palette.borderSoft());
            context.drawTextWithShadow(textRenderer, label, cx + 8, y + 5,
                    active ? palette.primary() : palette.text());
            cx += w + 4;
        }
    }

    // ------------------------------------------------------------------ crashes

    private void renderCrashes(DrawContext context, Palette palette, int mouseX, int mouseY) {
        List<CrashRecord> history = client.crashMonitor().history();
        int listWidth = Math.min(240, (contentRight - contentLeft) / 2);
        listTop = contentTop + 12;
        listBottom = contentBottom;
        context.drawTextWithShadow(textRenderer,
                history.isEmpty() ? "no crashes captured this session" : "captured crashes (" + history.size() + ")",
                contentLeft, contentTop, palette.secondary());

        int y = listTop;
        for (int i = history.size() - 1; i >= 0; i--) {
            if (y + ROW_H > listBottom) {
                break;
            }
            CrashRecord record = history.get(i);
            boolean selected = i == selectedCrash;
            RenderUtil.roundedPanel(context, contentLeft, y, listWidth, ROW_H - 2, palette.radius(3),
                    selected ? palette.panelHover() : palette.card(),
                    selected ? palette.primary() : palette.borderSoft());
            String label = record.category().label() + " \u00B7 " + shortTime(record.timestampMillis());
            context.drawTextWithShadow(textRenderer, label, contentLeft + 6, y + 4,
                    selected ? palette.primary() : palette.text());
            String badge = record.severity().label();
            context.drawTextWithShadow(textRenderer, badge,
                    contentLeft + listWidth - 6 - textRenderer.getWidth(badge), y + 4,
                    ColorUtil.withAlpha(record.severity().tint(), 255));
            y += ROW_H;
        }

        // Detail pane.
        int detailX = contentLeft + listWidth + 12;
        int detailTop = contentTop;
        CrashRecord record = selectedCrash >= 0 && selectedCrash < history.size() ? history.get(selectedCrash) : null;
        if (record == null) {
            context.drawTextWithShadow(textRenderer, "select a crash to see its analysis",
                    detailX, detailTop, palette.textDim());
            button(context, palette, actionX, actionY, actionW, "Analyse latest",
                    !history.isEmpty(), mouseX, mouseY);
            button(context, palette, actionX + actionW + 6, actionY, actionW, "Clear history",
                    !history.isEmpty(), mouseX, mouseY);
            return;
        }
        Diagnosis diagnosis = client.crashAnalyzer().diagnose(record);
        int dy = detailTop;
        dy = line(context, palette, detailX, dy, "Cause (" + diagnosis.confidence().label() + ")",
                palette.secondary());
        dy = wrapped(context, palette, detailX, dy, contentRight - detailX, diagnosis.likelyCause(), palette.text());
        dy += 4;
        dy = line(context, palette, detailX, dy, "Fixes", palette.secondary());
        for (String fix : diagnosis.suggestedFixes()) {
            dy = wrapped(context, palette, detailX, dy, contentRight - detailX, "\u2022 " + fix, palette.text());
        }
        dy += 4;
        dy = line(context, palette, detailX, dy, "Fingerprint " + record.fingerprint(), palette.textDim());
        if (diagnosis.aiGenerated()) {
            line(context, palette, detailX, dy, "explanation enhanced by the local model", palette.tertiary());
        }

        int bx = actionX;
        button(context, palette, bx, actionY, actionW, "Queue report", true, mouseX, mouseY);
        bx += actionW + 6;
        button(context, palette, bx, actionY, actionW, "Ask AI", client.aiSettings().localAnalysis, mouseX, mouseY);
        bx += actionW + 6;
        button(context, palette, bx, actionY, actionW, "Delete", true, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ baritone

    private void renderBaritone(DrawContext context, Palette palette, int mouseX, int mouseY) {
        BaritoneAdvice advice = client.baritoneAdvice();
        int y = contentTop;
        y = line(context, palette, contentLeft, y, advice.summary(), palette.primary());
        y += 4;
        y = section(context, palette, contentLeft, y, "Facts", advice.facts());
        y = section(context, palette, contentLeft, y, "Possible issues", advice.likelyIssues());
        y = section(context, palette, contentLeft, y, "Suggestions", advice.suggestions());
        if (advice.aiGenerated()) {
            line(context, palette, contentLeft, y, "suggestions enhanced by the local model", palette.tertiary());
        }
        button(context, palette, actionX, actionY, actionW, "Refresh", true, mouseX, mouseY);
        button(context, palette, actionX + actionW + 6, actionY, actionW, "Open Baritone",
                true, mouseX, mouseY);
        button(context, palette, actionX + 2 * (actionW + 6), actionY, actionW, "Ask AI",
                client.aiSettings().localAnalysis && advice.available(), mouseX, mouseY);
    }

    private int section(DrawContext context, Palette palette, int x, int y, String title, List<String> lines) {
        y = line(context, palette, x, y, title, palette.secondary());
        for (String value : lines) {
            y = wrapped(context, palette, x + 6, y, contentRight - x - 6, "\u2022 " + value, palette.text());
        }
        return y + 4;
    }

    // ------------------------------------------------------------------ model

    private void renderModel(DrawContext context, Palette palette, int mouseX, int mouseY) {
        MiniAIManager ai = client.ai();
        int y = contentTop;
        y = line(context, palette, contentLeft, y, "State: " + ai.state().label(), palette.primary());
        if (!ai.lastError().isBlank()) {
            y = wrapped(context, palette, contentLeft, y, contentRight - contentLeft,
                    "Last error: " + ai.lastError(), palette.danger());
        }
        y = line(context, palette, contentLeft, y, "Queue: " + ai.queueDepth() + "/" + ai.queueLimit()
                + " \u00B7 completed " + ai.completedCount() + " \u00B7 dropped " + ai.rejectedCount(), palette.text());
        y += 4;

        for (ModelSpec spec : ModelCatalog.all()) {
            boolean installed = ai.loader().isInstalled(spec);
            boolean chosen = client.aiSettings().modelId.equals(spec.id());
            RenderUtil.roundedPanel(context, contentLeft, y, contentRight - contentLeft, 30, palette.radius(4),
                    chosen ? palette.panelHover() : palette.card(),
                    chosen ? palette.primary() : palette.borderSoft());
            context.drawTextWithShadow(textRenderer,
                    (chosen ? "\u2661 " : "\u2727 ") + spec.displayName()
                            + "  (" + Math.round(spec.sizeMegabytes()) + " MB, " + spec.license() + ")",
                    contentLeft + 6, y + 4, palette.text());
            context.drawTextWithShadow(textRenderer, spec.description(), contentLeft + 6, y + 16, palette.textDim());
            String state = installed ? "installed" : "not installed";
            context.drawTextWithShadow(textRenderer, state,
                    contentRight - 6 - textRenderer.getWidth(state), y + 4,
                    installed ? palette.enabled() : palette.textDim());
            y += 34;
        }

        y += 4;
        String hashNote = client.ai().currentSpec().hasPinnedHash()
                ? "download is checksum-verified"
                : "download is size- and format-checked (hash not pinned)";
        line(context, palette, contentLeft, y, hashNote, palette.textDim());

        int bx = actionX;
        button(context, palette, bx, actionY, actionW, "Use selected", true, mouseX, mouseY);
        bx += actionW + 6;
        ModelSpec current = client.ai().currentSpec();
        boolean installed = ai.loader().isInstalled(current);
        button(context, palette, bx, actionY, actionW, installed ? "Load" : "Download", true, mouseX, mouseY);
        bx += actionW + 6;
        button(context, palette, bx, actionY, actionW, "Unload", ai.isLoaded(), mouseX, mouseY);
        bx += actionW + 6;
        button(context, palette, bx, actionY, actionW, "Delete", installed, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ reports

    private void renderReports(DrawContext context, Palette palette, int mouseX, int mouseY) {
        List<BugReport> reports = client.reportService().reports();
        int listWidth = Math.min(220, (contentRight - contentLeft) / 2);
        listTop = contentTop + 12;
        listBottom = contentBottom;
        context.drawTextWithShadow(textRenderer,
                reports.isEmpty() ? "no reports queued" : "reports (" + reports.size() + ")",
                contentLeft, contentTop, palette.secondary());

        int y = listTop;
        for (int i = reports.size() - 1; i >= 0; i--) {
            if (y + ROW_H > listBottom) {
                break;
            }
            BugReport report = reports.get(i);
            boolean selected = i == selectedReport;
            RenderUtil.roundedPanel(context, contentLeft, y, listWidth, ROW_H - 2, palette.radius(3),
                    selected ? palette.panelHover() : palette.card(),
                    selected ? palette.primary() : palette.borderSoft());
            context.drawTextWithShadow(textRenderer, report.status().label() + " \u00B7 " + report.fingerprint(),
                    contentLeft + 6, y + 4, selected ? palette.primary() : palette.text());
            y += ROW_H;
        }

        int detailX = contentLeft + listWidth + 12;
        BugReport report = selectedReport >= 0 && selectedReport < reports.size() ? reports.get(selectedReport) : null;
        if (report == null) {
            context.drawTextWithShadow(textRenderer, "select a report to preview it",
                    detailX, contentTop, palette.textDim());
            button(context, palette, actionX, actionY, actionW, "Clear reports",
                    !reports.isEmpty(), mouseX, mouseY);
            return;
        }
        int dy = contentTop;
        dy = line(context, palette, detailX, dy, report.subject(), palette.primary());
        dy = wrapped(context, palette, detailX, dy, contentRight - detailX,
                "size " + report.sizeBytes() + " bytes \u00B7 attempts " + report.attempts(), palette.textDim());
        if (!report.lastError().isBlank()) {
            dy = wrapped(context, palette, detailX, dy, contentRight - detailX,
                    "last error: " + report.lastError(), palette.danger());
        }
        dy += 4;
        for (String previewLine : client.reportService().preview(report, 600).split("\n")) {
            if (dy + 10 > contentBottom) {
                break;
            }
            context.drawTextWithShadow(textRenderer, previewLine, detailX, dy, palette.text());
            dy += 10;
        }

        int bx = actionX;
        boolean sendable = client.aiSettings().emailEnabled && report.status() != BugReport.Status.SENT;
        button(context, palette, bx, actionY, actionW, "Send", sendable, mouseX, mouseY);
        bx += actionW + 6;
        button(context, palette, bx, actionY, actionW, "Copy mailto", true, mouseX, mouseY);
        bx += actionW + 6;
        button(context, palette, bx, actionY, actionW, "Delete", true, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ settings

    /** The boolean preferences shown on the Settings page, each writing straight to AISettings. */
    private List<ToggleRow> settingRows() {
        var s = client.aiSettings();
        return List.of(
                new ToggleRow("Enable AI assistant", () -> s.enabled, v -> s.enabled = v),
                new ToggleRow("Crash detection", () -> s.crashDetection, v -> s.crashDetection = v),
                new ToggleRow("Local AI crash analysis", () -> s.localAnalysis, v -> s.localAnalysis = v),
                new ToggleRow("Keep bug-report queue", () -> s.reportQueue, v -> s.reportQueue = v),
                new ToggleRow("Allow sending reports by email", () -> s.emailEnabled, v -> s.emailEnabled = v),
                new ToggleRow("Ask before sending each report", () -> s.askBeforeSending, v -> s.askBeforeSending = v),
                new ToggleRow("Include environment details", () -> s.includeEnvironment, v -> s.includeEnvironment = v),
                new ToggleRow("Include relevant logs", () -> s.includeLogs, v -> s.includeLogs = v),
                new ToggleRow("Keep local crash history", () -> s.keepLocalHistory, v -> s.keepLocalHistory = v));
    }

    private void renderSettings(DrawContext context, Palette palette, int mouseX, int mouseY) {
        List<ToggleRow> rows = settingRows();
        int columns = 2;
        int perColumn = (rows.size() + columns - 1) / columns;
        int colW = (contentRight - contentLeft) / columns - 8;
        settingsTop = contentTop;
        for (int i = 0; i < rows.size(); i++) {
            ToggleRow row = rows.get(i);
            int col = i / perColumn;
            int line = i % perColumn;
            int x = contentLeft + col * (colW + 16);
            int y = settingsTop + line * (SETTING_ROW_H + 2);
            boolean on = row.get().getAsBoolean();
            boolean hovered = mouseX >= x && mouseX <= x + colW && mouseY >= y && mouseY <= y + SETTING_ROW_H;
            context.drawTextWithShadow(textRenderer, row.label(), x, y + 3,
                    hovered ? palette.primary() : palette.text());
            String state = on ? "on" : "off";
            int pillW = 30;
            int pillX = x + colW - pillW;
            RenderUtil.roundedPanel(context, pillX, y, pillW, SETTING_ROW_H - 2, palette.radius(3),
                    on ? palette.enabled() : palette.card(),
                    on ? palette.primary() : palette.borderSoft());
            context.drawTextWithShadow(textRenderer, state,
                    pillX + (pillW - textRenderer.getWidth(state)) / 2, y + 3,
                    on ? palette.panel() : palette.textDim());
        }

        int y = settingsTop + perColumn * (SETTING_ROW_H + 2) + 6;
        y = line(context, palette, contentLeft, y, "Report size cap: " + client.aiSettings().maxReportKilobytes
                + " KB \u00B7 history " + client.aiSettings().maxHistory + " \u00B7 idle unload "
                + client.aiSettings().idleUnloadMinutes + " min", palette.textDim());
        y = line(context, palette, contentLeft, y, "Inference threads " + client.aiSettings().inferenceThreads
                + " \u00B7 queue " + client.aiSettings().pendingQueueLimit
                + " \u00B7 timeout " + client.aiSettings().inferenceTimeoutSeconds + "s", palette.textDim());
        wrapped(context, palette, contentLeft, y, contentRight - contentLeft,
                "SMTP is user-supplied and never bundled; with none configured, reports export as a mailto link. "
                        + "Crash detection stays on even when the AI model is disabled.",
                palette.textDim());

        int bx = actionX;
        button(context, palette, bx, actionY, actionW, "Delete reports", true, mouseX, mouseY);
        bx += actionW + 6;
        button(context, palette, bx, actionY, actionW, "Clear crashes", true, mouseX, mouseY);
        bx += actionW + 6;
        button(context, palette, bx, actionY, actionW, "Save settings", true, mouseX, mouseY);
    }

    private void settingsClick(int x, int y) {
        List<ToggleRow> rows = settingRows();
        int columns = 2;
        int perColumn = (rows.size() + columns - 1) / columns;
        int colW = (contentRight - contentLeft) / columns - 8;
        int rowBottom = settingsTop + perColumn * (SETTING_ROW_H + 2);
        if (y >= settingsTop && y < rowBottom) {
            int col = x < contentLeft + colW + 8 ? 0 : 1;
            int line = (y - settingsTop) / (SETTING_ROW_H + 2);
            int index = col * perColumn + line;
            if (index >= 0 && index < rows.size()) {
                ToggleRow row = rows.get(index);
                row.set().accept(!row.get().getAsBoolean());
                client.applyAiSettings();
                setStatus(row.label() + " updated");
                return;
            }
        }
        if (inButton(x, y, actionX, actionY, actionW)) {
            client.reportService().clear();
            setStatus("Cleared the report queue");
            return;
        }
        if (inButton(x, y, actionX + actionW + 6, actionY, actionW)) {
            client.crashMonitor().clear();
            setStatus("Cleared the crash history");
            return;
        }
        if (inButton(x, y, actionX + 2 * (actionW + 6), actionY, actionW)) {
            client.applyAiSettings();
            setStatus("Settings saved");
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String formatBytes(long bytes) {
        if (bytes < 0) {
            return "-";
        }
        if (bytes < 1024L * 1024L) {
            return (bytes / 1024L) + " KB";
        }
        return String.format("%.0f MB", bytes / (1024.0 * 1024.0));
    }

    private int line(DrawContext context, Palette palette, int x, int y, String text, int color) {
        context.drawTextWithShadow(textRenderer, text, x, y, color);
        return y + 11;
    }

    /** Draws wrapped text, returning the y after the last line. */
    private int wrapped(DrawContext context, Palette palette, int x, int y, int maxWidth, String text, int color) {
        if (text == null || text.isEmpty()) {
            return y;
        }
        for (String paragraph : text.split("\n")) {
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (textRenderer.getWidth(candidate) > maxWidth && !current.isEmpty()) {
                    context.drawTextWithShadow(textRenderer, current.toString(), x, y, color);
                    y += 10;
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(candidate);
                }
            }
            if (!current.isEmpty()) {
                context.drawTextWithShadow(textRenderer, current.toString(), x, y, color);
                y += 10;
            }
        }
        return y;
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

    private static boolean inButton(int mx, int my, int x, int y, int w) {
        return mx >= x && mx <= x + w && my >= y && my <= y + BTN_H;
    }

    private static String shortTime(long millis) {
        return java.time.Instant.ofEpochMilli(millis)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalTime().withNano(0).toString();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int x = (int) click.x();
        int y = (int) click.y();

        Page[] pages = Page.values();
        for (int i = 0; i < pages.length; i++) {
            if (inButton(x, y, tabX[i], tabY, tabW[i])) {
                page = pages[i];
                scroll = 0;
                return true;
            }
        }

        switch (page) {
            case OVERVIEW -> overviewClick(x, y);
            case CRASHES -> crashClick(x, y);
            case BARITONE -> baritoneClick(x, y);
            case MODEL -> modelClick(x, y);
            case REPORTS -> reportClick(x, y);
            case SETTINGS -> settingsClick(x, y);
        }
        return super.mouseClicked(click, doubled);
    }

    // ------------------------------------------------------------------ overview

    private void renderOverview(DrawContext context, Palette palette, int mouseX, int mouseY) {
        MiniAIManager ai = client.ai();
        List<CrashRecord> crashes = client.crashMonitor().history();
        int reports = client.reportService().reports().size();
        int y = contentTop;

        y = line(context, palette, contentLeft, y, "Assistant: "
                + (client.aiSettings().enabled ? "enabled" : "disabled")
                + " \u00B7 local analysis " + (client.aiSettings().localAnalysis ? "on" : "off"),
                palette.primary());
        y = line(context, palette, contentLeft, y, "Model: " + ai.currentSpec().displayName()
                + " (" + Math.round(ai.currentSpec().sizeMegabytes()) + " MB)", palette.text());
        y = line(context, palette, contentLeft, y, "State: " + ai.state().label()
                + (ai.isLoaded() ? " \u00B7 " + formatBytes(ai.loadedBytes()) + " in memory" : ""), palette.text());
        y = line(context, palette, contentLeft, y, "Inference: " + ai.activeCount() + " active \u00B7 "
                + ai.queueDepth() + "/" + ai.queueLimit() + " queued \u00B7 " + ai.completedCount()
                + " done \u00B7 " + ai.rejectedCount() + " dropped", palette.text());
        if (ai.lastLatencyMillis() >= 0) {
            y = line(context, palette, contentLeft, y,
                    "Last inference: " + ai.lastLatencyMillis() + " ms", palette.textDim());
        }
        if (!ai.lastError().isBlank()) {
            y = wrapped(context, palette, contentLeft, y, contentRight - contentLeft,
                    "Last error: " + ai.lastError(), palette.danger());
        }
        y += 4;
        y = line(context, palette, contentLeft, y, "Crashes captured: " + crashes.size()
                + " \u00B7 reports queued: " + reports, palette.secondary());

        if (!crashes.isEmpty()) {
            CrashRecord latest = crashes.get(crashes.size() - 1);
            Diagnosis diagnosis = client.crashAnalyzer().diagnose(latest);
            y += 4;
            y = line(context, palette, contentLeft, y, "Latest: " + latest.category().label()
                    + " \u00B7 " + latest.severity().label() + " \u00B7 " + shortTime(latest.timestampMillis()),
                    palette.text());
            y = wrapped(context, palette, contentLeft, y, contentRight - contentLeft,
                    "Diagnosis (" + diagnosis.confidence().label() + "): " + diagnosis.likelyCause(),
                    palette.textDim());
        }

        int bx = actionX;
        button(context, palette, bx, actionY, actionW, "Open crashes", true, mouseX, mouseY);
        bx += actionW + 6;
        button(context, palette, bx, actionY, actionW, "Analyse latest", !crashes.isEmpty(), mouseX, mouseY);
        bx += actionW + 6;
        button(context, palette, bx, actionY, actionW,
                client.aiSettings().enabled ? "Disable AI" : "Enable AI", true, mouseX, mouseY);
    }

    private void overviewClick(int x, int y) {
        if (inButton(x, y, actionX, actionY, actionW)) {
            page = Page.CRASHES;
            return;
        }
        if (inButton(x, y, actionX + actionW + 6, actionY, actionW)) {
            List<CrashRecord> crashes = client.crashMonitor().history();
            if (crashes.isEmpty()) {
                setStatus("No crash captured yet");
                return;
            }
            CrashRecord latest = crashes.get(crashes.size() - 1);
            Diagnosis diagnosis = client.crashAnalyzer().diagnose(latest);
            if (client.aiSettings().localAnalysis) {
                client.askAiAboutCrash(latest, diagnosis);
                setStatus("Asking the local model\u2026");
            } else {
                setStatus("Cause (" + diagnosis.confidence().label() + "): " + diagnosis.likelyCause());
            }
            return;
        }
        if (inButton(x, y, actionX + 2 * (actionW + 6), actionY, actionW)) {
            client.aiSettings().enabled = !client.aiSettings().enabled;
            client.applyAiSettings();
            setStatus(client.aiSettings().enabled ? "AI enabled" : "AI disabled");
        }
    }

    private void crashClick(int x, int y) {
        List<CrashRecord> history = client.crashMonitor().history();
        if (y >= listTop && y <= listBottom && x >= contentLeft) {
            int row = (y - listTop) / ROW_H;
            int index = history.size() - 1 - row;
            if (index >= 0 && index < history.size()) {
                selectedCrash = index;
                return;
            }
        }
        boolean hasSelection = selectedCrash >= 0 && selectedCrash < history.size();
        if (!hasSelection) {
            if (inButton(x, y, actionX, actionY, actionW)) {
                if (history.isEmpty()) {
                    setStatus("No crash captured yet");
                    return;
                }
                selectedCrash = history.size() - 1;
                setStatus("Analysed the latest crash");
                return;
            }
            if (inButton(x, y, actionX + actionW + 6, actionY, actionW)) {
                client.crashMonitor().clear();
                selectedCrash = -1;
                setStatus("Cleared the crash history");
            }
            return;
        }
        CrashRecord record = history.get(selectedCrash);
        Diagnosis diagnosis = client.crashAnalyzer().diagnose(record);
        if (inButton(x, y, actionX, actionY, actionW)) {
            if (client.reportService().queueReport(record, diagnosis) != null) {
                setStatus("Queued a report for review");
            } else {
                setStatus("A report for this crash is already queued");
            }
            return;
        }
        if (inButton(x, y, actionX + actionW + 6, actionY, actionW)) {
            if (client.aiSettings().localAnalysis) {
                client.askAiAboutCrash(record, diagnosis);
                setStatus("Asking the local model\u2026");
            } else {
                setStatus("Enable LocalAnalysis to ask the model");
            }
            return;
        }
        if (inButton(x, y, actionX + 2 * (actionW + 6), actionY, actionW)) {
            client.crashMonitor().remove(record.id());
            selectedCrash = -1;
            setStatus("Deleted the crash record");
        }
    }

    private void baritoneClick(int x, int y) {
        if (inButton(x, y, actionX, actionY, actionW)) {
            setStatus("Baritone diagnostics refreshed");
            return;
        }
        if (inButton(x, y, actionX + actionW + 6, actionY, actionW)) {
            if (ScreenGuard.fontsReady()) {
                MinecraftClient.getInstance().setScreen(new BaritoneScreen(client.theme(), client.baritone()));
            }
            return;
        }
        if (inButton(x, y, actionX + 2 * (actionW + 6), actionY, actionW)) {
            client.askAiAboutBaritone();
            setStatus("Asking the local model\u2026");
        }
    }

    private void modelClick(int x, int y) {
        // Selecting a model card.
        int cardTop = contentTop + 11 + (client.ai().lastError().isBlank() ? 0 : 22) + 11 + 4;
        int index = -1;
        for (int i = 0; i < ModelCatalog.all().size(); i++) {
            int cardY = cardTop + i * 34;
            if (y >= cardY && y <= cardY + 30 && x >= contentLeft && x <= contentRight) {
                index = i;
                break;
            }
        }
        if (index >= 0) {
            client.aiSettings().modelId = ModelCatalog.all().get(index).id();
            client.applyAiSettings();
            setStatus("Selected " + ModelCatalog.all().get(index).displayName());
            return;
        }
        ModelSpec current = client.ai().currentSpec();
        if (inButton(x, y, actionX, actionY, actionW)) {
            client.aiSettings().modelId = current.id();
            client.applyAiSettings();
            setStatus("Using " + current.displayName());
            return;
        }
        if (inButton(x, y, actionX + actionW + 6, actionY, actionW)) {
            if (client.ai().loader().isInstalled(current)) {
                client.ai().ensureLoaded();
                setStatus("Loading the model\u2026");
            } else {
                client.downloadModel(current);
                setStatus("Downloading " + current.displayName() + "\u2026");
            }
            return;
        }
        if (inButton(x, y, actionX + 2 * (actionW + 6), actionY, actionW)) {
            client.ai().unload();
            setStatus("Model unloaded");
            return;
        }
        if (inButton(x, y, actionX + 3 * (actionW + 6), actionY, actionW)) {
            client.ai().loader().remove(current);
            client.ai().unload();
            setStatus("Removed the model file");
        }
    }

    private void reportClick(int x, int y) {
        List<BugReport> reports = client.reportService().reports();
        if (y >= listTop && y <= listBottom && x >= contentLeft) {
            int row = (y - listTop) / ROW_H;
            int index = reports.size() - 1 - row;
            if (index >= 0 && index < reports.size()) {
                selectedReport = index;
                return;
            }
        }
        BugReport report = selectedReport >= 0 && selectedReport < reports.size() ? reports.get(selectedReport) : null;
        if (!reports.isEmpty() && inButton(x, y, actionX, actionY, actionW) && report == null) {
            client.reportService().clear();
            setStatus("Cleared the report queue");
            return;
        }
        if (report == null) {
            return;
        }
        if (inButton(x, y, actionX, actionY, actionW)) {
            client.reportService().approveAndSend(report.id());
            setStatus("Sending the report\u2026");
            return;
        }
        if (inButton(x, y, actionX + actionW + 6, actionY, actionW)) {
            MinecraftClient.getInstance().keyboard.setClipboard(
                    client.reportService().mailtoLink(report));
            setStatus("Copied a mailto link to the clipboard");
            return;
        }
        if (inButton(x, y, actionX + 2 * (actionW + 6), actionY, actionW)) {
            client.reportService().delete(report.id());
            selectedReport = -1;
            setStatus("Deleted the report");
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        return super.charTyped(input);
    }
}
