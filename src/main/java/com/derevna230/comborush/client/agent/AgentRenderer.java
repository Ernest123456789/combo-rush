package com.derevna230.comborush.client.agent;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.mixin.LivingRendererAccess;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Игрок в облике бойца: всё как у обычного игрока (предметы в руках, позы, ник), но своя модель и текстура */
public class AgentRenderer extends PlayerEntityRenderer {
    private final Identifier tex;

    public AgentRenderer(EntityRendererFactory.Context ctx, String id) {
        super(ctx, false);
        ((LivingRendererAccess) this).setComborushModel(AgentModels.<AbstractClientPlayerEntity>create(id));
        this.tex = Identifier.of(ComboRush.MOD_ID, "textures/entity/agent_" + id + ".png");
    }

    @Override
    public Identifier getTexture(AbstractClientPlayerEntity player) { return tex; }

    // руки от первого лица (пустая рука) — в перчатках бойца
    @Override
    public void renderRightArm(MatrixStack matrices, VertexConsumerProvider vertices, int light, AbstractClientPlayerEntity player) {
        arm(matrices, vertices, light, player, getModel().rightArm);
    }

    @Override
    public void renderLeftArm(MatrixStack matrices, VertexConsumerProvider vertices, int light, AbstractClientPlayerEntity player) {
        arm(matrices, vertices, light, player, getModel().leftArm);
    }

    private void arm(MatrixStack matrices, VertexConsumerProvider vertices, int light, AbstractClientPlayerEntity player, ModelPart arm) {
        PlayerEntityModel<AbstractClientPlayerEntity> m = getModel();
        m.handSwingProgress = 0f;
        m.sneaking = false;
        m.leaningPitch = 0f;
        m.setAngles(player, 0f, 0f, 0f, 0f, 0f);
        arm.pitch = 0f;
        arm.render(matrices, vertices.getBuffer(RenderLayer.getEntityTranslucent(tex)), light, OverlayTexture.DEFAULT_UV);
    }
}
