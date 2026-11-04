package com.derevna230.comborush.lobby;

import com.derevna230.comborush.campaign.StoryEntity;
import com.derevna230.comborush.campaign.Structures;
import com.derevna230.comborush.pet.ModEntities;
import com.derevna230.comborush.hud.HudPayload;
import com.derevna230.comborush.hud.HudSync;
import com.derevna230.comborush.universe.SkyWorlds;
import com.derevna230.comborush.universe.Universes;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.List;

/**
 * Лобби: небесный остров с четырьмя лавками. В каждой стоит NPC, зовущий в свою мини-игру.
 * Доступна пока одна — «Противостояние»; остальные заняты заглушками.
 */
public final class Lobby {
    private Lobby() {}

    private record Game(String name, String npc, boolean open, List<String> text) {}
    private static final List<Game> GAMES = List.of(
        new Game("Противостояние", "Командир Рэй", true, List.of(
            "Две команды, одна карта, один раунд —",
            "и никаких вторых шансов. Закупайся, занимай позицию, держи линию.",
            "Заходи в меню: выбери снаряжение и скины, собери группу друзей.")),
        new Game("Гонки по крышам", "Нова", false, List.of("Трассы над городом, трамплины и прыжки.", "Лавка откроется позже — я ещё размечаю маршрут.")),
        new Game("Битва строителей", "Зодчий Брик", false, List.of("Кто лучше построит по теме — тот и победил.", "Лавка откроется позже — я ещё собираю чертежи.")),
        new Game("Охота за сокровищами", "Золотце", false, List.of("Карты, подсказки, клад в конце пути.", "Лавка откроется позже — я ещё прячу сундуки.")));

