package com.derevna230.comborush.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Сюжет на экране: трекер текущего задания со стрелкой (слева сверху) и окно реплик NPC (внизу по центру) */
public final class StoryHud {
    private StoryHud() {}

    // ---------- трекер ----------
    private static boolean has = false, approx = false, server = false, hasPos = false;
    private static String title = "", hint = "";
    private static int prog = 0, max = 1, timer = -1;
    private static long timerAt = 0;
    private static double tx, ty, tz;
    private static long changedAt = 0;
    private static String lastTitle = "";

    // ---------- реплики ----------
    private record Line(String name, int color, String text) {}
    private static final Deque<Line> QUEUE = new ArrayDeque<>();
    private static Line cur = null;
    private static long lineAt = 0;
    private static int blipAt = 0;

    public static void reset() { has = false; QUEUE.clear(); cur = null; }

    public static void readTrack(JsonObject o) {
        has = o.has("has") && o.get("has").getAsBoolean();
        if (!has) return;
        title = o.get("title").getAsString();
        hint = o.has("hint") ? o.get("hint").getAsString() : "";
        prog = o.has("prog") ? o.get("prog").getAsInt() : 0;
        max = o.has("max") ? Math.max(1, o.get("max").getAsInt()) : 1;
        server = o.has("server") && o.get("server").getAsBoolean();
        hasPos = o.has("x");
        if (hasPos) { tx = o.get("x").getAsDouble(); ty = o.get("y").getAsDouble(); tz = o.get("z").getAsDouble(); }
        approx = o.has("approx") && o.get("approx").getAsBoolean();
        timer = o.has("timer") ? o.get("timer").getAsInt() : -1;
        timerAt = System.nanoTime();
        if (!title.equals(lastTitle)) { changedAt = System.nanoTime(); lastTitle = title; }
    }

    public static void readSay(JsonObject o) {
        String name = o.get("name").getAsString();
        int color = o.has("color") ? o.get("color").getAsInt() : 0xFFC94A;
        for (JsonElement e : o.getAsJsonArray("lines")) QUEUE.add(new Line(name, color, e.getAsString()));
    }

    public static void render(DrawContext c, TextRenderer tr, int W, int H) {
        long now = System.nanoTime();
        renderTracker(c, tr, now);
        renderDialog(c, tr, now, W, H);
    }

