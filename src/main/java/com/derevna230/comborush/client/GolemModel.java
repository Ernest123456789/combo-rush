package com.derevna230.comborush.client;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.boss.RedstoneGolemEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoRenderer;

/** Редстоуновый голем: во второй фазе — раскалённая текстура. Между атаками голова следит за игроком */
public class GolemModel extends GeoModel<RedstoneGolemEntity> {
    private static Identifier res(String path) { return Identifier.of(ComboRush.MOD_ID, path); }

    public Identifier getModelResource(RedstoneGolemEntity e) { return res("geo/entity/boss/redstone_golem.geo.json"); }
    public Identifier getTextureResource(RedstoneGolemEntity e) {
        return res(e.phase() >= 2 ? "textures/entity/boss/redstone_golem_enraged.png" : "textures/entity/boss/redstone_golem.png");
    }
    public Identifier getAnimationResource(RedstoneGolemEntity e) { return res("animations/entity/boss/redstone_golem.animation.json"); }

    public Identifier getModelResource(RedstoneGolemEntity e, GeoRenderer<RedstoneGolemEntity> renderer) { return getModelResource(e); }
    public Identifier getTextureResource(RedstoneGolemEntity e, GeoRenderer<RedstoneGolemEntity> renderer) { return getTextureResource(e); }

    @Override
    public void setCustomAnimations(RedstoneGolemEntity e, long instanceId, AnimationState<RedstoneGolemEntity> state) {
        if (e.attackId() != RedstoneGolemEntity.NONE) return;
        GeoBone head = getAnimationProcessor().getBone("redstone_golem_head");
        if (head == null) return;
        EntityModelData d = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (d == null) return;
        head.setRotX(head.getRotX() + MathHelper.clamp(d.headPitch(), -25f, 25f) * MathHelper.RADIANS_PER_DEGREE);
        head.setRotY(head.getRotY() + MathHelper.clamp(d.netHeadYaw(), -60f, 60f) * MathHelper.RADIANS_PER_DEGREE);
    }
}
