package com.derevna230.comborush.client;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.hud.ShopActionPayload;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
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

/** Магазин эссенции «COMBO RUSH COSMETICS»: кейсы с бронёй, разбор лишнего, переход в косметику */
public class EssenceShopScreen extends Screen {
    private static final Identifier LOGO = Identifier.of(ComboRush.MOD_ID, "textures/gui/cosmetics_logo.png");
    private static final int LOGO_W = 512, LOGO_H = 364;
    private static final int DW = 420, DH = 250;
    private static final ItemStack ESSENCE = new ItemStack(Items.AMETHYST_SHARD);
    private static final ItemStack DIAMOND = new ItemStack(Items.DIAMOND);

    static final class Drop { String name = "", rar = "common", chance = ""; double p; }
    static final class CaseV {
        String id = "", name = "", desc = "", rar = "common", cur = "essence";
        int price, owned;
        boolean diamond() { return cur.equals("diamond"); }
        ItemStack stack = ItemStack.EMPTY;
        List<Drop> drops = new ArrayList<>();
    }
    static final class Data {
        int essence, resets, diamonds;
        String msg = ""; boolean ok = true;
        String handName = "", handRar = "common"; int handValue = 0;
        List<CaseV> cases = new ArrayList<>();
    }
    private static final class Hit {
        float x, y, w, h; String key; Runnable click; Supplier<List<Text>> tip;
        boolean contains(float px, float py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }

    private Data d;
    private int sel = -1;
    private float u = 1, ox, oy, mx, my;
    private long lastNs = 0, openAt, toastAt = -10_000_000_000L, buyAt = -10_000_000_000L;
    private double essShown;
    private String toast = ""; private boolean toastOk = true;
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, Float> hov = new HashMap<>();
    private Hit hoverHit;

    private EssenceShopScreen(Data d) {
        super(Text.literal("Магазин эссенции"));
        this.d = d;
        this.essShown = d.essence;
        this.openAt = System.nanoTime();
        if (!d.msg.isEmpty()) { toast = d.msg; toastOk = d.ok; toastAt = openAt; }
    }

    // ================= пакеты =================
    public static void onPacket(String kind, JsonObject o) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (kind.equals("eshop_open") || !(mc.currentScreen instanceof EssenceShopScreen)) {
            if (kind.equals("eshop_open")) mc.setScreen(new EssenceShopScreen(parse(o)));
            return;
        }
        ((EssenceShopScreen) mc.currentScreen).apply(parse(o));
    }

    private static String str(JsonObject o, String k, String def) { return o.has(k) ? o.get(k).getAsString() : def; }
    private static int num(JsonObject o, String k) { return o.has(k) ? o.get(k).getAsInt() : 0; }

    private static Data parse(JsonObject o) {
        Data d = new Data();
        d.essence = num(o, "essence"); d.resets = num(o, "resets"); d.diamonds = num(o, "diamonds");
        d.msg = str(o, "msg", ""); d.ok = !o.has("ok") || o.get("ok").getAsBoolean();
        if (o.has("hand")) {
            JsonObject h = o.getAsJsonObject("hand");
            d.handName = str(h, "name", ""); d.handRar = str(h, "rar", "common"); d.handValue = num(h, "value");
        }
        if (o.has("cases")) for (JsonElement el : o.getAsJsonArray("cases")) {
            JsonObject j = el.getAsJsonObject();
            CaseV c = new CaseV();
            c.id = str(j, "id", ""); c.name = str(j, "name", "?"); c.desc = str(j, "desc", ""); c.rar = str(j, "rar", "common");
            c.price = num(j, "price"); c.owned = num(j, "owned"); c.cur = str(j, "cur", "essence");
            Identifier id = Identifier.tryParse(str(j, "item", ""));
            c.stack = id != null && Registries.ITEM.containsId(id) ? new ItemStack(Registries.ITEM.get(id)) : ItemStack.EMPTY;
            if (j.has("drops")) for (JsonElement de : j.getAsJsonArray("drops")) {
                JsonObject dj = de.getAsJsonObject();
                Drop dr = new Drop(); dr.name = str(dj, "name", ""); dr.rar = str(dj, "rar", "common"); dr.chance = str(dj, "chance", ""); dr.p = dj.has("p") ? dj.get("p").getAsDouble() : 0;
                c.drops.add(dr);
            }
            d.cases.add(c);
        }
        return d;
    }

