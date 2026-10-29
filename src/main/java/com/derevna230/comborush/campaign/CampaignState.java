package com.derevna230.comborush.campaign;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.*;

/** Общий на сервер прогресс кампании и места построек. Хранится в мире (data/combo-rush_campaign.dat) */
public class CampaignState extends PersistentState {
    public final Set<String> done = new LinkedHashSet<>();
    public final Map<String, Integer> counters = new HashMap<>();

    /** Касса Билетёра */
    public BlockPos booth = null;
    public UUID ticketer = null;
    public float boothYaw = 0;
    /** Афиши Акта I: где стоят и найдены ли */
    public final List<Poster> posters = new ArrayList<>();
    /** Съёмочная площадка мини-босса */
    public BlockPos stage = null;
    /** Акт II: Закулисье (место Суфлёра) и трасса гонки на скейте (кольца по порядку) */
    public BlockPos backstage = null;
    public float backstageYaw = 0;
    public final List<BlockPos> race = new ArrayList<>();
    /** Акт III: студия Режиссёра (центр сцены) и трон (место Режиссёра) */
    public BlockPos studio = null, throne = null;
    public float studioYaw = 0;
    /** Выбранный финал: "" — ещё нет, "chair" — кресло Режиссёра, "chaos" — шоу сломано */
    public String ending = "";

    public static final class Poster {
        public BlockPos pos; public boolean found; public UUID entity; public int index; public float yaw;
        public Poster(int index, BlockPos pos, float yaw) { this.index = index; this.pos = pos; this.yaw = yaw; }
    }

    private static final Type<CampaignState> TYPE = new Type<>(CampaignState::new, CampaignState::read, null);

    public static CampaignState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "combo-rush_campaign");
    }

    public int counter(String id) { return counters.getOrDefault(id, 0); }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtList d = new NbtList();
        for (String s : done) d.add(NbtString.of(s));
        nbt.put("done", d);
        NbtCompound c = new NbtCompound();
        counters.forEach(c::putInt);
        nbt.put("counters", c);
        if (booth != null) nbt.putLong("booth", booth.asLong());
        if (ticketer != null) nbt.putUuid("ticketer", ticketer);
        nbt.putFloat("booth_yaw", boothYaw);
        if (stage != null) nbt.putLong("stage", stage.asLong());
        if (backstage != null) nbt.putLong("backstage", backstage.asLong());
        nbt.putFloat("backstage_yaw", backstageYaw);
        if (studio != null) nbt.putLong("studio", studio.asLong());
        if (throne != null) nbt.putLong("throne", throne.asLong());
        nbt.putFloat("studio_yaw", studioYaw);
        nbt.putString("ending", ending);
        nbt.putLongArray("race", race.stream().mapToLong(BlockPos::asLong).toArray());
        NbtList ps = new NbtList();
        for (Poster p : posters) {
            NbtCompound o = new NbtCompound();
            o.putInt("i", p.index);
            o.putLong("pos", p.pos.asLong());
            o.putBoolean("found", p.found);
            o.putFloat("yaw", p.yaw);
            if (p.entity != null) o.putUuid("e", p.entity);
            ps.add(o);
        }
        nbt.put("posters", ps);
        return nbt;
    }

    private static CampaignState read(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        CampaignState s = new CampaignState();
        for (NbtElement e : nbt.getList("done", NbtElement.STRING_TYPE)) s.done.add(e.asString());
        NbtCompound c = nbt.getCompound("counters");
        for (String k : c.getKeys()) s.counters.put(k, c.getInt(k));
        if (nbt.contains("booth")) s.booth = BlockPos.fromLong(nbt.getLong("booth"));
        if (nbt.containsUuid("ticketer")) s.ticketer = nbt.getUuid("ticketer");
        s.boothYaw = nbt.getFloat("booth_yaw");
        if (nbt.contains("stage")) s.stage = BlockPos.fromLong(nbt.getLong("stage"));
        if (nbt.contains("backstage")) s.backstage = BlockPos.fromLong(nbt.getLong("backstage"));
        s.backstageYaw = nbt.getFloat("backstage_yaw");
        if (nbt.contains("studio")) s.studio = BlockPos.fromLong(nbt.getLong("studio"));
        if (nbt.contains("throne")) s.throne = BlockPos.fromLong(nbt.getLong("throne"));
        s.studioYaw = nbt.getFloat("studio_yaw");
        s.ending = nbt.getString("ending");
        for (long l : nbt.getLongArray("race")) s.race.add(BlockPos.fromLong(l));
        for (NbtElement e : nbt.getList("posters", NbtElement.COMPOUND_TYPE)) {
            NbtCompound o = (NbtCompound) e;
            Poster p = new Poster(o.getInt("i"), BlockPos.fromLong(o.getLong("pos")), o.getFloat("yaw"));
            p.found = o.getBoolean("found");
            if (o.containsUuid("e")) p.entity = o.getUuid("e");
            s.posters.add(p);
        }
        return s;
    }
}
