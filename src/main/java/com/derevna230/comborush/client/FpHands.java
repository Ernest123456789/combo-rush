package com.derevna230.comborush.client;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.client.agent.AgentClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Руки от первого лица со стволом/ножом/гранатой: кисть в перчатке, большой палец, манжета и рукав формы.
 * Кисти привязаны к костям модели оружия (рукоять, цевьё, магазин, рукоять ножа), поэтому повторяют все анимации:
 * выстрел, передёргивание помпы, перезарядку (левая рука переходит на магазин), осмотр, доставание.
 * Координаты — в единицах модели (1/16 блока), в системе кости после всех анимаций.
 */
public final class FpHands {
    private FpHands() {}

    public enum Mode { RIFLE, PISTOL, KNIFE, GRENADE }

    /** Снимок кости: матрицы, габариты её кубов (x0,x1,y0,y1,z0,z1 в единицах модели), смещение анимацией */
    public record Cap(Matrix4f pose, Matrix3f normal, float[] b, float moved) {}

    public static boolean wants(String bone) {
        return bone.equals("pistol_grip") || bone.equals("foregrip") || bone.equals("magazine") || bone.equals("handle") || bone.equals("weapon");
    }

    private static final Map<GeoBone, float[]> BOUNDS = new IdentityHashMap<>();

