package com.logvex.crystal.module.misc;

import com.logvex.crystal.ai.AISettings;
import com.logvex.crystal.ai.model.ModelCatalog;
import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.gui.AIAssistantScreen;
import com.logvex.crystal.gui.ScreenGuard;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.EnumSetting;
import com.logvex.crystal.setting.IntSetting;
import com.logvex.crystal.setting.StringSetting;
import net.minecraft.client.MinecraftClient;

/**
 * The mini-AI assistant: crash intelligence, Baritone diagnostics and local diagnostics
 * analysis, all optional. Enabling the module shows the HUD readout; the settings mirror the
 * Phase 7 preferences so the whole subsystem is reachable from the module list.
 *
 * <p>Every setting drives the shared {@link AISettings}, and a change reconfigures the manager
 * immediately - no restart. The module never loads a model by itself: inference only starts
 * when the user asks for an analysis.
 */
public final class AIAssistantModule extends Module {

    /** A stable identifier for the model choice, mapped to the catalogue. */
    public enum ModelChoice {
        SMOLLM2_360M("SmolLM2 360M"),
        QWEN25_05B("Qwen2.5 0.5B");

        private final String label;

        ModelChoice(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public String id() {
            return this == QWEN25_05B ? ModelCatalog.QWEN25_05B.id() : ModelCatalog.SMOLLM2_360M.id();
        }
    }

    private final BooleanSetting enabled = add("Enabled", "Master switch for the assistant.", true);
    private final BooleanSetting crashDetection = add("CrashDetection", "Capture and classify crashes.", true);
    private final BooleanSetting localAnalysis = add("LocalAnalysis", "Use a local model for extra explanation.", false);
    private final BooleanSetting reportQueue = add("ReportQueue", "Queue a report when a crash is captured.", true);
    private final BooleanSetting email = add("EmailReports", "Allow sending reports by email.", false);
    private final BooleanSetting askFirst = add("AskBeforeSending", "Approve every report before it is sent.", true);
    private final BooleanSetting includeEnvironment = add("IncludeEnvironment", "Include versions and OS in reports.", true);
    private final BooleanSetting includeLogs = add("IncludeLogs", "Include recent events in reports.", true);
    private final BooleanSetting keepHistory = add("KeepHistory", "Keep crash history between launches.", true);
    private final EnumSetting<ModelChoice> model = add("Model", "Local model to use.", ModelChoice.SMOLLM2_360M);
    private final IntSetting idleMinutes = add("IdleUnloadMinutes", "Unload the model after this idle time.", 10, 1, 240);
    private final IntSetting threads = add("InferenceThreads", "Worker threads for local inference.", 1, 1, 2);
    private final IntSetting queueLimit = add("QueueLimit", "Maximum queued inference requests.", 2, 1, 3);
    private final IntSetting maxReportKb = add("MaxReportKB", "Maximum report size in kilobytes.", 64, 8, 512);
    private final StringSetting redactions = add("ExtraRedactions", "Names to redact, one per line.", "");
    private final StringSetting smtpHost = add("SmtpHost", "SMTP server for sending reports (optional).", "");
    private final IntSetting smtpPort = add("SmtpPort", "SMTP port.", 587, 1, 65535);
    private final StringSetting smtpUser = add("SmtpUser", "SMTP username.", "");
    private final StringSetting smtpPassword = add("SmtpPassword", "SMTP password.", "");
    private final StringSetting smtpFrom = add("SmtpFrom", "From address used in sent reports.", "");
    private final BooleanSetting smtpStartTls = add("SmtpStartTls", "Use STARTTLS.", true);

