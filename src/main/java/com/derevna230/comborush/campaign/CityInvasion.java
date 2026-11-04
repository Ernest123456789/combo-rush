package com.derevna230.comborush.campaign;

import com.derevna230.comborush.universe.NightCity;
import com.derevna230.comborush.universe.Universes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Difficulty;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Вторжение в Ночной город: обычные монстры на улицах, «Чёрные Врата» (порталы контрабандистов), укрытия,
 * площадка наверху башни Нексуса и очистка города Камертоном.
 * До запуска Камертона монстры лезут и с улиц, и из Врат. После — только из Врат, и понемногу.
 * Все постройки ставятся лениво, когда игрок подходит ближе, и запоминаются в {@link CityState}.
 */
public final class CityInvasion {
    private CityInvasion() {}

    public static final String TAG_CITY = "cr_city", TAG_PORTAL = "cr_portal", TAG_HOLD = "cr_hold", TAG_ADD = "cr_badd";
    static final int P = NightCity.PERIOD;
    static final int G = NightCity.GROUND;

    /** Чёрные Врата: перекрёстки (i, j) */
    static final int[][] PORTALS = {{-1, -1}, {3, -1}, {-1, 2}, {3, 0}, {-3, 3}, {1, 3}, {-2, -1}, {2, 1}};

    /** Площадка на башне: центр, высота пола, точка прибытия лифта, точка спуска на площади */
    static final int DECK_X = 44, DECK_Z = 44, DECK_Y = 288;
    static final double ARRIVE_X = 44.5, ARRIVE_Y = DECK_Y + 1, ARRIVE_Z = 50.5;
    static final double PLAZA_X = 44.5, PLAZA_Y = G + 1, PLAZA_Z = 66.5;

    static BlockPos portalPos(int k) { return new BlockPos(PORTALS[k][0] * P + 8, G + 1, PORTALS[k][1] * P + 8); }
    /** Врата повёрнуты по очереди вдоль x и вдоль z */
    static boolean portalAlongX(int k) { return k % 2 == 0; }

    static ServerWorld city(MinecraftServer server) { return server.getWorld(Universes.NIGHT_CITY); }

    // ================================================================ тик
    static void tick(MinecraftServer server, ServerWorld w) {
        List<ServerPlayerEntity> ps = new ArrayList<>();
        for (ServerPlayerEntity p : w.getPlayers()) if (p.isAlive() && !p.isSpectator()) ps.add(p);
        if (ps.isEmpty()) return;
        int t = server.getTicks();
        CityState cs = CityState.get(server);
        if (t % 40 == 0) buildNear(w, cs, ps);
        if (t % 20 == 0) portalFx(w, cs, ps);
        if (w.getDifficulty() == Difficulty.PEACEFUL) return;
        if (t % 60 == 0 && !cs.cleared) ambient(w, ps);
        if (t % 100 == 0) portalSpawns(w, cs, ps);
    }

    static boolean near(List<ServerPlayerEntity> ps, double x, double z, double r) {
        for (ServerPlayerEntity p : ps) {
            double dx = p.getX() - x, dz = p.getZ() - z;
            if (dx * dx + dz * dz <= r * r) return true;
        }
        return false;
    }

