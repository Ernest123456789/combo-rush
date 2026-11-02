package com.derevna230.comborush.client;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.boss.GolemTraderEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoRenderer;

/** Торговец Редстоуна: голова поворачивается к игроку */
public class TraderModel extends GeoModel<GolemTraderEntity> {
    private static Identifier res(String path) { return Identifier.of(ComboRush.MOD_ID, path); }

    public Identifier getModelResource(GolemTraderEntity e) { return res("geo/entity/boss/golem_trader.geo.json"); }
    public Identifier getTextureResource(GolemTraderEntity e) { return res("textures/entity/boss/golem_trader.png"); }
    public Identifier getAnimationResource(GolemTraderEntity e) { return res("animations/entity/boss/golem_trader.animation.json"); }

    public Identifier getModelResource(GolemTraderEntity e, GeoRenderer<GolemTraderEntity> renderer) { return getModelResource(e); }
    public Identifier getTextureResource(GolemTraderEntity e, GeoRenderer<GolemTraderEntity> renderer) { return getTextureResource(e); }

    @Override
    public void setCustomAnimations(GolemTraderEntity e, long instanceId, AnimationState<GolemTraderEntity> state) {
        GeoBone head = getAnimationProcessor().getBone("head");
        if (head == null) return;
        EntityModelData d = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (d == null) return;
        head.setRotX(head.getRotX() + MathHelper.clamp(d.headPitch(), -30f, 30f) * MathHelper.RADIANS_PER_DEGREE);
        head.setRotY(head.getRotY() + MathHelper.clamp(d.netHeadYaw(), -70f, 70f) * MathHelper.RADIANS_PER_DEGREE);
    }
}
