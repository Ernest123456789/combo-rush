package com.derevna230.comborush.species;

import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.hud.HudSync;
import com.derevna230.comborush.prestige.PrestigeManager;
import com.derevna230.comborush.shop.ShopService.Res;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class SpeciesGui {
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private final ServerPlayerEntity player;
    private final SimpleGui gui;

    public static void open(ServerPlayerEntity player) {
        if (HudSync.has(player)) { SpeciesNet.open(player); return; }   // экран в стиле Balatro
        new SpeciesGui(player).gui.open();
    }

    private SpeciesGui(ServerPlayerEntity player) {
        this.player = player;
        this.gui = new SimpleGui(ScreenHandlerType.GENERIC_9X4, player, false);
        gui.setTitle(Text.literal("Выбор существа"));
        render();
    }

    private void render() {
        PlayerProfile p = PlayerProfile.of(player);
        for (int i = 0; i < 36; i++)
            gui.setSlot(i, new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE).setName(Text.literal(" ")));

        List<SpeciesDef> list = new ArrayList<>(SpeciesRegistry.all());
        boolean canChange = SpeciesService.canChange(p);

        for (int i = 0; i < list.size() && i < SLOTS.length; i++) {
            SpeciesDef d = list.get(i);
            boolean selected = d.id.equals(p.species());
            boolean unlocked = d.unlockedFor(p);

            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal("§7" + d.description));
            lore.add(Text.literal("§7Ранг: §f" + p.rankOf(d.id) + "/" + PrestigeManager.MAX_RANK));
            if (d.shop_price_mult != 1.0)
                lore.add(Text.literal("§7Цены в магазине: " + percent(d.shop_price_mult)));
            if (selected) lore.add(Text.literal("§aВыбрано"));
            else if (!unlocked) lore.add(Text.literal("§cЗакрыто: нужно сбросов — " + d.unlock_resets));
            else if (!canChange) lore.add(Text.literal("§cСмена возможна только после сброса"));
            else lore.add(Text.literal("§eНажми, чтобы выбрать"));

            gui.setSlot(SLOTS[i], new GuiElementBuilder(unlocked ? icon(d) : Items.BARRIER)
                .setName(Text.literal((selected ? "§a§l" : unlocked ? "§f" : "§8") + d.name))
                .setLore(lore)
                .glow(selected)
                .setCallback((slot, type, action) -> choose(d)));
        }
    }

    private void choose(SpeciesDef d) {
        if (d.id.equals(PlayerProfile.of(player).species())) return;
        Res r = SpeciesService.choose(player, d.id);
        msg((r.ok() ? "§a" : "§c") + r.msg());
        render();
    }

    private static String percent(double mult) {
        int pct = (int) Math.round((mult - 1.0) * 100);
        return (pct > 0 ? "§c+" : "§a") + pct + "%";
    }

    private static Item icon(SpeciesDef d) {
        Identifier id = Identifier.tryParse(d.icon);
        return id == null ? Items.PAPER : Registries.ITEM.get(id);
    }

    private void msg(String s) { player.sendMessage(Text.literal(s), true); }
}
