package com.derevna230.comborush.client;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.client.agent.AgentClient;
import com.derevna230.comborush.client.agent.AgentModel;
import com.derevna230.comborush.client.agent.AgentModels;
import com.derevna230.comborush.lobby.AgentDefs;
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

import java.util.HashMap;
import java.util.Map;

/** Бот матча: случайный агент своей стороны (как у игроков), ствол в руках как у игрока */
public class BotRenderer extends BipedEntityRenderer<BotEntity, PlayerEntityModel<BotEntity>> {
    private final Map<String, AgentModel<BotEntity>> models = new HashMap<>();

    public BotRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, AgentModels.<BotEntity>create("ct"), 0.5f);
        for (String id : AgentDefs.CT) models.put(id, AgentModels.create(id));
        for (String id : AgentDefs.T) models.put(id, AgentModels.create(id));
        AgentClient.init(ctx);           // рендеры бойцов-игроков создаём здесь: тут есть контекст рендера
    }

    private static String agentId(BotEntity e) {
        var list = e.team() == Team.T ? AgentDefs.T : AgentDefs.CT;
        return list.get(Math.floorMod(e.agent(), list.size()));
    }

    @Override
    public Identifier getTexture(BotEntity e) { return Identifier.of(ComboRush.MOD_ID, "textures/entity/agent_" + agentId(e) + ".png"); }

    @Override
    public void render(BotEntity e, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
        ((LivingRendererAccess) this).setComborushModel(models.get(agentId(e)));
        this.model.rightArmPose = BipedEntityModel.ArmPose.CROSSBOW_HOLD;   // ствол держим как арбалет — двумя руками
        this.model.leftArmPose = BipedEntityModel.ArmPose.EMPTY;
        super.render(e, yaw, tickDelta, matrices, vertices, light);
    }
}
