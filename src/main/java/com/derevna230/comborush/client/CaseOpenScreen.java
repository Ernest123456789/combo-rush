package com.derevna230.comborush.client;

import com.derevna230.comborush.hud.ShopActionPayload;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Открытие кейса в стиле CS: лента предметов прокручивается и замедляется, останавливаясь на награде.
 * Награду сервер уже выдал — экран её только показывает. Клик или пробел — пропустить прокрутку.
 */
public class CaseOpenScreen extends Screen {
    private static final int DW = 420, DH = 250;
    private static final float CARD = 46f;              // ширина карточки с промежутком
    private static final float SPIN_MS = 6200f;

    private static final class Entry { ItemStack stack = ItemStack.EMPTY; String rar = "common"; }
    private static final class Spark { float x, y, vx, vy; int rgb; long born; }

    private final List<Entry> items = new ArrayList<>();
    private final String caseId, caseName, caseRar, rewardName, rewardRar, setName;
    private final int win, left;
    private final float off;
    private final long start;
    private long revealAt = -1, lastNs = 0;
    private int lastTick = -1;
    private float u = 1, ox, oy;
    private final List<Spark> sparks = new ArrayList<>();
    private float hovAgain = 0, hovClose = 0;

    private CaseOpenScreen(JsonObject o) {
        super(Text.literal("Открытие кейса"));
        caseId = str(o, "case"); caseName = str(o, "caseName"); caseRar = str(o, "caseRar");
        rewardName = str(o, "name"); rewardRar = str(o, "rar"); setName = str(o, "set");
        win = o.has("win") ? o.get("win").getAsInt() : 0;
        off = o.has("off") ? o.get("off").getAsFloat() : 0.5f;
        left = o.has("left") ? o.get("left").getAsInt() : 0;
        for (JsonElement el : o.getAsJsonArray("items")) {
            JsonObject j = el.getAsJsonObject();
            Entry e = new Entry();
            Identifier id = Identifier.tryParse(str(j, "id"));
            e.stack = id != null && Registries.ITEM.containsId(id) ? new ItemStack(Registries.ITEM.get(id)) : ItemStack.EMPTY;
            e.rar = str(j, "rar");
            items.add(e);
        }
        start = System.nanoTime();
    }

    private static String str(JsonObject o, String k) { return o.has(k) ? o.get(k).getAsString() : ""; }

