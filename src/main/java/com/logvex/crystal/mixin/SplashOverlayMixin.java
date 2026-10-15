package com.logvex.crystal.mixin;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.render.RenderUtil;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a cute pastel touch to the resource-reload splash: a soft pink wash and a small
 * paw-print message. The whole vanilla splash (logo, progress bar, fade) is left intact;
 * this only paints on top while the reload is still in progress.
 *
 * <p>Gated behind the theme's "splash text" option, so the loading screen is untouched
 * when the user turns the cute extras off.
 */
@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    private long reloadCompleteTime;

    @Inject(method = "render", at = @At("TAIL"))
    private void crystal$cuteLoading(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo info) {
        // Only decorate the loading phase; once the reload is done the vanilla fade runs.
        if (reloadCompleteTime != 0L) {
            return;
        }
        CrystalClient bootstrap = CrystalClient.get();
        if (bootstrap == null || bootstrap.theme() == null || !bootstrap.theme().settings().splashText) {
            return;
        }
        Palette palette = bootstrap.theme().palette();
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        // A soft pastel wash along the bottom edge so it reads as part of the theme.
        int bandTop = height - 28;
        RenderUtil.verticalGradient(context, 0, bandTop, width, 28,
                ColorUtil.withAlpha(palette.primary(), 0),
                ColorUtil.withAlpha(palette.primary(), 60));
        String message = "\u2661 loading crystals \u2661";
        int textWidth = client.textRenderer.getWidth(message);
        int textX = (width - textWidth) / 2;
        RenderUtil.paw(context, textX - 12, bandTop + 9, 1, palette.tertiary());
        context.drawTextWithShadow(client.textRenderer, message, textX, bandTop + 10, palette.primary());
    }
}
