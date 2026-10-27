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
import net.minecraft.util.math.RotationAxis;

import java.util.*;
import java.util.function.Supplier;

/**
 * Выбор существа в стиле Balatro (как выбор колоды перед забегом):
 * большая карта по центру с переворотом при листании, бонусы и штрафы справа, вся «колода» существ снизу.
 * Выбор проверяет сервер (SpeciesService).
 */
public class SpeciesScreen extends Screen {
    // ---------------- данные ----------------
    static final class Trait { String text = ""; boolean good = true; }
    static final class Sp {
        String id = "", name = "", desc = "", icon = "minecraft:player_head";
        boolean unlocked = true;
        int need, rank;
        List<Trait> traits = new ArrayList<>();
        ItemStack stack = ItemStack.EMPTY;
    }
    static final class Data {
        String current = "", msg = "";
        boolean canChange = true, ok = true;
        int events, resets, essence, maxRank = 5;
        List<Sp> list = new ArrayList<>();
    }

    private static final class Hit {
        float x, y, w, h; String key; Runnable click; Supplier<List<Text>> tip;
        boolean contains(float px, float py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }
    private static final class Spark { float x, y, vx, vy; int rgb; long born; }

    private static final int DW = 420, DH = 250;
    private static final int PER_PAGE = 9;

    // ---------------- состояние ----------------
    private Data d;
    private int view = 0, prevView = 0, page = 0;
    private long flipAt = -10_000_000_000L, lastNs = 0, toastAt = -10_000_000_000L, pickAt = -10_000_000_000L;
    private float u = 1, ox, oy, mx, my;
    private String toast = ""; private boolean toastOk = true;
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, Float> hov = new HashMap<>();
    private final List<Spark> sparks = new ArrayList<>();
    private Hit hoverHit = null;

    private SpeciesScreen(Data d) {
        super(Text.literal("Выбор существа"));
        this.d = d;
        view = Math.max(0, indexOf(d.current));
        prevView = view;
        page = view / PER_PAGE;
    }

    // ================= пакеты =================
    public static void onPacket(String kind, JsonObject o) {
        MinecraftClient mc = MinecraftClient.getInstance();
        switch (kind) {
            case "species_open" -> mc.setScreen(new SpeciesScreen(parse(o)));
            case "species" -> { if (mc.currentScreen instanceof SpeciesScreen s) s.apply(parse(o)); }
            default -> { }
        }
    }

    private static String str(JsonObject o, String k, String def) { return o.has(k) ? o.get(k).getAsString() : def; }
    private static int num(JsonObject o, String k) { return o.has(k) ? o.get(k).getAsInt() : 0; }
    private static boolean bool(JsonObject o, String k, boolean def) { return o.has(k) ? o.get(k).getAsBoolean() : def; }

    private static Data parse(JsonObject o) {
        Data d = new Data();
        d.current = str(o, "current", ""); d.msg = str(o, "msg", "");
        d.canChange = bool(o, "canChange", true); d.ok = bool(o, "ok", true);
        d.events = num(o, "events"); d.resets = num(o, "resets"); d.essence = num(o, "essence");
        d.maxRank = Math.max(1, num(o, "maxRank"));
        if (o.has("list")) {
            for (JsonElement el : o.getAsJsonArray("list")) {
                JsonObject j = el.getAsJsonObject();
                Sp s = new Sp();
                s.id = str(j, "id", ""); s.name = str(j, "name", "?"); s.desc = str(j, "desc", "");
                s.icon = str(j, "icon", "minecraft:player_head");
                s.unlocked = bool(j, "unlocked", true); s.need = num(j, "need"); s.rank = num(j, "rank");
                if (j.has("traits")) for (JsonElement te : j.getAsJsonArray("traits")) {
                    JsonObject tj = te.getAsJsonObject();
                    Trait t = new Trait(); t.text = str(tj, "t", ""); t.good = bool(tj, "g", true);
                    s.traits.add(t);
                }
                Identifier id = Identifier.tryParse(s.icon);
                s.stack = id != null && Registries.ITEM.containsId(id) ? new ItemStack(Registries.ITEM.get(id)) : ItemStack.EMPTY;
                d.list.add(s);
            }
        }
        return d;
    }

