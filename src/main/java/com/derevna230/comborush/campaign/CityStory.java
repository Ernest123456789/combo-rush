package com.derevna230.comborush.campaign;

import com.derevna230.comborush.car.CarEntity;
import com.derevna230.comborush.universe.NightCity;
import com.derevna230.comborush.universe.Universes;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
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
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Сюжет Ночного города: задания «поехать», «убить», «добраться», «продержаться», «победить босса».
 * Типы целей (goal.type): enter | drive | kill | reach | hold | boss, а также talk с городскими персонажами
 * (nail, rita, lis, doc, kodo, term). Логика считает прогресс раз в секунду; убийства — по событию.
 * Заданиям нужны точки города (см. {@link #SITES}); постройки и монстры — в {@link CityInvasion}.
 */
public final class CityStory {
    private CityStory() {}

    static final Set<String> TYPES = Set.of("enter", "drive", "kill", "reach", "hold", "boss");
    static final int P = NightCity.PERIOD;

    // ================================================================ точки города
    static final Map<String, int[]> SITES = new LinkedHashMap<>();
    static final String[] POD_SITES = {"garage", "clinic", "taxi", "market", "sh1", "sh2", "sh3", "sh4"};
    static final String[] RELAY_SITES = {"r_n", "r_e", "r_s", "r_w"};

    private static void cross(String id, int i, int j) { SITES.put(id, new int[]{i * P + 8, 64, j * P + 8}); }

    static {
        cross("garage", 1, 1);
        cross("scrap", -2, 2);
        cross("clinic", 2, -1);
        cross("metro", -1, 3);
        cross("taxi", 3, 2);
        cross("market", -3, -1);
        cross("docks", 4, 4);
        cross("stadium", -2, -3);
        cross("r_n", 1, -3);
        cross("r_e", 4, 0);
        cross("r_s", 2, 4);
        cross("r_w", -3, 1);
        cross("sh1", 3, -2);
        cross("sh2", 0, -2);
        cross("sh3", -2, 0);
        cross("sh4", 0, 2);
        SITES.put("plaza", new int[]{44, 64, 70});
        SITES.put("deck", new int[]{CityInvasion.DECK_X, CityInvasion.DECK_Y + 1, CityInvasion.DECK_Z});
    }

    public static BlockPos site(String id) {
        int[] a = SITES.get(id);
        return a == null ? null : new BlockPos(a[0], a[1], a[2]);
    }

    // ================================================================ персонажи
    private record Npc(String id, String name, double x, double y, double z, int count, boolean invisible) {}

    private static final List<Npc> NPCS = new ArrayList<>();

    private static void npc(String id, String name, String site, double dx, double dz, int count) {
        BlockPos s = site(site);
        NPCS.add(new Npc(id, name, s.getX() + 0.5 + dx, s.getY(), s.getZ() + 0.5 + dz, count, false));
    }

    static {
        NPCS.add(new Npc("kodo", "§b§lПрофессор Кодо", 51.5, 64, 70.5, 1, false));
        npc("nail", "§6§lГвоздь", "garage", 0, 0, 1);
        npc("rita", "§d§lРита", "taxi", 0, 0, 1);
        npc("lis", "§a§lЛис", "market", 0, 0, 1);
        npc("doc", "§f§lДоктор Ли", "clinic", 0, 0, 1);
        for (String s : new String[]{"sh1", "sh2", "sh3", "sh4"}) npc("surv", "§7Выжившие", s, 1.5, 0, 2);
        NPCS.add(new Npc("term", "§b§lТерминал «Камертон»", 50.5, CityInvasion.DECK_Y + 1, 47.5, 1, true));
    }

    /** Есть ли такой городской персонаж (для цели talk) */
    static boolean isNpc(String id) {
        for (Npc n : NPCS) if (n.id.equals(id)) return true;
        return false;
    }

    // ================================================================ состояние в памяти
    private static final Map<String, Long> RUN = new HashMap<>();                 // reach с таймером: когда истекает (в тиках сервера)
    private static final Map<UUID, net.minecraft.util.math.Vec3d> DRIVE_LAST = new HashMap<>();
    private static final Map<UUID, Double> DRIVE_ACC = new HashMap<>();

    private static final class Hold { int held, absent, wave; boolean said; }
    private static final Map<String, Hold> HOLDS = new HashMap<>();

    private static final class Fight {
        UUID id; ServerBossBar bar; int t, absent, lost; boolean p66, p33; BossDef def; BlockPos home;
    }
    private static final Map<String, Fight> BOSSES = new HashMap<>();

    private static void clearMemory() {
        RUN.clear(); DRIVE_LAST.clear(); DRIVE_ACC.clear(); HOLDS.clear();
        for (Fight f : BOSSES.values()) f.bar.clearPlayers();
        BOSSES.clear();
    }

    // ================================================================ боссы
    private record BossDef(String name, EntityType<? extends MobEntity> type, BossBar.Color color, double hp, double hpPer, double scale,
                           double dmg, double speed, double kb, int addEvery, EntityType<? extends MobEntity> addType, int addCount, int addCap) {}

    private static final Map<String, BossDef> DEFS = new HashMap<>();

    static {
        DEFS.put("matka", new BossDef("§5§lМатка Роя", EntityType.SPIDER, BossBar.Color.PURPLE, 260, 90, 2.6, 8, 0.34, 0.5, 14, EntityType.CAVE_SPIDER, 4, 10));
        DEFS.put("pogrom", new BossDef("§6§lПогром", EntityType.HUSK, BossBar.Color.YELLOW, 320, 110, 2.3, 9, 0.27, 0.7, 16, EntityType.ZOMBIE, 3, 9));
        DEFS.put("podryv", new BossDef("§a§lПодрыв", EntityType.CREEPER, BossBar.Color.GREEN, 220, 80, 2.2, 6, 0.30, 0.5, 18, EntityType.CREEPER, 2, 6));
        DEFS.put("gate", new BossDef("§c§lПривратник Врат", EntityType.WITHER_SKELETON, BossBar.Color.RED, 700, 250, 2.3, 11, 0.30, 0.8, 16, EntityType.SKELETON, 3, 9));
    }

    // ================================================================ инициализация
    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> clearMemory());
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity.getWorld() instanceof ServerWorld w) || !w.getRegistryKey().equals(Universes.NIGHT_CITY)) return;
            MinecraftServer server = w.getServer();
            if (server == null || !Campaign.enabled()) return;
            for (String t : entity.getCommandTags()) {
                if (t.startsWith("cr_boss:")) { bossDied(server, w, entity, source, t.substring("cr_boss:".length())); return; }
            }
            if (source.getAttacker() instanceof ServerPlayerEntity killer && entity instanceof HostileEntity) onKill(killer, entity);
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
            String id = npcId(entity);
            if (id == null) return ActionResult.PASS;
            if (!world.isClient && player instanceof ServerPlayerEntity sp) talk(sp, id);
            return ActionResult.SUCCESS;
        });
    }

    private static String npcId(Entity e) {
        for (String t : e.getCommandTags()) if (t.startsWith("cr_npc:")) return t.substring("cr_npc:".length());
        return null;
    }

    // ================================================================ тик
    public static void tick(MinecraftServer server) {
        ServerWorld w = server.getWorld(Universes.NIGHT_CITY);
        if (w == null) return;
        CityInvasion.tick(server, w);
        int now = server.getTicks();
        if (now % 20 != 0) return;
        CityState cs = CityState.get(server);

        List<ServerPlayerEntity> here = new ArrayList<>();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            if (p.getServerWorld() == w) here.add(p);
        }
        // «вход в город»
        if (!here.isEmpty()) {
            for (QuestDef q : QuestRegistry.all()) {
                if (!q.goal.type.equals("enter")) continue;
                for (ServerPlayerEntity p : here)
                    if (Campaign.status(p, q) == Campaign.St.ACTIVE) Campaign.setProgress(p, q, q.count());
            }
        } else { DRIVE_LAST.clear(); }

        List<QuestDef> cq = new ArrayList<>();
        for (QuestDef q : QuestRegistry.all()) if (TYPES.contains(q.goal.type) && !q.goal.type.equals("enter")) cq.add(q);

        Map<String, List<ServerPlayerEntity>> holdZone = new HashMap<>();
        Map<String, List<ServerPlayerEntity>> bossZone = new HashMap<>();
        Set<String> holdActive = new HashSet<>(), bossActive = new HashSet<>();
        for (ServerPlayerEntity p : here) {
            double meters = driveDelta(p);
            for (QuestDef q : cq) {
                if (Campaign.status(p, q) != Campaign.St.ACTIVE) continue;
                briefing(p, q);
                switch (q.goal.type) {
                    case "drive" -> drive(p, q, meters);
                    case "reach" -> reach(p, q, cs, now);
                    case "hold" -> {
                        holdActive.add(q.id);
                        BlockPos s = site(q.goal.site);
                        if (s != null && inZone(p, s, q.goal.radius > 0 ? q.goal.radius : 14)) holdZone.computeIfAbsent(q.id, k -> new ArrayList<>()).add(p);
                    }
                    case "boss" -> {
                        bossActive.add(q.id);
                        BlockPos s = site(q.goal.site);
                        if (s != null && inZone(p, s, q.goal.radius > 0 ? q.goal.radius : 48)) bossZone.computeIfAbsent(q.id, k -> new ArrayList<>()).add(p);
                    }
                    default -> { }
                }
            }
        }
        // таймеры маршрутов
        for (Iterator<Map.Entry<String, Long>> it = RUN.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (now <= e.getValue()) continue;
            it.remove();
            QuestDef q = QuestRegistry.get(e.getKey());
            cs.visitedOf(e.getKey()).clear();
            cs.markDirty();
            for (ServerPlayerEntity p : here) {
                if (q == null || Campaign.status(p, q) != Campaign.St.ACTIVE) continue;
                p.sendMessage(Text.literal("§c§l⏱ Время вышло! §7Маршрут сорван — начните сначала."), false);
                Campaign.setProgress(p, q, 0);
            }
        }
        // обороны и боссы
        for (String id : holdActive) processHold(server, w, id, holdZone.getOrDefault(id, List.of()), here);
        for (Iterator<Map.Entry<String, Hold>> it = HOLDS.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (holdActive.contains(e.getKey())) continue;
            it.remove();
            discardTagged(w, CityInvasion.TAG_HOLD);
        }
        for (String id : bossActive) processBoss(server, w, id, bossZone.getOrDefault(id, List.of()));
        for (Iterator<Map.Entry<String, Fight>> it = BOSSES.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (bossActive.contains(e.getKey())) continue;
            if (++e.getValue().lost > 60) { endFight(w, e.getValue(), true); it.remove(); }
        }
        // персонажи и маяки
        if (now % 40 == 0) ensureNpcs(w, here);
        if (now % 40 == 0) beacons(w, here, cq);
    }

    private static boolean inZone(ServerPlayerEntity p, BlockPos s, int r) {
        double dx = p.getX() - (s.getX() + 0.5), dz = p.getZ() - (s.getZ() + 0.5);
        return dx * dx + dz * dz <= (double) r * r && Math.abs(p.getY() - s.getY()) < 16;
    }

    private static boolean drivingCar(ServerPlayerEntity p) {
        return p.getVehicle() instanceof CarEntity c && c.getControllingPassenger() == p;
    }

    // ================================================================ реплики-брифинги
    private static void briefing(ServerPlayerEntity p, QuestDef q) {
        if (q.lines_start.isEmpty()) return;
        PlayerCampaign pc = PlayerCampaign.of(p);
        String key = "cb_" + q.id;
        if (pc.flag(key) != 0) return;
        PlayerCampaign.set(p, pc.withFlag(key, 1));
        Campaign.say(p, q.speaker, q.lines_start);
        p.sendMessage(Text.literal("§b✦ Новая миссия: §f" + q.title + " §7— " + q.hint), false);
    }

    // ================================================================ езда
    private static double driveDelta(ServerPlayerEntity p) {
        if (!drivingCar(p)) { DRIVE_LAST.remove(p.getUuid()); return 0; }
        net.minecraft.util.math.Vec3d pos = p.getPos();
        net.minecraft.util.math.Vec3d last = DRIVE_LAST.put(p.getUuid(), pos);
        if (last == null) return 0;
        double d = Math.hypot(pos.x - last.x, pos.z - last.z);
        return d > 60 ? 0 : d;
    }

    private static void drive(ServerPlayerEntity p, QuestDef q, double meters) {
        if (meters <= 0) return;
        double acc = DRIVE_ACC.merge(p.getUuid(), meters, Double::sum);
        if (acc < 12) return;
        int add = (int) acc;
        DRIVE_ACC.put(p.getUuid(), acc - add);
        Campaign.setProgress(p, q, Campaign.progress(p, q) + add);
    }

    // ================================================================ «добраться»
    private static void reach(ServerPlayerEntity p, QuestDef q, CityState cs, int now) {
        QuestDef.Goal g = q.goal;
        if (g.sites.isEmpty()) return;
        if (g.car && !drivingCar(p)) return;
        Set<String> vis = cs.visitedOf(q.id);
        if (g.seconds > 0 && !vis.isEmpty() && !RUN.containsKey(q.id)) { vis.clear(); cs.markDirty(); }      // остаток после перезапуска
        int r = g.radius > 0 ? g.radius : 12;
        for (int i = 0; i < g.sites.size(); i++) {
            String sid = g.sites.get(i);
            if (vis.contains(sid)) continue;
            if (g.ordered && i != vis.size()) continue;
            BlockPos s = site(sid);
            if (s == null || !inZone(p, s, r)) continue;
            vis.add(sid);
            cs.markDirty();
            if (g.seconds > 0 && vis.size() == 1) {
                RUN.put(q.id, (long) now + g.seconds * 20L);
                p.sendMessage(Text.literal("§e⏱ Таймер запущен: §f" + g.seconds + " с на весь маршрут"), false);
            }
            if (i < g.msgs.size() && !g.msgs.get(i).isEmpty()) Campaign.say(p, q.speaker, List.of(g.msgs.get(i)));
            ServerWorld w = p.getServerWorld();
            w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 1.2f, 1.4f);
            w.spawnParticles(ParticleTypes.END_ROD, s.getX() + 0.5, s.getY() + 1.5, s.getZ() + 0.5, 40, 1.5, 1.5, 1.5, 0.08);
            if (vis.size() >= g.sites.size()) RUN.remove(q.id);
            Campaign.setProgress(p, q, vis.size());
            return;
        }
    }

    // ================================================================ убийства
    private static boolean mobMatches(String kind, Entity e) {
        return switch (kind) {
            case "zombie" -> e instanceof ZombieEntity;
            case "skeleton" -> e instanceof AbstractSkeletonEntity;
            case "spider" -> e instanceof SpiderEntity;
            case "creeper" -> e instanceof CreeperEntity;
            default -> e instanceof HostileEntity;
        };
    }

    private static void onKill(ServerPlayerEntity killer, Entity victim) {
        for (QuestDef q : QuestRegistry.all()) {
            if (!q.goal.type.equals("kill") || Campaign.status(killer, q) != Campaign.St.ACTIVE) continue;
            QuestDef.Goal g = q.goal;
            if (!mobMatches(g.mob.isEmpty() ? "any" : g.mob, victim)) continue;
            if (g.car && !drivingCar(killer)) continue;
            if (!g.site.isEmpty()) {
                BlockPos s = site(g.site);
                if (s == null) continue;
                double dx = victim.getX() - s.getX(), dz = victim.getZ() - s.getZ();
                int r = g.radius > 0 ? g.radius : 55;
                if (dx * dx + dz * dz > (double) r * r) continue;
            }
            Campaign.setProgress(killer, q, Campaign.progress(killer, q) + 1);
        }
    }

    // ================================================================ оборона
    private static void processHold(MinecraftServer server, ServerWorld w, String id, List<ServerPlayerEntity> zone, List<ServerPlayerEntity> here) {
        QuestDef q = QuestRegistry.get(id);
        if (q == null) return;
        Hold h = HOLDS.computeIfAbsent(id, k -> new Hold());
        BlockPos s = site(q.goal.site);
        if (s == null) return;
        if (zone.isEmpty()) {
            if (h.held > 0 && ++h.absent >= 20) {
                h.held = 0; h.absent = 0; h.wave = 0; h.said = false;
                discardTagged(w, CityInvasion.TAG_HOLD);
                for (ServerPlayerEntity p : here) if (Campaign.status(p, q) == Campaign.St.ACTIVE) {
                    p.sendMessage(Text.literal("§c§lОборона сорвана. §7Вернитесь на точку, чтобы начать заново."), false);
                    Campaign.setProgress(p, q, 0);
                    break;
                }
            }
            return;
        }
        h.absent = 0;
        h.held++;
        if (!h.said) {
            h.said = true;
            for (ServerPlayerEntity p : zone) p.sendMessage(Text.literal("§c§l⚠ Держитесь на точке! §7Монстры идут отовсюду."), false);
        }
        int n = zone.size();
        // волны: первая сразу, дальше каждые 10 секунд
        if (h.held == 1 || h.held % 10 == 0) {
            h.wave++;
            int alive = w.getEntitiesByClass(HostileEntity.class, new Box(s).expand(40, 12, 40), e -> e.getCommandTags().contains(CityInvasion.TAG_HOLD)).size();
            int want = Math.min(3 + n + h.wave / 2, 16 - alive);
            for (int i = 0; i < want; i++) spawnHoldMob(w, s, q.goal.mob, zone, q.goal.site.equals("deck"));
        }
        int count = q.count();
        int left = Math.max(0, count - h.held);
        if (left == count / 2) for (ServerPlayerEntity p : zone) p.sendMessage(Text.literal("§e✦ Половина позади! Ещё " + left + " с."), true);
        Campaign.setProgress(zone.get(0), q, Math.min(count, h.held));
    }

    private static void spawnHoldMob(ServerWorld w, BlockPos s, String kind, List<ServerPlayerEntity> zone, boolean deck) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        EntityType<? extends MobEntity> type;
        if (kind.equals("spiders")) type = r.nextInt(3) == 0 ? et(EntityType.CAVE_SPIDER) : et(EntityType.SPIDER);
        else if (kind.equals("creepers")) type = r.nextInt(4) == 0 ? et(EntityType.ZOMBIE) : et(EntityType.CREEPER);
        else if (kind.equals("undead")) type = r.nextInt(5) < 3 ? et(EntityType.ZOMBIE) : et(EntityType.SKELETON);
        else type = CityInvasion.pick(r);
        double x, y, z;
        if (deck) {
            double a = r.nextDouble() * Math.PI * 2, d = 6 + r.nextDouble() * 2;
            x = CityInvasion.DECK_X + Math.cos(a) * d; z = CityInvasion.DECK_Z + Math.sin(a) * d; y = CityInvasion.DECK_Y + 1;
        } else {
            BlockPos at = CityInvasion.streetSpot(w, s.getX() + 0.5, s.getZ() + 0.5, 14, 24, false);
            if (at == null) return;
            x = at.getX() + 0.5; y = at.getY(); z = at.getZ() + 0.5;
        }
        MobEntity m = CityInvasion.spawn(w, type, x, y, z, CityInvasion.TAG_CITY, CityInvasion.TAG_HOLD);
        if (m == null) return;
        m.setTarget(zone.get(r.nextInt(zone.size())));
        w.spawnParticles(ParticleTypes.REVERSE_PORTAL, x, y + 1, z, 16, 0.3, 0.7, 0.3, 0.1);
    }

    private static EntityType<? extends MobEntity> et(EntityType<? extends MobEntity> t) { return t; }

    static void discardTagged(ServerWorld w, String tag) {
        for (HostileEntity e : w.getEntitiesByClass(HostileEntity.class, new Box(-420, -64, -420, 520, 330, 520), m -> m.getCommandTags().contains(tag))) {
            w.spawnParticles(ParticleTypes.POOF, e.getX(), e.getBodyY(0.5), e.getZ(), 8, 0.3, 0.5, 0.3, 0.03);
            e.discard();
        }
    }

    // ================================================================ боссы: бой
    private static void processBoss(MinecraftServer server, ServerWorld w, String id, List<ServerPlayerEntity> zone) {
        QuestDef q = QuestRegistry.get(id);
        if (q == null) return;
        BossDef def = DEFS.get(q.goal.mob);
        BlockPos site = site(q.goal.site);
        if (def == null || site == null) return;
        Fight f = BOSSES.get(id);
        if (f == null) {
            if (zone.isEmpty()) return;
            boolean close = false;
            int rr = q.goal.site.equals("deck") ? 14 : 30;
            for (ServerPlayerEntity p : zone) if (inZone(p, site, rr)) { close = true; break; }
            if (close) startFight(w, id, q, def, site, zone);
            return;
        }
        f.lost = 0;
        fightTick(w, q, f, zone);
    }

    private static void startFight(ServerWorld w, String id, QuestDef q, BossDef def, BlockPos site, List<ServerPlayerEntity> zone) {
        String tag = "cr_boss:" + id;
        for (HostileEntity e : w.getEntitiesByClass(HostileEntity.class, new Box(site).expand(90, 40, 90), m -> m.getCommandTags().contains(tag) || m.getCommandTags().contains(CityInvasion.TAG_ADD)))
            e.discard();
        BlockPos at = bossSpawn(w, id, site);
        MobEntity mob = def.type.create(w);
        if (mob == null) return;
        int n = Math.max(1, zone.size());
        double hp = def.hp + def.hpPer * (n - 1);
        mob.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        mob.initialize(w, w.getLocalDifficulty(at), net.minecraft.entity.SpawnReason.EVENT, null);
        attr(mob, EntityAttributes.GENERIC_MAX_HEALTH, hp);
        mob.setHealth((float) hp);
        attr(mob, EntityAttributes.GENERIC_SCALE, def.scale);
        attr(mob, EntityAttributes.GENERIC_ATTACK_DAMAGE, def.dmg);
        attr(mob, EntityAttributes.GENERIC_MOVEMENT_SPEED, def.speed);
        attr(mob, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, def.kb);
        attr(mob, EntityAttributes.GENERIC_FOLLOW_RANGE, 48);
        switch (q.goal.mob) {
            case "pogrom" -> {
                equip(mob, EquipmentSlot.HEAD, Items.IRON_HELMET);
                equip(mob, EquipmentSlot.CHEST, Items.IRON_CHESTPLATE);
            }
            case "gate" -> {
                equip(mob, EquipmentSlot.HEAD, Items.NETHERITE_HELMET);
                equip(mob, EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD);
            }
            case "podryv" -> {
                NbtCompound nbt = new NbtCompound();
                mob.writeNbt(nbt);
                nbt.putShort("Fuse", (short) 32767);                     // сам не взрывается — только по нашему сигналу
                nbt.putByte("ExplosionRadius", (byte) 1);
                nbt.putBoolean("powered", true);
                mob.readNbt(nbt);
            }
            default -> { }
        }
        mob.setCustomName(Text.literal(def.name));
        mob.setCustomNameVisible(true);
        mob.setPersistent();
        mob.addCommandTag(tag);
        mob.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20 * 1200, 0, false, false));
        w.spawnEntityAndPassengers(mob);

        Fight f = new Fight();
        f.id = mob.getUuid();
        f.def = def;
        f.home = site;
        f.bar = new ServerBossBar(Text.literal(def.name), def.color, BossBar.Style.NOTCHED_10);
        f.bar.setPercent(1f);
        BOSSES.put(id, f);
        w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, mob.getX(), mob.getY() + 1, mob.getZ(), 1, 0, 0, 0, 0);
        w.playSound(null, at, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.2f, 1.1f);
        for (ServerPlayerEntity p : zone) {
            title(p, def.name, "§7Бой начался");
            p.sendMessage(Text.literal("§c⚔ " + def.name + " §7— " + bossIntro(q.goal.mob)), false);
        }
    }

    private static String bossIntro(String boss) {
        return switch (boss) {
            case "matka" -> "гнездо проснулось. Она плюётся паутиной и зовёт детёнышей.";
            case "pogrom" -> "бригадир погромщиков. Бьёт по земле — не стойте вплотную.";
            case "podryv" -> "заряженный крипер-гигант. Сам не взорвётся, но по сигналу рванёт всё вокруг. Смотрите на частицы!";
            case "gate" -> "страж Чёрных Врат. Чем меньше у него сил, тем больше слуг из Врат.";
            default -> "держитесь вместе.";
        };
    }

    private static BlockPos bossSpawn(ServerWorld w, String id, BlockPos site) {
        if (site.getY() > 200) return new BlockPos(CityInvasion.DECK_X - 6, CityInvasion.DECK_Y + 1, CityInvasion.DECK_Z + 4);
        BlockPos[] tries = {site.add(6, 0, 0), site.add(-6, 0, 0), site.add(0, 0, 6), site.add(0, 0, -6), site};
        for (BlockPos t : tries) w.getChunk(t);
        for (BlockPos t : tries) if (w.getBlockState(t).isAir() && w.getBlockState(t.up(2)).isAir()) return t;
        return site;
    }

    private static void attr(MobEntity m, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> a, double v) {
        EntityAttributeInstance i = m.getAttributeInstance(a);
        if (i != null) i.setBaseValue(v);
    }

    private static void equip(MobEntity m, EquipmentSlot slot, Item item) {
        m.equipStack(slot, new ItemStack(item));
        m.setEquipmentDropChance(slot, 0f);
    }

    private static void fightTick(ServerWorld w, QuestDef q, Fight f, List<ServerPlayerEntity> zone) {
        Entity e = w.getEntity(f.id);
        for (ServerPlayerEntity p : w.getPlayers()) if (zone.contains(p)) f.bar.addPlayer(p); else f.bar.removePlayer(p);
        if (!(e instanceof MobEntity mob) || !mob.isAlive()) {
            if (e == null && ++f.absent > 12) { endFight(w, f, true); BOSSES.remove(q.id); }
            return;
        }
        f.absent = 0;
        if (zone.isEmpty()) {
            if (++f.lost > 60) { sayAll(w, "§7Босс ушёл в тень: на точке никого. Вернитесь — бой начнётся заново."); endFight(w, f, true); BOSSES.remove(q.id); }
            return;
        }
        f.lost = 0;
        f.t++;
        float pct = Math.max(0f, mob.getHealth() / mob.getMaxHealth());
        f.bar.setPercent(pct);
        // не отпускать далеко от точки
        double dx = mob.getX() - (f.home.getX() + 0.5), dz = mob.getZ() - (f.home.getZ() + 0.5);
        if (dx * dx + dz * dz > 44 * 44 || (f.home.getY() > 200 && mob.getY() < f.home.getY() - 4)) {
            BlockPos back = bossSpawn(w, q.id, f.home);
            mob.requestTeleport(back.getX() + 0.5, back.getY(), back.getZ() + 0.5);
        }
        // помощники
        BossDef d = f.def;
        if (f.t % d.addEvery == 0) summon(w, mob, d.addType, d.addCount, d.addCap, zone);
        if (!f.p66 && pct < 0.66f) { f.p66 = true; phase(w, q, mob, zone, 1); }
        if (!f.p33 && pct < 0.33f) { f.p33 = true; phase(w, q, mob, zone, 2); }
        // особые приёмы
        switch (q.goal.mob) {
            case "matka" -> { if (f.t % 10 == 0) webSpit(w, mob, zone); }
            case "pogrom" -> { if (f.t % 12 == 0) shockwave(w, mob, zone); }
            case "podryv" -> blast(w, mob, f, zone);
            case "gate" -> { if (f.t % 14 == 0) gateCall(w, mob, zone); }
            default -> { }
        }
    }

    private static void summon(ServerWorld w, MobEntity boss, EntityType<? extends MobEntity> type, int count, int cap, List<ServerPlayerEntity> zone) {
        int alive = w.getEntitiesByClass(HostileEntity.class, boss.getBoundingBox().expand(40), e -> e.getCommandTags().contains(CityInvasion.TAG_ADD)).size();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < count && alive + i < cap; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double x = boss.getX() + Math.cos(a) * 4, z = boss.getZ() + Math.sin(a) * 4;
            MobEntity m = CityInvasion.spawn(w, type, x, boss.getY(), z, CityInvasion.TAG_CITY, CityInvasion.TAG_ADD);
            if (m == null) continue;
            m.setTarget(zone.get(r.nextInt(zone.size())));
            w.spawnParticles(ParticleTypes.REVERSE_PORTAL, x, boss.getY() + 1, z, 14, 0.3, 0.6, 0.3, 0.1);
        }
    }

    private static void phase(ServerWorld w, QuestDef q, MobEntity boss, List<ServerPlayerEntity> zone, int stage) {
        w.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.HOSTILE, 1.5f, 0.7f);
        w.spawnParticles(ParticleTypes.PORTAL, boss.getX(), boss.getY() + 1.5, boss.getZ(), 80, 1.5, 1.5, 1.5, 0.6);
        switch (q.goal.mob) {
            case "matka" -> {
                boss.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 600, 1, false, false));
                summon(w, boss, EntityType.CAVE_SPIDER, 6, 14, zone);
                sayAll(w, "§5Матка визжит — гнездо взбудоражено!");
            }
            case "pogrom" -> {
                boss.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 20 * 600, 0, false, false));
                summon(w, boss, EntityType.ZOMBIE, 5, 12, zone);
                sayAll(w, "§6Погром ревёт и зовёт своих!");
            }
            case "podryv" -> {
                summon(w, boss, EntityType.CREEPER, 3, 9, zone);
                sayAll(w, "§aПодрыв шипит — вокруг лезут малые заряды!");
            }
            default -> {
                boss.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 600, stage - 1, false, false));
                summon(w, boss, EntityType.WITHER_SKELETON, 2 + stage, 10, zone);
                summon(w, boss, EntityType.ZOMBIE, 3 + stage, 14, zone);
                sayAll(w, "§cВрата распахиваются шире — Привратник зовёт слуг!");
            }
        }
    }

    private static void webSpit(ServerWorld w, MobEntity boss, List<ServerPlayerEntity> zone) {
        boolean any = false;
        for (ServerPlayerEntity p : zone) {
            if (p.squaredDistanceTo(boss) > 12 * 12) continue;
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 2));
            w.spawnParticles(ParticleTypes.ITEM_COBWEB, p.getX(), p.getY() + 1, p.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
            any = true;
        }
        if (any) {
            w.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_SPIDER_HURT, SoundCategory.HOSTILE, 1.6f, 0.6f);
            sayAll(w, "§5Паутина! §7Вы замедлены на 3 секунды.");
        }
    }

    private static void shockwave(ServerWorld w, MobEntity boss, List<ServerPlayerEntity> zone) {
        w.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.HOSTILE, 2f, 0.5f);
        w.spawnParticles(ParticleTypes.EXPLOSION, boss.getX(), boss.getY() + 0.3, boss.getZ(), 6, 1.6, 0.1, 1.6, 0);
        for (ServerPlayerEntity p : zone) {
            if (p.squaredDistanceTo(boss) > 6 * 6) continue;
            p.damage(w.getDamageSources().mobAttack(boss), 5f);
            p.takeKnockback(1.1, boss.getX() - p.getX(), boss.getZ() - p.getZ());
            p.velocityModified = true;
        }
    }

    private static void blast(ServerWorld w, MobEntity boss, Fight f, List<ServerPlayerEntity> zone) {
        int ph = f.t % 9;
        if (ph == 5) {
            w.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_CREEPER_PRIMED, SoundCategory.HOSTILE, 2f, 0.6f);
            sayAll(w, "§a§l⚠ Подрыв заряжается! §7Отбегите от него на 6+ блоков.");
        }
        if (ph >= 5) w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, boss.getX(), boss.getY() + 2.2, boss.getZ(), 40, 1.4, 1.4, 1.4, 0.2);
        if (ph == 8) {
            w.createExplosion(boss, boss.getX(), boss.getY() + 0.5, boss.getZ(), 3.2f, World.ExplosionSourceType.NONE);
            w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, boss.getX(), boss.getY() + 1, boss.getZ(), 1, 0, 0, 0, 0);
        }
    }

    private static void gateCall(ServerWorld w, MobEntity boss, List<ServerPlayerEntity> zone) {
        w.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 1.5f, 1.2f);
        for (ServerPlayerEntity p : zone) {
            if (p.squaredDistanceTo(boss) > 9 * 9) continue;
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 80, 0));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 60, 0));
            w.spawnParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 1, p.getZ(), 10, 0.3, 0.6, 0.3, 0.02);
        }
        sayAll(w, "§c§lВзгляд Врат! §7Слабость и тьма.");
    }

    private static void sayAll(ServerWorld w, String text) {
        for (ServerPlayerEntity p : w.getPlayers()) if (p.isAlive() && !p.isSpectator() && p.getY() > -1000) p.sendMessage(Text.literal(text), true);
    }

    private static void endFight(ServerWorld w, Fight f, boolean despawn) {
        f.bar.clearPlayers();
        if (despawn) {
            Entity e = w.getEntity(f.id);
            if (e != null) {
                w.spawnParticles(ParticleTypes.POOF, e.getX(), e.getBodyY(0.5), e.getZ(), 30, 0.5, 1.0, 0.5, 0.05);
                e.discard();
            }
        }
        discardTagged(w, CityInvasion.TAG_ADD);
    }

    private static void bossDied(MinecraftServer server, ServerWorld w, Entity boss, DamageSource source, String questId) {
        QuestDef q = QuestRegistry.get(questId);
        Fight f = BOSSES.remove(questId);
        if (f != null) endFight(w, f, false);
        discardTagged(w, CityInvasion.TAG_ADD);
        w.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, boss.getX(), boss.getY() + 1.5, boss.getZ(), 90, 1.8, 1.5, 1.8, 0.4);
        w.spawnParticles(ParticleTypes.FIREWORK, boss.getX(), boss.getY() + 2, boss.getZ(), 60, 2, 1.5, 2, 0.15);
        w.playSound(null, boss.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.5f, 1f);
        if (q == null) return;
        ServerPlayerEntity who = source.getAttacker() instanceof ServerPlayerEntity sp ? sp : null;
        if (who == null || Campaign.status(who, q) != Campaign.St.ACTIVE) {
            who = null;
            double best = Double.MAX_VALUE;
            for (ServerPlayerEntity p : w.getPlayers()) {
                if (!p.isAlive() || p.isSpectator() || Campaign.status(p, q) != Campaign.St.ACTIVE) continue;
                double d = p.squaredDistanceTo(boss);
                if (d < best && d < 80 * 80) { best = d; who = p; }
            }
        }
        if (who != null) {
            for (ServerPlayerEntity p : w.getPlayers()) if (p.squaredDistanceTo(boss) < 60 * 60) title(p, "§6§lПОБЕДА", "§f" + q.title);
            Campaign.setProgress(who, q, q.count());
        }
    }

    // ================================================================ разговоры
    private static void talk(ServerPlayerEntity p, String id) {
        p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_VILLAGER_AMBIENT, SoundCategory.NEUTRAL, 0.6f, id.equals("term") ? 1.8f : 1.1f);
        for (QuestDef q : QuestRegistry.all()) {
            if (!q.goal.type.equals("talk") || !q.goal.target.equals(id) || Campaign.status(p, q) != Campaign.St.ACTIVE) continue;
            Campaign.setProgress(p, q, q.count());
            return;
        }
        MinecraftServer server = p.getServer();
        if (server == null) return;
        CityState cs = CityState.get(server);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        switch (id) {
            case "kodo" -> {
                CampaignState st = CampaignState.get(server);
                if (st.done.contains("city_ascend") && !st.done.contains("city_device")) {
                    Campaign.say(p, "kodo", List.of("Лифт на вершину работает. Поднимаю."));
                    CityInvasion.liftToDeck(p);
                    return;
                }
                Campaign.say(p, "kodo", List.of(pick(cs.cleared ? KODO_AFTER : KODO_BEFORE, r)));
            }
            case "term" -> {
                Campaign.say(p, "term", List.of("Лифт: спуск на площадь. Камертон ждёт своего часа."));
                CityInvasion.liftToPlaza(p);
            }
            case "nail" -> {
                if (p.isSneaking()) { giveCar(p); return; }
                Campaign.say(p, "nail", List.of(pick(NAIL, r)));
            }
            case "rita" -> Campaign.say(p, "rita", List.of(pick(cs.cleared ? RITA_AFTER : RITA, r)));
            case "lis" -> Campaign.say(p, "lis", List.of(pick(LIS, r)));
            case "doc" -> Campaign.say(p, "doc", List.of(pick(DOC, r)));
            default -> Campaign.say(p, "surv", List.of(pick(cs.cleared ? SURV_AFTER : SURV, r)));
        }
    }

    private static String pick(String[] a, ThreadLocalRandom r) { return a[r.nextInt(a.length)]; }

    /** Новая машина у Гвоздя (присесть + ПКМ), не чаще раза в 3 минуты */
    private static void giveCar(ServerPlayerEntity p) {
        PlayerCampaign pc = PlayerCampaign.of(p);
        long now = p.getServer().getTicks();
        long next = pc.flag("city_car_cd");
        if (next > now) {
            Campaign.say(p, "nail", List.of("Погоди, " + ((next - now) / 20 + 1) + " с. Я не печатаю их на принтере."));
            return;
        }
        Item car = com.derevna230.comborush.item.ModItems.CARS.get("mustang");
        if (car == null) return;
        p.getInventory().offerOrDrop(new ItemStack(car));
        PlayerCampaign.set(p, pc.withFlag("city_car_cd", now + 20 * 180));
        Campaign.say(p, "nail", List.of("Потерял тачку? Держи новую. Бережёшь — получаешь, не бережёшь — получаешь снова. Но не чаще раза в три минуты."));
    }

    private static final String[] KODO_BEFORE = {
        "«Камертон» — не оружие. Это частота, на которой Врата перестают держать форму. Я построил его на вершине башни — осталось подключить.",
        "Нексус строили так, чтобы его нельзя было взломать снаружи. Забавно: теперь и изнутри не получается.",
        "Не трогай красные кабели в лифте. Серьёзно. Я однажды тронул — отсюда и седина.",
        "Монстры не умные, они голодные. А Врата — это дверь, которую никто не закрыл. Мы закроем."};
    private static final String[] KODO_AFTER = {
        "Слышишь? Это тишина. Я её не слышал три года.",
        "Врата остались. Закрыть их насовсем нельзя — только держать на привязи. Камертон держит.",
        "Город твой, курьер. Только возвращайся иногда: кто-то же должен гонять тех, кто вылезет из порталов."};
    private static final String[] NAIL = {
        "Машина — это не транспорт. Это последний аргумент. Давишь — значит живёшь.",
        "Скелеты стреляют, зомби кусаются, пауки липнут. Только крипера не дави в упор — рванёт под днище.",
        "Забыл тачку или потерял? Присядь и тыкни в меня — выдам новую. Раз в три минуты, я не волшебник.",
        "Нитро — для тех, кто торопится. И для тех, кого догоняют."};
    private static final String[] RITA = {
        "Такси не работает, пока не работает город. Но я всё равно жду пассажира.",
        "Знаешь, в чём главный секрет вождения по ночному городу? Не смотреть на карту. Смотреть в зеркало.",
        "Говорят, с вершины башни видно все Врата. А я их и так вижу — каждый раз, когда выезжаю."};
    private static final String[] RITA_AFTER = {
        "Тихо стало. Даже скучно. Но платят нормально — пассажиры возвращаются.",
        "Подвезти? Бесплатно. Для тебя — бесплатно."};
    private static final String[] LIS = {
        "Чёрные Врата? Я ничего не знаю. Но если вдруг узнаю — это будет стоить.",
        "На рынке можно купить всё. Кроме тишины. Её, говорят, в Нексусе варят.",
        "Хочешь совет бесплатно? Не стой на месте. Стоящих тут съедают."};
    private static final String[] DOC = {
        "Если укусили — не геройствуй, зови. Яд у пауков с этой стороны Врат — злее обычного.",
        "Сыворотка почти готова. Мне нужен кто-то, у кого есть колёса и нет паники.",
        "Знаешь, что самое опасное в городе? Не монстры. Люди, которые говорят «да нормально всё»."};
    private static final String[] SURV = {
        "Спасибо, что приехали. Мы уже думали — всё, никто не вернётся.",
        "Тут тепло и светло. Когда выйдем наружу — непонятно.",
        "Если увидишь Риту — передай, что мы ждём."};
    private static final String[] SURV_AFTER = {
        "Тихо. Страшно тихо. Но хорошо.",
        "Завтра пойдём домой. Впервые за год — домой."};

    // ================================================================ персонажи: появление
    private static void ensureNpcs(ServerWorld w, List<ServerPlayerEntity> here) {
        MinecraftServer server = w.getServer();
        if (server == null) return;
        boolean deckBuilt = CityState.get(server).built.contains("deck");
        for (Npc n : NPCS) {
            if (n.id.equals("term") && !deckBuilt) continue;
            if (!CityInvasion.near(here, n.x, n.z, n.id.equals("term") ? 30 : 64)) continue;
            if (!w.isChunkLoaded((int) Math.floor(n.x) >> 4, (int) Math.floor(n.z) >> 4)) continue;
            String tag = "cr_npc:" + n.id;
            Box box = new Box(n.x - 5, n.y - 4, n.z - 5, n.x + 5, n.y + 5, n.z + 5);
            int have = w.getEntitiesByClass(VillagerEntity.class, box, v -> v.getCommandTags().contains(tag)).size();
            for (int i = have; i < n.count; i++) {
                double ox = n.count > 1 ? (i == 0 ? -0.9 : 0.9) : 0;
                spawnNpc(w, n, n.x + ox, n.z);
            }
        }
    }

    private static void spawnNpc(ServerWorld w, Npc n, double x, double z) {
        VillagerEntity v = EntityType.VILLAGER.create(w);
        if (v == null) return;
        v.refreshPositionAndAngles(x, n.y, z, ThreadLocalRandom.current().nextFloat() * 360f, 0f);
        v.setAiDisabled(true);
        v.setInvulnerable(true);
        v.setPersistent();
        v.setSilent(true);
        v.setCustomName(Text.literal(n.name));
        v.setCustomNameVisible(true);
        v.addCommandTag("cr_npc:" + n.id);
        if (n.invisible) v.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 20 * 3600 * 10, 0, false, false));
        w.spawnEntityAndPassengers(v);
    }

    // ================================================================ маяки у целей
    private static void beacons(ServerWorld w, List<ServerPlayerEntity> here, List<QuestDef> cq) {
        for (ServerPlayerEntity p : here) {
            for (QuestDef q : cq) {
                if (Campaign.status(p, q) != Campaign.St.ACTIVE) continue;
                BlockPos t = target(p, q);
                if (t == null || t.getY() > 200) continue;
                double dx = p.getX() - t.getX(), dz = p.getZ() - t.getZ();
                if (dx * dx + dz * dz > 200 * 200) continue;
                for (int y = 2; y < 46; y += 3)
                    w.spawnParticles(p, ParticleTypes.END_ROD, true, t.getX() + 0.5, t.getY() + y, t.getZ() + 0.5, 2, 0.1, 1.2, 0.1, 0);
            }
        }
    }

    // ================================================================ для трекера и журнала
    /** Куда указывает стрелка трекера (только внутри города) */
    static BlockPos target(ServerPlayerEntity p, QuestDef q) {
        if (!Universes.inCity(p)) return null;
        QuestDef.Goal g = q.goal;
        switch (g.type) {
            case "reach": {
                MinecraftServer server = p.getServer();
                if (server == null) return null;
                Set<String> vis = CityState.get(server).visitedOf(q.id);
                BlockPos best = null; double bd = Double.MAX_VALUE;
                for (int i = 0; i < g.sites.size(); i++) {
                    String sid = g.sites.get(i);
                    if (vis.contains(sid)) continue;
                    BlockPos s = site(sid);
                    if (s == null) continue;
                    if (g.ordered) return s;
                    double d = p.squaredDistanceTo(s.getX() + 0.5, s.getY(), s.getZ() + 0.5);
                    if (d < bd) { bd = d; best = s; }
                }
                return best;
            }
            case "hold": case "boss": return site(g.site);
            case "kill": return g.site.isEmpty() ? null : site(g.site);
            case "talk": {
                for (Npc n : NPCS) if (n.id.equals(g.target)) return BlockPos.ofFloored(n.x, n.y, n.z);
                return null;
            }
            default: return null;
        }
    }

    /** Секунды на табло трекера: таймер маршрута или оставшееся время обороны; -1 — нет */
    static int timer(ServerPlayerEntity p, QuestDef q) {
        MinecraftServer server = p.getServer();
        if (server == null) return -1;
        if (q.goal.type.equals("reach") && RUN.containsKey(q.id))
            return (int) Math.max(0, (RUN.get(q.id) - server.getTicks()) / 20);
        if (q.goal.type.equals("hold")) {
            Hold h = HOLDS.get(q.id);
            return h == null ? -1 : Math.max(0, q.count() - h.held);
        }
        return -1;
    }

    // ================================================================ завершение заданий
    static void onQuestDone(ServerPlayerEntity p, QuestDef q) {
        MinecraftServer server = p.getServer();
        if (server == null || !q.chapter.startsWith("other")) return;
        CityState cs = CityState.get(server);
        cs.visitedOf(q.id).clear();
        cs.markDirty();
        RUN.remove(q.id);
        ServerWorld w = server.getWorld(Universes.NIGHT_CITY);
        if (HOLDS.remove(q.id) != null && w != null) discardTagged(w, CityInvasion.TAG_HOLD);
        Fight f = BOSSES.remove(q.id);
        if (f != null && w != null) endFight(w, f, false);
        switch (q.id) {
            case "city_ascend" -> {
                CityInvasion.ensureDeck(server);
                CityInvasion.liftToDeck(p);
                for (ServerPlayerEntity o : server.getPlayerManager().getPlayerList())
                    if (o != p && Universes.inCity(o))
                        o.sendMessage(Text.literal("§b[Кодо] §fЛифт на вершину запущен. Подойдите ко мне — подниму."), false);
            }
            case "city_device" -> finale(server);
            default -> { }
        }
    }

    /** Камертон включён: монстры улиц исчезают, остаются только порталы */
    private static void finale(MinecraftServer server) {
        CityState cs = CityState.get(server);
        cs.cleared = true;
        cs.markDirty();
        ServerWorld w = server.getWorld(Universes.NIGHT_CITY);
        if (w == null) return;
        int n = CityInvasion.purge(server);
        for (ServerPlayerEntity p : new ArrayList<>(w.getPlayers())) {
            title(p, "§b§lТИШИНА", "§7Камертон запущен. Остались только Врата.");
            p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 0.8f, 1.1f);
            p.sendMessage(Text.literal("§b✦ Улицы очищены: §f" + n + " §7монстров исчезло. Новые будут появляться лишь из Чёрных Врат — по одному."), false);
            if (p.getY() > 200) CityInvasion.liftToPlaza(p);
        }
    }

    static void reset(MinecraftServer server) {
        clearMemory();
        CityState cs = CityState.get(server);
        cs.cleared = false;
        cs.visited.clear();
        cs.markDirty();
    }

    private static void title(ServerPlayerEntity p, String t1, String t2) {
        p.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 50, 15));
        p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(t1)));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(t2)));
    }
}
