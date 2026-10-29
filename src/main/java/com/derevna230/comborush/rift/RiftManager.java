package com.derevna230.comborush.rift;

import com.derevna230.comborush.cycle.CycleManager;
import com.derevna230.comborush.cycle.CyclePhase;
import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.item.ModItems;
import com.derevna230.comborush.score.ScoreEngine;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Разломы: во время события рядом с игроками открывается портал, из которого идут три волны мобов.
 * Закрыл разлом (перебил все волны) — бонусные очки, добыча и шанс на кейс. Не успел за 4 минуты — разлом схлопнется.
 */
public final class RiftManager {
    private RiftManager() {}

    static final String MOB_TAG = "cr_rift_mob";
    private static final int WAVES = 3;
    public static int LIFETIME = 20 * 240;                 // 4 минуты (config: rift_minutes)
    private static final int ACTIVATE_DIST = 22;           // волны начинаются, когда игрок подошёл
    public static int PER_EVENT = 2;                       // config: rifts_per_event (0–2)

    private static final List<Rift> RIFTS = new ArrayList<>();
    private static final List<Integer> SCHEDULE = new ArrayList<>();   // секунды события до открытия
    private static int eventTicks = 0;

    static final class Rift {
        final ServerWorld world; final BlockPos pos; final ServerBossBar bar;
        int age = 0, wave = 0, cooldown = 0;
        boolean active = false;
        /** Сюжетная «склейка»: не схлопывается по времени и в конце события */
        boolean story = false;
        String name = "§5§lРазлом";
        final List<UUID> mobs = new ArrayList<>();
        final Set<UUID> fighters = new HashSet<>();

        Rift(ServerWorld world, BlockPos pos) {
            this.world = world; this.pos = pos;
            this.bar = new ServerBossBar(Text.literal("§5§lРазлом §7— подойди ближе"), BossBar.Color.PURPLE, BossBar.Style.NOTCHED_6);
        }

        Rift(ServerWorld world, BlockPos pos, boolean story) {
            this(world, pos);
            this.story = story;
            if (story) { this.name = "§d§lСклейка"; this.bar.setColor(BossBar.Color.PINK); this.bar.setName(Text.literal(name + " §7— подойди ближе")); }
        }

        Vec3d center() { return Vec3d.ofBottomCenter(pos).add(0, 1.6, 0); }
    }

    // ------------------------------------------------------------ события цикла
    public static void onEventStart(MinecraftServer server) {
        SCHEDULE.clear();
        eventTicks = 0;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (PER_EVENT <= 0) return;
        SCHEDULE.add(20 + r.nextInt(40));
        if (PER_EVENT > 1) SCHEDULE.add(140 + r.nextInt(50));
    }

