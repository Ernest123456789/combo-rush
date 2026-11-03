package com.derevna230.comborush.client;

import com.derevna230.comborush.hud.ShopActionPayload;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
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
 * «Вселенные»: экран миров, где продолжается история. Сейчас одна вселенная — «Ночной город».
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

    /** Состояние от сервера: вход открыт (пролог пройден), игрок уже внутри */
    private static volatile boolean open = false, inside = false;
    private static volatile String serverMsg = "";

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
        inside = isCityClient();
        if (ClientPlayNetworking.canSend(ShopActionPayload.ID)) ClientPlayNetworking.send(new ShopActionPayload("univ_state", 0, 0, ""));
    }

    private static boolean isCityClient() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.world != null && mc.world.getRegistryKey().getValue().getPath().equals("night_city");
    }

    public static void onPacket(JsonObject o) {
        open = o.has("open") && o.get("open").getAsBoolean();
        inside = o.has("inside") ? o.get("inside").getAsBoolean() : isCityClient();
        serverMsg = o.has("msg") ? o.get("msg").getAsString() : "";
    }

    private void act(String action) {
        if (ClientPlayNetworking.canSend(ShopActionPayload.ID)) ClientPlayNetworking.send(new ShopActionPayload(action, 0, 0, ""));
        close();
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
        text(c, "НОЧНОЙ ГОРОД", tx, CY + 12, 1.35f, 0xFF7AF0FF, 0);
        text(c, "Вселенная I · продолжение сюжета", tx, CY + 29, 0.78f, 0xFFB9A8F0, 0);
        c.fill(tx, CY + 41, tx + 150, CY + 42, 0x40FFFFFF);
        String[] lines = {
            "Город, где всегда ночь, а небоскрёбы",
            "светят ярче звёзд. За чертой города —",
            "холмы, горы и гоночные трассы.",
            "Цикл событий идёт и здесь; питомцев",
            "и машины можно взять с собой.",
            "Выход открыт всегда."};
        for (int i = 0; i < lines.length; i++) text(c, lines[i], tx, CY + 48 + i * 10.5f, 0.78f, 0xFFD6D0EE, 0);

        // статус
        boolean here = inside;
        String badge = here ? "ВЫ ЗДЕСЬ" : open ? "ОТКРЫТО" : "ЗАКРЫТО";
        int top = here ? 0x3CD8E8 : open ? 0x58D68D : 0xE05A5A, bot = here ? 0x1890A8 : open ? 0x2E8B57 : 0xA02A2A;
        float bw = 62, bh = 14, bx = CX + CW - bw - 8, by = CY + 8;
        float bp = 0.5f + 0.5f * (float) Math.sin(now / 5e8);
        c.fill((int) bx - 1, (int) by - 1, (int) (bx + bw) + 1, (int) (by + bh) + 1, 0xFF000000);
        c.fillGradient((int) bx, (int) by, (int) (bx + bw), (int) (by + bh), 0xFF000000 | top, 0xFF000000 | bot);
        c.fill((int) bx, (int) by, (int) (bx + bw), (int) by + 1, 0x80FFFFFF);
        c.fill((int) bx, (int) by, (int) (bx + bw), (int) (by + bh), ((int) (30 * bp) << 24) | 0xFFFFFF);
        text(c, badge, bx + bw / 2f, by + 3.2f, 0.9f, 0xFFFFFFFF, 1);

        // кнопка действия; хит карточки добавляем раньше, чтобы кнопка была выше по приоритету
        hit(CX, CY, CW, CH, "card", null);
        button(c, "enter", tx, CY + CH - 26, 150, 18, here ? "Вернуться домой" : open ? "Войти в город" : "Откроется после пролога");
        if (!serverMsg.isEmpty() && !open) text(c, "Пройдите все задания пролога", tx + 75, CY + CH - 6, 0.7f, 0xFFB9A8F0, 1);
        m.pop();
    }

    /** Неоновый горизонт: звёзды, зарево, башни со светящимися окнами и мигающими маячками, отражение внизу */
    private void drawPortal(DrawContext c, long now) {
        c.fill(PX - 1, PY - 1, PX + PW + 1, PY + PH + 1, 0xFF000000);
        c.fillGradient(PX, PY, PX + PW, PY + PH, 0xFF0B0620, 0xFF3A1060);
        double t = now / 1e9;
        // звёзды
        for (int i = 0; i < 46; i++) {
            int h = i * 9973 + 7;
            int x = PX + 2 + Math.abs(h * 31) % (PW - 4), y = PY + 2 + Math.abs(h * 17 + i * 53) % (PH / 2);
            float tw = 0.5f + 0.5f * (float) Math.sin(t * 1.4 + i);
            c.fill(x, y, x + 1, y + 1, ((int) (50 + 120 * tw) << 24) | 0xFFFFFF);
        }
        int ground = PY + PH - 16;
        // зарево над городом
        for (int i = 0; i < 6; i++) c.fill(PX, ground - 8 - i * 7, PX + PW, ground - i * 7, ((14 - i * 2) << 24) | 0xFF40D0);
        // башни
        int[] cols = {0x28E8F0, 0xFF3CD8, 0x7CFF50, 0xFFA030, 0x4C8CFF, 0xB46BFF};
        int n = 11;
        for (int i = 0; i < n; i++) {
            int h = i * 7919 + 31;
            boolean main = i == n / 2;
            int bwid = main ? 16 : 8 + Math.abs(h) % 7;
            int x0 = PX + 2 + i * (PW - 4) / n + (main ? -4 : 0);
            int hh = main ? 78 : 22 + Math.abs(h * 3) % 40;
            int col = cols[Math.abs(h >> 3) % cols.length];
            int y0 = ground - hh;
            int xe = Math.min(PX + PW - 1, x0 + bwid);
            c.fill(x0, y0, xe, ground, 0xFF0C0818);
            c.fill(x0, y0, xe, y0 + 1, 0xFF000000 | col);            // подсвеченная крыша
            c.fill(x0, y0, x0 + 1, ground, 0x66000000 | col);         // неон по краю
            c.fill(xe - 1, y0, xe, ground, 0x40000000 | col);
            // окна
            for (int wy = y0 + 3; wy < ground - 2; wy += 3)
                for (int wx = x0 + 2; wx < xe - 2; wx += 3) {
                    int wh = wx * 31 + wy * 17 + i * 101;
                    float f = 0.5f + 0.5f * (float) Math.sin(t * (0.6 + (Math.abs(wh) % 7) * 0.15) + wh);
                    if (Math.abs(wh) % 5 < 3) c.fill(wx, wy, wx + 1, wy + 2, ((int) (60 + 150 * f) << 24) | (Math.abs(wh) % 4 == 0 ? 0xFFE6A0 : col));
                }
            if (main) {                                                // шпиль и маячок
                c.fill(x0 + bwid / 2, y0 - 12, x0 + bwid / 2 + 1, y0, 0xFF9090A0);
                float bl = 0.5f + 0.5f * (float) Math.sin(t * 3);
                c.fill(x0 + bwid / 2 - 1, y0 - 14, x0 + bwid / 2 + 2, y0 - 12, ((int) (90 + 165 * bl) << 24) | 0xFF2020);
                c.fill(x0 - 2, y0 + 18, xe + 2, y0 + 20, 0xCC000000 | col);   // обзорное кольцо
            }
        }
        // улица с неоновой разметкой и отражением
        c.fillGradient(PX, ground, PX + PW, PY + PH, 0xFF14102A, 0xFF05030F);
        for (int x = PX + ((int) (t * 12) % 10); x < PX + PW; x += 10) c.fill(x, ground + 7, x + 5, ground + 8, 0xB0FFE6A0);
        for (int i = 0; i < 5; i++) c.fill(PX, ground + 1 + i, PX + PW, ground + 2 + i, ((26 - i * 5) << 24) | 0x28E8F0);
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
        boolean enabled = key.equals("close") || (key.equals("enter") && (inside || open));
        boolean accent = key.equals("enter") && enabled;
        float ha = hov.getOrDefault(key, 0f);
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(x + w / 2f, y + h / 2f, 0);
        m.scale(1f + 0.04f * ha, 1f + 0.04f * ha, 1f);
        m.translate(-w / 2f, -h / 2f, 0);
        int iw = (int) w, ih = (int) h;
        int top = accent ? (inside ? 0xE0883C : 0x28C8E8) : enabled ? 0x7A5CFF : 0x5A5A66;
        int bot = accent ? (inside ? 0xA04C1C : 0x1470B8) : enabled ? 0x4A2CC0 : 0x2E2E36;
        c.fill(-1, -1, iw + 1, ih + 3, 0xFF000000);
        c.fillGradient(0, 0, iw, ih, 0xFF000000 | top, 0xFF000000 | bot);
        c.fill(1, 1, iw - 1, 2, 0x55FFFFFF);
        if (ha > 0.05f) c.fill(0, 0, iw, ih, ((int) (40 * ha) << 24) | 0xFFFFFF);
        text(c, label, w / 2f, (h - 9f) / 2f, 1.0f, enabled ? 0xFFFFFFFF : 0xFF9A9AA6, 1);
        m.pop();
        Runnable click = key.equals("close") ? this::close : () -> act(inside ? "univ_leave" : "univ_enter");
        hit(x, y, w, h + 2, key, enabled ? click : () -> HudModel.play(SoundEvents.ENTITY_VILLAGER_NO, 1.2f, 0.3f));
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
