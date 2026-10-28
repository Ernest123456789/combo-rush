package com.derevna230.comborush.campaign;

import com.derevna230.comborush.ComboRush;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.*;

/**
 * Личный прогресс кампании. Не сбрасывается при сбросе прогресса (сюжет — это «память», как и эссенция).
 * done — выполненные личные задания; claimed — общие задания, за которые игрок уже получил награду;
 * flags — служебные числа (дедлайн курьера, места адресатов и т.п.).
 */
public record PlayerCampaign(List<String> done, Map<String, Integer> counters, List<String> claimed, Map<String, Long> flags) {
    public static final PlayerCampaign EMPTY = new PlayerCampaign(List.of(), Map.of(), List.of(), Map.of());

    public static final Codec<PlayerCampaign> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.listOf().optionalFieldOf("done", List.of()).forGetter(PlayerCampaign::done),
        Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("counters", Map.of()).forGetter(PlayerCampaign::counters),
        Codec.STRING.listOf().optionalFieldOf("claimed", List.of()).forGetter(PlayerCampaign::claimed),
        Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("flags", Map.of()).forGetter(PlayerCampaign::flags)
    ).apply(i, PlayerCampaign::new));

    public static final AttachmentType<PlayerCampaign> TYPE = AttachmentRegistry.create(
        Identifier.of(ComboRush.MOD_ID, "campaign"),
        b -> b.persistent(CODEC).copyOnDeath().initializer(() -> EMPTY));

    public static PlayerCampaign of(ServerPlayerEntity p) { return p.getAttachedOrElse(TYPE, EMPTY); }
    public static void set(ServerPlayerEntity p, PlayerCampaign c) { p.setAttached(TYPE, c); }

    public boolean isDone(String id) { return done.contains(id); }
    public boolean hasClaimed(String id) { return claimed.contains(id); }
    public int counter(String id) { return counters.getOrDefault(id, 0); }
    public long flag(String key) { return flags.getOrDefault(key, 0L); }

    public PlayerCampaign withDone(String id) {
        List<String> d = new ArrayList<>(done); if (!d.contains(id)) d.add(id);
        Map<String, Integer> c = new HashMap<>(counters); c.remove(id);
        return new PlayerCampaign(List.copyOf(d), Map.copyOf(c), claimed, flags);
    }
    public PlayerCampaign withClaimed(String id) {
        List<String> d = new ArrayList<>(claimed); if (!d.contains(id)) d.add(id);
        return new PlayerCampaign(done, counters, List.copyOf(d), flags);
    }
    public PlayerCampaign withCounter(String id, int v) {
        Map<String, Integer> c = new HashMap<>(counters); c.put(id, v);
        return new PlayerCampaign(done, Map.copyOf(c), claimed, flags);
    }
    public PlayerCampaign withFlag(String key, long v) {
        Map<String, Long> f = new HashMap<>(flags);
        if (v == 0) f.remove(key); else f.put(key, v);
        return new PlayerCampaign(done, counters, claimed, Map.copyOf(f));
    }
    /** Убрать все флаги с префиксом (например, «courier_») */
    public PlayerCampaign clearFlags(String prefix) {
        Map<String, Long> f = new HashMap<>(flags);
        f.keySet().removeIf(k -> k.startsWith(prefix));
        return new PlayerCampaign(done, counters, claimed, Map.copyOf(f));
    }
}
