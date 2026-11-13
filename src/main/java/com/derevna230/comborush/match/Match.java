package com.derevna230.comborush.match;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.item.BombEntity;
import com.derevna230.comborush.item.BombItem;
import com.derevna230.comborush.item.ModItems;
import com.derevna230.comborush.lobby.Loadout;
import com.derevna230.comborush.lobby.Party;
import com.derevna230.comborush.lobby.Standoff;
import com.derevna230.comborush.pet.ModEntities;
import com.derevna230.comborush.universe.DustMap;
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
 * Матч «Противостояния» на карте «Дюны»: до {@value #WIN_ROUNDS} побед, раунд = заморозка → бой → пауза.
 * <ul>
 * <li>Т побеждают, взорвав бомбу или уничтожив спецназ; КТ — обезвредив бомбу, уничтожив Т до закладки или дотянув до конца времени.</li>
 * <li>Команды добираются ботами до {@value #TEAM_SIZE} человек. Живые игроки получают ствол/нож по своему снаряжению из меню; свой инвентарь на матч прячется и возвращается после.</li>
 * <li>Погибший игрок становится наблюдателем до следующего раунда. Огня по своим нет.</li>
 * </ul>
 * Одновременно идёт один матч на сервер.
 */
public final class Match {
    private Match() {}

    public static final int TEAM_SIZE = 5, WIN_ROUNDS = 4;
    private static final int FREEZE_TICKS = 5 * 20, ROUND_TICKS = 115 * 20, END_TICKS = 5 * 20, OVER_TICKS = 7 * 20;
    /** Урон ботов относительно «человеческого» урона ствола */
    public static final float BOT_DAMAGE = 0.9f;

    private enum Phase { IDLE, FREEZE, LIVE, END }

    private static final class Human {
        Team team; GameMode prev; double sx, sz; float yaw;
        Human(Team t, GameMode prev) { this.team = t; this.prev = prev; }
    }

    private static final AttachmentType<NbtCompound> STASH = AttachmentRegistry.create(
        Identifier.of(ComboRush.MOD_ID, "match_stash"), b -> b.persistent(NbtCompound.CODEC));

    private static final Map<UUID, Human> HUMANS = new LinkedHashMap<>();
    private static final List<UUID> BOTS = new ArrayList<>();
    private static Phase phase = Phase.IDLE;
    private static int timer, round, winsT, winsCT;
    private static boolean planted, over;
    private static UUID bombId;
    private static char targetSite = 'A';
    private static Team pendingWinner;
    private static String pendingReason = "";
    private static ServerBossBar bar;
    private static final Random RND = new Random();
    private static int botSerial;

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
            if (phase == Phase.IDLE || !e.getWorld().getRegistryKey().equals(Universes.DUST)) return true;
            if (src.isOf(DamageTypes.GENERIC_KILL) || src.isOf(DamageTypes.OUT_OF_WORLD)) return true;
            if (!(e instanceof ServerPlayerEntity || e instanceof BotEntity)) return true;
            if (phase != Phase.LIVE) return false;                                  // заморозка и пауза: урона нет
            Team vt = teamOf(e);
            Entity at = src.getAttacker();
            if (at != null && at != e && vt != null && teamOf(at) == vt) return false;   // по своим не бьём
            return true;
        });
        ServerLivingEntityEvents.ALLOW_DEATH.register((e, src, amount) -> {
            if (phase != Phase.IDLE && e instanceof ServerPlayerEntity p && HUMANS.containsKey(p.getUuid())
                    && p.getWorld().getRegistryKey().equals(Universes.DUST)) {
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
            if (p.hasAttached(STASH)) restore(p);
            if (Universes.inDust(p)) {
                p.changeGameMode(GameMode.ADVENTURE);
                Standoff.toMenu(p);
            }
        });
    }

    // ---------------------------------------------------------------- старт и остановка
    /** Начать матч за команду team: игрок и его группа (из меню) попадают на карту, остальные места занимают боты. @return null или причина отказа */
    public static String start(ServerPlayerEntity host, Team team) {
        MinecraftServer server = host.getServer();
        if (phase != Phase.IDLE) return "Матч уже идёт.";
        ServerWorld w = server == null ? null : server.getWorld(Universes.DUST);
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
        for (ServerPlayerEntity p : group) {
            if (!Universes.inUniverse(p)) Universes.enterLobby(p, true);        // запомнить, откуда вернуть домой
            HUMANS.put(p.getUuid(), new Human(team, p.interactionManager.getGameMode()));
            stash(p);
        }
        round = 0; winsT = 0; winsCT = 0; over = false; planted = false; bombId = null; pendingWinner = null;
        BombEntity.LISTENER = LISTENER;
        bar = new ServerBossBar(Text.literal("Матч"), BossBar.Color.YELLOW, BossBar.Style.PROGRESS);
        for (ServerPlayerEntity p : group) bar.addPlayer(p);
        broadcast("§6§lМатч начинается! §fКарта «Дюны», до §e" + WIN_ROUNDS + "§f побед. Вы играете за " + team.coloredTag() + "§f.");
        startRound(server, w);
        return null;
    }

    /** Закончить матч сейчас и вернуть всех в меню */
    public static String stop(MinecraftServer server) {
        if (phase == Phase.IDLE) return "Матч не идёт.";
        finish(server, null);
        return null;
    }

    /** Игрок вышел из матча (команда, выход из вселенной, отключение) */
    public static void leave(ServerPlayerEntity p, boolean toMenu) {
        Human h = HUMANS.remove(p.getUuid());
        if (h == null) return;
        if (bar != null) bar.removePlayer(p);
        p.removeStatusEffect(StatusEffects.SLOWNESS);
        p.changeGameMode(h.prev);
        restore(p);
        if (toMenu && p.getWorld().getRegistryKey().equals(Universes.DUST)) Standoff.toMenu(p);
        if (HUMANS.isEmpty() && phase != Phase.IDLE && p.getServer() != null) finish(p.getServer(), null);
    }

    private static void loadMapChunks(ServerWorld w) {
        for (int cx = (DustMap.X0 - 1) >> 4; cx <= (DustMap.X1 + 1) >> 4; cx++)
            for (int cz = (DustMap.Z0 - 1) >> 4; cz <= (DustMap.Z1 + 1) >> 4; cz++) w.getChunk(cx, cz);
    }

    // ---------------------------------------------------------------- раунд

    private static void startRound(MinecraftServer server, ServerWorld w) {
        round++;
        phase = Phase.FREEZE;
        timer = FREEZE_TICKS;
        planted = false; bombId = null; pendingWinner = null;
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
        if (HUMANS.isEmpty()) { finish(server, null); return; }

        // носитель бомбы: случайный из Т (человек или будущий бот)
        int carrier = RND.nextInt(TEAM_SIZE);
        for (Team t : Team.values()) {
            float yaw = t == Team.T ? -90f : 90f;
            int idx = 0;
            for (ServerPlayerEntity p : humans.get(t)) {
                Human h = HUMANS.get(p.getUuid());
                DustMap.Pt sp = DustMap.spawn(t == Team.CT, idx);
                h.sx = sp.cx(); h.sz = sp.cz(); h.yaw = yaw;
                setupHuman(p, h, w, t == Team.T && idx == carrier);
                idx++;
            }
            for (; idx < TEAM_SIZE; idx++) {
                BotEntity b = ModEntities.BOT.create(w);
                if (b == null) continue;
                DustMap.Pt sp = DustMap.spawn(t == Team.CT, idx);
                b.refreshPositionAndAngles(sp.cx(), sp.y(), sp.cz(), yaw, 0f);
                b.setHeadYaw(yaw); b.setBodyYaw(yaw);
                b.setup(t, pickGun(t), idx % 2 == 0, t == Team.T ? 'A' : (idx % 3 == 0 ? 'A' : idx % 3 == 1 ? 'B' : 'M'));
                b.setCustomName(Text.literal(t.color + "[" + t.tag + "] §fБот " + BOT_NAMES[(botSerial++) % BOT_NAMES.length]));
                b.setCustomNameVisible(false);
                if (t == Team.T && idx == carrier) b.giveBomb();
                w.spawnEntity(b);
                BOTS.add(b.getUuid());
            }
        }
        broadcast("§e§lРаунд " + round + " §7— счёт §6T " + winsT + " §7: §9" + winsCT + " CT§7. До начала 5 секунд.");
        title("§eРаунд " + round, "§7Т " + winsT + " : " + winsCT + " КТ");
    }

    private static final String[] BOT_NAMES = {"Алекс", "Дима", "Макс", "Витя", "Кирилл", "Серёга", "Тимур", "Артём", "Влад", "Егор", "Рома", "Паша"};

    private static String pickGun(Team t) {
        String[] pool = t == Team.T
            ? new String[]{"ak47", "ak47", "ak47", "ak47", "galil", "galil", "mac10", "p90", "nova", "awp"}
            : new String[]{"m4a4", "m4a4", "m4a4", "m4a4", "m4a1s", "m4a1s", "famas", "famas", "mp9", "mag7", "awp"};
        return pool[RND.nextInt(pool.length)];
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

    private static void setupHuman(ServerPlayerEntity p, Human h, ServerWorld w, boolean carrier) {
        p.changeGameMode(GameMode.ADVENTURE);
        p.stopRiding();
        p.getInventory().clear();
        p.clearStatusEffects();
        p.setHealth(p.getMaxHealth());
        p.extinguish();
        p.getAbilities().flying = false;
        p.getHungerManager().setFoodLevel(10);
        p.fallDistance = 0;
        // снаряжение из меню: основной ствол и пистолет по команде, нож — надетый (стандартный — под команду)
        boolean t = h.team == Team.T;
        give(p, Loadout.equipped(p, t ? "ak47" : "m4a4"));
        give(p, Loadout.equipped(p, t ? "glock18" : "usp"));
        String knife = Loadout.equipped(p, Loadout.KNIFE_SLOT);
        if (knife.equals("knife_t") || knife.equals("knife_ct")) knife = t ? "knife_t" : "knife_ct";
        give(p, knife);
        if (carrier) give(p, "bomb");
        if (!t) give(p, "defuse_kit");
        p.teleport(w, h.sx, DustMap.FLOOR + 1, h.sz, h.yaw, 0f);
        p.getInventory().selectedSlot = 0;
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
        ServerWorld w = server.getWorld(Universes.DUST);
        if (w == null) { finish(server, null); return; }

        // вышедших с карты (например, кнопка «Вселенные») убираем из матча
        for (UUID id : new ArrayList<>(HUMANS.keySet())) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p == null) { HUMANS.remove(id); continue; }
            if (!Universes.inDust(p)) leave(p, false);
        }
        if (HUMANS.isEmpty()) { finish(server, null); return; }
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
                    if (dx * dx + dz * dz > 0.5) p.teleport(w, h.sx, DustMap.FLOOR + 1, h.sz, p.getYaw(), p.getPitch());
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
                if (timer % 5 == 0) checkRound(server, w);
                if (phase == Phase.LIVE && timer <= 0 && !planted) endRound(server, Team.CT, "Время вышло");
            }
            case END -> {
                if (timer <= 0) {
                    if (over) finish(server, winsT >= WIN_ROUNDS ? Team.T : Team.CT);
                    else startRound(server, w);
                }
            }
            default -> { }
        }
        if (phase != Phase.IDLE && server.getTicks() % 10 == 0) updateBar(w);
        if (phase == Phase.LIVE && server.getTicks() % 10 == 0)
            for (ServerPlayerEntity p : players(server))
                p.sendMessage(Text.literal("§7Место: §f" + DustMap.zoneAt(p.getX(), p.getZ())), true);
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
        phase = Phase.END;
        timer = END_TICKS;
        if (winner == Team.T) winsT++; else winsCT++;
        over = winsT >= WIN_ROUNDS || winsCT >= WIN_ROUNDS;
        if (over) timer = OVER_TICKS;
        String line = winner.coloredTag() + " §fвыигрывают раунд §7— " + reason + ". §fСчёт §6T " + winsT + " §7: §9" + winsCT + " CT";
        broadcast(line);
        title(winner.color + "§l" + winner.title + " побеждают", "§7" + reason);
        if (over) broadcast("§6§lМатч окончен! §fПобеда: " + winner.coloredTag() + " §7(" + winsT + " : " + winsCT + ")");
    }

    /** Конец матча: награда, возврат инвентаря, все в меню */
    private static void finish(MinecraftServer server, Team winner) {
        ServerWorld w = server.getWorld(Universes.DUST);
        BombEntity.LISTENER = null;
        List<ServerPlayerEntity> list = players(server);
        Map<UUID, Team> teams = new HashMap<>();
        for (var en : HUMANS.entrySet()) teams.put(en.getKey(), en.getValue().team);
        Map<UUID, GameMode> modes = new HashMap<>();
        for (var en : HUMANS.entrySet()) modes.put(en.getKey(), en.getValue().prev);
        phase = Phase.IDLE;
        HUMANS.clear();
        if (w != null) cleanMap(w);
        if (bar != null) { bar.clearPlayers(); bar = null; }
        for (ServerPlayerEntity p : list) {
            p.removeStatusEffect(StatusEffects.SLOWNESS);
            p.changeGameMode(modes.getOrDefault(p.getUuid(), GameMode.SURVIVAL));
            restore(p);
            if (winner != null) Standoff.finishMatch(p, teams.get(p.getUuid()) == winner);
            if (p.getWorld().getRegistryKey().equals(Universes.DUST)) Standoff.toMenu(p);
        }
    }

    // ---------------------------------------------------------------- события
    private static final BombEntity.Listener LISTENER = new BombEntity.Listener() {
        @Override public void planted(ServerWorld w, BombEntity bomb, Entity owner) {
            if (phase != Phase.LIVE) return;
            planted = true;
            bombId = bomb.getUuid();
            char site = DustMap.siteAt(bomb.getX(), bomb.getZ());
            broadcast("§c§lБомба заложена" + (site == 0 ? "" : " на точке §e" + site + " §7(" + DustMap.zoneAt(bomb.getX(), bomb.getZ()) + ")§c") + "§c! §fСпецназ, обезвреживайте.");
        }
        @Override public void exploded(ServerWorld w) {
            if (phase == Phase.LIVE) { pendingWinner = Team.T; pendingReason = "Бомба взорвалась"; }
        }
        @Override public void defused(ServerWorld w, LivingEntity by) {
            if (phase == Phase.LIVE) { pendingWinner = Team.CT; pendingReason = "Бомба обезврежена"; }
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
        String killer = at == null ? "§7случай" : at.getDisplayName().getString();
        broadcast((t == null ? "" : t.color) + p.getName().getString() + " §7погиб от §f" + killer);
    }

    static void onBotKilled(BotEntity bot, DamageSource src) {
        Entity at = src.getAttacker();
        String killer = at == null ? "§7случай" : at.getDisplayName().getString();
        broadcast(bot.getDisplayName().getString() + " §7погиб от §f" + killer);
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
        return "Раунд " + round + ", счёт T " + winsT + " : " + winsCT + " CT, фаза " + phase + ", игроков " + HUMANS.size() + ", ботов " + BOTS.size();
    }
}
