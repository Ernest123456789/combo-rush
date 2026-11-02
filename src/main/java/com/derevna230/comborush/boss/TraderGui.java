package com.derevna230.comborush.boss;

import com.derevna230.comborush.cases.CaseService;
import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.item.ModItems;
import com.derevna230.comborush.score.ScoreEngine;
import com.derevna230.comborush.score.ScoreSource;
import com.derevna230.comborush.shop.ShopService.Res;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/** Прилавок Торговца Редстоуна (сундучный интерфейс sgui — работает и без клиентского мода) */
public final class TraderGui {
    public static final int HEART_DIAMONDS = 24, HEART_ESSENCE = 300, FIGURINE_DIAMONDS = 16;

    private final ServerPlayerEntity player;
    private final SimpleGui gui;

    public static void open(ServerPlayerEntity player, GolemTraderEntity trader) {
        new TraderGui(player).gui.open();
    }

    private TraderGui(ServerPlayerEntity player) {
        this.player = player;
        this.gui = new SimpleGui(ScreenHandlerType.GENERIC_9X3, player, false);
        gui.setTitle(Text.literal("Торговец Редстоуна"));
        render();
    }

    private static Item figurine() { return Registries.ITEM.get(Identifier.of("combo-rush", "decor_redstone_golem")); }

    private void render() {
        PlayerProfile pr = PlayerProfile.of(player);
        int dia = CaseService.countDiamonds(player);
        for (int i = 0; i < 27; i++)
            gui.setSlot(i, new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE).setName(Text.literal(" ")));

        gui.setSlot(10, new GuiElementBuilder(ModItems.REDSTONE_HEART)
            .setName(Text.literal("§c§lСердце голема §7(за алмазы)"))
            .setLore(List.of(Text.literal("§7ПКМ по земле — пробудить §cРедстоунового голема§7."),
                Text.literal("§7Очень опасный босс. Награда: кейсы, монеты, эссенция."),
                Text.literal("§bЦена: " + HEART_DIAMONDS + " алмазов §7(у вас " + dia + ")"),
                Text.literal("§aЛКМ §7— купить")))
            .glow(true)
            .setCallback((slot, type, action) -> done(buyHeart(false))));

        gui.setSlot(12, new GuiElementBuilder(ModItems.REDSTONE_HEART)
            .setName(Text.literal("§c§lСердце голема §7(за эссенцию)"))
            .setLore(List.of(Text.literal("§7То же сердце, но за эссенцию."),
                Text.literal("§dЦена: " + HEART_ESSENCE + " эссенции §7(у вас " + pr.essence() + ")"),
                Text.literal("§aЛКМ §7— купить")))
            .setCallback((slot, type, action) -> done(buyHeart(true))));

        Item fig = figurine();
        gui.setSlot(14, new GuiElementBuilder(fig == Items.AIR ? Items.REDSTONE_BLOCK : fig)
            .setName(Text.literal("§6Фигурка Редстоунового голема"))
            .setLore(List.of(Text.literal("§7Декор: ПКМ по блоку — поставить."),
                Text.literal("§bЦена: " + FIGURINE_DIAMONDS + " алмазов"),
                Text.literal("§aЛКМ §7— купить")))
            .setCallback((slot, type, action) -> done(buyFigurine())));

        gui.setSlot(16, new GuiElementBuilder(ModItems.CASES.get("decor"))
            .setName(Text.literal("§dКейс «Декор»"))
            .setLore(List.of(Text.literal("§7Растения, брёвна, фигурки, машины."),
                Text.literal("§bЦена: 12 алмазов"),
                Text.literal("§aЛКМ §7— купить")))
            .setCallback((slot, type, action) -> done(CaseService.buy(player, "decor"))));

        gui.setSlot(22, new GuiElementBuilder(Items.DIAMOND)
            .setName(Text.literal("§fВаш кошелёк"))
            .setLore(List.of(Text.literal("§bАлмазы в инвентаре: " + dia), Text.literal("§dЭссенция: " + pr.essence()))));
    }

    private Res buyHeart(boolean essence) {
        if (essence) {
            PlayerProfile pr = PlayerProfile.of(player);
            if (pr.essence() < HEART_ESSENCE) return Res.err("Не хватает эссенции: нужно " + HEART_ESSENCE + ".");
            player.setAttached(PlayerProfile.TYPE, pr.addEssence(-HEART_ESSENCE));
        } else {
            if (CaseService.countDiamonds(player) < HEART_DIAMONDS) return Res.err("Не хватает алмазов: нужно " + HEART_DIAMONDS + ".");
            CaseService.takeDiamonds(player, HEART_DIAMONDS);
        }
        player.getInventory().offerOrDrop(new ItemStack(ModItems.REDSTONE_HEART));
        return Res.ok("Сердце голема у вас. Найдите открытое место и ПКМ по земле — если не боитесь.");
    }

    private Res buyFigurine() {
        Item fig = figurine();
        if (fig == Items.AIR) return Res.err("Фигурки сейчас нет в наличии.");
        if (CaseService.countDiamonds(player) < FIGURINE_DIAMONDS) return Res.err("Не хватает алмазов: нужно " + FIGURINE_DIAMONDS + ".");
        CaseService.takeDiamonds(player, FIGURINE_DIAMONDS);
        player.getInventory().offerOrDrop(new ItemStack(fig));
        return Res.ok("Куплена фигурка Редстоунового голема.");
    }

    private void done(Res r) {
        player.sendMessage(Text.literal((r.ok() ? "§a" : "§c") + r.msg()), true);
        player.getWorld().playSound(null, player.getBlockPos(), r.ok() ? SoundEvents.ENTITY_VILLAGER_YES : SoundEvents.ENTITY_VILLAGER_NO,
            SoundCategory.NEUTRAL, 1f, 1f);
        if (r.ok()) ScoreEngine.award(player, ScoreSource.TRADE, "redstone_trader");
        render();
    }
}
