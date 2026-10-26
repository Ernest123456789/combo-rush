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
import net.minecraft.util.math.RotationAxis;

import java.util.*;
import java.util.function.Supplier;

/** Магазин в стиле Balatro: сукно, парящие карточки, ценники и крупные кнопки. Вся логика — на сервере */
public class ShopScreen extends Screen {
    // ---------------- данные ----------------
    static final class View {
        String id = "", name = "", desc = "", icon = "minecraft:paper", rar = "common", cat = "chips", ed = "";
        int price, lvl, sell;
        boolean canNeg, needsTarget, neg;
        ItemStack stack = ItemStack.EMPTY;
    }
    static final class Data {
        int coins, level, events, resets, essence, rank, rerollCost, slotCost, extra, cap;
        String species = "", promo = "", msg = "";
        boolean ok = true;
        List<View> offers = new ArrayList<>(), cOffers = new ArrayList<>(), owned = new ArrayList<>(), ownedCons = new ArrayList<>();
    }

    private static final class Hit {
        float x, y, w, h; String key; Runnable click; Supplier<List<Text>> tip;
        boolean contains(float px, float py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }
    private static final class Float2 { String text; int rgb; float x, y; long born; }
    private static final class Sel { char kind; int idx; String id; }

    private static final int DW = 420, DH = 250;
    private static final ItemStack COIN = new ItemStack(Items.GOLD_NUGGET);

    // ---------------- состояние экрана ----------------
    private Data d;
    private Sel sel = null;
    private String targetId = null;                    // расходник, ожидающий выбор усилителя
    private float u = 1, ox, oy, mx, my;
    private long lastNs = 0, dealAt = 0, toastAt = -10_000_000_000L;
    private double moneyShown;
    private String offerSig = "";
    private String toast = ""; private boolean toastOk = true;
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, Float> hov = new HashMap<>();
    private final Map<String, Long> jiggle = new HashMap<>();
    private final List<Float2> floats = new ArrayList<>();
    private String hoverKey = "";
    private Hit hoverHit = null;

    private ShopScreen(Data d) {
        super(Text.literal("Магазин"));
        this.d = d;
        this.moneyShown = d.coins;
        this.offerSig = sig(d.offers) + "|" + sig(d.cOffers);
        this.dealAt = System.nanoTime();
    }

    // ================= приём пакетов =================
    public static void onPacket(String kind, JsonObject o) {
        MinecraftClient mc = MinecraftClient.getInstance();
        switch (kind) {
            case "shop_open" -> mc.setScreen(new ShopScreen(parse(o)));
            case "shop" -> { if (mc.currentScreen instanceof ShopScreen s) s.apply(parse(o)); }
            case "shop_close" -> { if (mc.currentScreen instanceof ShopScreen) mc.setScreen(null); }
            default -> { }
        }
    }

    private static String str(JsonObject o, String k, String def) { return o.has(k) ? o.get(k).getAsString() : def; }
    private static int num(JsonObject o, String k) { return o.has(k) ? o.get(k).getAsInt() : 0; }

    private static List<View> views(JsonObject o, String key) {
        List<View> out = new ArrayList<>();
        if (!o.has(key)) return out;
        for (JsonElement el : o.getAsJsonArray(key)) {
            JsonObject j = el.getAsJsonObject();
            View v = new View();
            v.id = str(j, "id", ""); v.name = str(j, "name", "?"); v.desc = str(j, "desc", "");
            v.icon = str(j, "icon", "minecraft:paper"); v.rar = str(j, "rar", "common"); v.cat = str(j, "cat", "chips");
            v.ed = str(j, "ed", ""); v.price = num(j, "price"); v.lvl = num(j, "lvl"); v.sell = num(j, "sell");
            v.canNeg = j.has("canNeg") && j.get("canNeg").getAsBoolean();
            v.needsTarget = j.has("needsTarget") && j.get("needsTarget").getAsBoolean();
            v.neg = j.has("neg") && j.get("neg").getAsBoolean();
            Identifier id = Identifier.tryParse(v.icon);
            v.stack = id != null && Registries.ITEM.containsId(id) ? new ItemStack(Registries.ITEM.get(id)) : ItemStack.EMPTY;
            out.add(v);
        }
        return out;
    }

    private static Data parse(JsonObject o) {
        Data d = new Data();
        d.coins = num(o, "coins"); d.level = num(o, "level"); d.events = num(o, "events"); d.resets = num(o, "resets");
        d.essence = num(o, "essence"); d.rank = num(o, "rank"); d.rerollCost = num(o, "rerollCost");
        d.slotCost = num(o, "slotCost"); d.extra = num(o, "extra"); d.cap = num(o, "cap");
        d.species = str(o, "species", ""); d.promo = str(o, "promo", ""); d.msg = str(o, "msg", "");
        d.ok = !o.has("ok") || o.get("ok").getAsBoolean();
        d.offers = views(o, "offers"); d.cOffers = views(o, "cOffers");
        d.owned = views(o, "owned"); d.ownedCons = views(o, "ownedCons");
        return d;
    }

