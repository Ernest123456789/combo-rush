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
    private static final String[] TABS = {"ГЛАВНАЯ", "СНАРЯЖЕНИЕ", "КАРТЫ", "НАСТРОЙКИ"};
    private static final int GOLD = 0xE8B33A, DARK = 0x10131A;

    // ---- состояние с сервера
    private record Member(String name, boolean leader, boolean me) {}
    private record Online(String name, boolean busy) {}
    private static volatile List<Member> members = List.of();
    private static volatile List<Online> online = List.of();
    private static volatile String inviteFrom = null;
    private static volatile Map<String, String> loadout = Map.of();
    private static volatile Set<String> owned = Set.of();
    private static volatile int essence = 0, partyMax = 5;

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
        Set<String> ow = new HashSet<>();
        if (o.has("owned")) for (JsonElement e : o.getAsJsonArray("owned")) ow.add(e.getAsString());
        owned = ow;
        essence = o.has("essence") ? o.get("essence").getAsInt() : 0;
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
        return g == null ? slot : g.name();
    }

    private static String equippedOf(String slot) {
        String v = loadout.get(slot);
        return v == null ? LoadoutDefs.candidates(slot).get(0) : v;
    }

    private static boolean has(String slot, String item) { return LoadoutDefs.isStandard(slot, item) || owned.contains(item); }

    private static String rarityOf(String item) {
        for (GunDefs.Skin s : GunDefs.SKINS) if (s.id().equals(item)) return s.rarity();
        return item.equals("claw") ? "mythic" : "common";
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
            ItemStack st = new ItemStack(it);
            long gid = 4_000_000_000L + Math.abs((long) k.hashCode());
            NbtComponent.set(DataComponentTypes.CUSTOM_DATA, st, nbt -> nbt.putLong("GeckoLibID", gid));
            return st;
        });
    }

    private static long gid(ItemStack st) {
        NbtComponent c = st.get(DataComponentTypes.CUSTOM_DATA);
        return c == null ? -1L : c.copyNbt().getLong("GeckoLibID");
    }

    private static String itemName(String id) { return stackOf(id).getName().getString(); }

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
        if (tab == 0 && px > 296) onlineScroll = Math.max(0, Math.min(Math.max(0, online.size() - 5), onlineScroll + d));
        return true;
    }

    private boolean inViewer(float px, float py) { return px >= VX && px < VX + VW && py >= VY && py < VY + VH; }

    private void inspect() {
        if (tab == 1 && selItem != null) play(stackOf(selItem), "inspect");
        else if (tab == 0) { play(stackOf(equippedOf(featured)), "inspect"); anim = "inspect"; animStart = System.nanoTime(); }
        HudModel.play(SoundEvents.ITEM_ARMOR_EQUIP_IRON.value(), 1.2f, 0.4f);
    }

    // ================= отрисовка =================
    private static final int VX = 126, VY = 34, VW = 286, VH = 138;      // окно просмотра ствола

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
            case 2 -> drawMaps(c, now);
            default -> drawSettings(c);
        }
        drawTopBar(c, now);
        drawInvite(c, now);
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
        c.fill(-200, 0, DW + 200, 26, 0xF0090C12);
        c.fill(-200, 26, DW + 200, 27, 0xFF000000 | GOLD);
        c.fill(-200, 27, DW + 200, 28, 0x30000000 | GOLD);
        // выход
        button(c, "exit", 6, 5, 62, 16, "◀  ЛОББИ", 0x3A4256, () -> { send("so_exit", ""); close(); });
        // вкладки
        int tw = 74, x0 = (DW - tw * TABS.length) / 2;
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            float x = x0 + i * tw;
            boolean sel = tab == i;
            float h = hoverAmt("tab" + i, hitOver(x, 0, tw, 26));
            if (sel || h > 0.02f) c.fill((int) x, 0, (int) (x + tw), 26, ((int) ((sel ? 34 : 20 * h)) << 24) | 0xFFFFFF);
            text(c, TABS[i], x + tw / 2f, 9, 0.9f, sel ? 0xFFFFFFFF : 0xFFA9B4C9, 1);
            if (sel) c.fill((int) x + 6, 24, (int) (x + tw - 6), 27, 0xFF000000 | GOLD);
            hit(x, 0, tw, 26, () -> { tab = idx; HudModel.play(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.5f);
                if (idx == 1) { selItem = equippedOf(selSlot); } });
        }
        // эссенция
        text(c, "✦ " + essence, DW - 8, 9, 0.95f, 0xFFD7A8FF, 2);
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
        String[] info = {"Две команды, один раунд.", "Выберите снаряжение, соберите", "группу и ждите карты —", "матчи появятся позже."};
        for (int i = 0; i < info.length; i++) text(c, info[i], 14, 60 + i * 10, 0.72f, 0xFFB8C2D6, 0);
        button(c, "play", 14, 106, 100, 20, "ИГРАТЬ  (скоро)", 0x4A4F5C, null);
        text(c, "Карта: не выбрана", 64, 134, 0.72f, 0xFF8A94A8, 1);
        panel(c, 8, 160, 112, 80);
        text(c, "КЕЙС «АРСЕНАЛ»", 64, 168, 0.85f, 0xFFFFB02E, 1);
        String[] cs = {"Выпадает после матчей.", "Ключ стоит 250 эссенции:", "его покупают при открытии", "кейса из инвентаря."};
        for (int i = 0; i < cs.length; i++) text(c, cs[i], 14, 182 + i * 10, 0.7f, 0xFFB8C2D6, 0);
        text(c, "§7Скин в руке: §f" + itemName(equippedOf(featured)), 14, 226, 0.7f, 0xFFFFFFFF, 0);

        drawParty(c, now);
        // подпись под моделью
        text(c, "Скины и осмотр оружия — вкладка «Снаряжение»", 210, 234, 0.62f, 0xFF6F7A90, 1);
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
            String[] pool = {"inspect", "draw", "look", "swing", "crouch", "inspect"};
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

        float cx = ox + 210 * u, top = oy + 32 * u, bottom = oy + 232 * u;
        float lookX, lookY;
        if (anim.equals("look") && t < 3.0) { lookX = cx + (float) Math.sin(t * 2.4) * 170 * u; lookY = top + 70 * u; }
        else { lookX = (float) (cx + (mouseX - cx) * 0.5); lookY = (float) (top + 70 * u + (mouseY - top - 70 * u) * 0.4); }
        int half = (int) (70 * u);
        // подиум-свечение
        int fy = (int) (bottom - 14 * u);
        for (int i = 0; i < 6; i++) {
            int rw = (int) ((62 - i * 8) * u);
            c.fill((int) cx - rw, fy - i, (int) cx + rw, fy - i + 1, ((int) (22 + i * 6) << 24) | 0x5A7CBF);
        }
        float lift = (1f - appear) * 12f;
        InventoryScreen.drawEntity(c, (int) cx - half, (int) (top + lift), (int) cx + half, (int) bottom, (int) (78 * u), 0.0625f, lookX, lookY, p);
    }

    // ================= СНАРЯЖЕНИЕ =================
    private void drawEquipment(DrawContext c, long now, float dt) {
        // список слотов
        panel(c, 8, 34, 112, 206);
        List<String> slots = LoadoutDefs.slots();
        int rows = 11, rh = 17;
        slotScroll = Math.max(0, Math.min(slotScroll, Math.max(0, slots.size() - rows)));
        for (int i = 0; i < Math.min(rows, slots.size()); i++) {
            String slot = slots.get(i + slotScroll);
            String eq = equippedOf(slot);
            boolean sel = slot.equals(selSlot);
            float y = 38 + i * rh;
            float h = hoverAmt("slot" + slot, hitOver(10, y, 108, rh - 1));
            c.fill(10, (int) y, 118, (int) (y + rh - 1), sel ? 0x55E8B33A : ((int) (28 * h) << 24) | 0xFFFFFF);
            if (sel) c.fill(10, (int) y, 12, (int) (y + rh - 1), 0xFF000000 | GOLD);
            text(c, slotLabel(slot), 16, y + 2, 0.82f, sel ? 0xFFFFFFFF : 0xFFC4CCDB, 0);
            boolean std = LoadoutDefs.isStandard(slot, eq);
            text(c, std ? "стандартный" : trim(itemName(eq), 18), 16, y + 10, 0.58f, std ? 0xFF6F7A90 : 0xFF000000 | rarColor(rarityOf(eq)), 0);
            final String s = slot;
            hit(10, y, 108, rh - 1, () -> { selSlot = s; selItem = equippedOf(s); featured = s; rotY = 0.5f; spin = 0;
                HudModel.play(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.4f); });
        }
        if (slots.size() > rows) text(c, "колесо — ещё", 64, 229, 0.6f, 0xFF6F7A90, 1);

        // окно просмотра
        panel(c, VX, VY, VW, VH);
        String item = selItem == null ? equippedOf(selSlot) : selItem;
        String rar = rarityOf(item);
        int rc = rarColor(rar);
        for (int i = 0; i < 10; i++) c.fill(VX + 1, VY + VH - 24 + i * 2, VX + VW - 1, VY + VH - 22 + i * 2, ((int) (3 + i) << 24) | rc);
        // свечение по редкости
        float pulse = 0.6f + 0.4f * (float) Math.sin(now / 6e8);
        for (int r = 56; r > 8; r -= 8) discClip(c, VX + VW / 2, VY + 58, r, ((int) (6 * pulse) << 24) | rc);
        if (!dragging) { rotY += spin; spin *= (float) Math.pow(0.04, dt); if (Math.abs(spin) < 0.002f) spin = 0; }
        text(c, itemName(item).toUpperCase(Locale.ROOT), VX + VW / 2f, VY + 6, 1.1f, 0xFFFFFFFF, 1);
        text(c, rarLabel(rar), VX + VW / 2f, VY + 19, 0.78f, 0xFF000000 | rc, 1);
        boolean ownedNow = has(selSlot, item);
        boolean worn = item.equals(equippedOf(selSlot));
        text(c, worn ? "§a● надето" : ownedNow ? "§7в наличии" : "§cнет в инвентаре", VX + VW - 8, VY + 8, 0.72f, 0xFFFFFFFF, 2);
        text(c, "ПКМ + мышь — вращать", VX + 8, VY + VH - 11, 0.62f, 0xFF7C879D, 0);
        drawWeapon(c, item);

        // варианты слота
        List<String> cands = LoadoutDefs.candidates(selSlot);
        int n = cands.size(), cw = 30, gap = 3;
        float x0 = VX + (VW - (n * cw + (n - 1) * gap)) / 2f;
        for (int i = 0; i < n; i++) {
            String id = cands.get(i);
            float x = x0 + i * (cw + gap), y = VY + VH + 6;
            boolean has = has(selSlot, id), cur = id.equals(item);
            int col = rarColor(rarityOf(id));
            float h = hoverAmt("chip" + id, hitOver(x, y, cw, 30));
            c.fill((int) x - 1, (int) y - 1, (int) (x + cw) + 1, (int) (y + 31), cur ? 0xFF000000 | col : 0xFF000000);
            c.fill((int) x, (int) y, (int) (x + cw), (int) (y + 30), ((int) (0xC0) << 24) | (0x10131A + ((int) (h * 0x08) * 0x010101)));
            c.fill((int) x, (int) (y + 28), (int) (x + cw), (int) (y + 30), (has ? 0xFF000000 : 0x66000000) | col);
            MatrixStack m = c.getMatrices();
            m.push();
            m.translate(x + cw / 2f - 8, y + 6, 0);
            c.drawItem(stackOf(id), 0, 0);
            m.pop();
            m.push();
            m.translate(0, 0, 200);                                         // поверх предмета (он рисуется на z = 150)
            if (!has) { c.fill((int) x, (int) y, (int) (x + cw), (int) (y + 30), 0xAA000000); drawLock(c, x + cw / 2f, y + 15); }
            if (equippedOf(selSlot).equals(id)) text(c, "●", x + cw - 6, y + 1, 0.7f, 0xFF55FF55, 1);
            m.pop();
            final String pick = id;
            hit(x, y, cw, 30, () -> { selItem = pick; HudModel.play(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.4f); });
        }
        // кнопки
        float by = VY + VH + 42;
        button(c, "inspect", VX + 20, by, 118, 18, "Осмотреть  [V]", 0x3A5A8C, this::inspect);
        boolean can = ownedNow && !worn;
        button(c, "wear", VX + VW - 138, by, 118, 18, worn ? "Надето" : ownedNow ? "Надеть" : "Нет скина",
            can ? 0x2F7D4A : 0x3A4050, can ? () -> { send("so_equip", selSlot + "|" + item);
                Map<String, String> lo = new HashMap<>(loadout);
                if (LoadoutDefs.candidates(selSlot).get(0).equals(item)) lo.remove(selSlot); else lo.put(selSlot, item);
                loadout = lo; HudModel.play(SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(), 1.2f, 0.5f); } : null);
    }

    /** Ствол в окне просмотра, повёрнутый на rotY вокруг вертикальной оси */
    private void drawWeapon(DrawContext c, String item) {
        ItemStack st = stackOf(item);
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(VX + VW / 2f, VY + 62, 0);
        m.scale(7.5f, 7.5f, 7.5f);
        m.multiply(new Quaternionf().rotationY(rotY + 0.0f));
        m.translate(0, 0, -150);
        c.drawItem(st, -8, -8);
        m.pop();
    }

    // ================= КАРТЫ / НАСТРОЙКИ =================
    private void drawMaps(DrawContext c, long now) {
        text(c, "КАРТЫ", 210, 38, 1.2f, 0xFFFFFFFF, 1);
        text(c, "Выбор карты для матча. Карты добавим позже.", 210, 54, 0.78f, 0xFF8A94A8, 1);
        String[] names = {"Карта 1", "Карта 2", "Карта 3", "Карта 4"};
        for (int i = 0; i < 4; i++) {
            float x = 24 + i * 94, y = 78, w = 86, h = 110;
            panel(c, (int) x, (int) y, (int) w, (int) h);
            c.fillGradient((int) x + 4, (int) y + 4, (int) (x + w - 4), (int) (y + 66), 0xFF1A2030, 0xFF0C0F16);
            for (int k = 0; k < 4; k++) c.fill((int) x + 4, (int) y + 14 + k * 12, (int) (x + w - 4), (int) y + 15 + k * 12, 0x0CFFFFFF);
            drawLock(c, x + w / 2, y + 36);
            text(c, names[i], x + w / 2, y + 74, 0.9f, 0xFF8A94A8, 1);
            text(c, "скоро", x + w / 2, y + 88, 0.72f, 0xFF5A6478, 1);
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