    public AIAssistantModule() {
        super(Category.MISC, "AIAssistant",
                "Optional local AI: crash intelligence, Baritone help and diagnostics.");
        enabled.onChange(v -> { get().enabled = v; reconfigure(); });
        crashDetection.onChange(v -> { get().crashDetection = v; reconfigure(); });
        localAnalysis.onChange(v -> { get().localAnalysis = v; reconfigure(); });
        reportQueue.onChange(v -> { get().reportQueue = v; reconfigure(); });
        email.onChange(v -> { get().emailEnabled = v; reconfigure(); });
        askFirst.onChange(v -> { get().askBeforeSending = v; reconfigure(); });
        includeEnvironment.onChange(v -> { get().includeEnvironment = v; reconfigure(); });
        includeLogs.onChange(v -> { get().includeLogs = v; reconfigure(); });
        keepHistory.onChange(v -> { get().keepLocalHistory = v; reconfigure(); });
        idleMinutes.onChange(v -> { get().idleUnloadMinutes = v; reconfigure(); });
        threads.onChange(v -> { get().inferenceThreads = v; reconfigure(); });
        queueLimit.onChange(v -> { get().pendingQueueLimit = v; reconfigure(); });
        maxReportKb.onChange(v -> { get().maxReportKilobytes = v; reconfigure(); });
        redactions.onChange(v -> { get().extraRedactions = v; reconfigure(); });
        smtpHost.onChange(v -> { get().smtpHost = v; reconfigure(); });
        smtpPort.onChange(v -> { get().smtpPort = v; reconfigure(); });
        smtpUser.onChange(v -> { get().smtpUser = v; reconfigure(); });
        smtpPassword.onChange(v -> { get().smtpPassword = v; reconfigure(); });
        smtpFrom.onChange(v -> { get().smtpFrom = v; reconfigure(); });
        smtpStartTls.onChange(v -> { get().smtpStartTls = v; reconfigure(); });
        model.onChange(v -> { get().modelId = v.id(); reconfigure(); });
    }

    private AISettings get() {
        CrystalClient client = CrystalClient.get();
        return client == null ? new AISettings() : client.aiSettings();
    }

    private void reconfigure() {
        CrystalClient client = CrystalClient.get();
        if (client != null) {
            client.applyAiSettings();
        }
    }

    @Override
    protected void onEnable() {
        if (context() != null) {
            context().hudManager().setElementVisible(com.logvex.crystal.gui.hud.AIHudElement.ID, true);
        }
        syncFromSettings();
    }

    @Override
    protected void onDisable() {
        if (context() != null) {
            context().hudManager().setElementVisible(com.logvex.crystal.gui.hud.AIHudElement.ID, false);
        }
    }

    /** Copies the shared settings into the module's controls (used after a config load). */
    public void syncFromSettings() {
        CrystalClient client = CrystalClient.get();
        if (client == null) {
            return;
        }
        AISettings s = client.aiSettings();
        enabled.setSilently(s.enabled);
        crashDetection.setSilently(s.crashDetection);
        localAnalysis.setSilently(s.localAnalysis);
        reportQueue.setSilently(s.reportQueue);
        email.setSilently(s.emailEnabled);
        askFirst.setSilently(s.askBeforeSending);
        includeEnvironment.setSilently(s.includeEnvironment);
        includeLogs.setSilently(s.includeLogs);
        keepHistory.setSilently(s.keepLocalHistory);
        idleMinutes.setSilently(s.idleUnloadMinutes);
        threads.setSilently(s.inferenceThreads);
        queueLimit.setSilently(s.pendingQueueLimit);
        maxReportKb.setSilently(s.maxReportKilobytes);
        redactions.setSilently(s.extraRedactions);
        smtpHost.setSilently(s.smtpHost);
        smtpPort.setSilently(s.smtpPort);
        smtpUser.setSilently(s.smtpUser);
        smtpPassword.setSilently(s.smtpPassword);
        smtpFrom.setSilently(s.smtpFrom);
        smtpStartTls.setSilently(s.smtpStartTls);
        model.setSilently(s.modelId.equals(ModelCatalog.QWEN25_05B.id())
                ? ModelChoice.QWEN25_05B : ModelChoice.SMOLLM2_360M);
    }

    /** Opens the assistant screen. */
    public void open() {
        if (!ScreenGuard.fontsReady()) {
            return;
        }
        CrystalClient client = CrystalClient.get();
        if (client != null) {
            MinecraftClient.getInstance().setScreen(new AIAssistantScreen(client.theme(), client));
        }
    }
}
