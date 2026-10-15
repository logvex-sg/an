package com.logvex.crystal.render.esp;

import com.logvex.crystal.render.ScreenProjector;
import com.logvex.crystal.render.ViewProjection;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.MatrixStack.Entry;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Draws the {@link WorldEspCollector} buffer into the world.
 *
 * <p>Boxes are drawn as outlines with the vanilla {@code lines()} render layer (one
 * {@code VertexConsumer} per box, flushed immediately) and labels are drawn with the
 * vanilla text renderer into the same consumer provider, so the text is a real 3-D label
 * that scales with distance rather than a screen-space overlay. Both happen inside a single
 * matrix push/pop rooted at the camera, so a failure can never leave the matrix stack
 * unbalanced.
 *
 * <p>The camera-relative offset convention used here matches vanilla's own debug outline
 * rendering (subtract the camera position, then draw).
 */
public final class WorldEspRenderer {

    private static final int MAX_BOXES_PER_FRAME = 4096;

    private WorldEspRenderer() {
    }

    /**
     * Draws the buffer.
     *
     * @param context  the world render context (camera-relative matrix stack + consumers)
     * @param client   the client
     * @param camera   the active camera
     * @param buffer   the collected primitives
     */
    public static void render(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext context,
                              MinecraftClient client, Camera camera, WorldEspCollector buffer) {
        if (buffer.isEmpty() || client.textRenderer == null) {
            return;
        }
        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) {
            return;
        }
        Vec3d cam = camera.getCameraPos();
        List<WorldEspCollector.Box> boxes = buffer.boxes();
        int limit = Math.min(boxes.size(), MAX_BOXES_PER_FRAME);
        matrices.push();
        try {
            matrices.translate(-cam.x, -cam.y, -cam.z);
            Entry entry = matrices.peek();
            Matrix4f matrix = entry.getPositionMatrix();
            for (int i = 0; i < limit; i++) {
                WorldEspCollector.Box box = boxes.get(i);
                VertexConsumer line = consumers.getBuffer(RenderLayers.lines());
                addBoxLines(line, matrix, box);
            }
            drawLabels(client, consumers, matrices, buffer.labels(), camera);
        } finally {
            matrices.pop();
        }
    }

    private static void addBoxLines(VertexConsumer vc, Matrix4f matrix, WorldEspCollector.Box b) {
        float x0 = (float) b.minX();
        float y0 = (float) b.minY();
        float z0 = (float) b.minZ();
        float x1 = (float) b.maxX();
        float y1 = (float) b.maxY();
        float z1 = (float) b.maxZ();
        int c = b.color();
        float w = b.lineWidth();
        // Bottom rectangle.
        edge(vc, matrix, x0, y0, z0, x1, y0, z0, c, w);
        edge(vc, matrix, x1, y0, z0, x1, y0, z1, c, w);
        edge(vc, matrix, x1, y0, z1, x0, y0, z1, c, w);
        edge(vc, matrix, x0, y0, z1, x0, y0, z0, c, w);
        // Top rectangle.
        edge(vc, matrix, x0, y1, z0, x1, y1, z0, c, w);
        edge(vc, matrix, x1, y1, z0, x1, y1, z1, c, w);
        edge(vc, matrix, x1, y1, z1, x0, y1, z1, c, w);
        edge(vc, matrix, x0, y1, z1, x0, y1, z0, c, w);
        // Verticals.
        edge(vc, matrix, x0, y0, z0, x0, y1, z0, c, w);
        edge(vc, matrix, x1, y0, z0, x1, y1, z0, c, w);
        edge(vc, matrix, x1, y0, z1, x1, y1, z1, c, w);
        edge(vc, matrix, x0, y0, z1, x0, y1, z1, c, w);
    }

    private static void edge(VertexConsumer vc, Matrix4f m, float ax, float ay, float az,
                             float bx, float by, float bz, int color, float width) {
        vc.vertex(m, ax, ay, az).color(color).lineWidth(width);
        vc.vertex(m, bx, by, bz).color(color).lineWidth(width);
    }

    private static void drawLabels(MinecraftClient client, VertexConsumerProvider consumers, MatrixStack matrices,
                                   List<WorldEspCollector.Label> labels, Camera camera) {
        if (labels.isEmpty()) {
            return;
        }
        TextRenderer renderer = client.textRenderer;
        float scale = 0.025f;
        for (WorldEspCollector.Label label : labels) {
            matrices.push();
            try {
                matrices.translate(label.x(), label.y(), label.z());
                matrices.scale(scale, -scale, scale);
                float width = renderer.getWidth(label.text());
                Matrix4f matrix = matrices.peek().getPositionMatrix();
                renderer.draw(Text.literal(label.text()), -width / 2f, 0, label.color(), false,
                        matrix, consumers, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
            } catch (Throwable t) {
                // A label must never break the render pass, but a persistent failure should
                // still surface once via the log/; errors rather than vanish.
                com.logvex.crystal.util.BestEffort.report("esp-label", "ESP label", t);
            } finally {
                matrices.pop();
            }
        }
    }

    /** Whether the given world point is close enough to be worth drawing. */
    public static boolean within(Vec3d cameraPos, double x, double y, double z, double maxDistance) {
        double dx = x - cameraPos.x;
        double dy = y - cameraPos.y;
        double dz = z - cameraPos.z;
        return dx * dx + dy * dy + dz * dz <= maxDistance * maxDistance;
    }

    /** Projects a point through the current frame's view-projection for the screen-space ESP. */
    public static ScreenProjector.Projected project(MinecraftClient client, Camera camera, double x, double y, double z) {
        Vec3d cam = camera.getCameraPos();
        float fov = 70f;
        if (client.options != null) {
            fov = client.options.getFov().getValue().floatValue();
        }
        int width = client.getWindow().getFramebufferWidth();
        int height = client.getWindow().getFramebufferHeight();
        float[] mvp = ViewProjection.build(cam.x, cam.y, cam.z, camera.getYaw(), camera.getPitch(),
                fov, width, height, 0.05f, 512f);
        return ScreenProjector.project(mvp, x, y, z, width, height);
    }
}
