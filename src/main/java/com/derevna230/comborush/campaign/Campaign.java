package com.derevna230.comborush.campaign;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.hud.HudPayload;
import com.derevna230.comborush.hud.HudSync;
import com.derevna230.comborush.item.ModItems;
import com.derevna230.comborush.pet.ModEntities;
import com.derevna230.comborush.rift.RiftManager;
import com.derevna230.comborush.species.SpeciesRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

/**
 * Сюжетная кампания «Сезон»: пролог и Акт I.
 * Прогресс общий на сервер (scope "server") с частью личных заданий (scope "personal").
 * Постройки (касса, стойки адресатов, афиши, площадка) ставятся рядом с игроком, когда задание началось.
 */
public final class Campaign {
    private Campaign() {}

    public enum St { LOCKED, ACTIVE, DONE }

    static final String COURIER = "act1_courier", POSTERS = "act1_posters", SPLICE = "act1_splice";
    private static final int COURIER_SECONDS = 180;
    private static final String[] RECIPIENT_KIND = {"lighter", "makeup", "costumer"};
    private static final String[] RECIPIENT_NAME = {"Осветитель Борис", "Гримёрша Вера", "Костюмер Гоша"};

    private static final Map<UUID, String> LAST_TRACK = new HashMap<>();
    private static final Map<UUID, String> LAST_JOURNAL = new HashMap<>();
    private static int boothRetry = 0, posterRetry = 0, stageRetry = 0, spliceWait = 0;

