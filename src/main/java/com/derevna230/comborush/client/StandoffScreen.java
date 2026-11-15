package com.derevna230.comborush.client;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.hud.ShopActionPayload;
import com.derevna230.comborush.item.GunDefs;
import com.derevna230.comborush.lobby.LoadoutDefs;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.entity.player.PlayerModelPart;
import org.joml.Quaternionf;
import software.bernie.geckolib.animatable.GeoItem;

import java.util.*;

/**
 * Меню «Противостояния» в духе CS:GO. Главная — игрок с оружием в руке и группа; Снаряжение — выбор скинов,
 * осмотр и вращение ствола на ПКМ; Карты и Настройки пока пустые. Рисуется в условных 420×250.
 * Игрок стоит в тёмной комнате (измерение «standoff»); экран открывается сам, пока игрок в ней.
 */
public class StandoffScreen extends Screen {
    private static final int DW = 420, DH = 250;
    private static final String[] TABS = {"ГЛАВНАЯ", "СНАРЯЖЕНИЕ", "ОБМЕН", "РЫНОК", "КАРТЫ", "НАСТРОЙКИ"};
    /** номера вкладок в TABS → внутренние номера экранов (рынок — 5) */
    private static final int[] TAB_ID = {0, 1, 2, 5, 3, 4};
    private static final int GOLD = 0xE8B33A, DARK = 0x10131A;

    // ---- состояние с сервера
    private record Member(String name, boolean leader, boolean me) {}
    private record Online(String name, boolean busy) {}
    private static volatile List<Member> members = List.of();
    private static volatile List<Online> online = List.of();
    private static volatile String inviteFrom = null;
    private static volatile Map<String, String> loadout = Map.of();
    private static volatile Set<String> owned = Set.of();
    private static volatile Map<String, Integer> counts = Map.of();         // повторы скинов: показываем ×N
    private static volatile int essence = 0, partyMax = 5;
    private record TStack(String id, String loc, float wf, boolean st) {}
    private record MLot(int id, String item, float wf, boolean st, int price, String seller, boolean mine) {}
    private record MMine(String loc, String item, float wf, boolean st, int n, int q) {}
    private static volatile List<MLot> mLots = List.of();
    private static volatile List<MMine> mMine = List.of();
    private static volatile List<TStack> tstacks = List.of();                  // скины, которые можно положить в контракт
    private static volatile JsonObject tradedRes = null;                       // итог последнего контракта
    private static volatile long tradedAt = 0;
    private static volatile boolean clearSel = false;

    private static String featured = LoadoutDefs.candidates("ak47").get(0);      // что в руке на главной (id слота)
    private static boolean listening = false;

