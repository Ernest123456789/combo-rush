package com.derevna230.comborush.client;

import com.derevna230.comborush.hud.ShopActionPayload;
import com.derevna230.comborush.item.CRGunItem;
import com.derevna230.comborush.item.CRKnifeItem;
import com.derevna230.comborush.item.GunDefs;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Управление стволами «Арсенала» на клиенте: ЛКМ — выстрел (очередь или одиночный), ПКМ — прицел (AWP) или глушитель,
 * R — перезарядка, V — осмотр. Здесь же отдача камеры, прицельная сетка, счётчик патронов и отметка попадания.
 * Анимации предметов проигрываются локально: серверные триггеры GeckoLib ходят по имени класса и не находят нужный ствол.
 */
public final class WeaponClient {
    private WeaponClient() {}

    private static int ticks = 0;
    private static double nextFire = 0;
    private static boolean prevAttack = false, prevUse = false;
    private static int scopeLevel = 0;                 // 0 — нет, 1 — 3×, 2 — 8×
    private static int reloadUntil = 0;
    private static long lastWeaponId = -2;
    private static int predAmmo = -1, lastStackAmmo = -1;
    private static int shotIdx = 0, lastShotTick = -100;
    private static int hitTicks = 0;
    private static boolean hitHead = false;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(WeaponClient::tick);
        HudRenderCallback.EVENT.register(WeaponClient::hud);
    }

    // ---------------- доступ для миксина камеры ----------------
    /** Во сколько раз сузить поле зрения (1 — без прицела) */
    public static double fovMult() {
        return scopeLevel == 1 ? 0.30 : scopeLevel == 2 ? 0.11 : 1.0;
    }

    public static boolean scoped() { return scopeLevel > 0; }

    public static void onHit(JsonObject o) {
        hitTicks = 8;
        hitHead = o.has("h") && o.get("h").getAsInt() == 1;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.playSoundToPlayer(hitHead ? SoundEvents.ENTITY_PLAYER_LEVELUP : SoundEvents.ENTITY_ARROW_HIT_PLAYER,
            net.minecraft.sound.SoundCategory.PLAYERS, hitHead ? 0.35f : 0.5f, hitHead ? 2.0f : 1.4f);
    }

    // ---------------- помощники ----------------
    private static void send(String action, int a) {
        if (ClientPlayNetworking.canSend(ShopActionPayload.ID)) ClientPlayNetworking.send(new ShopActionPayload(action, a, 0, ""));
    }

    private static long idOf(ItemStack st) {
        return software.bernie.geckolib.animatable.GeoItem.getId(st);       // Long.MAX_VALUE — сервер ещё не назначил номер
    }

    private static boolean silencerOff(ItemStack st) {
        NbtComponent c = st.get(DataComponentTypes.CUSTOM_DATA);
        return c != null && c.copyNbt().getBoolean("nosil");
    }

    private static CRGunItem heldGun(MinecraftClient mc) {
        if (mc.player == null) return null;
        return mc.player.getMainHandStack().getItem() instanceof CRGunItem g && g.modern() ? g : null;
    }

    private static boolean reloading() { return ticks < reloadUntil; }

    // ---------------- клавиши (вызываются из ComboRushClient) ----------------
    public static void keyReload() {
        MinecraftClient mc = MinecraftClient.getInstance();
        CRGunItem g = heldGun(mc);
        if (g == null || reloading()) return;
        ItemStack st = mc.player.getMainHandStack();
        int ammo = predAmmo >= 0 ? predAmmo : g.ammo(st);
        if (ammo >= g.def().mag()) return;
        startReload(g, st);
    }

    private static void startReload(CRGunItem g, ItemStack st) {
        reloadUntil = ticks + g.def().reloadTicks();
        scopeLevel = 0;
        g.playLocal(st, "reload");
        send("gun_reload", 0);
    }

    public static void keyInspect() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.currentScreen != null) return;
        ItemStack st = mc.player.getMainHandStack();
        boolean ok = false;
        if (st.getItem() instanceof CRGunItem g && g.modern()) { if (!reloading()) { g.playLocal(st, "inspect"); ok = true; } }
        else if (st.getItem() instanceof CRKnifeItem k) { k.playLocal(st, "inspect"); ok = true; }
        // как в CS: при осмотре показываем износ, паттерн и счётчик StatTrak™
        String info = ok ? com.derevna230.comborush.item.SkinData.inspectLine(st) : "";
        if (!info.isEmpty()) mc.inGameHud.setOverlayMessage(net.minecraft.text.Text.literal("§f" + st.getName().getString() + "  " + info), false);
    }

    private static void secondary(CRGunItem g, ItemStack st) {
        String kind = g.secondary();
        if (kind.equals("scope")) {
            scopeLevel = (scopeLevel + 1) % 3;
            MinecraftClient.getInstance().player.playSoundToPlayer(SoundEvents.ITEM_SPYGLASS_USE, net.minecraft.sound.SoundCategory.PLAYERS, 0.6f, scopeLevel == 0 ? 1.3f : 1.0f);
        } else if (kind.equals("silencer") && !reloading()) {
            g.playLocal(st, silencerOff(st) ? "sil_on" : "sil_off");
            send("gun_silencer", 0);
        }
    }

    // ---------------- тик ----------------
    private static void tick(MinecraftClient mc) {
        ticks++;
        ClientPlayerEntity p = mc.player;
        if (p == null) { lastWeaponId = -2; scopeLevel = 0; return; }
        if (hitTicks > 0) hitTicks--;
        ItemStack st = p.getMainHandStack();
        boolean weapon = (st.getItem() instanceof CRGunItem g0 && g0.modern()) || st.getItem() instanceof CRKnifeItem;
        if (!weapon) { lastWeaponId = -2; scopeLevel = 0; prevAttack = mc.options.attackKey.isPressed(); prevUse = mc.options.useKey.isPressed(); return; }

        // доставание: номер GeckoLib сменился — в руке другой ствол
        long id = idOf(st);
        if (id != Long.MAX_VALUE && id != lastWeaponId) {
            lastWeaponId = id;
            scopeLevel = 0;
            reloadUntil = 0;
            predAmmo = -1; lastStackAmmo = -1;
            if (st.getItem() instanceof CRGunItem g) g.playLocal(st, "draw");
            else if (st.getItem() instanceof CRKnifeItem k) k.playLocal(st, "draw");
        }
        boolean attack = mc.options.attackKey.isPressed() && mc.currentScreen == null && !p.isSpectator();
        boolean use = mc.options.useKey.isPressed() && mc.currentScreen == null && !p.isSpectator();
        boolean attackEdge = attack && !prevAttack, useEdge = use && !prevUse;
        prevAttack = mc.options.attackKey.isPressed(); prevUse = mc.options.useKey.isPressed();
        if (mc.currentScreen != null) scopeLevel = 0;

        if (!(st.getItem() instanceof CRGunItem g) || !g.modern()) return;
        GunDefs.Gun def = g.def();
        int stackAmmo = g.ammo(st);
        if (stackAmmo != lastStackAmmo) { lastStackAmmo = stackAmmo; predAmmo = stackAmmo; }
        if (reloading()) { scopeLevel = 0; return; }
        if (reloadUntil != 0) { reloadUntil = 0; predAmmo = -1; lastStackAmmo = -1; }

        if (useEdge) secondary(g, st);

        if (attack && (def.auto() || attackEdge) && ticks + 1 >= nextFire) {
            int ammo = predAmmo >= 0 ? predAmmo : stackAmmo;
            if (ammo <= 0) {
                if (attackEdge || ticks % 10 == 0) startReload(g, st);
                return;
            }
            send("gun_fire", scopeLevel > 0 ? 1 : 0);
            nextFire = Math.max(nextFire, ticks) + def.cooldown();
            predAmmo = ammo - 1;
            g.playLocal(st, "shoot");
            recoil(p, def);
            if (predAmmo <= 0) mc.player.playSoundToPlayer(SoundEvents.BLOCK_DISPENSER_FAIL, net.minecraft.sound.SoundCategory.PLAYERS, 0.3f, 1.8f);
        }
    }

    private static void recoil(ClientPlayerEntity p, GunDefs.Gun def) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        shotIdx = ticks - lastShotTick > 8 ? 0 : shotIdx + 1;
        lastShotTick = ticks;
        float ramp = Math.min(1f, 0.55f + shotIdx * 0.09f);
        float kick = def.recoil() * ramp * (0.9f + r.nextFloat() * 0.25f) * (scopeLevel > 0 ? 0.6f : 1f);
        float yaw = (float) (Math.sin(shotIdx * 1.35) * def.recoil() * 0.30 * ramp + (r.nextFloat() - 0.5f) * def.recoil() * 0.12f);
        p.setPitch(MathHelper.clamp(p.getPitch() - kick, -90f, 90f));
        p.setYaw(p.getYaw() + yaw);
    }

    // ---------------- интерфейс ----------------
    private static void hud(DrawContext c, net.minecraft.client.render.RenderTickCounter tc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        int w = c.getScaledWindowWidth(), h = c.getScaledWindowHeight();
        CRGunItem g = heldGun(mc);

        if (g != null && scopeLevel > 0 && mc.options.getPerspective().isFirstPerson()) drawScope(c, w, h);

        // отметка попадания вокруг прицела
        if (hitTicks > 0) {
            int cx = w / 2, cy = h / 2;
            int col = (hitHead ? 0xFFFF4040 : 0xFFFFFFFF), a = Math.min(255, hitTicks * 40);
            col = (a << 24) | (col & 0xFFFFFF);
            for (int i = 4; i < 9; i++) {
                c.fill(cx - i - 1, cy - i - 1, cx - i, cy - i, col);
                c.fill(cx + i, cy - i - 1, cx + i + 1, cy - i, col);
                c.fill(cx - i - 1, cy + i, cx - i, cy + i + 1, col);
                c.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, col);
            }
        }
        if (g == null) return;
        // патроны и перезарядка
        var tr = mc.textRenderer;
        GunDefs.Gun def = g.def();
        ItemStack st = mc.player.getMainHandStack();
        int ammo = predAmmo >= 0 ? predAmmo : g.ammo(st);
        int x = w - 12, y = h - 36;
        String cur = String.valueOf(Math.max(0, ammo));
        var m = c.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(2.2f, 2.2f, 1f);
        int col = ammo <= Math.max(1, def.mag() / 5) ? 0xFFFF5555 : 0xFFFFFFFF;
        c.drawText(tr, cur, -tr.getWidth(cur) - tr.getWidth(" / " + def.mag()) * 0 - 14, 0, col, true);
        m.pop();
        c.drawText(tr, " / " + def.mag() + "  ∞", x - 40 + 4, y + 8, 0xFFB0B8C8, true);
        if (reloading()) {
            int total = def.reloadTicks();
            float pr = 1f - (reloadUntil - ticks) / (float) total;
            int bw = 90, bx = w / 2 - bw / 2, by = h / 2 + 24;
            c.fill(bx - 1, by - 1, bx + bw + 1, by + 5, 0xCC000000);
            c.fill(bx, by, bx + (int) (bw * pr), by + 4, 0xFFFFB02E);
            String t = "ПЕРЕЗАРЯДКА";
            c.drawText(tr, t, w / 2 - tr.getWidth(t) / 2, by - 11, 0xFFFFD27A, true);
        } else if (ammo <= 0) {
            String t = "Нет патронов — R";
            c.drawText(tr, t, w / 2 - tr.getWidth(t) / 2, h / 2 + 24, 0xFFFF5555, true);
        }
    }

    /** Оптический прицел: чёрные поля вокруг круга и тонкий перекрест */
    private static void drawScope(DrawContext c, int w, int h) {
        int cx = w / 2, cy = h / 2;
        int r = (int) (Math.min(w, h) * 0.46);
        for (int y = 0; y < h; y++) {
            int dy = y - cy;
            if (Math.abs(dy) >= r) { c.fill(0, y, w, y + 1, 0xFF000000); continue; }
            int hw = (int) Math.sqrt((double) r * r - (double) dy * dy);
            c.fill(0, y, cx - hw, y + 1, 0xFF000000);
            c.fill(cx + hw, y, w, y + 1, 0xFF000000);
        }
        c.fill(cx - r, cy, cx + r, cy + 1, 0xFF000000);
        c.fill(cx, cy - r, cx + 1, cy + r, 0xFF000000);
        int gap = 6;
        c.fill(cx - gap, cy, cx + gap + 1, cy + 1, 0x00000000);        // центр без линий (чтобы цель была видна)
        c.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xCCFF3030);
    }
}
