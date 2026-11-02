package com.derevna230.comborush.campaign;

import com.derevna230.comborush.cycle.CycleManager;
import com.derevna230.comborush.cycle.CyclePhase;
import com.derevna230.comborush.director.DirectorManager;
import com.derevna230.comborush.hud.HudPayload;
import com.derevna230.comborush.hud.HudSync;
import com.derevna230.comborush.pet.ModEntities;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/**
 * Акт II «Изнанка» и Акт III «Финал сезона»: Закулисье с Суфлёром, студия Режиссёра, выбор финала и его последствия.
 * Финал общий на сервер: кто первым выбрал — так и будет (сбросить можно командой /cr story reset server).
 */
public final class Finale {
    private Finale() {}

    public static final String CHAIR = "chair", CHAOS = "chaos";
    static final String CHOICE = "act3_choice";
    private static int backRetry = 0, studioRetry = 0;

    public static String ending(MinecraftServer server) { return CampaignState.get(server).ending; }
    public static boolean chaos(MinecraftServer server) { return server != null && CHAOS.equals(ending(server)); }

    // ================================================================ постройки
    static void ensureBackstage(ServerPlayerEntity p) {
        CampaignState st = CampaignState.get(p.getServer());
        if (st.backstage != null || --backRetry > 0) return;
        backRetry = 10;
        ServerWorld w = p.getServerWorld();
        if (w.getRegistryKey() != World.OVERWORLD || !p.isOnGround()) return;
        Structures.Site s = Structures.findBackstage(w, p.getBlockPos());
        if (s == null) return;
        st.backstage = Structures.buildBackstage(w, s);
        st.backstageYaw = s.yaw();
        st.markDirty();
        Structures.spawn(w, ModEntities.STORY_NPC, "prompter", st.backstage, st.backstageYaw, null, -1);
        for (ServerPlayerEntity o : p.getServer().getPlayerManager().getPlayerList()) {
            if (o.getServerWorld() != w) continue;
            title(o, "§5§lЗАКУЛИСЬЕ", "§fпроход открылся " + Campaign.direction(o, st.backstage));
        }
    }

    static void ensureStudio(ServerPlayerEntity p) {
        CampaignState st = CampaignState.get(p.getServer());
        if (st.studio != null || --studioRetry > 0) return;
        studioRetry = 10;
        ServerWorld w = p.getServerWorld();
        if (w.getRegistryKey() != World.OVERWORLD || !p.isOnGround()) return;
        Structures.Site s = Structures.findStudio(w, p.getBlockPos());
        if (s == null) return;
        st.studio = Structures.buildStudio(w, s);
        st.throne = s.at(0, 0, -3);
        st.studioYaw = s.yaw();
        st.markDirty();
        Structures.spawn(w, ModEntities.STORY_NPC, "director", st.throne, st.studioYaw, null, -1);
        for (ServerPlayerEntity o : p.getServer().getPlayerManager().getPlayerList()) {
            if (o.getServerWorld() != w) continue;
            title(o, "§c§lСТУДИЯ РЕЖИССЁРА", "§fдвери открыты " + Campaign.direction(o, st.studio));
        }
    }

    /** Вернуть Суфлёра и Режиссёра на места, если их нет (например, после /kill или после боя) */
    static void upkeep(MinecraftServer server) {
        CampaignState st = CampaignState.get(server);
        ServerWorld w = server.getOverworld();
        if (st.backstage != null && near(w, st.backstage))
            respawn(w, "prompter", st.backstage, st.backstageYaw);
        if (st.throne != null && near(w, st.throne) && !DirectorBoss.fighting() && !CHAIR.equals(st.ending))
            respawn(w, "director", st.throne, st.studioYaw);
    }

    private static void respawn(ServerWorld w, String kind, BlockPos pos, float yaw) {
        if (w.getEntitiesByClass(StoryEntity.class, new Box(pos).expand(4), e -> e.kind().equals(kind)).isEmpty())
            Structures.spawn(w, ModEntities.STORY_NPC, kind, pos, yaw, null, -1);
    }

    private static boolean near(ServerWorld w, BlockPos pos) {
        for (ServerPlayerEntity p : w.getPlayers()) if (p.age > 100 && p.squaredDistanceTo(Vec3d.ofCenter(pos)) < 40 * 40) return true;
        return false;
    }

