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
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import java.util.*;
import java.util.function.Supplier;

/** Прилавок Торговца Редстоуна: Сердце голема по центру, справа — три кейса со скидкой (в стиле остальных меню мода) */
public class TraderScreen extends Screen {
    private static final int DW = 420, DH = 250;
    private static final ItemStack ESSENCE = new ItemStack(Items.AMETHYST_SHARD);
    private static final ItemStack DIAMOND = new ItemStack(Items.DIAMOND);

    static final class Offer {
        String key = "", name = "", desc = "", rar = "common", cur = "essence";
        int base, price, stock, owned;
        ItemStack stack = ItemStack.EMPTY;
        boolean diamond() { return cur.equals("diamond"); }
    }
    static final class Data {
        int diamonds, essence, left, discount = 25, heartDia = 24, heartEss = 300;
        String msg = ""; boolean ok = true, gone = false;
        List<Offer> offers = new ArrayList<>();
    }
    private static final class Hit {
        float x, y, w, h; String key; Runnable click; Supplier<List<Text>> tip;
        boolean contains(float px, float py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }

    private Data d;
    private float u = 1, ox, oy, mx, my;
    private long lastNs = 0, openAt, leftAt, toastAt = -10_000_000_000L, buyAt = -10_000_000_000L;
    private String toast = ""; private boolean toastOk = true;
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, Float> hov = new HashMap<>();
    private Hit hoverHit;

    private TraderScreen(Data d) {
        super(Text.literal("Торговец Редстоуна"));
        this.d = d;
        this.openAt = this.leftAt = System.nanoTime();
        if (!d.msg.isEmpty()) { toast = d.msg; toastOk = d.ok; toastAt = openAt; }
    }

    // ================= пакеты =================
    public static void onPacket(String kind, JsonObject o) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Data n = parse(o);
        if (kind.equals("trader_open")) { mc.setScreen(new TraderScreen(n)); HudModel.play(SoundEvents.ENTITY_WANDERING_TRADER_REAPPEARED, 1.1f, 0.5f); return; }
        if (!(mc.currentScreen instanceof TraderScreen s)) return;
        if (n.gone) { s.toast = "Торговец ушёл"; s.toastOk = false; s.toastAt = System.nanoTime(); return; }
        s.apply(n);
    }

    private static String str(JsonObject o, String k, String def) { return o.has(k) ? o.get(k).getAsString() : def; }
    private static int num(JsonObject o, String k, int def) { return o.has(k) ? o.get(k).getAsInt() : def; }

    private static Data parse(JsonObject o) {
        Data d = new Data();
        d.gone = o.has("gone");
        d.diamonds = num(o, "diamonds", 0); d.essence = num(o, "essence", 0); d.left = num(o, "left", 0);
        d.discount = num(o, "discount", 25); d.heartDia = num(o, "heartDia", 24); d.heartEss = num(o, "heartEss", 300);
        d.msg = str(o, "msg", ""); d.ok = !o.has("ok") || o.get("ok").getAsBoolean();
        if (o.has("offers")) for (JsonElement el : o.getAsJsonArray("offers")) {
            JsonObject j = el.getAsJsonObject();
            Offer f = new Offer();
            f.key = str(j, "key", ""); f.name = str(j, "name", "?"); f.desc = str(j, "desc", ""); f.rar = str(j, "rar", "common");
            f.cur = str(j, "cur", "essence"); f.base = num(j, "base", 0); f.price = num(j, "price", 0);
            f.stock = num(j, "stock", 0); f.owned = num(j, "owned", 0);
            Identifier id = Identifier.tryParse(str(j, "item", ""));
            f.stack = id != null && Registries.ITEM.containsId(id) ? new ItemStack(Registries.ITEM.get(id)) : ItemStack.EMPTY;
            d.offers.add(f);
        }
        return d;
    }