    // ================================================================ инициализация
    public static void init() {
        Objects.requireNonNull(PlayerCampaign.TYPE);          // зарегистрировать вложение до загрузки мира
        StageBoss.register();
        ServerPlayConnectionEvents.JOIN.register((h, s, server) -> {
            LAST_TRACK.remove(h.player.getUuid());
            LAST_JOURNAL.remove(h.player.getUuid());
            server.send(new net.minecraft.server.ServerTask(server.getTicks() + 60, () -> onJoin(h.player)));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> {
            LAST_TRACK.remove(h.player.getUuid());
            LAST_JOURNAL.remove(h.player.getUuid());
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> { boothRetry = posterRetry = stageRetry = spliceWait = 0; });
    }

    private static CampaignState state(ServerPlayerEntity p) { return CampaignState.get(p.getServer()); }

    // ================================================================ статусы
    public static boolean reqsMet(ServerPlayerEntity p, QuestDef q) {
        CampaignState st = state(p);
        PlayerCampaign pc = PlayerCampaign.of(p);
        for (String r : q.requires) {
            QuestDef rq = QuestRegistry.get(r);
            if (rq == null) continue;
            boolean done = rq.server() ? st.done.contains(r) : pc.isDone(r);
            if (!done) return false;
        }
        return true;
    }

    public static St status(ServerPlayerEntity p, QuestDef q) {
        boolean done = q.server() ? state(p).done.contains(q.id) : PlayerCampaign.of(p).isDone(q.id);
        if (done) return St.DONE;
        return reqsMet(p, q) ? St.ACTIVE : St.LOCKED;
    }

    public static boolean isActive(ServerPlayerEntity p, String id) {
        QuestDef q = QuestRegistry.get(id);
        return q != null && status(p, q) == St.ACTIVE;
    }

    public static int progress(ServerPlayerEntity p, QuestDef q) {
        return q.server() ? state(p).counter(q.id) : PlayerCampaign.of(p).counter(q.id);
    }

    /** Первое активное задание по порядку сюжета (личное или общее) */
    public static QuestDef current(ServerPlayerEntity p) {
        for (QuestDef q : QuestRegistry.all()) if (status(p, q) == St.ACTIVE) return q;
        return null;
    }

    // ================================================================ продвижение
    /** Действие игрока: type — тип цели, target — уточнение (роль NPC и т.п.), amount — сколько добавить */
    public static void trigger(ServerPlayerEntity p, String type, String target, int amount) {
        if (p == null || p.getServer() == null) return;
        // сначала собрать подходящие, чтобы только что открывшееся задание не засчиталось тем же действием
        List<QuestDef> hit = new ArrayList<>();
        for (QuestDef q : QuestRegistry.all()) {
            if (!q.goal.type.equals(type)) continue;
            if (!q.goal.target.isEmpty() && !target.isEmpty() && !q.goal.target.equals(target)) continue;
            if (status(p, q) == St.ACTIVE) hit.add(q);
        }
        for (QuestDef q : hit) if (status(p, q) == St.ACTIVE) setProgress(p, q, progress(p, q) + amount);
    }

    static void setProgress(ServerPlayerEntity p, QuestDef q, int value) {
        int v = Math.min(q.count(), Math.max(0, value));
        if (q.server()) {
            CampaignState st = state(p);
            st.counters.put(q.id, v);
            st.markDirty();
        } else PlayerCampaign.set(p, PlayerCampaign.of(p).withCounter(q.id, v));
        if (v >= q.count()) complete(p, q);
        else {
            p.sendMessage(Text.literal("§6✦ " + q.title + ": §f" + v + "/" + q.count()), true);
            refreshAll(p.getServer());
        }
    }

    /** Завершить задание. Для общего — награда всем, кто до него дошёл (оффлайн получат при входе) */
    public static void complete(ServerPlayerEntity p, QuestDef q) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        if (q.server()) {
            CampaignState st = CampaignState.get(server);
            if (!st.done.add(q.id)) return;
            st.counters.remove(q.id);
            st.markDirty();
            for (ServerPlayerEntity o : server.getPlayerManager().getPlayerList()) {
                if (!reqsMet(o, q)) continue;
                announce(o, q, o == p ? "" : p.getName().getString());
                claimServer(o);
            }
        } else {
            if (PlayerCampaign.of(p).isDone(q.id)) return;
            PlayerCampaign.set(p, PlayerCampaign.of(p).withDone(q.id));
            announce(p, q, "");
            giveReward(p, q);
            claimServer(p);                               // догнать общие задания, если они уже пройдены
        }
        ComboRush.LOGGER.info("Кампания: «{}» выполнено ({})", q.title, p.getName().getString());
        refreshAll(server);
    }

    private static void announce(ServerPlayerEntity p, QuestDef q, String byOther) {
        p.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 50, 15));
        p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("§6§l✦ СЦЕНА СЫГРАНА ✦")));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("§f" + q.title)));
        p.getServerWorld().playSound(null, p.getBlockPos(), com.derevna230.comborush.sound.ModSounds.QUEST_DONE, SoundCategory.PLAYERS, 0.9f, 1.0f);
        if (!byOther.isEmpty()) p.sendMessage(Text.literal("§6✦ §f" + byOther + " §7завершил общее задание «§f" + q.title + "§7»"), false);
        if (!q.lines_done.isEmpty()) say(p, q.speaker, q.lines_done);
        QuestDef next = current(p);
        if (next != null && !next.id.equals(q.id))
            p.sendMessage(Text.literal("§6✦ Новое задание: §e" + next.title + " §7— " + next.hint), false);
    }

    /** Выдать награды за все пройденные сервером задания, до которых игрок дошёл */
    static void claimServer(ServerPlayerEntity p) {
        CampaignState st = state(p);
        boolean again = true;
        while (again) {                                   // награда может открыть следующее (уже пройденное) задание
            again = false;
            for (QuestDef q : QuestRegistry.all()) {
                if (!q.server() || !st.done.contains(q.id) || PlayerCampaign.of(p).hasClaimed(q.id) || !reqsMet(p, q)) continue;
                PlayerCampaign.set(p, PlayerCampaign.of(p).withClaimed(q.id));
                giveReward(p, q);
                again = true;
            }
        }
    }

    private static void giveReward(ServerPlayerEntity p, QuestDef q) {
        QuestDef.Reward r = q.reward;
        PlayerProfile pr = PlayerProfile.of(p);
        if (r.essence > 0) pr = pr.addEssence(r.essence);
        if (r.coins > 0) pr = pr.addCoins(r.coins);
        p.setAttached(PlayerProfile.TYPE, pr);
        for (String c : r.cases) {
            Item it = ModItems.CASES.get(c);
            if (it != null) p.getInventory().offerOrDrop(new ItemStack(it));
        }
        for (String s : r.items) {
            String[] parts = s.split(":");
            int count = 1;
            String id = s;
            if (parts.length == 3) { id = parts[0] + ":" + parts[1]; try { count = Integer.parseInt(parts[2]); } catch (NumberFormatException ignored) { } }
            Identifier ident = Identifier.tryParse(id);
            if (ident == null || !Registries.ITEM.containsId(ident)) continue;
            p.getInventory().offerOrDrop(new ItemStack(Registries.ITEM.get(ident), count));
        }
        if (r.skateboard) p.getInventory().offerOrDrop(new ItemStack(ModItems.SKATEBOARD));
        if (r.script_scrap) p.getInventory().offerOrDrop(scriptScrap(p));
        String txt = q.rewardText();
        if (!txt.isEmpty()) p.sendMessage(Text.literal("§6✦ Награда за «" + q.title + "»: §e" + txt), false);
        p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.7f, 1.3f);
    }

    /** Обрывок сценария: страница, где имя игрока зачёркнуто */
    private static ItemStack scriptScrap(ServerPlayerEntity p) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        String name = p.getName().getString();
        List<RawFilteredPair<Text>> pages = new ArrayList<>();
        pages.add(RawFilteredPair.of(Text.literal("СЦЕНАРИЙ\nсезон ").formatted(Formatting.DARK_PURPLE)
            .append(Text.literal("??").formatted(Formatting.OBFUSCATED))
            .append(Text.literal("\n\nСцена 1. Участник выходит на площадку. Зрители ждут.\n\nУчастник: ").formatted(Formatting.BLACK))
            .append(Text.literal(name).formatted(Formatting.STRIKETHROUGH, Formatting.DARK_RED))
            .append(Text.literal("\n\nРоль: ").formatted(Formatting.BLACK))
            .append(Text.literal("вычеркнуть после финала").formatted(Formatting.STRIKETHROUGH, Formatting.GRAY))));
        pages.add(RawFilteredPair.of(Text.literal("Пометка на полях, красными чернилами:\n\n").formatted(Formatting.BLACK)
            .append(Text.literal("«Если шов снова разойдётся — переснять сезон целиком. Никто не должен помнить.»\n\n").formatted(Formatting.DARK_RED, Formatting.ITALIC))
            .append(Text.literal("Ниже, другим почерком:\n").formatted(Formatting.BLACK))
            .append(Text.literal("«Я помню. — С.»").formatted(Formatting.DARK_GREEN))));
        book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT,
            new WrittenBookContentComponent(RawFilteredPair.of("Обрывок сценария"), "Режиссёр", 0, pages, true));
        return book;
    }

    // ================================================================ разговоры
    static String speakerName(String kind) {
        return switch (kind) {
            case "ticketer" -> "Билетёр";
            case "lighter" -> RECIPIENT_NAME[0];
            case "makeup" -> RECIPIENT_NAME[1];
            case "costumer" -> RECIPIENT_NAME[2];
            case "director" -> "Голос Режиссёра";
            case "assistant" -> "Помощник режиссёра";
            default -> kind.startsWith("poster") ? "Афиша" : kind;
        };
    }

    private static int speakerColor(String kind) {
        return switch (kind) {
            case "ticketer" -> 0xFFC94A;
            case "director" -> 0xFF5A6E;
            case "assistant" -> 0xFFAA33;
            case "lighter" -> 0x7FD8FF;
            case "makeup" -> 0xFF8AD8;
            case "costumer" -> 0x8EF0A0;
            default -> 0xD9A8FF;
        };
    }

    /** Реплики: в чат (журнал разговора) и окном внизу экрана, если стоит клиентская часть */
    public static void say(ServerPlayerEntity p, String kind, List<String> lines) {
        String name = speakerName(kind);
        String nick = p.getName().getString();
        JsonArray arr = new JsonArray();
        for (String l : lines) {
            String t = l.replace("{player}", nick);
            p.sendMessage(Text.literal("§6[" + name + "] §f" + t), false);
            arr.add(t);
        }
        if (HudSync.has(p)) {
            JsonObject o = new JsonObject();
            o.addProperty("name", name);
            o.addProperty("color", speakerColor(kind));
            o.add("lines", arr);
            ServerPlayNetworking.send(p, new HudPayload("camp_say", o.toString()));
        }
    }

    public static void onInteract(ServerPlayerEntity p, StoryEntity e) {
        String kind = e.kind();
        if (kind.equals("ticketer")) { talkTicketer(p); return; }
        if (kind.startsWith("poster_")) { readPoster(p, e); return; }
        if (e.owner() != null) { deliver(p, e); return; }
        say(p, kind, List.of("Здравствуй! Шоу идёт, а мы тут работаем."));
    }

    private static void talkTicketer(ServerPlayerEntity p) {
        p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_VILLAGER_AMBIENT, SoundCategory.NEUTRAL, 0.6f, 1.3f);
        // 1. задание «поговорить с Билетёром»
        for (QuestDef q : QuestRegistry.all()) {
            if (!q.goal.type.equals("talk") || !q.goal.target.equals("ticketer") || status(p, q) != St.ACTIVE) continue;
            setProgress(p, q, q.count());
            return;
        }
        // потерял пропуск — выдать новый
        PlayerCampaign pc = PlayerCampaign.of(p);
        if (pc.isDone("prologue_pass") && !hasItem(p, ModItems.PASS)) {
            p.getInventory().offerOrDrop(new ItemStack(ModItems.PASS));
            say(p, "ticketer", List.of("Потерял пропуск? Бывает. Держи новый — и больше не роняй его в лаву."));
            return;
        }
        // 2. курьер
        QuestDef courier = QuestRegistry.get(COURIER);
        if (courier != null && status(p, courier) == St.ACTIVE) {
            long left = (pc.flag("courier_deadline") - p.getServer().getOverworld().getTime()) / 20;
            if (pc.flag("courier_deadline") > 0 && left > 0)
                say(p, "ticketer", List.of("Письма ещё у тебя! Осталось " + left / 60 + ":" + String.format("%02d", left % 60) + ". Беги!"));
            else startCourier(p, courier);
            return;
        }
        // 3. подсказка
        QuestDef cur = current(p);
        if (cur == null) say(p, "ticketer", List.of("Первый акт сыгран. Второй готовится за кулисами — а туда даже мне вход закрыт. Пока."));
        else say(p, "ticketer", List.of("По сценарию у тебя сейчас: «" + cur.title + "». " + cur.hint));
    }

    private static boolean hasItem(ServerPlayerEntity p, Item item) {
        for (int i = 0; i < p.getInventory().size(); i++) if (p.getInventory().getStack(i).isOf(item)) return true;
        return false;
    }

    // ================================================================ курьер (личное, на время)
    private static void startCourier(ServerPlayerEntity p, QuestDef q) {
        ServerWorld w = p.getServerWorld();
        if (w.getRegistryKey() != World.OVERWORLD) { say(p, "ticketer", List.of("Письма ходят только по Верхнему миру!")); return; }
        double a0 = w.random.nextDouble() * Math.PI * 2;
        List<BlockPos> spots = new ArrayList<>();
        List<Structures.Site> sites = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Structures.Site s = Structures.findPost(w, p.getBlockPos(), a0 + i * Math.PI * 2 / 3);
            if (s == null) {
                say(p, "ticketer", List.of("Хм, адресаты куда-то разбрелись… Отойди на открытое место и спроси меня ещё раз."));
                return;
            }
            sites.add(s);
        }
        PlayerCampaign pc = PlayerCampaign.of(p).clearFlags("courier_").withCounter(q.id, 0);
        for (int i = 0; i < 3; i++) {
            BlockPos at = Structures.buildPost(w, sites.get(i), i);
            Structures.spawn(w, ModEntities.STORY_NPC, RECIPIENT_KIND[i], at, sites.get(i).yaw(), p.getUuid(), i);
            spots.add(at);
            pc = pc.withFlag("courier_pos_" + i, at.asLong());
            p.getInventory().offerOrDrop(LetterItem.make(p.getUuid(), i, RECIPIENT_NAME[i]));
        }
        pc = pc.withFlag("courier_deadline", w.getServer().getOverworld().getTime() + COURIER_SECONDS * 20L).withFlag("courier_mask", 8);
        PlayerCampaign.set(p, pc);
        List<String> lines = new ArrayList<>(q.lines_start);
        for (int i = 0; i < 3; i++) lines.add("§e" + RECIPIENT_NAME[i] + "§f — " + direction(p, spots.get(i)) + ".");
        say(p, q.speaker, lines);
        p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1f, 1f);
        refreshAll(p.getServer());
    }

    private static void deliver(ServerPlayerEntity p, StoryEntity e) {
        if (!p.getUuid().equals(e.owner())) {
            say(p, e.kind(), List.of("Спасибо, но я жду письмо не от тебя."));
            return;
        }
        PlayerCampaign pc = PlayerCampaign.of(p);
        int bit = 1 << e.slot();
        long mask = pc.flag("courier_mask");
        if ((mask & bit) != 0) { say(p, e.kind(), List.of("Моё письмо уже у меня. Беги к остальным!")); return; }
        int found = -1;
        for (int i = 0; i < p.getInventory().size(); i++) if (LetterItem.isFor(p.getInventory().getStack(i), p.getUuid(), e.slot())) { found = i; break; }
        if (found < 0) { say(p, e.kind(), List.of("А где же письмо? Без письма не считается…")); return; }
        p.getInventory().removeStack(found, 1);
        PlayerCampaign.set(p, pc.withFlag("courier_mask", mask | bit));
        String line = switch (e.kind()) {
            case "lighter" -> "Новый план света? «Прожектор на участника, остальных — в тень». Странно… Ну, Режиссёру виднее.";
            case "makeup" -> "Ох, грим на финал — «стереть лицо». Это же… шутка такая, да?";
            default -> "Костюмы на следующий сезон? Так этот ещё не кончился! Что-то он торопится.";
        };
        say(p, e.kind(), List.of(line));
        ServerWorld w = p.getServerWorld();
        w.spawnParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + 1.8, e.getZ(), 10, 0.3, 0.3, 0.3, 0);
        w.playSound(null, e.getBlockPos(), SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.NEUTRAL, 1f, 1.2f);
        QuestDef q = QuestRegistry.get(COURIER);
        if (q != null) {
            int got = Long.bitCount((mask | bit) & 7);
            if (got >= q.count()) PlayerCampaign.set(p, PlayerCampaign.of(p).clearFlags("courier_"));
            setProgress(p, q, got);
        }
    }

    private static void courierTick(ServerPlayerEntity p) {
        PlayerCampaign pc = PlayerCampaign.of(p);
        long dl = pc.flag("courier_deadline");
        if (dl <= 0) return;
        long now = p.getServer().getOverworld().getTime();
        if (now <= dl) return;
        for (int i = 0; i < p.getInventory().size(); i++)
            if (LetterItem.ownedBy(p.getInventory().getStack(i), p.getUuid())) p.getInventory().removeStack(i);
        PlayerCampaign.set(p, pc.clearFlags("courier_").withCounter(COURIER, 0));
        p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("§c§lВРЕМЯ ВЫШЛО")));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("§fПисьма рассыпались пеплом. Вернись к Билетёру")));
        p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 0.8f, 0.6f);
        refreshAll(p.getServer());
    }

    /** Держать ли временного адресата: хозяин в сети и его курьерское задание идёт */
    static boolean keepTemp(StoryEntity e) {
        MinecraftServer server = e.getServer();
        if (server == null) return false;
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.owner());
        if (p == null) return false;
        PlayerCampaign pc = PlayerCampaign.of(p);
        if (pc.flag("courier_deadline") <= 0) return false;
        return pc.flag("courier_pos_" + e.slot()) == e.getBlockPos().asLong()
            || e.getBlockPos().isWithinDistance(BlockPos.fromLong(pc.flag("courier_pos_" + e.slot())), 2);
    }

    // ================================================================ афиши (общее)
    private static final String[] POSTER_LINES = {
        "«СЕЗОН ОТКРЫТ! Каждый вечер — новые испытания!» …а снизу, мелко, процарапано: «сцена держится на клею».",
        "«Пять минут славы — и ни секундой больше!» Кто-то дописал углём: «но клей стареет, кадры расходятся».",
        "«Не пропустите финал!» Под слоем краски проступает: «там, где шов, — свет не того цвета».",
        "«Режиссёр благодарит участников!» На обороте свежая надпись: «найди склейку. не дай ей разойтись»."
    };

    private static void readPoster(ServerPlayerEntity p, StoryEntity e) {
        int idx;
        try { idx = Integer.parseInt(e.kind().substring("poster_".length())) - 1; } catch (Exception ex) { return; }
        if (idx < 0 || idx >= POSTER_LINES.length) return;
        CampaignState st = state(p);
        CampaignState.Poster ps = null;
        for (CampaignState.Poster x : st.posters) if (x.index == idx) ps = x;
        say(p, e.kind(), List.of(POSTER_LINES[idx]));
        if (ps == null || ps.found || !isActive(p, POSTERS)) return;
        ps.found = true;
        st.markDirty();
        ServerWorld w = p.getServerWorld();
        w.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, e.getX(), e.getY() + 1.4, e.getZ(), 30, 0.5, 0.6, 0.2, 0.3);
        w.playSound(null, e.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.2f, 1.4f);
        QuestDef q = QuestRegistry.get(POSTERS);
        if (q != null) setProgress(p, q, (int) st.posters.stream().filter(x -> x.found).count());
    }

    private static void ensurePosters(ServerPlayerEntity p) {
        CampaignState st = state(p);
        if (st.posters.size() >= 4 || --posterRetry > 0) return;
        posterRetry = 10;
        ServerWorld w = p.getServerWorld();
        Set<Integer> have = new HashSet<>();
        for (CampaignState.Poster x : st.posters) have.add(x.index);
        double a0 = w.random.nextDouble() * Math.PI * 2;
        boolean any = false;
        for (int i = 0; i < 4; i++) {
            if (have.contains(i)) continue;
            Structures.Site s = Structures.findPoster(w, p.getBlockPos(), a0 + i * Math.PI / 2);
            if (s == null) continue;
            BlockPos at = Structures.buildPoster(w, s);
            StoryEntity e = Structures.spawn(w, ModEntities.STORY_PROP, "poster_" + (i + 1), at, s.yaw(), null, -1);
            CampaignState.Poster ps = new CampaignState.Poster(i, at, s.yaw());
            if (e != null) ps.entity = e.getUuid();
            st.posters.add(ps);
            any = true;
        }
        st.markDirty();
        if (any) {
            for (ServerPlayerEntity o : p.getServer().getPlayerManager().getPlayerList())
                if (isActive(o, POSTERS)) say(o, "director", List.of("§oДамы и господа! По всей округе расклеены афиши нового сезона… §7(найди 4 афиши — они светятся столбом света)"));
        }
    }

    // ================================================================ касса Билетёра
    private static void ensureBooth(ServerPlayerEntity p) {
        CampaignState st = state(p);
        if (st.booth != null || --boothRetry > 0) return;
        boothRetry = 10;
        ServerWorld w = p.getServerWorld();
        if (w.getRegistryKey() != World.OVERWORLD || !p.isOnGround() || !w.isSkyVisible(p.getBlockPos().up())) return;
        Structures.Site s = Structures.findBooth(w, p.getBlockPos());
        if (s == null) return;
        BlockPos at = Structures.buildBooth(w, s);
        StoryEntity e = Structures.spawn(w, ModEntities.STORY_NPC, "ticketer", at, s.yaw(), null, -1);
        st.booth = at;
        st.boothYaw = s.yaw();
        st.ticketer = e == null ? null : e.getUuid();
        st.markDirty();
        for (ServerPlayerEntity o : p.getServer().getPlayerManager().getPlayerList()) {
            if (o.getServerWorld() != w) continue;
            o.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("§6§lCOMBO RUSH")));
            o.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("§fУ кассы " + direction(o, at) + " ждёт Билетёр")));
        }
    }

    /** Если сюжетного персонажа нет рядом с его местом — вернуть (например, после /kill) */
    private static void upkeep(MinecraftServer server) {
        CampaignState st = CampaignState.get(server);
        ServerWorld w = server.getOverworld();
        if (st.booth != null && playerNear(w, st.booth, 40)) {
            if (w.getEntitiesByClass(StoryEntity.class, new Box(st.booth).expand(4), e -> e.kind().equals("ticketer")).isEmpty()) {
                StoryEntity e = Structures.spawn(w, ModEntities.STORY_NPC, "ticketer", st.booth, st.boothYaw, null, -1);
                st.ticketer = e == null ? null : e.getUuid();
                st.markDirty();
            }
        }
        for (CampaignState.Poster ps : st.posters) {
            if (!playerNear(w, ps.pos, 40)) continue;
            String kind = "poster_" + (ps.index + 1);
            if (w.getEntitiesByClass(StoryEntity.class, new Box(ps.pos).expand(3), e -> e.kind().equals(kind)).isEmpty()) {
                StoryEntity e = Structures.spawn(w, ModEntities.STORY_PROP, kind, ps.pos, ps.yaw, null, -1);
                ps.entity = e == null ? null : e.getUuid();
                st.markDirty();
            }
        }
    }

    private static boolean playerNear(ServerWorld w, BlockPos pos, double r) {
        for (ServerPlayerEntity p : w.getPlayers()) if (p.age > 100 && p.squaredDistanceTo(Vec3d.ofCenter(pos)) < r * r) return true;
        return false;
    }

    private static void ensureStage(ServerPlayerEntity p) {
        CampaignState st = state(p);
        if (st.stage != null || --stageRetry > 0) return;
        stageRetry = 10;
        ServerWorld w = p.getServerWorld();
        if (!p.isOnGround()) return;
        Structures.Site s = Structures.findStage(w, p.getBlockPos());
        if (s == null) return;
        st.stage = Structures.buildStage(w, s);
        st.markDirty();
        for (ServerPlayerEntity o : p.getServer().getPlayerManager().getPlayerList())
            if (isActive(o, StageBoss.QUEST))
                say(o, "director", List.of("§oПлощадка готова. Свет, камера… §7(съёмочная площадка " + direction(o, st.stage) + " — встань на жёлтый крест)"));
    }

    private static void ensureSplice(ServerPlayerEntity p) {
        if (RiftManager.hasStory()) { spliceWait = 0; return; }
        if (++spliceWait < 20) return;                   // пауза после афиш, чтобы прочитать
        spliceWait = 0;
        if (RiftManager.openStory(p))
            for (ServerPlayerEntity o : p.getServer().getPlayerManager().getPlayerList())
                if (isActive(o, SPLICE)) say(o, "ticketer", List.of("Слышишь гул? Это не по сценарию… Там, где свет не того цвета, — шов. Закрой его!"));
    }

    public static void onSpliceClosed(MinecraftServer server, Set<UUID> who) {
        for (UUID id : who) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p != null && isActive(p, SPLICE)) { trigger(p, "splice", "", 1); return; }
        }
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList())
            if (isActive(p, SPLICE)) { trigger(p, "splice", "", 1); return; }
    }

    // ================================================================ события мода → задания
    public static void onEventFinished(ServerPlayerEntity p, int medalOrdinal) {
        for (QuestDef q : new ArrayList<>(QuestRegistry.all())) {
            if (!q.goal.type.equals("event") || status(p, q) != St.ACTIVE) continue;
            int need = switch (q.goal.target) { case "SILVER" -> 2; case "GOLD" -> 3; case "PLATINUM" -> 4; case "NONE" -> 0; default -> 1; };
            if (medalOrdinal >= need) setProgress(p, q, progress(p, q) + 1);
            else p.sendMessage(Text.literal("§6✦ " + q.title + ": §7нужна медаль не ниже бронзы — в следующий раз!"), false);
        }
    }

    /** Цели, которые можно проверить по состоянию (выбрал вид раньше, чем получил пропуск, и т.п.) */
    private static void checkConditions(ServerPlayerEntity p) {
        PlayerProfile pr = PlayerProfile.of(p);
        for (QuestDef q : new ArrayList<>(QuestRegistry.all())) {
            if (status(p, q) != St.ACTIVE) continue;
            boolean ok = switch (q.goal.type) {
                case "species" -> !pr.species().equals(SpeciesRegistry.DEFAULT_ID) || pr.eventsDone() > 0;
                case "booster" -> !pr.boosters().isEmpty();
                default -> false;
            };
            if (ok) setProgress(p, q, q.count());
        }
    }

    // ================================================================ тик
    public static void tick(MinecraftServer server) {
        StageBoss.tick(server);
        if (server.getTicks() % 20 != 0) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator()) continue;
            courierTick(p);
            if (server.getTicks() % 40 == 0) checkConditions(p);
            if (p.getServerWorld().getRegistryKey() != World.OVERWORLD) continue;
            QuestDef cur = null;
            for (QuestDef q : QuestRegistry.all()) {
                if (status(p, q) != St.ACTIVE) continue;
                if (cur == null) cur = q;
                switch (q.goal.type) {
                    case "talk" -> { if (q.goal.target.equals("ticketer")) ensureBooth(p); }
                    case "poster" -> ensurePosters(p);
                    case "splice" -> ensureSplice(p);
                    case "stage" -> ensureStage(p);
                    default -> { }
                }
            }
            beacons(p);
        }
        if (server.getTicks() % 200 == 0) upkeep(server);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) sendTrack(p);
    }

    /** Столбы света над ненайденными афишами и адресатами — видно издалека */
    private static void beacons(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        if (isActive(p, POSTERS))
            for (CampaignState.Poster ps : state(p).posters) if (!ps.found) column(w, p, ps.pos, ParticleTypes.END_ROD);
        PlayerCampaign pc = PlayerCampaign.of(p);
        if (pc.flag("courier_deadline") > 0)
            for (int i = 0; i < 3; i++)
                if ((pc.flag("courier_mask") & (1L << i)) == 0 && pc.flag("courier_pos_" + i) != 0)
                    column(w, p, BlockPos.fromLong(pc.flag("courier_pos_" + i)), ParticleTypes.WAX_ON);
    }

    private static void column(ServerWorld w, ServerPlayerEntity p, BlockPos pos, net.minecraft.particle.ParticleEffect fx) {
        if (p.squaredDistanceTo(Vec3d.ofCenter(pos)) > 220 * 220) return;
        for (int y = 3; y < 40; y += 3)
            w.spawnParticles(p, fx, true, pos.getX() + 0.5, pos.getY() + y, pos.getZ() + 0.5, 2, 0.08, 1.2, 0.08, 0);
    }

    // ================================================================ вход
    private static void onJoin(ServerPlayerEntity p) {
        if (p.isRemoved()) return;
        claimServer(p);
        checkConditions(p);
        QuestDef cur = current(p);
        if (cur != null && cur.id.equals("prologue_pass") && state(p).booth != null)
            p.sendMessage(Text.literal("§6✦ §fУ кассы " + direction(p, state(p).booth) + " ждёт Билетёр. Поговори с ним!"), false);
        else if (cur != null)
            p.sendMessage(Text.literal("§6✦ Сценарий: §e" + cur.title + " §7— " + cur.hint + " §8(журнал: J)"), false);
        refresh(p);
    }

    // ================================================================ журнал и трекер
    public static void openJournal(ServerPlayerEntity p) {
        if (!HudSync.has(p)) { chatJournal(p); return; }
        String json = journalJson(p).toString();
        LAST_JOURNAL.put(p.getUuid(), json);
        ServerPlayNetworking.send(p, new HudPayload("camp_open", json));
    }

    private static void chatJournal(ServerPlayerEntity p) {
        p.sendMessage(Text.literal("§6§l✦ СЦЕНАРИЙ ✦"), false);
        for (QuestDef q : QuestRegistry.all()) {
            St s = status(p, q);
            if (s == St.LOCKED) continue;
            String mark = s == St.DONE ? "§a✔ " : "§e➜ ";
            p.sendMessage(Text.literal(mark + "§f" + q.title + (s == St.ACTIVE ? " §7— " + q.hint + " (" + progress(p, q) + "/" + q.count() + ")" : "")), false);
        }
    }

    public static void refresh(ServerPlayerEntity p) {
        if (!HudSync.has(p)) return;
        String json = journalJson(p).toString();
        if (json.equals(LAST_JOURNAL.get(p.getUuid()))) return;
        LAST_JOURNAL.put(p.getUuid(), json);
        ServerPlayNetworking.send(p, new HudPayload("camp", json));
    }

    public static void refreshAll(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) { refresh(p); LAST_TRACK.remove(p.getUuid()); }
    }

    private static JsonObject journalJson(ServerPlayerEntity p) {
        JsonObject root = new JsonObject();
        JsonArray chs = new JsonArray();
        for (QuestRegistry.ChapterDef c : QuestRegistry.chapters()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", c.id);
            o.addProperty("title", c.title);
            o.addProperty("sub", c.subtitle);
            List<QuestDef> qs = QuestRegistry.of(c.id);
            String state;
            if (c.soon || qs.isEmpty()) state = "soon";
            else if (qs.stream().allMatch(q -> status(p, q) == St.DONE)) state = "done";
            else if (qs.stream().anyMatch(q -> status(p, q) == St.ACTIVE)) state = "active";
            else state = qs.stream().anyMatch(q -> status(p, q) == St.DONE) ? "active" : "locked";
            o.addProperty("state", state);
            chs.add(o);
        }
        root.add("chapters", chs);
        JsonArray qa = new JsonArray();
        for (QuestDef q : QuestRegistry.all()) {
            St s = status(p, q);
            JsonObject o = new JsonObject();
            o.addProperty("id", q.id);
            o.addProperty("ch", q.chapter);
            o.addProperty("state", s.name().toLowerCase(Locale.ROOT));
            o.addProperty("title", s == St.LOCKED ? "???" : q.title);
            o.addProperty("desc", s == St.LOCKED ? "Эта сцена ещё не написана для тебя." : q.desc);
            o.addProperty("hint", s == St.ACTIVE ? q.hint : "");
            o.addProperty("prog", s == St.DONE ? q.count() : progress(p, q));
            o.addProperty("max", q.count());
            o.addProperty("reward", q.rewardText());
            o.addProperty("server", q.server());
            qa.add(o);
        }
        root.add("quests", qa);
        root.addProperty("essence", PlayerProfile.of(p).essence());
        QuestDef cur = current(p);
        root.addProperty("current", cur == null ? "" : cur.id);
        return root;
    }

    /** Трекер на экране: текущее задание и стрелка к цели */
    private static void sendTrack(ServerPlayerEntity p) {
        if (!HudSync.has(p)) return;
        JsonObject o = new JsonObject();
        QuestDef q = current(p);
        if (q == null) o.addProperty("has", false);
        else {
            o.addProperty("has", true);
            o.addProperty("title", q.title);
            o.addProperty("hint", q.hint);
            o.addProperty("prog", progress(p, q));
            o.addProperty("max", q.count());
            o.addProperty("server", q.server());
            BlockPos target = target(p, q);
            boolean approx = q.goal.type.equals("poster");
            if (target != null) {
                o.addProperty("x", target.getX() + 0.5);
                o.addProperty("y", target.getY() + 1);
                o.addProperty("z", target.getZ() + 0.5);
                o.addProperty("approx", approx);
            }
            long dl = PlayerCampaign.of(p).flag("courier_deadline");
            if (q.id.equals(COURIER) && dl > 0)
                o.addProperty("timer", Math.max(0, (dl - p.getServer().getOverworld().getTime()) / 20));
        }
        String s = o.toString();
        if (s.equals(LAST_TRACK.get(p.getUuid()))) return;
        LAST_TRACK.put(p.getUuid(), s);
        ServerPlayNetworking.send(p, new HudPayload("camp_track", s));
    }

    private static BlockPos target(ServerPlayerEntity p, QuestDef q) {
        CampaignState st = state(p);
        PlayerCampaign pc = PlayerCampaign.of(p);
        switch (q.goal.type) {
            case "talk": return st.booth;
            case "courier": {
                if (pc.flag("courier_deadline") <= 0) return st.booth;
                BlockPos best = null; double bd = Double.MAX_VALUE;
                for (int i = 0; i < 3; i++) {
                    if ((pc.flag("courier_mask") & (1L << i)) != 0 || pc.flag("courier_pos_" + i) == 0) continue;
                    BlockPos b = BlockPos.fromLong(pc.flag("courier_pos_" + i));
                    double d = p.squaredDistanceTo(Vec3d.ofCenter(b));
                    if (d < bd) { bd = d; best = b; }
                }
                return best;
            }
            case "poster": {
                BlockPos best = null; double bd = Double.MAX_VALUE;
                for (CampaignState.Poster ps : st.posters) {
                    if (ps.found) continue;
                    // примерная зона поиска: смещение до 12 блоков, вблизи — точно
                    Random r = new Random(ps.pos.asLong() * 31 + 7);
                    BlockPos zone = ps.pos.add(r.nextInt(25) - 12, 0, r.nextInt(25) - 12);
                    double d = p.squaredDistanceTo(Vec3d.ofCenter(ps.pos));
                    BlockPos t = d < 24 * 24 ? ps.pos : zone;
                    if (d < bd) { bd = d; best = t; }
                }
                return best;
            }
            case "splice": return RiftManager.storyPos();
            case "stage": return st.stage;
            default: return null;
        }
    }

    // ================================================================ мелочи
    static String direction(ServerPlayerEntity p, BlockPos pos) {
        double dx = pos.getX() + 0.5 - p.getX(), dz = pos.getZ() + 0.5 - p.getZ();
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        double ang = Math.toDegrees(Math.atan2(dx, -dz));
        String[] dirs = {"к северу", "к северо-востоку", "к востоку", "к юго-востоку", "к югу", "к юго-западу", "к западу", "к северо-западу"};
        return "в " + dist + " бл. " + dirs[(int) Math.round(((ang % 360) + 360) % 360 / 45.0) % 8];
    }

    // ================================================================ отладка (/cr story …)
    public static String debugComplete(ServerPlayerEntity p, String id) {
        QuestDef q = QuestRegistry.get(id);
        if (q == null) return "Нет такого задания: " + id;
        if (status(p, q) == St.DONE) return "Уже выполнено.";
        complete(p, q);
        return "Выполнено: " + q.title;
    }

    public static String debugReset(ServerPlayerEntity p, boolean server) {
        PlayerCampaign.set(p, PlayerCampaign.EMPTY);
        if (server) {
            CampaignState st = state(p);
            st.done.clear(); st.counters.clear(); st.posters.clear();
            st.booth = null; st.ticketer = null; st.stage = null;
            st.markDirty();
        }
        refreshAll(p.getServer());
        return server ? "Кампания сброшена для всего сервера (постройки остаются в мире)." : "Твой личный прогресс кампании сброшен.";
    }

    public static String debugBooth(ServerPlayerEntity p) {
        CampaignState st = state(p);
        st.booth = null; st.markDirty();
        boothRetry = 0;
        ensureBooth(p);
        return st.booth == null ? "Не нашёл ровного места рядом." : "Касса поставлена " + direction(p, st.booth) + ".";
    }

    public static List<String> questIds() {
        List<String> out = new ArrayList<>();
        for (QuestDef q : QuestRegistry.all()) out.add(q.id);
        return out;
    }
}
