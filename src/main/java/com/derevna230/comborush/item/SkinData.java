package com.derevna230.comborush.item;

import com.derevna230.comborush.ComboRush;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Данные скина как в CS2, хранятся в предмете (CUSTOM_DATA):
 * wf — износ (float 0..1, выдаётся один раз и не меняется), ps — паттерн (0..999), st — счётчик убийств StatTrak™ (нет ключа — обычный).
 * Износ укладывается в окно скина: у одних скинов бывают только «поношенные», у других — только «с завода».
 */
public final class SkinData {
    private SkinData() {}

    public static final String WF = "wf", PS = "ps", ST = "st";
    public static final List<String> RARITIES = List.of("uncommon", "rare", "epic", "legendary", "mythic");

    private static final Map<String, float[]> OWN_WINDOW = Map.of(
        "ak47_redline", new float[]{0.10f, 0.70f},
        "claw_fade", new float[]{0.00f, 0.08f},
        "claw_doppler", new float[]{0.00f, 0.08f},
        "claw_heat", new float[]{0.00f, 0.75f},
        "claw_tiger", new float[]{0.00f, 1.00f},
        "claw_crimsonweb", new float[]{0.00f, 1.00f},
        "glock18_hazefade", new float[]{0.00f, 0.08f});

    public static String idOf(ItemStack st) {
        Identifier id = Registries.ITEM.getId(st.getItem());
        return id.getNamespace().equals(ComboRush.MOD_ID) ? id.getPath() : "";
    }

    /** Редкость предмета по id ("" — у предмета нет скиновой редкости) */
    public static String rarityOf(String id) {
        for (GunDefs.Skin s : GunDefs.SKINS) if (s.id().equals(id)) return s.rarity();
        return id.equals(GunDefs.KNIFE) ? "mythic" : "";
    }

    /** Предметы с износом: скины и Нож-коготь (стандартные стволы и ножи Т/КТ — «без покраски») */
    public static boolean rollable(String id) {
        String r = rarityOf(id);
        return !r.isEmpty() && !r.equals("common");
    }
    public static boolean rollable(ItemStack st) { return !st.isEmpty() && rollable(idOf(st)); }

    public static float[] window(String id) {
        float[] w = OWN_WINDOW.get(id);
        if (w != null) return w;
        return switch (rarityOf(id)) {
            case "epic" -> new float[]{0f, 0.80f};
            case "legendary" -> new float[]{0f, 0.60f};
            case "mythic" -> new float[]{0f, 0.60f};
            default -> new float[]{0f, 1f};
        };
    }

    private static NbtCompound nbt(ItemStack st) {
        NbtComponent c = st.get(DataComponentTypes.CUSTOM_DATA);
        return c == null ? new NbtCompound() : c.copyNbt();
    }

    public static float wear(ItemStack st) {
        NbtCompound n = nbt(st);
        return n.contains(WF) ? n.getFloat(WF) : -1f;
    }
    public static int pattern(ItemStack st) { NbtCompound n = nbt(st); return n.contains(PS) ? n.getInt(PS) : -1; }
    /** Убийств на StatTrak™-предмете; -1 — предмет обычный */
    public static int kills(ItemStack st) { NbtCompound n = nbt(st); return n.contains(ST) ? n.getInt(ST) : -1; }
    public static boolean statTrak(ItemStack st) { return kills(st) >= 0; }

    /** Износ в долях окна скина (0 — лучший, 1 — худший): нужен обмену */
    public static float norm(ItemStack st) {
        float f = wear(st);
        if (f < 0) return 0.5f;
        float[] w = window(idOf(st));
        return w[1] <= w[0] ? 0f : Math.max(0f, Math.min(1f, (f - w[0]) / (w[1] - w[0])));
    }

    public static void apply(ItemStack st, float wf, int ps, boolean stat) {
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, st, n -> {
            n.putFloat(WF, wf); n.putInt(PS, ps);
            if (stat) n.putInt(ST, 0); else n.remove(ST);
        });
    }

    /** Выдать новому предмету износ, паттерн и (1 из 10) StatTrak™. Не скин — ничего не делает. */
    public static void roll(ItemStack st, Random r) {
        if (!rollable(st)) return;
        float[] w = window(idOf(st));
        apply(st, w[0] + r.nextFloat() * (w[1] - w[0]), r.nextInt(1000), r.nextInt(10) == 0);
    }

    /** Предмет без данных (выдан командой или старый) получает их при первом тике на сервере */
    public static void ensure(ItemStack st) {
        if (rollable(st) && wear(st) < 0) roll(st, new Random());
    }

    public static void addKill(ItemStack st) {
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, st, n -> { if (n.contains(ST)) n.putInt(ST, n.getInt(ST) + 1); });
    }

    // ---------------- состояния ----------------
    public static int condIndex(float f) { return f < 0.07f ? 0 : f < 0.15f ? 1 : f < 0.38f ? 2 : f < 0.45f ? 3 : 4; }
    private static final String[] COND = {"Прямо с завода", "Немного поношенное", "После полевых испытаний", "Поношенное", "Закалённое в боях"};
    private static final String[] COND_SHORT = {"FN", "MW", "FT", "WW", "BS"};
    public static String condition(float f) { return COND[condIndex(f)]; }
    public static String condShort(float f) { return COND_SHORT[condIndex(f)]; }
    public static String floatText(float f) { return String.format(Locale.ROOT, "%.4f", f); }

    /** « (Поношенное)» для названия в рулетке и сообщениях; пусто, если износа нет */
    public static String suffix(ItemStack st) {
        float f = wear(st);
        return f < 0 ? "" : " (" + condition(f) + ")";
    }

    /** Название с приставкой StatTrak™ */
    public static Text name(ItemStack st, Text base) {
        if (!statTrak(st)) return base;
        return Text.literal("StatTrak™ ").formatted(Formatting.GOLD).append(base);
    }

    public static void tooltip(ItemStack st, List<Text> tip) {
        float f = wear(st);
        if (f >= 0) {
            tip.add(Text.literal("§7Состояние: §f" + condition(f) + " §8(" + floatText(f) + ")"));
            int p = pattern(st);
            if (p >= 0) tip.add(Text.literal("§7Паттерн: §f#" + p));
        }
        int k = kills(st);
        if (k >= 0) tip.add(Text.literal("§6StatTrak™ §7— убийств: §f" + k));
    }

    /** Одна строка для осмотра (V) */
    public static String inspectLine(ItemStack st) {
        float f = wear(st);
        if (f < 0) return "";
        StringBuilder sb = new StringBuilder("§7").append(condition(f)).append(" §8(").append(floatText(f)).append(")§7  Паттерн §f#").append(pattern(st));
        int k = kills(st);
        if (k >= 0) sb.append("  §6StatTrak™ §f").append(k);
        return sb.toString();
    }

    /** Цвет модели: чем сильнее износ, тем темнее и тусклее скин (РГБ без альфы) */
    public static int tint(ItemStack st) {
        float f = wear(st);
        if (f < 0) return 0xFFFFFF;
        float v = 1f - 0.34f * f;
        int c = (int) (255 * v);
        int b = (int) (255 * (v - 0.04f * f));
        return (c << 16) | (c << 8) | Math.max(0, b);
    }
}
