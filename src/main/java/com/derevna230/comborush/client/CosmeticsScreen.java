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

/** Косметика «COMBO RUSH COSMETICS»: питомцы, титулы и следы. Открывается кнопкой «Косметика» в магазине кейсов */
public class CosmeticsScreen extends Screen {
    private static final Identifier LOGO = Identifier.of(ComboRush.MOD_ID, "textures/gui/cosmetics_logo.png");
    private static final int LOGO_W = 512, LOGO_H = 364;
    private static final int DW = 420, DH = 250;
    private static final ItemStack ESSENCE = new ItemStack(Items.AMETHYST_SHARD);
    private static final String[] TABS = {"pet", "title", "trail"};
    private static final String[] TAB_NAMES = {"ПИТОМЦЫ", "ТИТУЛЫ", "СЛЕДЫ"};
    private static final int COLS = 3, ROWS = 2;

    static final class Item {
        String id = "", name = "", desc = "", type = "title", prefix = "";
        int price; boolean owned, on;
        ItemStack icon = ItemStack.EMPTY;
    }
    static final class Data {
        int essence; String msg = "", player = ""; boolean ok = true;
        List<Item> items = new ArrayList<>();
    }
    private static final class Hit {
        float x, y, w, h; String key; Runnable click; Supplier<List<Text>> tip;
        boolean contains(float px, float py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }

    private Data d;
    private int tab = 0, rowScroll = 0;
    private float u = 1, ox, oy, mx, my;
    private long lastNs = 0, openAt, toastAt = -10_000_000_000L, flashAt = -10_000_000_000L;
    private String toast = "", flashId = ""; private boolean toastOk = true;
    private double essShown;
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, Float> hov = new HashMap<>();
    private Hit hoverHit;

    private CosmeticsScreen(Data d) {
        super(Text.literal("Косметика"));
        this.d = d;
        this.essShown = d.essence;
        this.openAt = System.nanoTime();
    }

    // ================= пакеты =================
    public static void onPacket(String kind, JsonObject o) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Data n = parse(o);
        if (kind.equals("cosm_open")) { mc.setScreen(new CosmeticsScreen(n)); HudModel.play(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 0.7f); return; }
        if (mc.currentScreen instanceof CosmeticsScreen s) s.apply(n);
    }

    private static String str(JsonObject o, String k, String def) { return o.has(k) ? o.get(k).getAsString() : def; }

    private static Data parse(JsonObject o) {
        Data d = new Data();
        d.essence = o.has("essence") ? o.get("essence").getAsInt() : 0;
        d.msg = str(o, "msg", ""); d.ok = !o.has("ok") || o.get("ok").getAsBoolean(); d.player = str(o, "name", "Игрок");
        if (o.has("items")) for (JsonElement el : o.getAsJsonArray("items")) {
            JsonObject j = el.getAsJsonObject();
            Item it = new Item();
            it.id = str(j, "id", ""); it.name = str(j, "name", "?"); it.desc = str(j, "desc", ""); it.type = str(j, "type", "title");
            it.prefix = str(j, "prefix", ""); it.price = j.has("price") ? j.get("price").getAsInt() : 0;
            it.owned = j.has("owned") && j.get("owned").getAsBoolean(); it.on = j.has("on") && j.get("on").getAsBoolean();
            Identifier id = Identifier.tryParse(str(j, "icon", "minecraft:paper"));
            it.icon = id != null && Registries.ITEM.containsId(id) ? new ItemStack(Registries.ITEM.get(id)) : new ItemStack(Items.PAPER);
            d.items.add(it);
        }
        return d;
    }

    private void apply(Data n) {
        long now = System.nanoTime();
        if (!n.msg.isEmpty()) {
            toast = n.msg; toastOk = n.ok; toastAt = now;
            if (n.ok) {
                HudModel.play(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.3f, 0.7f);
                if (n.msg.startsWith("Куплено")) HudModel.play(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.1f, 0.5f);
            } else HudModel.play(SoundEvents.ENTITY_VILLAGER_NO, 1.0f, 0.6f);
        }
        d = n;
        flashAt = now;
    }

    private void send(String action, String c) { ClientPlayNetworking.send(new ShopActionPayload(action, 0, 0, c)); }