    public static void open(JsonObject o) { MinecraftClient.getInstance().setScreen(new CaseOpenScreen(o)); }

    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) { }

    private boolean revealed() { return revealAt >= 0; }

    private void skip() {
        if (!revealed()) reveal(System.nanoTime());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 32 || keyCode == 257) && !revealed()) { skip(); return true; }        // пробел / Enter
        if (keyCode == 257 && revealed() && left > 0) { again(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!revealed()) { skip(); return true; }
        float px = (float) ((mouseX - ox) / u), py = (float) ((mouseY - oy) / u);
        if (left > 0 && inside(px, py, 150, 214, 110, 20)) { again(); return true; }
        close();                                    // любой другой клик после показа награды закрывает экран
        return true;
    }

    private static boolean inside(float px, float py, float x, float y, float w, float h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    private void again() { ClientPlayNetworking.send(new ShopActionPayload("case_again", 0, 0, caseId)); }

    // ================= прокрутка =================
    /** Позиция ленты (в карточках) в момент t ∈ [0..1]: сильное замедление в конце, как в CS */
    private float position(float t) {
        float target = win + off;
        float e = 1f - (float) Math.pow(1f - t, 4.2);
        return e * target;
    }

    private void reveal(long now) {
        revealAt = now;
        Random r = new Random();
        int col = rarRgb(rewardRar);
        for (int i = 0; i < 70; i++) {
            Spark s = new Spark();
            double ang = r.nextDouble() * Math.PI * 2;
            float sp = 50f + r.nextFloat() * 120f;
            s.x = 210; s.y = 100;
            s.vx = (float) Math.cos(ang) * sp; s.vy = (float) Math.sin(ang) * sp - 50f;
            s.rgb = r.nextInt(3) == 0 ? 0xFFFFFF : col;
            s.born = now;
            sparks.add(s);
        }
        switch (rewardRar) {
            case "legendary" -> { HudModel.play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.7f); HudModel.play(SoundEvents.ENTITY_PLAYER_LEVELUP, 0.8f, 0.6f); }
            case "epic" -> { HudModel.play(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.1f, 0.6f); HudModel.play(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 0.8f); }
            case "rare" -> HudModel.play(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.4f, 0.5f);
            default -> HudModel.play(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.6f);
        }
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        u = Math.max(0.5f, Math.min(width / (float) DW, height / (float) DH));
        ox = (width - DW * u) / 2f; oy = (height - DH * u) / 2f;
        float mx = (mouseX - ox) / u, my = (mouseY - oy) / u;

        float t = Math.min(1f, (now - start) / 1e6f / SPIN_MS);
        if (!revealed() && t >= 1f) reveal(now);
        float pos = revealed() ? win + off : position(t);

        // щелчок на каждой новой карточке, проходящей мимо указателя
        int tick = (int) Math.floor(pos);
        if (!revealed() && tick != lastTick) {
            lastTick = tick;
            HudModel.play(SoundEvents.BLOCK_NOTE_BLOCK_HAT, 1.6f + 0.3f * t, 0.5f);
        }

        // фон
        c.fill(0, 0, width, height, 0xE0080510);
        float pulse = 0.85f + 0.15f * (float) Math.sin(now / 6e8);
        int glow = revealed() ? rarRgb(rewardRar) : rarRgb(caseRar);
        for (int i = 0; i < 9; i++) {
            int rad = (int) ((30 + i * 22) * u * pulse);
            disc(c, width / 2, (int) (oy + 100 * u), rad, ((5 + i) << 24) | (glow & 0xFFFFFF));
        }

        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(ox, oy, 0);
        m.scale(u, u, 1f);

        text(c, caseName.toUpperCase(), 210, 10, 1.4f, 0xFF000000 | rarRgb(caseRar), 1);
        text(c, revealed() ? "" : "Клик или пробел — пропустить", 210, 24, 0.6f, 0x88FFFFFF, 1);

        // лента
        float stripY = 70, stripH = 62, viewL = 20, viewR = 400;
        c.fill((int) viewL - 2, (int) stripY - 2, (int) viewR + 2, (int) (stripY + stripH + 2), 0xFF000000);
        c.fill((int) viewL, (int) stripY, (int) viewR, (int) (stripY + stripH), 0xFF15101F);
        c.enableScissor((int) (ox + viewL * u), (int) (oy + stripY * u), (int) (ox + viewR * u), (int) (oy + (stripY + stripH) * u));
        float center = 210;
        int first = Math.max(0, (int) Math.floor(pos - 6)), last = Math.min(items.size() - 1, (int) Math.ceil(pos + 6));
        for (int i = first; i <= last; i++) {
            float x = center + (i - pos) * CARD;
            Entry e = items.get(i);
            boolean isWin = revealed() && i == win;
            float rt = isWin ? Math.min(1f, (now - revealAt) / 1e6f / 400f) : 0f;
            float sc = 1f + (isWin ? 0.12f * (float) Math.sin(Math.PI * Math.min(1f, rt)) + 0.04f : 0f);
            m.push();
            m.translate(x, stripY + stripH / 2f, 0);
            m.scale(sc, sc, 1f);
            drawCard(c, e, now, isWin);
            m.pop();
        }
        c.disableScissor();
        // затемнение краёв ленты
        for (int i = 0; i < 40; i++) {
            int a = (int) (200 * Math.pow(1 - i / 40f, 2));
            c.fill((int) viewL + i, (int) stripY, (int) viewL + i + 1, (int) (stripY + stripH), a << 24);
            c.fill((int) viewR - i - 1, (int) stripY, (int) viewR - i, (int) (stripY + stripH), a << 24);
        }
        // указатель
        int pc = revealed() ? 0xFF000000 | rarRgb(rewardRar) : 0xFFFFE066;
        c.fill((int) center - 1, (int) stripY - 8, (int) center + 1, (int) (stripY + stripH + 8), pc);
        triangle(c, center, stripY - 8, true, pc);
        triangle(c, center, stripY + stripH + 8, false, pc);

        // результат
        if (revealed()) {
            float rt = Math.min(1f, (now - revealAt) / 1e6f / 500f);
            float pop = 1f + 0.5f * (float) Math.exp(-rt * 4) * (float) Math.cos(rt * 14);
            m.push();
            m.translate(210, 158, 0);
            m.scale(pop, pop, 1f);
            text(c, rarLabel(rewardRar), 0, -12, 0.75f, 0xFF000000 | rarRgb(rewardRar), 1);
            text(c, rewardName, 0, -2, 1.5f, 0xFFFFFFFF, 1);
            if (!setName.isEmpty()) text(c, "Комплект: " + setName, 0, 13, 0.75f, 0xFFB8B8C8, 1);
            m.pop();
            text(c, "Предмет уже в твоём инвентаре", 210, 190, 0.65f, 0xFF8EF0A0, 1);

            float tgt1 = left > 0 && inside(mx, my, 150, 214, 110, 20) ? 1f : 0f;
            float tgt2 = inside(mx, my, left > 0 ? 270 : 160, 214, 100, 20) ? 1f : 0f;
            hovAgain += (tgt1 - hovAgain) * Math.min(1f, dt * 14f);
            hovClose += (tgt2 - hovClose) * Math.min(1f, dt * 14f);
            if (left > 0) button(c, 150, 214, 110, 20, "ЕЩЁ (" + left + ")", 0x6FE08A, 0x1E8A4A, hovAgain);
            button(c, left > 0 ? 270 : 160, 214, 100, 20, "ЗАКРЫТЬ", 0xFF5A4A, 0xC02828, hovClose);
        }

        // искры
        m.push();
        m.translate(0, 0, 300);
        sparks.removeIf(s -> (now - s.born) / 1e6 > 1300);
        for (Spark s : sparks) {
            s.x += s.vx * dt; s.y += s.vy * dt; s.vy += 150f * dt; s.vx *= 0.985f;
            float st = (now - s.born) / 1e6f / 1300f;
            int a = (int) (255 * (1 - st));
            c.fill((int) s.x, (int) s.y, (int) s.x + 2, (int) s.y + 2, (a << 24) | (s.rgb & 0xFFFFFF));
        }
        m.pop();
        m.pop();
    }

    private void drawCard(DrawContext c, Entry e, long now, boolean win) {
        int w = 42, h = 56;
        int col = rarRgb(e.rar);
        c.fill(-w / 2 - 1, -h / 2 - 1, w / 2 + 1, h / 2 + 1, win ? 0xFFFFFFFF : 0xFF000000);
        c.fillGradient(-w / 2, -h / 2, w / 2, h / 2, 0xFF2A2236, 0xFF120E1A);
        // полоса редкости снизу и подсветка
        c.fillGradient(-w / 2, h / 2 - 22, w / 2, h / 2, 0x00000000, (0x70 << 24) | (col & 0xFFFFFF));
        c.fill(-w / 2, h / 2 - 3, w / 2, h / 2, 0xFF000000 | col);
        if (!e.stack.isEmpty()) {
            MatrixStack m = c.getMatrices();
            m.push();
            m.translate(0, -5, 50);
            m.scale(2.0f, 2.0f, 1f);
            m.translate(-8, -8, 0);
            c.drawItem(e.stack, 0, 0);
            m.pop();
        }
        if (win) {
            float p = 0.5f + 0.5f * (float) Math.sin(now / 1.5e8);
            c.fill(-w / 2, -h / 2, w / 2, h / 2, ((int) (40 * p) << 24) | (col & 0xFFFFFF));
        }
    }

    private void triangle(DrawContext c, float cx, float y, boolean down, int col) {
        for (int i = 0; i < 6; i++) {
            int yy = (int) (down ? y + i : y - i);
            c.fill((int) cx - (6 - i), yy, (int) cx + (6 - i), yy + 1, col);
        }
    }

    private void button(DrawContext c, float x, float y, float w, float h, String label, int top, int bot, float ha) {
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(x + w / 2f, y + h / 2f, 0);
        m.scale(1f + 0.05f * ha, 1f + 0.05f * ha, 1f);
        m.translate(-w / 2f, -h / 2f, 0);
        int iw = (int) w, ih = (int) h;
        c.fill(-1, -1, iw + 1, ih + 3, 0xFF000000);
        c.fill(0, ih - 1, iw, ih + 2, 0xFF000000 | (bot >> 1 & 0x7F7F7F));
        c.fillGradient(0, 0, iw, ih, 0xFF000000 | top, 0xFF000000 | bot);
        c.fill(1, 1, iw - 1, 2, 0x66FFFFFF);
        if (ha > 0.05f) c.fill(0, 0, iw, ih, ((int) (40 * ha) << 24) | 0xFFFFFF);
        text(c, label, w / 2f, (h - 8f) / 2f, 0.95f, 0xFFFFFFFF, 1);
        m.pop();
    }

    @Override
    public void close() { MinecraftClient.getInstance().setScreen(null); }

    private void disc(DrawContext c, int cx, int cy, int r, int argb) {
        for (int dy = -r; dy <= r; dy += 2) {
            int hw = (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
            c.fill(cx - hw, cy + dy, cx + hw, cy + dy + 2, argb);
        }
    }

    private static int rarRgb(String r) { return EssenceShopScreen.rarColor(r) & 0xFFFFFF; }
    private static String rarLabel(String r) { return EssenceShopScreen.rarLabel(r); }

    private void text(DrawContext c, String s, float x, float y, float scale, int argb, int align) {
        if (s == null || s.isEmpty()) return;
        float w = textRenderer.getWidth(s) * scale;
        float px = align == 1 ? x - w / 2f : align == 2 ? x - w : x;
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(px, y, 0);
        m.scale(scale, scale, 1f);
        c.drawText(textRenderer, s, 0, 0, argb, ((argb >> 16 & 255) * 3 + (argb >> 8 & 255) * 6 + (argb & 255)) > 700);   // тёмный текст без тени — иначе «грязный»
        m.pop();
    }
}
