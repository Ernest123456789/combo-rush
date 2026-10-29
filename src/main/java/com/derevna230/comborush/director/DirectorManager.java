package com.derevna230.comborush.director;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.cycle.CycleManager;
import com.derevna230.comborush.cycle.CyclePhase;
import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.score.ScoreEngine;
import com.derevna230.comborush.score.ScoreSource;
import com.google.gson.Gson;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/** «Режиссёр»: выбирает сценарий на каждое событие, меняет правила подсчёта, спавнит осады и охоту на Метку */
public class DirectorManager implements SimpleSynchronousResourceReloadListener {
    private static final String MOB_TAG = "cr_event_mob";
    private static final String HUNTER_TAG = "cr_hunter";

    private static final Map<String, DirectorEventDef> ALL = new LinkedHashMap<>();
    private static final Gson GSON = new Gson();

    private static DirectorEventDef current = null;
    private static String lastId = "";
    private static String forcedNext = "";
    private static int eventTick = 0;
    private static UUID marked = null;
    private static UUID boss = null;
    private static double avgEvent = 1;

    // ---------- загрузка ----------
    @Override public Identifier getFabricId() { return Identifier.of(ComboRush.MOD_ID, "director"); }

    @Override
    public void reload(ResourceManager manager) {
        ALL.clear();
        for (var entry : manager.findResources("director", id -> id.getPath().endsWith(".json")).entrySet()) {
            Identifier file = entry.getKey();
            try (var reader = entry.getValue().getReader()) {
                DirectorEventDef def = GSON.fromJson(reader, DirectorEventDef.class);
                String p = file.getPath();
                def.id = p.substring("director/".length(), p.length() - ".json".length());
                ALL.put(def.id, def);
            } catch (Exception e) {
                ComboRush.LOGGER.error("Не удалось загрузить событие Режиссёра {}", file, e);
            }
        }
        ComboRush.LOGGER.info("Загружено событий Режиссёра: {}", ALL.size());
    }

    // ---------- что событие меняет в подсчёте (0/1, если события нет) ----------
    public static double sourceMult(ScoreSource src) {
        if (current == null) return 1.0;
        return current.source_mult.getOrDefault(src.name(), current.source_mult.getOrDefault("ALL", 1.0));
    }
    public static double multAdd()       { return current == null ? 0 : current.mult_add; }
    public static double comboStep()     { return current == null ? 0 : current.combo_step; }
    public static double comboWindow()   { return current == null ? 0 : current.combo_window; }
    public static double coinBonus()     { return current == null ? 0 : current.coin_bonus; }
    public static double markMult(ServerPlayerEntity p) {
        return current != null && current.mark != null && p.getUuid().equals(marked) ? current.mark.score_mult : 1.0;
    }
    public static String currentName() { return current == null ? "" : current.name; }
    public static String suffix() { return current == null ? "" : " «" + current.name + "»"; }
    public static Collection<String> ids() { return ALL.keySet(); }
    public static DirectorEventDef get(String id) { return ALL.get(id); }
    public static boolean setNext(String id) {
        if (!ALL.containsKey(id)) return false;
        forcedNext = id; return true;
    }

    // ---------- старт события ----------
    public static void start(MinecraftServer server) {
        eventTick = 0; marked = null; boss = null;
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        avgEvent = players.isEmpty() ? 1 : players.stream()
            .mapToInt(p -> PlayerProfile.of(p).eventsDone() + 1).average().orElse(1);

        current = pick();
        if (current != null) {
            lastId = current.id;
            server.getPlayerManager().broadcast(Text.literal("§6§l⚡ " + current.name), false);
            if (!current.description.isEmpty())
                server.getPlayerManager().broadcast(Text.literal("§e" + current.description), false);

            if (!current.weather.isEmpty()) setWeather(server, current.weather);
            if (current.mark != null && current.mark.enabled) startHunt(server);
        }
        startBossBlind(server);
    }

    // ================= Босс-блайнд: каждое 5-е событие игрока =================
    public static int BOSS_EVERY = 5;                      // config: boss_blind_every (0 — без босс-блайндов)
    private static DebuffDef debuff = null;
    private static final Set<UUID> debuffPlayers = new HashSet<>();
    private static String lastDebuff = "";

    public static DebuffDef debuffFor(ServerPlayerEntity p) {
        return debuff != null && debuffPlayers.contains(p.getUuid()) ? debuff : null;
    }
    public static boolean hasDebuff(ServerPlayerEntity p, String id) {
        DebuffDef d = debuffFor(p);
        return d != null && d.id.equals(id);
    }
    public static double debuffSourceMult(ServerPlayerEntity p, ScoreSource src) {
        DebuffDef d = debuffFor(p);
        if (d == null) return 1.0;
        return d.source_mult.getOrDefault(src.name(), d.source_mult.getOrDefault("ALL", 1.0));
    }
    public static double targetMult(ServerPlayerEntity p) {
        DebuffDef d = debuffFor(p);
        return d == null ? 1.0 : d.target_mult;
    }
    /** «Голод»: во время события нельзя есть, пока не набрана доля бронзового порога */
    public static boolean blocksFood(ServerPlayerEntity p) {
        DebuffDef d = debuffFor(p);
        if (d == null || d.hunger_target <= 0 || CycleManager.phase() != CyclePhase.EVENT) return false;
        int number = PlayerProfile.of(p).eventsDone() + 1;
        return ScoreEngine.get(p.getUuid()).total() < d.hunger_target * ScoreEngine.bronzeTarget(number) * d.target_mult;
    }

