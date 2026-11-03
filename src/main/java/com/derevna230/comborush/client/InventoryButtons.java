package com.derevna230.comborush.client;

import com.derevna230.comborush.hud.ShopActionPayload;
import com.derevna230.comborush.item.ModItems;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Ряд кнопок над инвентарём: магазин, существа, эссенция, мастерская, вселенные, журнал.
 * Следует за окном инвентаря, когда открывается книга рецептов.
 */
public final class InventoryButtons {
    private InventoryButtons() {}

    private record Def(String tip, ItemStack icon, Runnable action) {}

    private static void command(String cmd) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.networkHandler.sendChatCommand(cmd);
    }

    private static void payload(String action) { payload(action, ""); }

    private static void payload(String action, String c) {
        if (ClientPlayNetworking.canSend(ShopActionPayload.ID)) ClientPlayNetworking.send(new ShopActionPayload(action, 0, 0, c));
    }

    private static List<Def> defs() {
        return List.of(
            new Def("Магазин усилителей (/shop)", new ItemStack(Items.EMERALD), () -> command("shop")),
            new Def("Существа (/species)", new ItemStack(Items.TOTEM_OF_UNDYING), () -> command("species")),
            new Def("Эссенция и кейсы (/essence)", new ItemStack(Items.AMETHYST_SHARD), () -> command("essence")),
            new Def("Мастерская: станки и обмен (/workshop)", new ItemStack(Items.CRAFTING_TABLE), () -> payload("ws_open")),
            new Def("Вселенные: Ночной город", UniversesScreen.icon(), () -> MinecraftClient.getInstance().setScreen(new UniversesScreen())),
            new Def("Журнал «Сценарий» (J)", new ItemStack(ModItems.PASS), () -> payload("camp_open")));
    }

    /** Купленные в Мастерской карманные станки: колонка кнопок справа от окна инвентаря */
    private record Pocket(String id, String tip, ItemStack icon) {}

    private static List<Pocket> pockets() {
        List<Pocket> all = List.of(
            new Pocket("pocket_crafting", "Карманный верстак", new ItemStack(Items.CRAFTING_TABLE)),
            new Pocket("pocket_furnace", "Карманная печь: переплавить стопку с курсора или из руки (1 уголь за предмет)", new ItemStack(Items.FURNACE)),
            new Pocket("pocket_anvil", "Карманная наковальня", new ItemStack(Items.ANVIL)),
            new Pocket("pocket_enchanting", "Карманная чаровальня", new ItemStack(Items.ENCHANTING_TABLE)));
        List<Pocket> out = new ArrayList<>();
        for (Pocket p : all) if (HudModel.pockets.contains(p.id())) out.add(p);
        return out;
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof InventoryScreen)) return;
            List<Pocket> pk = pockets();
            List<ButtonWidget> side = new ArrayList<>();
            for (Pocket p : pk) {
                ButtonWidget b = ButtonWidget.builder(Text.empty(), x -> payload("pocket_use", p.id()))
                    .dimensions(0, 0, 20, 20).tooltip(Tooltip.of(Text.literal(p.tip()))).build();
                side.add(b);
                Screens.getButtons(screen).add(b);
            }
            if (!side.isEmpty()) ScreenEvents.afterRender(screen).register((s, c, mx, my, delta) -> {
                int left = leftOf(s, side);
                int top = (s.height - 166) / 2 + 6;
                for (int i = 0; i < side.size(); i++) {
                    ButtonWidget b = side.get(i);
                    b.setPosition(left + 176 + 4, top + i * 22);
                    c.drawItem(pk.get(i).icon(), b.getX() + 2, b.getY() + 2);
                }
            });
            List<Def> list = defs();
            List<ButtonWidget> mine = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                Def d = list.get(i);
                ButtonWidget b = ButtonWidget.builder(Text.empty(), x -> d.action().run())
                    .dimensions(0, 0, 20, 20).tooltip(Tooltip.of(Text.literal(d.tip()))).build();
                mine.add(b);
                Screens.getButtons(screen).add(b);
            }
            place(screen, mine);
            ScreenEvents.afterRender(screen).register((s, c, mx, my, delta) -> {
                place(s, mine);
                for (int i = 0; i < mine.size(); i++) {
                    ButtonWidget b = mine.get(i);
                    c.drawItem(list.get(i).icon(), b.getX() + 2, b.getY() + 2);
                }
            });
        });
    }

    /** Левый край окна инвентаря = кнопка книги рецептов − 104 (она двигается вместе с окном) */
    private static int leftOf(net.minecraft.client.gui.screen.Screen screen, List<ButtonWidget> mine) {
        int left = (screen.width - 176) / 2;
        for (ClickableWidget wd : Screens.getButtons(screen)) {
            if (mine.contains(wd)) continue;
            if (wd.getWidth() == 20 && wd.getHeight() == 18) { left = wd.getX() - 104; break; }
        }
        return left;
    }

    private static void place(net.minecraft.client.gui.screen.Screen screen, List<ButtonWidget> mine) {
        int left = leftOf(screen, mine);
        int top = (screen.height - 166) / 2 - 22;
        int total = mine.size() * 22 - 2;
        int x0 = left + (176 - total) / 2;
        for (int i = 0; i < mine.size(); i++) mine.get(i).setPosition(x0 + i * 22, top);
    }
}
