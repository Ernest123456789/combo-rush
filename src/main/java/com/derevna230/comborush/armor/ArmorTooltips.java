package com.derevna230.comborush.armor;

import com.derevna230.comborush.booster.EffectDef;
import com.derevna230.comborush.cases.CaseDef;
import com.derevna230.comborush.cases.CaseRegistry;
import com.derevna230.comborush.hud.HudPayload;
import com.derevna230.comborush.hud.HudSync;
import com.derevna230.comborush.item.ModItems;
import com.derevna230.comborush.species.SpeciesService;
import com.derevna230.comborush.species.SpeciesService.Trait;
import com.derevna230.comborush.weapon.AbilityDef;
import com.derevna230.comborush.weapon.AbilityText;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Подсказки к броне и кейсам. Описания живут в датапаке на сервере, поэтому сервер присылает готовые строки клиенту
 * (пакет "armor_info"), а предметы показывают их в подсказке.
 */
public final class ArmorTooltips {
    private ArmorTooltips() {}
    private static final Gson GSON = new Gson();

    /** Клиент: id комплекта / кейса → строки подсказки */
    private static final Map<String, List<String>> SETS = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> CASES = new ConcurrentHashMap<>();

    // ---------------- клиент ----------------
    public static void append(String setId, List<Text> tooltip) {
        List<String> lines = SETS.get(setId);
        if (lines == null) { tooltip.add(Text.literal("§8Описание загрузится при входе на сервер")); return; }
        for (String l : lines) tooltip.add(Text.literal(l));
    }

    public static void appendCase(String caseId, List<Text> tooltip) {
        List<String> lines = CASES.get(caseId);
        if (lines != null) for (String l : lines) tooltip.add(Text.literal(l));
        tooltip.add(Text.literal("§eПКМ — открыть"));
    }

    public static void read(JsonObject o) {
        SETS.clear(); CASES.clear();
        readInto(o.getAsJsonObject("sets"), SETS);
        readInto(o.getAsJsonObject("cases"), CASES);
    }

