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
 * Ряд кнопок над инвентарём: магазин, существа, эссенция, мастерская, журнал.
 * Следует за окном инвентаря, когда открывается книга рецептов.
 */
public final class InventoryButtons {
    private InventoryButtons() {}

    private record Def(String tip, ItemStack icon, Runnable action) {}

    private static void command(String cmd) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.networkHandler.sendChatCommand(cmd);
    }

    private static void payload(String action) {
        if (ClientPlayNetworking.canSend(ShopActionPayload.ID)) ClientPlayNetworking.send(new ShopActionPayload(action, 0, 0, ""));
    }

    private static List<Def> defs() {
        return List.of(
            new Def("Магазин усилителей (/shop)", new ItemStack(Items.EMERALD), () -> command("shop")),
            new Def("Существа (/species)", new ItemStack(Items.TOTEM_OF_UNDYING), () -> command("species")),
            new Def("Эссенция и кейсы (/essence)", new ItemStack(Items.AMETHYST_SHARD), () -> command("essence")),
            new Def("Мастерская: станки и обмен (/workshop)", new ItemStack(ModItems.POCKET.get("pocket_crafting")), () -> payload("ws_open")),
            new Def("Журнал «Сценарий» (J)", new ItemStack(ModItems.PASS), () -> payload("camp_open")));
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof InventoryScreen)) return;
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
    private static void place(net.minecraft.client.gui.screen.Screen screen, List<ButtonWidget> mine) {
        int left = (screen.width - 176) / 2;
        for (ClickableWidget wd : Screens.getButtons(screen)) {
            if (mine.contains(wd)) continue;
            if (wd.getWidth() == 20 && wd.getHeight() == 18) { left = wd.getX() - 104; break; }
        }
        int top = (screen.height - 166) / 2 - 22;
        int total = mine.size() * 22 - 2;
        int x0 = left + (176 - total) / 2;
        for (int i = 0; i < mine.size(); i++) mine.get(i).setPosition(x0 + i * 22, top);
    }
}
