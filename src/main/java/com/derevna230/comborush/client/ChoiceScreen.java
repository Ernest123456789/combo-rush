package com.derevna230.comborush.client;

import com.derevna230.comborush.hud.ShopActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.List;

/** Выбор финала: две большие карты — «Кресло Режиссёра» и «Сломать шоу». Выбор общий для сервера */
public class ChoiceScreen extends Screen {
    private static final int DW = 420, DH = 250;
    private final long openAt = System.nanoTime();
    private float u = 1, ox, oy;
    private int hover = -1;
    private boolean sent = false;

    private record Opt(String key, String title, String sub, List<String> lines, int color) {}
    private static final Opt[] OPTS = {
        new Opt("chair", "КРЕСЛО", "РЕЖИССЁРА", List.of(
            "Шоу продолжается — по твоим правилам.",
            "Прошедшие сюжет выбирают следующее событие: /cr direct",
            "Награда: легендарный кейс и эссенция."), 0xFFC94A),
        new Opt("chaos", "СЛОМАТЬ", "ШОУ", List.of(
            "Никакого сценария. Правит случай.",
            "В каждом событии — случайное правило босс-блайнда для всех.",
            "Зато монеты за события удваиваются."), 0xD96BFF)
    };

    private ChoiceScreen() { super(Text.literal("Финал")); }

