package com.derevna230.comborush.lobby;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.item.GunDefs;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.*;

/**
 * Снаряжение для «Противостояния»: на каждый слот (ствол или «knife») — какой скин надет.
 * Слот → id предмета. Хранится у игрока строкой «слот=предмет;…» и переживает смерть.
 */
public final class Loadout {
    private Loadout() {}

    private static final AttachmentType<String> DATA = AttachmentRegistry.create(
        Identifier.of(ComboRush.MOD_ID, "standoff_loadout"),
        b -> b.persistent(Codec.STRING).copyOnDeath());

    public static final String KNIFE_SLOT = LoadoutDefs.KNIFE_SLOT;
    public static List<String> slots() { return LoadoutDefs.slots(); }
    public static List<String> candidates(String slot) { return LoadoutDefs.candidates(slot); }
    public static boolean isStandard(String slot, String item) { return LoadoutDefs.isStandard(slot, item); }

    /** Какие предметы-оружие у игрока есть (инвентарь + эндер-сундук) */
    public static Set<String> owned(ServerPlayerEntity p) {
        Set<String> out = new HashSet<>();
        scan(p.getInventory(), out);
        scan(p.getEnderChestInventory(), out);
        return out;
    }

    private static void scan(Inventory inv, Set<String> out) {
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isEmpty()) continue;
            Identifier id = Registries.ITEM.getId(st.getItem());
            if (id.getNamespace().equals(ComboRush.MOD_ID)) out.add(id.getPath());
        }
    }

    public static Map<String, String> get(ServerPlayerEntity p) {
        Map<String, String> m = new LinkedHashMap<>();
        String s = p.getAttached(DATA);
        if (s == null || s.isEmpty()) return m;
        for (String part : s.split(";")) {
            int i = part.indexOf('=');
            if (i > 0) m.put(part.substring(0, i), part.substring(i + 1));
        }
        return m;
    }

    private static void put(ServerPlayerEntity p, Map<String, String> m) {
        StringBuilder sb = new StringBuilder();
        for (var e : m.entrySet()) { if (sb.length() > 0) sb.append(';'); sb.append(e.getKey()).append('=').append(e.getValue()); }
        p.setAttached(DATA, sb.toString());
    }

    /** Надетый предмет слота (стандартный, если ничего не выбрано) */
    public static String equipped(ServerPlayerEntity p, String slot) {
        String v = get(p).get(slot);
        return v == null ? candidates(slot).get(0) : v;
    }

    /** @return null или причина отказа */
    public static String equip(ServerPlayerEntity p, String slot, String item) {
        if (!slots().contains(slot) || !candidates(slot).contains(item)) return "Такого предмета для этого слота нет.";
        if (!isStandard(slot, item) && !owned(p).contains(item)) return "Этого скина нет в вашем инвентаре или эндер-сундуке.";
        Map<String, String> m = get(p);
        if (isStandard(slot, item) && item.equals(candidates(slot).get(0))) m.remove(slot); else m.put(slot, item);
        put(p, m);
        return null;
    }
}