    private static String sig(List<View> l) {
        StringBuilder sb = new StringBuilder();
        for (View v : l) sb.append(v.id).append(',');
        return sb.toString();
    }

    /** Новое состояние от сервера: звуки, анимации, сброс устаревшего выбора */
    private void apply(Data n) {
        long now = System.nanoTime();
        Data old = d;

        if (!n.msg.isEmpty()) {
            toast = n.msg; toastOk = n.ok; toastAt = now;
            if (n.ok) {
                HudModel.play(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.25f, 0.5f);
                HudModel.play(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.5f);
            } else {
                HudModel.play(SoundEvents.ENTITY_VILLAGER_NO, 1.0f, 0.6f);
            }
        }
        if (n.coins != old.coins) {
            Float2 f = new Float2();
            int diff = n.coins - old.coins;
            f.text = (diff > 0 ? "+" : "") + diff; f.rgb = diff > 0 ? 0x8EF0A0 : 0xFF7A7A;
            f.x = 52; f.y = 24; f.born = now;
            floats.add(f);
        }
        // карточки, которые появились или улучшились, подпрыгивают
        Map<String, String> oldMark = new HashMap<>();
        for (View v : old.owned) oldMark.put(v.id, v.lvl + v.ed);
        for (View v : n.owned) if (!(v.lvl + v.ed).equals(oldMark.get(v.id))) jiggle.put(v.id, now);
        Set<String> oldCons = new HashSet<>();
        for (View v : old.ownedCons) oldCons.add(v.id);
        for (View v : n.ownedCons) if (!oldCons.contains(v.id)) jiggle.put(v.id, now);

        String newSig = sig(n.offers) + "|" + sig(n.cOffers);
        if (!newSig.equals(offerSig)) { offerSig = newSig; dealAt = now; }

        d = n;
        if (sel != null && !selValid()) sel = null;
        if (targetId != null && d.ownedCons.stream().noneMatch(v -> v.id.equals(targetId))) targetId = null;
        if (targetId != null && n.ok && !n.msg.isEmpty()) targetId = null;
    }

    private boolean selValid() {
        List<View> l = listOf(sel.kind);
        return sel.idx >= 0 && sel.idx < l.size() && l.get(sel.idx).id.equals(sel.id);
    }

    private List<View> listOf(char kind) {
        return switch (kind) { case 'O' -> d.offers; case 'C' -> d.cOffers; case 'W' -> d.owned; default -> d.ownedCons; };
    }

    private void send(String action, int a, int b, String c) {
        ClientPlayNetworking.send(new ShopActionPayload(action, a, b, c));
    }

