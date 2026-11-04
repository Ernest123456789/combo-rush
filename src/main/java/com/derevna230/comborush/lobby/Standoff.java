package com.derevna230.comborush.lobby;

import com.derevna230.comborush.item.ModItems;
import com.derevna230.comborush.universe.SkyWorlds;
import com.derevna230.comborush.universe.Universes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.concurrent.ThreadLocalRandom;

/** «Противостояние»: вход в комнату меню (из Лобби), возврат и награда за матч. Сами матчи и карты добавим позже. */
public final class Standoff {
    private Standoff() {}

    /** Шанс выпадения кейса «Арсенал» за сыгранный матч; победителю чуть больше */
    public static final double CASE_CHANCE = 0.25, CASE_CHANCE_WIN = 0.35;

    /** Место игрока в комнате меню: у каждого своя клетка, чтобы не стоять друг в друге */
    private static double[] slot(ServerPlayerEntity p) {
        int s = Math.floorMod(p.getUuid().hashCode(), 25);
        return new double[]{(s % 5 - 2) * 4 + 0.5, (s / 5 - 2) * 4 + 0.5};
    }

    /** Вход игрока и его группы (кто в Лобби) в меню. @return null или причина отказа */
    public static String enter(ServerPlayerEntity p, boolean force) {
        if (!force && !Universes.inLobby(p)) return "В «Противостояние» входят из Лобби.";
        String err = tp(p);
        if (err != null) return err;
        Party q = Party.of(p);
        if (q != null && q.leader().equals(p.getUuid())) {
            for (ServerPlayerEntity m : q.online(p.getServer(), p)) {
                if (Universes.inLobby(m) && m.isAlive()) {
                    m.sendMessage(Text.literal("§b" + p.getName().getString() + " §fведёт группу в «Противостояние»."), false);
                    tp(m);
                }
            }
        }
        return null;
    }

    private static String tp(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        ServerWorld w = server == null ? null : server.getWorld(Universes.STANDOFF);
        if (w == null) return "Комната меню ещё не загрузилась.";
        if (Universes.inStandoff(p)) return "Вы уже в меню.";
        if (!p.isAlive() || p.isSpectator()) return "Сейчас войти нельзя.";
        double[] at = slot(p);
        w.getChunk(new BlockPos((int) Math.floor(at[0]), SkyWorlds.ST_FLOOR, (int) Math.floor(at[1])));
        if (!Universes.inUniverse(p)) Universes.enterLobby(p, true);     // из дома напрямую: запомнить возврат
        p.stopRiding();
        p.teleport(w, at[0], SkyWorlds.ST_FLOOR + 1, at[1], 180f, 0f);
        p.fallDistance = 0;
        return null;
    }

    /** Назад в Лобби, к лавке «Противостояния» */
    public static String exit(ServerPlayerEntity p) {
        if (!Universes.inStandoff(p)) return "Вы не в меню «Противостояния».";
        ServerWorld w = p.getServer().getWorld(Universes.LOBBY);
        if (w == null) return "Лобби недоступно.";
        w.getChunk(new BlockPos(0, SkyWorlds.TOP, -8));
        p.stopRiding();
        p.teleport(w, 0.5, SkyWorlds.TOP + 1, -9.5, 0f, 6f);
        p.fallDistance = 0;
        return null;
    }

    /** Награда за матч (вызывать, когда появятся матчи). @return true, если выпал кейс */
    public static boolean finishMatch(ServerPlayerEntity p, boolean won) {
        double chance = won ? CASE_CHANCE_WIN : CASE_CHANCE;
        if (ThreadLocalRandom.current().nextDouble() >= chance) return false;
        return giveCase(p);
    }

    public static boolean giveCase(ServerPlayerEntity p) {
        Item c = ModItems.CASES.get("arsenal");
        if (c == null) return false;
        p.getInventory().offerOrDrop(new ItemStack(c));
        p.sendMessage(Text.literal("§6§lНаграда за матч: §fКейс «Арсенал»! §7Ключ к нему — 250 эссенции, покупается при открытии."), false);
        p.getServerWorld().playSound(null, p.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_PLAYER_LEVELUP,
            net.minecraft.sound.SoundCategory.PLAYERS, 0.8f, 1.2f);
        return true;
    }
}
