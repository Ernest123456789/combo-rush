package com.derevna230.comborush.match;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.item.BombEntity;
import com.derevna230.comborush.item.BombItem;
import com.derevna230.comborush.item.ModItems;
import com.derevna230.comborush.lobby.Loadout;
import com.derevna230.comborush.lobby.Party;
import com.derevna230.comborush.lobby.Standoff;
import com.derevna230.comborush.pet.ModEntities;
import com.derevna230.comborush.universe.GameMap;
import com.derevna230.comborush.universe.GameMaps;
import com.derevna230.comborush.universe.Universes;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;

import java.util.*;

/**
 * Соревновательный матч «Противостояния» на карте «Дюны»: до {@value #WIN_ROUNDS} побед (12:12 — ничья), после {@value #HALF}-го раунда
 * стороны меняются. Раунд = заморозка с закупкой → бой → пауза. Деньги — как в CS: за победу/поражение раунда (с бонусом за серию
 * поражений), за убийства, закладку и разминирование; выжившие сохраняют оружие, погибшие начинают с пистолета.
 * <ul>
 * <li>Т побеждают, взорвав бомбу или уничтожив спецназ; КТ — обезвредив бомбу, уничтожив Т до закладки или дотянув до конца времени.</li>
 * <li>Команды добираются ботами до {@value #TEAM_SIZE} человек. Живые игроки получают ствол/нож по своему снаряжению из меню; свой инвентарь на матч прячется и возвращается после.</li>
 * <li>Погибший игрок становится наблюдателем до следующего раунда. Огня по своим нет.</li>
 * </ul>
 * Одновременно идёт один матч на сервер.
 */
public final class Match {
    private Match() {}

    public static final int TEAM_SIZE = 5, WIN_ROUNDS = 13, HALF = 10, DRAW_AT = 12;
    private static final int FREEZE_TICKS = 12 * 20, ROUND_TICKS = 115 * 20, END_TICKS = 5 * 20, HALF_TICKS = 8 * 20, OVER_TICKS = 8 * 20;
    /** Закупка: 30 с от начала раунда (заморозка + остаток после старта), и только у своей базы */
    private static final int BUY_TICKS = 30 * 20 - FREEZE_TICKS;
    private static final double BUY_RADIUS = 13;
    /** Урон ботов относительно «человеческого» урона ствола */
    public static final float BOT_DAMAGE = 0.9f;

    private enum Phase { IDLE, FREEZE, LIVE, END }

    private static final class Human {
        Team team; GameMode prev; double sx, sy, sz; float yaw;
        Human(Team t, GameMode prev) { this.team = t; this.prev = prev; }
    }

    private static final AttachmentType<NbtCompound> STASH = AttachmentRegistry.create(
        Identifier.of(ComboRush.MOD_ID, "match_stash"), b -> b.persistent(NbtCompound.CODEC));

    private static final Map<UUID, Human> HUMANS = new LinkedHashMap<>();
    private static final List<UUID> BOTS = new ArrayList<>();
    private static Phase phase = Phase.IDLE;
    private static int timer, round, winsT, winsCT;
    private static boolean planted, over, draw, halftime;
    private static int liveTicks;
    // экономика
    private static final Map<UUID, Integer> MONEY = new HashMap<>();
    private static final Set<UUID> SURVIVED = new HashSet<>();
    private static final int[] LOSS = new int[2], BOT_MONEY = new int[2];
    private static final Map<UUID, Integer> LAST_PAY = new HashMap<>();
    private static UUID bombId;
    private static char targetSite = 'A';
    private static Team pendingWinner;
    private static String pendingReason = "";
    private static ServerBossBar bar;
    private static final Random RND = new Random();
    private static int botSerial;
    /** Карта текущего (или последнего) матча */
    private static GameMap map = GameMaps.dust();
    public static GameMap map() { return map; }

    // ---- координация ботов: занятые позиции, разведданные, сбор Т перед заходом
    private static final Map<String, UUID> CLAIMS = new HashMap<>();
    private static final char[] ALERT = new char[2];
    private static final int[] ALERT_AT = {-9999, -9999};
    private static final Set<UUID> T_READY = new HashSet<>();
    private static boolean tGo;
    private static int aliveTBots;

    /** Занять точку (false — её уже держит другой бот) */
    public static boolean claim(String key, UUID who) {
        UUID c = CLAIMS.get(key);
        if (c != null && !c.equals(who)) return false;
        CLAIMS.values().removeIf(who::equals);
        CLAIMS.put(key, who);
        return true;
    }
    public static boolean claimedByOther(String key, UUID who) { UUID c = CLAIMS.get(key); return c != null && !c.equals(who); }
    public static void release(UUID who) { CLAIMS.values().removeIf(who::equals); }

    /** Кто-то из стороны {@code by} заметил противника в точке pos: если рядом площадка — сторона знает, куда идут */
    public static void spotted(Team by, net.minecraft.util.math.Vec3d pos) {
        for (char c : new char[]{'A', 'B'}) {
            double[] sc = map.siteCenter(c);
            double dx = pos.x - sc[0], dz = pos.z - sc[1];
            if (dx * dx + dz * dz < 30 * 30) { ALERT[by.ordinal()] = c; ALERT_AT[by.ordinal()] = liveTicks; return; }
        }
    }
    /** Площадка, у которой недавно (20 с) видели противника, или 0 */
    public static char alert(Team t) { return liveTicks - ALERT_AT[t.ordinal()] < 20 * 20 ? ALERT[t.ordinal()] : 0; }
    public static int liveTicks() { return liveTicks; }

    /** Бот Т дошёл до места сбора; заход начинается, когда собрались почти все (или прошло 35 с) */
    public static void tReady(UUID id) { T_READY.add(id); }
    public static boolean tGo() {
        if (!tGo && (planted || liveTicks > 35 * 20 || T_READY.size() >= Math.max(1, aliveTBots - 1))) tGo = true;
        return tGo;
    }

    // ---------------------------------------------------------------- состояние для ботов и событий
    public static boolean active() { return phase != Phase.IDLE; }
    public static boolean live() { return phase == Phase.LIVE; }
    public static boolean bombPlanted() { return planted; }
    public static char targetSite() { return targetSite; }

