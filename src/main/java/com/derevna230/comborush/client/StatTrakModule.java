package com.derevna230.comborush.client;

import com.derevna230.comborush.ComboRush;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Модуль StatTrak™ на ствольной коробке: корпус по габаритам невидимой кости «stattrak» модели
 * и светящееся табло из 6 цифр (ведущие нули — тусклые) на наружной (+x) грани.
 */
public final class StatTrakModule {
    private StatTrakModule() {}

    private static final Identifier TEX = Identifier.of(ComboRush.MOD_ID, "textures/item/stattrak.png");
    private static final float[] BODY = {0, 0, 16, 8}, SCREEN = {16, 0, 32, 8};
    private static final int GLOW = 0xF000F0;

    public static void draw(Matrix4f pose, Matrix3f normal, float[] b, int kills, VertexConsumerProvider vcp, int light) {
        MatrixStack ms = new MatrixStack();
        ms.peek().getPositionMatrix().set(pose);
        ms.peek().getNormalMatrix().set(normal);
        FpHands.box(vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TEX)), ms, b[0], b[1], b[2], b[3], b[4], b[5], BODY, BODY, light, 64);
        MatrixStack.Entry e = ms.peek();
        float x = (b[1] + 0.02f) / 16f, x2 = (b[1] + 0.04f) / 16f;
        float ya = b[3] - 0.2f, yb = b[2] + 0.2f, za = b[5] - 0.25f, zb = b[4] + 0.25f;
        quad(vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TEX)), e, x, ya / 16f, yb / 16f, za / 16f, zb / 16f, SCREEN[0], SCREEN[1], SCREEN[2], SCREEN[3]);
        String s = String.format("%06d", Math.min(999999, Math.max(0, kills)));
        int firstNz = 0;
        while (firstNz < 5 && s.charAt(firstNz) == '0') firstNz++;
        float cw = (za - zb - 0.3f) / 6f, dy0 = ya - 0.12f, dy1 = yb + 0.12f;
        for (int i = 0; i < 6; i++) {
            int d = s.charAt(i) - '0';
            float zl = za - 0.15f - i * cw, zr = zl - cw * 0.9f;                 // зритель смотрит с +x: «лево» — это +z
            float row = i < firstNz ? 16 : 8;
            quad(vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TEX)), e, x2, dy0 / 16f, dy1 / 16f, zl / 16f, zr / 16f, d * 4, row, d * 4 + 3, row + 7);
        }
    }

    /** вертикальный квад в плоскости x: (y0 сверху, y1 снизу, zl слева, zr справа) */
    private static void quad(VertexConsumer vc, MatrixStack.Entry e, float x, float y0, float y1, float zl, float zr, float u0, float v0, float u1, float v1) {
        Matrix4f p = e.getPositionMatrix();
        float a = u0 / 64f, b = v0 / 64f, c = u1 / 64f, d = v1 / 64f;
        vc.vertex(p, x, y0, zl).color(255, 255, 255, 255).texture(a, b).overlay(OverlayTexture.DEFAULT_UV).light(GLOW).normal(e, 1, 0, 0);
        vc.vertex(p, x, y1, zl).color(255, 255, 255, 255).texture(a, d).overlay(OverlayTexture.DEFAULT_UV).light(GLOW).normal(e, 1, 0, 0);
        vc.vertex(p, x, y1, zr).color(255, 255, 255, 255).texture(c, d).overlay(OverlayTexture.DEFAULT_UV).light(GLOW).normal(e, 1, 0, 0);
        vc.vertex(p, x, y0, zr).color(255, 255, 255, 255).texture(c, b).overlay(OverlayTexture.DEFAULT_UV).light(GLOW).normal(e, 1, 0, 0);
    }
}
