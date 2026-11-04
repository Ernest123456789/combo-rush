package com.derevna230.comborush.campaign;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.*;

/**
 * Состояние сюжета Ночного города: очищен ли город устройством, какие постройки уже стоят
 * (укрытия, порталы, площадка на башне) и какие точки уже посетили в заданиях «посетить все».
 * Хранится в мире (data/combo-rush_city.dat).
 */
public class CityState extends PersistentState {
    public boolean cleared = false;
    public final Set<String> built = new HashSet<>();
    public final Map<String, Set<String>> visited = new HashMap<>();

    private static final Type<CityState> TYPE = new Type<>(CityState::new, CityState::read, null);

    public static CityState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "combo-rush_city");
    }

    public Set<String> visitedOf(String quest) { return visited.computeIfAbsent(quest, k -> new HashSet<>()); }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        nbt.putBoolean("cleared", cleared);
        NbtList b = new NbtList();
        for (String s : built) b.add(NbtString.of(s));
        nbt.put("built", b);
        NbtCompound v = new NbtCompound();
        for (var e : visited.entrySet()) {
            NbtList l = new NbtList();
            for (String s : e.getValue()) l.add(NbtString.of(s));
            v.put(e.getKey(), l);
        }
        nbt.put("visited", v);
        return nbt;
    }

    private static CityState read(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        CityState s = new CityState();
        s.cleared = nbt.getBoolean("cleared");
        for (NbtElement e : nbt.getList("built", NbtElement.STRING_TYPE)) s.built.add(e.asString());
        NbtCompound v = nbt.getCompound("visited");
        for (String k : v.getKeys()) {
            Set<String> set = new HashSet<>();
            for (NbtElement e : v.getList(k, NbtElement.STRING_TYPE)) set.add(e.asString());
            s.visited.put(k, set);
        }
        return s;
    }
}
