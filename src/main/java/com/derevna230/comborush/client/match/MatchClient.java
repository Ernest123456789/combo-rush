package com.derevna230.comborush.client.match;

import com.derevna230.comborush.universe.Universes;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.sound.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Клиент соревновательного матча: состояние от сервера (деньги, закупка, сторона, место), HUD
 * и анимация конца раунда — победа (золотая полоса, конфетти), поражение (красная, пепел), ничья, смена сторон, конец матча.
 */
public final class MatchClient {
    private MatchClient() {}

    static int money, buyTicks, round, wt, wct;
    static boolean zone, active, ct;
    static String place = "", phase = "";
    private static long stateAt;

    private record Pop(int amount, long at) {}
    private static final List<Pop> POPS = new ArrayList<>();

    // ---- баннер конца раунда
    private static JsonObject banner;
    private static long bannerAt;

    public static void receive(String kind, JsonObject o) {
        if (kind.equals("mstate")) {
            if (o.has("off")) { active = false; return; }
            int m = o.get("money").getAsInt();
            if (active && m != money) POPS.add(new Pop(m - money, System.currentTimeMillis()));
            money = m;
            buyTicks = o.get("buy").getAsInt();
            zone = o.get("zone").getAsBoolean();
            ct = o.get("side").getAsString().equals("ct");
            round = o.get("round").getAsInt();
            wt = o.get("wt").getAsInt();
            wct = o.get("wct").getAsInt();
            place = o.get("place").getAsString();
            phase = o.get("phase").getAsString();
            active = true;
            stateAt = System.currentTimeMillis();
        } else if (kind.equals("mround")) {
            banner = o;
            bannerAt = System.currentTimeMillis();
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;
            String k = o.get("kind").getAsString();
            boolean win = o.get("win").getAsBoolean();
            if (k.equals("draw")) mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1f, 0.8f);
            else if (win) {
                mc.player.playSound(k.equals("match") ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.ENTITY_PLAYER_LEVELUP, 0.9f, 1.0f);
            } else {
                mc.player.playSound(SoundEvents.BLOCK_BEACON_DEACTIVATE, 1f, 0.7f);
                mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 1f, 0.5f);
            }
        }
    }

    /** Игрок сейчас в матче (E открывает закупку вместо инвентаря) */
    public static boolean inMatch() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return active && mc.world != null && mc.world.getRegistryKey().equals(Universes.DUST)
            && System.currentTimeMillis() - stateAt < 4000;
    }

    public static boolean canBuyNow() { return buyTicks > 0 && zone; }

    // ================================================================= HUD
    public static void render(DrawContext c) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden) return;
        if (inMatch()) hud(c, mc);
        if (banner != null) drawBanner(c, mc);
    }

    static String fmtMoney(int m) {
        String s = Integer.toString(Math.abs(m));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) { if (i > 0 && (s.length() - i) % 3 == 0) b.append(' '); b.append(s.charAt(i)); }
        return (m < 0 ? "-" : "") + "$" + b;
    }

    static void text(DrawContext c, TextRenderer tr, String s, float x, float y, float scale, int argb, int align) {
        if (((argb >>> 24) & 0xFF) < 6) return;
        c.getMatrices().push();
        c.getMatrices().translate(x, y, 0);
        c.getMatrices().scale(scale, scale, 1);
        float w = tr.getWidth(s);
        float dx = align == 1 ? -w / 2f : align == 2 ? -w : 0;
        c.drawText(tr, s, (int) dx, 0, argb, true);
        c.getMatrices().pop();
    }

    private static void hud(DrawContext c, MinecraftClient mc) {
        TextRenderer tr = mc.textRenderer;
        int w = c.getScaledWindowWidth(), h = c.getScaledWindowHeight();
        long now = System.currentTimeMillis();
        // деньги: справа от хотбара (слева внизу — чат)
        float mx = w / 2f + 91 + 12, my = h - 26;
        c.fill((int) mx - 4, (int) my - 4, (int) mx + 92, (int) my + 16, 0x90000000);
        c.fill((int) mx - 4, (int) my - 4, (int) mx - 2, (int) my + 16, ct ? 0xFF4A86E8 : 0xFFE0A030);
        text(c, tr, fmtMoney(money), mx + 2, my, 1.5f, 0xFF7CE07C, 0);
        // всплывающие изменения
        POPS.removeIf(p -> now - p.at() > 1800);
        int i = 0;
        for (Pop p : POPS) {
            float t = (now - p.at()) / 1800f;
            int a = (int) (255 * (1 - t));
            String s = (p.amount() > 0 ? "+" : "") + fmtMoney(p.amount());
            text(c, tr, s, mx + 98, my + 2 - i * 10 - t * 12, 1f, (a << 24) | (p.amount() > 0 ? 0x7CE07C : 0xE07C7C), 0);
            i++;
        }
        // закупка
        if (buyTicks > 0) {
            int s = (buyTicks + 19) / 20;
            String line = zone ? "§e[E]§f Закупка  §7" + s / 60 + ":" + String.format("%02d", s % 60) : "§7Закупка — только на своей базе";
            text(c, tr, line, mx, my - 12, 0.85f, 0xFFFFFFFF, 0);
        }
        // место на карте и сторона
        String side = ct ? "§9§lСПЕЦНАЗ" : "§6§lТЕРРОРИСТЫ";
        text(c, tr, side, 8, 8, 0.9f, 0xFFFFFFFF, 0);
        text(c, tr, "§7◆ §f" + place, 8, 19, 0.85f, 0xFFFFFFFF, 0);
    }

    // ================================================================= анимация конца раунда
    private static float clamp(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }
    private static float easeOut(float t) { float u = 1 - t; return 1 - u * u * u; }
    private static float easeBack(float t) { float c1 = 1.70158f, c3 = c1 + 1; float u = t - 1; return 1 + c3 * u * u * u + c1 * u * u; }
    private static int argb(float a, int rgb) { return ((int) (Math.max(0, Math.min(1, a)) * 255) << 24) | (rgb & 0xFFFFFF); }

    private static void drawBanner(DrawContext c, MinecraftClient mc) {
        JsonObject o = banner;
        TextRenderer tr = mc.textRenderer;
        int w = c.getScaledWindowWidth(), h = c.getScaledWindowHeight();
        float t = (System.currentTimeMillis() - bannerAt) / 1000f;
        float dur = Math.max(2.5f, o.get("ticks").getAsInt() / 20f - 0.2f);
        if (t > dur) { banner = null; return; }
        String kind = o.get("kind").getAsString();
        boolean win = o.get("win").getAsBoolean(), draw = kind.equals("draw"), big = kind.equals("match") || draw;
        boolean tWon = o.get("winner").getAsString().equals("t");
        int accent = draw ? 0xB8C0CC : win ? 0xF2C14E : 0xD04545;
        int team = tWon ? 0xE0A030 : 0x4A86E8;
        float in = clamp(t / 0.35f), out = clamp((dur - t) / 0.5f), vis = in * out;

        // затемнение и цветной отсвет сверху/снизу
        c.fill(0, 0, w, h, argb(0.42f * vis, 0x000000));
        c.fillGradient(0, 0, w, h / 3, argb(0.30f * vis, accent), 0x00000000);
        c.fillGradient(0, h * 2 / 3, w, h, 0x00000000, argb(0.22f * vis, accent));

        // частицы: конфетти при победе, пепел при поражении
        Random r = new Random(o.get("round").getAsInt() * 7919L + (win ? 1 : 0));
        int n = win ? (big ? 140 : 80) : 60;
        int[] pal = {accent, 0xFFFFFF, team, 0xFF7A4A, 0x7AD0FF};
        for (int i = 0; i < n; i++) {
            float x0 = r.nextFloat() * w, delay = r.nextFloat() * 0.8f, sp = 35 + r.nextFloat() * 70, sway = r.nextFloat() * 6.28f;
            int col = win ? pal[r.nextInt(pal.length)] : (r.nextBoolean() ? 0x6A6A6A : 0x3A3A3A);
            float tt = t - delay;
            if (tt < 0) continue;
            float px, py, a = vis;
            if (win || draw) {
                py = -10 - r.nextFloat() * 40 + tt * sp * 1.6f;
                px = x0 + (float) Math.sin(sway + tt * 3) * 14;
                int fw = 2 + (int) (Math.abs(Math.sin(sway + tt * 9)) * 3);
                c.fill((int) px, (int) py, (int) px + fw, (int) py + 4, argb(a, col));
            } else {
                py = h + 10 - tt * sp * 0.6f;
                px = x0 + (float) Math.sin(sway + tt * 1.5f) * 18;
                c.fill((int) px, (int) py, (int) px + 2, (int) py + 2, argb(a * 0.8f, col));
            }
        }

        // полоса-баннер
        float bandH = big ? 84 : 66, cy = h * 0.34f;
        float shake = !win && !draw && t < 0.4f ? (float) Math.sin(t * 80) * (0.4f - t) * 10 : 0;
        float slide = easeOut(clamp(t / 0.45f));
        int x0 = (int) (-w + w * slide + shake);
        int y0 = (int) (cy - bandH / 2), y1 = (int) (cy + bandH / 2);
        int strips = 48;
        for (int i = 0; i < strips; i++) {
            float fx = (i + 0.5f) / strips;
            float edge = 1 - Math.abs(fx - 0.5f) * 2;                       // плотнее к центру
            float a = (0.25f + 0.65f * (float) Math.sqrt(edge)) * out;
            int xa = x0 + w * i / strips, xb = x0 + w * (i + 1) / strips;
            c.fillGradient(xa, y0, xb, y1, argb(a, darken(accent, 0.45f)), argb(a, 0x0C0E12));
        }
        float lw = w * easeOut(clamp((t - 0.12f) / 0.5f));
        c.fill((int) (w / 2f - lw / 2), y0 - 2, (int) (w / 2f + lw / 2), y0, argb(out, accent));
        c.fill((int) (w / 2f - lw / 2), y1, (int) (w / 2f + lw / 2), y1 + 2, argb(out, accent));
        // блик, пробегающий по полосе
        float st = clamp((t - 0.35f) / 0.8f);
        if (st > 0 && st < 1) {
            float sx = -120 + (w + 240) * st;
            for (int y = y0; y < y1; y++) {
                float off = (y - cy) * 0.6f;
                c.fill((int) (sx + off), y, (int) (sx + off + 22), y + 1, argb(0.16f * out, 0xFFFFFF));
                c.fill((int) (sx + off + 26), y, (int) (sx + off + 32), y + 1, argb(0.10f * out, 0xFFFFFF));
            }
        }

        // заголовок с «отскоком» и разлетающимися буквами
        String title = draw ? "НИЧЬЯ" : kind.equals("match") ? (win ? "ПОБЕДА В МАТЧЕ" : "ПОРАЖЕНИЕ В МАТЧЕ") : (win ? "ПОБЕДА" : "ПОРАЖЕНИЕ");
        float tp = clamp((t - 0.1f) / 0.5f);
        float scale = (big ? 3.4f : 3.0f) * (0.55f + 0.45f * easeBack(tp));
        float spacing = 6f * (1 - easeOut(tp)) + 1f;
        float tw = 0;
        for (char ch : title.toCharArray()) tw += tr.getWidth(String.valueOf(ch)) + spacing;
        tw -= spacing;
        float tx = w / 2f + shake - tw * scale / 2f, ty = cy - (big ? 24 : 18) - 4 * scale + 12;
        int tcol = argb(clamp(tp * 2) * out, win || draw ? 0xFFFFFF : 0xFFD8D8);
        c.getMatrices().push();
        c.getMatrices().translate(tx, ty, 0);
        c.getMatrices().scale(scale, scale, 1);
        float cx = 0;
        for (char ch : title.toCharArray()) {
            String s = String.valueOf(ch);
            if (((tcol >>> 24) & 0xFF) >= 6) {
                c.drawText(tr, s, (int) cx + 1, 1, argb(clamp(tp * 2) * out, darken(accent, 0.5f)), false);
                c.drawText(tr, s, (int) cx, 0, tcol, false);
            }
            cx += tr.getWidth(s) + spacing;
        }
        c.getMatrices().pop();

        // подпись: кто выиграл и почему
        String who = (tWon ? "§6ТЕРРОРИСТЫ" : "§9СПЕЦНАЗ") + " §fвыигрывают " + (kind.equals("match") ? "матч" : "раунд");
        if (draw) who = "§fМатч окончен вничью";
        float sa = clamp((t - 0.45f) / 0.3f) * out;
        text(c, tr, who + "  §7·  " + o.get("reason").getAsString(), w / 2f + shake, cy + (big ? 20 : 14), 1.0f, argb(sa, 0xFFFFFF), 1);

        // счёт: число победившей стороны «подпрыгивает»
        float ka = clamp((t - 0.6f) / 0.3f) * out;
        int wT = o.get("wt").getAsInt(), wCT = o.get("wct").getAsInt();
        float pulse = 1 + 0.5f * (float) Math.max(0, Math.sin(clamp((t - 0.75f) / 0.35f) * Math.PI));
        float sy = y1 + 10;
        c.fill((int) (w / 2f - 56), (int) sy - 3, (int) (w / 2f + 56), (int) sy + 21, argb(0.55f * ka, 0x000000));
        text(c, tr, "T", w / 2f - 44, sy + 4, 1.1f, argb(ka, 0xE0A030), 1);
        text(c, tr, "CT", w / 2f + 44, sy + 4, 1.1f, argb(ka, 0x4A86E8), 1);
        text(c, tr, ":", w / 2f, sy + 2, 1.6f, argb(ka, 0xAAAAAA), 1);
        text(c, tr, Integer.toString(wT), w / 2f - 18, sy + 1 - (tWon ? (pulse - 1) * 6 : 0), 1.8f * (tWon ? pulse : 1), argb(ka, tWon ? 0xFFFFFF : 0xB0B0B0), 1);
        text(c, tr, Integer.toString(wCT), w / 2f + 18, sy + 1 - (!tWon ? (pulse - 1) * 6 : 0), 1.8f * (!tWon ? pulse : 1), argb(ka, !tWon ? 0xFFFFFF : 0xB0B0B0), 1);

        // деньги за раунд
        int pay = o.get("pay").getAsInt();
        float pa = clamp((t - 0.95f) / 0.3f) * out;
        if (pay > 0 && !big)
            text(c, tr, "+" + fmtMoney(pay) + (win ? "  §7за победу" : "  §7бонус за поражение"), w / 2f, sy + 30, 1.0f, argb(pa, 0x7CE07C), 1);
        if (kind.equals("half")) {
            float ha = clamp((t - 1.3f) / 0.3f) * out * (0.75f + 0.25f * (float) Math.sin(t * 6));
            text(c, tr, "ПЕРЕРЫВ — СМЕНА СТОРОН", w / 2f, sy + 46, 1.6f, argb(ha, 0xFFE07A), 1);
            text(c, tr, "Деньги — снова $800, оружие сдаётся", w / 2f, sy + 64, 0.9f, argb(ha, 0xC8C8C8), 1);
        }
        if (big) {
            float ma = clamp((t - 1.3f) / 0.4f) * out;
            text(c, tr, "Возврат в меню «Противостояния»…", w / 2f, sy + 34, 0.9f, argb(ma, 0xC8C8C8), 1);
        }
    }

    private static int darken(int rgb, float k) {
        int r = (int) (((rgb >> 16) & 255) * k), g = (int) (((rgb >> 8) & 255) * k), b = (int) ((rgb & 255) * k);
        return (r << 16) | (g << 8) | b;
    }
}
