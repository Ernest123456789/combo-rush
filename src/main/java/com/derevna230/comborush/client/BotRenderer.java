package com.derevna230.comborush.client;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.client.agent.AgentClient;
import com.derevna230.comborush.client.agent.AgentModel;
import com.derevna230.comborush.client.agent.AgentModels;
import com.derevna230.comborush.match.BotEntity;
import com.derevna230.comborush.match.Team;
import com.derevna230.comborush.mixin.LivingRendererAccess;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Бот матча: модель бойца своей стороны (как у игроков), ствол в руках как у игрока */
public class BotRenderer extends BipedEntityRenderer<BotEntity, PlayerEntityModel<BotEntity>> {
    private static final Identifier TEX_T = Identifier.of(ComboRush.MOD_ID, "textures/entity/agent_t.png");
    private static final Identifier TEX_CT = Identifier.of(ComboRush.MOD_ID, "textures/entity/agent_ct.png");
    private final AgentModel<BotEntity> modelT, modelCT;

    public BotRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, AgentModels.<BotEntity>create("ct"), 0.5f);
        this.modelCT = (AgentModel<BotEntity>) this.model;
        this.modelT = AgentModels.create("t");
        AgentClient.init(ctx);           // рендеры бойцов-игроков создаём здесь: тут есть контекст рендера
    }

    @Override
    public Identifier getTexture(BotEntity e) { return e.team() == Team.T ? TEX_T : TEX_CT; }

    @Override
    public void render(BotEntity e, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
        ((LivingRendererAccess) this).setComborushModel(e.team() == Team.T ? modelT : modelCT);
        this.model.rightArmPose = BipedEntityModel.ArmPose.CROSSBOW_HOLD;   // ствол держим как арбалет — двумя руками
        this.model.leftArmPose = BipedEntityModel.ArmPose.EMPTY;
        super.render(e, yaw, tickDelta, matrices, vertices, light);
    }
}
