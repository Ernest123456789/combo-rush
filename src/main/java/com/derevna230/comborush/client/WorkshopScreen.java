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
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Мастерская: сверху — 4 карманных станка за монеты, снизу — обмен алмазов и изумрудов на монеты */
public class WorkshopScreen extends Screen {
    private static final int DW = 440, DH = 262;
    private static final ItemStack COIN = new ItemStack(Items.GOLD_NUGGET);

    static final class Offer { String id = "", name = "", desc = ""; int price; boolean owned; ItemStack stack = ItemStack.EMPTY; }
    static final class Ex { String id = "", name = ""; int rate, have, blocks; ItemStack stack = ItemStack.EMPTY; }
    static final class Data { int coins; String msg = ""; boolean ok = true; List<Offer> offers = new ArrayList<>(); List<Ex> ex = new ArrayList<>(); }

    private Data d;
    private float u = 1, ox, oy;
    private final long openAt = System.nanoTime();
    private long toastAt = -10_000_000_000L, coinAt = -10_000_000_000L;
    private String toast = ""; private boolean toastOk = true;
    private final List<float[]> btn = new ArrayList<>();
    private final List<Runnable> act = new ArrayList<>();

    private WorkshopScreen(Data d) { super(Text.literal("Мастерская")); this.d = d; }