    private static void startBossBlind(MinecraftServer server) {
        debuff = null;
        debuffPlayers.clear();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList())
            if (BOSS_EVERY > 0 && (PlayerProfile.of(p).eventsDone() + 1) % BOSS_EVERY == 0) debuffPlayers.add(p.getUuid());
        // финал «хаос»: случайное правило босс-блайнда в каждом событии и для всех
        boolean chaos = com.derevna230.comborush.campaign.Finale.chaos(server);
        if (chaos) for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) debuffPlayers.add(p.getUuid());
        if (debuffPlayers.isEmpty()) return;

        DebuffDef d = DebuffRegistry.pick(lastDebuff);
        if (d == null) { debuffPlayers.clear(); return; }
        debuff = d;
        lastDebuff = d.id;

        List<String> names = new ArrayList<>();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!debuffPlayers.contains(p.getUuid())) continue;
            names.add(p.getName().getString());
            if (!d.status_effect.isEmpty()) {
                Identifier id = Identifier.tryParse(d.status_effect);
                if (id != null) Registries.STATUS_EFFECT.getEntry(id).ifPresent(eff ->
                    p.addStatusEffect(new StatusEffectInstance(eff, CycleManager.EVENT_SECONDS * 20, d.amplifier, false, true, true)));
            }
        }
        server.getPlayerManager().broadcast(Text.literal("§4§lБОСС-БЛАЙНД: §c" + d.name + " §7(" + String.join(", ", names) + ")"), false);
        server.getPlayerManager().broadcast(Text.literal("§c" + d.description), false);
        server.getPlayerManager().broadcast(Text.literal("§6Награда за бронзу и выше: монеты +"
            + Math.round(d.coin_bonus * 100) + "%" + (d.reward_consumable ? " и случайный расходник" : "")), false);
    }

    private static void endBossBlind(MinecraftServer server) {
        if (debuff != null && !debuff.status_effect.isEmpty()) {
            Identifier id = Identifier.tryParse(debuff.status_effect);
            if (id != null) Registries.STATUS_EFFECT.getEntry(id).ifPresent(eff -> {
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList())
                    if (debuffPlayers.contains(p.getUuid())) p.removeStatusEffect(eff);
            });
        }
        debuff = null;
        debuffPlayers.clear();
    }

    private static DirectorEventDef pick() {
        if (!forcedNext.isEmpty()) {
            DirectorEventDef d = ALL.get(forcedNext);
            forcedNext = "";
            if (d != null) return d;
        }
        List<DirectorEventDef> pool = ALL.values().stream()
            .filter(d -> d.min_event <= avgEvent).collect(Collectors.toCollection(ArrayList::new));
        if (pool.size() > 1) pool.removeIf(d -> d.id.equals(lastId)); // не два раза подряд
        if (pool.isEmpty()) return null;

        int total = pool.stream().mapToInt(d -> Math.max(1, d.weight)).sum();
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (DirectorEventDef d : pool) {
            roll -= Math.max(1, d.weight);
            if (roll < 0) return d;
        }
        return pool.get(0);
    }

    private static void setWeather(MinecraftServer server, String w) {
        ServerWorld ow = server.getOverworld();
        switch (w) {
            case "thunder" -> ow.setWeather(0, 6000, true, true);
            case "rain"    -> ow.setWeather(0, 6000, true, false);
            case "clear"   -> ow.setWeather(6000, 0, false, false);
            default -> { }
        }
    }

    // ---------- тик: волны мобов ----------
    public static void tick(MinecraftServer server) {
        if (current == null || CycleManager.phase() != CyclePhase.EVENT) return;
        if (server.getPlayerManager().getPlayerList().isEmpty()) return;
        eventTick++;
        DirectorEventDef.Spawns sp = current.spawns;
        if (sp != null && eventTick > 100 && eventTick % Math.max(20, sp.interval) == 0) spawnWave(server, sp);
    }

    private static void spawnWave(MinecraftServer server, DirectorEventDef.Spawns sp) {
        if (sp.entities.isEmpty()) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator()) continue;
            ServerWorld w = p.getServerWorld();
            int near = w.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(40),
                m -> m.getCommandTags().contains(MOB_TAG)).size();
            int toSpawn = Math.min(sp.per_player, sp.cap - near);
            for (int i = 0; i < toSpawn; i++) {
                BlockPos pos = findSpot(w, p, sp.radius_min, sp.radius_max);
                if (pos == null) continue;
                String id = sp.entities.get(ThreadLocalRandom.current().nextInt(sp.entities.size()));
                Entity e = spawn(w, id, pos);
                if (e instanceof MobEntity m) {
                    m.addCommandTag(MOB_TAG);
                    m.setTarget(p);
                }
            }
        }
    }

    private static Entity spawn(ServerWorld w, String entityId, BlockPos pos) {
        Identifier id = Identifier.tryParse(entityId);
        if (id == null || !Registries.ENTITY_TYPE.containsId(id)) return null;
        EntityType<?> type = Registries.ENTITY_TYPE.get(id);
        return type.spawn(w, pos, SpawnReason.EVENT);
    }

    private static BlockPos findSpot(ServerWorld w, ServerPlayerEntity p, int rmin, int rmax) {
        var r = w.random;
        for (int i = 0; i < 12; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            int d = rmin + r.nextInt(Math.max(1, rmax - rmin + 1));
            int x = p.getBlockX() + (int) (Math.cos(ang) * d);
            int z = p.getBlockZ() + (int) (Math.sin(ang) * d);
            for (int dy = 4; dy >= -4; dy--) {
                BlockPos pos = new BlockPos(x, p.getBlockY() + dy, z);
                if (w.getBlockState(pos).isAir() && w.getBlockState(pos.up()).isAir()
                        && w.getBlockState(pos.down()).isSolidBlock(w, pos.down())) return pos;
            }
        }
        return null;
    }

    // ---------- Охота на Метку ----------
    private static void startHunt(MinecraftServer server) {
        List<ServerPlayerEntity> list = server.getPlayerManager().getPlayerList().stream()
            .filter(p -> !p.isSpectator()).collect(Collectors.toList());
        if (list.isEmpty()) return;
        ServerPlayerEntity target = list.get(ThreadLocalRandom.current().nextInt(list.size()));
        marked = target.getUuid();

        DirectorEventDef.Mark m = current.mark;
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING,
            CycleManager.EVENT_SECONDS * 20, 0, false, false, true));
        server.getPlayerManager().broadcast(Text.literal("§c§lМЕТКА: §f" + target.getName().getString()
            + " §7(очки ×" + m.score_mult + "). §fУбейте охотника, пока он не добрался до цели!"), false);

        ServerWorld w = target.getServerWorld();
        BlockPos pos = findSpot(w, target, 12, 20);
        if (pos == null) return;
        Entity e = spawn(w, m.boss_entity, pos);
        if (!(e instanceof MobEntity mob)) return;

        double hp = m.boss_health + m.boss_health_per_event * avgEvent;
        EntityAttributeInstance inst = mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (inst != null) { inst.setBaseValue(hp); mob.setHealth((float) hp); }
        mob.setCustomName(Text.literal("§c" + m.boss_name.replace("{player}", target.getName().getString())));
        mob.setCustomNameVisible(true);
        mob.setPersistent();
        mob.addCommandTag(MOB_TAG);
        mob.addCommandTag(HUNTER_TAG);
        mob.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, CycleManager.EVENT_SECONDS * 20));
        mob.setTarget(target);
        boss = mob.getUuid();
    }

    /** Вызывается из слушателя смерти мобов */
    public static void onHunterKilled(MinecraftServer server, ServerPlayerEntity killer) {
        if (current == null || current.mark == null || server == null) return;
        DirectorEventDef.Mark m = current.mark;
        double bonus = m.bonus + m.bonus_per_event * avgEvent;
        boss = null;

        if (killer == null) {
            server.getPlayerManager().broadcast(Text.literal("§7Охотник пал сам по себе. Бонуса нет."), false);
            return;
        }
        server.getPlayerManager().broadcast(Text.literal("§a§l" + killer.getName().getString()
            + " §fубил охотника! §6+" + (long) bonus + " очков"), false);
        ScoreEngine.addBonus(killer, bonus);

        if (marked != null && !marked.equals(killer.getUuid())) {
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(marked);
            if (target != null) ScoreEngine.addBonus(target, bonus / 2);
        }
    }

    // ---------- конец события ----------
    public static void end(MinecraftServer server) {
        endBossBlind(server);
        if (current == null) return;

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.getServerWorld().getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(160),
                m -> m.getCommandTags().contains(MOB_TAG)).forEach(Entity::discard);
            if (p.getUuid().equals(marked)) p.removeStatusEffect(StatusEffects.GLOWING);
        }
        if (boss != null) {
            for (ServerWorld w : server.getWorlds()) {
                Entity e = w.getEntity(boss);
                if (e != null) e.discard();
            }
            server.getPlayerManager().broadcast(Text.literal("§7Охотник скрылся в тени…"), false);
        }
        current = null; marked = null; boss = null; eventTick = 0;
    }

    // ---------- команды ----------
    public static void describe(ServerCommandSource src) {
        if (current == null) {
            src.sendFeedback(() -> Text.literal("§7Сейчас нет события."), false);
            return;
        }
        src.sendFeedback(() -> Text.literal("§6⚡ " + current.name + " §7— " + current.description), false);
    }
}