    public static void onPacket(JsonObject o) {
        List<Member> ms = new ArrayList<>();
        JsonObject party = o.getAsJsonObject("party");
        if (party != null) {
            partyMax = party.has("max") ? party.get("max").getAsInt() : 5;
            for (JsonElement e : party.getAsJsonArray("members")) {
                JsonObject m = e.getAsJsonObject();
                ms.add(new Member(m.get("name").getAsString(), m.get("leader").getAsBoolean(), m.get("me").getAsBoolean()));
            }
        }
        members = ms;
        List<Online> on = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("online")) {
            JsonObject m = e.getAsJsonObject();
            on.add(new Online(m.get("name").getAsString(), m.get("busy").getAsBoolean()));
        }
        online = on;
        inviteFrom = o.has("invite") ? o.get("invite").getAsString() : null;
        Map<String, String> lo = new HashMap<>();
        if (o.has("loadout")) for (var e : o.getAsJsonObject("loadout").entrySet()) lo.put(e.getKey(), e.getValue().getAsString());
        loadout = lo;
        com.derevna230.comborush.client.agent.AgentClient.setGlove(lo.getOrDefault(LoadoutDefs.GLOVES_SLOT, ""));
        com.derevna230.comborush.client.agent.AgentClient.setLocal(lo.get(com.derevna230.comborush.lobby.AgentDefs.SLOT_T), lo.get(com.derevna230.comborush.lobby.AgentDefs.SLOT_CT));
        Set<String> ow = new HashSet<>();
        if (o.has("owned")) for (JsonElement e : o.getAsJsonArray("owned")) ow.add(e.getAsString());
        owned = ow;
        Map<String, Integer> cn = new HashMap<>();
        if (o.has("counts")) for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("counts").entrySet()) cn.put(e.getKey(), e.getValue().getAsInt());
        counts = cn;
        essence = o.has("essence") ? o.get("essence").getAsInt() : 0;
        List<TStack> ts = new ArrayList<>();
        if (o.has("stacks")) for (JsonElement e : o.getAsJsonArray("stacks")) {
            JsonObject t = e.getAsJsonObject();
            ts.add(new TStack(t.get("id").getAsString(), t.get("loc").getAsString(), t.get("wf").getAsFloat(), t.get("st").getAsInt() == 1));
        }
        tstacks = ts;
        if (o.has("market")) {
            JsonObject mk = o.getAsJsonObject("market");
            List<MLot> ml = new ArrayList<>();
            for (JsonElement e : mk.getAsJsonArray("lots")) {
                JsonObject j = e.getAsJsonObject();
                ml.add(new MLot(j.get("id").getAsInt(), j.get("item").getAsString(), j.get("wf").getAsFloat(), j.get("st").getAsInt() == 1,
                    j.get("price").getAsInt(), j.get("seller").getAsString(), j.get("mine").getAsBoolean()));
            }
            List<MMine> mm = new ArrayList<>();
            for (JsonElement e : mk.getAsJsonArray("mine")) {
                JsonObject j = e.getAsJsonObject();
                mm.add(new MMine(j.get("loc").getAsString(), j.get("item").getAsString(), j.get("wf").getAsFloat(), j.get("st").getAsInt() == 1,
                    j.get("n").getAsInt(), j.get("q").getAsInt()));
            }
            mLots = ml; mMine = mm;
        }
        if (o.has("traded")) {
            tradedRes = o.getAsJsonObject("traded"); tradedAt = System.currentTimeMillis(); clearSel = true;
            HudModel.play(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 0.9f);
        }
    }

    /** Вызывать каждый тик клиента: пока игрок в комнате меню и других экранов нет — открыть меню */
    public static void tickReopen(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        boolean inRoom = mc.world.getRegistryKey().getValue().getPath().equals("standoff")
            && mc.world.getRegistryKey().getValue().getNamespace().equals(ComboRush.MOD_ID);
        if (inRoom && mc.currentScreen == null && mc.player.age > 5) mc.setScreen(new StandoffScreen());
    }

    // ---- интерфейс
    private static final class Hit {
        float x, y, w, h; Runnable click;
        boolean contains(float px, float py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }

    private float u = 1, ox, oy, mx, my;
    private long lastNs = 0;
    private final long openAt = System.nanoTime();
    private final List<Hit> hits = new ArrayList<>();
    private int tab = 0;
    private int onlineScroll = 0;
    private long lastPoll = 0;
    private final Map<String, Float> hov = new HashMap<>();

    // снаряжение
    private String selSlot = featured;
    private String selItem = null;                 // что показано в окне просмотра
    private float rotY = 0.5f, spin = 0f;
    private boolean dragging = false;
    private double lastMouseX;
    private int slotScroll = 0;

    // игрок на главной
    private PreviewPlayer dummy;
    private long nextAnimAt = 0, animStart = 0;
    private String anim = "";

    public StandoffScreen() {
        super(Text.literal("Противостояние"));
    }

    @Override
    protected void init() {
        HudModel.play(SoundEvents.BLOCK_BEACON_ACTIVATE, 1.4f, 0.4f);
        send("so_state", "");
        selItem = equippedOf(selSlot);
        nextAnimAt = System.nanoTime() + 1_200_000_000L;
    }

    @Override public boolean shouldPause() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) { }

    private static void send(String action, String c) {
        if (ClientPlayNetworking.canSend(ShopActionPayload.ID)) ClientPlayNetworking.send(new ShopActionPayload(action, 0, 0, c));
    }

    // ================= данные предметов =================
    private static String slotLabel(String slot) {
        if (slot.equals(LoadoutDefs.KNIFE_SLOT)) return "Нож";
        GunDefs.Gun g = GunDefs.gun(slot);
        if (g != null) return g.name();
        return switch (slot) {
            case "grenade_he" -> "Осколочная"; case "grenade_flash" -> "Светошумовая";
            case "grenade_smoke" -> "Дымовая"; case "grenade_molotov" -> "Молотов";
            default -> slot;
        };
    }

    private static String equippedOf(String slot) {
        String v = loadout.get(slot);
        return v == null ? LoadoutDefs.candidates(slot).get(0) : v;
    }

    private static boolean has(String slot, String item) { return LoadoutDefs.isStandard(slot, item) || owned.contains(item); }

    private static String rarityOf(String item) {
        for (GunDefs.Skin s : GunDefs.SKINS) if (s.id().equals(item)) return s.rarity();
        return item.equals("claw") || item.equals("bayonet") || item.equals("skeleton") || item.equals("gurkha") ? "mythic" : "common";
    }

    private static int rarColor(String r) {
        return switch (r) {
            case "uncommon" -> 0x6BE06B; case "rare" -> 0x5A8CFF; case "epic" -> 0xB46BFF;
            case "legendary" -> 0xFFB02E; case "mythic" -> 0xFF4A4A; default -> 0xC8C8D0;
        };
    }

    private static String rarLabel(String r) {
        return switch (r) {
            case "uncommon" -> "Необычный"; case "rare" -> "Редкий"; case "epic" -> "Эпический";
            case "legendary" -> "Легендарный"; case "mythic" -> "Тайный"; default -> "Стандартный";
        };
    }

    private static final Map<String, ItemStack> STACKS = new HashMap<>();

    /** Предмет для показа: с постоянным номером GeckoLib, чтобы его анимации (осмотр, доставание) играли на месте */
    private static ItemStack stackOf(String id) {
        return STACKS.computeIfAbsent(id, k -> {
            Item it = Registries.ITEM.get(Identifier.of(ComboRush.MOD_ID, k));
            return new ItemStack(it);          // без номера GeckoLib: рендерер и «play» используют один и тот же менеджер предмета
        });
    }

    private static long gid(ItemStack st) {
        return GeoItem.getId(st);
    }

    private static String itemName(String id) {
        if (com.derevna230.comborush.lobby.AgentDefs.isAgent(id)) return com.derevna230.comborush.lobby.AgentDefs.name(id);
        return stackOf(id).getName().getString();
    }

    /** Значок 16×16: предмет или лицо агента */
    private static void drawIcon(DrawContext c, String id, int x, int y) {
        if (com.derevna230.comborush.lobby.AgentDefs.isAgent(id)) {
            int[] ic = com.derevna230.comborush.client.agent.AgentClient.icon(id);
            c.drawTexture(Identifier.of(ComboRush.MOD_ID, "textures/entity/agent_" + id + ".png"), x, y, 16, 16, ic[0], ic[1], ic[2], ic[3], ic[4], ic[5]);
            return;
        }
        c.drawItem(stackOf(id), x, y);
    }

    /** Проиграть анимацию предмета (осмотр, доставание) на экране */
    private static void play(ItemStack st, String name) {
        if (!(st.getItem() instanceof GeoItem gi)) return;
        try {
            var mgr = gi.getAnimatableInstanceCache().getManagerForId(gid(st));
            if (mgr != null) mgr.tryTriggerAnimation("main", name);
        } catch (RuntimeException ignored) { /* анимация — только украшение */ }
    }

    // ================= игрок =================
    private static final class PreviewPlayer extends OtherClientPlayerEntity {
        PreviewPlayer(ClientWorld w, GameProfile p) {
            super(w, p);
            this.setPosition(0, 5000, 0);       // далеко от камеры: без таблички с ником
        }
        @Override public boolean isPartVisible(PlayerModelPart part) { return true; }
    }

    private PreviewPlayer dummy() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (dummy == null && mc.world != null && mc.player != null) dummy = new PreviewPlayer(mc.world, mc.player.getGameProfile());
        return dummy;
    }

    // ================= ввод =================
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {                  // Esc — обычное меню паузы; закроется — меню вернётся
            MinecraftClient.getInstance().setScreen(new GameMenuScreen(true));
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_V) { inspect(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float px = (float) ((mouseX - ox) / u), py = (float) ((mouseY - oy) / u);
        if (tradedRes != null && System.currentTimeMillis() - tradedAt > 300) { tradedRes = null; return true; }
        if (button == 1 && tab == 1 && inViewer(px, py)) { dragging = true; lastMouseX = mouseX; return true; }
        if (button != 0) return true;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.contains(px, py)) { if (h.click != null) h.click.run(); return true; }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging && button == 1) {
            rotY += (float) (mouseX - lastMouseX) * 0.012f;
            spin = (float) (mouseX - lastMouseX) * 0.012f;
            lastMouseX = mouseX;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 1) dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        float px = (float) ((mouseX - ox) / u);
        int d = vertical > 0 ? -1 : 1;
        if (tab == 1 && px < 298) gridScroll += d;
        if (tab == 2 && px < 298) tScroll += d;
        if (tab == 5) { if (px < 209) mScrollL = Math.max(0, mScrollL + d); else mScrollR = Math.max(0, mScrollR + d); }
        if (tab == 0 && px > 296) onlineScroll = Math.max(0, Math.min(Math.max(0, online.size() - 5), onlineScroll + d));
        return true;
    }

    private boolean inViewer(float px, float py) { return px >= VX && px < VX + VW && py >= VY && py < VY + VH; }

    private void inspect() {
        if (tab == 1 && selItem != null) play(stackOf(selItem), "inspect");

        HudModel.play(SoundEvents.ITEM_ARMOR_EQUIP_IRON.value(), 1.2f, 0.4f);
    }

    // ================= отрисовка =================
    private static final int VX = 300, VY = 34, VW = 112, VH = 210;      // окно просмотра ствола

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        u = Math.max(0.5f, Math.min(width / (float) DW, height / (float) DH));
        ox = (width - DW * u) / 2f; oy = (height - DH * u) / 2f;
        mx = (mouseX - ox) / u; my = (mouseY - oy) / u;
        if (now - lastPoll > 3_000_000_000L) { lastPoll = now; send("so_state", ""); }

        hits.clear();

        drawBackground(c, now);
        float appear = Math.min(1f, (now - openAt) / 1e6f / 350f);
        // игрока рисуем в реальных координатах вне масштаба
        if (tab == 0) drawPlayer(c, now, mouseX, mouseY, appear);

        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(ox, oy, 0);
        m.scale(u, u, 1f);
        switch (tab) {
            case 0 -> drawHome(c, now);
            case 1 -> drawEquipment(c, now, dt);
            case 2 -> drawTrade(c, now);
            case 3 -> drawMaps(c, now);
            case 5 -> drawMarket(c, now);
            default -> drawSettings(c);
        }
        drawTopBar(c, now);
        drawInvite(c, now);
        drawMatchResult(c);
        m.pop();
    }

    // ---- фон
    private void drawBackground(DrawContext c, long now) {
        c.fillGradient(0, 0, width, height, 0xFF141A24, 0xFF06080C);
        for (int y = 0; y < height; y += 3) c.fill(0, y, width, y + 1, 0x07FFFFFF);
        // диагональные световые полосы, как на фоне главного меню
        float t = now / 4e9f;
        for (int i = 0; i < 5; i++) {
            int x0 = (int) (((i * 0.27f + t) % 1.4f - 0.2f) * width);
            for (int k = 0; k < 40; k++) c.fill(x0 + k, 0, x0 + k + 1, height, ((int) (7 * (1 - k / 40f)) << 24) | 0x6A8CCF);
        }
        int vw = Math.max(10, width / 10);
        for (int i = 0; i < vw; i++) {
            int a = (int) (170 * Math.pow(1 - i / (float) vw, 2));
            c.fill(i, 0, i + 1, height, a << 24);
            c.fill(width - i - 1, 0, width - i, height, a << 24);
        }
        c.fillGradient(0, height - height / 5, width, height, 0x00000000, 0xDD000000);
    }

    // ---- верхняя панель
    private void drawTopBar(DrawContext c, long now) {
        c.fillGradient(-200, 0, DW + 200, 30, 0xB0090C12, 0x00090C12);          // мягкая подложка без жёсткой границы
        // выход
        button(c, "exit", 6, 5, 62, 16, "◀  ЛОББИ", 0x3A4256, () -> { send("so_exit", ""); close(); });
        // вкладки
        int tw = 50, x0 = 72;
        for (int i = 0; i < TABS.length; i++) {
            final int idx = TAB_ID[i];
            float x = x0 + i * tw;
            boolean sel = tab == idx;
            float h = hoverAmt("tab" + i, hitOver(x, 0, tw, 26));
            if (sel || h > 0.02f) c.fill((int) x, 0, (int) (x + tw), 26, ((int) ((sel ? 34 : 20 * h)) << 24) | 0xFFFFFF);
            text(c, TABS[i], x + tw / 2f, 9.5f, 0.78f, sel ? 0xFFFFFFFF : 0xFFA9B4C9, 1);
            if (sel) c.fill((int) x + 6, 24, (int) (x + tw - 6), 27, 0xFF000000 | GOLD);
            hit(x, 0, tw, 26, () -> { tab = idx; HudModel.play(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.5f);
                if (idx == 1) { selItem = equippedOf(selSlot); } });
        }
        // эссенция
        text(c, "✦ " + essence, DW - 8, 9, 0.95f, 0xFFD7A8FF, 2);
    }

    // ---- итоги матча: награды по игре
    private void drawMatchResult(DrawContext c) {
        com.google.gson.JsonObject r = com.derevna230.comborush.client.match.MatchClient.result;
        if (r == null) return;
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(0, 0, 400);
        c.fill(-200, -200, DW + 200, DH + 200, 0xB0000000);
        float x = 110, y = 30, w = 200, h = 196;
        panel(c, (int) x, (int) y, (int) w, (int) h);
        String res = r.get("result").getAsString();
        String title = res.equals("win") ? "§a§lПОБЕДА" : res.equals("draw") ? "§e§lНИЧЬЯ" : "§c§lПОРАЖЕНИЕ";
        text(c, title, x + w / 2, y + 8, 1.4f, 0xFFFFFFFF, 1);
        text(c, "Счёт " + r.get("score").getAsString(), x + w / 2, y + 24, 0.8f, 0xFFB8C2D6, 1);
        text(c, "У " + r.get("k").getAsInt() + "  ·  П " + r.get("a").getAsInt() + "  ·  С " + r.get("d").getAsInt()
            + "  ·  урон " + r.get("dmg").getAsInt() + "  ·  ★" + r.get("mvp").getAsInt(), x + w / 2, y + 36, 0.7f, 0xFFFFFFFF, 1);
        if (r.get("best").getAsBoolean()) text(c, "§6★ Лучший игрок матча", x + w / 2, y + 46, 0.7f, 0xFFFFFFFF, 1);
        float ly = y + 58;
        for (com.google.gson.JsonElement e : r.getAsJsonArray("lines")) {
            com.google.gson.JsonObject l = e.getAsJsonObject();
            text(c, l.get("n").getAsString(), x + 14, ly, 0.68f, 0xFFB8C2D6, 0);
            text(c, "§d+" + l.get("v").getAsInt() + " ✦", x + w - 14, ly, 0.68f, 0xFFFFFFFF, 2);
            ly += 10;
        }
        c.fill((int) x + 12, (int) ly + 1, (int) (x + w - 12), (int) ly + 2, 0x40FFFFFF);
        text(c, "Итого", x + 14, ly + 5, 0.85f, 0xFFFFFFFF, 0);
        text(c, "§d+" + r.get("essence").getAsInt() + " ✦", x + w - 14, ly + 5, 0.85f, 0xFFFFFFFF, 2);
        String cs = r.has("case") ? "§6Выпал кейс «" + r.get("case").getAsString() + "»!" : "§7Кейс не выпал (шанс был " + r.get("chance").getAsInt() + "%)";
        text(c, cs, x + w / 2, y + h - 30, 0.75f, 0xFFFFFFFF, 1);
        text(c, "Чем лучше игра — тем больше эссенции и выше шанс кейса", x + w / 2, y + h - 19, 0.55f, 0xFF6F7A90, 1);
        text(c, "клик — закрыть", x + w / 2, y + h - 10, 0.55f, 0xFF6F7A90, 1);
        m.pop();
        hit(-200, -200, DW + 400, DH + 400, () -> com.derevna230.comborush.client.match.MatchClient.result = null);
    }

    // ---- приглашение в группу
    private void drawInvite(DrawContext c, long now) {
        String from = inviteFrom;
        if (from == null) return;
        float w = 250, h = 22, x = (DW - w) / 2f, y = 32;
        c.fill((int) x - 1, (int) y - 1, (int) (x + w) + 1, (int) (y + h) + 1, 0xFF000000 | GOLD);
        c.fillGradient((int) x, (int) y, (int) (x + w), (int) (y + h), 0xFF1C2230, 0xFF0C1018);
        text(c, "§e" + from + " §fприглашает вас в группу", x + 8, y + 7, 0.85f, 0xFFFFFFFF, 0);
        button(c, "acc", x + w - 108, y + 3, 50, 16, "Принять", 0x3C9A52, () -> send("so_accept", ""));
        button(c, "dec", x + w - 54, y + 3, 50, 16, "Отказ", 0x9A3C3C, () -> send("so_decline", ""));
    }

    // ================= ГЛАВНАЯ =================
    private void drawHome(DrawContext c, long now) {
        // левая панель
        panel(c, 8, 34, 112, 120);
        text(c, "ПРОТИВОСТОЯНИЕ", 64, 42, 0.95f, 0xFF000000 | GOLD, 1);
        c.fill(16, 54, 112, 55, 0x40FFFFFF);
        String[] info = {"Соревновательный: до 13 побед,", "12:12 — ничья, смена сторон", "после 10-го раунда. E — закупка.", "5 на 5, недостающих — боты."};
        for (int i = 0; i < info.length; i++) text(c, info[i], 14, 60 + i * 10, 0.72f, 0xFFB8C2D6, 0);
        button(c, "playT", 14, 102, 100, 16, "ИГРАТЬ ЗА  T", 0xB5651D, () -> { send("so_play", "t|" + selMap); close(); });
        button(c, "playCT", 14, 121, 100, 16, "ИГРАТЬ ЗА  CT", 0x2F5FA8, () -> { send("so_play", "ct|" + selMap); close(); });
        button(c, "mapCycle", 14, 140, 100, 11, "Карта: «" + mapTitle(selMap) + "»  ▸", 0x2A3242,
            () -> selMap = selMap.equals("dust") ? "medina" : "dust");
        panel(c, 8, 160, 112, 80);
        text(c, "КЕЙС «АРСЕНАЛ»", 64, 168, 0.85f, 0xFFFFB02E, 1);
        String[] cs = {"Выпадает после матчей.", "Ключ стоит 250 эссенции:", "его покупают при открытии", "кейса из инвентаря."};
        for (int i = 0; i < cs.length; i++) text(c, cs[i], 14, 182 + i * 10, 0.7f, 0xFFB8C2D6, 0);
        text(c, "§7Скин в руке: §f" + itemName(equippedOf(featured)), 14, 226, 0.7f, 0xFFFFFFFF, 0);

        drawParty(c, now);
        // подпись под моделью
        boolean ct = com.derevna230.comborush.client.agent.AgentClient.menuCT();
        button(c, "agentCT", 156, 40, 52, 12, (ct ? "● " : "") + "СПЕЦНАЗ", ct ? 0x2F5FA8 : 0x2A3242,
            () -> com.derevna230.comborush.client.agent.AgentClient.setMenuCT(true));
        button(c, "agentT", 212, 40, 52, 12, (!ct ? "● " : "") + "БОЕВИК", !ct ? 0xB5651D : 0x2A3242,
            () -> com.derevna230.comborush.client.agent.AgentClient.setMenuCT(false));
        text(c, "Облик в меню · в матче — по стороне. Скины — «Снаряжение»", 210, 234, 0.6f, 0xFF6F7A90, 1);
    }

    private void drawParty(DrawContext c, long now) {
        float x = 298, w = 114;
        panel(c, (int) x, 34, (int) w, 206);
        List<Member> ms = members;
        text(c, "ГРУППА  " + Math.max(1, ms.size()) + "/" + partyMax, x + w / 2, 41, 0.9f, 0xFF000000 | GOLD, 1);
        c.fill((int) x + 6, 53, (int) (x + w - 6), 54, 0x40FFFFFF);
        boolean iAmLeader = ms.isEmpty() || ms.stream().anyMatch(m -> m.me() && m.leader());
        float y = 58;
        if (ms.isEmpty()) {
            text(c, "★ " + MinecraftClient.getInstance().getSession().getUsername(), x + 8, y, 0.8f, 0xFFFFFFFF, 0);
            y += 12;
        }
        for (Member m : ms) {
            text(c, (m.leader() ? "§6★ " : "  ") + (m.me() ? "§f" : "§7") + m.name(), x + 8, y, 0.8f, 0xFFFFFFFF, 0);
            if (iAmLeader && !m.me()) {
                final String nm = m.name();
                button(c, "kick" + nm, x + w - 18, y - 3, 12, 12, "×", 0x9A3C3C, () -> send("so_kick", nm));
            }
            y += 12;
        }
        if (ms.size() > 1) { button(c, "leave", x + 8, y + 2, w - 16, 14, "Покинуть группу", 0x5A4050, () -> send("so_leave_party", "")); y += 20; }
        y = Math.max(y + 4, 112);
        c.fill((int) x + 6, (int) y - 3, (int) (x + w - 6), (int) y - 2, 0x40FFFFFF);
        text(c, "ИГРОКИ В ЛОББИ", x + w / 2, y + 2, 0.78f, 0xFF9AA6BD, 1);
        y += 14;
        List<Online> on = online;
        if (on.isEmpty()) {
            text(c, "Никого нет рядом", x + w / 2, y + 4, 0.72f, 0xFF6F7A90, 1);
            text(c, "Друг зайдёт в Лобби —", x + w / 2, y + 18, 0.66f, 0xFF6F7A90, 1);
            text(c, "появится здесь", x + w / 2, y + 28, 0.66f, 0xFF6F7A90, 1);
        } else {
            for (int i = onlineScroll; i < Math.min(on.size(), onlineScroll + 5); i++) {
                Online o = on.get(i);
                text(c, (o.busy() ? "§8" : "§f") + trim(o.name(), 11), x + 8, y + 2, 0.78f, 0xFFFFFFFF, 0);
                final String nm = o.name();
                boolean can = !o.busy() && iAmLeader && ms.size() < partyMax;
                button(c, "inv" + nm, x + w - 40, y - 1, 34, 12, o.busy() ? "в группе" : "+ звать", can ? 0x2F7D4A : 0x3A4050, can ? () -> send("so_invite", nm) : null);
                y += 16;
            }
            if (on.size() > 5) text(c, "колесо мыши — ещё", x + w / 2, y + 2, 0.62f, 0xFF6F7A90, 1);
        }
        if (!iAmLeader) text(c, "Звать может лидер", x + w / 2, 230, 0.65f, 0xFF8A94A8, 1);
    }

    private static String trim(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "…"; }

    /** Игрок посреди экрана: оружие в руке, время от времени — небольшая анимация */
    private void drawPlayer(DrawContext c, long now, int mouseX, int mouseY, float appear) {
        PreviewPlayer p = dummy();
        if (p == null) return;
        String item = equippedOf(featured);
        ItemStack st = stackOf(item);
        if (!ItemStack.areItemsEqual(p.getEquippedStack(EquipmentSlot.MAINHAND), st)) p.equipStack(EquipmentSlot.MAINHAND, st);
        p.age = (int) (now / 50_000_000L);
        // расписание анимаций
        double t = (now - animStart) / 1e9;
        if (now >= nextAnimAt) {
            String[] pool = {"draw", "look", "crouch", "look"};          // осмотра оружия у персонажа в меню нет
            anim = pool[(int) (Math.random() * pool.length)];
            animStart = now;
            nextAnimAt = now + (long) ((anim.equals("inspect") ? 5.0 : 3.5) * 1e9) + (long) (Math.random() * 3e9);
            t = 0;
            if (anim.equals("inspect")) play(st, "inspect");
            else if (anim.equals("draw")) play(st, "draw");
        }
        // замах рукой / присед
        float swing = 0f;
        if (anim.equals("swing") && t < 0.9) swing = (float) ((t * 2.2) % 1.0);
        p.lastHandSwingProgress = swing; p.handSwingProgress = swing;
        p.setPose(anim.equals("crouch") && t < 1.4 ? EntityPose.CROUCHING : EntityPose.STANDING);

        float cx = ox + 210 * u;
        float lift = (1f - appear) * 12f * u;
        // голова следит за курсором (или оглядывается в анимации «look»), тело стоит ровно
        float look, pitch;
        if (anim.equals("look") && t < 3.0) { look = (float) Math.sin(t * 2.4) * 40f; pitch = 0f; }
        else {
            look = Math.max(-40f, Math.min(40f, (mouseX - cx) / u * 0.35f));
            pitch = Math.max(-20f, Math.min(20f, (mouseY - (oy + 110 * u)) / u * 0.15f));
        }
        float view = -0.55f + (float) Math.sin(now / 2.2e9) * 0.05f;
        float body = 180f + 28f;
        MenuDiorama.draw(c, p, ox + 124 * u, oy + 26 * u, ox + 294 * u, oy + 244 * u, cx + 4 * u, oy + 214 * u + lift, 52 * u,
            view, body, body - look, pitch);
    }

    // ================= СНАРЯЖЕНИЕ =================
    private static final String[] CATS = {"ВСЕ", "ВИНТОВКИ", "ПП", "ДРОБОВИКИ", "ПИСТОЛЕТЫ", "НОЖИ", "УТИЛИТЫ", "ПЕРЧАТКИ", "АГЕНТЫ"};
    private static final Set<String> RIFLES = Set.of("ak47", "galil", "m4a4", "m4a1s", "awp", "famas", "scar20");
    private static final Set<String> GEAR = Set.of("taser", "grenade_he", "grenade_flash", "grenade_smoke", "grenade_molotov");
    private static final Set<String> SMGS = Set.of("p90", "mp9", "mp7", "mac10");
    private static final Set<String> SHOTGUNS = Set.of("nova", "xm1014", "mag7", "sawedoff");
    private static final Set<String> PISTOLS = Set.of("glock18", "usp", "deagle", "fiveseven", "revolver");
    private int cat = 0, gridScroll = 0;
    private boolean onlyOwned = false, sortQuality = false;
    private long lastCardClick = 0;
    private String lastCardId = "";

    private static String slotOf(String item) {
        if (com.derevna230.comborush.lobby.AgentDefs.isAgent(item)) return com.derevna230.comborush.lobby.AgentDefs.slotOf(item);
        if (item.equals("knife_t") || item.equals("knife_ct") || item.equals("claw") || item.equals("bayonet") || item.equals("skeleton") || item.equals("gurkha")) return LoadoutDefs.KNIFE_SLOT;
        if (GunDefs.STD_GLOVES.contains(item)) return LoadoutDefs.GLOVES_SLOT;
        for (GunDefs.Skin s : GunDefs.SKINS) if (s.id().equals(item)) return GunDefs.isSpecialKnife(s.base()) ? LoadoutDefs.KNIFE_SLOT : s.base();
        return item;
    }

    private static int rarRank(String r) {
        return switch (r) { case "uncommon" -> 1; case "rare" -> 2; case "epic" -> 3; case "legendary" -> 4; case "mythic" -> 5; default -> 0; };
    }

    private List<String> gridItems() {
        List<String> l = new ArrayList<>();
        for (String slot : LoadoutDefs.slots()) {
            boolean ok = switch (cat) {
                case 1 -> RIFLES.contains(slot);
                case 2 -> SMGS.contains(slot);
                case 3 -> SHOTGUNS.contains(slot);
                case 4 -> PISTOLS.contains(slot);
                case 5 -> slot.equals(LoadoutDefs.KNIFE_SLOT);
                case 6 -> GEAR.contains(slot);
                case 7 -> slot.equals(LoadoutDefs.GLOVES_SLOT);
                case 8 -> slot.startsWith("agent_");
                default -> true;
            };
            if (!ok) continue;
            for (String id : LoadoutDefs.candidates(slot)) if (has(slot, id)) l.add(id);          // только то, что есть
        }
        if (sortQuality) l.sort(Comparator.comparingInt((String id) -> -rarRank(rarityOf(id))));
        return l;
    }

    private void select(String id) {
        selItem = id; selSlot = slotOf(id); featured = selSlot; rotY = 0.5f; spin = 0;
        HudModel.play(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.4f);
    }

    private void wear(String item) {
        String slot = slotOf(item);
        send("so_equip", slot + "|" + item);
        Map<String, String> lo = new HashMap<>(loadout);
        if (LoadoutDefs.candidates(slot).get(0).equals(item)) lo.remove(slot); else lo.put(slot, item);
        loadout = lo;
        HudModel.play(SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(), 1.2f, 0.5f);
    }

    private static String[] splitName(String full) {
        int i = full.indexOf(" | ");
        return i < 0 ? new String[]{full, ""} : new String[]{full.substring(0, i), full.substring(i + 3)};
    }

    private void drawEquipment(DrawContext c, long now, float dt) {
        // ---- фильтры
        float fx = 8;
        for (int i = 0; i < CATS.length; i++) {
            float w = textRenderer.getWidth(CATS[i]) * 0.52f + 7;
            boolean on = cat == i;
            float h = hoverAmt("cat" + i, hitOver(fx, 34, w, 14));
            c.fill((int) fx, 34, (int) (fx + w), 48, on ? 0xFF000000 | GOLD : 0xFF000000 | (0x1A1E28 + ((int) (h * 0x0C) * 0x010101)));
            text(c, CATS[i], fx + w / 2f, 39, 0.52f, on ? 0xFF10131A : 0xFFC4CCDB, 1);
            final int ci = i;
            hit(fx, 34, w, 14, () -> { cat = ci; gridScroll = 0; HudModel.play(SoundEvents.UI_BUTTON_CLICK.value(), 1.5f, 0.35f); });
            fx += w + 2;
        }
        String[] tg = {sortQuality ? "↓ РЕДК." : "↓ ТИП"};
        float tx = 294;
        for (int i = tg.length - 1; i >= 0; i--) {
            float w = textRenderer.getWidth(tg[i]) * 0.68f + 12;
            tx -= w;
            float h = hoverAmt("tg" + i, hitOver(tx, 34, w, 14));
            c.fill((int) tx, 34, (int) (tx + w), 48, 0xFF000000 | (0x1A1E28 + ((int) (h * 0x0C) * 0x010101)));
            text(c, tg[i], tx + w / 2f, 38, 0.68f, 0xFFB8C2D6, 1);
            final int ti = i;
            hit(tx, 34, w, 14, () -> { sortQuality = !sortQuality; gridScroll = 0;
                HudModel.play(SoundEvents.UI_BUTTON_CLICK.value(), 1.5f, 0.35f); });
            tx -= 3;
        }

        // ---- сетка предметов, как инвентарь CS
        final int cols = 5, cw = 54, ch = 58, gap = 4, gx = 8, gy = 52, vrows = 3;
        List<String> items = gridItems();
        int totalRows = (items.size() + cols - 1) / cols;
        gridScroll = Math.max(0, Math.min(gridScroll, Math.max(0, totalRows - vrows)));
        MatrixStack m = c.getMatrices();
        for (int r = 0; r < vrows; r++) for (int k = 0; k < cols; k++) {
            int idx = (r + gridScroll) * cols + k;
            float x = gx + k * (cw + gap), y = gy + r * (ch + gap);
            if (idx >= items.size()) { c.fill((int) x, (int) y, (int) (x + cw), (int) (y + ch), 0x300C1018); continue; }
            String id = items.get(idx), slot = slotOf(id);
            boolean owns = has(slot, id), sel = id.equals(selItem), worn = id.equals(equippedOf(slot));
            int rc = rarColor(rarityOf(id));
            float h = hoverAmt("card" + id, hitOver(x, y, cw, ch));
            c.fill((int) x - 1, (int) y - 1, (int) (x + cw) + 1, (int) (y + ch) + 1, sel ? 0xFF000000 | GOLD : 0xFF000000);
            c.fill((int) x, (int) y, (int) (x + cw), (int) (y + ch), 0xFF000000 | (0x151A24 + ((int) (h * 0x0A) * 0x010101)));
            c.fillGradient((int) x, (int) y, (int) (x + cw), (int) (y + ch - 14), 0x00000000, ((owns ? 0x40 : 0x18) << 24) | rc);
            c.fill((int) x, (int) (y + ch - 3), (int) (x + cw), (int) (y + ch), (owns ? 0xFF000000 : 0x77000000) | rc);
            m.push();
            m.translate(x + cw / 2f, y + 22, 0);
            m.scale(2.3f, 2.3f, 1f);
            drawIcon(c, id, -8, -8);
            m.pop();
            m.push();
            m.translate(0, 0, 200);                                         // поверх предмета (он рисуется на z = 150)
            String[] nm = splitName(itemName(id));
            if (nm[1].isEmpty()) {
                text(c, trim(nm[0], 17), x + cw / 2f, y + ch - 17, 0.6f, 0xFFE6EAF2, 1);
            } else {
                text(c, trim(nm[0], 18), x + cw / 2f, y + ch - 23, 0.56f, 0xFFB8C2D6, 1);
                text(c, trim(nm[1], 16), x + cw / 2f, y + ch - 14, 0.62f, 0xFF000000 | rc, 1);
            }
            if (!owns) { c.fill((int) x, (int) y, (int) (x + cw), (int) (y + ch - 3), 0x99000000); drawLock(c, x + cw / 2f, y + 20); }
            Integer cnt = counts.get(id);
            if (cnt != null && cnt > 1) {                                   // повторы: ×N
                String t = "×" + cnt;
                float tw = textRenderer.getWidth(t) * 0.62f + 5;
                c.fill((int) x + 2, (int) y + 2, (int) (x + 2 + tw), (int) y + 10, 0xCC000000);
                text(c, t, x + 4.5f, y + 3, 0.62f, 0xFFFFD27A, 0);
            }
            if (worn) { c.fill((int) x + cw - 12, (int) y + 2, (int) x + cw - 2, (int) y + 12, 0xFF2F7D4A); text(c, "✔", x + cw - 7, y + 3, 0.7f, 0xFFFFFFFF, 1); }
            m.pop();
            final String pick = id;
            hit(x, y, cw, ch, () -> {
                long nowMs = System.currentTimeMillis();
                if (pick.equals(lastCardId) && nowMs - lastCardClick < 350 && has(slotOf(pick), pick) && !pick.equals(equippedOf(slotOf(pick)))) wear(pick);
                else select(pick);
                lastCardId = pick; lastCardClick = nowMs;
            });
        }
        if (items.isEmpty()) text(c, "Здесь пока пусто", gx + 143, gy + 80, 0.9f, 0xFF6F7A90, 1);
        if (totalRows > vrows) {
            int trackH = vrows * (ch + gap) - gap;
            c.fill(296, gy, 298, gy + trackH, 0x33FFFFFF);
            int thumb = Math.max(14, trackH * vrows / totalRows);
            int ty = gy + (trackH - thumb) * gridScroll / Math.max(1, totalRows - vrows);
            c.fill(296, ty, 298, ty + thumb, 0xFF000000 | GOLD);
            text(c, "колесо — ещё", gx + 143, 241, 0.58f, 0xFF6F7A90, 1);
        }

        // ---- окно просмотра справа
        panel(c, VX, VY, VW, VH);
        String item = selItem == null ? equippedOf(selSlot) : selItem;
        String slot = slotOf(item);
        String rar = rarityOf(item);
        int rc = rarColor(rar);
        for (int i = 0; i < 12; i++) c.fill(VX + 1, VY + 100 + i * 3, VX + VW - 1, VY + 103 + i * 3, ((int) (2 + i) << 24) | rc);
        float pulse = 0.6f + 0.4f * (float) Math.sin(now / 6e8);
        for (int r = 46; r > 6; r -= 8) discClip(c, VX + VW / 2, VY + 66, r, ((int) (6 * pulse) << 24) | rc);
        if (!dragging) { rotY += spin; spin *= (float) Math.pow(0.04, dt); if (Math.abs(spin) < 0.002f) spin = 0; }
        String[] nm = splitName(itemName(item));
        text(c, nm[0].toUpperCase(Locale.ROOT), VX + VW / 2f, VY + 6, nm[0].length() > 12 ? 0.85f : 1.0f, 0xFFFFFFFF, 1);
        if (!nm[1].isEmpty()) text(c, nm[1], VX + VW / 2f, VY + 17, 0.85f, 0xFF000000 | rc, 1);
        text(c, rarLabel(rar), VX + VW / 2f, VY + (nm[1].isEmpty() ? 18 : 28), 0.66f, 0xFF000000 | rc, 1);
        boolean ownedNow = has(slot, item);
        boolean worn = item.equals(equippedOf(slot));
        text(c, worn ? "§a● надето" : ownedNow ? "§7в наличии" : "§cнет в инвентаре", VX + VW / 2f, VY + 150, 0.7f, 0xFFFFFFFF, 1);
        text(c, "ПКМ + мышь — вращать", VX + VW / 2f, VY + 163, 0.58f, 0xFF7C879D, 1);
        drawWeapon(c, item);
        if (!com.derevna230.comborush.lobby.AgentDefs.isAgent(item))
            button(c, "inspect", VX + 8, VY + 174, VW - 16, 14, "Осмотреть  [V]", 0x3A5A8C, this::inspect);
        else text(c, "§7Бесплатно · " + (com.derevna230.comborush.lobby.AgentDefs.CT.contains(item) ? "§9за спецназ" : "§6за террористов"),
            VX + VW / 2f, VY + 178, 0.6f, 0xFFFFFFFF, 1);
        boolean can = ownedNow && !worn;
        final String wearItem = item;
        button(c, "wear", VX + 8, VY + 192, VW - 16, 14, worn ? "Надето" : ownedNow ? "Надеть" : "Нет скина",
            can ? 0x2F7D4A : 0x3A4050, can ? () -> wear(wearItem) : null);
    }

    /** Ствол в окне просмотра, повёрнутый на rotY вокруг вертикальной оси */
    private void drawWeapon(DrawContext c, String item) {
        if (com.derevna230.comborush.lobby.AgentDefs.isAgent(item)) {          // агент — во весь рост, вращается
            PreviewPlayer p = dummy();
            if (p == null) return;
            com.derevna230.comborush.client.agent.AgentClient.preview = item;
            p.bodyYaw = 180; p.prevBodyYaw = 180; p.headYaw = 180; p.prevHeadYaw = 180; p.setYaw(180); p.setPitch(0);
            InventoryScreen.drawEntity(c, VX + VW / 2f, VY + 140, 50, new org.joml.Vector3f(),
                new Quaternionf().rotateZ((float) Math.PI).rotateX(-0.1f).rotateY(rotY), null, p);
            com.derevna230.comborush.client.agent.AgentClient.preview = null;
            return;
        }
        ItemStack st = stackOf(item);
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(VX + VW / 2f, VY + 66, 0);
        m.scale(5.2f, 5.2f, 5.2f);
        m.multiply(new Quaternionf().rotationY(rotY));
        m.translate(0, 0, -150);
        c.drawItem(st, -8, -8);
        m.pop();
    }

    // ================= ОБМЕН (контракт, как в CS2) =================
    private final Set<String> tsel = new LinkedHashSet<>();
    private int tScroll = 0;

    private static String nextRar(String r) {
        return switch (r) { case "uncommon" -> "rare"; case "rare" -> "epic"; case "epic" -> "legendary"; case "legendary" -> "mythic"; default -> ""; };
    }

    private void drawTrade(DrawContext c, long now) {
        if (clearSel) { tsel.clear(); clearSel = false; }
        List<TStack> all = new ArrayList<>(tstacks);
        all.sort(Comparator.<TStack>comparingInt(t -> -rarRank(rarityOf(t.id()))).thenComparing(TStack::id).thenComparingDouble(TStack::wf));
        Map<String, TStack> byLoc = new HashMap<>();
        for (TStack t : all) byLoc.put(t.loc(), t);
        tsel.removeIf(l -> !byLoc.containsKey(l));
        String selRar = null; boolean selSt = false;
        for (String l : tsel) { TStack t = byLoc.get(l); selRar = rarityOf(t.id()); selSt = t.st(); break; }
        int need = selRar != null && selRar.equals("legendary") ? 5 : 10;

        final int cols = 5, cw = 54, ch = 58, gap = 4, gx = 8, gy = 40, vrows = 3;
        int totalRows = (all.size() + cols - 1) / cols;
        tScroll = Math.max(0, Math.min(tScroll, Math.max(0, totalRows - vrows)));
        MatrixStack m = c.getMatrices();
        text(c, "Выберите скины одной редкости — обмен даст один скин редкостью выше", gx + 143, 241, 0.58f, 0xFF6F7A90, 1);
        for (int r = 0; r < vrows; r++) for (int k = 0; k < cols; k++) {
            int idx = (r + tScroll) * cols + k;
            float x = gx + k * (cw + gap), y = gy + r * (ch + gap);
            if (idx >= all.size()) { c.fill((int) x, (int) y, (int) (x + cw), (int) (y + ch), 0x300C1018); continue; }
            TStack t = all.get(idx);
            String rar = rarityOf(t.id());
            int rc = rarColor(rar);
            boolean on = tsel.contains(t.loc());
            boolean ok = on || (selRar == null || (rar.equals(selRar) && t.st() == selSt));
            float h = hoverAmt("tr" + t.loc(), hitOver(x, y, cw, ch));
            c.fill((int) x - 1, (int) y - 1, (int) (x + cw) + 1, (int) (y + ch) + 1, on ? 0xFF000000 | GOLD : 0xFF000000);
            c.fill((int) x, (int) y, (int) (x + cw), (int) (y + ch), 0xFF000000 | (0x151A24 + ((int) (h * 0x0A) * 0x010101)));
            c.fillGradient((int) x, (int) y, (int) (x + cw), (int) (y + ch - 14), 0x00000000, 0x40000000 | rc);
            c.fill((int) x, (int) (y + ch - 3), (int) (x + cw), (int) (y + ch), 0xFF000000 | rc);
            m.push();
            m.translate(x + cw / 2f, y + 24, 0);
            m.scale(2.3f, 2.3f, 1f);
            c.drawItem(stackOf(t.id()), -8, -8);
            m.pop();
            m.push();
            m.translate(0, 0, 200);
            String[] nm = splitName(itemName(t.id()));
            text(c, trim(nm[0], 18), x + cw / 2f, y + ch - 23, 0.56f, 0xFFB8C2D6, 1);
            if (!nm[1].isEmpty()) text(c, trim(nm[1], 16), x + cw / 2f, y + ch - 14, 0.62f, 0xFF000000 | rc, 1);
            text(c, com.derevna230.comborush.item.SkinData.condShort(t.wf()), x + 3, y + 3, 0.62f, 0xFFE6EAF2, 0);
            text(c, String.format(Locale.ROOT, "%.3f", t.wf()), x + 3, y + 11, 0.5f, 0xFF8A94A8, 0);
            if (t.st()) text(c, "ST™", x + cw - 3, y + 3, 0.62f, 0xFFFFB02E, 2);
            if (on) { c.fill((int) x + cw - 12, (int) y + ch - 18, (int) x + cw - 2, (int) y + ch - 8, 0xFF2F7D4A); text(c, "✔", x + cw - 7, y + ch - 17, 0.7f, 0xFFFFFFFF, 1); }
            if (!ok) c.fill((int) x, (int) y, (int) (x + cw), (int) (y + ch - 3), 0x99000000);
            m.pop();
            final TStack pick = t;
            final boolean can = ok;
            final int needF = need;
            hit(x, y, cw, ch, () -> {
                if (tsel.contains(pick.loc())) tsel.remove(pick.loc());
                else if (can && tsel.size() < needF) tsel.add(pick.loc());
                else { HudModel.play(SoundEvents.ENTITY_VILLAGER_NO, 1.3f, 0.3f); return; }
                HudModel.play(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.4f);
            });
        }
        if (all.isEmpty()) {
            text(c, "Нет скинов для обмена", gx + 143, gy + 70, 0.95f, 0xFF6F7A90, 1);
            text(c, "Нужны скины из кейса «Арсенал» (кроме ножей)", gx + 143, gy + 84, 0.68f, 0xFF59627A, 1);
        }
        if (totalRows > vrows) {
            int trackH = vrows * (ch + gap) - gap;
            c.fill(296, gy, 298, gy + trackH, 0x33FFFFFF);
            int thumb = Math.max(14, trackH * vrows / totalRows);
            int ty = gy + (trackH - thumb) * tScroll / Math.max(1, totalRows - vrows);
            c.fill(296, ty, 298, ty + thumb, 0xFF000000 | GOLD);
        }

        // ---- контракт справа
        panel(c, VX, VY, VW, VH);
        text(c, "КОНТРАКТ ОБМЕНА", VX + VW / 2f, VY + 6, 0.85f, 0xFF000000 | GOLD, 1);
        List<String> sel = new ArrayList<>(tsel);
        for (int i = 0; i < need; i++) {
            float x = VX + 7 + (i % 5) * 20, y = VY + 20 + (i / 5) * 20;
            boolean filled = i < sel.size();
            TStack t = filled ? byLoc.get(sel.get(i)) : null;
            int rc = t == null ? 0x303848 : rarColor(rarityOf(t.id()));
            c.fill((int) x - 1, (int) y - 1, (int) x + 19, (int) y + 19, 0xFF000000 | rc);
            c.fill((int) x, (int) y, (int) x + 18, (int) y + 18, 0xFF10131A);
            if (t != null) {
                c.drawItem(stackOf(t.id()), (int) x + 1, (int) y + 1);
                final String loc = sel.get(i);
                hit(x, y, 18, 18, () -> { tsel.remove(loc); HudModel.play(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.4f); });
            }
        }
        float ry = VY + (need > 5 ? 62 : 42);
        text(c, sel.size() + " / " + need, VX + VW / 2f, ry, 0.95f, sel.size() == need ? 0xFF55FF55 : 0xFFFFFFFF, 1);
        String[] rule = {"10 скинов одной редкости →", "1 скин редкостью выше.", "5 «Тайных» → скин ножа.", "Износ — средний из вложенных.", "StatTrak™ с обычными не смешать."};
        for (int i = 0; i < rule.length; i++) text(c, rule[i], VX + VW / 2f, ry + 14 + i * 9, 0.6f, 0xFF9AA5BB, 1);
        float by = VY + 138;
        if (selRar != null) {
            String nr = nextRar(selRar);
            text(c, "Результат:", VX + VW / 2f, by, 0.66f, 0xFF7C879D, 1);
            text(c, nr.equals("mythic") ? "Скин ножа" : rarLabel(nr), VX + VW / 2f, by + 9, 0.85f, 0xFF000000 | rarColor(nr), 1);
        } else text(c, "Положите скины в контракт", VX + VW / 2f, by + 4, 0.66f, 0xFF59627A, 1);
        boolean ready = sel.size() == need;
        final String locs = String.join(",", sel);
        button(c, "sign", VX + 8, VY + 168, VW - 16, 16, "Подписать контракт", ready ? 0x2F7D4A : 0x3A4050,
            ready ? () -> send("so_trade", locs) : null);
        button(c, "clr", VX + 8, VY + 188, VW - 16, 14, "Очистить", 0x3A4256, tsel.isEmpty() ? null : () -> tsel.clear());

        // ---- результат: поверх всего
        JsonObject res = tradedRes;
        if (res != null) {
            if (System.currentTimeMillis() - tradedAt > 7000) { tradedRes = null; return; }
            String rid = res.get("id").getAsString();
            int rc = rarColor(res.get("rar").getAsString());
            float wf = res.get("wf").getAsFloat();
            m.push();
            m.translate(0, 0, 400);
            c.fill(-300, -200, DW + 300, DH + 200, 0xB0000000);
            c.fill(115, 62, 305, 188, 0xFF000000 | rc);
            c.fill(116, 63, 304, 187, 0xFF0C1018);
            c.fillGradient(116, 63, 304, 187, 0x00000000, 0x40000000 | rc);
            text(c, "КОНТРАКТ ВЫПОЛНЕН", 210, 70, 0.95f, 0xFF000000 | GOLD, 1);
            m.push();
            m.translate(210, 112, 0);
            m.scale(3.6f, 3.6f, 1f);
            c.drawItem(stackOf(rid), -8, -8);
            m.pop();
            m.translate(0, 0, 200);
            String[] nm = splitName(res.get("name").getAsString());
            text(c, (res.get("st").getAsInt() == 1 ? "StatTrak™ " : "") + nm[0], 210, 144, 0.9f, 0xFFFFFFFF, 1);
            if (!nm[1].isEmpty()) text(c, nm[1], 210, 155, 0.9f, 0xFF000000 | rc, 1);
            text(c, com.derevna230.comborush.item.SkinData.condition(wf) + "  (" + com.derevna230.comborush.item.SkinData.floatText(wf) + ")", 210, 168, 0.68f, 0xFFB8C2D6, 1);
            text(c, "клик — закрыть", 210, 177, 0.55f, 0xFF6F7A90, 1);
            m.pop();
        }
    }

    // ================= РЫНОК =================
    private int mScrollL = 0, mScrollR = 0;
    private String mListLoc = null, mConfirmLoc = null;
    private int mListPrice = 0;
    private long mConfirmAt = 0;

    private static String skinLabel(String item, float wf, boolean st) {
        String n = (st ? "StatTrak™ " : "") + itemName(item);
        if (wf >= 0) n += " (" + com.derevna230.comborush.item.SkinData.condShort(wf) + ")";
        return n;
    }

    private void drawMarket(DrawContext c, long now) {
        text(c, "ТОРГОВАЯ ПЛОЩАДКА", 210, 36, 1.1f, 0xFFFFFFFF, 1);
        text(c, "Скины за эссенцию ✦ между игроками · комиссия 5% · игре можно продать сразу, но дешевле", 210, 48, 0.6f, 0xFF8A94A8, 1);
        long nowMs = System.currentTimeMillis();
        // ---- мои скины
        panel(c, 8, 58, 198, 184);
        List<MMine> mine = mMine;
        text(c, "МОИ СКИНЫ  " + mine.size(), 16, 63, 0.75f, 0xFF000000 | GOLD, 0);
        int rows = mListLoc != null ? 6 : 8;
        mScrollL = Math.max(0, Math.min(mScrollL, Math.max(0, mine.size() - rows)));
        if (mine.isEmpty()) text(c, "Скинов нет — они выпадают из кейсов", 107, 120, 0.65f, 0xFF6F7A90, 1);
        for (int r = 0; r < rows && r + mScrollL < mine.size(); r++) {
            MMine it = mine.get(r + mScrollL);
            float x = 12, y = 74 + r * 20;
            boolean sel = it.loc().equals(mListLoc);
            c.fill((int) x, (int) y, (int) x + 190, (int) y + 18, sel ? 0xFF2A3446 : 0xFF171C27);
            c.fill((int) x, (int) y + 17, (int) x + 190, (int) y + 18, 0xFF000000 | rarColor(rarityOf(it.item())));
            MatrixStack m = c.getMatrices();
            m.push(); m.translate(x + 9, y + 9, 0); m.scale(0.9f, 0.9f, 1f); c.drawItem(stackOf(it.item()), -8, -8); m.pop();
            m.push(); m.translate(0, 0, 200);
            text(c, trim(skinLabel(it.item(), it.wf(), it.st()), 26), x + 20, y + 3, 0.55f, 0xFFE6EAF2, 0);
            if (it.n() > 1) text(c, "×" + it.n(), x + 20, y + 10.5f, 0.5f, 0xFFFFD27A, 0);
            m.pop();
            boolean confirm = it.loc().equals(mConfirmLoc) && nowMs - mConfirmAt < 3000;
            final MMine fi = it;
            button(c, "msell" + it.loc(), x + 104, y + 3, 40, 12, confirm ? "ТОЧНО?" : "✦" + it.q(), confirm ? 0x9A3C3C : 0x3A4256, () -> {
                if (fi.loc().equals(mConfirmLoc) && System.currentTimeMillis() - mConfirmAt < 3000) { send("so_mkt_sell", fi.loc()); mConfirmLoc = null; }
                else { mConfirmLoc = fi.loc(); mConfirmAt = System.currentTimeMillis(); }
            });
            button(c, "mlist" + it.loc(), x + 147, y + 3, 40, 12, "ПРОДАТЬ", 0x2F7D4A, () -> {
                mListLoc = fi.loc(); mListPrice = Math.max(1, fi.q() * 2);
            });
        }
        text(c, "✦ — продать игре (2 клика) · «ПРОДАТЬ» — выставить на площадку", 107, 197 + (mListLoc != null ? -40 : 0), 0.5f, 0xFF6F7A90, 1);
        if (mListLoc != null) {
            MMine it = null;
            for (MMine x : mine) if (x.loc().equals(mListLoc)) it = x;
            if (it == null) mListLoc = null;
            else {
                float y = 200;
                c.fill(12, (int) y - 2, 202, (int) y + 40, 0xFF1C2433);
                text(c, "Цена для «" + trim(itemName(it.item()), 18) + "»:", 16, y + 1, 0.6f, 0xFFB8C2D6, 0);
                text(c, "✦ " + mListPrice, 196, y, 0.85f, 0xFFD7A8FF, 2);
                String[] lb = {"−100", "−10", "+10", "+100", "×2"};
                int[] dv = {-100, -10, 10, 100, 0};
                for (int i = 0; i < lb.length; i++) {
                    final int d = dv[i];
                    button(c, "mp" + i, 16 + i * 31, y + 12, 29, 11, lb[i], 0x3A4256, () ->
                        mListPrice = Math.max(1, Math.min(com.derevna230.comborush.lobby.SkinMarket.MAX_PRICE, d == 0 ? mListPrice * 2 : mListPrice + d)));
                }
                text(c, "вы получите ✦" + Math.max(1, Math.round(mListPrice * 0.95)), 16, y + 28, 0.55f, 0xFF8A94A8, 0);
                final String loc = it.loc();
                button(c, "mok", 120, y + 25, 48, 12, "ВЫСТАВИТЬ", 0x2F7D4A, () -> { send("so_mkt_list", loc + "|" + mListPrice); mListLoc = null; });
                button(c, "mno", 171, y + 25, 27, 12, "✕", 0x6A3030, () -> mListLoc = null);
            }
        }
        // ---- лоты
        panel(c, 212, 58, 200, 184);
        List<MLot> lots = mLots;
        text(c, "ЛОТЫ  " + lots.size(), 220, 63, 0.75f, 0xFF000000 | GOLD, 0);
        int rr = 8;
        mScrollR = Math.max(0, Math.min(mScrollR, Math.max(0, lots.size() - rr)));
        if (lots.isEmpty()) text(c, "Пока никто ничего не выставил", 312, 120, 0.65f, 0xFF6F7A90, 1);
        for (int r = 0; r < rr && r + mScrollR < lots.size(); r++) {
            MLot it = lots.get(r + mScrollR);
            float x = 216, y = 74 + r * 20;
            c.fill((int) x, (int) y, (int) x + 192, (int) y + 18, it.mine() ? 0xFF1E2A22 : 0xFF171C27);
            c.fill((int) x, (int) y + 17, (int) x + 192, (int) y + 18, 0xFF000000 | rarColor(rarityOf(it.item())));
            MatrixStack m = c.getMatrices();
            m.push(); m.translate(x + 9, y + 9, 0); m.scale(0.9f, 0.9f, 1f); c.drawItem(stackOf(it.item()), -8, -8); m.pop();
            m.push(); m.translate(0, 0, 200);
            text(c, trim(skinLabel(it.item(), it.wf(), it.st()), 24), x + 20, y + 3, 0.55f, 0xFFE6EAF2, 0);
            text(c, (it.mine() ? "§aваш лот" : "§7" + trim(it.seller(), 14)), x + 20, y + 10.5f, 0.5f, 0xFFFFFFFF, 0);
            m.pop();
            text(c, "✦" + it.price(), x + 146, y + 5, 0.62f, 0xFFD7A8FF, 2);
            final int id = it.id();
            if (it.mine()) button(c, "mc" + id, x + 150, y + 3, 38, 12, "СНЯТЬ", 0x6A3030, () -> send("so_mkt_cancel", String.valueOf(id)));
            else button(c, "mb" + id, x + 150, y + 3, 38, 12, "КУПИТЬ", essence >= it.price() ? 0x2F7D4A : 0x2A3242,
                essence >= it.price() ? () -> send("so_mkt_buy", String.valueOf(id)) : null);
        }
    }

    // ================= КАРТЫ / НАСТРОЙКИ =================
    private static final Identifier MAP_DUST = Identifier.of(ComboRush.MOD_ID, "textures/gui/map_dust.png");
    private static final Identifier MAP_MEDINA = Identifier.of(ComboRush.MOD_ID, "textures/gui/map_medina.png");
    /** Выбранная карта матча (кнопки «Играть» на главной) */
    private static String selMap = "dust";
    private static String mapTitle(String id) { return id.equals("medina") ? "Медина" : "Дюны"; }

    private void drawMaps(DrawContext c, long now) {
        text(c, "КАРТЫ", 210, 38, 1.2f, 0xFFFFFFFF, 1);
        text(c, "Соревновательный режим 5×5 · клик по карте — выбрать для «Играть»", 210, 52, 0.68f, 0xFF8A94A8, 1);
        String[][] info = {
            {"dust", "ДЮНЫ", "Классика: 120×88 блоков", "A приподнята (балкон, яма),", "B утоплена (склад, мостик)", "Лонг, Мид, Склад, Тоннели"},
            {"medina", "МЕДИНА", "Старый город, уровни −4…+8", "Мост над Мидом, Крыши,", "Тоннели к Б, Колодец,", "Балкон А, Склад Б в 2 этажа"}};
        for (int i = 0; i < 2; i++) {
            String id = info[i][0];
            float x = 14 + i * 202, y = 64, w = 194, h = 176;
            boolean sel = selMap.equals(id);
            boolean over = hitOver(x, y, w, 150);
            panel(c, (int) x, (int) y, (int) w, (int) h);
            if (sel) {
                c.fill((int) x - 2, (int) y - 2, (int) (x + w) + 2, (int) y, 0xFF000000 | GOLD);
                c.fill((int) x - 2, (int) (y + h), (int) (x + w) + 2, (int) (y + h) + 2, 0xFF000000 | GOLD);
                c.fill((int) x - 2, (int) y, (int) x, (int) (y + h), 0xFF000000 | GOLD);
                c.fill((int) (x + w), (int) y, (int) (x + w) + 2, (int) (y + h), 0xFF000000 | GOLD);
            }
            c.fill((int) x + 8, (int) y + 7, (int) (x + w) - 8, (int) y + 133, over ? 0xFF3A4458 : 0xFF000000);
            c.drawTexture(i == 0 ? MAP_DUST : MAP_MEDINA, (int) x + 9, (int) y + 8, (int) w - 18, 124, 0f, 0f, 256, i == 0 ? 189 : 179, 256, i == 0 ? 189 : 179);
            text(c, info[i][1], x + 9, y + 137, 0.95f, sel ? 0xFF000000 | GOLD : 0xFFFFFFFF, 0);
            text(c, sel ? "§aвыбрана" : "§7выбрать", x + w - 9, y + 138, 0.62f, 0xFFFFFFFF, 2);
            for (int k = 2; k < 6; k++) text(c, info[i][k], x + 9, y + 148 + (k - 2) * 7f, 0.55f, 0xFFB8C2D6, 0);
            final String fid = id;
            hit(x, y, w, 134, () -> { selMap = fid; HudModel.play(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.5f); });
            button(c, "map" + i + "T", x + w - 82, y + h - 20, 36, 14, "T", 0xB5651D, () -> { send("so_play", "t|" + fid); close(); });
            button(c, "map" + i + "CT", x + w - 42, y + h - 20, 36, 14, "CT", 0x2F5FA8, () -> { send("so_play", "ct|" + fid); close(); });
        }
    }

    /** Замок из прямоугольников (в шрифте Minecraft нет значка) */
    private void drawLock(DrawContext c, float cx, float cy) {
        int x = (int) cx, y = (int) cy;
        c.fill(x - 4, y - 7, x - 2, y - 2, 0xFF8A94A8);
        c.fill(x + 2, y - 7, x + 4, y - 2, 0xFF8A94A8);
        c.fill(x - 4, y - 8, x + 4, y - 6, 0xFF8A94A8);
        c.fill(x - 6, y - 2, x + 6, y + 6, 0xFF8A94A8);
        c.fill(x - 1, y + 1, x + 1, y + 4, 0xFF1A1F2B);
    }

    private void drawSettings(DrawContext c) {
        text(c, "НАСТРОЙКИ", 210, 38, 1.2f, 0xFFFFFFFF, 1);
        panel(c, 90, 76, 240, 90);
        text(c, "Пока пусто", 210, 112, 1.0f, 0xFF8A94A8, 1);
        text(c, "Здесь появятся параметры матча и интерфейса.", 210, 128, 0.72f, 0xFF5A6478, 1);
    }

    // ================= элементы =================
    private float hoverAmt(String id, boolean over) {
        float cur = hov.getOrDefault(id, 0f);
        cur += ((over ? 1f : 0f) - cur) * 0.35f;
        hov.put(id, cur);
        return cur;
    }

    private boolean hitOver(float x, float y, float w, float h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    private void hit(float x, float y, float w, float h, Runnable click) {
        Hit h2 = new Hit();
        h2.x = x; h2.y = y; h2.w = w; h2.h = h; h2.click = click;
        hits.add(h2);
    }

    private void panel(DrawContext c, int x, int y, int w, int h) {
        c.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        c.fill(x, y, x + w, y + h, 0xCC0C1018);
        c.fill(x, y, x + w, y + 1, 0x28FFFFFF);
    }

    private void button(DrawContext c, String id, float x, float y, float w, float h, String label, int rgb, Runnable click) {
        boolean on = click != null;
        float ha = on ? hoverAmt("b" + id, hitOver(x, y, w, h)) : 0f;
        int top = on ? lighten(rgb, 18 + (int) (28 * ha)) : rgb, bot = on ? rgb : darken(rgb);
        c.fill((int) x - 1, (int) y - 1, (int) (x + w) + 1, (int) (y + h) + 1, 0xFF000000);
        c.fillGradient((int) x, (int) y, (int) (x + w), (int) (y + h), 0xFF000000 | top, 0xFF000000 | bot);
        c.fill((int) x, (int) y, (int) (x + w), (int) y + 1, on ? 0x55FFFFFF : 0x22FFFFFF);
        text(c, label, x + w / 2f, y + (h - 7f) / 2f, h < 14 ? 0.78f : 0.82f, on ? 0xFFFFFFFF : 0xFF8A92A3, 1);
        hit(x, y, w, h, on ? () -> { HudModel.play(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.55f); click.run(); }
            : () -> HudModel.play(SoundEvents.ENTITY_VILLAGER_NO, 1.2f, 0.3f));
    }

    private static int lighten(int rgb, int d) {
        int r = Math.min(255, ((rgb >> 16) & 255) + d), g = Math.min(255, ((rgb >> 8) & 255) + d), b = Math.min(255, (rgb & 255) + d);
        return (r << 16) | (g << 8) | b;
    }

    private static int darken(int rgb) {
        int r = ((rgb >> 16) & 255) * 3 / 4, g = ((rgb >> 8) & 255) * 3 / 4, b = (rgb & 255) * 3 / 4;
        return (r << 16) | (g << 8) | b;
    }

    /** Круг, обрезанный окном просмотра */
    private void discClip(DrawContext c, int cx, int cy, int r, int argb) {
        for (int dy = -r; dy <= r; dy += 2) {
            int hw = (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
            int y0 = cy + dy, y1 = y0 + 2;
            if (y1 <= VY || y0 >= VY + VH) continue;
            int x0 = Math.max(VX, cx - hw), x1 = Math.min(VX + VW, cx + hw);
            if (x1 > x0) c.fill(x0, Math.max(VY, y0), x1, Math.min(VY + VH, y1), argb);
        }
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
}