    public static void onPacket(String kind, JsonObject o) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Data n = parse(o);
        if (kind.equals("ws_open")) { mc.setScreen(new WorkshopScreen(n)); HudModel.play(SoundEvents.BLOCK_SMITHING_TABLE_USE, 1.2f, 0.4f); return; }
        if (mc.currentScreen instanceof WorkshopScreen s) s.apply(n);
    }

    private static ItemStack stack(String id) {
        Identifier i = Identifier.tryParse(id);
        return i != null && Registries.ITEM.containsId(i) ? new ItemStack(Registries.ITEM.get(i)) : ItemStack.EMPTY;
    }

    private static Data parse(JsonObject o) {
        Data d = new Data();
        d.coins = o.get("coins").getAsInt();
        d.msg = o.has("msg") ? o.get("msg").getAsString() : "";
        d.ok = !o.has("ok") || o.get("ok").getAsBoolean();
        for (JsonElement e : o.getAsJsonArray("offers")) {
            JsonObject j = e.getAsJsonObject();
            Offer f = new Offer();
            f.id = j.get("id").getAsString(); f.name = j.get("name").getAsString(); f.desc = j.get("desc").getAsString();
            f.price = j.get("price").getAsInt(); f.owned = j.get("owned").getAsBoolean(); f.stack = stack(j.get("item").getAsString());
            d.offers.add(f);
        }
        for (JsonElement e : o.getAsJsonArray("ex")) {
            JsonObject j = e.getAsJsonObject();
            Ex x = new Ex();
            x.id = j.get("id").getAsString(); x.name = j.get("name").getAsString(); x.rate = j.get("rate").getAsInt();
            x.have = j.get("have").getAsInt(); x.blocks = j.get("blocks").getAsInt(); x.stack = stack(j.get("item").getAsString());
            d.ex.add(x);
        }
        return d;
    }

    private void apply(Data n) {
        if (!n.msg.isEmpty()) {
            toast = n.msg; toastOk = n.ok; toastAt = System.nanoTime();
            if (n.ok) { HudModel.play(com.derevna230.comborush.sound.ModSounds.COIN, 1.1f, 0.6f); coinAt = toastAt; }
            else HudModel.play(SoundEvents.ENTITY_VILLAGER_NO, 1.0f, 0.6f);
        }
        d = n;
    }

    private static void send(String action, int a, String c) {
        ClientPlayNetworking.send(new ShopActionPayload(action, a, 0, c));
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) { }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float px = (float) ((mouseX - ox) / u), py = (float) ((mouseY - oy) / u);
        for (int i = 0; i < btn.size(); i++) {
            float[] b = btn.get(i);
            if (px >= b[0] && px < b[0] + b[2] && py >= b[1] && py < b[1] + b[3]) {
                HudModel.play(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.5f);
                act.get(i).run();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void hit(float x, float y, float w, float h, Runnable r) { btn.add(new float[]{x, y, w, h}); act.add(r); }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float t = (now - openAt) / 1e9f;
        u = Math.min(width / (float) DW, height / (float) DH) * 0.96f;
        ox = (width - DW * u) / 2f; oy = (height - DH * u) / 2f;
        float mx = (mouseX - ox) / u, my = (mouseY - oy) / u;
        btn.clear(); act.clear();

        // фон: тёмно-бирюзовое «сукно» мастерской
        c.fillGradient(0, 0, width, height, 0xF0102018, 0xF0081410);
        for (int i = 0; i < 50; i++) {
            float sx = (float) ((Math.sin(i * 12.9898) * 43758.5453) % 1 + 1) % 1 * width;
            float sy = (float) ((Math.sin(i * 78.233) * 12543.1) % 1 + 1) % 1 * height;
            c.fill((int) sx, (int) sy, (int) sx + 1, (int) sy + 1, StoryHud.argb(0xFFE6A0, 0.15f + 0.15f * (float) Math.abs(Math.sin(t + i))));
        }
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(ox, oy, 0);
        m.scale(u, u, 1f);

        // заголовок, монеты, закрыть
        String title = "МАСТЕРСКАЯ";
        float tw0 = textRenderer.getWidth(title) * 2.2f, tx = DW / 2f - tw0 / 2f;
        for (int i = 0; i < title.length(); i++) {
            String ch = String.valueOf(title.charAt(i));
            text(c, ch, tx, 6 + (float) Math.sin(t * 3 + i * 0.6) * 1.5f, 2.2f, 0xFFFFC94A, 0);
            tx += textRenderer.getWidth(ch) * 2.2f;
        }
        text(c, "Карманные станки (кнопки у инвентаря, навсегда) · обмен драгоценностей", DW / 2f, 28, 0.6f, 0xFFB8D0C0, 1);
        float pop = (float) Math.max(0, 1 - (now - coinAt) / 4e8);
        panel(c, DW - 96, 4, 90, 24);
        m.push(); m.translate(DW - 92, 8, 0); c.drawItem(COIN, 0, 0); m.pop();
        text(c, String.valueOf(d.coins), DW - 72, 10 - pop * 2, 1.3f + pop * 0.3f, 0xFFFFD34A, 0);
        boolean hc = in(mx, my, 6, 4, 70, 22);
        button(c, 6, 4, 70, 22, "ЗАКРЫТЬ", hc ? 0xFFFF6B6B : 0xFFE04848, 0xFF8A1C1C);
        hit(6, 4, 70, 22, this::close);

        // станки
        float cw = 101, ch = 112, gap = 6, x0 = (DW - (cw * 4 + gap * 3)) / 2f, y0 = 42;
        for (int i = 0; i < d.offers.size() && i < 4; i++) {
            Offer f = d.offers.get(i);
            float x = x0 + i * (cw + gap);
            boolean hv = in(mx, my, x, y0, cw, ch);
            m.push();
            m.translate(x, y0 + (hv ? -2 : 0), 0);
            card(c, (int) cw, (int) ch, hv, 0xFFC94A);
            m.push();
            m.translate(cw / 2f - 16, 8 + (float) Math.sin(t * 2 + i) * 1.5f, 0);
            m.scale(2f, 2f, 1f);
            c.drawItem(f.stack, 0, 0);
            m.pop();
            int ty = 44;
            for (String line : StoryHud.wrap(textRenderer, f.name, (int) ((cw - 8) / 0.75f))) { text(c, line, cw / 2f, ty, 0.75f, 0xFFFFFFFF, 1); ty += 8; }
            ty += 2;
            for (String line : StoryHud.wrap(textRenderer, f.desc, (int) ((cw - 10) / 0.6f))) { text(c, line, cw / 2f, ty, 0.6f, 0xFFB8D0C0, 1); ty += 7; }
            m.pop();
            boolean afford = !f.owned && d.coins >= f.price;
            float by = y0 + ch - 22;
            boolean hb = in(mx, my, x + 6, by, cw - 12, 16);
            int top = f.owned ? 0xFF4A6A5A : afford ? (hb ? 0xFF8CF0A6 : 0xFF6FE08A) : 0xFF8A6A6A;
            int bot = f.owned ? 0xFF2A3A30 : afford ? 0xFF1E8A4A : 0xFF4A2A2A;
            button(c, x + 6, by, cw - 12, 16, f.owned ? "ОТКРЫТО" : "КУПИТЬ · " + f.price, top, bot);
            if (!f.owned) { String id = f.id; hit(x + 6, by, cw - 12, 16, () -> send("ws_buy", 0, id)); }
        }

        // обмен
        float ew = 210, eh = 82, ey = 164, ex0 = (DW - ew * 2 - 10) / 2f;
        for (int i = 0; i < d.ex.size() && i < 2; i++) {
            Ex x = d.ex.get(i);
            float ex = ex0 + i * (ew + 10);
            int accent = x.id.equals("diamond") ? 0x5FE8E8 : 0x4AE07A;
            m.push();
            m.translate(ex, ey, 0);
            card(c, (int) ew, (int) eh, in(mx, my, ex, ey, ew, eh), accent);
            m.push(); m.translate(10, 12, 0); m.scale(2.2f, 2.2f, 1f); c.drawItem(x.stack, 0, 0); m.pop();
            text(c, x.name.toUpperCase(), 56, 10, 1.0f, 0xFF000000 | accent, 0);
            text(c, "1 шт. = " + x.rate + " " + coins(x.rate) + " · блок = " + x.rate * 9, 56, 22, 0.65f, 0xFFE6F0E6, 0);
            text(c, "У тебя: " + x.have + (x.blocks > 0 ? " + блоков: " + x.blocks : ""), 56, 32, 0.65f, 0xFFB8D0C0, 0);
            m.pop();
            int total = (x.have + x.blocks * 9);
            float by = ey + eh - 26;
            float bw = (ew - 30) / 3f;
            String id = x.id;
            sellBtn(c, mx, my, ex + 10, by, bw, "СДАТЬ 1", x.have >= 1, () -> send("ws_sell", 1, id));
            sellBtn(c, mx, my, ex + 15 + bw, by, bw, "СДАТЬ 16", x.have >= 16, () -> send("ws_sell", 16, id));
            sellBtn(c, mx, my, ex + 20 + bw * 2, by, bw, "ВСЁ · +" + total * x.rate, total > 0, () -> send("ws_sell", 0, id));
        }

        // сообщение
        float ta = (now - toastAt) / 1e9f;
        if (ta < 2.6f && !toast.isEmpty()) {
            float a = Math.min(1f, (2.6f - ta) * 2f);
            float w = textRenderer.getWidth(toast) * 0.8f + 16;
            c.fill((int) (DW / 2f - w / 2f), 249, (int) (DW / 2f + w / 2f), 261, StoryHud.argb(0x000000, 0.8f * a));
            text(c, toast, DW / 2f, 251, 0.8f, StoryHud.argb(toastOk ? 0x8EF0A0 : 0xFF8A8A, a), 1);
        }
        m.pop();
        super.render(c, mouseX, mouseY, delta);
    }

    private static String coins(int n) {
        int a = Math.abs(n) % 100, b = a % 10;
        return a > 10 && a < 20 ? "монет" : b == 1 ? "монета" : b >= 2 && b <= 4 ? "монеты" : "монет";
    }

    private void sellBtn(DrawContext c, float mx, float my, float x, float y, float w, String label, boolean on, Runnable r) {
        boolean hv = in(mx, my, x, y, w, 18);
        button(c, x, y, w, 18, label, on ? (hv ? 0xFFFFE08A : 0xFFE8C14A) : 0xFF6A6A6A, on ? 0xFF8A6A1E : 0xFF3A3A3A);
        if (on) hit(x, y, w, 18, r);
    }

    private void card(DrawContext c, int w, int h, boolean hv, int accent) {
        c.fill(-2, -2, w + 2, h + 2, 0xFF000000);
        c.fill(-1, -1, w + 1, h + 1, 0xFF000000 | (hv ? accent : darken(accent, 0.5f)));
        c.fillGradient(0, 0, w, h, 0xFF1E3A2E, 0xFF0E1C16);
        for (int yy = 6; yy < h - 6; yy += 8)
            for (int xx = 6 + ((yy / 8) % 2) * 4; xx < w - 6; xx += 8) c.fill(xx, yy, xx + 1, yy + 1, 0x14FFFFFF);
    }

    private boolean in(float x, float y, float bx, float by, float bw, float bh) { return x >= bx && x < bx + bw && y >= by && y < by + bh; }

    private void panel(DrawContext c, float x, float y, float w, float h) {
        c.fill((int) x - 1, (int) y - 1, (int) (x + w) + 1, (int) (y + h) + 1, 0xFF000000);
        c.fillGradient((int) x, (int) y, (int) (x + w), (int) (y + h), 0xFF1E3A2E, 0xFF0E1C16);
    }

    private void button(DrawContext c, float x, float y, float w, float h, String label, int top, int bot) {
        c.fill((int) x - 1, (int) y - 1, (int) (x + w) + 1, (int) (y + h) + 1, 0xFF000000);
        c.fillGradient((int) x, (int) y, (int) (x + w), (int) (y + h), top, bot);
        c.fill((int) x + 1, (int) y + 1, (int) (x + w) - 1, (int) y + 2, 0x55FFFFFF);
        float sc = Math.min(0.85f, (w - 6) / Math.max(1, textRenderer.getWidth(label)));
        text(c, label, x + w / 2f, y + h / 2f - 4 * sc / 0.85f, sc, 0xFFFFFFFF, 1);
    }

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

    private static int darken(int rgb, float k) {
        return ((int) ((rgb >> 16 & 255) * k) << 16) | ((int) ((rgb >> 8 & 255) * k) << 8) | (int) ((rgb & 255) * k);
    }
}