    public static float[] bounds(GeoBone bone) {
        return BOUNDS.computeIfAbsent(bone, b -> {
            float[] r = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE};
            boolean any = false;
            for (GeoCube c : b.getCubes())
                for (GeoQuad q : c.quads())
                    for (GeoVertex v : q.vertices()) {
                        float x = v.position().x() * 16, y = v.position().y() * 16, z = v.position().z() * 16;
                        r[0] = Math.min(r[0], x); r[1] = Math.max(r[1], x);
                        r[2] = Math.min(r[2], y); r[3] = Math.max(r[3], y);
                        r[4] = Math.min(r[4], z); r[5] = Math.max(r[5], z);
                        any = true;
                    }
            return any ? r : null;
        });
    }

    // ---------------------------------------------------------------- текстуры
    private static Identifier glove() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Boolean ct = mc.player == null ? null : AgentClient.side(mc.player);
        boolean isCt = ct == null ? AgentClient.menuCT() : ct;
        return Identifier.of(ComboRush.MOD_ID, "textures/entity/gloves/" + AgentClient.gloveFor(isCt) + ".png");
    }

    private static Identifier sleeve() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Boolean ct = mc.player == null ? null : AgentClient.side(mc.player);
        boolean isCt = ct == null ? AgentClient.menuCT() : ct;
        return Identifier.of(ComboRush.MOD_ID, "textures/entity/gloves/sleeve_" + (isCt ? "ct" : "t") + ".png");
    }

    // области текстуры перчатки 64×64
    private static final float[] BACK = {0, 0, 32, 32}, FING = {32, 0, 64, 16}, PALM = {32, 16, 64, 32}, CUFF = {0, 32, 32, 48}, SLV = {0, 0, 32, 32};

    // ---------------------------------------------------------------- отрисовка
    public static void draw(Map<String, Cap> caps, Mode mode, VertexConsumerProvider vcp, int light) {
        Cap grip = caps.get("pistol_grip");
        if (mode == Mode.KNIFE) grip = caps.containsKey("handle") && caps.get("handle").b() != null ? caps.get("handle") : caps.get("weapon");
        if (grip == null || grip.b() == null) return;
        Identifier gt = glove();
        // ---- правая рука
        float[] g = grip.b();
        MatrixStack ms = stack(grip);
        float gx0 = g[0], gx1 = g[1], gy0 = g[2], gy1 = g[3], gz0 = g[4], gz1 = g[5];
        if (mode == Mode.KNIFE && !caps.containsKey("handle")) gz1 = gz0 + Math.min(5f, (gz1 - gz0) * 0.4f);     // рукоять — задняя часть ножа
        float cx = (gx0 + gx1) / 2;
        float top, h, wy;
        if (mode == Mode.KNIFE || mode == Mode.GRENADE) {
            top = mode == Mode.GRENADE ? (gy0 + gy1) / 2 + 0.5f : gy1 + 0.6f;
            h = top - gy0 + 0.7f;
            box(vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(gt)), ms, gx0 - 0.8f, gx1 + 0.8f, top - h, top, gz0 - 0.4f, gz1 + 0.4f, BACK, FING, light, 64);
            box(vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(gt)), ms, gx1 + 0.3f, gx1 + 1.4f, top - 1.8f, top + 0.3f, gz0 + 0.4f, gz1 + 1.4f, FING, FING, light, 64);   // большой палец
            wy = top - h * 0.5f;
        } else {
            top = gy1 + 0.4f;
            h = Math.min(gy1 - gy0, 5f) + 0.4f;
            box(vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(gt)), ms, gx0 - 0.7f, gx1 + 0.7f, top - h, top, gz0 - 0.8f, gz1 + 0.6f, BACK, FING, light, 64);
            box(vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(gt)), ms, gx1 + 0.4f, gx1 + 1.5f, top - 1.7f, top + 0.2f, gz0 + 0.2f, gz1 + 1.6f, FING, FING, light, 64);
            wy = top - h * 0.55f;
        }
        forearm(vcp, ms, cx, wy, gz0 - 0.6f, 0.18f, -0.32f, light);

        // ---- левая рука
        if (mode == Mode.RIFLE) {
            Cap mag = caps.get("magazine"), fore = caps.get("foregrip");
            boolean reload = mag != null && mag.b() != null && mag.moved() > 1.5f;
            if (reload) {
                float[] m = mag.b();
                MatrixStack mm = stack(mag);
                float mx = (m[0] + m[1]) / 2;
                box(vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(gt)), mm, m[0] - 0.8f, m[1] + 0.8f, m[2] - 1.2f, m[2] + 2.4f, m[4] - 0.6f, m[5] + 0.6f, BACK, FING, light, 64);
                forearm(vcp, mm, mx + 0.5f, m[2] - 0.8f, m[4], -0.45f, -0.7f, light);
            } else if (fore != null && fore.b() != null) {
                float[] f = fore.b();
                MatrixStack fm = stack(fore);
                float fx = (f[0] + f[1]) / 2, fh = f[3] - f[2], len = f[5] - f[4];
                float zc = f[4] + Math.min(len * 0.35f, 5f);
                box(vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(gt)), fm, f[0] - 0.8f, f[1] + 0.8f, f[2] - 1.6f, f[2] + Math.min(2.2f, fh * 0.6f), zc - 2.2f, zc + 2.2f, PALM, FING, light, 64);
                box(vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(gt)), fm, f[0] - 1.3f, f[0] - 0.4f, f[2] - 0.5f, f[2] + fh * 0.8f, zc - 2.0f, zc + 1.8f, FING, FING, light, 64);   // пальцы сбоку
                box(vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(gt)), fm, f[1] + 0.4f, f[1] + 1.3f, f[2], f[2] + fh * 0.7f, zc - 1.5f, zc + 2.0f, FING, FING, light, 64);         // большой палец
                forearm(vcp, fm, fx + 0.6f, f[2] - 1.2f, zc - 1.8f, -0.5f, -0.55f, light);
            }
        } else if (mode == Mode.PISTOL) {
            // вторая рука обхватывает первую снизу и слева
            box(vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(gt)), ms, gx1 - 0.2f, gx1 + 2.8f, top - h - 0.6f, top - 1.0f, gz0 - 0.4f, gz1 + 1.2f, BACK, FING, light, 64);
            box(vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(gt)), ms, gx0 - 0.6f, gx1 + 2.6f, top - h - 0.4f, top - 1.4f, gz1 + 0.4f, gz1 + 1.3f, FING, FING, light, 64);
            forearm(vcp, ms, gx1 + 1.6f, top - h * 0.7f, gz0 - 0.4f, -0.55f, -0.35f, light);
        }
    }

    private static MatrixStack stack(Cap c) {
        MatrixStack ms = new MatrixStack();
        ms.peek().getPositionMatrix().set(c.pose());
        ms.peek().getNormalMatrix().set(c.normal());
        return ms;
    }

    /** Предплечье: манжета перчатки и рукав, уходят назад-вниз к камере; yaw — вбок, pitch — вниз */
    private static void forearm(VertexConsumerProvider vcp, MatrixStack base, float x, float y, float z, float yaw, float pitch, int light) {
        base.push();
        base.translate(x / 16f, y / 16f, z / 16f);
        base.multiply(RotationAxis.POSITIVE_Y.rotation(yaw));
        base.multiply(RotationAxis.POSITIVE_X.rotation(pitch));
        box(vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(glove())), base, -2.0f, 2.0f, -2.0f, 2.0f, -4.5f, 0.6f, CUFF, CUFF, light, 64);
        box(vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(sleeve())), base, -2.25f, 2.25f, -2.25f, 2.25f, -30f, -4.5f, SLV, SLV, light, 32);
        base.pop();
    }

    /**
     * Коробка (единицы модели): боковые грани и низ — область side, перед (+z) и верх — область front.
     * Буфер берётся заново перед каждой коробкой: смена текстуры (перчатка ↔ рукав) закрывает прежний буфер.
     */
    private static void box(VertexConsumer vc, MatrixStack ms, float x0, float x1, float y0, float y1, float z0, float z1,
                            float[] side, float[] front, int light, int tex) {
        MatrixStack.Entry e = ms.peek();
        float a = x0 / 16f, b = x1 / 16f, c = y0 / 16f, d = y1 / 16f, f = z0 / 16f, g = z1 / 16f;
        quad(vc, e, light, tex, front, b, c, g, a, c, g, a, d, g, b, d, g, 0, 0, 1);      // +z
        quad(vc, e, light, tex, side, a, c, f, b, c, f, b, d, f, a, d, f, 0, 0, -1);      // −z
        quad(vc, e, light, tex, side, b, c, f, b, c, g, b, d, g, b, d, f, 1, 0, 0);       // +x
        quad(vc, e, light, tex, side, a, c, g, a, c, f, a, d, f, a, d, g, -1, 0, 0);      // −x
        quad(vc, e, light, tex, front, a, d, f, b, d, f, b, d, g, a, d, g, 0, 1, 0);      // верх
        quad(vc, e, light, tex, side, a, c, g, b, c, g, b, c, f, a, c, f, 0, -1, 0);      // низ
    }

    private static void quad(VertexConsumer vc, MatrixStack.Entry e, int light, int tex, float[] r,
                             float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4,
                             float nx, float ny, float nz) {
        float u0 = r[0] / tex, v0 = r[1] / tex, u1 = r[2] / tex, v1 = r[3] / tex;
        Matrix4f p = e.getPositionMatrix();
        vc.vertex(p, x1, y1, z1).color(255, 255, 255, 255).texture(u0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, ny, nz);
        vc.vertex(p, x2, y2, z2).color(255, 255, 255, 255).texture(u1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, ny, nz);
        vc.vertex(p, x3, y3, z3).color(255, 255, 255, 255).texture(u1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, ny, nz);
        vc.vertex(p, x4, y4, z4).color(255, 255, 255, 255).texture(u0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, ny, nz);
    }
}
