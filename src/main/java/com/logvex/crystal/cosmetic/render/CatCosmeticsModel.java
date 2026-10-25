package com.logvex.crystal.cosmetic.render;

import com.logvex.crystal.cosmetic.PlayerCosmetics;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.math.MathHelper;

/**
 * The cosmetic accessory model worn by players: cat ears, an optional pastel bow and an
 * optional halo. Original geometry authored for LOGVEX CRYSTAL.
 *
 * <p>The model is drawn in head-local space (the feature renderer applies the vanilla head
 * transform before rendering), so the parts are positioned relative to the head with the
 * top of the head at {@code y = -8}, exactly as the vanilla biped head box is laid out.
 *
 * <p>{@link #setAngles} is invoked automatically by the render command queue. It resets the
 * parts and then, only while the cosmetic is animated, applies a gentle sway derived from the
 * entity's age. When reduced motion or the animation toggle is off the accessories stay still.
 */
public final class CatCosmeticsModel extends EntityModel<PlayerEntityRenderState> {

    /** Radius of the halo ring, in model units. */
    private static final float HALO_RADIUS = 4.4f;
    private static final int HALO_SEGMENTS = 8;

    private final ModelPart earLeft;
    private final ModelPart earRight;
    private final ModelPart bow;
    private final ModelPart halo;

    public CatCosmeticsModel(ModelPart root) {
        super(root);
        this.earLeft = root.getChild("ear_left");
        this.earRight = root.getChild("ear_right");
        this.bow = root.getChild("bow");
        this.halo = root.getChild("halo");
    }

    @Override
    public void setAngles(PlayerEntityRenderState state) {
        super.setAngles(state);
        PlayerCosmetics.Style style = PlayerCosmetics.style();
        boolean ears = style == PlayerCosmetics.Style.CAT_EARS || style == PlayerCosmetics.Style.CAT_EARS_BOW;
        boolean bowVisible = style == PlayerCosmetics.Style.CAT_EARS_BOW;
        boolean haloVisible = style == PlayerCosmetics.Style.HALO;
        earLeft.visible = ears;
        earRight.visible = ears;
        bow.visible = bowVisible;
        halo.visible = haloVisible;
        if (!PlayerCosmetics.animated()) {
            return;
        }
        float age = state == null ? 0f : state.age;
        float sway = PlayerCosmetics.sway(age, 0f);
        earLeft.roll = sway;
        earRight.roll = -sway;
        earLeft.yaw = sway * 0.5f;
        earRight.yaw = -sway * 0.5f;
        halo.pitch = MathHelper.sin(age / 24f) * 0.06f;
    }

    /** The baked geometry for the accessory layer. */
    public static TexturedModelData getTexturedModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();

        // Cat ears. The group pivot sits at the head top so a small roll sways the whole ear.
        addEar(root, "ear_left", 2.2f, false);
        addEar(root, "ear_right", -2.2f, true);

        // Bow: two lobes and a knot, pinned to the back of the head.
        ModelPartData bow = root.addChild("bow", ModelPartBuilder.create(),
                ModelTransform.origin(0f, -4.0f, 4.3f));
        bow.addChild("bow_left", ModelPartBuilder.create().uv(0, 6)
                        .cuboid(-3.0f, -1.2f, -0.45f, 2.6f, 2.0f, 0.9f),
                ModelTransform.NONE);
        bow.addChild("bow_right", ModelPartBuilder.create().uv(0, 6)
                        .cuboid(0.4f, -1.2f, -0.45f, 2.6f, 2.0f, 0.9f),
                ModelTransform.NONE);
        bow.addChild("bow_knot", ModelPartBuilder.create().uv(10, 6)
                        .cuboid(-0.7f, -1.0f, -0.5f, 1.4f, 1.4f, 1.0f),
                ModelTransform.NONE);

        // Halo: a ring of flat segments above the head.
        ModelPartData halo = root.addChild("halo", ModelPartBuilder.create(),
                ModelTransform.origin(0f, -11.6f, 0f));
        for (int i = 0; i < HALO_SEGMENTS; i++) {
            float angle = (float) (i * (Math.PI * 2.0 / HALO_SEGMENTS));
            float x = HALO_RADIUS * MathHelper.sin(angle);
            float z = HALO_RADIUS * MathHelper.cos(angle);
            halo.addChild("halo_" + i, ModelPartBuilder.create().uv(0, 12)
                            .cuboid(-1.1f, -0.25f, -0.4f, 2.2f, 0.5f, 0.8f),
                    ModelTransform.of(x, 0f, z, 0f, -angle, 0f));
        }

        return TexturedModelData.of(data, 64, 64);
    }

    /**
     * Fetches the baked accessory part from the client's entity model set, falling back to
     * building it directly from the provider if the layer is somehow not baked. The fallback
     * keeps the cosmetic working even if a resource reload races registration.
     */
    public static net.minecraft.client.model.ModelPart bakedPart(
            net.minecraft.client.render.entity.EntityRendererFactory.Context context) {
        try {
            return context.getEntityModels().getModelPart(CosmeticModelLayers.PLAYER_ACCESSORIES);
        } catch (RuntimeException e) {
            return getTexturedModelData().createModel();
        }
    }

    private static void addEar(ModelPartData root, String name, float pivotX, boolean mirrored) {
        ModelPartData ear = root.addChild(name, ModelPartBuilder.create(),
                ModelTransform.origin(pivotX, -8.0f, -0.4f));
        ModelPartBuilder outer = ModelPartBuilder.create().uv(0, 0);
        ModelPartBuilder inner = ModelPartBuilder.create().uv(10, 0);
        if (mirrored) {
            outer = outer.mirrored();
            inner = inner.mirrored();
        }
        ear.addChild(name + "_outer", outer.cuboid(-1.2f, -2.8f, -0.6f, 2.4f, 2.8f, 1.2f),
                ModelTransform.NONE);
        ear.addChild(name + "_inner", inner.cuboid(-0.75f, -2.3f, -0.65f, 1.5f, 1.9f, 0.9f),
                ModelTransform.NONE);
    }
}
