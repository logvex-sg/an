package com.logvex.crystal.gui.hud;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.gui.HudElement;
import com.logvex.crystal.notification.NotificationManager.Notification;
import com.logvex.crystal.notification.NotificationManager;
import com.logvex.crystal.render.RenderUtil;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.util.ColorUtil;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * The cute notification toasts. Each notice slides in from the right, shows its severity
 * glyph and text, and fades out. When cat cosmetics are on, a paw print is drawn beside
 * the toast. Kept to a small fixed stack so it never covers the play area.
 */
public final class NotificationHudElement implements HudElement {

    private static final int MAX_SHOWN = 6;

    @Override
    public String id() {
        return "notifications";
    }

    @Override
    public Column column() {
        return Column.RIGHT;
    }

    @Override
    public void render(DrawContext context, MinecraftClient client, float tickDelta, int x, int y, boolean rightAligned) {
        var bootstrap = CrystalClient.get();
        if (bootstrap == null) {
            return;
        }
        NotificationManager notifications = bootstrap.notifications();
        Palette palette = bootstrap.theme().palette();
        List<Notification> active = notifications.active();
        if (active.isEmpty()) {
            return;
        }
        int shown = Math.min(active.size(), MAX_SHOWN);
        int rowHeight = 16;
        int start = active.size() - shown;
        int index = 0;
        for (int i = start; i < active.size(); i++) {
            Notification notification = active.get(i);
            float alpha = NotificationManager.alpha(notification);
            int levelColor = levelColor(notification, palette);
            int width = client.textRenderer.getWidth(notification.text()) + 26;
            int slide = (int) ((1f - Math.min(1f, alpha * 1.6f)) * 20);
            int rowX = x - width + slide;
            int rowY = y + index * (rowHeight + 2);
            int fill = ColorUtil.withAlpha(palette.panel(), (int) (palette.panel() >>> 24 & 0xFF));
            fill = ColorUtil.scaleAlpha(fill, alpha);
            RenderUtil.softShadow(context, rowX, rowY, width, rowHeight, palette.radius(4), 60);
            RenderUtil.roundedPanel(context, rowX, rowY, width, rowHeight, palette.radius(4),
                    fill, ColorUtil.scaleAlpha(palette.borderSoft(), alpha));
            // Accent bar on the left edge in the severity colour.
            RenderUtil.roundedRect(context, rowX + 1, rowY + 2, 2, rowHeight - 4, palette.radius(1),
                    ColorUtil.scaleAlpha(levelColor, alpha));
            String glyph = notification.level().glyph();
            context.drawTextWithShadow(client.textRenderer, glyph, rowX + 6, rowY + 4,
                    ColorUtil.scaleAlpha(levelColor, alpha));
            context.drawTextWithShadow(client.textRenderer, notification.text(), rowX + 17, rowY + 4,
                    ColorUtil.scaleAlpha(palette.text(), alpha));
            if (bootstrap.theme().settings().catCosmetics) {
                RenderUtil.paw(context, rowX + width - 9, rowY + 4, 1,
                        ColorUtil.scaleAlpha(palette.tertiary(), alpha * 0.8f));
            }
            index++;
        }
    }

    private int levelColor(Notification notification, Palette palette) {
        return switch (notification.level()) {
            case SUCCESS -> palette.primary();
            case WARN -> palette.warning();
            case ERROR -> palette.danger();
            case CUTE -> palette.primary();
            case INFO -> palette.secondary();
        };
    }

    @Override
    public int height(MinecraftClient client) {
        var bootstrap = CrystalClient.get();
        if (bootstrap == null) {
            return 0;
        }
        int shown = Math.min(bootstrap.notifications().size(), MAX_SHOWN);
        return shown * 18;
    }

    @Override
    public int width(MinecraftClient client) {
        return 140;
    }
}
