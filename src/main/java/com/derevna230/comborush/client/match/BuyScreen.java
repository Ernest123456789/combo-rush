package com.derevna230.comborush.client.match;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.hud.ShopActionPayload;
import com.derevna230.comborush.item.GunDefs;
import com.derevna230.comborush.match.BuyDefs;
import com.derevna230.comborush.match.Team;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Меню закупки (E в матче): категории слева, карточки с ценой и характеристиками справа, ЛКМ — купить */
public class BuyScreen extends Screen {
    private static int cat = 3;                 // по умолчанию — винтовки
    private float u, ox, oy;
    private String hover;
    private int hoverCat = -1;
    private static final float W = 440, H = 262;

    public BuyScreen() { super(Text.literal("Закупка")); }

    @Override public boolean shouldPause() { return false; }

    private Team side() { return MatchClient.ct ? Team.CT : Team.T; }

    private List<BuyDefs.Entry> entries() {
        List<BuyDefs.Entry> l = new ArrayList<>();
        for (BuyDefs.Entry e : BuyDefs.ALL) if (e.cat() == cat && BuyDefs.allowed(e, side())) l.add(e);
        return l;
    }

    private static ItemStack icon(String id) {
        Item it = Registries.ITEM.get(Identifier.of(ComboRush.MOD_ID, id));
        return it == Items.AIR ? ItemStack.EMPTY : new ItemStack(it);
    }

    private boolean has(String id) {
        if (client == null || client.player == null) return false;
        var inv = client.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (!st.isEmpty() && GunDefs.baseOf(Registries.ITEM.getId(st.getItem()).getPath()).equals(id)) return true;
        }
        return false;
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        if (!MatchClient.inMatch()) { close(); return; }
        u = Math.min(width / (W + 20), height / (H + 20));
        ox = (width - W * u) / 2f; oy = (height - H * u) / 2f;
        float mx = (mouseX - ox) / u, my = (mouseY - oy) / u;
        c.fill(0, 0, width, height, 0x88000000);
        c.getMatrices().push();
        c.getMatrices().translate(ox, oy, 0);
        c.getMatrices().scale(u, u, 1);
        int accent = MatchClient.ct ? 0xFF4A86E8 : 0xFFE0A030;
        // рамка
        c.fill(-1, -1, (int) W + 1, (int) H + 1, 0xFF000000);
        c.fill(0, 0, (int) W, (int) H, 0xEE10141C);
        c.fill(0, 0, (int) W, 24, 0xFF161C28);
        c.fill(0, 24, (int) W, 25, accent);
        var tr = textRenderer;
        MatchClient.text(c, tr, "ЗАКУПКА", 10, 7, 1.3f, 0xFFFFFFFF, 0);
        MatchClient.text(c, tr, MatchClient.ct ? "§9СПЕЦНАЗ" : "§6ТЕРРОРИСТЫ", 78, 9, 0.9f, 0xFFFFFFFF, 0);
        MatchClient.text(c, tr, MatchClient.fmtMoney(MatchClient.money), W - 10, 6, 1.5f, 0xFF7CE07C, 2);
        String bt;
        if (MatchClient.buyTicks <= 0) bt = "§cВремя закупки вышло";
        else if (!MatchClient.zone) bt = "§cТолько на своей базе";
        else { int s = (MatchClient.buyTicks + 19) / 20; bt = "§7Осталось §f" + s / 60 + ":" + String.format("%02d", s % 60); }
        MatchClient.text(c, tr, bt, W - 100, 9, 0.8f, 0xFFFFFFFF, 2);

        // категории
        hoverCat = -1;
        for (int i = 0; i < BuyDefs.CATS.size(); i++) {
            float x = 8, y = 32 + i * 30, w = 96, h = 26;
            boolean over = mx >= x && mx < x + w && my >= y && my < y + h;
            if (over) hoverCat = i;
            boolean sel = i == cat;
            c.fill((int) x, (int) y, (int) (x + w), (int) (y + h), sel ? 0xFF243048 : over ? 0xFF1C2433 : 0xFF161B26);
            if (sel) c.fill((int) x, (int) y, (int) x + 3, (int) (y + h), accent);
            MatchClient.text(c, tr, "§7" + (i + 1), x + 8, y + 9, 0.8f, 0xFFFFFFFF, 0);
            MatchClient.text(c, tr, BuyDefs.CATS.get(i), x + 18, y + 9, 0.8f, sel ? 0xFFFFFFFF : 0xFFB8C2D6, 0);
        }