    private void apply(Data n) {
        long now = System.nanoTime();
        if (!n.msg.isEmpty()) {
            toast = n.msg; toastOk = n.ok; toastAt = now;
            if (n.ok) {
                HudModel.play(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 0.7f);
                HudModel.play(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.1f, 0.4f);
            } else HudModel.play(SoundEvents.ENTITY_VILLAGER_NO, 1.0f, 0.6f);
        }
        if (n.ok && (n.essence < d.essence || n.diamonds < d.diamonds)) buyAt = now;
        d = n;
        if (sel >= d.cases.size()) sel = -1;
    }

    private void send(String action, String c) { ClientPlayNetworking.send(new ShopActionPayload(action, 0, 0, c)); }

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
        sel = -1;
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
        essShown += (d.essence - essShown) * Math.min(1f, dt * 8f);
        if (Math.abs(d.essence - essShown) < 0.5) essShown = d.essence;

        hits.clear();
        drawBackground(c, now);
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(ox, oy, 0);
        m.scale(u, u, 1f);
        drawLogo(c, now);
        drawSidebar(c, now);
        drawCases(c, now);
        drawToast(c, now);
        m.pop();
        if (hoverHit != null && hoverHit.tip != null) c.drawTooltip(textRenderer, hoverHit.tip.get(), mouseX, mouseY);
    }

    // ================= фон: тёмно-фиолетовое сукно в цветах логотипа =================
    private void drawBackground(DrawContext c, long now) {
        c.fillGradient(0, 0, width, height, 0xFF2A1450, 0xFF090514);
        for (int y = 0; y < height; y += 4) c.fill(0, y, width, y + 1, 0x09FFFFFF);
        for (int i = 0; i < 240; i++) {
            int h = i * 7919 + 13;
            int x = Math.abs(h * 31) % Math.max(1, width), y = Math.abs(h * 17 + i * 101) % Math.max(1, height);
            float tw = 0.5f + 0.5f * (float) Math.sin(now / 1.1e9 + i);
            c.fill(x, y, x + 1, y + 1, ((int) (40 * tw) << 24) | (i % 2 == 0 ? 0xFF9AF0 : 0x7AE0FF));
        }
        float pulse = 0.85f + 0.15f * (float) Math.sin(now / 8e8);
        int gx = width / 2, gy = (int) (oy + 150 * u);
        for (int i = 0; i < 8; i++) {
            int rad = (int) ((40 + i * 20) * u * pulse);
            disc(c, gx, gy, rad, ((5 + i) << 24) | (i % 2 == 0 ? 0xC060FF : 0x40A0FF));
        }
        int vw = Math.max(8, width / 14);
        for (int i = 0; i < vw; i++) {
            int a = (int) (160 * Math.pow(1 - i / (float) vw, 2));
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

    private void drawLogo(DrawContext c, long now) {
        float t = Math.min(1f, (now - openAt) / 1e6f / 500f);
        float pop = 1f + 0.25f * (float) Math.exp(-t * 5) * (float) Math.cos(t * 18);
        float w = 140f * pop, h = w * LOGO_H / LOGO_W;
        float bob = (float) Math.sin(now / 6e8) * 1.5f;
        float angle = (float) Math.sin(now / 1.3e9) * 1.5f;
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(258, 4 + h / 2f + bob, 0);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
        m.translate(-w / 2f, -h / 2f, 0);
        m.scale(w / LOGO_W, h / LOGO_H, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        c.drawTexture(LOGO, 0, 0, 0f, 0f, LOGO_W, LOGO_H, LOGO_W, LOGO_H);
        RenderSystem.disableBlend();
        m.pop();
    }

    // ================= левая колонка =================
    private void drawSidebar(DrawContext c, long now) {
        panel(c, 6, 4, 92, 30);
        MatrixStack m = c.getMatrices();
        m.push(); m.translate(10, 10, 0); m.scale(1.2f, 1.2f, 1f); c.drawItem(ESSENCE, 0, 0); m.pop();
        text(c, "ЭССЕНЦИЯ", 32, 8, 0.55f, 0xFFC8A0F0, 0);
        text(c, Long.toString(Math.round(essShown)), 32, 16, 1.5f, 0xFFE070FF, 0);

        button(c, "close", 6, 38, 92, 26, "ЗАКРЫТЬ", 0xFF5A4A, 0xC02828, true,
            () -> MinecraftClient.getInstance().setScreen(null), () -> List.of(Text.literal("§7Esc")));
        button(c, "cosm", 6, 68, 92, 26, "КОСМЕТИКА", 0xFF7AD9, 0xA0287A, true, () -> send("eshop_cosmetics", ""),
            () -> List.of(Text.literal("§dПитомцы, титулы и следы"), Text.literal("§7Тоже за эссенцию")));

        boolean canScrap = !d.handName.isEmpty();
        button(c, "scrap", 6, 98, 92, 26, "РАЗОБРАТЬ", 0xFFD45B, 0xA8740E, canScrap, () -> send("eshop_scrap", ""),
            () -> canScrap
                ? List.of(Text.literal("§6Разобрать предмет из руки"), Text.literal("§f" + d.handName),
                          Text.literal("§d+" + d.handValue + " эссенции"), Text.literal("§cПредмет исчезнет"))
                : List.of(Text.literal("§7Возьми в руку предмет из кейса"), Text.literal("§7(броню, оружие, питомца),"), Text.literal("§7чтобы обменять его на эссенцию")));
        panel(c, 6, 128, 92, 34);
        if (canScrap) {
            text(c, "В руке:", 10, 131, 0.55f, 0xFFA0A0A8, 0);
            text(c, fit(d.handName, 84, 0.62f), 10, 139, 0.62f, rarColor(d.handRar), 0);
            text(c, "→ +" + d.handValue + " эссенции", 10, 149, 0.6f, 0xFFE070FF, 0);
        } else {
            text(c, "Эссенция даётся", 10, 132, 0.6f, 0xFFB8B8C0, 0);
            text(c, "за сброс прогресса", 10, 140, 0.6f, 0xFFB8B8C0, 0);
            text(c, "(/cr reset)", 10, 149, 0.6f, 0xFF8A8A94, 0);
        }
        text(c, "Кейс появится в инвентаре —", 52, 168, 0.55f, 0xAAFFFFFF, 1);
        text(c, "возьми в руку и нажми ПКМ", 52, 175, 0.55f, 0xAAFFFFFF, 1);
    }

    // ================= витрина кейсов =================
    private void drawCases(DrawContext c, long now) {
        int n = d.cases.size();
        if (n == 0) { text(c, "Кейсы не загружены", 258, 150, 1f, 0xFFFFFFFF, 1); return; }
        float cw = n > 6 ? 42 : n > 5 ? 48 : 56, gap = n > 6 ? 4 : n > 5 ? 5 : 6;
        float total = n * cw + (n - 1) * gap;
        float x0 = 258 - total / 2f + 1, y = 112;
        for (int i = 0; i < n; i++) {
            CaseV cv = d.cases.get(i);
            float x = x0 + i * (cw + gap);
            String key = "c" + i;
            float ha = hov.getOrDefault(key, 0f);
            boolean selected = sel == i;
            boolean afford = cv.diamond() ? d.diamonds >= cv.price : d.essence >= cv.price;
            float dealT = Math.min(1f, Math.max(0f, ((now - openAt) / 1e6f - 120f * i) / 450f));
            float deal = 1f - (1f - dealT) * (1f - dealT) * (1f - dealT);
            if (deal <= 0.01f) continue;
            float yOff = -6f * ha - (selected ? 8f : 0f) + (float) Math.sin(now / 7e8 + i * 1.3) * 0.8f + (1f - deal) * 80f;
            float angle = (float) Math.sin(now / 9e8 + i) * 1.2f + ha * Math.max(-0.5f, Math.min(0.5f, (mx - (x + cw / 2f)) / cw)) * 10f;
            float bought = selected ? (now - buyAt) / 1e6f : 1e9f;
            float pop = bought < 500f ? 0.2f * (float) Math.exp(-bought / 120f) * (float) Math.cos(bought / 40f) : 0f;

            MatrixStack m = c.getMatrices();
            m.push();
            m.translate(x + cw / 2f, y + 44 + yOff, 0);
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
            m.scale(1f + 0.06f * ha + pop, 1f + 0.06f * ha + pop, 1f);
            m.translate(-cw / 2f, -44, 0);
            int border = selected ? 0xFFFFE066 : rarColor(cv.rar);
            c.fill(-2, -2, (int) cw + 2, 90, 0xFF000000);
            c.fill(-1, -1, (int) cw + 1, 89, border);
            c.fillGradient(0, 0, (int) cw, 88, 0xFF3A2266, 0xFF140A28);
            c.fill(1, 1, (int) cw - 1, 2, 0x55FFFFFF);
            // лучи за кейсом
            for (int r = 0; r < 6; r++) {
                m.push();
                m.translate(cw / 2f, 40, 0);
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (now / 3e7 % 360) + r * 60));
                c.fill(-1, 0, 1, 26, (0x18 << 24) | (rarColor(cv.rar) & 0xFFFFFF));
                m.pop();
            }
            c.fill(1, 1, (int) cw - 1, 8, 0x90000000);
            text(c, rarLabel(cv.rar), cw / 2f, 2.2f, 0.42f, rarColor(cv.rar), 1);
            if (!cv.stack.isEmpty()) {
                float spin = (float) Math.sin(now / 1.2e9 + i) * 6f;
                m.push();
                m.translate(cw / 2f, 42, 50);
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(spin * 0.3f));
                m.scale(2.6f, 2.6f, 1f);
                m.translate(-8, -8, 0);
                c.drawItem(cv.stack, 0, 0);
                m.pop();
            }
            c.fill(1, 66, (int) cw - 1, 87, 0xC8000000);
            List<String> lines = wrap(cv.name, (int) ((cw - 4) / 0.55f));
            for (int k = 0; k < lines.size() && k < 2; k++) text(c, lines.get(k), cw / 2f, 68 + k * 7, 0.55f, 0xFFFFFFFF, 1);
            if (cv.owned > 0) {
                c.fill((int) cw - 13, 9, (int) cw - 1, 17, 0xD0000000);
                text(c, "×" + cv.owned, cw - 7, 10, 0.55f, 0xFF8EF0A0, 1);
            }
            if (!afford) c.fill(0, 0, (int) cw, 88, 0x60101014);
            m.pop();

            final int idx = i;
            hit(x, y, cw, 90, key, () -> {
                sel = sel == idx ? -1 : idx;
                HudModel.play(SoundEvents.ENTITY_ITEM_PICKUP, 1.4f, 0.3f);
            }, () -> caseTip(cv));

            // ценник
            float py = y + 96 + (1f - deal) * 80f;
            c.fill((int) x + 7, (int) py - 1, (int) (x + cw - 7), (int) py + 12, 0xFF000000);
            int tagTop = cv.diamond() ? 0xFF7AF0FF : 0xFFE89AFF, tagBot = cv.diamond() ? 0xFF1E8AA8 : 0xFF8A2AB0;
            c.fillGradient((int) x + 8, (int) py, (int) (x + cw - 8), (int) py + 11, afford ? tagTop : 0xFFB06060, afford ? tagBot : 0xFF702020);
            m.push(); m.translate(x + 10, py + 1.5f, 0); m.scale(0.5f, 0.5f, 1f); c.drawItem(cv.diamond() ? DIAMOND : ESSENCE, 0, 0); m.pop();
            text(c, Integer.toString(cv.price), x + cw - 11, py + 2, 0.85f, 0xFFFFFFFF, 2);

            if (selected) {
                button(c, "buy" + i, x + 2, py + 15, cw - 4, 12, "КУПИТЬ", 0x6FE08A, 0x1E8A4A, afford,
                    () -> send("eshop_buy", cv.id),
                    () -> List.of(Text.literal("§aКупить «" + cv.name + "»"),
                        Text.literal((afford ? (cv.diamond() ? "§b" : "§d") : "§c") + cv.price + (cv.diamond() ? " алмазов" : " эссенции"))));
            }
        }
    }

    private List<Text> caseTip(CaseV cv) {
        List<Text> t = new ArrayList<>();
        t.add(Text.literal(rarCode(cv.rar) + "§l" + cv.name));
        for (String l : wrap(cv.desc, 240)) t.add(Text.literal("§7" + l));
        t.add(Text.literal("§8Может выпасть:"));
        for (String r : List.of("mythic", "legendary", "epic", "rare", "uncommon", "common")) {
            double sum = 0;
            List<String> names = new ArrayList<>();
            for (Drop dr : cv.drops) if (dr.rar.equals(r)) { sum += dr.p; names.add(dr.name); }
            if (names.isEmpty()) continue;
            double pc = sum * 100;
            String pct = (pc < 1 ? String.format(java.util.Locale.ROOT, "%.1f", pc) : String.valueOf(Math.round(pc))) + "%";
            t.add(Text.literal(" " + rarCode(r) + "■ " + rarLabel(r) + " §8— §7" + pct));
            for (String l : wrap(String.join(", ", names), 260)) t.add(Text.literal("   §f" + l));
        }
        if (cv.diamond()) {
            t.add(Text.literal("§bЦена: " + cv.price + " алмазов"));
            t.add(Text.literal((d.diamonds >= cv.price ? "§7" : "§c") + "В инвентаре алмазов: " + d.diamonds));
        } else t.add(Text.literal("§dЦена: " + cv.price + " эссенции"));
        if (cv.owned > 0) t.add(Text.literal("§aВ инвентаре: " + cv.owned));
        return t;
    }

    // ================= элементы =================
    private void panel(DrawContext c, int x, int y, int w, int h) {
        c.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        c.fill(x, y, x + w, y + h, 0xC8140A24);
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
        if (ih >= 20) text(c, label, w / 2f, (h - 9f) / 2f, 1.0f, enabled ? 0xFFFFFFFF : 0xFF909098, 1);
        else text(c, label, w / 2f, (h - 5.5f) / 2f, 0.6f, enabled ? 0xFFFFFFFF : 0xFF909098, 1);
        m.pop();
        hit(x, y, w, h + 2, key, enabled ? click : () -> HudModel.play(SoundEvents.ENTITY_VILLAGER_NO, 1.2f, 0.3f), tip);
    }

    private void hit(float x, float y, float w, float h, String key, Runnable click, Supplier<List<Text>> tip) {
        Hit h2 = new Hit();
        h2.x = x; h2.y = y; h2.w = w; h2.h = h; h2.key = key; h2.click = click; h2.tip = tip;
        hits.add(h2);
    }

    private void drawToast(DrawContext c, long now) {
        float t = (now - toastAt) / 1e6f;
        if (t > 3000f || toast.isEmpty()) return;
        float a = t < 150f ? t / 150f : t > 2500f ? Math.max(0f, (3000f - t) / 500f) : 1f;
        float y = 239f;
        float w = textRenderer.getWidth(toast) * 0.8f + 14f;
        int col = toastOk ? 0x2E8B57 : 0xB02A2A;
        float shake = !toastOk && t < 300f ? (float) Math.sin(t / 25f) * 2f : 0f;
        c.fill((int) (258 - w / 2 + shake) - 1, (int) y - 1, (int) (258 + w / 2 + shake) + 1, (int) y + 11, argb(0x000000, a));
        c.fill((int) (258 - w / 2 + shake), (int) y, (int) (258 + w / 2 + shake), (int) y + 10, argb(col, a * 0.95f));
        text(c, toast, 258 + shake, y + 1.8f, 0.8f, argb(0xFFFFFF, a), 1);
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
        c.drawText(textRenderer, s, 0, 0, argb, ((argb >> 16 & 255) * 3 + (argb >> 8 & 255) * 6 + (argb & 255)) > 700);   // тёмный текст без тени — иначе «грязный»
        m.pop();
    }
}