    // ================================================================ трекер
    private static void renderTracker(DrawContext c, TextRenderer tr, long now) {
        if (!has) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)) return;
        float since = (now - changedAt) / 1e9f;
        float slide = Math.min(1f, since * 3f);
        float ease = 1 - (1 - slide) * (1 - slide);
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(-150 * (1 - ease) + 4, 4, 0);

        int w = 150;
        List<String> lines = wrap(tr, hint, (int) ((w - 30) / 0.75f));
        if (lines.size() > 2) lines = lines.subList(0, 2);
        int h = 26 + lines.size() * 8 + (max > 1 ? 8 : 0) + (timer >= 0 ? 9 : 0);
        c.fill(-1, -1, w + 1, h + 1, 0xFF000000);
        c.fillGradient(0, 0, w, h, 0xE02A1F46, 0xE0141024);
        int accent = server ? 0x7FD8FF : 0xFFC94A;
        float pulse = since < 3 ? 0.5f + 0.5f * (float) Math.sin(since * 10) : 0;
        c.fill(0, 0, 2, h, 0xFF000000 | accent);
        if (pulse > 0) c.fill(0, 0, w, 1, argb(accent, pulse));

        text(c, tr, server ? "СЦЕНАРИЙ · ОБЩЕЕ" : "СЦЕНАРИЙ", 6, 3, 0.55f, 0xFF000000 | accent);
        text(c, tr, clip(tr, title, (int) ((w - 34) / 0.85f)), 6, 11, 0.85f, 0xFFFFFFFF);
        int y = 21;
        for (String l : lines) { text(c, tr, l, 6, y, 0.75f, 0xFFB8B0D0); y += 8; }
        if (max > 1) {
            int bw = w - 40;
            c.fill(6, y + 1, 6 + bw, y + 5, 0xFF000000);
            c.fill(7, y + 2, 7 + (int) ((bw - 2) * (prog / (float) max)), y + 4, 0xFF000000 | accent);
            text(c, tr, prog + "/" + max, 10 + bw, y, 0.65f, 0xFFE6E0FF);
            y += 8;
        }
        if (timer >= 0) {
            int left = Math.max(0, timer - (int) ((now - timerAt) / 1e9));
            int col = left <= 30 ? ((now / 250_000_000L) % 2 == 0 ? 0xFFFF5A5A : 0xFFFFB0B0) : 0xFFFFE08A;
            text(c, tr, "Время: " + left / 60 + ":" + String.format("%02d", left % 60), 6, y + 1, 0.8f, col);
        }

        // стрелка к цели и расстояние
        if (hasPos) {
            double dx = tx - mc.player.getX(), dz = tz - mc.player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            float targetYaw = (float) (MathHelper.atan2(dz, dx) * (180 / Math.PI)) - 90f;
            float rel = MathHelper.wrapDegrees(targetYaw - mc.player.getYaw());
            int ax = w - 14, ay = 14;
            m.push();
            m.translate(ax, ay, 0);
            if (dist > 4) {
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rel));
                arrow(c, 0xFF000000 | accent);
            } else {
                float b = 1 + 0.2f * (float) Math.sin(now / 1.5e8);
                m.scale(b, b, 1);
                c.fill(-3, -3, 3, 3, 0xFF000000);
                c.fill(-2, -2, 2, 2, 0xFF000000 | accent);
            }
            m.pop();
            String d = dist > 4 ? (approx ? "≈" : "") + (int) dist + " м" : "здесь";
            text(c, tr, d, ax - tr.getWidth(d) * 0.6f / 2f, ay + 9, 0.6f, 0xFFE6E0FF);
        }
        m.pop();
    }

    /** Стрелка «вверх» из прямоугольников (повёрнута матрицей) */
    private static void arrow(DrawContext c, int color) {
        for (int i = 0; i < 6; i++) c.fill(-i - 1, -7 + i, i + 2, -6 + i, 0xFF000000);
        c.fill(-2, -1, 3, 7, 0xFF000000);
        for (int i = 0; i < 5; i++) c.fill(-i, -6 + i, i + 1, -5 + i, color);
        c.fill(-1, -1, 2, 6, color);
    }

    // ================================================================ реплики
    private static void renderDialog(DrawContext c, TextRenderer tr, long now, int W, int H) {
        if (cur == null && !QUEUE.isEmpty()) {
            cur = QUEUE.poll();
            lineAt = now;
            blipAt = 0;
        }
        if (cur == null) return;
        float t = (now - lineAt) / 1e9f;
        float life = 2.4f + cur.text.length() * 0.045f;
        if (t > life) { cur = null; return; }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)) return;

        int w = Math.min(300, W - 40);
        int shown = Math.min(cur.text.length(), (int) (t * 55));
        // «голос»: короткий блип на каждые 3 буквы, у каждого персонажа своя высота
        if (shown - blipAt >= 3 && shown < cur.text.length()) {
            blipAt = shown;
            char ch = cur.text.charAt(shown - 1);
            if (ch != ' ' && ch != '§') {
                float base = 0.75f + ((cur.color >> 4) % 7) * 0.09f;
                HudModel.play(com.derevna230.comborush.sound.ModSounds.VOICE, base + (float) (Math.random() * 0.12), 0.22f);
            }
        }
        List<String> lines = wrap(tr, cur.text, (int) ((w - 16) / 0.85f));
        int h = 18 + lines.size() * 9;
        float a = Math.min(1f, Math.min(t * 6f, (life - t) * 3f));
        int x = (W - w) / 2, y = H - 64 - h;
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(0, (1 - Math.min(1f, t * 6f)) * 8, 0);
        c.fill(x - 1, y - 1, x + w + 1, y + h + 1, argb(0x000000, 0.9f * a));
        c.fillGradient(x, y, x + w, y + h, argb(0x2A1F46, 0.92f * a), argb(0x141024, 0.92f * a));
        c.fill(x, y, x + w, y + 1, argb(cur.color, a));
        // имя на плашке
        int nw = (int) (tr.getWidth(cur.name) * 0.8f) + 10;
        c.fill(x + 6, y - 6, x + 6 + nw, y + 5, argb(0x000000, a));
        c.fill(x + 7, y - 5, x + 5 + nw, y + 4, argb(darken(cur.color, 0.45f), a));
        text(c, tr, cur.name, x + 11, y - 3, 0.8f, argb(0xFFFFFF, a));
        // текст печатается по буквам
        int printed = 0, ly = y + 9;
        for (String l : lines) {
            int take = Math.max(0, Math.min(l.length(), shown - printed));
            if (take > 0 && take < l.length() && l.charAt(take - 1) == '§') take--;   // не резать код цвета
            printed += l.length() + 1;
            if (take > 0) text(c, tr, l.substring(0, take), x + 8, ly, 0.85f, argb(0xF2EEFF, a));
            ly += 9;
        }
        if (shown >= cur.text.length() && !QUEUE.isEmpty() && (now / 300_000_000L) % 2 == 0)
            text(c, tr, "▼", x + w - 10, y + h - 9, 0.7f, argb(cur.color, a));
        m.pop();
    }

    // ================================================================ мелочи
    static void text(DrawContext c, TextRenderer tr, String s, float x, float y, float scale, int argb) {
        if (s == null || s.isEmpty()) return;
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(scale, scale, 1f);
        c.drawText(tr, s, 0, 0, argb, false);
        m.pop();
    }

    static List<String> wrap(TextRenderer tr, String s, int maxPx) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (tr.getWidth(test) > maxPx && line.length() > 0) { out.add(line.toString()); line = new StringBuilder(word); }
            else line = new StringBuilder(test);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    static String clip(TextRenderer tr, String s, int maxPx) {
        if (tr.getWidth(s) <= maxPx) return s;
        while (s.length() > 1 && tr.getWidth(s + "…") > maxPx) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    static int argb(int rgb, float a) { return (Math.max(0, Math.min(255, (int) (a * 255))) << 24) | (rgb & 0xFFFFFF); }
    static int darken(int rgb, float k) {
        return ((int) ((rgb >> 16 & 255) * k) << 16) | ((int) ((rgb >> 8 & 255) * k) << 8) | (int) ((rgb & 255) * k);
    }
}
