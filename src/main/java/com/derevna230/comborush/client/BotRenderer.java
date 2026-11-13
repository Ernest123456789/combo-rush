package com.derevna230.comborush.client;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.match.BotEntity;
import com.derevna230.comborush.match.Team;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Бот матча: модель игрока, скин по команде (T — коричневый с балаклавой, CT — синий со шлемом), ствол в руках как у игрока */
public class BotRenderer extends BipedEntityRenderer<BotEntity, PlayerEntityModel<BotEntity>> {
    private static final Identifier TEX_T = Identifier.of(ComboRush.MOD_ID, "textures/entity/bot_t.png");
    private static final Identifier TEX_CT = Identifier.of(ComboRush.MOD_ID, "textures/entity/bot_ct.png");

    public BotRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public Identifier getTexture(BotEntity e) { return e.team() == Team.T ? TEX_T : TEX_CT; }

    @Override
    public void render(BotEntity e, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
        this.model.rightArmPose = BipedEntityModel.ArmPose.CROSSBOW_HOLD;   // ствол держим как арбалет — двумя руками
        this.model.leftArmPose = BipedEntityModel.ArmPose.EMPTY;
        super.render(e, yaw, tickDelta, matrices, vertices, light);
    }
}