    public static BombEntity bomb(ServerWorld w) {
        return bombId != null && w.getEntity(bombId) instanceof BombEntity b && !b.isRemoved() ? b : null;
    }

    /** Команда участника матча (null — не участник или не жив: наблюдатели не считаются противником) */
    public static Team teamOf(Entity e) {
        if (e instanceof BotEntity b) return b.team();
        if (e instanceof ServerPlayerEntity p) { Human h = HUMANS.get(p.getUuid()); return h == null ? null : h.team; }
        return null;
    }

    // ---------------------------------------------------------------- подключение к игре
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Match::tick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((e, src, amount) -> {
            if (phase == Phase.IDLE || !GameMaps.isMatchWorld(e.getWorld())) return true;
            if (src.isOf(DamageTypes.GENERIC_KILL) || src.isOf(DamageTypes.OUT_OF_WORLD)) return true;
            if (!(e instanceof ServerPlayerEntity || e instanceof BotEntity)) return true;
            if (phase != Phase.LIVE) return false;                                  // заморозка и пауза: урона нет
            Team vt = teamOf(e);
            Entity at = src.getAttacker();
            if (at != null && at != e && vt != null && teamOf(at) == vt) return false;   // по своим не бьём
            if (at != null && at != e) MatchStats.damage(e, at, amount);
            return true;
        });
        ServerLivingEntityEvents.ALLOW_DEATH.register((e, src, amount) -> {
            if (phase != Phase.IDLE && e instanceof ServerPlayerEntity p && HUMANS.containsKey(p.getUuid())
                    && GameMaps.isMatchWorld(p.getWorld())) {
                onHumanDeath(p, src);
                return false;
            }
            return true;
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> leave(handler.getPlayer(), false));
        // если сервер упал посреди матча: вернуть спрятанный инвентарь и вывести из карты
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.getPlayer();
            if (HUMANS.containsKey(p.getUuid())) return;
            clearSide(p);
            if (p.hasAttached(STASH)) restore(p);
            if (Universes.inDust(p)) {
                p.changeGameMode(GameMode.ADVENTURE);
                Standoff.toMenu(p);
            }
        });
    }

    // ---------------------------------------------------------------- старт и остановка
    /** Начать матч за команду team: игрок и его группа (из меню) попадают на карту, остальные места занимают боты. @return null или причина отказа */
    public static String start(ServerPlayerEntity host, Team team) { return start(host, team, "dust"); }

    public static String start(ServerPlayerEntity host, Team team, String mapId) {
        MinecraftServer server = host.getServer();
        if (phase != Phase.IDLE) return "Матч уже идёт.";
        map = GameMaps.get(mapId);
        ServerWorld w = server == null ? null : server.getWorld(map.world);
        if (w == null) return "Карта ещё не загрузилась.";
        if (!host.isAlive() || host.isSpectator()) return "Сейчас войти нельзя.";
        if (!Universes.inStandoff(host) && !host.hasPermissionLevel(2)) return "Матч запускается из меню «Противостояния».";
        loadMapChunks(w);

        List<ServerPlayerEntity> group = new ArrayList<>();
        group.add(host);
        Party q = Party.of(host);
        if (q != null && q.leader().equals(host.getUuid())) {
            for (ServerPlayerEntity m : q.online(server, host)) {
                if (m != host && m.isAlive() && (Universes.inStandoff(m) || Universes.inLobby(m)) && group.size() < TEAM_SIZE) group.add(m);
            }
        }
        HUMANS.clear();
        BOTS.clear();
        MatchStats.reset();
        List<String> names = new ArrayList<>(List.of(BOT_NAMES));
        Collections.shuffle(names, RND);
        for (int i = 0; i < 2 * TEAM_SIZE; i++) BOTNAME[i / TEAM_SIZE][i % TEAM_SIZE] = names.get(i % names.size());
        for (ServerPlayerEntity p : group) {
            if (!Universes.inUniverse(p)) Universes.enterLobby(p, true);        // запомнить, откуда вернуть домой
            HUMANS.put(p.getUuid(), new Human(team, p.interactionManager.getGameMode()));
            stash(p);
            setSide(p, team);
            MONEY.put(p.getUuid(), BuyDefs.START_MONEY);
        }
        round = 0; winsT = 0; winsCT = 0; over = false; draw = false; halftime = false; planted = false; bombId = null; pendingWinner = null;
        SURVIVED.clear(); LOSS[0] = LOSS[1] = 0; BOT_MONEY[0] = BOT_MONEY[1] = BuyDefs.START_MONEY;
        BombEntity.LISTENER = LISTENER;
        bar = new ServerBossBar(Text.literal("Матч"), BossBar.Color.YELLOW, BossBar.Style.PROGRESS);
        for (ServerPlayerEntity p : group) bar.addPlayer(p);
        broadcast("§6§lСоревновательный матч! §fКарта «" + map.title + "», до §e" + WIN_ROUNDS + "§f побед (12:12 — ничья), смена сторон после " + HALF
            + "-го раунда. Вы играете за " + team.coloredTag() + "§f. §eE §f— закупка.");
        startRound(server, w);
        return null;
    }

    /** Закончить матч сейчас и вернуть всех в меню */
    public static String stop(MinecraftServer server) {
        if (phase == Phase.IDLE) return "Матч не идёт.";
        finish(server, null, false);
        return null;
    }

    /** Игрок вышел из матча (команда, выход из вселенной, отключение) */
    public static void leave(ServerPlayerEntity p, boolean toMenu) {
        Human h = HUMANS.remove(p.getUuid());
        if (h == null) return;
        MONEY.remove(p.getUuid()); SURVIVED.remove(p.getUuid());
        if (bar != null) bar.removePlayer(p);
        clearSide(p);
        p.removeStatusEffect(StatusEffects.SLOWNESS);
        p.changeGameMode(h.prev);
        restore(p);
        if (toMenu && GameMaps.isMatchWorld(p.getWorld())) Standoff.toMenu(p);
        if (HUMANS.isEmpty() && phase != Phase.IDLE && p.getServer() != null) finish(p.getServer(), null, false);
    }

    // ---- сторона игрока видна клиентам через scoreboard-команды: по ней рисуется модель бойца и цвет ника
    private static void setSide(ServerPlayerEntity p, Team t) {
        if (p.getServer() == null) return;
        net.minecraft.scoreboard.Scoreboard sb = p.getServer().getScoreboard();
        String name = t == Team.T ? "cr_t" : "cr_ct";
        net.minecraft.scoreboard.Team st = sb.getTeam(name);
        if (st == null) {
            st = sb.addTeam(name);
            st.setColor(t == Team.T ? net.minecraft.util.Formatting.GOLD : net.minecraft.util.Formatting.BLUE);
        }
        sb.addScoreHolderToTeam(p.getNameForScoreboard(), st);
    }

    private static void clearSide(ServerPlayerEntity p) {
        if (p.getServer() == null) return;
        net.minecraft.scoreboard.Scoreboard sb = p.getServer().getScoreboard();
        net.minecraft.scoreboard.Team cur = sb.getScoreHolderTeam(p.getNameForScoreboard());
        if (cur != null && cur.getName().startsWith("cr_")) sb.removeScoreHolderFromTeam(p.getNameForScoreboard(), cur);
    }

    private static void loadMapChunks(ServerWorld w) {
        for (int cx = (map.x0 - 1) >> 4; cx <= (map.x1 + 1) >> 4; cx++)
            for (int cz = (map.z0 - 1) >> 4; cz <= (map.z1 + 1) >> 4; cz++) w.getChunk(cx, cz);
    }

    // ---------------------------------------------------------------- раунд

    private static void startRound(MinecraftServer server, ServerWorld w) {
        round++;
        phase = Phase.FREEZE;
        timer = FREEZE_TICKS;
        planted = false; bombId = null; pendingWinner = null; liveTicks = 0;
        MatchStats.roundStart();
        CLAIMS.clear(); T_READY.clear(); tGo = false; ALERT_AT[0] = ALERT_AT[1] = -9999;
        boolean lurk = RND.nextInt(3) > 0;
        targetSite = RND.nextBoolean() ? 'A' : 'B';
        cleanMap(w);

        // кто есть в командах: люди занимают первые места, остальное добирают боты
        Map<Team, List<ServerPlayerEntity>> humans = new EnumMap<>(Team.class);
        for (Team t : Team.values()) humans.put(t, new ArrayList<>());
        for (Iterator<Map.Entry<UUID, Human>> it = HUMANS.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(en.getKey());
            if (p == null) { it.remove(); continue; }
            humans.get(en.getValue().team).add(p);
        }
        if (HUMANS.isEmpty()) { finish(server, null, false); return; }

        // носитель бомбы: случайный из Т (человек или будущий бот)
        int carrier = RND.nextInt(TEAM_SIZE);
        for (Team t : Team.values()) {
            float yaw = t == Team.T ? -90f : 90f;
            int idx = 0;
            for (ServerPlayerEntity p : humans.get(t)) {
                Human h = HUMANS.get(p.getUuid());
                GameMap.Pt sp = map.spawn(t == Team.CT, idx);
                h.sx = sp.cx(); h.sy = sp.y(); h.sz = sp.cz(); h.yaw = yaw;
                setupHuman(p, h, w, t == Team.T && idx == carrier, SURVIVED.contains(p.getUuid()));
                MatchStats.join(p);
                idx++;
            }
            BotBuy buy = botBuy(t);
            for (; idx < TEAM_SIZE; idx++) {
                BotEntity b = ModEntities.BOT.create(w);
                if (b == null) continue;
                GameMap.Pt sp = map.spawn(t == Team.CT, idx);
                b.refreshPositionAndAngles(sp.cx(), sp.y(), sp.cz(), yaw, 0f);
                b.setHeadYaw(yaw); b.setBodyYaw(yaw);
                b.setup(t, buy.gun(RND), t == Team.CT && buy.kit && RND.nextInt(3) > 0, t == Team.T ? (lurk && idx == TEAM_SIZE - 1 ? 'L' : 'A') : (idx % 3 == 0 ? 'A' : idx % 3 == 1 ? 'B' : 'M'));
                String bn = BOTNAME[t.ordinal()][idx];
                b.setIdentity(bn);
                b.setAgent(RND.nextInt(3));
                b.setCustomName(Text.literal(t.color + "[" + t.tag + "] §fБот " + bn));
                b.setCustomNameVisible(false);
                if (t == Team.T && idx == carrier) b.giveBomb();
                w.spawnEntity(b);
                BOTS.add(b.getUuid());
                MatchStats.join(b);
            }
        }
        SURVIVED.clear();
        for (ServerPlayerEntity p : players(server)) sendState(p);
        broadcast("§e§lРаунд " + round + " §7— счёт §6T " + winsT + " §7: §9" + winsCT + " CT§7. Закупка " + FREEZE_TICKS / 20 + " с (§eE§7).");
        title("§eРаунд " + round, "§7Т " + winsT + " : " + winsCT + " КТ");
    }

    /** Имена ботов по сторонам и слотам — постоянные на весь матч (по ним ведётся статистика) */
    private static final String[][] BOTNAME = new String[2][TEAM_SIZE];
    private static final String[] BOT_NAMES = {"Алекс", "Дима", "Макс", "Витя", "Кирилл", "Серёга", "Тимур", "Артём", "Влад", "Егор", "Рома", "Паша"};

    /** Закупка ботов: у каждой стороны общий «средний кошелёк», боты закупаются вместе (раунд экономии, форс, полная закупка) */
    private record BotBuy(String[] pool, boolean kit) {
        String gun(Random r) { return pool[r.nextInt(pool.length)]; }
    }

    private static BotBuy botBuy(Team t) {
        int i = t.ordinal(), m = BOT_MONEY[i];
        boolean T = t == Team.T;
        String[] pool; int cost;
        if (m >= 3700) {
            pool = T ? new String[]{"ak47", "ak47", "ak47", "ak47", "galil", "awp"} : new String[]{"m4a4", "m4a4", "m4a1s", "m4a1s", "famas", "awp"};
            cost = T ? 2900 : 3300;
        } else if (m >= 2000) {
            pool = T ? new String[]{"galil", "galil", "mac10", "mp7", "sawedoff", "xm1014"} : new String[]{"famas", "famas", "mp9", "mp7", "mag7", "xm1014"};
            cost = 1700;
        } else if (m >= 1000) {
            pool = T ? new String[]{"deagle", "mac10", "glock18", "revolver"} : new String[]{"deagle", "fiveseven", "mp9", "usp"};
            cost = 700;
        } else {
            pool = T ? new String[]{"glock18"} : new String[]{"usp"};
            cost = 0;
        }
        BOT_MONEY[i] = Math.max(0, m - cost);
        boolean kit = !T && BOT_MONEY[i] >= 400;
        if (kit) BOT_MONEY[i] -= 300;
        return new BotBuy(pool, kit);
    }

    private static void cleanMap(ServerWorld w) {
        for (UUID id : BOTS) { Entity e = w.getEntity(id); if (e != null) e.discard(); }
        BOTS.clear();
        for (BombEntity b : w.getEntitiesByType(ModEntities.BOMB, e -> true)) b.discard();
        for (ItemEntity i : w.getEntitiesByType(net.minecraft.entity.EntityType.ITEM, e -> true)) i.discard();
    }

    private static Item itemOf(String id) {
        Item it = Registries.ITEM.get(Identifier.of(ComboRush.MOD_ID, id));
        return it == Items.AIR ? null : it;
    }

    private static void give(ServerPlayerEntity p, String id) {
        Item it = itemOf(id);
        if (it != null) p.getInventory().offerOrDrop(new ItemStack(it));
    }

    private static ItemStack stackOf(String id) {
        Item it = itemOf(id);
        return it == null ? ItemStack.EMPTY : new ItemStack(it);
    }

    private static String knifeFor(ServerPlayerEntity p, Team team) {
        String knife = Loadout.equipped(p, Loadout.KNIFE_SLOT);
        if (knife.equals("knife_t") || knife.equals("knife_ct")) knife = team == Team.T ? "knife_t" : "knife_ct";
        return knife;
    }

    /** keep — игрок пережил прошлый раунд: оружие остаётся, иначе — нож и стандартный пистолет стороны */
    private static void setupHuman(ServerPlayerEntity p, Human h, ServerWorld w, boolean carrier, boolean keep) {
        p.changeGameMode(GameMode.ADVENTURE);
        p.stopRiding();
        var inv = p.getInventory();
        if (keep) {
            for (int i = 0; i < inv.size(); i++) if (inv.getStack(i).getItem() instanceof BombItem) inv.setStack(i, ItemStack.EMPTY);
        } else {
            inv.clear();
            boolean t = h.team == Team.T;
            inv.setStack(BuyDefs.SLOT_PISTOL, stackOf(Loadout.equipped(p, t ? "glock18" : "usp")));
        }
        // нож всегда под текущую сторону (после смены сторон стандартный нож меняется)
        inv.setStack(BuyDefs.SLOT_KNIFE, stackOf(knifeFor(p, h.team)));
        if (carrier) inv.setStack(BuyDefs.SLOT_GEAR, stackOf("bomb"));
        p.clearStatusEffects();
        p.setHealth(p.getMaxHealth());
        p.extinguish();
        p.getAbilities().flying = false;
        p.getHungerManager().setFoodLevel(10);
        p.fallDistance = 0;
        p.teleport(w, h.sx, h.sy, h.sz, h.yaw, 0f);
        inv.selectedSlot = inv.getStack(BuyDefs.SLOT_PRIMARY).isEmpty() ? BuyDefs.SLOT_PISTOL : BuyDefs.SLOT_PRIMARY;
        p.playerScreenHandler.sendContentUpdates();
    }

    // ---------------------------------------------------------------- инвентарь игрока на время матча
    private static void stash(ServerPlayerEntity p) {
        if (p.hasAttached(STASH)) return;
        NbtCompound c = new NbtCompound();
        c.put("inv", p.getInventory().writeNbt(new NbtList()));
        p.setAttached(STASH, c);
        p.getInventory().clear();
    }

    private static void restore(ServerPlayerEntity p) {
        NbtCompound c = p.getAttached(STASH);
        if (c == null) return;
        p.getInventory().clear();
        p.getInventory().readNbt(c.getList("inv", 10));
        p.removeAttached(STASH);
        p.playerScreenHandler.sendContentUpdates();
    }

    // ---------------------------------------------------------------- тик матча
    private static void tick(MinecraftServer server) {
        if (phase == Phase.IDLE) return;
        ServerWorld w = server.getWorld(map.world);
        if (w == null) { finish(server, null, false); return; }

        // вышедших с карты (например, кнопка «Вселенные») убираем из матча
        for (UUID id : new ArrayList<>(HUMANS.keySet())) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p == null) { HUMANS.remove(id); continue; }
            if (!Universes.inDust(p)) leave(p, false);
        }
        if (HUMANS.isEmpty()) { finish(server, null, false); return; }
        if (phase == Phase.IDLE) return;

        timer--;
        switch (phase) {
            case FREEZE -> {
                for (var en : HUMANS.entrySet()) {
                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(en.getKey());
                    if (p == null) continue;
                    Human h = en.getValue();
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 6, false, false, false));
                    double dx = p.getX() - h.sx, dz = p.getZ() - h.sz;
                    if (dx * dx + dz * dz > 0.5) p.teleport(w, h.sx, h.sy, h.sz, p.getYaw(), p.getPitch());
                }
                if (timer <= 0) {
                    phase = Phase.LIVE;
                    timer = ROUND_TICKS;
                    for (ServerPlayerEntity p : players(server)) {
                        p.removeStatusEffect(StatusEffects.SLOWNESS);
                        w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 1.0f, 1.5f);
                    }
                    title("§a§lВперёд!", "§7Закладка бомбы — точки A и B");
                }
            }
            case LIVE -> {
                liveTicks++;
                if (liveTicks % 20 == 1) {
                    int n = 0;
                    for (UUID id : BOTS) if (w.getEntity(id) instanceof BotEntity b && b.isAlive() && b.team() == Team.T) n++;
                    aliveTBots = n;
                }
                if (timer % 5 == 0) checkRound(server, w);
                if (phase == Phase.LIVE && timer <= 0 && !planted) endRound(server, Team.CT, "Время вышло");
            }
            case END -> {
                if (timer <= 0) {
                    if (over) finish(server, draw ? null : winsT >= WIN_ROUNDS ? Team.T : Team.CT, draw);
                    else {
                        if (halftime) swapSides(server);
                        startRound(server, w);
                    }
                }
            }
            default -> { }
        }
        if (phase != Phase.IDLE && server.getTicks() % 10 == 0) updateBar(w);
        if (phase != Phase.IDLE && server.getTicks() % 10 == 0) {
            for (ServerPlayerEntity p : players(server)) sendState(p);
            sendTable(server, w);
        }
        if (phase != Phase.IDLE && server.getTicks() % 100 == 0)
            for (ServerPlayerEntity p : players(server)) p.getHungerManager().setFoodLevel(10);
    }

    private static List<ServerPlayerEntity> players(MinecraftServer server) {
        List<ServerPlayerEntity> l = new ArrayList<>();
        for (UUID id : HUMANS.keySet()) { ServerPlayerEntity p = server.getPlayerManager().getPlayer(id); if (p != null) l.add(p); }
        return l;
    }

    private static int alive(MinecraftServer server, ServerWorld w, Team t) {
        int n = 0;
        for (var en : HUMANS.entrySet()) {
            if (en.getValue().team != t) continue;
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(en.getKey());
            if (p != null && p.isAlive() && !p.isSpectator()) n++;
        }
        for (UUID id : BOTS) if (w.getEntity(id) instanceof BotEntity b && b.isAlive() && b.team() == t) n++;
        return n;
    }

    private static void checkRound(MinecraftServer server, ServerWorld w) {
        if (pendingWinner != null) { endRound(server, pendingWinner, pendingReason); return; }
        int aT = alive(server, w, Team.T), aCT = alive(server, w, Team.CT);
        if (aCT == 0) endRound(server, Team.T, planted ? "Спецназ уничтожен, бомба заложена" : "Спецназ уничтожен");
        else if (!planted && aT == 0) endRound(server, Team.CT, "Террористы уничтожены");
    }

    private static void endRound(MinecraftServer server, Team winner, String reason) {
        if (phase != Phase.LIVE) return;
        ServerWorld w = server.getWorld(map.world);
        phase = Phase.END;
        if (winner == Team.T) winsT++; else winsCT++;
        Team loser = winner.opposite();
        // деньги: победителям 3250 (3500 за взрыв/разминирование), проигравшим 1400 + 500 за каждое следующее поражение подряд (до 3400)
        boolean bombWin = reason.startsWith("Бомба");
        LOSS[winner.ordinal()] = 0;
        LOSS[loser.ordinal()] = Math.min(5, LOSS[loser.ordinal()] + 1);
        int winPay = bombWin ? 3500 : 3250;
        int lossPay = Math.min(3400, 1400 + 500 * (LOSS[loser.ordinal()] - 1)) + (loser == Team.T && planted ? 800 : 0);
        SURVIVED.clear();
        LAST_PAY.clear();
        for (var en : HUMANS.entrySet()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(en.getKey());
            if (p == null) continue;
            if (p.isAlive() && !p.isSpectator()) SURVIVED.add(p.getUuid());
            int pay = en.getValue().team == winner ? winPay : lossPay;
            LAST_PAY.put(p.getUuid(), pay);
            addMoney(p, pay);
        }
        BOT_MONEY[winner.ordinal()] = Math.min(BuyDefs.MAX_MONEY, BOT_MONEY[winner.ordinal()] + winPay);
        BOT_MONEY[loser.ordinal()] = Math.min(BuyDefs.MAX_MONEY, BOT_MONEY[loser.ordinal()] + lossPay);

        String mvp = MatchStats.mvp(winner, bombWin);
        if (mvp != null) broadcast("§6★ MVP раунда: §f" + mvp);
        draw = winsT == DRAW_AT && winsCT == DRAW_AT;
        over = winsT >= WIN_ROUNDS || winsCT >= WIN_ROUNDS || draw;
        halftime = !over && round == HALF;
        timer = over ? OVER_TICKS : halftime ? HALF_TICKS : END_TICKS;
        broadcast(winner.coloredTag() + " §fвыигрывают раунд §7— " + reason + ". §fСчёт §6T " + winsT + " §7: §9" + winsCT + " CT");
        if (draw) broadcast("§e§lМатч окончен вничью! §7(" + winsT + " : " + winsCT + ")");
        else if (over) broadcast("§6§lМатч окончен! §fПобеда: " + winner.coloredTag() + " §7(" + winsT + " : " + winsCT + ")");
        else if (halftime) broadcast("§e§lПерерыв: смена сторон! §7Деньги обнуляются до $" + BuyDefs.START_MONEY + ", оружие сдаётся.");

        // анимация конца раунда — каждому со своей стороны
        for (var en : HUMANS.entrySet()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(en.getKey());
            if (p == null) continue;
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            boolean won = en.getValue().team == winner;
            o.addProperty("win", won);
            o.addProperty("side", en.getValue().team == Team.T ? "t" : "ct");
            o.addProperty("winner", winner == Team.T ? "t" : "ct");
            o.addProperty("reason", reason);
            o.addProperty("wt", winsT);
            o.addProperty("wct", winsCT);
            o.addProperty("round", round);
            o.addProperty("pay", LAST_PAY.getOrDefault(p.getUuid(), 0));
            o.addProperty("kind", draw ? "draw" : over ? "match" : halftime ? "half" : "round");
            if (mvp != null) o.addProperty("mvp", mvp);
            o.addProperty("ticks", timer);
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new com.derevna230.comborush.hud.HudPayload("mround", o.toString()));
        }
        if (w != null) updateBar(w);
    }

    /** Смена сторон после {@link #HALF}-го раунда: счёт и команды меняются местами, деньги и оружие — с нуля */
    private static void swapSides(MinecraftServer server) {
        int t = winsT; winsT = winsCT; winsCT = t;
        MatchStats.swapSides();
        String[] tmp = BOTNAME[0]; BOTNAME[0] = BOTNAME[1]; BOTNAME[1] = tmp;
        LOSS[0] = LOSS[1] = 0;
        BOT_MONEY[0] = BOT_MONEY[1] = BuyDefs.START_MONEY;
        SURVIVED.clear();
        halftime = false;
        for (var en : HUMANS.entrySet()) {
            Human h = en.getValue();
            h.team = h.team.opposite();
            MONEY.put(en.getKey(), BuyDefs.START_MONEY);
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(en.getKey());
            if (p != null) {
                setSide(p, h.team);
                p.sendMessage(Text.literal("§e§lСмена сторон! §fТеперь вы играете за " + h.team.coloredTag() + "§f."), false);
            }
        }
    }

    // ---------------------------------------------------------------- деньги и закупка
    public static int money(ServerPlayerEntity p) { return MONEY.getOrDefault(p.getUuid(), 0); }

    private static void addMoney(ServerPlayerEntity p, int d) {
        if (!MONEY.containsKey(p.getUuid())) return;
        MONEY.put(p.getUuid(), Math.max(0, Math.min(BuyDefs.MAX_MONEY, money(p) + d)));
    }

    private static String baseInHand(LivingEntity e) {
        ItemStack st = e.getMainHandStack();
        if (st.isEmpty()) return null;
        Identifier id = Registries.ITEM.getId(st.getItem());
        return com.derevna230.comborush.item.GunDefs.baseOf(id.getPath());
    }

    /** Награда за убийство: людям — на счёт, ботам — в общий кошелёк стороны */
    private static void killReward(Entity killer, Team victimTeam) {
        if (!(killer instanceof LivingEntity le) || phase != Phase.LIVE) return;
        Team kt = teamOf(killer);
        if (kt == null || kt == victimTeam) return;
        int r = BuyDefs.killReward(baseInHand(le));
        if (killer instanceof ServerPlayerEntity p) addMoney(p, r);
        else BOT_MONEY[kt.ordinal()] = Math.min(BuyDefs.MAX_MONEY, BOT_MONEY[kt.ordinal()] + r / 2);
    }

    private static int buyTicksLeft() {
        if (phase == Phase.FREEZE) return timer + BUY_TICKS;
        if (phase == Phase.LIVE) return Math.max(0, BUY_TICKS - liveTicks);
        return 0;
    }

    private static boolean inBuyZone(ServerPlayerEntity p, Human h) {
        for (int i = 0; i < TEAM_SIZE; i++) {
            GameMap.Pt sp = map.spawn(h.team == Team.CT, i);
            double dx = p.getX() - sp.cx(), dz = p.getZ() - sp.cz();
            if (dx * dx + dz * dz <= BUY_RADIUS * BUY_RADIUS) return true;
        }
        return false;
    }

    /** null — можно, иначе причина */
    private static String buyBlock(ServerPlayerEntity p, Human h) {
        if (h == null) return "Вы не в матче.";
        if (!p.isAlive() || p.isSpectator()) return "Погибшие не закупаются.";
        if (buyTicksLeft() <= 0) return "Время закупки вышло.";
        if (!inBuyZone(p, h)) return "Закупаться можно только на своей базе.";
        return null;
    }

    public static void handleNet(ServerPlayerEntity p, com.derevna230.comborush.hud.ShopActionPayload a) {
        if (a.action().equals("m_buy")) {
            String err = buy(p, a.c());
            if (err != null) p.sendMessage(Text.literal("§c" + err), true);
        }
        sendState(p);
    }

    private static String buy(ServerPlayerEntity p, String id) {
        Human h = HUMANS.get(p.getUuid());
        String block = buyBlock(p, h);
        if (block != null) return block;
        BuyDefs.Entry e = BuyDefs.get(id);
        if (e == null) return "Такого товара нет.";
        if (!BuyDefs.allowed(e, h.team)) return e.name() + " — только для " + (e.side() == Team.T ? "террористов" : "спецназа") + ".";
        if (money(p) < e.price()) return "Не хватает денег: нужно $" + e.price() + ".";
        var inv = p.getInventory();
        int slot = e.slot();
        if (slot == BuyDefs.SLOT_GREN0) {
            int free = -1, count = 0;
            for (int i = BuyDefs.SLOT_GREN0; i <= BuyDefs.SLOT_GREN1; i++) {
                ItemStack st = inv.getStack(i);
                if (st.isEmpty()) { if (free < 0) free = i; continue; }
                count++;
                if (com.derevna230.comborush.item.GunDefs.baseOf(Registries.ITEM.getId(st.getItem()).getPath()).equals(id)) return "Эта граната уже есть.";
            }
            if (free < 0 || count >= 4) return "Больше гранат не унести.";
            slot = free;
        } else {
            ItemStack cur = inv.getStack(slot);
            if (!cur.isEmpty()) {
                String base = com.derevna230.comborush.item.GunDefs.baseOf(Registries.ITEM.getId(cur.getItem()).getPath());
                if (base.equals(id)) return "Это уже есть.";
                if (cur.getItem() instanceof BombItem) return "Слот занят бомбой.";
                p.dropItem(cur.copy(), false, false);        // старый ствол — на землю (его может подобрать союзник)
            }
        }
        String item = id.equals("defuse_kit") ? "defuse_kit" : Loadout.equipped(p, id);
        ItemStack st = stackOf(item);
        if (st.isEmpty()) return "Предмет недоступен.";
        inv.setStack(slot, st);
        addMoney(p, -e.price());
        if (slot <= BuyDefs.SLOT_PISTOL) inv.selectedSlot = slot;
        p.playerScreenHandler.sendContentUpdates();
        p.playSoundToPlayer(SoundEvents.ITEM_ARMOR_EQUIP_GENERIC.value(), SoundCategory.PLAYERS, 0.8f, 1.2f);
        return null;
    }

    /** Состояние для клиента: деньги, закупка, сторона, место, счёт (каждые полсекунды) */
    private static void sendState(ServerPlayerEntity p) {
        Human h = HUMANS.get(p.getUuid());
        if (h == null || !net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, com.derevna230.comborush.hud.HudPayload.ID)) return;
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("money", money(p));
        o.addProperty("buy", buyTicksLeft());
        o.addProperty("zone", inBuyZone(p, h));
        o.addProperty("side", h.team == Team.T ? "t" : "ct");
        o.addProperty("round", round);
        o.addProperty("wt", winsT);
        o.addProperty("wct", winsCT);
        o.addProperty("place", map.zoneAt(p.getX(), p.getZ()));
        o.addProperty("phase", phase.name());
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new com.derevna230.comborush.hud.HudPayload("mstate", o.toString()));
    }

    /** Конец матча: награда, возврат инвентаря, все в меню */
    private static void finish(MinecraftServer server, Team winner, boolean wasDraw) {
        ServerWorld w = server.getWorld(map.world);
        BombEntity.LISTENER = null;
        List<ServerPlayerEntity> list = players(server);
        Map<UUID, Team> teams = new HashMap<>();
        for (var en : HUMANS.entrySet()) teams.put(en.getKey(), en.getValue().team);
        Map<UUID, GameMode> modes = new HashMap<>();
        for (var en : HUMANS.entrySet()) modes.put(en.getKey(), en.getValue().prev);
        phase = Phase.IDLE;
        HUMANS.clear();
        MONEY.clear(); SURVIVED.clear();
        for (ServerPlayerEntity p : list)
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new com.derevna230.comborush.hud.HudPayload("mstate", "{\"off\":true}"));
        if (w != null) cleanMap(w);
        if (bar != null) { bar.clearPlayers(); bar = null; }
        for (ServerPlayerEntity p : list) {
            clearSide(p);
            p.removeStatusEffect(StatusEffects.SLOWNESS);
            p.changeGameMode(modes.getOrDefault(p.getUuid(), GameMode.SURVIVAL));
            restore(p);
            if (winner != null || wasDraw) reward(p, teams.get(p.getUuid()), winner, wasDraw);
            if (GameMaps.isMatchWorld(p.getWorld())) Standoff.toMenu(p);
        }
    }

    // ---------------------------------------------------------------- события
    private static final BombEntity.Listener LISTENER = new BombEntity.Listener() {
        @Override public void planted(ServerWorld w, BombEntity bomb, Entity owner) {
            if (phase != Phase.LIVE) return;
            planted = true;
            bombId = bomb.getUuid();
            if (owner instanceof ServerPlayerEntity sp) addMoney(sp, 300);
            MatchStats.Row pr = MatchStats.row(owner);
            if (pr != null) { pr.plants++; MatchStats.planter = pr.key; }
            char site = map.siteAt(bomb.getX(), bomb.getZ());
            broadcast("§c§lБомба заложена" + (site == 0 ? "" : " на точке §e" + site + " §7(" + map.zoneAt(bomb.getX(), bomb.getZ()) + ")§c") + "§c! §fСпецназ, обезвреживайте.");
        }
        @Override public void exploded(ServerWorld w) {
            if (phase == Phase.LIVE) { pendingWinner = Team.T; pendingReason = "Бомба взорвалась"; }
        }
        @Override public void defused(ServerWorld w, LivingEntity by) {
            if (phase == Phase.LIVE) { pendingWinner = Team.CT; pendingReason = "Бомба обезврежена"; }
            if (by instanceof ServerPlayerEntity sp) addMoney(sp, 300);
            MatchStats.Row dr = MatchStats.row(by);
            if (dr != null) { dr.defuses++; MatchStats.defuser = dr.key; }
        }
    };

    private static void onHumanDeath(ServerPlayerEntity p, DamageSource src) {
        if (phase == Phase.LIVE) {
            for (int i = 0; i < p.getInventory().size(); i++) {
                ItemStack st = p.getInventory().getStack(i);
                if (st.getItem() instanceof BombItem) { p.dropStack(st.copy()); break; }
            }
        }
        p.getInventory().clear();
        p.setHealth(p.getMaxHealth());
        p.changeGameMode(GameMode.SPECTATOR);
        Team t = teamOf(p);
        Entity at = src.getAttacker();
        killReward(at, t);
        feed(p, at, src);
    }

    static void onBotKilled(BotEntity bot, DamageSource src) {
        Entity at = src.getAttacker();
        killReward(at, bot.team());
        feed(bot, at, src);
    }

    /** Лента убийств: всем участникам — кто, чем, хедшот; «me» — если это ты убил или тебя */
    private static void feed(LivingEntity victim, Entity killer, DamageSource src) {
        if (phase == Phase.IDLE) return;
        com.google.gson.JsonObject o = MatchStats.kill(victim, killer);
        String w = o.get("w").getAsString();
        if (killer == null || killer == victim) {
            if (src.isOf(DamageTypes.EXPLOSION) || src.isOf(DamageTypes.PLAYER_EXPLOSION)) w = "bomb";
            else if (src.isOf(DamageTypes.FALL)) w = "fall";
            else w = "world";
            o.addProperty("w", w);
        }
        Team vt = teamOf(victim), kt = killer == null ? null : teamOf(killer);
        o.addProperty("v", victim instanceof BotEntity b ? b.botName() : victim.getName().getString());
        o.addProperty("vt", vt == null ? "" : vt == Team.T ? "t" : "ct");
        if (killer != null && killer != victim) {
            o.addProperty("k", killer instanceof BotEntity b ? b.botName() : killer.getName().getString());
            o.addProperty("kt", kt == null ? "" : kt == Team.T ? "t" : "ct");
        }
        MinecraftServer server = victim.getServer();
        if (server == null) return;
        for (ServerPlayerEntity p : players(server)) {
            com.google.gson.JsonObject c = o.deepCopy();
            c.addProperty("me", p == killer || p == victim);
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new com.derevna230.comborush.hud.HudPayload("mkill", c.toString()));
        }
    }

    /** Таблица Tab: всем участникам раз в полсекунды */
    private static void sendTable(MinecraftServer server, ServerWorld w) {
        Map<String, Boolean> alive = new HashMap<>();
        Map<String, Integer> money = new HashMap<>();
        for (var en : HUMANS.entrySet()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(en.getKey());
            if (p == null) continue;
            alive.put(p.getUuidAsString(), p.isAlive() && !p.isSpectator());
            money.put(p.getUuidAsString(), money(p));
        }
        for (UUID id : BOTS) if (w.getEntity(id) instanceof BotEntity b && b.isAlive()) alive.put(b.statKey(), true);
        for (ServerPlayerEntity p : players(server)) {
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.add("rows", MatchStats.table(alive, money, p.getUuidAsString()));
            o.addProperty("wt", winsT); o.addProperty("wct", winsCT); o.addProperty("round", round);
            o.addProperty("map", map.title);
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new com.derevna230.comborush.hud.HudPayload("mtab", o.toString()));
        }
    }

    /** Награда за матч: эссенция и шанс кейса — тем больше, чем лучше сыграл */
    private static void reward(ServerPlayerEntity p, Team team, Team winner, boolean wasDraw) {
        MatchStats.Row r = MatchStats.ROWS.get(p.getUuidAsString());
        if (r == null) return;
        boolean won = winner != null && team == winner;
        int top = 0;
        for (MatchStats.Row x : MatchStats.ROWS.values()) top = Math.max(top, x.score());
        boolean best = r.score() > 0 && r.score() >= top;
        com.google.gson.JsonArray lines = new com.google.gson.JsonArray();
        int total = 0;
        int[][] parts = {{25}, {r.kills * 6}, {r.assists * 3}, {r.mvps * 10}, {(r.plants + r.defuses) * 8}, {Math.round(r.damage * 0.5f)},
            {won ? 60 : wasDraw ? 30 : 0}, {best ? 25 : 0}};
        String[] names = {"Участие", "Убийства ×" + r.kills, "Помощь ×" + r.assists, "MVP ×" + r.mvps, "Бомба ×" + (r.plants + r.defuses),
            "Урон " + Math.round(r.damage * 5), won ? "Победа" : wasDraw ? "Ничья" : "Поражение", "Лучший игрок матча"};
        for (int i = 0; i < parts.length; i++) {
            if (parts[i][0] <= 0 && i != 6) continue;
            com.google.gson.JsonObject l = new com.google.gson.JsonObject();
            l.addProperty("n", names[i]); l.addProperty("v", parts[i][0]);
            lines.add(l);
            total += parts[i][0];
        }
        p.setAttached(com.derevna230.comborush.data.PlayerProfile.TYPE, com.derevna230.comborush.data.PlayerProfile.of(p).addEssence(total));
        double chance = Math.min(0.65, 0.12 + 0.015 * r.kills + 0.04 * r.mvps + (won ? 0.12 : wasDraw ? 0.06 : 0) + (best ? 0.05 : 0));
        String caseName = RND.nextDouble() < chance ? Standoff.giveCaseNamed(p) : null;
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("result", wasDraw ? "draw" : won ? "win" : "loss");
        o.addProperty("score", (team == Team.T ? winsT : winsCT) + " : " + (team == Team.T ? winsCT : winsT));
        o.addProperty("k", r.kills); o.addProperty("d", r.deaths); o.addProperty("a", r.assists); o.addProperty("mvp", r.mvps);
        o.addProperty("dmg", Math.round(r.damage * 5)); o.addProperty("hs", r.headshots);
        o.addProperty("essence", total);
        o.add("lines", lines);
        o.addProperty("chance", Math.round(chance * 100));
        if (caseName != null) o.addProperty("case", caseName);
        o.addProperty("best", best);
        p.sendMessage(Text.literal("§6§lИтоги матча: §d+" + total + " ✦ эссенции" + (caseName != null ? " §7и §6кейс «" + caseName + "»" : "")), false);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new com.derevna230.comborush.hud.HudPayload("mresult", o.toString()));
    }

    // ---------------------------------------------------------------- сообщения
    private static void broadcast(String msg) {
        Text t = Text.literal(msg);
        for (ServerPlayerEntity p : barPlayers()) p.sendMessage(t, false);
    }

    private static Collection<ServerPlayerEntity> barPlayers() {
        return bar == null ? List.of() : bar.getPlayers();
    }

    private static void title(String main, String sub) {
        for (ServerPlayerEntity p : barPlayers()) {
            p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(sub)));
            p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(main)));
        }
    }

    private static void updateBar(ServerWorld w) {
        if (bar == null) return;
        MinecraftServer server = w.getServer();
        int aT = alive(server, w, Team.T), aCT = alive(server, w, Team.CT);
        String time;
        float pct;
        if (phase == Phase.FREEZE) { time = "§7Закупка " + (timer + 19) / 20 + " с"; pct = timer / (float) FREEZE_TICKS; }
        else if (planted && phase == Phase.LIVE) {
            BombEntity b = bomb(w);
            int s = b == null ? 0 : (b.remainTicks() + 19) / 20;
            time = "§c§lБОМБА " + s + " с"; pct = b == null ? 0 : b.remainTicks() / (float) BombEntity.FUSE_TICKS;
        } else if (phase == Phase.LIVE) {
            int s = (timer + 19) / 20;
            time = "§f" + s / 60 + ":" + String.format("%02d", s % 60); pct = timer / (float) ROUND_TICKS;
        } else { time = "§7Конец раунда"; pct = 0; }
        bar.setName(Text.literal("§eРаунд " + round + "  §6T " + winsT + " §7: §9" + winsCT + " CT   " + time
            + "   §7живы: §6T " + aT + " §7/ §9CT " + aCT));
        bar.setPercent(Math.max(0f, Math.min(1f, pct)));
        bar.setColor(planted ? BossBar.Color.RED : BossBar.Color.YELLOW);
    }

    /** Строка состояния для команды /cr match status */
    public static String status() {
        if (phase == Phase.IDLE) return "Матч не идёт.";
        return "Раунд " + round + " (до " + WIN_ROUNDS + ", смена сторон после " + HALF + "), счёт T " + winsT + " : " + winsCT + " CT, фаза " + phase + ", игроков " + HUMANS.size() + ", ботов " + BOTS.size();
    }
}
