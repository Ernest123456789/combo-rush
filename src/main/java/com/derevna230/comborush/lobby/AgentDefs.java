package com.derevna230.comborush.lobby;

import java.util.List;

/** Агенты (облик бойца) «Противостояния»: по три на сторону, все бесплатные; выбираются в «Снаряжении» → «АГЕНТЫ» */
public final class AgentDefs {
    private AgentDefs() {}

    public static final List<String> CT = List.of("ct", "ct_marine", "ct_gas");
    public static final List<String> T = List.of("t", "t_merc", "t_guerrilla");
    public static final String SLOT_CT = "agent_ct", SLOT_T = "agent_t";

    public static boolean isAgent(String id) { return CT.contains(id) || T.contains(id); }
    public static String slotOf(String id) { return CT.contains(id) ? SLOT_CT : SLOT_T; }

    public static String name(String id) {
        return switch (id) {
            case "ct" -> "Спецназ | Штурмовая группа";
            case "ct_marine" -> "Спецназ | Морпех";
            case "ct_gas" -> "Спецназ | Химзащита";
            case "t" -> "Боевик | Пустынный отряд";
            case "t_merc" -> "Боевик | Наёмник";
            case "t_guerrilla" -> "Боевик | Партизан";
            default -> id;
        };
    }
}
