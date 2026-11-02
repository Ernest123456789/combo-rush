package com.derevna230.comborush.boss;

import com.derevna230.comborush.cases.CaseDef;
import com.derevna230.comborush.cases.CaseRegistry;
import com.derevna230.comborush.cases.CaseService;
import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.item.ModItems;
import com.derevna230.comborush.shop.ShopService.Res;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

/** Запасной прилавок для игроков без клиентского мода: сундучное меню sgui. С модом открывается TraderScreen */
public final class TraderGui {
    private static final int[] CASE_SLOTS = {14, 15, 16};

    private final ServerPlayerEntity player;
    private final GolemTraderEntity trader;
    private final SimpleGui gui;

    public static void open(ServerPlayerEntity player, GolemTraderEntity trader) {
        new TraderGui(player, trader).gui.open();
    }

    private TraderGui(ServerPlayerEntity player, GolemTraderEntity trader) {
        this.player = player;
        this.trader = trader;
        this.gui = new SimpleGui(ScreenHandlerType.GENERIC_9X3, player, false);
        gui.setTitle(Text.literal("Торговец Редстоуна"));
        render();
    }

    private void render() {
        PlayerProfile pr = PlayerProfile.of(player);
        int dia = CaseService.countDiamonds(player);
        for (int i = 0; i < 27; i++)
            gui.setSlot(i, new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE).setName(Text.literal(" ")));

        gui.setSlot(10, new GuiElementBuilder(ModItems.REDSTONE_HEART)
            .setName(Text.literal("§c§lСердце голема §7(за алмазы)"))
            .setLore(List.of(Text.literal("§7ПКМ по земле — пробудить §cРедстоунового голема§7."),
                Text.literal("§7Очень опасный босс. Награда: кейсы, монеты, эссенция."),
                Text.literal("§bЦена: " + TraderNet.HEART_DIAMONDS + " алмазов §7(у вас " + dia + ")"),
                Text.literal("§aЛКМ §7— купить")))
            .glow(true)
            .setCallback((slot, type, action) -> done(TraderNet.buyAndNotify(player, trader, "heart_d"))));

        gui.setSlot(11, new GuiElementBuilder(ModItems.REDSTONE_HEART)
            .setName(Text.literal("§c§lСердце голема §7(за эссенцию)"))
            .setLore(List.of(Text.literal("§7То же сердце, но за эссенцию."),
                Text.literal("§dЦена: " + TraderNet.HEART_ESSENCE + " эссенции §7(у вас " + pr.essence() + ")"),
                Text.literal("§aЛКМ §7— купить")))
            .setCallback((slot, type, action) -> done(TraderNet.buyAndNotify(player, trader, "heart_e"))));

        List<String> ids = trader.offers();
        for (int i = 0; i < ids.size() && i < CASE_SLOTS.length; i++) {
            CaseDef def = CaseRegistry.get(ids.get(i));
            if (def == null || ModItems.CASES.get(def.id) == null) continue;
            final String key = "case" + i;
            int stock = trader.stock(i);
            String cur = def.paysDiamonds() ? " алмазов" : " эссенции";
            gui.setSlot(CASE_SLOTS[i], new GuiElementBuilder(ModItems.CASES.get(def.id))
                .setName(Text.literal("§d" + def.name + " §a−" + GolemTraderEntity.DISCOUNT_PERCENT + "%"))
                .setLore(List.of(Text.literal("§7" + def.description),
                    Text.literal("§8Было: §m" + def.price + cur),
                    Text.literal((def.paysDiamonds() ? "§b" : "§d") + "Цена: " + TraderNet.price(def) + cur),
                    Text.literal(stock > 0 ? "§7Осталось: " + stock : "§cРаскуплено"),
                    Text.literal("§aЛКМ §7— купить")))
                .setCallback((slot, type, action) -> done(TraderNet.buyAndNotify(player, trader, key))));
        }

        gui.setSlot(22, new GuiElementBuilder(Items.DIAMOND)
            .setName(Text.literal("§fВаш кошелёк"))
            .setLore(List.of(Text.literal("§bАлмазы в инвентаре: " + dia), Text.literal("§dЭссенция: " + pr.essence()))));
    }

    private void done(Res r) {
        player.sendMessage(Text.literal((r.ok() ? "§a" : "§c") + r.msg()), true);
        render();
    }
}
