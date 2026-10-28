package com.derevna230.comborush.campaign;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
 * Мини-босс Акта I «Помощник режиссёра» на съёмочной площадке.
 * Правило боя (как у босс-блайнда): «МОТОР!» — дерёмся; «СТОП!» — все замирают.
 * Кто сдвинулся во время «СТОП!», испортил дубль: урон, замедление, а босс лечится. Кто замер — получает Силу.
 */
public final class StageBoss {
    private StageBoss() {}

    static final String QUEST = "act1_stage";
    private static final String BOSS_TAG = "cr_stage_boss", ADD_TAG = "cr_stage_add";
    private static final int CYCLE = 200, WARN_AT = 130, STOP_AT = 150;   // 10 с: 6.5 мотор, 1 внимание, 2.5 стоп

    private static UUID boss = null;
    private static ServerWorld world = null;
    private static BlockPos center = null;
    private static int t = 0, absent = 0, cleanTimer = 0;
    private static boolean add66 = false, add33 = false;
    private static final Map<UUID, Vec3d> ANCHOR = new HashMap<>();
    private static final Set<UUID> SPOILED = new HashSet<>();
    private static final Set<UUID> FIGHTERS = new HashSet<>();
    private static final ServerBossBar BAR = new ServerBossBar(Text.literal("§6Помощник режиссёра"), BossBar.Color.YELLOW, BossBar.Style.NOTCHED_10);

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (boss == null || !entity.getUuid().equals(boss)) return;
            ServerPlayerEntity killer = source.getAttacker() instanceof ServerPlayerEntity sp ? sp : null;
            win(entity.getServer(), killer);
        });
    }

    public static boolean fighting() { return boss != null; }

    public static void tick(MinecraftServer server) {
        CampaignState st = CampaignState.get(server);
        if (boss != null && (st.done.contains(QUEST) || st.stage == null)) { stopFight("", false); return; }
        if (st.stage == null || st.done.contains(QUEST)) return;
        ServerWorld w = server.getOverworld();
        BlockPos c = st.stage;

        if (boss == null) {
            // осиротевший босс после перезапуска сервера
            if (++cleanTimer % 100 == 0 && w.isChunkLoaded(c.getX() >> 4, c.getZ() >> 4))
                w.getEntitiesByClass(MobEntity.class, new Box(c).expand(48),
                    m -> m.getCommandTags().contains(BOSS_TAG) || m.getCommandTags().contains(ADD_TAG)).forEach(Entity::discard);
            if (server.getTicks() % 10 != 0) return;
            for (ServerPlayerEntity p : w.getPlayers()) {
                if (p.isSpectator() || !Campaign.isActive(p, QUEST)) continue;
                double dx = p.getX() - (c.getX() + 0.5), dz = p.getZ() - (c.getZ() + 0.5);
                if (dx * dx + dz * dz < 3.5 * 3.5 && Math.abs(p.getY() - c.getY()) < 3) { start(w, c); return; }
            }
            return;
        }
        fightTick(server);
    }

    private static List<ServerPlayerEntity> nearby() {
        List<ServerPlayerEntity> out = new ArrayList<>();
        if (world == null || center == null) return out;
        Vec3d cc = Vec3d.ofBottomCenter(center);
        for (ServerPlayerEntity p : world.getPlayers())
            if (!p.isSpectator() && p.isAlive() && p.squaredDistanceTo(cc) < 40 * 40) out.add(p);
        return out;
    }

    private static void start(ServerWorld w, BlockPos c) {
        world = w; center = c; t = 0; absent = 0; add66 = add33 = false;
        ANCHOR.clear(); SPOILED.clear(); FIGHTERS.clear();
        MobEntity mob = EntityType.VINDICATOR.create(w);
        if (mob == null) return;
        int n = Math.max(1, nearby().size());
        double hp = 140 + 70 * (n - 1);
        mob.refreshPositionAndAngles(c.getX() + 0.5, c.getY(), c.getZ() - 2.5, 0, 0);
        mob.initialize(w, w.getLocalDifficulty(c), SpawnReason.EVENT, null);
        EntityAttributeInstance hpA = mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (hpA != null) hpA.setBaseValue(hp);
        mob.setHealth((float) hp);
        EntityAttributeInstance kb = mob.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
        if (kb != null) kb.setBaseValue(0.6);
        mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        mob.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0f);
        mob.setCustomName(Text.literal("§6§lПомощник режиссёра"));
        mob.setCustomNameVisible(true);
        mob.setPersistent();
        mob.addCommandTag(BOSS_TAG);
        mob.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20 * 600, 0, false, false));
        w.spawnEntityAndPassengers(mob);
        boss = mob.getUuid();
        w.spawnParticles(ParticleTypes.FLASH, mob.getX(), mob.getY() + 1, mob.getZ(), 1, 0, 0, 0, 0);
        w.playSound(null, c, SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 1.5f, 1.2f);
        for (ServerPlayerEntity p : nearby()) {
            title(p, "§6§lМОТОР!", "§fЗамирай, когда Помощник кричит «СТОП!»");
            p.sendMessage(Text.literal("§6[Помощник режиссёра] §fТишина на площадке! Дубль первый. §7Правило: на «§cСТОП!§7» не двигайся — "
                + "кто сдвинется, испортит дубль. Кто замрёт — получит §aСилу§7."), false);
        }
        BAR.setPercent(1f);
    }

    private static void fightTick(MinecraftServer server) {
        Entity e = world.getEntity(boss);
        List<ServerPlayerEntity> ps = nearby();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) if (ps.contains(p)) BAR.addPlayer(p); else BAR.removePlayer(p);
        if (ps.isEmpty()) {
            if (++absent > 600) stopFight("§7Съёмка сорвана: на площадке никого. Возвращайтесь — снимем заново.", true);
            return;
        }
        absent = 0;
        if (!(e instanceof MobEntity mob) || !mob.isAlive()) {
            if (e == null && ++absent > 100) stopFight("§7Помощник режиссёра ушёл со сцены. Начнём заново.", true);
            return;
        }
        for (ServerPlayerEntity p : ps) FIGHTERS.add(p.getUuid());

        // не уходить далеко со сцены
        Vec3d cc = Vec3d.ofBottomCenter(center);
        if (mob.squaredDistanceTo(cc) > 16 * 16) {
            world.spawnParticles(ParticleTypes.POOF, mob.getX(), mob.getBodyY(0.5), mob.getZ(), 20, 0.3, 0.6, 0.3, 0.05);
            mob.requestTeleport(cc.x, cc.y, cc.z);
        }
        float hpPct = mob.getHealth() / mob.getMaxHealth();
        BAR.setPercent(Math.max(0f, hpPct));
        if (!add66 && hpPct < 0.66f) { add66 = true; extras(ps.size(), EntityType.ZOMBIE); }
        if (!add33 && hpPct < 0.33f) { add33 = true; extras(ps.size(), EntityType.SKELETON); }

        int ph = t % CYCLE;
        if (ph == 0) {
            BAR.setName(Text.literal("§6§lПомощник режиссёра §f· §aМОТОР"));
            BAR.setColor(BossBar.Color.YELLOW);
            mob.setAiDisabled(false);
            if (t > 0) for (ServerPlayerEntity p : ps) title(p, "§a§lМОТОР!", "");
        } else if (ph == WARN_AT) {
            BAR.setName(Text.literal("§6§lПомощник режиссёра §f· §eвнимание…"));
            world.playSound(null, center, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.HOSTILE, 2f, 0.7f);
            for (ServerPlayerEntity p : ps) p.sendMessage(Text.literal("§e§lВнимание… сейчас будет «СТОП!»"), true);
        } else if (ph == STOP_AT) {
            BAR.setName(Text.literal("§6§lПомощник режиссёра §f· §c§lСТОП!"));
            BAR.setColor(BossBar.Color.RED);
            mob.setAiDisabled(true);
            mob.setVelocity(Vec3d.ZERO);
            world.playSound(null, center, SoundEvents.BLOCK_NOTE_BLOCK_SNARE.value(), SoundCategory.HOSTILE, 2.5f, 0.5f);
            world.playSound(null, center, SoundEvents.ENTITY_VINDICATOR_CELEBRATE, SoundCategory.HOSTILE, 1.5f, 0.8f);
            ANCHOR.clear(); SPOILED.clear();
            for (ServerPlayerEntity p : ps) { ANCHOR.put(p.getUuid(), p.getPos()); title(p, "§c§lСТОП!", "§7не двигайся"); }
        } else if (ph > STOP_AT + 4 && ph < CYCLE - 1) {
            // первые 4 тика — на реакцию
            for (ServerPlayerEntity p : ps) {
                Vec3d a = ANCHOR.get(p.getUuid());
                if (a == null || SPOILED.contains(p.getUuid())) continue;
                double dx = p.getX() - a.x, dz = p.getZ() - a.z;
                if (dx * dx + dz * dz > 0.5 * 0.5 || p.getY() - a.y > 0.7) spoil(p, mob);
            }
        } else if (ph == CYCLE - 1) {
            for (ServerPlayerEntity p : ps) {
                if (!ANCHOR.containsKey(p.getUuid()) || SPOILED.contains(p.getUuid())) continue;
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 120, 0));
                p.sendMessage(Text.literal("§a§lОтличный дубль! §7Сила на 6 с."), true);
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 10, 0.4, 0.5, 0.4, 0);
            }
        }
        t++;
    }

    private static void spoil(ServerPlayerEntity p, MobEntity mob) {
        SPOILED.add(p.getUuid());
        p.damage(world.getDamageSources().magic(), 4f);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
        mob.heal(10f);
        p.sendMessage(Text.literal("§c§lИспортил дубль! §7Помощник подлечился."), true);
        world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, p.getX(), p.getY() + 2, p.getZ(), 3, 0.3, 0.2, 0.3, 0);
        world.spawnParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + 2.2, mob.getZ(), 4, 0.4, 0.3, 0.4, 0);
        world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.HOSTILE, 1f, 0.6f);
    }

    private static void extras(int players, EntityType<? extends MobEntity> type) {
        int count = 2 + players;
        Random r = new Random();
        for (int i = 0; i < count; i++) {
            MobEntity m = type.create(world);
            if (m == null) continue;
            double a = r.nextDouble() * Math.PI * 2;
            m.refreshPositionAndAngles(center.getX() + 0.5 + Math.cos(a) * 3.5, center.getY(), center.getZ() + 0.5 + Math.sin(a) * 3.5, 0, 0);
            m.initialize(world, world.getLocalDifficulty(center), SpawnReason.EVENT, null);
            m.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            m.setEquipmentDropChance(EquipmentSlot.HEAD, 0f);
            m.setCustomName(Text.literal("§7Статист"));
            m.addCommandTag(ADD_TAG);
            m.setPersistent();
            world.spawnEntityAndPassengers(m);
            world.spawnParticles(ParticleTypes.POOF, m.getX(), m.getY() + 1, m.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
        }
        for (ServerPlayerEntity p : nearby())
            p.sendMessage(Text.literal("§6[Помощник режиссёра] §fСтатисты, на площадку!"), false);
    }

    private static void win(MinecraftServer server, ServerPlayerEntity killer) {
        if (server == null || world == null) return;
        ServerPlayerEntity who = killer;
        if (who == null) for (ServerPlayerEntity p : nearby()) { who = p; break; }
        Vec3d cc = Vec3d.ofBottomCenter(center);
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, cc.x, cc.y + 1, cc.z, 80, 1.5, 1, 1.5, 0.4);
        world.spawnParticles(ParticleTypes.FIREWORK, cc.x, cc.y + 2, cc.z, 60, 2, 1.5, 2, 0.15);
        world.playSound(null, center, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.5f, 1f);
        for (ServerPlayerEntity p : nearby()) title(p, "§6§lСНЯТО!", "§fПервый акт сыгран");
        cleanupAdds();
        boss = null;
        BAR.clearPlayers();
        if (who != null) Campaign.trigger(who, "stage", "", 1);
    }

    private static void stopFight(String msg, boolean despawn) {
        if (world != null && boss != null && despawn) {
            Entity e = world.getEntity(boss);
            if (e != null) {
                world.spawnParticles(ParticleTypes.POOF, e.getX(), e.getBodyY(0.5), e.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
                e.discard();
            }
        }
        if (!msg.isEmpty()) for (ServerPlayerEntity p : nearby()) p.sendMessage(Text.literal(msg), false);
        cleanupAdds();
        boss = null;
        BAR.clearPlayers();
    }

    private static void cleanupAdds() {
        if (world == null || center == null) return;
        world.getEntitiesByClass(MobEntity.class, new Box(center).expand(48), m -> m.getCommandTags().contains(ADD_TAG)).forEach(m -> {
            world.spawnParticles(ParticleTypes.POOF, m.getX(), m.getBodyY(0.5), m.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
            m.discard();
        });
    }

    private static void title(ServerPlayerEntity p, String t1, String t2) {
        p.networkHandler.sendPacket(new TitleFadeS2CPacket(3, 20, 6));
        p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(t1)));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(t2)));
    }
}