    public static final String KIND_PREFIX = "lobby_";

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Lobby::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Party.leave(handler.getPlayer(), false);
            com.derevna230.comborush.item.CRGunItem.forget(handler.getPlayer().getUuid());
        });
        // остров не ломают и не застраивают (в творческом режиме — можно)
        PlayerBlockBreakEvents.BEFORE.register((w, pl, pos, st, be) -> !(guarded(pl)));
        UseBlockCallback.EVENT.register((pl, w, hand, hit) -> {
            if (!guarded(pl)) return ActionResult.PASS;
            ItemStack held = pl.getStackInHand(hand);
            boolean placing = held.getItem() instanceof BlockItem || held.getItem() instanceof BucketItem;
            boolean chest = w.getBlockState(hit.getBlockPos()).isOf(Blocks.BARREL);
            return placing || chest ? ActionResult.FAIL : ActionResult.PASS;
        });
        // в Лобби и комнате меню никто никому не вредит
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((e, src, amount) ->
            !(e instanceof ServerPlayerEntity sp && Universes.lobbyWorld(sp.getWorld()) && !src.isOf(net.minecraft.entity.damage.DamageTypes.GENERIC_KILL)));
    }

    private static boolean guarded(net.minecraft.entity.player.PlayerEntity pl) {
        return pl instanceof ServerPlayerEntity sp && Universes.lobbyWorld(sp.getWorld()) && !sp.getAbilities().creativeMode;
    }

    // ---------------------------------------------------------------- тик
    private static void tick(MinecraftServer server) {
        int t = server.getTicks();
        if (t % 10 != 0) return;
        ServerWorld lobby = server.getWorld(Universes.LOBBY);
        if (lobby != null && !lobby.getPlayers().isEmpty()) {
            for (ServerPlayerEntity p : lobby.getPlayers()) {
                if (p.getY() < 40 && !p.isSpectator()) {                         // упал с острова — обратно на площадь
                    p.teleport(lobby, 0.5, SkyWorlds.TOP + 1, 6.5, 180f, 6f);
                    p.fallDistance = 0;
                    p.sendMessage(Text.literal("§7Вы упали с острова — возвращаем на площадь."), true);
                }
                keepFed(p);
            }
            if (t % 40 == 0) ensureNpcs(lobby);
        }
        ServerWorld room = server.getWorld(Universes.STANDOFF);
        if (room != null) {
            for (ServerPlayerEntity p : room.getPlayers()) {
                if (p.getY() < SkyWorlds.ST_FLOOR - 8 && !p.isSpectator()) {
                    p.teleport(room, 0.5, SkyWorlds.ST_FLOOR + 1, 0.5, 180f, 0f);
                    p.fallDistance = 0;
                }
                keepFed(p);
            }
        }
    }

    private static void keepFed(ServerPlayerEntity p) {
        if (p.getAbilities().creativeMode) return;
        if (p.getHungerManager().getFoodLevel() < 20) p.getHungerManager().setFoodLevel(20);
        if (p.getHealth() < p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    /** NPC стоят у прилавков всегда; пропавшего возвращаем, лишних убираем */
    private static void ensureNpcs(ServerWorld w) {
        for (int i = 0; i < 4; i++) {
            int bx = SkyWorlds.BOOTH[i][0], bz = SkyWorlds.BOOTH[i][1];
            if (!w.isChunkLoaded(new BlockPos(bx, SkyWorlds.TOP, bz))) continue;
            String kind = KIND_PREFIX + (i + 1);
            List<StoryEntity> found = w.getEntitiesByClass(StoryEntity.class,
                new Box(bx - 6, SkyWorlds.TOP - 2, bz - 6, bx + 7, SkyWorlds.TOP + 8, bz + 7), e -> e.kind().equals(kind));
            for (int k = 1; k < found.size(); k++) found.get(k).discard();
            if (!found.isEmpty()) continue;
            int[] f = FACING[i];
            Structures.spawn(w, ModEntities.STORY_NPC, kind, new BlockPos(bx - f[0], SkyWorlds.TOP + 1, bz - f[1]), SkyWorlds.BOOTH_YAW[i], null, -1);
        }
    }
    /** Куда смотрит лавка (в сторону площади); NPC стоит на шаг позади центра */
    private static final int[][] FACING = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};

    // ---------------------------------------------------------------- разговор и игра
    public static int gameOf(String kind) {
        try { return Integer.parseInt(kind.substring(KIND_PREFIX.length())) - 1; } catch (RuntimeException e) { return -1; }
    }

    public static String npcName(String kind) {
        int i = gameOf(kind);
        return i >= 0 && i < GAMES.size() ? GAMES.get(i).npc() : kind;
    }

    /** Нажали на NPC: приглашение в игру */
    public static void talk(ServerPlayerEntity p, StoryEntity e) {
        int i = gameOf(e.kind());
        if (i < 0 || i >= GAMES.size()) return;
        Game g = GAMES.get(i);
        p.getServerWorld().playSound(null, p.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_VILLAGER_YES,
            net.minecraft.sound.SoundCategory.NEUTRAL, 0.6f, 1.2f);
        JsonObject o = new JsonObject();
        o.addProperty("idx", i);
        o.addProperty("game", g.name());
        o.addProperty("npc", g.npc());
        o.addProperty("open", g.open());
        JsonArray lines = new JsonArray();
        for (String l : g.text()) lines.add(l);
        o.add("lines", lines);
        Party q = Party.of(p);
        o.addProperty("party", q == null ? 1 : q.size());
        if (HudSync.has(p)) ServerPlayNetworking.send(p, new HudPayload("lobby_game", o.toString()));
        else for (String l : g.text()) p.sendMessage(Text.literal("§6[" + g.npc() + "] §f" + l), false);
    }

    /** «Играть» на экране лавки. @return null или причина отказа */
    public static String play(ServerPlayerEntity p, int idx) {
        if (!Universes.inLobby(p)) return "Игры запускаются из Лобби.";
        if (idx < 0 || idx >= GAMES.size()) return "Такой игры нет.";
        if (!GAMES.get(idx).open()) return "«" + GAMES.get(idx).name() + "» откроется позже.";
        return idx == 0 ? Standoff.enter(p, false) : "Игра пока недоступна.";
    }
}
