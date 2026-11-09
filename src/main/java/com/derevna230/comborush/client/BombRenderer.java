package com.derevna230.comborush.client;

import com.derevna230.comborush.item.BombEntity;
import com.derevna230.comborush.item.ModItems;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/** Заложенная бомба на земле: рисуем предмет-бомбу, над ней — таймер (имя сущности) */
public class BombRenderer extends EntityRenderer<BombEntity> {
    private final ItemRenderer items;
    private ItemStack stack;

    public BombRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.items = ctx.getItemRenderer();
        this.shadowRadius = 0.3f;
    }

    @Override
    public Identifier getTexture(BombEntity entity) { return Identifier.ofVanilla("textures/misc/white.png"); }

    @Override
    public void render(BombEntity e, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider vp, int light) {
        if (stack == null) stack = new ItemStack(ModItems.BOMB);
        m.push();
        m.translate(0, 0.22, 0);
        items.renderItem(stack, ModelTransformationMode.GROUND, light, OverlayTexture.DEFAULT_UV, m, vp, e.getWorld(), e.getId());
        m.pop();
        super.render(e, yaw, tickDelta, m, vp, light);
    }
}
