package com.derevna230.comborush.client;

import com.derevna230.comborush.hud.ShopActionPayload;
import com.derevna230.comborush.item.ModItems;
import com.google.gson.JsonElement;
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
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.List;

/** Меню прокачки скейтборда: три карты улучшений (скорость, прыжок, планирование) за эссенцию */
public class SkateScreen extends Screen {
    private static final int DW = 420, DH = 250;
    private static final ItemStack ESSENCE = new ItemStack(Items.AMETHYST_SHARD);

    static final class Up { String key = "", now = "", next = ""; int lvl, max, cost; }
    static final class Data { int essence; boolean has; String msg = ""; boolean ok = true; List<Up> ups = new ArrayList<>(); }

    private Data d;
    private float u = 1, ox, oy;
    private final long openAt = System.nanoTime();
    private long toastAt = -10_000_000_000L, upAt = -10_000_000_000L;
    private String toast = ""; private boolean toastOk = true; private String upKey = "";
    private final List<float[]> btn = new ArrayList<>();          // x, y, w, h, индекс карты
    private int hover = -1;

    private SkateScreen(Data d) {
        super(Text.literal("Скейтборд"));
        this.d = d;
        if (!d.msg.isEmpty()) { toast = d.msg; toastOk = d.ok; toastAt = openAt; }
    }

