package com.derevna230.comborush.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.RenderUtil;

import java.util.HashMap;
import java.util.Map;

/** Рендер оружия, который от первого лица дорисовывает руки в перчатках, держащие его ({@link FpHands}) */
public class HandsItemRenderer<T extends Item & GeoAnimatable> extends GeoItemRenderer<T> {
    private final FpHands.Mode mode;
    private final Map<String, FpHands.Cap> caps = new HashMap<>();

    public HandsItemRenderer(GeoModel<T> model, FpHands.Mode mode) {
        super(model);
        this.mode = mode;
    }

    private boolean firstPerson() { return mode != null && renderPerspective != null && renderPerspective.isFirstPerson(); }

    @Override
    public void renderRecursively(MatrixStack poseStack, T animatable, GeoBone bone, RenderLayer renderType, VertexConsumerProvider bufferSource,
                                  VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        if (!isReRender && firstPerson() && FpHands.wants(bone.getName())) {
            poseStack.push();
            RenderUtil.prepMatrixForBone(poseStack, bone);
            caps.put(bone.getName(), new FpHands.Cap(new Matrix4f(poseStack.peek().getPositionMatrix()), new Matrix3f(poseStack.peek().getNormalMatrix()),
                FpHands.bounds(bone), Math.abs(bone.getPosX()) + Math.abs(bone.getPosY()) + Math.abs(bone.getPosZ())));
            poseStack.pop();
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    @Override
    public void postRender(MatrixStack poseStack, T animatable, BakedGeoModel model, VertexConsumerProvider bufferSource, VertexConsumer buffer,
                           boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        super.postRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        if (!isReRender && firstPerson() && !caps.isEmpty() && bufferSource != null) FpHands.draw(caps, mode, bufferSource, packedLight);
        caps.clear();
    }
}