    // ================================================================ разговоры
    static void talkPrompter(ServerPlayerEntity p) {
        QuestDef cur = Campaign.current(p);
        String id = cur == null ? "" : cur.id;
        List<String> lines = switch (id) {
            case "act2_shard_power", "act2_shard_speed", "act2_shard_memory" -> List.of(
                "Ключ собирается из трёх осколков: силы, скорости и памяти.",
                "Сила — у переливчатых чудищ. Скорость — на трассе, на скейте. Память — в золоте, взятом на сцене.",
                "Журнал покажет, какие осколки уже у нас.");
            case "act3_director", CHOICE -> List.of("Ключ у тебя. Студия ждёт. Я… я буду суфлировать из-за кулис.");
            default -> CampaignState.get(p.getServer()).ending.isEmpty()
                ? List.of("Тише, тише. Здесь шёпотом.")
                : List.of(CHAOS.equals(ending(p.getServer()))
                    ? "Шоу трещит по швам — и, знаешь, никогда ещё зрители так не смеялись."
                    : "Новый Режиссёр, а? Постарайся не вычёркивать никого из сценария.");
        };
        Campaign.say(p, "prompter", lines);
    }

    static void talkDirector(ServerPlayerEntity p, StoryEntity e) {
        MinecraftServer server = p.getServer();
        CampaignState st = CampaignState.get(server);
        if (Campaign.isActive(p, DirectorBoss.QUEST)) {
            if (DirectorBoss.fighting()) return;
            Campaign.say(p, "director", List.of(
                "А, участник с зачёркнутым именем. Ты сломал мне весь сезон.",
                "Знаешь, сколько раз я переснимал этот мир? Пятьдесят событий — и все забывают. Кроме тебя.",
                "Что ж. Финальная сцена. Мотор!"));
            ServerWorld w = p.getServerWorld();
            w.spawnParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getBodyY(0.5), e.getZ(), 60, 0.4, 1, 0.4, 0.2);
            e.discard();
            DirectorBoss.start(w, st.studio, st.throne);
            return;
        }
        if (Campaign.isActive(p, CHOICE)) { openChoice(p); return; }
        if (CHAOS.equals(st.ending))
            Campaign.say(p, "director", List.of("Ха-ха… Хаос! Ни сценария, ни правил. Посмотрим, сколько вы протянете без меня."));
        else Campaign.say(p, "director", List.of("Мотор… стоп… мотор… Без сцены я просто тень."));
    }

    // ================================================================ выбор финала
    static void openChoice(ServerPlayerEntity p) {
        Campaign.say(p, "director", List.of(
            "Ты победил. Студия твоя — решай, что будет с шоу.",
            "Займи моё кресло — и сам выбирай, что увидят зрители. Или сломай шоу, и пусть правит случай."));
        if (HudSync.has(p)) {
            ServerPlayNetworking.send(p, new HudPayload("camp_choice", new JsonObject().toString()));
            return;
        }
        Text chair = Text.literal("§6§l[ЗАНЯТЬ КРЕСЛО]").styled(s -> s
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cr choose chair"))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Выбирать события сезона: /cr direct <событие>"))));
        Text chaos = Text.literal("§d§l[СЛОМАТЬ ШОУ]").styled(s -> s
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cr choose chaos"))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Хаос: монеты ×2, но в каждом событии — случайное правило босс-блайнда"))));
        p.sendMessage(Text.literal("§7Выбор общий для всего сервера: ").append(chair).append(Text.literal("  ")).append(chaos), false);
    }

    /** /cr choose chair|chaos или кнопка на экране выбора */
    public static String choose(ServerPlayerEntity p, String which) {
        MinecraftServer server = p.getServer();
        CampaignState st = CampaignState.get(server);
        if (!st.ending.isEmpty()) return "Финал уже выбран.";
        if (!Campaign.isActive(p, CHOICE)) return "Сначала победи Режиссёра в его студии.";
        if (!CHAIR.equals(which) && !CHAOS.equals(which)) return "Можно выбрать только chair или chaos.";
        st.ending = which;
        st.markDirty();
        ServerWorld w = server.getOverworld();
        if (st.throne != null) {
            BlockPos t = st.throne;
            for (StoryEntity e : w.getEntitiesByClass(StoryEntity.class, new Box(t).expand(4), x -> x.kind().equals("director"))) {
                if (CHAIR.equals(which)) {
                    w.spawnParticles(ParticleTypes.CLOUD, e.getX(), e.getBodyY(0.5), e.getZ(), 40, 0.4, 0.8, 0.4, 0.05);
                    e.discard();                                   // кресло свободно
                } else {
                    w.spawnParticles(ParticleTypes.WITCH, e.getX(), e.getBodyY(0.5), e.getZ(), 60, 0.5, 1, 0.5, 0.2);
                }
            }
            w.playSound(null, t, CHAIR.equals(which) ? com.derevna230.comborush.sound.ModSounds.MEDAL_GOLD
                : com.derevna230.comborush.sound.ModSounds.BOSS_START, SoundCategory.PLAYERS, 2f, 1f);
        }
        for (ServerPlayerEntity o : server.getPlayerManager().getPlayerList()) {
            if (CHAIR.equals(which)) {
                title(o, "§6§lНОВЫЙ РЕЖИССЁР", "§f" + p.getName().getString() + " занял кресло");
                o.sendMessage(Text.literal("§6✦ §fФинал: §6кресло Режиссёра§f. Те, кто прошёл сюжет, могут выбирать следующее событие: §e/cr direct <событие>"), false);
                o.sendMessage(Text.literal("§6✦ §fОткрылась кампания §e«С других сюжетов»§f — §eГлава 1§f ждёт в журнале §8(J)§f. Пролог пройден."), false);
            } else {
                title(o, "§d§lШОУ СЛОМАНО", "§fхаос: монеты ×2, правила случайны");
                o.sendMessage(Text.literal("§d✦ §fФинал: §dхаос§f. Теперь в каждом событии — случайное правило босс-блайнда для всех, а монеты удваиваются."), false);
            }
        }
        Campaign.trigger(p, "choice", "", 1);
        Campaign.refreshAll(server);
        return CHAIR.equals(which) ? "Ты занял кресло Режиссёра." : "Ты сломал шоу.";
    }

    /** /cr direct <событие>: прошедшие сюжет с финалом «кресло» выбирают следующее событие */
    public static String direct(ServerPlayerEntity p, String eventId) {
        MinecraftServer server = p.getServer();
        if (!CHAIR.equals(ending(server))) return "Выбирать события может только Режиссёр — для этого нужен финал «кресло».";
        if (!PlayerCampaign.of(p).hasClaimed(CHOICE)) return "Сначала пройди сюжет до конца.";
        if (CycleManager.phase() != CyclePhase.NORMAL) return "Выбрать следующее событие можно во время подготовки.";
        if (!DirectorManager.setNext(eventId)) return "Нет такого события: " + eventId;
        String name = DirectorManager.get(eventId).name;
        server.getPlayerManager().broadcast(Text.literal("§6✦ Режиссёр §f" + p.getName().getString() + " §6выбрал следующее событие: §e" + name), false);
        return "Следующее событие: " + name;
    }

    /** Дополнительные реплики эпилога в зависимости от финала */
    static List<String> epilogue(MinecraftServer server) {
        return CHAOS.equals(ending(server))
            ? List.of("Ты правда это сделал… Шоу без режиссёра. Монеты сыплются, правила скачут — зрители в восторге!",
                      "А сброс? Пусть стирает. Мы-то с тобой помним. Спасибо, участник.")
            : List.of("Новый Режиссёр! Только, пожалуйста, без зачёркнутых имён, ладно?",
                      "Выбирай события с умом: /cr direct. А я пойду продавать билеты на следующий сезон.");
    }

    static BlockPos target(ServerPlayerEntity p, QuestDef q) {
        CampaignState st = CampaignState.get(p.getServer());
        return switch (q.goal.type) {
            case "race" -> RaceManager.target(p);
            case "director", "choice" -> st.throne != null ? st.throne : st.studio;
            default -> null;
        };
    }

    private static void title(ServerPlayerEntity p, String a, String b) {
        p.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 50, 15));
        p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(a)));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(b)));
    }
}
