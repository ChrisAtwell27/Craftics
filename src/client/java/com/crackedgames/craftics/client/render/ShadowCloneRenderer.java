package com.crackedgames.craftics.client.render;

import com.crackedgames.craftics.entity.ShadowCloneEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.ArmorEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

// Draws a Shadow: a player's model, wearing the copied player's own skin darkened, with the
// armour and held weapon the server gave it.
//
// The class is written three times. Minecraft rebuilt entity rendering in 1.21.2 (render
// states) and changed held items again in 1.21.4, and nearly every line here touches one or
// the other. What does not change lives in ShadowCloneSkins.
//
// Both arm widths are loaded and the right one is picked per Shadow each frame, because one
// renderer serves every Shadow in a fight and two players can have different models.

//? if <=1.21.1 {
public class ShadowCloneRenderer extends net.minecraft.client.render.entity.BipedEntityRenderer<
        ShadowCloneEntity, PlayerEntityModel<ShadowCloneEntity>> {

    private final PlayerEntityModel<ShadowCloneEntity> wide;
    private final PlayerEntityModel<ShadowCloneEntity> slim;

    public ShadowCloneRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new DarkModel(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
        this.wide = this.model;
        this.slim = new DarkModel(ctx.getPart(EntityModelLayers.PLAYER_SLIM), true);
        this.addFeature(new ArmorFeatureRenderer<>(this,
            new ArmorEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
            new ArmorEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)),
            ctx.getModelManager()));
    }

    @Override
    public void render(ShadowCloneEntity shadow, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        this.model = ShadowCloneSkins.slim(ShadowCloneSkins.of(shadow)) ? slim : wide;
        super.render(shadow, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(ShadowCloneEntity shadow) {
        return ShadowCloneSkins.of(shadow).texture();
    }

    // This renderer generation has no hook for tinting a model, so the model darkens itself.
    private static final class DarkModel extends PlayerEntityModel<ShadowCloneEntity> {
        DarkModel(net.minecraft.client.model.ModelPart root, boolean thinArms) {
            super(root, thinArms);
        }

        @Override
        public void render(MatrixStack matrices, net.minecraft.client.render.VertexConsumer vertices,
                           int light, int overlay, int color) {
            super.render(matrices, vertices, light, overlay, ShadowCloneSkins.darken(color));
        }
    }
}
//?} else if <=1.21.3 {
/*public class ShadowCloneRenderer extends net.minecraft.client.render.entity.MobEntityRenderer<
        ShadowCloneEntity, net.minecraft.client.render.entity.state.PlayerEntityRenderState, PlayerEntityModel> {

    private final PlayerEntityModel wide;
    private final PlayerEntityModel slim;

    public ShadowCloneRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
        this.wide = this.model;
        this.slim = new PlayerEntityModel(ctx.getPart(EntityModelLayers.PLAYER_SLIM), true);
        this.addFeature(new ArmorFeatureRenderer<>(this,
            new ArmorEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
            new ArmorEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)),
            ctx.getEquipmentRenderer()));
        this.addFeature(new net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer<>(
            this, ctx.getItemRenderer()));
    }

    @Override
    public net.minecraft.client.render.entity.state.PlayerEntityRenderState createRenderState() {
        return new net.minecraft.client.render.entity.state.PlayerEntityRenderState();
    }

    @Override
    public void updateRenderState(ShadowCloneEntity shadow,
                                  net.minecraft.client.render.entity.state.PlayerEntityRenderState state,
                                  float tickDelta) {
        super.updateRenderState(shadow, state, tickDelta);
        net.minecraft.client.render.entity.BipedEntityRenderer.updateBipedRenderState(shadow, state, tickDelta);
        state.skinTextures = ShadowCloneSkins.of(shadow);
        // The outer skin layer is part of the look. A player toggles these; a Shadow shows all.
        state.hatVisible = true;
        state.jacketVisible = true;
        state.leftSleeveVisible = true;
        state.rightSleeveVisible = true;
        state.leftPantsLegVisible = true;
        state.rightPantsLegVisible = true;
    }

    @Override
    public void render(net.minecraft.client.render.entity.state.PlayerEntityRenderState state,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        this.model = ShadowCloneSkins.slim(state.skinTextures) ? slim : wide;
        super.render(state, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(net.minecraft.client.render.entity.state.PlayerEntityRenderState state) {
        return state.skinTextures.texture();
    }

    @Override
    protected int getMixColor(net.minecraft.client.render.entity.state.PlayerEntityRenderState state) {
        return ShadowCloneSkins.TINT;
    }
}
*///?} else {
/*public class ShadowCloneRenderer extends net.minecraft.client.render.entity.MobEntityRenderer<
        ShadowCloneEntity, net.minecraft.client.render.entity.state.PlayerEntityRenderState, PlayerEntityModel> {

    private final PlayerEntityModel wide;
    private final PlayerEntityModel slim;

    public ShadowCloneRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
        this.wide = this.model;
        this.slim = new PlayerEntityModel(ctx.getPart(EntityModelLayers.PLAYER_SLIM), true);
        this.addFeature(new ArmorFeatureRenderer<>(this,
            new ArmorEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
            new ArmorEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)),
            ctx.getEquipmentRenderer()));
        this.addFeature(new net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer<>(this));
    }

    @Override
    public net.minecraft.client.render.entity.state.PlayerEntityRenderState createRenderState() {
        return new net.minecraft.client.render.entity.state.PlayerEntityRenderState();
    }

    @Override
    public void updateRenderState(ShadowCloneEntity shadow,
                                  net.minecraft.client.render.entity.state.PlayerEntityRenderState state,
                                  float tickDelta) {
        super.updateRenderState(shadow, state, tickDelta);
        net.minecraft.client.render.entity.BipedEntityRenderer.updateBipedRenderState(
            shadow, state, tickDelta, this.itemModelResolver);
        state.skinTextures = ShadowCloneSkins.of(shadow);
        // The outer skin layer is part of the look. A player toggles these; a Shadow shows all.
        state.hatVisible = true;
        state.jacketVisible = true;
        state.leftSleeveVisible = true;
        state.rightSleeveVisible = true;
        state.leftPantsLegVisible = true;
        state.rightPantsLegVisible = true;
    }

    @Override
    public void render(net.minecraft.client.render.entity.state.PlayerEntityRenderState state,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        this.model = ShadowCloneSkins.slim(state.skinTextures) ? slim : wide;
        super.render(state, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(net.minecraft.client.render.entity.state.PlayerEntityRenderState state) {
        return state.skinTextures.texture();
    }

    @Override
    protected int getMixColor(net.minecraft.client.render.entity.state.PlayerEntityRenderState state) {
        return ShadowCloneSkins.TINT;
    }
}
*///?}