    // ================= Screen =================
    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) { }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && (targetId != null || sel != null)) {      // Esc: сначала отмена выбора
            targetId = null; sel = null;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        float px = (float) ((mouseX - ox) / u), py = (float) ((mouseY - oy) / u);
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.contains(px, py)) { if (h.click != null) h.click.run(); return true; }
        }
        sel = null; targetId = null;                                    // клик по пустому месту
        return true;
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;

        if ("EVENT".equals(HudModel.phase)) { MinecraftClient.getInstance().setScreen(null); return; }

        u = Math.max(0.5f, Math.min(width / (float) DW, height / (float) DH));
        ox = (width - DW * u) / 2f;
        oy = (height - DH * u) / 2f;
        mx = (mouseX - ox) / u;
        my = (mouseY - oy) / u;

        // что под курсором (по прошлому кадру) — для анимации наведения
        hoverHit = null;
        for (int i = hits.size() - 1; i >= 0; i--) if (hits.get(i).contains(mx, my)) { hoverHit = hits.get(i); break; }
        hoverKey = hoverHit == null ? "" : hoverHit.key;
        Set<String> keys = new HashSet<>(hov.keySet());
        keys.add(hoverKey);
        for (String k : keys) {
            float cur = hov.getOrDefault(k, 0f);
            float target = k.equals(hoverKey) ? 1f : 0f;
            hov.put(k, cur + (target - cur) * Math.min(1f, dt * 14f));
        }
        moneyShown += (d.coins - moneyShown) * Math.min(1f, dt * 8f);
        if (Math.abs(d.coins - moneyShown) < 0.5) moneyShown = d.coins;

        hits.clear();
        drawBackground(c, now);

        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(ox, oy, 0);
        m.scale(u, u, 1f);
        drawTitle(c, now);
        drawSidebar(c, now);
        drawOffers(c, now);
        drawTray(c, now);
        drawFloats(c, now);
        drawToast(c, now);
        m.pop();

        if (hoverHit != null && hoverHit.tip != null) c.drawTooltip(textRenderer, hoverHit.tip.get(), mouseX, mouseY);
    }

    // ================= фон: сукно с мерцанием =================
    private void drawBackground(DrawContext c, long now) {
        c.fillGradient(0, 0, width, height, 0xFF14493A, 0xFF07170F);
        for (int y = 0; y < height; y += 4) c.fill(0, y, width, y + 1, 0x0AFFFFFF);
        for (int i = 0; i < 220; i++) {
            int h = i * 7919 + 13;
            int x = Math.abs(h * 31) % Math.max(1, width), y = Math.abs(h * 17 + i * 101) % Math.max(1, height);
            float tw = 0.5f + 0.5f * (float) Math.sin(now / 1.2e9 + i);
            c.fill(x, y, x + 1, y + 1, ((int) (30 * tw) << 24) | 0xFFFFFF);
        }
        float pulse = 0.85f + 0.15f * (float) Math.sin(now / 8e8);
        for (int i = 0; i < 8; i++) {
            int rad = (int) ((height * 0.14f + i * height * 0.07f) * pulse);
            discAbs(c, width / 2, (int) (height * 0.38f), rad, ((6 + i) << 24) | 0x40E0A0);
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

    private void discAbs(DrawContext c, int cx, int cy, int r, int argb) {
        for (int dy = -r; dy <= r; dy += 2) {
            int hw = (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
            c.fill(cx - hw, cy + dy, cx + hw, cy + dy + 2, argb);
        }
    }

    // ================= заголовок с «волной» букв =================
    private void drawTitle(DrawContext c, long now) {
        String t = "МАГАЗИН РЕЖИССЁРА";
        float scale = 1.7f;
        float total = textRenderer.getWidth(t) * scale;
        float x = 258 - total / 2f;
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
        panel(c, 6, 4, 92, 28);
        MatrixStack m = c.getMatrices();
        m.push(); m.translate(10, 9, 0); m.scale(1.3f, 1.3f, 1f); c.drawItem(COIN, 0, 0); m.pop();
        text(c, "МОНЕТЫ", 34, 8, 0.55f, 0xFFA0A0A8, 0);
        float cb = Math.min(1f, Math.abs(d.coins - (float) moneyShown) / 5f);
        text(c, Long.toString(Math.round(moneyShown)), 34, 15, 1.5f + 0.25f * cb, 0xFFF2B72B, 0);

        int left = Math.max(0, HudModel.secs - (int) ((now - HudModel.secsAt) / 1_000_000_000L));
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 3e8);
        String timer = String.format(Locale.ROOT, "%d:%02d", left / 60, left % 60);
        button(c, "next", 6, 36, 92, 44, "К СОБЫТИЮ", 0xFF5A4A, 0xC02828, true, 1.0f + 0.04f * pulse,
            () -> MinecraftClient.getInstance().setScreen(null),
            () -> List.of(Text.literal("§cЗакрыть магазин"), Text.literal("§7Событие начнётся само через " + timer)));
        text(c, "до события " + timer, 52, 61, 0.7f, 0xFFFFE0D0, 1);
        text(c, "ЗАКРЫТЬ", 52, 70, 0.55f, 0xFFFFC0B0, 1);

        boolean canReroll = d.coins >= d.rerollCost;
        button(c, "reroll", 6, 84, 92, 26, "ПЕРЕКРУТ", 0x6FE08A, 0x1E8A4A, canReroll, 1f,
            () -> send("reroll", 0, 0, ""),
            () -> List.of(Text.literal("§aПерекрутить витрину"), Text.literal("§7Обновляет усилители и расходники"),
                Text.literal((canReroll ? "§e" : "§c") + "Цена: " + d.rerollCost + " монет")));
        text(c, d.rerollCost + "", 82, 99, 0.9f, canReroll ? 0xFFFFE08A : 0xFFFF8A8A, 1);

        panel(c, 6, 114, 92, 36);
        text(c, fit(d.species, 84, 0.7f) + "  " + d.rank + "/5", 10, 118, 0.7f, 0xFFE8D9A0, 0);
        text(c, "Уровень " + d.level + "  ·  события " + d.events + "/50", 10, 127, 0.6f, 0xFFB8B8C0, 0);
        text(c, "Сбросов " + d.resets + "  ·  эссенция " + d.essence, 10, 135, 0.6f, 0xFFB8B8C0, 0);
        if (!d.promo.isEmpty()) text(c, fit("★ " + d.promo, 84, 0.6f), 10, 143, 0.6f, 0xFFE070FF, 0);

        text(c, "РАСХОДНИКИ", 8, 154, 0.55f, 0xFF9AA8A0, 0);
        for (int i = 0; i < 2; i++) {
            float x = 8 + i * 44, y = 163;
            if (i < d.ownedCons.size()) {
                View v = d.ownedCons.get(i);
                boolean target = v.id.equals(targetId);
                boolean selected = sel != null && sel.kind == 'K' && sel.idx == i;
                final int idx = i;
                String key = "K" + i;
                drawCard(c, v, key, x, y, 38, 50, now, 40 + i, selected || target, false, 0f, false);
                hit(x, y, 38, 50, key, () -> select('K', idx, v.id), () -> cardTip(v, "Расходник"));
            } else {
                dashed(c, (int) x, (int) y, 38, 50, 0x55FFFFFF);
            }
        }
        if (sel != null && sel.kind == 'K' && sel.idx < d.ownedCons.size()) {
            View v = d.ownedCons.get(sel.idx);
            int idx = sel.idx;
            button(c, "usec", 8, 216, 88, 11, v.needsTarget ? "ВЫБРАТЬ ЦЕЛЬ" : "ИСПОЛЬЗОВАТЬ", 0x6FA8FF, 0x2358C7, true, 1f,
                () -> {
                    if (v.needsTarget) { if (d.owned.isEmpty()) toastMsg("У тебя нет усилителей.", false); else targetId = v.id; }
                    else send("usec", idx, -1, "");
                    sel = null;
                }, () -> List.of(Text.literal("§b" + v.name), Text.literal(v.needsTarget ? "§7Затем нажми на усилитель внизу" : "§7Применится сразу")));
            button(c, "sellc", 8, 229, 88, 11, "ПРОДАТЬ  +" + v.sell, 0xFFD45B, 0xA8740E, true, 1f,
                () -> { send("sellc", idx, 0, ""); sel = null; },
                () -> List.of(Text.literal("§6Продать «" + v.name + "»"), Text.literal("§7Вернёт " + v.sell + " монет")));
        }
    }

    // ================= витрина: усилители и расходники =================
    private void drawOffers(DrawContext c, long now) {
        text(c, "УСИЛИТЕЛИ", 110, 33, 0.7f, 0xFFB8F0D0, 0);
        text(c, "РАСХОДНИКИ", 326, 33, 0.7f, 0xFFB8F0D0, 0);
        c.fill(110, 41, 322, 42, 0x33FFFFFF);
        c.fill(326, 41, 410, 42, 0x33FFFFFF);
        if (d.offers.isEmpty()) text(c, "Витрина пуста — перекрути", 216, 80, 0.8f, 0x99FFFFFF, 1);

        for (int i = 0; i < d.offers.size(); i++) {
            View v = d.offers.get(i);
            float x = 110 + i * 70, y = 46;
            float t = (now - dealAt) / 1e6f - i * 90f;
            float dp = easeOut(t / 450f);
            if (dp <= 0.01f) continue;
            boolean selected = sel != null && sel.kind == 'O' && sel.idx == i;
            boolean afford = d.coins >= v.price;
            String key = "O" + i;
            final int idx = i;
            drawCard(c, v, key, x, y, 56, 76, now, i, selected, false, (1f - dp) * -90f, !afford);
            hit(x, y, 56, 76, key, () -> select('O', idx, v.id), () -> cardTip(v, "Цена: " + v.price + " монет"));
            drawPrice(c, x + 10, y + 79, v.price, afford);
            if (selected) {
                button(c, "buy" + i, x, y + 92, 56, 11, "КУПИТЬ", 0x6FE08A, 0x1E8A4A, afford, 1f,
                    () -> send("buy", idx, 0, ""),
                    () -> List.of(Text.literal("§aКупить «" + v.name + "»"), Text.literal((afford ? "§e" : "§c") + "Цена: " + v.price + " монет")));
                if (v.canNeg) {
                    button(c, "negg" + i, x, y + 105, 56, 11, "НЕГ. БЕЗ ИГРЫ", 0xB070E0, 0x5E1E9E, afford, 1f,
                        () -> send("buy", idx, 0, "game"),
                        () -> List.of(Text.literal("§5Негативный: +1 слот"), Text.literal("§7Теряет игровую часть (скорость, зрение…)"),
                            Text.literal("§7Очковая часть остаётся")));
                    button(c, "negs" + i, x, y + 118, 56, 11, "НЕГ. БЕЗ ОЧКОВ", 0xB070E0, 0x5E1E9E, afford, 1f,
                        () -> send("buy", idx, 0, "score"),
                        () -> List.of(Text.literal("§5Негативный: +1 слот"), Text.literal("§7Теряет очковую часть"),
                            Text.literal("§7Игровая часть остаётся")));
                }
            }
        }

        for (int i = 0; i < d.cOffers.size(); i++) {
            View v = d.cOffers.get(i);
            float x = 326 + i * 44, y = 46;
            float t = (now - dealAt) / 1e6f - (3 + i) * 90f;
            float dp = easeOut(t / 450f);
            if (dp <= 0.01f) continue;
            boolean selected = sel != null && sel.kind == 'C' && sel.idx == i;
            boolean afford = d.coins >= v.price && d.ownedCons.size() < 2;
            String key = "C" + i;
            final int idx = i;
            drawCard(c, v, key, x, y, 40, 56, now, 10 + i, selected, false, (1f - dp) * -90f, !afford);
            hit(x, y, 40, 56, key, () -> select('C', idx, v.id), () -> cardTip(v, "Цена: " + v.price + " монет"));
            drawPrice(c, x + 2, y + 59, v.price, d.coins >= v.price);
            if (selected) {
                button(c, "buyc" + i, x, y + 73, 40, 11, "КУПИТЬ", 0x6FE08A, 0x1E8A4A, afford, 1f,
                    () -> send("buyc", idx, 0, ""),
                    () -> List.of(Text.literal("§aКупить «" + v.name + "»"),
                        Text.literal(d.ownedCons.size() >= 2 ? "§cСлоты расходников заняты" : "§e" + v.price + " монет")));
            }
        }
    }

    // ================= нижняя панель: твои усилители =================
    private void drawTray(DrawContext c, long now) {
        panel(c, 102, 176, 312, 70);
        text(c, "ТВОИ УСИЛИТЕЛИ  " + d.owned.size() + "/" + d.cap, 108, 180, 0.7f, 0xFFB8F0D0, 0);

        boolean canSlot = d.extra < 4 && d.coins >= d.slotCost;
        button(c, "slot", 340, 177, 70, 10, d.extra >= 4 ? "МАКС. СЛОТОВ" : "+ СЛОТ  " + d.slotCost, 0xE070FF, 0x7A1E9E, canSlot, 1f,
            () -> send("slot", 0, 0, ""),
            () -> List.of(Text.literal("§dДополнительный слот"), Text.literal("§7Куплено: " + d.extra + "/4"),
                Text.literal("§7Цена: " + d.slotCost + " монет")));

        if (targetId != null) {
            float p = 0.5f + 0.5f * (float) Math.sin(now / 2.2e8);
            text(c, "Выбери усилитель для расходника (Esc — отмена)", 258, 168, 0.75f, rgbMix(0xFFE08A, 0xFFFFFF, p) | 0xFF000000, 1);
        }

        int slots = Math.max(d.cap, d.owned.size());
        for (int i = 0; i < 9; i++) {
            float x = 110 + i * 33, y = 190;
            if (i < d.owned.size()) {
                View v = d.owned.get(i);
                boolean selected = sel != null && sel.kind == 'W' && sel.idx == i;
                String key = "W" + i;
                final int idx = i;
                drawCard(c, v, key, x, y, 30, 40, now, 20 + i, selected, targetId != null, 0f, false);
                hit(x, y, 30, 40, key, () -> {
                    if (targetId != null) {
                        for (int k = 0; k < d.ownedCons.size(); k++)
                            if (d.ownedCons.get(k).id.equals(targetId)) { send("usec", k, idx, ""); break; }
                        targetId = null;
                    } else select('W', idx, v.id);
                }, () -> cardTip(v, "Продажа: +" + v.sell + " монет"));
                if (selected && targetId == null) {
                    button(c, "sell" + i, Math.min(x - 6, 366), 233, 44, 11, "ПРОДАТЬ +" + v.sell, 0xFFD45B, 0xA8740E, true, 1f,
                        () -> { send("sell", idx, 0, ""); sel = null; },
                        () -> List.of(Text.literal("§6Продать «" + v.name + "»"), Text.literal("§7Вернёт " + v.sell + " монет")));
                }
            } else if (i < slots) {
                dashed(c, (int) x, (int) y, 30, 40, 0x66FFFFFF);
                text(c, "+", x + 15, y + 15, 1.2f, 0x44FFFFFF, 1);
            } else {
                dashed(c, (int) x, (int) y, 30, 40, 0x22FFFFFF);
            }
        }
    }

    // ================= карточка =================
    private void select(char kind, int idx, String id) {
        if (sel != null && sel.kind == kind && sel.idx == idx) { sel = null; return; }
        Sel s = new Sel(); s.kind = kind; s.idx = idx; s.id = id;
        sel = s;
        HudModel.play(SoundEvents.ENTITY_ITEM_PICKUP, 1.4f, 0.3f);
    }

    private void drawCard(DrawContext c, View v, String key, float x, float y, float w, float h, long now, int seed,
                          boolean selected, boolean glow, float dealOffset, boolean dim) {
        MatrixStack m = c.getMatrices();
        float ha = hov.getOrDefault(key, 0f);
        long jt = (now - jiggle.getOrDefault(v.id, -100_000_000_000L)) / 1_000_000L;
        float jig = jt < 700 ? (float) Math.exp(-jt / 110.0) : 0f;
        float tilt = Math.max(-0.5f, Math.min(0.5f, (mx - (x + w / 2f)) / w));
        float angle = ha * tilt * 14f + (float) Math.sin(now / 9e8 + seed) * 1.1f + 8f * jig * (float) Math.sin(jt / 26.0)
            + dealOffset * 0.12f;
        float scale = 1f + 0.09f * ha + (selected ? 0.07f : 0f) + 0.15f * jig;
        float bob = (float) Math.sin(now / 7e8 + seed * 1.3) * 0.8f;
        float yOff = -5f * ha - (selected ? 7f : 0f) + bob + dealOffset;

        c.fill((int) (x + 3), (int) (y + h - 1), (int) (x + w - 3), (int) (y + h + 3), 0x45000000);   // тень

        int iw = (int) w, ih = (int) h;
        int[] pal = palette(v);
        int border = v.neg ? 0xFFFFFFFF : rarBorder(v.rar, now);
        if (selected) border = 0xFFFFE066;
        else if (glow) border = rgbMix(0xFFE066, border & 0xFFFFFF, 0.5f + 0.5f * (float) Math.sin(now / 2.5e8)) | 0xFF000000;

        m.push();
        m.translate(x + w / 2f, y + h / 2f + yOff, 0);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
        m.scale(scale, scale, 1f);
        m.translate(-w / 2f, -h / 2f, 0);

        c.fill(-1, -1, iw + 1, ih + 1, border);
        c.fill(0, 0, iw, ih, 0xFF000000);
        c.fillGradient(1, 1, iw - 1, ih - 1, 0xFF000000 | pal[0], 0xFF000000 | pal[1]);
        c.fill(2, 2, iw - 2, 3, 0x55FFFFFF);

        c.fill(1, 1, iw - 1, 7, 0x90000000);
        text(c, rarLabel(v.rar), w / 2f, 2f, 0.4f * Math.min(1.2f, w / 40f), rarText(v.rar), 1);

        if (!v.stack.isEmpty()) {
            float is = w * 0.55f / 16f;
            m.push();
            m.translate((w - 16 * is) / 2f, 9f + (h * 0.06f), 0);
            m.scale(is, is, 1f);
            c.drawItem(v.stack, 0, 0);
            m.pop();
        }

        float rh = h * 0.27f;
        c.fill(1, (int) (ih - rh), iw - 1, ih - 1, 0xC8000000);
        float ns = Math.max(0.36f, Math.min(0.55f, 0.5f * w / 56f * 1.1f));
        List<String> lines = wrapName(v.name, (int) ((w - 4) / ns));
        float ly = ih - rh + 2f;
        for (int i = 0; i < lines.size() && i < 2; i++) {
            text(c, lines.get(i), w / 2f, ly + i * (8f * ns * 1.2f), ns, 0xFFFFFFFF, 1);
        }

        if (v.lvl > 0) { c.fill(1, 8, 13, 14, 0xB0000000); text(c, "Ур" + v.lvl, 2, 8.5f, 0.42f, 0xFFFFE08A, 0); }
        if (!v.ed.isEmpty()) {
            c.fill(iw - 9, 8, iw - 1, 14, 0xB0000000);
            text(c, edLetter(v.ed), iw - 5f, 8f, 0.5f, edColor(v.ed, now), 1);
        }
        if (v.neg) c.fill(1, 1, iw - 1, ih - 1, 0x55000000);

        double ph = ((now / 1e9) + seed * 0.7) % 4.0 / 4.0;
        if (ph < 0.28) {
            int sx = (int) (-8 + (iw + 16) * (ph / 0.28));
            int x1 = Math.max(1, sx), x2 = Math.min(iw - 1, sx + 6);
            if (x2 > x1) c.fill(x1, 1, x2, ih - 1, 0x30FFFFFF);
        }
        if (dim) c.fill(0, 0, iw, ih, 0x78101014);
        m.pop();
    }

    private List<String> wrapName(String s, int maxUnits) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String test = cur.length() == 0 ? word : cur + " " + word;
            if (textRenderer.getWidth(test) > maxUnits && cur.length() > 0) { out.add(cur.toString()); cur = new StringBuilder(word); }
            else cur = new StringBuilder(test);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private void drawPrice(DrawContext c, float x, float y, int price, boolean afford) {
        int iw = 36, ih = 12;
        c.fill((int) x - 1, (int) y - 1, (int) x + iw + 1, (int) y + ih + 1, 0xFF000000);
        c.fillGradient((int) x, (int) y, (int) x + iw, (int) y + ih, afford ? 0xFFFFE08A : 0xFFB06060, afford ? 0xFFC8901A : 0xFF702020);
        c.fill((int) x + 1, (int) y + 1, (int) x + iw - 1, (int) y + 2, 0x66FFFFFF);
        MatrixStack m = c.getMatrices();
        m.push(); m.translate(x + 3, y + 2, 0); m.scale(0.55f, 0.55f, 1f); c.drawItem(COIN, 0, 0); m.pop();
        text(c, Integer.toString(price), x + iw - 4, y + 2.5f, 0.9f, afford ? 0xFF3A2A00 : 0xFFFFE0E0, 2);
    }

    // ================= элементы интерфейса =================
    private void panel(DrawContext c, int x, int y, int w, int h) {
        c.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        c.fill(x, y, x + w, y + h, 0xC8101A16);
        c.fill(x, y, x + w, y + 1, 0x30FFFFFF);
    }

    /** Кнопка в стиле Balatro: цветное тело, «толщина» снизу, подсвечивается при наведении */
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
        if (!label.isEmpty() && ih >= 24) text(c, label, w / 2f, 5, 1.15f, enabled ? 0xFFFFFFFF : 0xFF909098, 1);
        else if (!label.isEmpty()) text(c, label, w / 2f, (h - 5.5f) / 2f, 0.58f, enabled ? 0xFFFFFFFF : 0xFF909098, 1);
        m.pop();
        hit(x, y, w, h + 2, key, enabled ? click : () -> HudModel.play(SoundEvents.ENTITY_VILLAGER_NO, 1.2f, 0.3f), tip);
    }

    private void hit(float x, float y, float w, float h, String key, Runnable click, Supplier<List<Text>> tip) {
        Hit h2 = new Hit();
        h2.x = x; h2.y = y; h2.w = w; h2.h = h; h2.key = key; h2.click = click; h2.tip = tip;
        hits.add(h2);
    }

    private void toastMsg(String s, boolean ok) { toast = s; toastOk = ok; toastAt = System.nanoTime(); }

    private void drawToast(DrawContext c, long now) {
        float t = (now - toastAt) / 1e6f;
        if (t > 2600f || toast.isEmpty()) return;
        float a = t < 150f ? t / 150f : t > 2100f ? Math.max(0f, (2600f - t) / 500f) : 1f;
        float y = 30f + (t < 220f ? (1f - t / 220f) * -10f : 0f);
        float w = textRenderer.getWidth(toast) * 0.9f + 14f;
        int col = toastOk ? 0x2E8B57 : 0xB02A2A;
        float shake = !toastOk && t < 300f ? (float) Math.sin(t / 25f) * 2f : 0f;
        c.fill((int) (258 - w / 2 + shake) - 1, (int) y - 1, (int) (258 + w / 2 + shake) + 1, (int) y + 12, argb(0x000000, a));
        c.fill((int) (258 - w / 2 + shake), (int) y, (int) (258 + w / 2 + shake), (int) y + 11, argb(col, a * 0.95f));
        text(c, toast, 258 + shake, y + 1.8f, 0.9f, argb(0xFFFFFF, a), 1);
    }

    private void drawFloats(DrawContext c, long now) {
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(0, 0, 300);
        floats.removeIf(f -> (now - f.born) / 1e6 > 1100);
        for (Float2 f : floats) {
            float t = (now - f.born) / 1e6f / 1100f;
            int a = Math.max(30, (int) (255 * (1 - t * t)));
            text(c, f.text, f.x, f.y - 22f * (1 - (1 - t) * (1 - t)), 1.3f, (a << 24) | f.rgb, 1);
        }
        m.pop();
    }

    // ================= подсказки =================
    private List<Text> cardTip(View v, String last) {
        List<Text> t = new ArrayList<>();
        t.add(Text.literal(rarCode(v.rar) + "§l" + v.name));
        t.add(Text.literal("§7" + rarLongLabel(v.rar) + " · " + catLabel(v)));
        for (String l : wrapPx(v.desc, 170)) t.add(Text.literal("§f" + l));
        if (v.lvl > 0) t.add(Text.literal("§eУровень: " + v.lvl + "/5 §7(+" + v.lvl * 20 + "% к плюсам)"));
        if (!v.ed.isEmpty()) t.add(Text.literal("§bИздание: §f" + edName(v.ed) + " §7(" + edDesc(v.ed) + ")"));
        if (v.neg) t.add(Text.literal("§cНегативный усилитель (+1 слот)"));
        t.add(Text.literal("§e" + last));
        return t;
    }

    private List<String> wrapPx(String s, int maxPx) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String w : s.split(" ")) {
            String test = cur.length() == 0 ? w : cur + " " + w;
            if (textRenderer.getWidth(test) > maxPx && cur.length() > 0) { out.add(cur.toString()); cur = new StringBuilder(w); }
            else cur = new StringBuilder(test);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    // ================= цвета и мелочи =================
    private static int[] palette(View v) {
        if (v.neg) return new int[] {0x3A3A3A, 0x050505};
        return switch (v.cat) {
            case "mult"   -> new int[] {0xFF6B6B, 0xA82020};
            case "econ"   -> new int[] {0xFFD45B, 0xA8740E};
            case "game"   -> new int[] {0x58D68D, 0x1E7A44};
            case "hybrid" -> new int[] {0xC084FC, 0x5E1E9E};
            case "team"   -> new int[] {0x5EEAD4, 0x0F766E};
            case "cons"   -> new int[] {0xEADFBF, 0x7C6C42};
            default       -> new int[] {0x5B9BFF, 0x1B46A8};
        };
    }

    private static String catLabel(View v) {
        return switch (v.cat) {
            case "mult" -> "множитель"; case "econ" -> "экономика"; case "game" -> "игровой";
            case "hybrid" -> "гибрид"; case "team" -> "команда"; case "cons" -> "расходник"; default -> "фишки";
        };
    }

    private static int rarBorder(String rar, long now) {
        float pulse = 0.78f + 0.22f * (float) Math.sin(now / 3.5e8);
        return switch (rar) {
            case "uncommon"  -> 0xFF5CE08A;
            case "rare"      -> 0xFF4C9BFF;
            case "epic"      -> mulRgb(0xB45BFF, pulse);
            case "legendary" -> mulRgb(0xFFC040, pulse);
            default          -> 0xFFB8B8C0;
        };
    }
    private static int mulRgb(int rgb, float k) {
        int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * k + 25)), g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * k + 25)),
            b = Math.min(255, (int) ((rgb & 0xFF) * k + 25));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
    private static int rarText(String r) {
        return switch (r) { case "uncommon" -> 0xFF8BFFA8; case "rare" -> 0xFF8EC8FF; case "epic" -> 0xFFD9A0FF;
                            case "legendary" -> 0xFFFFD060; default -> 0xFFE0E0E0; };
    }
    private static String rarLabel(String r) {
        return switch (r) { case "uncommon" -> "НЕОБЫЧНЫЙ"; case "rare" -> "РЕДКИЙ"; case "epic" -> "ЭПИЧЕСКИЙ";
                            case "legendary" -> "ЛЕГЕНДАРНЫЙ"; default -> "ОБЫЧНЫЙ"; };
    }
    private static String rarLongLabel(String r) {
        return switch (r) { case "uncommon" -> "Необычный"; case "rare" -> "Редкий"; case "epic" -> "Эпический";
                            case "legendary" -> "Легендарный"; default -> "Обычный"; };
    }
    private static String rarCode(String r) {
        return switch (r) { case "uncommon" -> "§a"; case "rare" -> "§9"; case "epic" -> "§5"; case "legendary" -> "§6"; default -> "§f"; };
    }
    private static String edLetter(String e) { return switch (e) { case "foil" -> "Ф"; case "holo" -> "Г"; case "poly" -> "П"; default -> ""; }; }
    private static String edName(String e) { return switch (e) { case "foil" -> "Фольга"; case "holo" -> "Голограмма"; case "poly" -> "Полихром"; default -> e; }; }
    private static String edDesc(String e) {
        return switch (e) { case "foil" -> "+25 фишек к каждому действию"; case "holo" -> "+0.4 к множителю"; case "poly" -> "×1.2 к множителю"; default -> ""; };
    }
    private static int edColor(String e, long now) {
        return switch (e) {
            case "foil" -> 0xFFA8E0FF; case "holo" -> 0xFFFF9AE0;
            default -> 0xFF000000 | rgbMix(0xFF7AD9, 0x7AF0FF, 0.5f + 0.5f * (float) Math.sin(now / 3e8));
        };
    }
    private static int rgbMix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }
    private static int argb(int rgb, float alpha) { return (Math.max(0, Math.min(255, (int) (alpha * 255))) << 24) | (rgb & 0xFFFFFF); }
    private static float easeOut(float v) { v = Math.max(0f, Math.min(1f, v)); return 1f - (1f - v) * (1f - v) * (1f - v); }

    private String fit(String s, int maxPx, float scale) {
        if (textRenderer.getWidth(s) * scale <= maxPx) return s;
        while (s.length() > 1 && textRenderer.getWidth(s + "…") * scale > maxPx) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private void dashed(DrawContext c, int x, int y, int w, int h, int color) {
        for (int i = 0; i < w; i += 4) {
            c.fill(x + i, y, Math.min(x + w, x + i + 2), y + 1, color);
            c.fill(x + i, y + h - 1, Math.min(x + w, x + i + 2), y + h, color);
        }
        for (int j = 0; j < h; j += 4) {
            c.fill(x, y + j, x + 1, Math.min(y + h, y + j + 2), color);
            c.fill(x + w - 1, y + j, x + w, Math.min(y + h, y + j + 2), color);
        }
    }

    /** align: 0 — слева, 1 — по центру, 2 — справа */
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
}
