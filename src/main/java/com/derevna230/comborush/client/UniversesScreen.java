package com.derevna230.comborush.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * «Вселенные»: экран миров, где продолжается история. Пока одна вселенная — с пометкой «скоро».
 * Открывается кнопкой в инвентаре. Весь экран рисуется в условных 420×250 и масштабируется под окно.
 */
public class UniversesScreen extends Screen {
    private static final int DW = 420, DH = 250;
    private static final int CX = 56, CY = 52, CW = 308, CH = 150;      // карточка вселенной
    private static final int PX = CX + 8, PY = CY + 8, PW = 124, PH = CH - 16;   // окно портала

    private static final class Hit {
        float x, y, w, h; String key; Runnable click;
        boolean contains(float px, float py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }

    private float u = 1, ox, oy, mx, my;
    private long lastNs = 0, openAt;
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, Float> hov = new HashMap<>();
    private String hoverKey = "";

    public UniversesScreen() {
        super(Text.literal("Вселенные"));
        this.openAt = System.nanoTime();
    }

    @Override
    protected void init() {
        HudModel.play(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 0.6f);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) { }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        float px = (float) ((mouseX - ox) / u), py = (float) ((mouseY - oy) / u);
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.contains(px, py)) { if (h.click != null) h.click.run(); return true; }
        }
        return true;
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        u = Math.max(0.5f, Math.min(width / (float) DW, height / (float) DH));
        ox = (width - DW * u) / 2f; oy = (height - DH * u) / 2f;
        mx = (mouseX - ox) / u; my = (mouseY - oy) / u;

        hoverKey = "";
        for (int i = hits.size() - 1; i >= 0; i--) if (hits.get(i).contains(mx, my)) { hoverKey = hits.get(i).key; break; }
        for (String k : List.of("card", "enter", "close")) {
            float cur = hov.getOrDefault(k, 0f);
            hov.put(k, cur + ((k.equals(hoverKey) ? 1f : 0f) - cur) * Math.min(1f, dt * 12f));
        }

        hits.clear();
        drawBackground(c, now);
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(ox, oy, 0);
        m.scale(u, u, 1f);
        float appear = Math.min(1f, (now - openAt) / 1e6f / 450f);
        drawTitle(c, appear);
        drawCard(c, now, appear);
        drawClose(c);
        m.pop();
    }

    // ================= фон =================
    private void drawBackground(DrawContext c, long now) {
        c.fillGradient(0, 0, width, height, 0xFF160B3A, 0xFF05030F);
        for (int y = 0; y < height; y += 4) c.fill(0, y, width, y + 1, 0x08FFFFFF);
        for (int i = 0; i < 260; i++) {
            int h = i * 7919 + 13;
            int x = Math.abs(h * 31) % Math.max(1, width), y = Math.abs(h * 17 + i * 101) % Math.max(1, height);
            float tw = 0.5f + 0.5f * (float) Math.sin(now / 1.1e9 + i);
            int a = (int) (30 + 60 * tw);
            c.fill(x, y, x + 1, y + 1, (a << 24) | (i % 3 == 0 ? 0xFFE9A0 : i % 3 == 1 ? 0x9AD8FF : 0xFFFFFF));
        }
        // медленные туманности
        float pulse = 0.85f + 0.15f * (float) Math.sin(now / 9e8);
        disc(c, (int) (width * 0.22), (int) (height * 0.3), (int) (70 * u * pulse), 0x0A7A3CFF);
        disc(c, (int) (width * 0.22), (int) (height * 0.3), (int) (40 * u * pulse), 0x0C9A5CFF);
        disc(c, (int) (width * 0.8), (int) (height * 0.72), (int) (80 * u * pulse), 0x0A2A8CFF);
        disc(c, (int) (width * 0.8), (int) (height * 0.72), (int) (46 * u * pulse), 0x0C3AA0FF);
        int vw = Math.max(8, width / 14);
        for (int i = 0; i < vw; i++) {
            int a = (int) (150 * Math.pow(1 - i / (float) vw, 2));
            c.fill(i, 0, i + 1, height, a << 24);
            c.fill(width - i - 1, 0, width - i, height, a << 24);
        }
        c.fillGradient(0, height - height / 6, width, height, 0x00000000, 0xCC000000);
    }

    private void disc(DrawContext c, int cx, int cy, int r, int argb) {
        for (int dy = -r; dy <= r; dy += 2) {
            int hw = (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
            c.fill(cx - hw, cy + dy, cx + hw, cy + dy + 2, argb);
        }
    }

    // ================= заголовок =================
    private void drawTitle(DrawContext c, float appear) {
        float slide = (1f - appear) * -8f;
        text(c, "ВСЕЛЕННЫЕ", 210, 12 + slide, 2.2f, argb(0xFFE08A, appear), 1);
        text(c, "Миры, где продолжается история", 210, 34 + slide, 0.85f, argb(0xB9A8F0, appear), 1);
        int w = (int) (110 * appear);
        c.fill(210 - w, 46, 210 + w, 47, argb(0xFFE08A, 0.35f * appear));
    }

    // ================= карточка вселенной =================
    private void drawCard(DrawContext c, long now, float appear) {
        float ha = hov.getOrDefault("card", 0f);
        float rise = (1f - appear) * 14f - ha * 1.5f;
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(0, rise, 0);

        // рамка и тело
        int glow = (int) (20 + 30 * ha + 10 * Math.sin(now / 7e8));
        c.fill(CX - 3, CY - 3, CX + CW + 3, CY + CH + 3, (glow << 24) | 0xB070FF);
        panel(c, CX, CY, CW, CH);

        drawPortal(c, now);

        // текст справа
        int tx = PX + PW + 14;
        text(c, "ВСЕЛЕННАЯ I", tx, CY + 12, 1.5f, 0xFFFFE08A, 0);
        text(c, "Продолжение сюжета", tx, CY + 30, 0.85f, 0xFFB9A8F0, 0);
        c.fill(tx, CY + 42, tx + 140, CY + 43, 0x40FFFFFF);
        String[] lines = {
            "Новый мир за пределами пролога.",
            "Здесь история пойдёт дальше.",
            "Цикл событий идёт и там, с вами",
            "можно взять питомцев и машины.",
            "Выход — в любой момент."};
        for (int i = 0; i < lines.length; i++) text(c, lines[i], tx, CY + 50 + i * 10.5f, 0.78f, 0xFFD6D0EE, 0);

        // плашка «СКОРО»
        float bw = 66, bh = 14, bx = CX + CW - bw - 8, by = CY + 8;
        float bp = 0.5f + 0.5f * (float) Math.sin(now / 5e8);
        c.fill((int) bx - 1, (int) by - 1, (int) (bx + bw) + 1, (int) (by + bh) + 1, 0xFF000000);
        c.fillGradient((int) bx, (int) by, (int) (bx + bw), (int) (by + bh), 0xFFFFC857, 0xFFE0892B);
        c.fill((int) bx, (int) by, (int) (bx + bw), (int) by + 1, 0x80FFFFFF);
        c.fill((int) bx, (int) by, (int) (bx + bw), (int) (by + bh), ((int) (30 * bp) << 24) | 0xFFFFFF);
        text(c, "СКОРО", bx + bw / 2f, by + 3.2f, 1.0f, 0xFF3A2208, 1);

        // кнопка (пока недоступна); хит карточки добавляем раньше, чтобы кнопка была выше по приоритету
        hit(CX, CY, CW, CH, "card", null);
        button(c, "enter", tx, CY + CH - 26, 150, 18, "Войти — скоро");
        m.pop();
    }

    /** Мерцающий портал: вращающиеся кольца из точек, втягивающая спираль и пульсирующее ядро */
    private void drawPortal(DrawContext c, long now) {
        c.fill(PX - 1, PY - 1, PX + PW + 1, PY + PH + 1, 0xFF000000);
        c.fillGradient(PX, PY, PX + PW, PY + PH, 0xFF1A0E3F, 0xFF06030F);
        int cx = PX + PW / 2, cy = PY + PH / 2;
        double t = now / 1e9;

        // звёзды внутри
        for (int i = 0; i < 40; i++) {
            int h = i * 9973 + 7;
            int x = PX + 2 + Math.abs(h * 31) % (PW - 4), y = PY + 2 + Math.abs(h * 17 + i * 53) % (PH - 4);
            float tw = 0.5f + 0.5f * (float) Math.sin(t * 1.4 + i);
            c.fill(x, y, x + 1, y + 1, ((int) (40 + 100 * tw) << 24) | 0xFFFFFF);
        }
        // свечение ядра
        float pulse = 0.85f + 0.15f * (float) Math.sin(t * 2.2);
        for (int i = 0; i < 7; i++) {
            int r = (int) ((10 + i * 7) * pulse);
            discClip(c, cx, cy, r, ((12 + (6 - i) * 7) << 24) | (i % 2 == 0 ? 0xA050FF : 0x50B0FF));
        }
        // кольца
        int[] radii = {16, 26, 37, 48};
        int[] cols = {0xFFE08A, 0xE49BFF, 0x8AD0FF, 0xB89CFF};
        for (int k = 0; k < radii.length; k++) {
            int n = 10 + radii[k] / 2;
            double dir = k % 2 == 0 ? 1 : -1;
            double ph = t * (0.6 + 0.25 * k) * dir;
            for (int i = 0; i < n; i++) {
                double a = ph + i * Math.PI * 2 / n;
                double sq = 0.82;                                      // слегка приплюснутые кольца: объём
                int x = cx + (int) Math.round(Math.cos(a) * radii[k]);
                int y = cy + (int) Math.round(Math.sin(a) * radii[k] * sq);
                if (x < PX + 1 || x > PX + PW - 3 || y < PY + 1 || y > PY + PH - 3) continue;
                float f = 0.5f + 0.5f * (float) Math.sin(a * 2 + t * 3);
                c.fill(x, y, x + 2, y + 2, ((int) (70 + 150 * f) << 24) | cols[k]);
            }
        }
        // спираль
        for (int i = 0; i < 46; i++) {
            double r = 4 + i * 1.05;
            double a = -t * 1.6 + i * 0.28;
            int x = cx + (int) Math.round(Math.cos(a) * r * 0.9);
            int y = cy + (int) Math.round(Math.sin(a) * r * 0.74);
            if (x < PX + 1 || x > PX + PW - 3 || y < PY + 1 || y > PY + PH - 3) continue;
            c.fill(x, y, x + 1, y + 1, ((int) (230 * (1 - i / 46f)) << 24) | 0xFFFFFF);
        }
        // знак вопроса в центре
        float q = 0.6f + 0.4f * (float) Math.sin(t * 2.2 + 1);
        text(c, "?", cx, cy - 9f, 2.0f, argb(0xFFFFFF, q), 1);
        // тонкая рамка
        c.fill(PX, PY, PX + PW, PY + 1, 0x40FFFFFF);
    }

    /** Круг, обрезанный окном портала */
    private void discClip(DrawContext c, int cx, int cy, int r, int argb) {
        for (int dy = -r; dy <= r; dy += 2) {
            int hw = (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
            int y0 = cy + dy, y1 = y0 + 2;
            if (y1 <= PY || y0 >= PY + PH) continue;
            int x0 = Math.max(PX, cx - hw), x1 = Math.min(PX + PW, cx + hw);
            if (x1 > x0) c.fill(x0, Math.max(PY, y0), x1, Math.min(PY + PH, y1), argb);
        }
    }

    // ================= закрыть =================
    private void drawClose(DrawContext c) {
        button(c, "close", 170, 222, 80, 16, "Закрыть");
    }

    // ================= элементы =================
    private void panel(DrawContext c, int x, int y, int w, int h) {
        c.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        c.fill(x, y, x + w, y + h, 0xD2140A28);
        c.fill(x, y, x + w, y + 1, 0x30FFFFFF);
    }

    private void button(DrawContext c, String key, float x, float y, float w, float h, String label) {
        boolean enabled = key.equals("close");
        float ha = hov.getOrDefault(key, 0f);
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(x + w / 2f, y + h / 2f, 0);
        m.scale(1f + 0.04f * ha, 1f + 0.04f * ha, 1f);
        m.translate(-w / 2f, -h / 2f, 0);
        int iw = (int) w, ih = (int) h;
        int top = enabled ? 0x7A5CFF : 0x5A5A66, bot = enabled ? 0x4A2CC0 : 0x2E2E36;
        c.fill(-1, -1, iw + 1, ih + 3, 0xFF000000);
        c.fillGradient(0, 0, iw, ih, 0xFF000000 | top, 0xFF000000 | bot);
        c.fill(1, 1, iw - 1, 2, 0x55FFFFFF);
        if (ha > 0.05f) c.fill(0, 0, iw, ih, ((int) (40 * ha) << 24) | 0xFFFFFF);
        text(c, label, w / 2f, (h - 9f) / 2f, 1.0f, enabled ? 0xFFFFFFFF : 0xFF9A9AA6, 1);
        m.pop();
        hit(x, y, w, h + 2, key, enabled ? this::close : () -> HudModel.play(SoundEvents.ENTITY_VILLAGER_NO, 1.2f, 0.3f));
    }

    private void hit(float x, float y, float w, float h, String key, Runnable click) {
        Hit h2 = new Hit();
        h2.x = x; h2.y = y; h2.w = w; h2.h = h; h2.key = key; h2.click = click;
        hits.add(h2);
    }

    private static int argb(int rgb, float alpha) { return (Math.max(0, Math.min(255, (int) (alpha * 255))) << 24) | (rgb & 0xFFFFFF); }

    private void text(DrawContext c, String s, float x, float y, float scale, int argb, int align) {
        if (s == null || s.isEmpty()) return;
        float w = textRenderer.getWidth(s) * scale;
        float px = align == 1 ? x - w / 2f : align == 2 ? x - w : x;
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(px, y, 0);
        m.scale(scale, scale, 1f);
        c.drawText(textRenderer, s, 0, 0, argb, true);
        m.pop();
    }

    /** Значок кнопки в инвентаре */
    public static ItemStack icon() { return new ItemStack(Items.ENDER_EYE); }
}