    private int indexOf(String id) {
        for (int i = 0; i < d.list.size(); i++) if (d.list.get(i).id.equals(id)) return i;
        return -1;
    }

    private void apply(Data n) {
        long now = System.nanoTime();
        boolean changed = !n.current.equals(d.current);
        String viewedId = view < d.list.size() ? d.list.get(view).id : "";
        d = n;
        int idx = indexOf(viewedId);
        view = idx >= 0 ? idx : Math.max(0, indexOf(d.current));
        prevView = view;

        if (!n.msg.isEmpty()) {
            toast = n.msg; toastOk = n.ok; toastAt = now;
            if (!n.ok) HudModel.play(SoundEvents.ENTITY_VILLAGER_NO, 1.0f, 0.6f);
        }
        if (changed && n.ok) {
            pickAt = now;
            burst(now);
            HudModel.play(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.2f, 0.5f);
            HudModel.play(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 0.6f);
        }
    }

    private void send(String action, String c) { ClientPlayNetworking.send(new ShopActionPayload(action, 0, 0, c)); }

    private void go(int to) {
        if (d.list.isEmpty()) return;
        int n = Math.floorMod(to, d.list.size());
        if (n == view) return;
        prevView = view;
        view = n;
        flipAt = System.nanoTime();
        page = view / PER_PAGE;
        HudModel.play(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.1f, 0.6f);
    }

    private void pick() {
        if (d.list.isEmpty()) return;
        Sp s = d.list.get(view);
        if (s.id.equals(d.current)) { toastMsg("Это существо уже выбрано.", false); return; }
        if (!s.unlocked) { toastMsg("Закрыто: нужно сбросов — " + s.need + ".", false); return; }
        if (!d.canChange) { toastMsg("Сменить существо можно только после сброса.", false); return; }
        send("species_pick", s.id);
    }

