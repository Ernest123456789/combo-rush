package com.derevna230.comborush.client;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.campaign.StoryEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoRenderer;

/** Модель сюжетного персонажа или афиши по её виду: geo/entity/story/<kind>.geo.json; голова поворачивается к игроку */
public class StoryModel extends GeoModel<StoryEntity> {
    private static Identifier res(String path) { return Identifier.of(ComboRush.MOD_ID, path); }

    public Identifier getModelResource(StoryEntity e) { return res("geo/entity/story/" + e.kind() + ".geo.json"); }
    public Identifier getTextureResource(StoryEntity e) { return res("textures/entity/story/" + e.kind() + ".png"); }
    public Identifier getAnimationResource(StoryEntity e) { return res("animations/entity/story/" + e.kind() + ".animation.json"); }

    public Identifier getModelResource(StoryEntity e, GeoRenderer<StoryEntity> renderer) { return getModelResource(e); }
    public Identifier getTextureResource(StoryEntity e, GeoRenderer<StoryEntity> renderer) { return getTextureResource(e); }

    @Override
    public void setCustomAnimations(StoryEntity e, long instanceId, AnimationState<StoryEntity> state) {
        if (e.isProp()) return;
        GeoBone head = getAnimationProcessor().getBone("head");
        if (head == null) return;
        EntityModelData d = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (d == null) return;
        head.setRotX(d.headPitch() * MathHelper.RADIANS_PER_DEGREE);
        head.setRotY(d.netHeadYaw() * MathHelper.RADIANS_PER_DEGREE);
    }
}
