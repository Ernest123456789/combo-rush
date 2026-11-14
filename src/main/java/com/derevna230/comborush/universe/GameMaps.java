package com.derevna230.comborush.universe;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.List;

/** Карты соревновательного режима и их измерения */
public final class GameMaps {
    private GameMaps() {}

    public static final List<String> IDS = List.of("dust", "medina");
    private static GameMap dust, medina;

    public static GameMap dust() { if (dust == null) dust = new GameMap("dust", Universes.DUST); return dust; }
    public static GameMap medina() { if (medina == null) medina = new GameMap("medina", Universes.MEDINA); return medina; }

    public static GameMap get(String id) { return "medina".equals(id) ? medina() : dust(); }

    /** Карта, которой принадлежит мир (null — не карта матча) */
    public static GameMap of(RegistryKey<World> k) {
        if (k.equals(Universes.DUST)) return dust();
        if (k.equals(Universes.MEDINA)) return medina();
        return null;
    }

    public static boolean isMatchWorld(RegistryKey<World> k) { return k.equals(Universes.DUST) || k.equals(Universes.MEDINA); }
    public static boolean isMatchWorld(World w) { return w != null && isMatchWorld(w.getRegistryKey()); }
}
