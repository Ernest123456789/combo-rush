package com.derevna230.comborush.client.agent;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;

/** Модель бойца: ванильная модель игрока (те же кости и позы), детали лежат в частях «g» с масштабом 1/K */
public class AgentModel<T extends LivingEntity> extends PlayerEntityModel<T> {
    public AgentModel(ModelPart root, int k) {
        super(root, false);
        float s = 1f / k;
        for (String b : AgentModels.BONES) {
            ModelPart g = root.getChild(b).getChild("g");
            g.xScale = s; g.yScale = s; g.zScale = s;
        }
    }
}
