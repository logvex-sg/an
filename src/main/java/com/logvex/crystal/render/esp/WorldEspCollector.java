package com.logvex.crystal.render.esp;

import java.util.ArrayList;
import java.util.List;

/**
 * A per-frame buffer of world-space ESP primitives.
 *
 * <p>Modules do not draw directly. Each frame the renderer calls every ESP module, which
 * appends boxes and labels here; the renderer then draws the whole buffer in one pass.
 * This separates "what to show" (gameplay-side decisions, cheap and testable) from "how to
 * draw it" (one batched render pass), and it means a module can be collected without
 * touching the GL pipeline.
 *
 * <p>The buffer is reused across frames (cleared, not reallocated) so a busy frame does
 * not churn the heap.
 */
public final class WorldEspCollector {

    /** A world-space box outline request. */
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                      int color, float lineWidth) {
    }

    /** A world-space text label request. */
    public record Label(double x, double y, double z, String text, int color) {
    }

    private final List<Box> boxes = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();

    /** Clears the buffer for a new frame, keeping the backing arrays. */
    public void clear() {
        boxes.clear();
        labels.clear();
    }

    public void addBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                       int color, float lineWidth) {
        boxes.add(new Box(minX, minY, minZ, maxX, maxY, maxZ, color, lineWidth));
    }

    public void addLabel(double x, double y, double z, String text, int color) {
        labels.add(new Label(x, y, z, text, color));
    }

    public List<Box> boxes() {
        return boxes;
    }

    public List<Label> labels() {
        return labels;
    }

    public boolean isEmpty() {
        return boxes.isEmpty() && labels.isEmpty();
    }
}
