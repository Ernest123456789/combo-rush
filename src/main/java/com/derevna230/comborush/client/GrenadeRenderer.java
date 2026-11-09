package com.derevna230.comborush.client;

import com.derevna230.comborush.item.CRGrenadeItem;
import com.derevna230.comborush.item.GrenadeEntity;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import java.util.EnumMap;
import java.util.Map;

/** Летящая граната: рисуем тот же предмет (GeckoLib-модель с «наземной» позой), кувыркающийся в полёте. После срабатывания (дым, огонь) модель скрыта. */
public class GrenadeRenderer extends EntityRenderer<GrenadeEntity> {
    private final ItemRenderer items;
    private final Map<String, ItemStack> stacks = new java.util.HashMap<>();

    public GrenadeRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.items = ctx.getItemRenderer();
        this.shadowRadius = 0.12f;
    }

    @Override
    public Identifier getTexture(GrenadeEntity entity) { return Identifier.ofVanilla("textures/misc/white.png"); }

    @Override
    public void render(GrenadeEntity e, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider vp, int light) {
        if (e.done()) return;
        ItemStack st = stacks.computeIfAbsent(e.itemId(), k -> {
            net.minecraft.item.Item it = net.minecraft.registry.Registries.ITEM.get(Identifier.of(com.derevna230.comborush.ComboRush.MOD_ID, k));
            return new ItemStack(it == net.minecraft.item.Items.AIR ? ModItems.GRENADES.get(e.kind()) : it);
        });
        m.push();
        m.translate(0, 0.18, 0);
        float a = MathHelper.lerp(tickDelta, e.prevSpin, e.spin);
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(a));
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(a * 0.35f));
        items.renderItem(st, ModelTransformationMode.GROUND, light, OverlayTexture.DEFAULT_UV, m, vp, e.getWorld(), e.getId());
        m.pop();
    }
}