    // ================= Screen =================
    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) { }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 263 || keyCode == 65) { go(view - 1); return true; }     // ← или A
        if (keyCode == 262 || keyCode == 68) { go(view + 1); return true; }     // → или D
        if (keyCode == 257 || keyCode == 335) { pick(); return true; }          // Enter
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical != 0) { go(view + (vertical < 0 ? 1 : -1)); return true; }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

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
        ox = (width - DW * u) / 2f;
        oy = (height - DH * u) / 2f;
        mx = (mouseX - ox) / u;
        my = (mouseY - oy) / u;

        hoverHit = null;
        for (int i = hits.size() - 1; i >= 0; i--) if (hits.get(i).contains(mx, my)) { hoverHit = hits.get(i); break; }
        String hk = hoverHit == null ? "" : hoverHit.key;
        Set<String> keys = new HashSet<>(hov.keySet());
        keys.add(hk);
        for (String k : keys) {
            float cur = hov.getOrDefault(k, 0f);
            hov.put(k, cur + ((k.equals(hk) ? 1f : 0f) - cur) * Math.min(1f, dt * 14f));
        }

        hits.clear();
        drawBackground(c, now);

        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(ox, oy, 0);
        m.scale(u, u, 1f);
        drawTitle(c, now);
        drawSidebar(c, now);
        if (!d.list.isEmpty()) {
            drawBigCard(c, now);
            drawInfo(c, now);
            drawDeck(c, now);
        } else {
            text(c, "Существа не загружены", 258, 110, 1f, 0xFFFFFFFF, 1);
        }
        drawSparks(c, now, dt);
        drawToast(c, now);
        m.pop();

        if (hoverHit != null && hoverHit.tip != null) c.drawTooltip(textRenderer, hoverHit.tip.get(), mouseX, mouseY);
    }

    // ================= фон =================
    private void drawBackground(DrawContext c, long now) {
        c.fillGradient(0, 0, width, height, 0xFF14493A, 0xFF07170F);
        for (int y = 0; y < height; y += 4) c.fill(0, y, width, y + 1, 0x0AFFFFFF);
        for (int i = 0; i < 220; i++) {
            int h = i * 7919 + 13;
            int x = Math.abs(h * 31) % Math.max(1, width), y = Math.abs(h * 17 + i * 101) % Math.max(1, height);
            float tw = 0.5f + 0.5f * (float) Math.sin(now / 1.2e9 + i);
            c.fill(x, y, x + 1, y + 1, ((int) (30 * tw) << 24) | 0xFFFFFF);
        }
        // свечение за большой картой
        float pulse = 0.85f + 0.15f * (float) Math.sin(now / 8e8);
        int gx = (int) (ox + 178 * u), gy = (int) (oy + 100 * u);
        for (int i = 0; i < 8; i++) {
            int rad = (int) ((30 + i * 14) * u * pulse);
            disc(c, gx, gy, rad, ((7 + i) << 24) | 0x40E0A0);
        }
        int vw = Math.max(8, width / 14);
        for (int i = 0; i < vw; i++) {
            int a = (int) (150 * Math.pow(1 - i / (float) vw, 2));
            c.fill(i, 0, i + 1, height, a << 24);
            c.fill(width - i - 1, 0, width - i, height, a << 24);
        }
        c.fillGradient(0, 0, width, height / 7, 0xAA000000, 0x00000000);
        c.fillGradient(0, height - height / 6, width, height, 0x00000000, 0xCC000000);
    }

    private void disc(DrawContext c, int cx, int cy, int r, int argb) {
        for (int dy = -r; dy <= r; dy += 2) {
            int hw = (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
            c.fill(cx - hw, cy + dy, cx + hw, cy + dy + 2, argb);
        }
    }

    private void drawTitle(DrawContext c, long now) {
        String t = "ВЫБОР СУЩЕСТВА";
        float scale = 1.7f;
        float x = 258 - textRenderer.getWidth(t) * scale / 2f;
        for (int i = 0; i < t.length(); i++) {
            String ch = String.valueOf(t.charAt(i));
            float wob = (float) Math.sin(now / 3.5e8 + i * 0.55) * 1.6f;
            text(c, ch, x - 0.6f, 5 + wob + 0.9f, scale, 0xFF0B2A20, 0);
            text(c, ch, x, 5 + wob, scale, 0xFFF2B72B, 0);
            x += textRenderer.getWidth(ch) * scale;
        }
    }

    // ================= левая колонка =================
    private void drawSidebar(DrawContext c, long now) {
        Sp cur = indexOf(d.current) >= 0 ? d.list.get(indexOf(d.current)) : null;
        panel(c, 6, 4, 92, 38);
        text(c, "СЕЙЧАС", 10, 8, 0.55f, 0xFFA0A0A8, 0);
        if (cur != null) {
            if (!cur.stack.isEmpty()) {
                MatrixStack m = c.getMatrices();
                m.push(); m.translate(10, 17, 0); m.scale(1.2f, 1.2f, 1f); c.drawItem(cur.stack, 0, 0); m.pop();
            }
            text(c, fit(cur.name, 64, 0.9f), 32, 18, 0.9f, 0xFFFFFFFF, 0);
            pips(c, 32, 29, cur.rank, d.maxRank, 4, now);
        }

        Sp v = d.list.isEmpty() ? null : d.list.get(view);
        boolean isCurrent = v != null && v.id.equals(d.current);
        boolean can = v != null && v.unlocked && d.canChange && !isCurrent;
        String label = isCurrent ? "ВЫБРАНО" : v != null && !v.unlocked ? "ЗАКРЫТО" : !d.canChange ? "ПОСЛЕ СБРОСА" : "ВЫБРАТЬ";
        float pulse = can ? 1f + 0.035f * (0.5f + 0.5f * (float) Math.sin(now / 3e8)) : 1f;
        button(c, "pick", 6, 46, 92, 44, label, 0x6FE08A, 0x1E8A4A, can, pulse, this::pick,
            () -> {
                List<Text> t = new ArrayList<>();
                if (v == null) return t;
                t.add(Text.literal("§a" + v.name));
                if (isCurrent) t.add(Text.literal("§7Уже выбрано"));
                else if (!v.unlocked) t.add(Text.literal("§cНужно сбросов: " + v.need + " (у тебя " + d.resets + ")"));
                else if (!d.canChange) t.add(Text.literal("§cЗабег уже идёт: сменить можно после сброса"));
                else t.add(Text.literal("§7Enter — выбрать"));
                return t;
            });
        text(c, can ? "Enter" : "", 52, 77, 0.55f, 0xFFD0FFD8, 1);

        button(c, "close", 6, 94, 92, 26, "ЗАКРЫТЬ", 0xFF5A4A, 0xC02828, true, 1f,
            () -> MinecraftClient.getInstance().setScreen(null),
            () -> List.of(Text.literal("§cЗакрыть"), Text.literal("§7Или клавиша Esc")));

        panel(c, 6, 124, 92, 48);
        text(c, "ЗАБЕГ", 10, 128, 0.55f, 0xFFA0A0A8, 0);
        if (d.canChange) {
            text(c, "Ещё не начат —", 10, 137, 0.65f, 0xFF8EF0A0, 0);
            text(c, "можно выбрать", 10, 145, 0.65f, 0xFF8EF0A0, 0);
        } else {
            text(c, "Идёт: " + d.events + "/50 событий", 10, 137, 0.65f, 0xFFFF9A9A, 0);
            text(c, "смена после сброса", 10, 145, 0.65f, 0xFFFF9A9A, 0);
        }
        text(c, "Сбросов " + d.resets + "  ·  эссенция " + d.essence, 10, 157, 0.6f, 0xFFB8B8C0, 0);
        text(c, "← → листать", 52, 178, 0.6f, 0x88FFFFFF, 1);
    }

    // ================= большая карта =================
    private void drawBigCard(DrawContext c, long now) {
        float x = 130, y = 34, w = 96, h = 132;
        // стрелки
        arrow(c, "left", 106, 86, true, () -> go(view - 1));
        arrow(c, "right", 232, 86, false, () -> go(view + 1));

        float t = Math.min(1f, (now - flipAt) / 1e6f / 260f);
        Sp s = d.list.get(t < 0.5f ? Math.min(prevView, d.list.size() - 1) : view);
        float sx = t < 0.5f ? 1f - 2f * t : 2f * t - 1f;
        sx = Math.max(0.04f, sx);

        float ha = hov.getOrDefault("big", 0f);
        float pt = (now - pickAt) / 1e6f;
        float pop = pt < 600f ? 0.18f * (float) Math.exp(-pt / 150f) * (float) Math.cos(pt / 45f) : 0f;
        float tilt = Math.max(-0.5f, Math.min(0.5f, (mx - (x + w / 2f)) / w));
        float angle = ha * tilt * 8f + (float) Math.sin(now / 9e8) * 1.2f;
        float bob = (float) Math.sin(now / 7e8) * 1.2f - 3f * ha;
        float scale = 1f + 0.04f * ha + pop;

        c.fill((int) (x + 6), (int) (y + h), (int) (x + w - 6), (int) (y + h + 5), 0x55000000);
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(x + w / 2f, y + h / 2f + bob, 0);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
        m.scale(scale * sx, scale, 1f);
        m.translate(-w / 2f, -h / 2f, 0);
        drawSpeciesCard(c, s, (int) w, (int) h, now, true);
        m.pop();
        hit(x, y, w, h, "big", this::pick, () -> tip(s));

        // номер в колоде
        text(c, (view + 1) + " / " + d.list.size(), x + w / 2f, y + h + 8, 0.7f, 0xAAFFFFFF, 1);
    }

    /** Лицевая сторона карты существа (в локальных координатах 0..w, 0..h) */
    private void drawSpeciesCard(DrawContext c, Sp s, int w, int h, long now, boolean big) {
        boolean cur = s.id.equals(d.current);
        int border = cur ? 0xFFFFE066 : rankBorder(s.rank, d.maxRank, now);
        int top = s.unlocked ? 0x2E6B8F : 0x3A3A44, bot = s.unlocked ? 0x16304A : 0x15151A;

        c.fill(-2, -2, w + 2, h + 2, 0xFF000000);
        c.fill(-1, -1, w + 1, h + 1, border);
        c.fillGradient(0, 0, w, h, 0xFF000000 | top, 0xFF000000 | bot);
        // узор рубашки
        for (int yy = 6; yy < h - 6; yy += 8)
            for (int xx = 6 + ((yy / 8) % 2) * 4; xx < w - 6; xx += 8)
                c.fill(xx, yy, xx + 1, yy + 1, 0x18FFFFFF);
        c.fill(2, 2, w - 2, 3, 0x55FFFFFF);

        float fs = big ? 1f : 0.5f;
        // ранг
        pips(c, w / 2f - (d.maxRank * (big ? 9f : 4.5f)) / 2f + (big ? 1.5f : 0.75f), big ? 6 : 3, s.rank, d.maxRank,
            big ? 6 : 3, now);

        // иконка
        if (!s.stack.isEmpty()) {
            float is = big ? 3.6f : 1.15f;
            MatrixStack m = c.getMatrices();
            m.push();
            float bob = big ? (float) Math.sin(now / 5e8) * 1.5f : 0f;
            m.translate((w - 16 * is) / 2f, (big ? 22 : 7) + bob, 0);
            m.scale(is, is, 1f);
            c.drawItem(s.stack, 0, 0);
            m.pop();
        }

        // всё дальнейшее — поверх иконки (предметы рисуются на глубине ~150)
        MatrixStack mz = c.getMatrices();
        mz.push();
        mz.translate(0, 0, 250);

        // плашка с именем
        float ph = big ? 24 : 11;
        c.fill(2, (int) (h - ph - 2), w - 2, h - 2, 0xC8000000);
        text(c, fit(s.name, (int) (w - 6), big ? 1.1f : 0.5f), w / 2f, h - ph + (big ? 6 : 1), big ? 1.1f : 0.5f,
            s.unlocked ? 0xFFFFFFFF : 0xFF8A8A8A, 1);

        if (!s.unlocked) {
            c.fill(0, 0, w, h, 0xB0000000);
            c.fill(0, (int) (h / 2f - (big ? 14 : 8)), w, (int) (h / 2f + (big ? 14 : 5)), 0xC0000000);
            text(c, "ЗАКРЫТО", w / 2f, h / 2f - (big ? 10 : 6), big ? 1.2f : 0.5f, 0xFFFF7A7A, 1);
            text(c, "сбросов: " + s.need, w / 2f, h / 2f + (big ? 4 : 0), big ? 0.8f : 0.42f, 0xFFFFD0D0, 1);
        }
        if (cur) {                                   // лента «ВЫБРАНО» — над плашкой с именем, не на ней
            float rh = big ? 12 : 5;
            float ry = big ? h * 0.62f : h - ph - 2 - rh;
            c.fill(0, (int) ry, w, (int) (ry + rh), 0xE0C8901A);
            text(c, "ВЫБРАНО", w / 2f, ry + (big ? 2.5f : 0.8f), big ? 0.8f : 0.38f, 0xFF3A2A00, 1);
        }
        // блик
        double phs = ((now / 1e9) + s.id.hashCode() % 7 * 0.3) % 4.0 / 4.0;
        if (phs < 0.28) {
            int sx = (int) (-10 + (w + 20) * (phs / 0.28));
            int x1 = Math.max(1, sx), x2 = Math.min(w - 1, sx + (big ? 10 : 5));
            if (x2 > x1) c.fill(x1, 1, x2, h - 1, 0x28FFFFFF);
        }
        mz.pop();
    }

    private void arrow(DrawContext c, String key, float x, float y, boolean left, Runnable click) {
        float ha = hov.getOrDefault(key, 0f);
        float off = (float) Math.sin(System.nanoTime() / 2.5e8) * 1.2f * (left ? -1 : 1);
        int iw = 20, ih = 28;
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(x + off, y, 0);
        c.fill(-1, -1, iw + 1, ih + 3, 0xFF000000);
        c.fill(0, ih - 1, iw, ih + 2, 0xFF7A1A14);
        c.fillGradient(0, 0, iw, ih, 0xFFFF5A4A, 0xFFC02828);
        c.fill(1, 1, iw - 1, 2, 0x66FFFFFF);
        if (ha > 0.05f) c.fill(0, 0, iw, ih, ((int) (45 * ha) << 24) | 0xFFFFFF);
        text(c, left ? "<" : ">", iw / 2f, 7, 1.6f, 0xFFFFFFFF, 1);
        m.pop();
        hit(x, y, iw, ih + 2, key, click, () -> List.of(Text.literal(left ? "§7Предыдущее (←)" : "§7Следующее (→)")));
    }

    // ================= панель описания =================
    private void drawInfo(DrawContext c, long now) {
        Sp s = d.list.get(view);
        float t = Math.min(1f, (now - flipAt) / 1e6f / 350f);
        float a = 0.3f + 0.7f * t;
        int x = 256, y = 34, w = 158, h = 136;
        panel(c, x, y, w, h);

        text(c, fit(s.name, w - 10, 1.3f), x + 6, y + 5, 1.3f, argb(s.unlocked ? 0xF2B72B : 0x9A9AA4, a), 0);
        String status = s.id.equals(d.current) ? "Выбрано" : s.unlocked ? "Открыто" : "Закрыто · нужно сбросов: " + s.need;
        text(c, status, x + 6, y + 19, 0.6f, argb(s.id.equals(d.current) ? 0xFFE066 : s.unlocked ? 0x8EF0A0 : 0xFF8A8A, a), 0);
        text(c, "Ранг", x + w - 6 - d.maxRank * 7 - 22, y + 19, 0.6f, argb(0xA0A0A8, a), 0);
        pips(c, x + w - 6 - d.maxRank * 7f, y + 19, s.rank, d.maxRank, 5, now);

        c.fill(x + 6, y + 28, x + w - 6, y + 29, 0x33FFFFFF);
        float ly = y + 32;
        for (String line : wrap(s.desc, (int) ((w - 12) / 0.62f))) {
            if (ly > y + 62) break;
            text(c, line, x + 6, ly, 0.62f, argb(0xD8D8E0, a), 0);
            ly += 7;
        }

        ly = Math.max(ly + 3, y + 66);
        text(c, "БОНУСЫ И ШТРАФЫ", x + 6, ly, 0.55f, argb(0x9AA8A0, a), 0);
        ly += 8;
        for (int i = 0; i < s.traits.size(); i++) {
            Trait tr = s.traits.get(i);
            float rt = Math.max(0f, Math.min(1f, ((now - flipAt) / 1e6f - 80f * i) / 220f));
            float slide = (1f - rt) * 10f;
            for (String line : wrap(tr.text, (int) ((w - 22) / 0.62f))) {
                if (ly > y + h - 8) break;
                int col = tr.good ? 0x8EF0A0 : 0xFF8A8A;
                c.fill((int) (x + 6 + slide), (int) ly, (int) (x + 13 + slide), (int) ly + 6, argb(col, a * rt * 0.9f));
                text(c, tr.good ? "+" : "−", x + 9.5f + slide, ly - 0.3f, 0.62f, argb(0x101014, rt), 1);
                text(c, line, x + 17 + slide, ly, 0.62f, argb(col, rt), 0);
                ly += 8;
            }
        }
    }

    // ================= колода внизу =================
    private void drawDeck(DrawContext c, long now) {
        panel(c, 102, 182, 312, 64);
        int pages = (d.list.size() + PER_PAGE - 1) / PER_PAGE;
        page = Math.max(0, Math.min(page, pages - 1));
        text(c, "ВСЕ СУЩЕСТВА" + (pages > 1 ? "  " + (page + 1) + "/" + pages : ""), 108, 185, 0.6f, 0xFFB8F0D0, 0);

        int start = page * PER_PAGE, end = Math.min(d.list.size(), start + PER_PAGE);
        for (int i = start; i < end; i++) {
            Sp s = d.list.get(i);
            int slot = i - start;
            float x = 110 + slot * 33, y = 194;
            String key = "d" + i;
            float ha = hov.getOrDefault(key, 0f);
            boolean sel = i == view;
            float yOff = -4f * ha - (sel ? 4f : 0f) + (float) Math.sin(now / 7e8 + i * 1.3) * 0.6f;
            float angle = (float) Math.sin(now / 9e8 + i) * 1.2f;

            MatrixStack m = c.getMatrices();
            m.push();
            m.translate(x + 15, y + 20 + yOff, 0);
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
            m.scale(1f + 0.08f * ha, 1f + 0.08f * ha, 1f);
            m.translate(-15, -20, 0);
            drawSpeciesCard(c, s, 30, 40, now, false);
            if (sel) {
                c.fill(-3, -3, 33, -2, 0xFFFFE066); c.fill(-3, 42, 33, 43, 0xFFFFE066);
                c.fill(-3, -3, -2, 43, 0xFFFFE066); c.fill(32, -3, 33, 43, 0xFFFFE066);
            }
            m.pop();
            final int idx = i;
            hit(x, y, 30, 40, key, () -> go(idx), () -> tip(s));
        }
    }

    private List<Text> tip(Sp s) {
        List<Text> t = new ArrayList<>();
        t.add(Text.literal((s.unlocked ? "§6§l" : "§8§l") + s.name));
        t.add(Text.literal("§7Ранг: " + s.rank + "/" + d.maxRank + (s.id.equals(d.current) ? "  §e(выбрано)" : "")));
        for (String l : wrap(s.desc, 170)) t.add(Text.literal("§f" + l));
        for (Trait tr : s.traits) t.add(Text.literal((tr.good ? "§a+ " : "§c− ") + tr.text));
        if (!s.unlocked) t.add(Text.literal("§cНужно сбросов: " + s.need));
        return t;
    }

    // ================= ранг: ромбики =================
    private void pips(DrawContext c, float x, float y, int rank, int max, int size, long now) {
        for (int i = 0; i < max; i++) {
            float px = x + i * (size + 1.5f);
            boolean on = i < rank;
            boolean maxed = rank >= max;
            int col = !on ? 0xFF2A2A32 : maxed ? 0xFF000000 | rainbow(now, i * 0.12) : 0xFFF2B72B;
            MatrixStack m = c.getMatrices();
            m.push();
            m.translate(px + size / 2f, y + size / 2f, 0);
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45));
            float hs = size * 0.36f;
            c.fill((int) Math.floor(-hs - 1), (int) Math.floor(-hs - 1), (int) Math.ceil(hs + 1), (int) Math.ceil(hs + 1), 0xFF000000);
            c.fill((int) Math.floor(-hs), (int) Math.floor(-hs), (int) Math.ceil(hs), (int) Math.ceil(hs), col);
            m.pop();
        }
    }

    private int rankBorder(int rank, int max, long now) {
        if (rank >= max) return 0xFF000000 | rainbow(now, 0);
        return switch (rank) {
            case 0 -> 0xFFB8B8C0;
            case 1 -> 0xFF5CE08A;
            case 2 -> 0xFF4C9BFF;
            case 3 -> 0xFFB45BFF;
            default -> 0xFFFFC040;
        };
    }

    private static int rainbow(long now, double offset) {
        float h = (float) (((now / 1e9) * 0.5 + offset) % 1.0);
        int i = (int) (h * 6);
        float f = h * 6 - i, s = 0.55f, p = 1 - s, q = 1 - f * s, t = 1 - (1 - f) * s;
        float r, g, b;
        switch (i % 6) {
            case 0: r = 1; g = t; b = p; break;
            case 1: r = q; g = 1; b = p; break;
            case 2: r = p; g = 1; b = t; break;
            case 3: r = p; g = q; b = 1; break;
            case 4: r = t; g = p; b = 1; break;
            default: r = 1; g = p; b = q;
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    // ================= искры при выборе =================
    private void burst(long now) {
        Random r = new Random();
        int[] cols = {0xF2B72B, 0x8EF0A0, 0x6FA8FF, 0xFF7AD9, 0xFFFFFF};
        for (int i = 0; i < 40; i++) {
            Spark s = new Spark();
            double ang = r.nextDouble() * Math.PI * 2;
            float sp = 40f + r.nextFloat() * 90f;
            s.x = 178; s.y = 100;
            s.vx = (float) Math.cos(ang) * sp; s.vy = (float) Math.sin(ang) * sp - 40f;
            s.rgb = cols[r.nextInt(cols.length)];
            s.born = now;
            sparks.add(s);
        }
    }

    private void drawSparks(DrawContext c, long now, float dt) {
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(0, 0, 300);
        sparks.removeIf(s -> (now - s.born) / 1e6 > 1100);
        for (Spark s : sparks) {
            s.x += s.vx * dt; s.y += s.vy * dt; s.vy += 160f * dt; s.vx *= 0.98f;
            float t = (now - s.born) / 1e6f / 1100f;
            int a = (int) (255 * (1 - t));
            c.fill((int) s.x, (int) s.y, (int) s.x + 2, (int) s.y + 2, (a << 24) | s.rgb);
        }
        m.pop();
    }

    // ================= общие элементы =================
    private void panel(DrawContext c, int x, int y, int w, int h) {
        c.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        c.fill(x, y, x + w, y + h, 0xC8101A16);
        c.fill(x, y, x + w, y + 1, 0x30FFFFFF);
    }

    private void button(DrawContext c, String key, float x, float y, float w, float h, String label,
                        int topRgb, int botRgb, boolean enabled, float pulse, Runnable click, Supplier<List<Text>> tip) {
        float ha = hov.getOrDefault(key, 0f);
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(x + w / 2f, y + h / 2f, 0);
        m.scale(pulse * (1f + 0.04f * ha), pulse * (1f + 0.04f * ha), 1f);
        m.translate(-w / 2f, -h / 2f, 0);
        int iw = (int) w, ih = (int) h;
        int top = enabled ? topRgb : 0x6A6A72, bot = enabled ? botRgb : 0x34343A;
        c.fill(-1, -1, iw + 1, ih + 3, 0xFF000000);
        c.fill(0, ih - 1, iw, ih + 2, 0xFF000000 | (bot >> 1 & 0x7F7F7F));
        c.fillGradient(0, 0, iw, ih, 0xFF000000 | top, 0xFF000000 | bot);
        c.fill(1, 1, iw - 1, 2, 0x66FFFFFF);
        if (ha > 0.05f) c.fill(0, 0, iw, ih, ((int) (40 * ha) << 24) | 0xFFFFFF);
        if (ih >= 24) text(c, label, w / 2f, ih >= 40 ? 13 : 9, label.length() > 9 ? 0.9f : 1.15f, enabled ? 0xFFFFFFFF : 0xFF909098, 1);
        else text(c, label, w / 2f, (h - 5.5f) / 2f, 0.58f, enabled ? 0xFFFFFFFF : 0xFF909098, 1);
        m.pop();
        hit(x, y, w, h + 2, key, enabled ? click : () -> {
            if (key.equals("pick")) pick(); else HudModel.play(SoundEvents.ENTITY_VILLAGER_NO, 1.2f, 0.3f);
        }, tip);
    }

    private void hit(float x, float y, float w, float h, String key, Runnable click, Supplier<List<Text>> tip) {
        Hit h2 = new Hit();
        h2.x = x; h2.y = y; h2.w = w; h2.h = h; h2.key = key; h2.click = click; h2.tip = tip;
        hits.add(h2);
    }

    private void toastMsg(String s, boolean ok) {
        toast = s; toastOk = ok; toastAt = System.nanoTime();
        if (!ok) HudModel.play(SoundEvents.ENTITY_VILLAGER_NO, 1.0f, 0.6f);
    }

    private void drawToast(DrawContext c, long now) {
        float t = (now - toastAt) / 1e6f;
        if (t > 2600f || toast.isEmpty()) return;
        float a = t < 150f ? t / 150f : t > 2100f ? Math.max(0f, (2600f - t) / 500f) : 1f;
        float y = 22f + (t < 220f ? (1f - t / 220f) * -10f : 0f);
        float w = textRenderer.getWidth(toast) * 0.9f + 14f;
        int col = toastOk ? 0x2E8B57 : 0xB02A2A;
        float shake = !toastOk && t < 300f ? (float) Math.sin(t / 25f) * 2f : 0f;
        c.fill((int) (258 - w / 2 + shake) - 1, (int) y - 1, (int) (258 + w / 2 + shake) + 1, (int) y + 12, argb(0x000000, a));
        c.fill((int) (258 - w / 2 + shake), (int) y, (int) (258 + w / 2 + shake), (int) y + 11, argb(col, a * 0.95f));
        text(c, toast, 258 + shake, y + 1.8f, 0.9f, argb(0xFFFFFF, a), 1);
    }

    private List<String> wrap(String s, int maxPx) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        StringBuilder cur = new StringBuilder();
        for (String w : s.split(" ")) {
            String test = cur.length() == 0 ? w : cur + " " + w;
            if (textRenderer.getWidth(test) > maxPx && cur.length() > 0) { out.add(cur.toString()); cur = new StringBuilder(w); }
            else cur = new StringBuilder(test);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private String fit(String s, int maxPx, float scale) {
        if (textRenderer.getWidth(s) * scale <= maxPx) return s;
        while (s.length() > 1 && textRenderer.getWidth(s + "…") * scale > maxPx) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private static int argb(int rgb, float alpha) { return (Math.max(0, Math.min(255, (int) (alpha * 255))) << 24) | (rgb & 0xFFFFFF); }

    /** align: 0 — слева, 1 — по центру, 2 — справа */
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