    public static void open() {
        MinecraftClient.getInstance().setScreen(new ChoiceScreen());
        HudModel.play(com.derevna230.comborush.sound.ModSounds.BOSS_START, 1.2f, 0.6f);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) { }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hover >= 0 && !sent && (System.nanoTime() - openAt) / 1e9 > 0.8) {
            sent = true;
            HudModel.play(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.6f);
            ClientPlayNetworking.send(new ShopActionPayload("camp_choose", 0, 0, OPTS[hover].key()));
            close();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        float t = (System.nanoTime() - openAt) / 1e9f;
        u = Math.min(width / (float) DW, height / (float) DH) * 0.96f;
        ox = (width - DW * u) / 2f; oy = (height - DH * u) / 2f;
        float mx = (mouseX - ox) / u, my = (mouseY - oy) / u;
        c.fillGradient(0, 0, width, height, 0xF0120616, 0xF0060A16);
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(ox, oy, 0);
        m.scale(u, u, 1f);
        text(c, "ФИНАЛ СЕЗОНА", DW / 2f, 8, 2.0f, 0xFFFFFFFF, true);
        text(c, "Выбор один на весь сервер и его не отменить", DW / 2f, 28, 0.65f, 0xFFB8B0D0, true);

        hover = -1;
        float cw = 170, ch = 180, gap = 24, x0 = DW / 2f - cw - gap / 2f;
        for (int i = 0; i < OPTS.length; i++) {
            Opt o = OPTS[i];
            float x = x0 + i * (cw + gap), y = 46;
            float intro = Math.min(1f, Math.max(0f, (t - 0.15f * i) * 3f));
            boolean hv = mx >= x && mx < x + cw && my >= y && my < y + ch && intro >= 1;
            if (hv) hover = i;
            int col = i == 1 ? hsv((t * 0.25f) % 1f) : o.color();
            m.push();
            m.translate(x + cw / 2f, y + ch / 2f + (1 - intro) * 40 + (hv ? -4 : 0), 0);
            float tilt = hv ? (mx - (x + cw / 2f)) / cw * 6f : (float) Math.sin(t * 1.4 + i) * 1.2f;
            m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(tilt));
            float sc = hv ? 1.04f : 1f;
            m.scale(sc, sc, 1);
            m.translate(-cw / 2f, -ch / 2f, 0);
            c.fill(-3, -3, (int) cw + 3, (int) ch + 3, 0xFF000000);
            c.fill(-2, -2, (int) cw + 2, (int) ch + 2, 0xFF000000 | (hv ? col : darken(col, 0.6f)));
            c.fillGradient(0, 0, (int) cw, (int) ch, i == 0 ? 0xFF3A2A12 : 0xFF2A1240, 0xFF120A16);
            for (int yy = 8; yy < ch - 8; yy += 9)
                for (int xx = 8 + ((yy / 9) % 2) * 4; xx < cw - 8; xx += 9) c.fill(xx, yy, xx + 1, yy + 1, 0x18FFFFFF);
            text(c, o.title(), cw / 2f, 18, 2.0f, 0xFF000000 | col, true);
            text(c, o.sub(), cw / 2f, 40, 1.2f, 0xFFFFFFFF, true);
            // эмблема
            if (i == 0) {
                c.fill((int) cw / 2 - 14, 66, (int) cw / 2 + 14, 70, 0xFF000000 | col);
                c.fill((int) cw / 2 - 14, 58, (int) cw / 2 - 10, 70, 0xFF000000 | col);
                c.fill((int) cw / 2 + 10, 58, (int) cw / 2 + 14, 70, 0xFF000000 | col);
                c.fill((int) cw / 2 - 10, 50, (int) cw / 2 + 10, 66, 0xFF000000 | darken(col, 0.7f));
                c.fill((int) cw / 2 - 10, 70, (int) cw / 2 - 7, 78, 0xFF000000 | col);
                c.fill((int) cw / 2 + 7, 70, (int) cw / 2 + 10, 78, 0xFF000000 | col);
            } else {
                for (int k = 0; k < 7; k++) {
                    float jx = (float) Math.sin(t * 9 + k * 2.1) * 3;
                    c.fill((int) (cw / 2 - 18 + jx + k * 5), 50 + (k % 3) * 9, (int) (cw / 2 - 12 + jx + k * 5), 56 + (k % 3) * 9,
                        0xFF000000 | hsv((t * 0.4f + k * 0.13f) % 1f));
                }
            }
            float ly = 92;
            for (String line : o.lines())
                for (String w : StoryHud.wrap(textRenderer, line, (int) ((cw - 16) / 0.7f))) { text(c, w, cw / 2f, ly, 0.7f, 0xFFE6E0FF, true); ly += 9; }
            boolean ready = intro >= 1 && (System.nanoTime() - openAt) / 1e9 > 0.8;
            int bt = hv ? 0xFF000000 | col : 0xFF000000 | darken(col, 0.55f);
            c.fill(10, (int) ch - 26, (int) cw - 10, (int) ch - 8, 0xFF000000);
            c.fillGradient(11, (int) ch - 25, (int) cw - 11, (int) ch - 9, bt, 0xFF000000 | darken(col, 0.3f));
            text(c, ready ? "ВЫБРАТЬ" : "…", cw / 2f, ch - 21, 0.9f, 0xFFFFFFFF, true);
            m.pop();
        }
        text(c, "Esc — подумать ещё (выбор можно сделать позже у Режиссёра)", DW / 2f, DH - 12, 0.6f, 0xFF8A84A8, true);
        m.pop();
        super.render(c, mouseX, mouseY, delta);
    }

    private void text(DrawContext c, String s, float x, float y, float scale, int argb, boolean center) {
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(center ? x - textRenderer.getWidth(s) * scale / 2f : x, y, 0);
        m.scale(scale, scale, 1f);
        c.drawText(textRenderer, s, 0, 0, argb, true);
        m.pop();
    }

    private static int hsv(float h) {
        float r = Math.abs(h * 6 - 3) - 1, g = 2 - Math.abs(h * 6 - 2), b = 2 - Math.abs(h * 6 - 4);
        return ((int) (Math.max(0, Math.min(1, r)) * 200 + 55) << 16) | ((int) (Math.max(0, Math.min(1, g)) * 200 + 55) << 8)
            | (int) (Math.max(0, Math.min(1, b)) * 200 + 55);
    }

    private static int darken(int rgb, float k) {
        return ((int) ((rgb >> 16 & 255) * k) << 16) | ((int) ((rgb >> 8 & 255) * k) << 8) | (int) ((rgb & 255) * k);
    }
}