        // карточки
        hover = null;
        List<BuyDefs.Entry> list = entries();
        float gx = 114, gy = 32, cw = 104, ch = 70, gap = 6;
        for (int i = 0; i < list.size(); i++) {
            BuyDefs.Entry e = list.get(i);
            float x = gx + (i % 3) * (cw + gap), y = gy + (i / 3) * (ch + gap);
            boolean over = mx >= x && mx < x + cw && my >= y && my < y + ch;
            boolean afford = MatchClient.money >= e.price(), owned = has(e.id());
            boolean ok = afford && MatchClient.canBuyNow() && !owned;
            if (over) hover = e.id();
            c.fill((int) x - 1, (int) y - 1, (int) (x + cw) + 1, (int) (y + ch) + 1, over && ok ? accent : 0xFF000000);
            c.fillGradient((int) x, (int) y, (int) (x + cw), (int) (y + ch), over ? 0xFF283246 : 0xFF1B2230, 0xFF121722);
            // иконка
            ItemStack st = icon(e.id());
            if (!st.isEmpty()) {
                c.getMatrices().push();
                c.getMatrices().translate(x + cw / 2f - 16, y + 6, 0);
                c.getMatrices().scale(2f, 2f, 1f);
                c.drawItem(st, 0, 0);
                c.getMatrices().pop();
            }
            MatchClient.text(c, tr, e.name(), x + 5, y + 40, 0.8f, afford ? 0xFFFFFFFF : 0xFF8A8A8A, 0);
            MatchClient.text(c, tr, MatchClient.fmtMoney(e.price()), x + cw - 5, y + 40, 0.8f, afford ? 0xFF7CE07C : 0xFFE05A5A, 2);
            if (owned) MatchClient.text(c, tr, "ЕСТЬ", x + cw - 5, y + 4, 0.7f, 0xFF7CE07C, 2);
            if (e.side() != null) MatchClient.text(c, tr, e.side() == Team.T ? "§6T" : "§9CT", x + 5, y + 4, 0.7f, 0xFFFFFFFF, 0);
            GunDefs.Gun g = GunDefs.gun(e.id());
            if (g != null && !e.id().equals("taser")) {
                float dmg = g.damage() * g.pellets();
                float acc = (float) (1 - Math.min(1, g.spread() / (g.pellets() > 1 ? 0.09 : 0.0085)));
                if (g.scope()) acc = 0.97f;
                float rate = (float) Math.min(1, (20.0 / g.cooldown()) / 14.0);
                bar(c, tr, x + 5, y + 51, "Урон", Math.min(1, dmg / 22f), String.format("%.1f", dmg));
                bar(c, tr, x + 5, y + 57, "Точн.", acc, "");
                bar(c, tr, x + 5, y + 63, "Темп", rate, Math.round(1200 / g.cooldown()) + "/м");
            } else {
                String d = switch (e.id()) {
                    case "grenade_he" -> "Взрыв: урон по площади";
                    case "grenade_flash" -> "Ослепляет противников";
                    case "grenade_smoke" -> "Дымовая завеса";
                    case "grenade_molotov" -> "Огонь на земле";
                    case "taser" -> "Один заряд вплотную";
                    case "defuse_kit" -> "Разминирование вдвое быстрее";
                    default -> "";
                };
                MatchClient.text(c, tr, d, x + 5, y + 55, 0.6f, 0xFF9AA4B8, 0);
            }
        }
        if (list.isEmpty()) MatchClient.text(c, tr, "Для вашей стороны здесь ничего нет", gx + 160, 120, 0.9f, 0xFF8A94A8, 1);
        MatchClient.text(c, tr, "ЛКМ — купить · 1–7 — категории · E / Esc — закрыть · оружие выдаётся со скином из «Снаряжения»",
            W / 2f, H - 11, 0.62f, 0xFF6F7A90, 1);
        c.getMatrices().pop();
    }

    private static void bar(DrawContext c, net.minecraft.client.font.TextRenderer tr, float x, float y, String label, float v, String val) {
        MatchClient.text(c, tr, label, x, y - 1, 0.5f, 0xFF9AA4B8, 0);
        float bx = x + 22, bw = 56;
        c.fill((int) bx, (int) y, (int) (bx + bw), (int) y + 3, 0xFF2A3242);
        c.fill((int) bx, (int) y, (int) (bx + bw * Math.max(0.03f, v)), (int) y + 3, 0xFFD8DEE8);
        if (!val.isEmpty()) MatchClient.text(c, tr, val, bx + bw + 2, y - 1, 0.5f, 0xFFB8C2D6, 0);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (hoverCat >= 0) { cat = hoverCat; click(); return true; }
        if (hover != null) {
            if (ClientPlayNetworking.canSend(ShopActionPayload.ID)) ClientPlayNetworking.send(new ShopActionPayload("m_buy", 0, 0, hover));
            click();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void click() {
        if (client != null && client.player != null) client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.2f);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (client != null && client.options.inventoryKey.matchesKey(keyCode, scanCode)) { close(); return true; }
        if (keyCode >= org.lwjgl.glfw.GLFW.GLFW_KEY_1 && keyCode < org.lwjgl.glfw.GLFW.GLFW_KEY_1 + BuyDefs.CATS.size()) {
            cat = keyCode - org.lwjgl.glfw.GLFW.GLFW_KEY_1; click(); return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) { }
}