    private static void readInto(JsonObject o, Map<String, List<String>> map) {
        if (o == null) return;
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            List<String> l = new ArrayList<>();
            for (JsonElement s : e.getValue().getAsJsonArray()) l.add(s.getAsString());
            map.put(e.getKey(), l);
        }
    }

    // ---------------- сервер ----------------
    private static String serverJson = null;
    private static final Set<UUID> SENT = new HashSet<>();

    public static String rarCode(String r) {
        return switch (r) { case "uncommon" -> "§a"; case "rare" -> "§9"; case "epic" -> "§5"; case "legendary" -> "§6"; case "mythic" -> "§c"; default -> "§f"; };
    }
    public static String rarLabel(String r) {
        return switch (r) { case "uncommon" -> "Необычный"; case "rare" -> "Редкий"; case "epic" -> "Эпический";
                            case "legendary" -> "Легендарный"; case "mythic" -> "Мифический"; default -> "Обычный"; };
    }

    private static void effects(List<String> out, List<EffectDef> list) {
        for (EffectDef e : list) {
            Trait t = EffectText.describe(e);
            out.add((t.good() ? "  §a+ " : "  §c− ") + t.text());
        }
    }

    private static String kindLabel(String kind, int pieces) {
        return switch (kind) {
            case "armor" -> pieces > 1 ? "Комплект" : "Броня";
            case "hat" -> "Головной убор";
            case "shield" -> "Щит";
            case "bow" -> "Лук";
            case "gun" -> "Огнестрел";
            case "pick" -> "Кирка";
            case "pet" -> "Питомец";
            case "decor" -> "Декор";
            default -> "Оружие";
        };
    }

    private static String activeLabel(String kind, int pieces) {
        if (pieces > 1) return "§7Полный комплект (" + pieces + "/" + pieces + "):";
        return switch (kind) {
            case "hat" -> "§7Пока надета:";
            case "shield" -> "§7Пока в руке:";
            case "pet" -> "§7Пока питомец с тобой:";
            case "armor" -> "§7Пока надета:";
            default -> "§7Пока в основной руке:";
        };
    }

    /** Содержимое кейса по редкостям: «Легендарное — 8%: …» с переносом строк */
    public static List<String> groupedDrops(CaseDef c) {
        List<String> out = new ArrayList<>();
        for (String r : CaseDef.RAR_ORDER) {
            double sum = 0;
            List<String> names = new ArrayList<>();
            for (CaseDef.Drop d : c.dropsSorted()) {
                if (!ModItems.rarityOf(d.set).equals(r)) continue;
                sum += c.chance(d);
                ArmorSetDef def = ArmorSetRegistry.get(d.set);
                names.add(def == null ? d.set : def.name);
            }
            if (names.isEmpty()) continue;
            out.add(" " + rarCode(r) + "■ " + rarLabel(r) + " §8— §7" + CaseDef.pctText(sum));
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < names.size(); i++) {
                String n = names.get(i) + (i + 1 < names.size() ? ", " : "");
                if (line.length() > 0 && line.length() + n.length() > 56) { out.add("   §f" + line); line.setLength(0); }
                line.append(n);
            }
            if (line.length() > 0) out.add("   §f" + line);
        }
        return out;
    }

    /** Вызывается после перезагрузки датапаков (комплектов или кейсов) */
    public static synchronized void rebuildServer() {
        JsonObject root = new JsonObject();
        JsonObject sets = new JsonObject();
        for (String setId : ModItems.SET_ITEMS.keySet()) {
            ArmorSetDef def = ArmorSetRegistry.get(setId);
            String rar = ModItems.rarityOf(setId);
            String kind = ModItems.kindOf(setId);
            JsonArray a = new JsonArray();
            int pieces = ModItems.piecesIn(setId);
            a.add("§8" + kindLabel(kind, pieces) + ": " + rarCode(rar) + (def == null ? setId : def.name) + " §8· " + rarLabel(rar));
            if (def != null) {
                if (!def.description.isEmpty()) a.add("§7§o" + def.description);
                List<String> lines = new ArrayList<>();
                if (kind.equals("pet"))
                    lines.add(def.pet_damage > 0 ? "§7Боевой: сражается рядом с тобой (§c" + SpeciesService.num(def.pet_damage) + " урона§7)"
                                                 : "§7Спокойный: просто ходит рядом и помогает");
                if (!def.piece_effects.isEmpty()) {
                    lines.add("§7За каждую надетую часть:");
                    effects(lines, def.piece_effects);
                }
                if (!def.set_effects.isEmpty()) {
                    lines.add(activeLabel(kind, pieces));
                    effects(lines, def.set_effects);
                }
                if (!def.abilities.isEmpty()) {
                    lines.add("§7Способности:");
                    for (AbilityDef ab : def.abilities)
                        lines.add("  §d✦ §f" + AbilityText.describe(ab, kind.equals("bow"), kind.equals("pet")));
                }
                lines.forEach(a::add);
            }
            sets.add(setId, a);
        }
        root.add("sets", sets);

        JsonObject cases = new JsonObject();
        for (CaseDef c : CaseRegistry.all()) {
            JsonArray a = new JsonArray();
            if (!c.description.isEmpty()) a.add("§7" + c.description);
            if (c.needsKey()) a.add("§dНужен ключ: §f" + c.keyPrice + " эссенции §7(покупается при открытии)");
            if (c.noShop) a.add("§7Не продаётся: выпадает после матчей");
            a.add("§8Может выпасть:");
            for (String line : groupedDrops(c)) a.add(line);
            cases.add(c.id, a);
        }
        root.add("cases", cases);
        serverJson = GSON.toJson(root);
        SENT.clear();                                   // разослать заново
    }

    /** Раз в секунду: отправить описания тем, кто ещё не получил */
    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 0) return;
        if (serverJson == null) rebuildServer();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (SENT.contains(p.getUuid()) || !HudSync.has(p)) continue;
            ServerPlayNetworking.send(p, new HudPayload("armor_info", serverJson));
            SENT.add(p.getUuid());
        }
    }

    public static void forget(UUID id) { SENT.remove(id); }
}