    private List<Item> current() {
        List<Item> l = new ArrayList<>();
        for (Item it : d.items) if (it.type.equals(TABS[tab])) l.add(it);
        return l;
    }

    // ================= Screen =================
    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) { }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        int rows = (current().size() + COLS - 1) / COLS;
        rowScroll = Math.max(0, Math.min(Math.max(0, rows - ROWS), rowScroll - (int) Math.signum(v)));
        return true;
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
        drawTabs(c, now);
        drawGrid(c, now);
        drawToast(c, now);
        m.pop();
        if (hoverHit != null && hoverHit.tip != null) c.drawTooltip(textRenderer, hoverHit.tip.get(), mouseX, mouseY);
    }

    // ================= фон =================
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
        int gx = width / 2, gy = (int) (oy + 140 * u);
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
        float w = 104f * pop, h = w * LOGO_H / LOGO_W;
        float bob = (float) Math.sin(now / 6e8) * 1.2f;
        float angle = (float) Math.sin(now / 1.3e9) * 1.5f;
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(258, 2 + h / 2f + bob, 0);
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

        button(c, "back", 6, 38, 92, 26, "К КЕЙСАМ", 0xB07AFF, 0x5A2AB0, true, () -> send("cosm_back", ""),
            () -> List.of(Text.literal("§dВернуться в магазин кейсов")));
        button(c, "close", 6, 68, 92, 26, "ЗАКРЫТЬ", 0xFF5A4A, 0xC02828, true,
            () -> MinecraftClient.getInstance().setScreen(null), () -> List.of(Text.literal("§7Esc")));

        panel(c, 6, 100, 92, 70);
        text(c, "КАК ПОЛЬЗОВАТЬСЯ", 52, 104, 0.58f, 0xFFFFD0F0, 1);
        text(c, "Нажми на карточку:", 52, 115, 0.55f, 0xFFD0D0D8, 1);
        text(c, "• куплено — надеть", 52, 124, 0.52f, 0xFFB8F0C0, 1);
        text(c, "• надето — снять", 52, 132, 0.52f, 0xFFFFE08A, 1);
        text(c, "• иначе — купить", 52, 140, 0.52f, 0xFFE89AFF, 1);
        text(c, "Косметика остаётся", 52, 152, 0.52f, 0xFFA8A8B0, 1);
        text(c, "навсегда и не", 52, 159, 0.52f, 0xFFA8A8B0, 1);
        text(c, "пропадает при сбросе", 52, 166, 0.52f, 0xFFA8A8B0, 1);
        text(c, "Одно надето на тип", 52, 178, 0.5f, 0xAAFFFFFF, 1);
    }

    // ================= вкладки =================
    private void drawTabs(DrawContext c, long now) {
        float tw = 94, gap = 6, x0 = 258 - (3 * tw + 2 * gap) / 2f, y = 84;
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            boolean sel = tab == i;
            int count = 0, have = 0;
            for (Item it : d.items) if (it.type.equals(TABS[i])) { count++; if (it.owned) have++; }
            float x = x0 + i * (tw + gap);
            float ha = hov.getOrDefault("tab" + i, 0f);
            c.fill((int) x - 1, (int) y - 1, (int) (x + tw) + 1, (int) y + 15, 0xFF000000);
            c.fillGradient((int) x, (int) y, (int) (x + tw), (int) y + 14, sel ? 0xFFB07AFF : 0xFF3A2266, sel ? 0xFF5A2AB0 : 0xFF1C1034);
            if (ha > 0.05f) c.fill((int) x, (int) y, (int) (x + tw), (int) y + 14, ((int) (40 * ha) << 24) | 0xFFFFFF);
            c.fill((int) x + 1, (int) y + 1, (int) (x + tw) - 1, (int) y + 2, 0x55FFFFFF);
            text(c, TAB_NAMES[i] + "  " + have + "/" + count, x + tw / 2f, y + 3.5f, 0.72f, sel ? 0xFFFFFFFF : 0xFFC8B8E8, 1);
            hit(x, y, tw, 14, "tab" + i, () -> {
                if (tab != idx) { tab = idx; rowScroll = 0; HudModel.play(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.4f); }
            }, null);
        }
    }

    // ================= карточки =================
    private void drawGrid(DrawContext c, long now) {
        List<Item> list = current();
        float cw = 98, ch = 60, gap = 6;
        float x0 = 258 - (COLS * cw + (COLS - 1) * gap) / 2f, y0 = 106;
        if (list.isEmpty()) { text(c, "Здесь пока пусто", 258, 150, 1f, 0xFFB8B8C0, 1); return; }
        int totalRows = (list.size() + COLS - 1) / COLS;
        rowScroll = Math.max(0, Math.min(Math.max(0, totalRows - ROWS), rowScroll));
        for (int r = 0; r < ROWS; r++) for (int col = 0; col < COLS; col++) {
            int idx = (r + rowScroll) * COLS + col;
            if (idx >= list.size()) continue;
            drawCard(c, list.get(idx), idx, x0 + col * (cw + gap), y0 + r * (ch + gap), cw, ch, now, r * COLS + col);
        }
        if (totalRows > ROWS) {
            text(c, rowScroll > 0 ? "▲ колесо мыши" : "▼ колесо мыши — ещё", 258, 232, 0.5f, 0xAAFFFFFF, 1);
        }
    }

    private void drawCard(DrawContext c, Item it, int idx, float x, float y, float w, float h, long now, int order) {
        String key = "card" + it.id;
        float ha = hov.getOrDefault(key, 0f);
        boolean afford = d.essence >= it.price;
        float dealT = Math.min(1f, Math.max(0f, ((now - openAt) / 1e6f - 90f * order - 150f) / 420f));
        float deal = 1f - (1f - dealT) * (1f - dealT) * (1f - dealT);
        if (deal <= 0.01f) return;
        float flash = it.id.equals(flashId) ? Math.max(0f, 1f - (now - flashAt) / 6e8f) : 0f;
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(x + w / 2f, y + h / 2f - 2f * ha + (1f - deal) * 50f, 0);
        m.scale(1f + 0.03f * ha, 1f + 0.03f * ha, 1f);
        m.translate(-w / 2f, -h / 2f, 0);
        int iw = (int) w, ih = (int) h;
        int border = it.on ? 0xFFFFE066 : it.owned ? 0xFF5CE08A : 0xFF6A4A9A;
        c.fill(-1, -1, iw + 1, ih + 1, 0xFF000000);
        c.fill(0, 0, iw, ih, border);
        c.fillGradient(1, 1, iw - 1, ih - 1, it.on ? 0xFF4A3A22 : 0xFF3A2266, it.on ? 0xFF241A0C : 0xFF140A28);
        c.fill(1, 1, iw - 1, 2, 0x55FFFFFF);
        if (it.on) {
            for (int r = 0; r < 6; r++) {
                m.push();
                m.translate(20, 22, 0);
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (now / 3e7 % 360) + r * 60));
                c.fill(-1, 0, 1, 18, 0x22FFE066);
                m.pop();
            }
        }
        if (ha > 0.05f) c.fill(1, 1, iw - 1, ih - 1, ((int) (22 * ha) << 24) | 0xFFFFFF);
        if (flash > 0) c.fill(1, 1, iw - 1, ih - 1, ((int) (110 * flash) << 24) | 0xFFFFFF);
        // иконка
        m.push();
        m.translate(20, 22 + (float) Math.sin(now / 7e8 + idx) * 1.2f, 50);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.sin(now / 9e8 + idx) * 5f));
        m.scale(2.2f, 2.2f, 1f);
        m.translate(-8, -8, 0);
        c.drawItem(it.icon, 0, 0);
        m.pop();
        // название и описание
        List<String> nl = wrap(it.name, (int) ((w - 42) / 0.66f));
        for (int k = 0; k < nl.size() && k < 2; k++) text(c, nl.get(k), 40, 5 + k * 8, 0.66f, 0xFFFFFFFF, 0);
        int dy = 5 + Math.min(2, nl.size()) * 8 + 1;
        List<String> dl = wrap(it.desc, (int) ((w - 44) / 0.5f));
        for (int k = 0; k < dl.size() && k < 3; k++) text(c, dl.get(k), 40, dy + k * 6.5f, 0.5f, 0xFFB8B0D0, 0);
        // нижняя плашка состояния
        int by = ih - 15;
        c.fill(1, by - 1, iw - 1, ih - 1, 0xC8000000);
        if (it.type.equals("title") && !it.prefix.isEmpty())
            text(c, fit(it.prefix + d.player, (int) (w - 8), 0.62f), 5, by + 2f, 0.62f, 0xFFFFFFFF, 0);
        else if (it.type.equals("pet")) text(c, "Питомец — ходит рядом", 5, by + 2.5f, 0.52f, 0xFFA8A8B0, 0);
        else if (it.type.equals("trail")) text(c, "След частиц при ходьбе", 5, by + 2.5f, 0.52f, 0xFFA8A8B0, 0);
        // кнопка состояния
        String label; int top, bot;
        if (it.on) { label = "СНЯТЬ"; top = 0xFFD45B; bot = 0xA8740E; }
        else if (it.owned) { label = "НАДЕТЬ"; top = 0x6FE08A; bot = 0x1E8A4A; }
        else { label = "КУПИТЬ"; top = afford ? 0xE89AFF : 0x8A8A92; bot = afford ? 0x8A2AB0 : 0x44444A; }
        c.fill(iw - 53, ih - 14, iw - 2, ih - 2, 0xFF000000);
        c.fillGradient(iw - 52, ih - 13, iw - 3, ih - 3, 0xFF000000 | top, 0xFF000000 | bot);
        c.fill(iw - 52, ih - 13, iw - 3, ih - 12, 0x66FFFFFF);
        if (!it.owned) {
            m.push(); m.translate(iw - 50, ih - 12.5f, 0); m.scale(0.5f, 0.5f, 1f); c.drawItem(ESSENCE, 0, 0); m.pop();
            text(c, Integer.toString(it.price), iw - 5, ih - 11.5f, 0.78f, afford ? 0xFFFFFFFF : 0xFFFF9A9A, 2);
        } else text(c, label, iw - 27.5f, ih - 11.5f, 0.72f, 0xFFFFFFFF, 1);
        // метка «надето»
        if (it.on) {
            c.fill(iw - 40, 3, iw - 3, 11, 0xFF000000);
            c.fillGradient(iw - 39, 4, iw - 4, 10, 0xFFFFE066, 0xFFC08A10);
            text(c, "НАДЕТО", iw - 21.5f, 4.4f, 0.5f, 0xFF3A2A00, 1);
        } else if (it.owned) {
            text(c, "✔ куплено", iw - 4, 4, 0.5f, 0xFF8EF0A0, 2);
        }
        m.pop();

        Hit hh = new Hit();
        hh.x = x; hh.y = y; hh.w = w; hh.h = h; hh.key = key;
        boolean enabled = it.owned || afford;
        hh.click = enabled ? () -> { flashId = it.id; flashAt = System.nanoTime(); send("cosm_click", it.id); }
                           : () -> HudModel.play(SoundEvents.ENTITY_VILLAGER_NO, 1.2f, 0.3f);
        hh.tip = () -> {
            List<Text> t = new ArrayList<>();
            t.add(Text.literal("§d§l" + it.name));
            for (String l : wrap(it.desc, 230)) t.add(Text.literal("§7" + l));
            if (it.type.equals("title") && !it.prefix.isEmpty()) t.add(Text.literal("§8Так увидят ник: §r" + it.prefix + d.player));
            t.add(Text.literal(it.on ? "§eНадето — нажми, чтобы снять" : it.owned ? "§aКуплено — нажми, чтобы надеть"
                : (afford ? "§dЦена: " : "§cЦена: ") + it.price + " эссенции"));
            return t;
        };
        hits.add(hh);
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
        text(c, label, w / 2f, (h - 9f) / 2f, 1.0f, enabled ? 0xFFFFFFFF : 0xFF909098, 1);
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
        float w = Math.min(400f, textRenderer.getWidth(toast) * 0.8f + 14f);
        int col = toastOk ? 0x2E8B57 : 0xB02A2A;
        float shake = !toastOk && t < 300f ? (float) Math.sin(t / 25f) * 2f : 0f;
        c.fill((int) (258 - w / 2 + shake) - 1, (int) y - 1, (int) (258 + w / 2 + shake) + 1, (int) y + 11, argb(0x000000, a));
        c.fill((int) (258 - w / 2 + shake), (int) y, (int) (258 + w / 2 + shake), (int) y + 10, argb(col, a * 0.95f));
        text(c, fit(toast, 390, 0.8f), 258 + shake, y + 1.8f, 0.8f, argb(0xFFFFFF, a), 1);
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
