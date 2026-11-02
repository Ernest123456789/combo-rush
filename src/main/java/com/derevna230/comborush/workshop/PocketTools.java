package com.derevna230.comborush.workshop;

import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.hud.ShopActionPayload;
import com.derevna230.comborush.item.PocketToolItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Карманные станки — не предметы, а кнопки справа от окна инвентаря. Покупаются в Мастерской один раз
 * и остаются навсегда (хранятся в статистике life_*, которую сброс прогресса не трогает).
 */
public final class PocketTools {
    private PocketTools() {}

    public static final List<String> IDS = List.of("pocket_crafting", "pocket_furnace", "pocket_anvil", "pocket_enchanting");

    private static String key(String id) { return "life_unlock_" + id; }

    public static boolean unlocked(PlayerProfile prof, String id) { return prof.statLong(key(id)) > 0; }
    public static boolean unlocked(ServerPlayerEntity p, String id) { return unlocked(PlayerProfile.of(p), id); }

    public static PocketToolItem.Kind kindOf(String id) {
        return switch (id) {
            case "pocket_crafting" -> PocketToolItem.Kind.CRAFTING;
            case "pocket_furnace" -> PocketToolItem.Kind.FURNACE;
            case "pocket_anvil" -> PocketToolItem.Kind.ANVIL;
            default -> PocketToolItem.Kind.ENCHANTING;
        };
    }

    private static String idOf(PocketToolItem.Kind kind) {
        return switch (kind) {
            case CRAFTING -> "pocket_crafting"; case FURNACE -> "pocket_furnace";
            case ANVIL -> "pocket_anvil"; case ENCHANTING -> "pocket_enchanting";
        };
    }

    public static String title(String id) {
        return switch (id) {
            case "pocket_crafting" -> "Карманный верстак"; case "pocket_furnace" -> "Карманная печь";
            case "pocket_anvil" -> "Карманная наковальня"; default -> "Карманная чаровальня";
        };
    }

    public static void unlock(ServerPlayerEntity p, String id) {
        p.setAttached(PlayerProfile.TYPE, PlayerProfile.of(p).withStat(key(id), 1));
    }

    /** Старый предмет-станок использован: открываем кнопку */
    public static void unlock(ServerPlayerEntity p, PocketToolItem.Kind kind) {
        String id = idOf(kind);
        if (unlocked(p, id)) { p.sendMessage(Text.literal("§7" + title(id) + " уже открыт — кнопка справа от инвентаря."), true); return; }
        unlock(p, id);
        p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_SMITHING_TABLE_USE, SoundCategory.PLAYERS, 0.8f, 1.1f);
        p.sendMessage(Text.literal("§6" + title(id) + " открыт навсегда §7— кнопка справа от окна инвентаря."), false);
    }

    /** Нажата кнопка справа от инвентаря: c — id станка */
    public static void handle(ServerPlayerEntity p, ShopActionPayload a) {
        if (!a.action().equals("pocket_use")) return;
        String id = a.c();
        if (!IDS.contains(id) || !unlocked(p, id)) {
            p.sendMessage(Text.literal("§cЭтот станок ещё не куплен — Мастерская (/workshop)."), true);
            return;
        }
        PocketToolItem.openFor(p, kindOf(id));
    }
}
