package com.derevna230.comborush.client;

import com.derevna230.comborush.ComboRush;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * Объёмная модель пары перчаток для осмотра: манжета с ремешком, кисть, костяшки, четыре пальца из двух фаланг
 * (согнуты к ладони) и большой палец. Текстура — та же, что у рук от первого лица (64×64: тыл, пальцы, ладонь, манжета).
 * Единицы — как в моделях (1/16 блока); тыл кисти смотрит на +z.
 */
public final class GloveModel {
    private GloveModel() {}

    public static Identifier texture(String id) { return Identifier.of(ComboRush.MOD_ID, "textures/entity/gloves/" + id + ".png"); }

    /** Пара перчаток вокруг текущего начала координат (по центру между кистями) */
    public static void drawPair(MatrixStack ms, VertexConsumerProvider vcp, String gloveId, int light) {
        Identifier t = texture(gloveId);
        ms.push(); ms.translate(3.6f / 16f, 0, 0); ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-8)); hand(ms, vcp, t, light); ms.pop();
        ms.push(); ms.translate(-3.6f / 16f, 0, 0); ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(8)); ms.scale(-1, 1, 1); hand(ms, vcp, t, light); ms.pop();
    }

    private static void box(MatrixStack ms, VertexConsumerProvider vcp, Identifier t, float x0, float x1, float y0, float y1, float z0, float z1,
                            float[] side, float[] front, int light) {
        FpHands.box(vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(t)), ms, x0, x1, y0, y1, z0, z1, side, front, light, 64);
    }

    /** Правая кисть, пальцы вверх, тыл к зрителю (большой палец слева) */
    public static void hand(MatrixStack ms, VertexConsumerProvider vcp, Identifier t, int light) {
        float[] B = FpHands.BACK, F = FpHands.FING, P = FpHands.PALM, C = FpHands.CUFF;
        box(ms, vcp, t, -2.3f, 2.3f, -5.0f, -0.2f, -1.35f, 1.35f, C, C, light);            // манжета
        box(ms, vcp, t, -2.45f, 2.45f, -3.4f, -2.3f, -1.5f, 1.5f, C, C, light);            // ремешок
        box(ms, vcp, t, -0.6f, 0.6f, -3.5f, -2.2f, 1.5f, 1.7f, F, F, light);               // пряжка
        box(ms, vcp, t, -2.1f, 2.1f, -0.2f, 4.4f, -0.85f, 0.85f, P, B, light);             // кисть
        box(ms, vcp, t, -2.05f, 2.05f, 3.6f, 4.6f, 0.85f, 1.15f, B, B, light);             // накладка на костяшки
        float[] len = {2.9f, 3.3f, 3.0f, 2.3f};
        for (int i = 0; i < 4; i++) {
            float cx = -1.55f + i * 1.05f, L = len[i];
            ms.push();
            ms.translate(cx / 16f, 4.4f / 16f, 0);
            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((i - 1.5f) * -3f));       // лёгкий веер
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-12));
            box(ms, vcp, t, -0.48f, 0.48f, 0, L * 0.55f, -0.62f, 0.62f, F, F, light);
            ms.translate(0, L * 0.55f / 16f, 0);
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-28));
            box(ms, vcp, t, -0.44f, 0.44f, -0.05f, L * 0.5f, -0.56f, 0.56f, F, F, light);
            ms.pop();
        }
        ms.push();                                                                           // большой палец
        ms.translate(-1.9f / 16f, 0.9f / 16f, -0.2f / 16f);
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(38));
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-25));
        box(ms, vcp, t, -0.6f, 0.6f, 0, 2.0f, -0.65f, 0.65f, F, F, light);
        ms.translate(0, 2.0f / 16f, 0);
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-18));
        box(ms, vcp, t, -0.52f, 0.52f, -0.05f, 1.6f, -0.58f, 0.58f, F, F, light);
        ms.pop();
    }
}