    // ================================================================ уличные монстры
    private static void ambient(ServerWorld w, List<ServerPlayerEntity> ps) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (ServerPlayerEntity p : ps) {
            if (p.getY() > 90 || p.getY() < 40) continue;
            Box box = new Box(p.getBlockPos()).expand(48);
            int n = w.getEntitiesByClass(HostileEntity.class, box, e -> e.getCommandTags().contains(TAG_CITY)).size();
            if (n >= 12) continue;
            int add = Math.min(12 - n, 1 + r.nextInt(3));
            for (int i = 0; i < add; i++) {
                BlockPos at = streetSpot(w, p.getX(), p.getZ(), 20, 38, true);
                if (at == null) continue;
                spawn(w, pick(r), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, TAG_CITY);
            }
        }
    }

    static EntityType<? extends MobEntity> pick(ThreadLocalRandom r) {
        int x = r.nextInt(100);
        if (x < 38) return EntityType.ZOMBIE;
        if (x < 60) return EntityType.SKELETON;
        if (x < 80) return EntityType.SPIDER;
        return EntityType.CREEPER;
    }

    /** Место на улице (не внутри здания) на расстоянии rmin..rmax от точки. safe — не ближе 30 блоков к площади у башни */
    static BlockPos streetSpot(ServerWorld w, double cx, double cz, double rmin, double rmax, boolean safe) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 14; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = rmin + r.nextDouble() * (rmax - rmin);
            int x = (int) Math.floor(cx + Math.cos(a) * d), z = (int) Math.floor(cz + Math.sin(a) * d);
            if (x < NightCity.CITY_MIN || x > NightCity.CITY_MAX || z < NightCity.CITY_MIN || z > NightCity.CITY_MAX) continue;
            int u = Math.floorMod(x, P), v = Math.floorMod(z, P);
            if (u >= 16 && v >= 16) continue;                        // здание
            if (safe) {
                double sx = x - 44.0, sz = z - 58.0;
                if (sx * sx + sz * sz < 30 * 30) continue;
            }
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            BlockPos pos = new BlockPos(x, G + 1, z);
            if (!w.getBlockState(pos).isAir() || !w.getBlockState(pos.up()).isAir() || w.getBlockState(pos.down()).isAir()) continue;
            return pos;
        }
        return null;
    }

    static MobEntity spawn(ServerWorld w, EntityType<? extends MobEntity> type, double x, double y, double z, String... tags) {
        MobEntity m = type.create(w);
        if (m == null) return null;
        m.refreshPositionAndAngles(x, y, z, ThreadLocalRandom.current().nextFloat() * 360f, 0f);
        m.initialize(w, w.getLocalDifficulty(BlockPos.ofFloored(x, y, z)), SpawnReason.EVENT, null);
        if (m instanceof CreeperEntity) softenCreeper(m);
        for (String t : tags) m.addCommandTag(t);
        w.spawnEntityAndPassengers(m);
        return m;
    }

    /** Взрыв рядового крипера в городе небольшой: улицы и дома не превращаются в воронки */
    static void softenCreeper(MobEntity m) {
        NbtCompound n = new NbtCompound();
        m.writeNbt(n);
        n.putByte("ExplosionRadius", (byte) 1);
        m.readNbt(n);
    }

    // ================================================================ Чёрные Врата
    private static void portalSpawns(ServerWorld w, CityState cs, List<ServerPlayerEntity> ps) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int k = 0; k < PORTALS.length; k++) {
            if (!cs.built.contains("portal" + k)) continue;
            BlockPos c = portalPos(k);
            if (!near(ps, c.getX(), c.getZ(), 56)) continue;
            Box box = new Box(c).expand(22, 8, 22);
            int n = w.getEntitiesByClass(HostileEntity.class, box, e -> e.getCommandTags().contains(TAG_PORTAL)).size();
            int cap = cs.cleared ? 3 : 6;
            if (n >= cap) continue;
            int add = cs.cleared ? (r.nextInt(100) < 45 ? 1 : 0) : 2;
            for (int i = 0; i < add && n + i < cap; i++) {
                int side = r.nextBoolean() ? 2 : -2;
                double x = c.getX() + 0.5 + (portalAlongX(k) ? r.nextInt(3) - 1 : side);
                double z = c.getZ() + 0.5 + (portalAlongX(k) ? side : r.nextInt(3) - 1);
                MobEntity m = spawn(w, pick(r), x, G + 1, z, TAG_CITY, TAG_PORTAL);
                if (m != null) w.spawnParticles(ParticleTypes.REVERSE_PORTAL, x, G + 2, z, 18, 0.3, 0.8, 0.3, 0.1);
            }
        }
    }

    private static void portalFx(ServerWorld w, CityState cs, List<ServerPlayerEntity> ps) {
        for (int k = 0; k < PORTALS.length; k++) {
            if (!cs.built.contains("portal" + k)) continue;
            BlockPos c = portalPos(k);
            if (!near(ps, c.getX(), c.getZ(), 64)) continue;
            w.spawnParticles(ParticleTypes.PORTAL, c.getX() + 0.5, c.getY() + 3, c.getZ() + 0.5, 30, 1.0, 1.6, 1.0, 0.4);
            if (!cs.cleared) w.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, c.getX() + 0.5, c.getY() + 6.4, c.getZ() + 0.5, 2, 1.0, 0.1, 0.1, 0.01);
        }
    }

    /** Арка из плачущего обсидиана, внутри — фиолетовое стекло */
    private static void buildPortal(ServerWorld w, int k) {
        BlockPos c = portalPos(k);
        boolean ax = portalAlongX(k);
        for (int a = -2; a <= 2; a++) {
            for (int dy = 0; dy <= 5; dy++) {
                BlockPos p = ax ? c.add(a, dy, 0) : c.add(0, dy, a);
                boolean frame = Math.abs(a) == 2 || dy == 0 || dy == 5;
                if (frame) set(w, p, (dy + a) % 3 == 0 ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN);
                else set(w, p, (dy % 2 == 0) ? Blocks.PURPLE_STAINED_GLASS : Blocks.MAGENTA_STAINED_GLASS);
            }
        }
        // подсветка у основания
        for (int a : new int[]{-3, 3}) set(w, ax ? c.add(a, 0, 0) : c.add(0, 0, a), Blocks.SEA_LANTERN);
    }

    // ================================================================ укрытия и ретрансляторы
    /** Пятно 7×7 из чёрного бетона с бирюзовой каймой и четырьмя фонарными столбиками */
    private static void buildPod(ServerWorld w, BlockPos c) {
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            boolean edge = Math.max(Math.abs(dx), Math.abs(dz)) == 3;
            Block b = edge ? Blocks.CYAN_CONCRETE : (Math.abs(dx + dz) % 2 == 0 ? Blocks.BLACK_CONCRETE : Blocks.GRAY_CONCRETE);
            set(w, new BlockPos(c.getX() + dx, G, c.getZ() + dz), b);
        }
        for (int sx : new int[]{-3, 3}) for (int sz : new int[]{-3, 3}) {
            BlockPos p = new BlockPos(c.getX() + sx, G + 1, c.getZ() + sz);
            set(w, p, Blocks.BLACK_CONCRETE);
            set(w, p.up(), Blocks.BLACK_CONCRETE);
            set(w, p.up(2), Blocks.SEA_LANTERN);
        }
    }

    /** Высокая мачта ретранслятора — видна издалека */
    private static void buildRelay(ServerWorld w, BlockPos c) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            set(w, new BlockPos(c.getX() + dx, G, c.getZ() + dz), Blocks.BLACK_CONCRETE);
        for (int y = 1; y <= 16; y++) set(w, new BlockPos(c.getX(), G + y, c.getZ()), y % 4 == 0 ? Blocks.SEA_LANTERN : Blocks.CYAN_CONCRETE);
        set(w, new BlockPos(c.getX(), G + 17, c.getZ()), Blocks.SEA_LANTERN);
        set(w, new BlockPos(c.getX(), G + 18, c.getZ()), Blocks.LIGHTNING_ROD);
    }

    private static void buildNear(ServerWorld w, CityState cs, List<ServerPlayerEntity> ps) {
        for (int k = 0; k < PORTALS.length; k++) {
            String key = "portal" + k;
            if (cs.built.contains(key)) continue;
            BlockPos c = portalPos(k);
            if (!near(ps, c.getX(), c.getZ(), 56)) continue;
            w.getChunk(c);
            buildPortal(w, k);
            cs.built.add(key);
            cs.markDirty();
        }
        for (String id : CityStory.POD_SITES) {
            String key = "pod_" + id;
            if (cs.built.contains(key)) continue;
            BlockPos c = CityStory.site(id);
            if (c == null || !near(ps, c.getX(), c.getZ(), 56)) continue;
            w.getChunk(c);
            buildPod(w, c.down());
            cs.built.add(key);
            cs.markDirty();
        }
        for (String id : CityStory.RELAY_SITES) {
            String key = "relay_" + id;
            if (cs.built.contains(key)) continue;
            BlockPos c = CityStory.site(id);
            if (c == null || !near(ps, c.getX(), c.getZ(), 56)) continue;
            w.getChunk(c);
            buildRelay(w, c.down());
            cs.built.add(key);
            cs.markDirty();
        }
    }

    // ================================================================ площадка на башне
    static void ensureDeck(MinecraftServer server) {
        CityState cs = CityState.get(server);
        if (cs.built.contains("deck")) return;
        ServerWorld w = city(server);
        if (w == null) return;
        buildDeck(w);
        cs.built.add("deck");
        cs.markDirty();
    }

    private static void buildDeck(ServerWorld w) {
        for (int ox = -10; ox <= 10; ox += 10) for (int oz = -10; oz <= 10; oz += 10) w.getChunk(new BlockPos(DECK_X + ox, DECK_Y, DECK_Z + oz));
        for (int bx = DECK_X - 11; bx <= DECK_X + 10; bx++) {
            for (int bz = DECK_Z - 11; bz <= DECK_Z + 10; bz++) {
                double ex = bx + 0.5 - DECK_X, ez = bz + 0.5 - DECK_Z;
                double d = Math.sqrt(ex * ex + ez * ez);
                if (d > 9.7) continue;
                boolean core = Math.abs(ex) < 2.1 && Math.abs(ez) < 2.1;           // антенна и стеклянный столб в центре остаются
                // пол
                Block floor = d > 8.9 ? (((bx + bz) & 1) == 0 ? Blocks.SEA_LANTERN : Blocks.BLACK_CONCRETE)
                    : (d > 6.0 && d < 6.8) ? Blocks.CYAN_CONCRETE : Blocks.BLACK_CONCRETE;
                set(w, new BlockPos(bx, DECK_Y, bz), floor);
                // чаша снизу
                int[] rim = {9, 8, 6, 4};
                for (int i = 0; i < rim.length; i++) {
                    BlockPos q = new BlockPos(bx, DECK_Y - 1 - i, bz);
                    if (d <= rim[i] && w.getBlockState(q).isAir())
                        set(w, q, (i == 0 && d > 5 && (bx + bz) % 5 == 0) ? Blocks.SEA_LANTERN : Blocks.BLACK_CONCRETE);
                }
                // воздух над полом и ограждение
                for (int y = 1; y <= 9; y++) {
                    if (core) continue;
                    BlockPos q = new BlockPos(bx, DECK_Y + y, bz);
                    boolean rail = d > 8.7 && y <= 2;
                    set(w, q, rail ? Blocks.CYAN_STAINED_GLASS : Blocks.AIR);
                }
            }
        }
        // четыре столба с фонарями
        int[][] pil = {{8, 0}, {-8, 0}, {0, 8}, {0, -8}};
        for (int[] pl : pil) {
            int bx = DECK_X + pl[0] + (pl[0] > 0 ? 0 : -1), bz = DECK_Z + pl[1] + (pl[1] > 0 ? 0 : -1);
            for (int y = 1; y <= 4; y++) set(w, new BlockPos(bx, DECK_Y + y, bz), Blocks.BLACK_CONCRETE);
            set(w, new BlockPos(bx, DECK_Y + 5, bz), Blocks.SEA_LANTERN);
        }
        // терминал Камертона
        BlockPos t = new BlockPos(51, DECK_Y + 1, 47);
        set(w, t, Blocks.LODESTONE);
        set(w, t.up(), Blocks.END_ROD);
        set(w, t.west(), Blocks.SEA_LANTERN);
        set(w, t.south(), Blocks.SEA_LANTERN);
    }

    static void liftToDeck(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        ServerWorld w = city(server);
        if (w == null) return;
        ensureDeck(server);
        w.getChunk(new BlockPos((int) ARRIVE_X, DECK_Y, (int) ARRIVE_Z));
        p.stopRiding();
        p.teleport(w, ARRIVE_X, ARRIVE_Y, ARRIVE_Z, 180f, 10f);
        p.fallDistance = 0;
        w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.2f, 1.2f);
        w.spawnParticles(ParticleTypes.END_ROD, ARRIVE_X, ARRIVE_Y + 1, ARRIVE_Z, 30, 0.5, 1.0, 0.5, 0.05);
    }

    static void liftToPlaza(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        ServerWorld w = city(server);
        if (w == null) return;
        w.getChunk(new BlockPos((int) PLAZA_X, G, (int) PLAZA_Z));
        p.stopRiding();
        p.teleport(w, PLAZA_X, PLAZA_Y, PLAZA_Z, 0f, 8f);
        p.fallDistance = 0;
        w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.2f);
    }

    // ================================================================ очистка города
    /** Убрать монстров, пришедших с улиц и из Врат. Боссы, питомцы, чужие моды-события и именные существа остаются */
    static int purge(MinecraftServer server) {
        ServerWorld w = city(server);
        if (w == null) return 0;
        List<HostileEntity> list = w.getEntitiesByClass(HostileEntity.class, new Box(-420, -64, -420, 520, 330, 520), CityInvasion::purgeable);
        for (HostileEntity e : list) {
            w.spawnParticles(ParticleTypes.POOF, e.getX(), e.getBodyY(0.5), e.getZ(), 10, 0.3, 0.5, 0.3, 0.03);
            e.discard();
        }
        return list.size();
    }

    private static boolean purgeable(HostileEntity e) {
        boolean ours = false;
        for (String t : e.getCommandTags()) {
            if (t.startsWith("cr_boss")) return false;
            if (t.equals(TAG_CITY) || t.equals(TAG_PORTAL) || t.equals(TAG_HOLD) || t.equals(TAG_ADD)) ours = true;
        }
        if (ours) return true;
        return e.getCommandTags().isEmpty() && !e.hasCustomName();
    }

    // ================================================================ мелочи
    static void set(ServerWorld w, BlockPos p, Block b) {
        BlockState s = b.getDefaultState();
        if (w.getBlockState(p) != s) w.setBlockState(p, s, Block.NOTIFY_LISTENERS);
    }
}
