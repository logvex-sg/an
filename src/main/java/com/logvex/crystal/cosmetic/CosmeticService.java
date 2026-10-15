package com.logvex.crystal.cosmetic;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.util.ColorUtil;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * The purely cosmetic layer: paw-print particles spawned where the cursor interacts with
 * the ClickGUI, and a soft animated background shimmer.
 *
 * <p>Particles are capped, purely visual and never affect gameplay. They are spawned only
 * from GUI interactions and disabled by the reduced-motion and decorations settings, so
 * they cannot cost frames during combat.
 */
public final class CosmeticService {

    /** A single screen-space paw print that drifts up and fades. */
    private record Particle(double x, double y, long bornAtMillis, double drift, int color) {
    }

    private static final int MAX_PARTICLES = 24;
    private static final long LIFETIME_MILLIS = 900L;

    private final Deque<Particle> particles = new ArrayDeque<>();
    private final Random random = new Random();

    /** Spawns a small burst of paw prints at a screen position. */
    public void spawnPawBurst(double x, double y, int color) {
        if (!enabled()) {
            return;
        }
        int count = 3;
        synchronized (particles) {
            for (int i = 0; i < count; i++) {
                double dx = (random.nextDouble() - 0.5) * 10;
                double dy = (random.nextDouble() - 0.5) * 6;
                particles.addLast(new Particle(x + dx, y + dy, System.currentTimeMillis(),
                        0.6 + random.nextDouble() * 0.8, color));
                while (particles.size() > MAX_PARTICLES) {
                    particles.removeFirst();
                }
            }
        }
    }

    /** Drops expired particles. Called once per client tick. */
    public void tick(MinecraftClient client) {
        long cutoff = System.currentTimeMillis() - LIFETIME_MILLIS;
        synchronized (particles) {
            while (!particles.isEmpty() && particles.peekFirst().bornAtMillis() < cutoff) {
                particles.removeFirst();
            }
        }
    }

    /** Draws live particles. Called from the HUD render callback. */
    public void renderParticles(DrawContext context, MinecraftClient client) {
        if (!enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (particles) {
            for (Particle particle : particles) {
                float progress = (now - particle.bornAtMillis()) / (float) LIFETIME_MILLIS;
                if (progress >= 1f) {
                    continue;
                }
                float alpha = (1f - progress);
                int size = Math.max(1, Math.round(1 + progress));
                int color = ColorUtil.scaleAlpha(particle.color(), alpha * 0.7f);
                com.logvex.crystal.render.RenderUtil.paw(context,
                        (int) particle.x(), (int) (particle.y() - progress * 8 * particle.drift()),
                        size, color);
            }
        }
    }

    private boolean enabled() {
        CrystalClient bootstrap = CrystalClient.get();
        if (bootstrap == null) {
            return false;
        }
        var settings = bootstrap.theme().settings();
        return settings.decorations && settings.catCosmetics && !settings.reducedMotion;
    }
}