    public static void onPacket(String kind, JsonObject o) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Data n = parse(o);
        if (kind.equals("skate_open")) { mc.setScreen(new SkateScreen(n)); return; }
        if (mc.currentScreen instanceof SkateScreen s) s.apply(n);
    }

    private static Data parse(JsonObject o) {
        Data d = new Data();
        d.essence = o.has("essence") ? o.get("essence").getAsInt() : 0;
        d.has = o.has("has") && o.get("has").getAsBoolean();
        d.msg = o.has("msg") ? o.get("msg").getAsString() : "";
        d.ok = !o.has("ok") || o.get("ok").getAsBoolean();
        if (o.has("ups")) for (JsonElement e : o.getAsJsonArray("ups")) {
            JsonObject j = e.getAsJsonObject();
            Up up = new Up();
            up.key = j.get("key").getAsString(); up.lvl = j.get("lvl").getAsInt(); up.max = j.get("max").getAsInt();
            up.cost = j.get("cost").getAsInt(); up.now = j.get("now").getAsString(); up.next = j.get("next").getAsString();
            d.ups.add(up);
        }
        return d;
    }

    private void apply(Data n) {
        long now = System.nanoTime();
        if (!n.msg.isEmpty()) {
            toast = n.msg; toastOk = n.ok; toastAt = now;
            if (n.ok) {
                HudModel.play(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.4f, 0.8f);
                HudModel.play(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.6f, 0.4f);
                upAt = now;
            } else HudModel.play(SoundEvents.ENTITY_VILLAGER_NO, 1.0f, 0.6f);
        }
        d = n;
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) { }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float px = (float) ((mouseX - ox) / u), py = (float) ((mouseY - oy) / u);
        for (float[] b : btn) {
            if (px >= b[0] && px < b[0] + b[2] && py >= b[1] && py < b[1] + b[3]) {
                int i = (int) b[4];
                if (i < 0) { close(); return true; }
                Up up = d.ups.get(i);
                upKey = up.key;
                HudModel.play(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.5f);
                ClientPlayNetworking.send(new ShopActionPayload("skate_up", 0, 0, up.key));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float t = (now - openAt) / 1e9f;
        u = Math.min(width / (float) DW, height / (float) DH) * 0.96f;
        ox = (width - DW * u) / 2f; oy = (height - DH * u) / 2f;
        float mx = (mouseX - ox) / u, my = (mouseY - oy) / u;

        // фон: тёмный градиент со звёздами
        c.fillGradient(0, 0, width, height, 0xF0120A24, 0xF00A1C24);
        for (int i = 0; i < 60; i++) {
            float sx = (float) ((Math.sin(i * 12.9898) * 43758.5453) % 1 + 1) % 1 * width;
            float sy = (float) ((Math.sin(i * 78.233) * 12543.1) % 1 + 1) % 1 * height;
            float tw = 0.4f + 0.6f * (float) Math.abs(Math.sin(t * 1.5 + i));
            c.fill((int) sx, (int) sy, (int) sx + 1, (int) sy + 1, argb(0xFFFFFF, 0.25f * tw));
        }

        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(ox, oy, 0);
        m.scale(u, u, 1f);
        btn.clear();

        // заголовок с переливом полихрома
        String title = "СКЕЙТБОРД";
        float tw0 = textRenderer.getWidth(title) * 2.2f, tx = DW / 2f - tw0 / 2f;
        for (int i = 0; i < title.length(); i++) {
            String ch = String.valueOf(title.charAt(i));
            float wob = (float) Math.sin(t * 3 + i * 0.6) * 1.5f;
            text(c, ch, tx, 8 + wob, 2.2f, 0xFF000000 | hsv((t * 0.15f + i * 0.08f) % 1f), 0);
            tx += textRenderer.getWidth(ch) * 2.2f;
        }
        text(c, "Прокачка за эссенцию · улучшения остаются в самом скейте", DW / 2f, 30, 0.6f, 0xFFB8B0D0, 1);

        // левая панель: эссенция и закрыть
        panel(c, 6, 46, 92, 34);
        m.push(); m.translate(10, 52, 0); m.scale(1.3f, 1.3f, 1f); c.drawItem(ESSENCE, 0, 0); m.pop();
        text(c, "ЭССЕНЦИЯ", 34, 50, 0.6f, 0xFFB8A0E0, 0);
        text(c, String.valueOf(d.essence), 34, 60, 1.6f, 0xFFD9A8FF, 0);
        boolean hc = in(mx, my, 6, 86, 92, 22);
        button(c, 6, 86, 92, 22, "ЗАКРЫТЬ", hc ? 0xFFFF6B6B : 0xFFE04848, 0xFF8A1C1C);
        btn.add(new float[]{6, 86, 92, 22, -1});
        text(c, "Shift+ПКМ скейтом —", 52, 116, 0.55f, 0xFF8A8AA0, 1);
        text(c, "открыть это меню", 52, 124, 0.55f, 0xFF8A8AA0, 1);

        // большой скейт по центру сверху справа от панели
        m.push();
        m.translate(52, 175, 50);
        float bob = (float) Math.sin(t * 2) * 3;
        m.translate(0, bob, 0);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.sin(t * 1.3) * 12));
        m.scale(4.2f, 4.2f, 1f);
        c.drawItem(new ItemStack(ModItems.SKATEBOARD), -8, -8);
        m.pop();

        if (!d.has) {
            text(c, "Возьми скейт в основную руку и открой меню снова", 265, 120, 0.8f, 0xFFFF8A8A, 1);
        }

        // три карты улучшений
        hover = -1;
        float cw = 96, ch = 160, gap = 8, cx0 = 112;
        for (int i = 0; i < d.ups.size(); i++) {
            Up up = d.ups.get(i);
            float x = cx0 + i * (cw + gap), y = 48;
            boolean hv = in(mx, my, x, y, cw, ch);
            if (hv) hover = i;
            float pop = up.key.equals(upKey) ? (float) Math.max(0, 1 - (now - upAt) / 4e8) : 0;
            float lift = hv ? -3 : 0;
            m.push();
            m.translate(x + cw / 2f, y + ch / 2f + lift, 0);
            float sc = 1 + pop * 0.06f;
            m.scale(sc, sc, 1f);
            m.translate(-cw / 2f, -ch / 2f, 0);
            drawCard(c, up, (int) cw, (int) ch, t, hv);
            m.pop();
            // кнопка
            boolean maxed = up.lvl >= up.max;
            boolean afford = !maxed && d.has && d.essence >= up.cost;
            float by = y + ch + 6;
            boolean hb = in(mx, my, x, by, cw, 20);
            int top = maxed ? 0xFF6A6A78 : afford ? (hb ? 0xFF8CF0A6 : 0xFF6FE08A) : 0xFF8A6A6A;
            int bot = maxed ? 0xFF3A3A44 : afford ? 0xFF1E8A4A : 0xFF4A2A2A;
            button(c, x, by, cw, 20, maxed ? "МАКСИМУМ" : "УЛУЧШИТЬ · " + up.cost, top, bot);
            if (afford) btn.add(new float[]{x, by, cw, 20, i});
        }

        // всплывающее сообщение
        float ta = (now - toastAt) / 1e9f;
        if (ta < 2.6f && !toast.isEmpty()) {
            float a = Math.min(1f, (2.6f - ta) * 2f);
            float w = textRenderer.getWidth(toast) * 0.8f + 16;
            c.fill((int) (DW / 2f - w / 2f), 236, (int) (DW / 2f + w / 2f), 249, argb(0x000000, 0.75f * a));
            text(c, toast, DW / 2f, 239, 0.8f, argb(toastOk ? 0x8EF0A0 : 0xFF8A8A, a), 1);
        }
        m.pop();
        super.render(c, mouseX, mouseY, delta);
    }

    private void drawCard(DrawContext c, Up up, int w, int h, float t, boolean hv) {
        int accent = switch (up.key) { case "speed" -> hsv((t * 0.2f) % 1f); case "jump" -> 0x7FD8FF; default -> 0xE8F4FF; };
        String name = switch (up.key) { case "speed" -> "СКОРОСТЬ"; case "jump" -> "ПРЫЖОК"; default -> "ПЛАНИРОВАНИЕ"; };
        ItemStack icon = new ItemStack(switch (up.key) { case "speed" -> Items.SUGAR; case "jump" -> Items.RABBIT_FOOT; default -> Items.PHANTOM_MEMBRANE; });
        c.fill(-2, -2, w + 2, h + 2, 0xFF000000);
        c.fill(-1, -1, w + 1, h + 1, 0xFF000000 | (hv ? accent : darken(accent, 0.6f)));
        c.fillGradient(0, 0, w, h, 0xFF2A1F46, 0xFF141024);
        for (int yy = 6; yy < h - 6; yy += 8)
            for (int xx = 6 + ((yy / 8) % 2) * 4; xx < w - 6; xx += 8) c.fill(xx, yy, xx + 1, yy + 1, 0x14FFFFFF);
        c.fill(0, 0, w, 16, 0xFF000000 | darken(accent, 0.45f));
        text(c, name, w / 2f, 4, 0.85f, 0xFFFFFFFF, 1);

        MatrixStack m = c.getMatrices();
        m.push();
        float bob = (float) Math.sin(t * 2.2 + up.key.length()) * 1.5f;
        m.translate(w / 2f - 16, 24 + bob, 0);
        m.scale(2f, 2f, 1f);
        c.drawItem(icon, 0, 0);
        m.pop();

        m.push();
        m.translate(0, 0, 200);
        // уровни
        float pw = up.max * 12f, px = w / 2f - pw / 2f;
        for (int i = 0; i < up.max; i++) {
            boolean on = i < up.lvl;
            c.fill((int) (px + i * 12), 62, (int) (px + i * 12 + 9), 71, 0xFF000000);
            c.fill((int) (px + i * 12 + 1), 63, (int) (px + i * 12 + 8), 70, on ? 0xFF000000 | accent : 0xFF3A3450);
        }
        text(c, "Уровень " + up.lvl + " / " + up.max, w / 2f, 76, 0.6f, 0xFFB8B0D0, 1);

        String hint = switch (up.key) { case "speed" -> "За скейтом остаётся след полихрома"; case "jump" -> "При прыжке — пыль света";
                                        default -> "Держи пробел в воздухе"; };
        int hy = 84;
        for (String line : wrap(hint, (int) ((w - 10) / 0.55f))) { text(c, line, w / 2f, hy, 0.55f, 0xFF9A90C0, 1); hy += 7; }

        int yy = Math.max(100, hy + 4);
        text(c, "СЕЙЧАС", 6, yy, 0.55f, 0xFF8A84A8, 0); yy += 8;
        for (String line : wrap(up.now, (int) ((w - 12) / 0.65f))) { text(c, line, 6, yy, 0.65f, 0xFFE6E0FF, 0); yy += 8; }
        if (!up.next.isEmpty()) {
            yy += 3;
            text(c, "ДАЛЬШЕ", 6, yy, 0.55f, 0xFF8A84A8, 0); yy += 8;
            for (String line : wrap(up.next, (int) ((w - 12) / 0.65f))) { text(c, line, 6, yy, 0.65f, 0xFF8EF0A0, 0); yy += 8; }
        }
        m.pop();
    }

    // ---------------------------------------------------------------- мелочи
    private boolean in(float x, float y, float bx, float by, float bw, float bh) { return x >= bx && x < bx + bw && y >= by && y < by + bh; }

    private void panel(DrawContext c, float x, float y, float w, float h) {
        c.fill((int) x - 1, (int) y - 1, (int) (x + w) + 1, (int) (y + h) + 1, 0xFF000000);
        c.fillGradient((int) x, (int) y, (int) (x + w), (int) (y + h), 0xFF2A2140, 0xFF1A1428);
    }

    private void button(DrawContext c, float x, float y, float w, float h, String label, int top, int bot) {
        c.fill((int) x - 1, (int) y - 1, (int) (x + w) + 1, (int) (y + h) + 1, 0xFF000000);
        c.fillGradient((int) x, (int) y, (int) (x + w), (int) (y + h), top, bot);
        c.fill((int) x + 1, (int) y + 1, (int) (x + w) - 1, (int) y + 2, 0x55FFFFFF);
        text(c, label, x + w / 2f, y + h / 2f - 4, 0.9f, 0xFFFFFFFF, 1);
    }

    private void text(DrawContext c, String s, float x, float y, float scale, int argb, int align) {
        if (s == null || s.isEmpty()) return;
        float w = textRenderer.getWidth(s) * scale;
        float px = align == 1 ? x - w / 2f : align == 2 ? x - w : x;
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(px, y, 0);
        m.scale(scale, scale, 1f);
        c.drawText(textRenderer, s, 0, 0, argb, ((argb >> 16 & 255) * 3 + (argb >> 8 & 255) * 6 + (argb & 255)) > 700);
        m.pop();
    }

    private List<String> wrap(String s, int maxPx) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String test = cur.length() == 0 ? word : cur + " " + word;
            if (textRenderer.getWidth(test) > maxPx && cur.length() > 0) { out.add(cur.toString()); cur = new StringBuilder(word); }
            else cur = new StringBuilder(test);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static int hsv(float h) {
        float r = Math.abs(h * 6 - 3) - 1, g = 2 - Math.abs(h * 6 - 2), b = 2 - Math.abs(h * 6 - 4);
        int ri = (int) (Math.max(0, Math.min(1, r)) * 200 + 55), gi = (int) (Math.max(0, Math.min(1, g)) * 200 + 55),
            bi = (int) (Math.max(0, Math.min(1, b)) * 200 + 55);
        return (ri << 16) | (gi << 8) | bi;
    }
    private static int darken(int rgb, float k) {
        return ((int) ((rgb >> 16 & 255) * k) << 16) | ((int) ((rgb >> 8 & 255) * k) << 8) | (int) ((rgb & 255) * k);
    }
    private static int argb(int rgb, float a) { return (Math.max(0, Math.min(255, (int) (a * 255))) << 24) | (rgb & 0xFFFFFF); }
}