    public static void onEventEnd(MinecraftServer server) {
        for (Rift rf : new ArrayList<>(RIFTS)) if (!rf.story) collapse(rf, "§7Событие закончилось — разлом схлопнулся.");
        SCHEDULE.clear();
    }

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!entity.getCommandTags().contains(MOB_TAG)) return;
            if (source.getAttacker() instanceof ServerPlayerEntity p)
                for (Rift rf : RIFTS) if (rf.mobs.contains(entity.getUuid())) rf.fighters.add(p.getUuid());
        });
    }

    // ------------------------------------------------------------ тик
    public static void tick(MinecraftServer server) {
        if (CycleManager.phase() == CyclePhase.EVENT) {
            eventTicks++;
            if (eventTicks % 20 == 0 && !SCHEDULE.isEmpty() && eventTicks / 20 >= SCHEDULE.get(0)) {
                SCHEDULE.remove(0);
                List<ServerPlayerEntity> ps = new ArrayList<>(server.getPlayerManager().getPlayerList());
                ps.removeIf(p -> p.isSpectator() || !p.isAlive());
                if (!ps.isEmpty()) open(ps.get(ThreadLocalRandom.current().nextInt(ps.size())));
            }
        }
        for (Rift rf : new ArrayList<>(RIFTS)) tickRift(server, rf);
    }

    /** Открыть разлом рядом с игроком (также команда /cr rift) */
    public static boolean open(ServerPlayerEntity near) {
        ServerWorld w = near.getServerWorld();
        BlockPos pos = findSpot(w, near.getBlockPos());
        if (pos == null) return false;
        Rift rf = new Rift(w, pos);
        RIFTS.add(rf);
        w.playSound(null, pos, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.5f, 1.4f);
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (p.squaredDistanceTo(Vec3d.ofCenter(pos)) > 160 * 160) continue;
            p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("§5§lРАЗЛОМ ОТКРЫЛСЯ")));
            p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("§d" + direction(p, pos) + " · закрой его, пока он не схлопнулся")));
            p.sendMessage(Text.literal("§5[Разлом] §fОткрылся §d" + direction(p, pos) + " §7(" + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                + "). §fПеребей 3 волны — получишь бонус и добычу!"), false);
        }
        return true;
    }

    /** Сюжетная склейка (кампания, Акт I). Живёт, пока её не закроют */
    public static boolean openStory(ServerPlayerEntity near) {
        if (hasStory()) return true;
        ServerWorld w = near.getServerWorld();
        BlockPos pos = findSpot(w, near.getBlockPos());
        if (pos == null) return false;
        RIFTS.add(new Rift(w, pos, true));
        w.playSound(null, pos, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.5f, 1.8f);
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (p.squaredDistanceTo(Vec3d.ofCenter(pos)) > 200 * 200) continue;
            p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("§d§lСКЛЕЙКА")));
            p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("§f" + direction(p, pos) + " · кадры расходятся по шву")));
        }
        return true;
    }

    public static boolean hasStory() { return RIFTS.stream().anyMatch(r -> r.story); }
    public static BlockPos storyPos() { return RIFTS.stream().filter(r -> r.story).map(r -> r.pos).findFirst().orElse(null); }

    private static String direction(ServerPlayerEntity p, BlockPos pos) {
        double dx = pos.getX() + 0.5 - p.getX(), dz = pos.getZ() + 0.5 - p.getZ();
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        double ang = Math.toDegrees(Math.atan2(dx, -dz));                 // 0 — север
        String[] dirs = {"к северу", "к северо-востоку", "к востоку", "к юго-востоку", "к югу", "к юго-западу", "к западу", "к северо-западу"};
        String d = dirs[(int) Math.round(((ang % 360) + 360) % 360 / 45.0) % 8];
        return "в " + dist + " бл. " + d;
    }

    /** Ровное место с воздухом над ним в 24–40 блоках от игрока */
    private static BlockPos findSpot(ServerWorld w, BlockPos around) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 30; attempt++) {
            double ang = r.nextDouble() * Math.PI * 2, dist = 24 + r.nextDouble() * 16;
            int x = around.getX() + (int) Math.round(Math.cos(ang) * dist), z = around.getZ() + (int) Math.round(Math.sin(ang) * dist);
            for (int y = around.getY() + 10; y >= around.getY() - 14; y--) {
                BlockPos ground = new BlockPos(x, y, z);
                BlockState st = w.getBlockState(ground);
                if (st.getCollisionShape(w, ground).isEmpty() || !st.getFluidState().isEmpty()) continue;
                BlockPos up = ground.up();
                if (w.isAir(up) && w.isAir(up.up()) && w.isAir(up.up(2)) && w.getFluidState(up).isEmpty()) return up;
                break;
            }
        }
        return null;
    }

    private static void tickRift(MinecraftServer server, Rift rf) {
        rf.age++;
        ServerWorld w = rf.world;
        Vec3d c = rf.center();

        // вид: кольцо портальных частиц, луч в небо, гул
        if (rf.age % 4 == 0) {
            double a0 = rf.age * 0.15;
            for (int i = 0; i < 10; i++) {
                double a = a0 + i * Math.PI / 5;
                w.spawnParticles(ParticleTypes.REVERSE_PORTAL, c.x + Math.cos(a) * 1.6, c.y + Math.sin(a) * 1.6, c.z, 1, 0, 0, 0, 0);
            }
            w.spawnParticles(ParticleTypes.PORTAL, c.x, c.y, c.z, 6, 0.4, 0.8, 0.4, 0.6);
        }
        if (rf.age % 10 == 0)
            for (int y = 0; y < 30; y += 2) w.spawnParticles(ParticleTypes.END_ROD, c.x, c.y + y, c.z, 1, 0.05, 0.2, 0.05, 0);
        if (rf.age % 60 == 0) w.playSound(null, rf.pos, SoundEvents.BLOCK_PORTAL_AMBIENT, SoundCategory.HOSTILE, 0.8f, 0.6f);

        // полоса босса для тех, кто рядом
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            boolean near = p.getServerWorld() == w && p.squaredDistanceTo(c) < 64 * 64;
            if (near) rf.bar.addPlayer(p); else rf.bar.removePlayer(p);
        }

        if (!rf.story && rf.age > LIFETIME) { collapse(rf, "§7Разлом схлопнулся — не успели закрыть."); return; }

        if (!rf.active) {
            int left = (LIFETIME - rf.age) / 20;
            if (rf.story) { rf.bar.setName(Text.literal(rf.name + " §7— подойди ближе")); rf.bar.setPercent(1f); }
            else {
                rf.bar.setName(Text.literal(rf.name + " §7— подойди ближе §8(" + left / 60 + ":" + String.format("%02d", left % 60) + ")"));
                rf.bar.setPercent(1f - rf.age / (float) LIFETIME);
            }
            for (ServerPlayerEntity p : w.getPlayers())
                if (!p.isSpectator() && p.squaredDistanceTo(c) < ACTIVATE_DIST * ACTIVATE_DIST) { rf.active = true; rf.cooldown = 40; break; }
            return;
        }

        rf.mobs.removeIf(id -> { Entity e = w.getEntity(id); return e == null || !e.isAlive(); });
        if (rf.wave > 0) {
            rf.bar.setName(Text.literal(rf.name + " §f— волна " + rf.wave + "/" + WAVES + " §7· врагов: " + rf.mobs.size()));
            rf.bar.setPercent(Math.max(0f, 1f - ((rf.wave - 1) + (rf.mobs.isEmpty() ? 1f : 0.5f)) / WAVES));
        }
        if (!rf.mobs.isEmpty()) return;
        if (rf.wave >= WAVES) { close(server, rf); return; }
        if (--rf.cooldown > 0) return;
        rf.wave++;
        spawnWave(rf);
        rf.cooldown = 60;
    }

    private static EntityType<?>[] pool(World w, int wave) {
        if (w.getRegistryKey() == World.NETHER) return wave < 3
            ? new EntityType<?>[]{EntityType.WITHER_SKELETON, EntityType.BLAZE, EntityType.ZOMBIFIED_PIGLIN}
            : new EntityType<?>[]{EntityType.WITHER_SKELETON, EntityType.BLAZE, EntityType.PIGLIN_BRUTE};
        if (w.getRegistryKey() == World.END) return new EntityType<?>[]{EntityType.ENDERMAN, EntityType.ENDERMITE};
        return switch (wave) {
            case 1 -> new EntityType<?>[]{EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER};
            case 2 -> new EntityType<?>[]{EntityType.HUSK, EntityType.STRAY, EntityType.SPIDER, EntityType.SKELETON};
            default -> new EntityType<?>[]{EntityType.VINDICATOR, EntityType.WITCH, EntityType.STRAY, EntityType.ZOMBIE};
        };
    }

    private static void spawnWave(Rift rf) {
        ServerWorld w = rf.world;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long players = w.getPlayers().stream().filter(p -> !p.isSpectator() && p.squaredDistanceTo(rf.center()) < 48 * 48).count();
        int count = 2 + rf.wave + (int) Math.max(0, players - 1);
        EntityType<?>[] pool = pool(w, rf.wave);
        boolean eliteDone = false;
        for (int i = 0; i < count; i++) {
            Entity e = pool[r.nextInt(pool.length)].create(w);
            if (!(e instanceof MobEntity mob)) continue;
            double a = r.nextDouble() * Math.PI * 2;
            mob.refreshPositionAndAngles(rf.pos.getX() + 0.5 + Math.cos(a) * 1.5, rf.pos.getY(), rf.pos.getZ() + 0.5 + Math.sin(a) * 1.5,
                r.nextFloat() * 360f, 0);
            mob.initialize(w, w.getLocalDifficulty(rf.pos), SpawnReason.EVENT, null);
            mob.setPersistent();
            mob.addCommandTag(MOB_TAG);
            w.spawnEntityAndPassengers(mob);
            if (mob instanceof HostileEntity h) {
                if (rf.wave == WAVES && !eliteDone) {            // в последней волне всегда есть элитный
                    EliteMobs.make(h, r.nextDouble() < 0.3 ? EliteMobs.Edition.POLY : EliteMobs.Edition.HOLO);
                    eliteDone = true;
                } else if (r.nextDouble() < 0.15) EliteMobs.make(h, EliteMobs.roll());
            }
            ServerPlayerEntity target = (ServerPlayerEntity) w.getClosestPlayer(mob, 40);
            if (target != null && !target.isCreative()) mob.setTarget(target);
            rf.mobs.add(mob.getUuid());
            w.spawnParticles(ParticleTypes.REVERSE_PORTAL, mob.getX(), mob.getBodyY(0.5), mob.getZ(), 20, 0.3, 0.6, 0.3, 0.2);
        }
        w.playSound(null, rf.pos, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.5f, 0.6f);
        for (ServerPlayerEntity p : w.getPlayers())
            if (p.squaredDistanceTo(rf.center()) < 64 * 64)
                p.sendMessage(Text.literal("§5Разлом: §fволна " + rf.wave + "/" + WAVES + "!"), true);
    }

    private static void close(MinecraftServer server, Rift rf) {
        ServerWorld w = rf.world;
        Vec3d c = rf.center();
        Set<UUID> who = new HashSet<>(rf.fighters);
        for (ServerPlayerEntity p : w.getPlayers()) if (!p.isSpectator() && p.squaredDistanceTo(c) < 40 * 40) who.add(p.getUuid());
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (UUID id : who) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p == null) continue;
            int eventNo = PlayerProfile.of(p).eventsDone() + 1;
            ScoreEngine.addBonus(p, ScoreEngine.bronzeTarget(eventNo) * 0.35, "RIFT", "Разлом закрыт");
            p.getInventory().offerOrDrop(new ItemStack(Items.DIAMOND, 1 + r.nextInt(2)));
            p.getInventory().offerOrDrop(new ItemStack(Items.ENDER_PEARL, 1 + r.nextInt(2)));
            double roll = r.nextDouble();
            if (roll < 0.06) give(p, "rare");
            else if (roll < 0.30) give(p, "common");
            p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(rf.story ? "§d§lШОВ ЗАШИТ" : "§d§lРАЗЛОМ ЗАКРЫТ")));
            p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("§fБонус к очкам и добыча — твои")));
            if (!rf.story) com.derevna230.comborush.campaign.Campaign.trigger(p, "rift", "", 1);
        }
        if (rf.story) com.derevna230.comborush.campaign.Campaign.onSpliceClosed(server, who);
        w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        w.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y, c.z, 60, 0.8, 1.0, 0.8, 0.5);
        w.playSound(null, rf.pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.2f, 1.0f);
        remove(rf);
    }

    private static void give(ServerPlayerEntity p, String caseId) {
        var it = ModItems.CASES.get(caseId);
        if (it == null) return;
        p.getInventory().offerOrDrop(new ItemStack(it));
        p.sendMessage(Text.literal("§d[Разлом] §fВыпал кейс: " + new ItemStack(it).getName().getString() + "!"), false);
    }

    private static void collapse(Rift rf, String msg) {
        ServerWorld w = rf.world;
        for (UUID id : rf.mobs) {
            Entity e = w.getEntity(id);
            if (e != null) {
                w.spawnParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getBodyY(0.5), e.getZ(), 15, 0.3, 0.5, 0.3, 0.1);
                e.discard();
            }
        }
        for (ServerPlayerEntity p : w.getPlayers()) if (p.squaredDistanceTo(rf.center()) < 64 * 64) p.sendMessage(Text.literal(msg), false);
        w.spawnParticles(ParticleTypes.SMOKE, rf.center().x, rf.center().y, rf.center().z, 40, 0.6, 0.8, 0.6, 0.05);
        w.playSound(null, rf.pos, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 1.2f, 0.7f);
        remove(rf);
    }

    private static void remove(Rift rf) {
        rf.bar.clearPlayers();
        RIFTS.remove(rf);
    }

    public static int count() { return RIFTS.size(); }
}