    private void apply(Data n) {
        long now = System.nanoTime();
        if (!n.msg.isEmpty()) {
            toast = n.msg; toastOk = n.ok; toastAt = now;
            if (n.ok) {
                HudModel.play(SoundEvents.ENTITY_WANDERING_TRADER_YES, 1.0f, 0.7f);
                HudModel.play(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.3f, 0.6f);
                buyAt = now;
            } else HudModel.play(SoundEvents.ENTITY_VILLAGER_NO, 1.0f, 0.6f);
        }
        d = n;
        leftAt = now;
    }

    private void send(String key) { ClientPlayNetworking.send(new ShopActionPayload("trader_buy", 0, 0, key)); }

    // ================= Screen =================
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

        hoverHit = null;
        for (int i = hits.size() - 1; i >= 0; i--) if (hits.get(i).contains(mx, my)) { hoverHit = hits.get(i); break; }
        String hk = hoverHit == null ? "" : hoverHit.key;
        Set<String> keys = new HashSet<>(hov.keySet()); keys.add(hk);
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
        drawHeart(c, now);
        drawOffers(c, now);
        drawToast(c, now);
        m.pop();
        if (hoverHit != null && hoverHit.tip != null) c.drawTooltip(textRenderer, hoverHit.tip.get(), mouseX, mouseY);
    }

    // ================= фон: раскалённый редстоун =================
    private void drawBackground(DrawContext c, long now) {
        c.fillGradient(0, 0, width, height, 0xFF3C0B12, 0xFF0A0305);
        for (int y = 0; y < height; y += 4) c.fill(0, y, width, y + 1, 0x09FFFFFF);
        // искры поднимаются вверх
        for (int i = 0; i < 90; i++) {
            int h = i * 7919 + 13;
            float speed = 18f + (Math.abs(h) % 30);
            float t = (now / 1e9f * speed + Math.abs(h * 31) % 1000) % (height + 20);
            int x = Math.abs(h * 17 + i * 101) % Math.max(1, width), y = height - (int) t;
            float tw = 0.4f + 0.6f * (float) Math.sin(now / 4e8 + i);
            c.fill(x, y, x + 1, y + (i % 3 == 0 ? 2 : 1), ((int) (110 * tw) << 24) | (i % 2 == 0 ? 0xFF3030 : 0xFF9A40));
        }
        float pulse = 0.85f + 0.15f * (float) Math.sin(now / 5e8);
        int gx = (int) (ox + 181 * u), gy = (int) (oy + 100 * u);
        for (int i = 0; i < 8; i++) {
            int rad = (int) ((30 + i * 18) * u * pulse);
            disc(c, gx, gy, rad, ((5 + i) << 24) | (i % 2 == 0 ? 0xFF2020 : 0xFF6030));
        }
        int vw = Math.max(8, width / 14);
        for (int i = 0; i < vw; i++) {
            int a = (int) (170 * Math.pow(1 - i / (float) vw, 2));
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

    private void drawTitle(DrawContext c, long now) {
        float flick = 0.9f + 0.1f * (float) Math.sin(now / 1.7e8) * (float) Math.sin(now / 5.3e8);
        text(c, "ТОРГОВЕЦ РЕДСТОУНА", 258, 6, 1.9f, argb(0xFF4A3A, flick), 1);
        text(c, "Сердце голема — призыв босса · кейсы со скидкой", 258, 24, 0.62f, 0xFFE0B0A8, 1);
    }

    // ================= левая колонка =================
    private void drawSidebar(DrawContext c, long now) {
        panel(c, 6, 4, 92, 52);
        MatrixStack m = c.getMatrices();
        m.push(); m.translate(10, 8, 0); m.scale(1.1f, 1.1f, 1f); c.drawItem(DIAMOND, 0, 0); m.pop();
        text(c, "АЛМАЗЫ", 32, 7, 0.5f, 0xFF9FD8E8, 0);
        text(c, Integer.toString(d.diamonds), 32, 14, 1.3f, 0xFF7AF0FF, 0);
        m.push(); m.translate(10, 32, 0); m.scale(1.1f, 1.1f, 1f); c.drawItem(ESSENCE, 0, 0); m.pop();
        text(c, "ЭССЕНЦИЯ", 32, 31, 0.5f, 0xFFC8A0F0, 0);
        text(c, Integer.toString(d.essence), 32, 38, 1.3f, 0xFFE070FF, 0);

        long secs = Math.max(0, d.left - (now - leftAt) / 1_000_000_000L);
        boolean urgent = secs < 120;
        panel(c, 6, 60, 92, 30);
        text(c, "УХОДИТ ЧЕРЕЗ", 52, 64, 0.55f, 0xFFD8A8A0, 1);
        float blink = urgent ? 0.6f + 0.4f * (float) Math.sin(now / 2e8) : 1f;
        text(c, String.format("%d:%02d", secs / 60, secs % 60), 52, 73, 1.5f, argb(urgent ? 0xFF5A4A : 0xFFE9D0, blink), 1);

        button(c, "close", 6, 94, 92, 26, "ЗАКРЫТЬ", 0xFF5A4A, 0xC02828, true,
            () -> MinecraftClient.getInstance().setScreen(null), () -> List.of(Text.literal("§7Esc")));

        panel(c, 6, 126, 92, 62);
        text(c, "Как это работает", 52, 130, 0.62f, 0xFFFFC0B0, 1);
        text(c, "Купи Сердце и ПКМ", 52, 141, 0.55f, 0xFFD0D0D8, 1);
        text(c, "по земле — голем", 52, 149, 0.55f, 0xFFD0D0D8, 1);
        text(c, "проснётся. Бой", 52, 157, 0.55f, 0xFFD0D0D8, 1);
        text(c, "очень тяжёлый,", 52, 165, 0.55f, 0xFFD0D0D8, 1);
        text(c, "зови друзей!", 52, 173, 0.55f, 0xFFFFE08A, 1);
    }

    // ================= Сердце голема =================
    private void drawHeart(DrawContext c, long now) {
        float x = 110, y = 36, w = 142, h = 196;
        panel(c, (int) x, (int) y, (int) w, (int) h);
        MatrixStack m = c.getMatrices();
        // лучи и биение сердца
        float beat = (float) Math.pow(Math.max(0, Math.sin(now / 2.4e8)), 6);
        for (int r = 0; r < 8; r++) {
            m.push();
            m.translate(x + w / 2f, y + 62, 0);
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (now / 4e7 % 360) + r * 45));
            c.fill(-1, 6, 1, 40 + (int) (14 * beat), (0x22 << 24) | 0xFF3030);
            m.pop();
        }
        float s = 5.2f + 0.7f * beat;
        m.push();
        m.translate(x + w / 2f, y + 62 + (float) Math.sin(now / 6e8) * 2f, 60);
        m.scale(s, s, 1f);
        m.translate(-8, -8, 0);
        c.drawItem(new ItemStack(ModItems.REDSTONE_HEART), 0, 0);
        m.pop();

        text(c, "СЕРДЦЕ ГОЛЕМА", x + w / 2f, y + 112, 1.15f, 0xFFFF5A4A, 1);
        text(c, "ЭПИЧЕСКИЙ ПРЕДМЕТ", x + w / 2f, y + 125, 0.5f, rarColor("epic"), 1);
        text(c, "ПКМ по земле — пробудить", x + w / 2f, y + 136, 0.58f, 0xFFE0E0E8, 1);
        text(c, "Редстоунового голема.", x + w / 2f, y + 144, 0.58f, 0xFFE0E0E8, 1);
        text(c, "Награда: кейсы, монеты, эссенция", x + w / 2f, y + 153, 0.5f, 0xFFFFE08A, 1);

        boolean okD = d.diamonds >= d.heartDia, okE = d.essence >= d.heartEss;
        priceButton(c, "hd", x + 6, y + 164, 64, 26, d.heartDia, true, okD, "heart_d", "Сердце голема за алмазы");
        priceButton(c, "he", x + w - 70, y + 164, 64, 26, d.heartEss, false, okE, "heart_e", "Сердце голема за эссенцию");
    }

    private void priceButton(DrawContext c, String key, float x, float y, float w, float h, int price, boolean diamond,
                             boolean afford, String buyKey, String tipName) {
        int top = diamond ? 0x7AF0FF : 0xE89AFF, bot = diamond ? 0x1E8AA8 : 0x8A2AB0;
        button(c, key, x, y, w, h, "", top, bot, afford, () -> send(buyKey),
            () -> List.of(Text.literal("§a" + tipName),
                Text.literal((afford ? (diamond ? "§b" : "§d") : "§c") + price + (diamond ? " алмазов" : " эссенции")),
                Text.literal(afford ? "§7ЛКМ — купить" : "§cНе хватает")));
        MatrixStack m = c.getMatrices();
        m.push(); m.translate(x + 6, y + 5, 0); m.scale(0.95f, 0.95f, 1f); c.drawItem(diamond ? DIAMOND : ESSENCE, 0, 0); m.pop();
        text(c, Integer.toString(price), x + w - 6, y + 8, 1.15f, afford ? 0xFFFFFFFF : 0xFFC0C0C8, 2);
    }

    // ================= кейсы со скидкой =================
    private void drawOffers(DrawContext c, long now) {
        float x = 262, w = 152;
        panel(c, (int) x, 36, (int) w, 196);
        text(c, "СКИДКА −" + d.discount + "%", x + w / 2f, 40, 1.0f, argb(0x6FE08A, 0.85f + 0.15f * (float) Math.sin(now / 3e8)), 1);
        text(c, "У каждого торговца свой набор", x + w / 2f, 51, 0.5f, 0xFFA8A8B0, 1);
        if (d.offers.isEmpty()) { text(c, "Сегодня без скидок", x + w / 2f, 130, 0.9f, 0xFFB8B8C0, 1); return; }
        for (int i = 0; i < d.offers.size(); i++) {
            Offer f = d.offers.get(i);
            float cy = 60 + i * 56;
            drawOffer(c, f, i, x + 5, cy, w - 10, 52, now);
        }
    }

    private void drawOffer(DrawContext c, Offer f, int i, float x, float y, float w, float h, long now) {
        String key = "o" + i;
        float ha = hov.getOrDefault(key, 0f);
        boolean soldOut = f.stock <= 0;
        boolean afford = f.diamond() ? d.diamonds >= f.price : d.essence >= f.price;
        float dealT = Math.min(1f, Math.max(0f, ((now - openAt) / 1e6f - 140f * i - 200f) / 420f));
        float deal = 1f - (1f - dealT) * (1f - dealT) * (1f - dealT);
        if (deal <= 0.01f) return;
        float bought = (now - buyAt) / 1e6f;
        float pop = bought < 450f ? 0.03f * (float) Math.exp(-bought / 120f) * (float) Math.cos(bought / 35f) : 0f;
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(x + w / 2f + (1f - deal) * 60f, y + h / 2f - 1.5f * ha, 0);
        m.scale(1f + 0.015f * ha + pop, 1f + 0.015f * ha + pop, 1f);
        m.translate(-w / 2f, -h / 2f, 0);
        int iw = (int) w, ih = (int) h;
        int border = rarColor(f.rar);
        c.fill(-1, -1, iw + 1, ih + 1, 0xFF000000);
        c.fill(0, 0, iw, ih, (border & 0xFFFFFF) | 0xFF000000);
        c.fillGradient(1, 1, iw - 1, ih - 1, 0xFF3A1A24, 0xFF160A12);
        c.fill(1, 1, iw - 1, 2, 0x44FFFFFF);
        if (ha > 0.05f) c.fill(1, 1, iw - 1, ih - 1, ((int) (22 * ha) << 24) | 0xFFFFFF);
        // бейдж скидки
        c.fill(iw - 30, 2, iw - 2, 11, 0xFF000000);
        c.fillGradient(iw - 29, 3, iw - 3, 10, 0xFF6FE08A, 0xFF1E8A4A);
        text(c, "−" + d.discount + "%", iw - 16, 3.6f, 0.62f, 0xFFFFFFFF, 1);
        // предмет
        if (!f.stack.isEmpty()) {
            m.push();
            m.translate(20, h / 2f + (float) Math.sin(now / 7e8 + i) * 1.2f, 50);
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.sin(now / 9e8 + i) * 4f));
            m.scale(1.9f, 1.9f, 1f);
            m.translate(-8, -8, 0);
            c.drawItem(f.stack, 0, 0);
            m.pop();
        }
        text(c, fit(f.name, (int) (w - 78), 0.72f), 40, 5, 0.72f, 0xFFFFFFFF, 0);
        text(c, rarLabel(f.rar), 40, 14, 0.45f, rarColor(f.rar), 0);
        // цены: старая зачёркнута
        String old = Integer.toString(f.base);
        float ow = textRenderer.getWidth(old) * 0.62f;
        text(c, old, 40, 24, 0.62f, 0xFF8A8A94, 0);
        c.fill(40, 27, (int) (40 + ow), 28, 0xFFB04A4A);
        MatrixStack mm = c.getMatrices();
        text(c, "→", 40 + ow + 4, 24, 0.62f, 0xFF8A8A94, 0);
        mm.push(); mm.translate(40 + ow + 12, 21, 0); mm.scale(0.6f, 0.6f, 1f); c.drawItem(f.diamond() ? DIAMOND : ESSENCE, 0, 0); mm.pop();
        text(c, Integer.toString(f.price), 40 + ow + 23, 22.5f, 0.95f, afford ? (f.diamond() ? 0xFF7AF0FF : 0xFFE89AFF) : 0xFFFF7070, 0);
        text(c, soldOut ? "Раскуплено" : "Осталось: " + f.stock, 40, 41, 0.5f, soldOut ? 0xFFFF7070 : 0xFFA8A8B0, 0);
        if (f.owned > 0) text(c, "У вас ×" + f.owned, iw - 5, 41, 0.5f, 0xFF8EF0A0, 2);
        m.pop();

        boolean enabled = afford && !soldOut;
        // кнопка покупки — вся карточка
        Hit hh = new Hit();
        hh.x = x; hh.y = y; hh.w = w; hh.h = h; hh.key = key;
        hh.click = enabled ? () -> send(f.key) : () -> HudModel.play(SoundEvents.ENTITY_VILLAGER_NO, 1.2f, 0.3f);
        hh.tip = () -> {
            List<Text> t = new ArrayList<>();
            t.add(Text.literal(rarCode(f.rar) + "§l" + f.name));
            for (String l : wrap(f.desc, 230)) t.add(Text.literal("§7" + l));
            t.add(Text.literal("§8Было: §m" + f.base + (f.diamond() ? " алмазов" : " эссенции")));
            t.add(Text.literal((afford ? (f.diamond() ? "§b" : "§d") : "§c") + "Цена: " + f.price + (f.diamond() ? " алмазов" : " эссенции")));
            t.add(Text.literal(soldOut ? "§cРаскуплено" : enabled ? "§aЛКМ — купить" : "§cНе хватает " + (f.diamond() ? "алмазов" : "эссенции")));
            return t;
        };
        hits.add(hh);
    }

    // ================= элементы =================
    private void panel(DrawContext c, int x, int y, int w, int h) {
        c.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        c.fill(x, y, x + w, y + h, 0xC8240A10);
        c.fill(x, y, x + w, y + 1, 0x30FFFFFF);
    }

    private void button(DrawContext c, String key, float x, float y, float w, float h, String label,
                        int topRgb, int botRgb, boolean enabled, Runnable click, Supplier<List<Text>> tip) {
        float ha = hov.getOrDefault(key, 0f);
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(x + w / 2f, y + h / 2f, 0);
        m.scale(1f + 0.04f * ha, 1f + 0.04f * ha, 1f);
        m.translate(-w / 2f, -h / 2f, 0);
        int iw = (int) w, ih = (int) h;
        int top = enabled ? topRgb : 0x6A6A72, bot = enabled ? botRgb : 0x34343A;
        c.fill(-1, -1, iw + 1, ih + 3, 0xFF000000);
        c.fill(0, ih - 1, iw, ih + 2, 0xFF000000 | (bot >> 1 & 0x7F7F7F));
        c.fillGradient(0, 0, iw, ih, 0xFF000000 | top, 0xFF000000 | bot);
        c.fill(1, 1, iw - 1, 2, 0x66FFFFFF);
        if (ha > 0.05f) c.fill(0, 0, iw, ih, ((int) (40 * ha) << 24) | 0xFFFFFF);
        if (!label.isEmpty()) text(c, label, w / 2f, (h - 9f) / 2f, 1.0f, enabled ? 0xFFFFFFFF : 0xFF909098, 1);
        m.pop();
        Hit h2 = new Hit();
        h2.x = x; h2.y = y; h2.w = w; h2.h = h + 2; h2.key = key;
        h2.click = enabled ? click : () -> HudModel.play(SoundEvents.ENTITY_VILLAGER_NO, 1.2f, 0.3f);
        h2.tip = tip;
        hits.add(h2);
    }

    private void drawToast(DrawContext c, long now) {
        float t = (now - toastAt) / 1e6f;
        if (t > 3000f || toast.isEmpty()) return;
        float a = t < 150f ? t / 150f : t > 2500f ? Math.max(0f, (3000f - t) / 500f) : 1f;
        float y = 238f;
        float w = Math.min(400f, textRenderer.getWidth(toast) * 0.7f + 14f);
        int col = toastOk ? 0x2E8B57 : 0xB02A2A;
        float shake = !toastOk && t < 300f ? (float) Math.sin(t / 25f) * 2f : 0f;
        c.fill((int) (258 - w / 2 + shake) - 1, (int) y - 1, (int) (258 + w / 2 + shake) + 1, (int) y + 11, argb(0x000000, a));
        c.fill((int) (258 - w / 2 + shake), (int) y, (int) (258 + w / 2 + shake), (int) y + 10, argb(col, a * 0.95f));
        text(c, fit(toast, 390, 0.7f), 258 + shake, y + 1.8f, 0.7f, argb(0xFFFFFF, a), 1);
    }

    static int rarColor(String r) {
        return switch (r) {
            case "uncommon" -> 0xFF5CE08A; case "rare" -> 0xFF4C9BFF; case "epic" -> 0xFFB45BFF;
            case "legendary" -> 0xFFFFC040; case "mythic" -> 0xFFFF5A5A; default -> 0xFFB8B8C0;
        };
    }
    static String rarLabel(String r) {
        return switch (r) { case "uncommon" -> "НЕОБЫЧНЫЙ"; case "rare" -> "РЕДКИЙ"; case "epic" -> "ЭПИЧЕСКИЙ";
                            case "legendary" -> "ЛЕГЕНДАРНЫЙ"; case "mythic" -> "МИФИЧЕСКИЙ"; default -> "ОБЫЧНЫЙ"; };
    }
    static String rarCode(String r) {
        return switch (r) { case "uncommon" -> "§a"; case "rare" -> "§9"; case "epic" -> "§5"; case "legendary" -> "§6"; case "mythic" -> "§c"; default -> "§f"; };
    }
    private static int argb(int rgb, float alpha) { return (Math.max(0, Math.min(255, (int) (alpha * 255))) << 24) | (rgb & 0xFFFFFF); }

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
}
