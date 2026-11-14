package com.derevna230.comborush.client;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Сцена главного меню «Противостояния»: боец стоит на кусочке карты «Дюны» —
 * песчаный пол, край площадки закладки, ящики, полуразрушенная стена, фонарь; позади — небо пустыни.
 * Блоки и персонаж рисуются в одной 3D-системе координат (персонаж — в начале координат, пол — под ним).
 */
public final class MenuDiorama {
    private MenuDiorama() {}

    private record B(int x, int y, int z, BlockState s) {}
    private static List<B> scene;

    private static BlockState sand(int x, int z) {
        int h = (x * 7 + z * 13) & 7;
        return h < 5 ? Blocks.SMOOTH_SANDSTONE.getDefaultState() : h < 7 ? Blocks.CUT_SANDSTONE.getDefaultState() : Blocks.SANDSTONE.getDefaultState();
    }

    private static List<B> scene() {
        if (scene != null) return scene;
        List<B> l = new ArrayList<>();
        // пол в два слоя
        for (int x = -5; x <= 5; x++)
            for (int z = -4; z <= 6; z++) {
                BlockState top = sand(x, z);
                boolean site = x >= 1 && z <= 0;                       // край площадки закладки: справа спереди
                if (site) top = (x == 1 || z == 0) ? Blocks.YELLOW_CONCRETE.getDefaultState() : Blocks.ORANGE_TERRACOTTA.getDefaultState();
                l.add(new B(x, -1, z, top));
                l.add(new B(x, -2, z, Blocks.SANDSTONE.getDefaultState()));
            }
        // стена позади: слева выше, справа обрушена
        for (int x = -5; x <= 5; x++) {
            int hgt = x <= -1 ? 3 : x <= 2 ? 2 : 1;
            for (int y = 0; y < hgt; y++)
                for (int z = 5; z <= 6; z++)
                    l.add(new B(x, y, z, y == 1 && hgt == 3 ? Blocks.CHISELED_SANDSTONE.getDefaultState() : Blocks.CUT_SANDSTONE.getDefaultState()));
        }
        l.add(new B(-3, 2, 4, Blocks.SEA_LANTERN.getDefaultState()));           // фонарь на стене
        // ящики слева
        l.add(new B(-3, 0, 2, Blocks.SPRUCE_PLANKS.getDefaultState()));
        l.add(new B(-3, 1, 2, Blocks.SPRUCE_PLANKS.getDefaultState()));
        l.add(new B(-2, 0, 2, Blocks.DARK_OAK_PLANKS.getDefaultState()));
        l.add(new B(-3, 0, 3, Blocks.DARK_OAK_PLANKS.getDefaultState()));
        l.add(new B(-4, 0, 3, Blocks.SPRUCE_PLANKS.getDefaultState()));
        l.add(new B(-4, 1, 3, Blocks.DARK_OAK_PLANKS.getDefaultState()));
        l.add(new B(-2, 0, 3, Blocks.BARREL.getDefaultState()));
        // справа: низкое укрытие, бочка и столб площадки с фонарём
        l.add(new B(2, 0, 2, Blocks.STONE_BRICKS.getDefaultState()));
        l.add(new B(3, 0, 2, Blocks.STONE_BRICKS.getDefaultState()));
        l.add(new B(3, 0, 3, Blocks.BARREL.getDefaultState()));
        for (int y = 0; y < 2; y++) l.add(new B(4, y, 1, Blocks.YELLOW_CONCRETE.getDefaultState()));
        l.add(new B(4, 2, 1, Blocks.SEA_LANTERN.getDefaultState()));
        l.add(new B(-2, 0, -2, Blocks.DEAD_BUSH.getDefaultState()));
        l.add(new B(2, 0, -2, Blocks.DEAD_BUSH.getDefaultState()));
        scene = l;
        return l;
    }

    /**
     * @param x0..y1   окно сцены на экране (реальные координаты)
     * @param cx, groundY  куда на экране встаёт персонаж (точка под ногами)
     * @param size     пикселей на блок
     * @param viewYaw  поворот сцены вокруг вертикали (радианы)
     */
    public static void draw(DrawContext c, LivingEntity e, float x0, float y0, float x1, float y1, float cx, float groundY, float size,
                            float viewYaw, float bodyYaw, float headYaw, float pitch) {
        c.enableScissor((int) x0, (int) y0, (int) x1, (int) y1);
        // небо пустыни
        c.fillGradient((int) x0, (int) y0, (int) x1, (int) y1, 0xFF1C2A44, 0xFFC8955A);
        float sunX = x0 + (x1 - x0) * 0.78f, sunY = y0 + (y1 - y0) * 0.30f;
        for (int i = 6; i >= 1; i--) {
            int r = (int) (i * 7 * size / 40f);
            c.fill((int) sunX - r, (int) sunY - r / 2, (int) sunX + r, (int) sunY + r / 2, ((10 + 6 * (6 - i)) << 24) | 0xFFE2A0);
        }

        MatrixStack m = c.getMatrices();
        Quaternionf q = new Quaternionf().rotateZ((float) Math.PI).rotateX(-0.30f).rotateY(viewYaw);
        m.push();
        m.translate(0, 0, 900);                                        // сцена целиком перед фоном (глубина потом сбрасывается)
        // блоки
        m.push();
        m.translate(cx, groundY, 50);
        m.scale(size, size, -size);
        m.multiply(q);
        DiffuseLighting.enableGuiDepthLighting();
        MinecraftClient mc = MinecraftClient.getInstance();
        BlockRenderManager brm = mc.getBlockRenderManager();
        VertexConsumerProvider.Immediate vc = c.getVertexConsumers();
        for (B b : scene()) {
            m.push();
            m.translate(b.x() - 0.5f, b.y(), b.z() - 0.5f);
            brm.renderBlockAsEntity(b.s(), m, vc, 0xF000F0, OverlayTexture.DEFAULT_UV);
            m.pop();
        }
        vc.draw();
        m.pop();
        // персонаж: только голова следит за курсором, тело стоит ровно
        float by = e.bodyYaw;
        e.bodyYaw = bodyYaw; e.prevBodyYaw = bodyYaw;
        e.setYaw(bodyYaw); e.prevYaw = bodyYaw;
        e.headYaw = headYaw; e.prevHeadYaw = headYaw;
        e.setPitch(pitch); e.prevPitch = pitch;
        InventoryScreen.drawEntity(c, cx, groundY, size, new Vector3f(), new Quaternionf(q), null, e);
        e.bodyYaw = by;
        m.pop();
        RenderSystem.clear(GlConst.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
        // мягкая виньетка по краям окна
        int vw = (int) (size * 0.8f);
        c.fillGradient((int) x0, (int) y1 - vw, (int) x1, (int) y1, 0x00000000, 0xC0080B12);
        c.fillGradient((int) x0, (int) y0, (int) x1, (int) y0 + vw / 2, 0x90080B12, 0x00000000);
        c.disableScissor();
    }
}
