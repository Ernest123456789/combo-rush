package com.derevna230.comborush.campaign;

import com.derevna230.comborush.rift.EliteMobs;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Финальный босс «Режиссёр» в своей студии. Три фазы — три правила, как у босс-блайндов:
 * 1. «Мотор / Стоп» — на «СТОП!» замри.
 * 2. «Софиты» — стой в луче прожектора, он ползёт по сцене; в тени зрители тебя не видят и ты теряешь здоровье.
 * 3. «Финальный монтаж» — оба правила сразу, «СТОП!» чаще, на сцену выходят полихромные статисты.
 */
public final class DirectorBoss {
    private DirectorBoss() {}

    static final String QUEST = "act3_director";
    private static final String BOSS_TAG = "cr_director_boss", ADD_TAG = "cr_director_add";

    private static UUID boss = null;
    private static ServerWorld world = null;
    private static BlockPos center = null;
    private static int t = 0, absent = 0, phase = 1, clean = 0;
    private static double spotAng = 0;
    private static final Map<UUID, Vec3d> ANCHOR = new HashMap<>();
    private static final Set<UUID> SPOILED = new HashSet<>();
    private static final ServerBossBar BAR = new ServerBossBar(Text.literal("§c§lРЕЖИССЁР"), BossBar.Color.RED, BossBar.Style.NOTCHED_10);

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (boss == null || !entity.getUuid().equals(boss)) return;
            ServerPlayerEntity killer = source.getAttacker() instanceof ServerPlayerEntity sp ? sp : null;
            win(entity.getServer(), killer);
        });
    }

    public static boolean fighting() { return boss != null; }

    static void start(ServerWorld w, BlockPos c, BlockPos throne) {
        if (boss != null) return;
        world = w; center = c; t = 0; absent = 0; phase = 1; spotAng = 0;
        ANCHOR.clear(); SPOILED.clear();
        MobEntity mob = EntityType.EVOKER.create(w);
        if (mob == null) return;
        int n = Math.max(1, nearby().size());
        double hp = 300 + 150 * (n - 1);
        mob.refreshPositionAndAngles(throne.getX() + 0.5, throne.getY(), throne.getZ() + 0.5, 0, 0);
        mob.initialize(w, w.getLocalDifficulty(c), SpawnReason.EVENT, null);
        EntityAttributeInstance hpA = mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (hpA != null) hpA.setBaseValue(hp);
        mob.setHealth((float) hp);
        EntityAttributeInstance kb = mob.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
        if (kb != null) kb.setBaseValue(0.8);
        mob.setCustomName(Text.literal("§c§lРЕЖИССЁР"));
        mob.setCustomNameVisible(true);
        mob.setPersistent();
        mob.addCommandTag(BOSS_TAG);
        mob.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20 * 1200, 0, false, false));
        w.spawnEntityAndPassengers(mob);
        boss = mob.getUuid();
        w.spawnParticles(ParticleTypes.FLASH, mob.getX(), mob.getY() + 1, mob.getZ(), 1, 0, 0, 0, 0);
        w.playSound(null, c, com.derevna230.comborush.sound.ModSounds.BOSS_START, SoundCategory.HOSTILE, 2f, 0.9f);
        for (ServerPlayerEntity p : nearby()) {
            title(p, "§c§lМОТОР!", "§fФаза 1 · на «СТОП!» замри");
            p.sendMessage(Text.literal("§c[Режиссёр] §fВсе по местам. Это последний дубль сезона — и он будет моим."), false);
        }
        BAR.setPercent(1f);
    }

    static void tick(MinecraftServer server) {
        CampaignState st = CampaignState.get(server);
        if (boss == null) {
            if (st.studio != null && ++clean % 100 == 0) {
                ServerWorld w = server.getOverworld();
                BlockPos c = st.studio;
                if (w.isChunkLoaded(c.getX() >> 4, c.getZ() >> 4))
                    w.getEntitiesByClass(MobEntity.class, new Box(c).expand(48),
                        m -> m.getCommandTags().contains(BOSS_TAG) || m.getCommandTags().contains(ADD_TAG)).forEach(Entity::discard);
            }
            return;
        }
        if (st.done.contains(QUEST)) { stop("", true); return; }
        Entity e = world.getEntity(boss);
        List<ServerPlayerEntity> ps = nearby();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) if (ps.contains(p)) BAR.addPlayer(p); else BAR.removePlayer(p);
        if (ps.isEmpty()) {
            if (++absent > 600) stop("§7Съёмка сорвана: в студии никого. Режиссёр вернулся на трон.", true);
            return;
        }
        absent = 0;
        if (!(e instanceof MobEntity mob) || !mob.isAlive()) {
            if (e == null && ++absent > 100) stop("§7Режиссёр исчез со сцены. Попробуйте снова.", true);
            return;
        }
        Vec3d cc = Vec3d.ofBottomCenter(center);
        if (mob.squaredDistanceTo(cc) > 14 * 14) {
            world.spawnParticles(ParticleTypes.REVERSE_PORTAL, mob.getX(), mob.getBodyY(0.5), mob.getZ(), 30, 0.3, 0.6, 0.3, 0.1);
            mob.requestTeleport(cc.x, cc.y, cc.z);
        }
        float hp = mob.getHealth() / mob.getMaxHealth();
        BAR.setPercent(Math.max(0f, hp));
        int want = hp > 0.66f ? 1 : hp > 0.33f ? 2 : 3;
        if (want > phase) enterPhase(want, mob, ps);

        boolean stopRule = phase != 2, spotRule = phase >= 2;
        int cycle = phase == 3 ? 140 : 200, warnAt = phase == 3 ? 90 : 130, stopAt = phase == 3 ? 105 : 150;
        String base = "§c§lРЕЖИССЁР §f· " + (phase == 1 ? "фаза 1" : phase == 2 ? "§eСОФИТЫ" : "§dФИНАЛЬНЫЙ МОНТАЖ");
        if (stopRule) {
            int ph = t % cycle;
            if (ph == 0) {
                BAR.setName(Text.literal(base + " §7· §aМОТОР"));
                mob.setAiDisabled(false);
                if (t > 0) for (ServerPlayerEntity p : ps) p.sendMessage(Text.literal("§a§lМОТОР!"), true);
            } else if (ph == warnAt) {
                world.playSound(null, center, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.HOSTILE, 2f, 0.7f);
                for (ServerPlayerEntity p : ps) p.sendMessage(Text.literal("§e§lВнимание… сейчас будет «СТОП!»"), true);
            } else if (ph == stopAt) {
                BAR.setName(Text.literal(base + " §7· §c§lСТОП!"));
                mob.setAiDisabled(true);
                mob.setVelocity(Vec3d.ZERO);
                world.playSound(null, center, SoundEvents.BLOCK_NOTE_BLOCK_SNARE.value(), SoundCategory.HOSTILE, 2.5f, 0.5f);
                ANCHOR.clear(); SPOILED.clear();
                for (ServerPlayerEntity p : ps) { ANCHOR.put(p.getUuid(), p.getPos()); title(p, "§c§lСТОП!", "§7не двигайся"); }
            } else if (ph > stopAt + 4 && ph < cycle - 1) {
                for (ServerPlayerEntity p : ps) {
                    Vec3d a = ANCHOR.get(p.getUuid());
                    if (a == null || SPOILED.contains(p.getUuid())) continue;
                    double dx = p.getX() - a.x, dz = p.getZ() - a.z;
                    if (dx * dx + dz * dz > 0.25 || p.getY() - a.y > 0.7) spoil(p, mob);
                }
            } else if (ph == cycle - 1) {
                for (ServerPlayerEntity p : ps) {
                    if (!ANCHOR.containsKey(p.getUuid()) || SPOILED.contains(p.getUuid())) continue;
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 120, 0));
                    p.sendMessage(Text.literal("§a§lОтличный дубль! §7Сила на 6 с."), true);
                }
            }
        } else if (t % 40 == 0) {
            BAR.setName(Text.literal(base + " §7· держись в луче"));
            mob.setAiDisabled(false);
        }
        if (spotRule) spotlight(mob, ps);
        t++;
    }

    private static void enterPhase(int p, MobEntity mob, List<ServerPlayerEntity> ps) {
        phase = p;
        mob.setAiDisabled(false);
        world.playSound(null, center, com.derevna230.comborush.sound.ModSounds.BOSS_START, SoundCategory.HOSTILE, 2f, p == 2 ? 1.1f : 1.25f);
        world.spawnParticles(ParticleTypes.FLASH, mob.getX(), mob.getY() + 1, mob.getZ(), 1, 0, 0, 0, 0);
        if (p == 2) {
            for (ServerPlayerEntity pl : ps) {
                title(pl, "§e§lСОФИТЫ", "§fстой в луче прожектора");
                pl.sendMessage(Text.literal("§c[Режиссёр] §fСвет! Кто вне кадра — того не существует."), false);
            }
        } else {
            mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 600, 0, false, false));
            for (ServerPlayerEntity pl : ps) {
                title(pl, "§d§lФИНАЛЬНЫЙ МОНТАЖ", "§fсвет и «СТОП!» — вместе");
                pl.sendMessage(Text.literal("§c[Режиссёр] §fМонтаж! Склеим вас в последний кадр — навсегда."), false);
            }
            Random r = new Random();
            for (int i = 0; i < 1 + ps.size(); i++) {
                MobEntity m = (i % 2 == 0 ? EntityType.ZOMBIE : EntityType.SKELETON).create(world);
                if (m == null) continue;
                double a = r.nextDouble() * Math.PI * 2;
                m.refreshPositionAndAngles(center.getX() + 0.5 + Math.cos(a) * 4, center.getY(), center.getZ() + 0.5 + Math.sin(a) * 4, 0, 0);
                m.initialize(world, world.getLocalDifficulty(center), SpawnReason.EVENT, null);
                m.addCommandTag(ADD_TAG);
                m.setPersistent();
                world.spawnEntityAndPassengers(m);
                if (m instanceof HostileEntity h) EliteMobs.make(h, EliteMobs.Edition.POLY);
            }
        }
    }

    /** Луч прожектора ползёт по кругу; кто вне его — теряет здоровье */
    private static void spotlight(MobEntity mob, List<ServerPlayerEntity> ps) {
        spotAng += phase == 3 ? 0.012 : 0.008;
        double R = 2.6;
        double sx = center.getX() + 0.5 + Math.cos(spotAng) * 3.2, sz = center.getZ() + 0.5 + Math.sin(spotAng) * 3.2;
        double sy = center.getY() + 0.1;
        if (t % 4 == 0) {
            for (int k = 0; k < 28; k++) {
                double a = k * Math.PI * 2 / 28;
                world.spawnParticles(ParticleTypes.END_ROD, sx + Math.cos(a) * R, sy, sz + Math.sin(a) * R, 1, 0, 0, 0, 0);
            }
            world.spawnParticles(ParticleTypes.END_ROD, sx, sy + 6, sz, 2, 0.3, 3, 0.3, 0);
        }
        if (t % 20 != 0) return;
        for (ServerPlayerEntity p : ps) {
            double dx = p.getX() - sx, dz = p.getZ() - sz;
            if (dx * dx + dz * dz > R * R) {
                p.damage(world.getDamageSources().magic(), 2f);
                p.sendMessage(Text.literal("§6Ты вне кадра! §7Вернись в луч прожектора."), true);
            } else {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 30, 0, false, false));
            }
        }
    }

    private static void spoil(ServerPlayerEntity p, MobEntity mob) {
        SPOILED.add(p.getUuid());
        p.damage(world.getDamageSources().magic(), 5f);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
        mob.heal(15f);
        p.sendMessage(Text.literal("§c§lИспортил дубль! §7Режиссёр подлечился."), true);
        world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, p.getX(), p.getY() + 2, p.getZ(), 3, 0.3, 0.2, 0.3, 0);
        world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE, 1f, 1.2f);
    }

    private static void win(MinecraftServer server, ServerPlayerEntity killer) {
        if (server == null || world == null) return;
        ServerPlayerEntity who = killer;
        if (who == null) for (ServerPlayerEntity p : nearby()) { who = p; break; }
        Vec3d cc = Vec3d.ofBottomCenter(center);
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, cc.x, cc.y + 1, cc.z, 120, 2, 1.5, 2, 0.5);
        world.spawnParticles(ParticleTypes.FIREWORK, cc.x, cc.y + 2, cc.z, 100, 3, 2, 3, 0.2);
        world.playSound(null, center, com.derevna230.comborush.sound.ModSounds.MEDAL_PLATINUM, SoundCategory.PLAYERS, 2f, 1f);
        for (ServerPlayerEntity p : nearby()) title(p, "§6§lСНЯТО!", "§fСезон окончен. Режиссёр повержен");
        cleanupAdds();
        boss = null;
        BAR.clearPlayers();
        if (who != null) Campaign.trigger(who, "director", "", 1);
    }

    static void stop(String msg, boolean despawn) {
        if (world != null && boss != null && despawn) {
            Entity e = world.getEntity(boss);
            if (e != null) {
                world.spawnParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getBodyY(0.5), e.getZ(), 40, 0.4, 0.8, 0.4, 0.1);
                e.discard();
            }
        }
        if (!msg.isEmpty()) for (ServerPlayerEntity p : nearby()) p.sendMessage(Text.literal(msg), false);
        cleanupAdds();
        boss = null;
        BAR.clearPlayers();
    }

    private static List<ServerPlayerEntity> nearby() {
        List<ServerPlayerEntity> out = new ArrayList<>();
        if (world == null || center == null) return out;
        Vec3d cc = Vec3d.ofBottomCenter(center);
        for (ServerPlayerEntity p : world.getPlayers())
            if (!p.isSpectator() && p.isAlive() && p.squaredDistanceTo(cc) < 40 * 40) out.add(p);
        return out;
    }

    private static void cleanupAdds() {
        if (world == null || center == null) return;
        world.getEntitiesByClass(MobEntity.class, new Box(center).expand(48), m -> m.getCommandTags().contains(ADD_TAG)).forEach(Entity::discard);
    }

    private static void title(ServerPlayerEntity p, String a, String b) {
        p.networkHandler.sendPacket(new TitleFadeS2CPacket(3, 25, 8));
        p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(a)));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(b)));
    }
}
