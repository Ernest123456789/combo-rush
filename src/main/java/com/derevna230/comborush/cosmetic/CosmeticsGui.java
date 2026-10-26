package com.derevna230.comborush.cosmetic;

import com.derevna230.comborush.data.PlayerProfile;
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

public class CosmeticsGui {
    private static final int[] SLOTS = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34};

    private final ServerPlayerEntity player;
    private final SimpleGui gui;

    public static void open(ServerPlayerEntity player) { new CosmeticsGui(player).gui.open(); }

    private CosmeticsGui(ServerPlayerEntity player) {
        this.player = player;
        this.gui = new SimpleGui(ScreenHandlerType.GENERIC_9X5, player, false);
        gui.setTitle(Text.literal("Косметика"));
        render();
    }

    private static String typeLabel(String t) {
        return switch (t) { case "pet" -> "Питомец"; case "title" -> "Титул"; case "trail" -> "След"; default -> t; };
    }

    private void render() {
        PlayerProfile p = PlayerProfile.of(player);
        for (int i = 0; i < 45; i++)
            gui.setSlot(i, new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE).setName(Text.literal(" ")));

        gui.setSlot(4, new GuiElementBuilder(Items.AMETHYST_SHARD)
            .setName(Text.literal("§dЭссенция: " + p.essence()))
            .setLore(List.of(Text.literal("§7Даётся за сброс прогресса."),
                             Text.literal("§7Косметика не пропадает при сбросе."))));

        List<CosmeticDef> list = new ArrayList<>(CosmeticRegistry.all());
        for (int i = 0; i < list.size() && i < SLOTS.length; i++) {
            CosmeticDef d = list.get(i);
            boolean owned = p.owns(d.id);
            boolean on = owned && d.id.equals(p.equippedId(d.type));

            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal("§8" + typeLabel(d.type)));
            lore.add(Text.literal("§7" + d.description));
            if (on) lore.add(Text.literal("§aНадето §7— нажми, чтобы снять"));
            else if (owned) lore.add(Text.literal("§eКуплено §7— нажми, чтобы надеть"));
            else lore.add(Text.literal((p.essence() >= d.price ? "§d" : "§c") + "Цена: " + d.price + " эссенции"));

            gui.setSlot(SLOTS[i], new GuiElementBuilder(icon(d))
                .setName(Text.literal((on ? "§a§l" : owned ? "§f" : "§7") + d.name))
                .setLore(lore).glow(on)
                .setCallback((slot, type, action) -> click(d)));
        }
    }

    private void click(CosmeticDef d) {
        PlayerProfile p = PlayerProfile.of(player);
        if (p.owns(d.id)) {
            if (d.id.equals(p.equippedId(d.type))) player.setAttached(PlayerProfile.TYPE, p.unequip(d.type));
            else player.setAttached(PlayerProfile.TYPE, p.equip(d.type, d.id));
        } else {
            if (p.essence() < d.price) { msg("§cНе хватает эссенции."); return; }
            player.setAttached(PlayerProfile.TYPE, p.buyCosmetic(d.id, d.price).equip(d.type, d.id));
            msg("§aКуплено: " + d.name);
        }
        CosmeticManager.refresh(player);
        render();
    }

    private static Item icon(CosmeticDef d) {
        Identifier id = Identifier.tryParse(d.icon);
        return id == null ? Items.PAPER : Registries.ITEM.get(id);
    }
    private void msg(String s) { player.sendMessage(Text.literal(s), true); }
}
