package com.derevna230.comborush.universe;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.campaign.Campaign;
import com.derevna230.comborush.hud.HudPayload;
import com.derevna230.comborush.hud.ShopActionPayload;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Вселенные: отдельные измерения, где продолжается история. Сейчас одна — «Ночной город».
 * Вход открывается после пролога (операторам — всегда), выход свободный. Смерть возвращает в родной мир
 * (кровати в измерении не работают), выход из игры оставляет игрока на месте — это делает сам Minecraft.
 */
public final class Universes {
    private Universes() {}

    public static final RegistryKey<World> NIGHT_CITY = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(ComboRush.MOD_ID, "night_city"));

    /** Откуда игрок вошёл: «мир|x|y|z|yaw|pitch» — туда он вернётся при выходе */
    private static final AttachmentType<String> RETURN = AttachmentRegistry.create(
        Identifier.of(ComboRush.MOD_ID, "universe_return"),
        b -> b.persistent(Codec.STRING));

    /** Загрузить класс при старте, чтобы тип данных игрока был зарегистрирован до загрузки мира */
    public static void init() { NightCityChunkGenerator.register(); }

    /** Мир «Ночной город» открыт: пролог пройден (или оператор) */
    public static boolean unlocked(ServerPlayerEntity p) { return p.hasPermissionLevel(2) || Campaign.prologueDone(p); }

    /** Доступно ли содержимое мира с идентификатором world ("" — всегда доступно) */
    public static boolean worldOpen(ServerPlayerEntity p, String world) {
        if (world == null || world.isEmpty()) return true;
        return switch (world) { case "night_city" -> unlocked(p); default -> true; };
    }

    public static boolean inCity(ServerPlayerEntity p) { return p.getWorld().getRegistryKey().equals(NIGHT_CITY); }

    // ---------------------------------------------------------------- вход и выход
    /** @return null, если всё получилось, иначе причина отказа */
    public static String enter(ServerPlayerEntity p, boolean force) {
        MinecraftServer server = p.getServer();
        ServerWorld city = server == null ? null : server.getWorld(NIGHT_CITY);
        if (city == null) return "Вселенная ещё не загрузилась. Попробуйте позже.";
        if (inCity(p)) return "Вы уже в «Ночном городе».";
        if (!force && !p.hasPermissionLevel(2) && !Campaign.prologueDone(p)) return "Вход откроется после окончания пролога.";
        if (!p.isAlive() || p.isSpectator()) return "Сейчас войти нельзя.";
        ServerWorld from = p.getServerWorld();
        p.setAttached(RETURN, from.getRegistryKey().getValue() + "|" + p.getX() + "|" + p.getY() + "|" + p.getZ() + "|" + p.getYaw() + "|" + p.getPitch());
        city.getChunk(new BlockPos(NightCity.SPAWN_X, NightCity.GROUND, NightCity.SPAWN_Z));   // прогрузить чанк под ногами
        p.stopRiding();
        p.teleport(city, NightCity.SPAWN_X + 0.5, NightCity.GROUND + 1, NightCity.SPAWN_Z + 0.5, 180f, 8f);
        p.fallDistance = 0;
        p.sendMessage(Text.literal("§d§lНочной город §7— добро пожаловать. Выход: кнопка «Вселенные» в инвентаре."), false);
        return null;
    }

    /** Вернуться в мир, откуда вошли (или на точку возрождения, если запись потерялась) */
    public static String leave(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        if (server == null) return "Сервер недоступен.";
        if (!inCity(p)) return "Вы не во вселенной.";
        ServerWorld to = server.getOverworld();
        Vec3d at = null; float yaw = p.getYaw(), pitch = p.getPitch();
        String saved = p.getAttached(RETURN);
        if (saved != null) {
            try {
                String[] a = saved.split("\\|");
                ServerWorld w = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(a[0])));
                if (w != null) { to = w; at = new Vec3d(Double.parseDouble(a[1]), Double.parseDouble(a[2]), Double.parseDouble(a[3])); yaw = Float.parseFloat(a[4]); pitch = Float.parseFloat(a[5]); }
            } catch (RuntimeException ignored) { at = null; }
        }
        if (at == null) {
            BlockPos sp = to.getSpawnPos();
            at = new Vec3d(sp.getX() + 0.5, to.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, sp.getX(), sp.getZ()), sp.getZ() + 0.5);
        }
        p.stopRiding();
        p.teleport(to, at.x, at.y, at.z, yaw, pitch);
        p.fallDistance = 0;
        p.removeAttached(RETURN);
        p.sendMessage(Text.literal("§7Вы вернулись домой."), false);
        return null;
    }

    /** Для операторов: телепорт к началу трассы (при необходимости сначала входит во вселенную) */
    public static String toSpot(ServerPlayerEntity p, String key) {
        double[] at = NightCityWild.spot(key);
        if (at == null) return "Нет такой точки: " + key + " (serpentine, summit, grandprix, drift).";
        if (!inCity(p)) { String err = enter(p, true); if (err != null) return err; }
        ServerWorld city = p.getServer().getWorld(NIGHT_CITY);
        city.getChunk(new BlockPos((int) Math.floor(at[0]), (int) at[1], (int) Math.floor(at[2])));
        p.stopRiding();
        p.teleport(city, at[0], at[1], at[2], (float) at[3], 8f);
        p.fallDistance = 0;
        return null;
    }

    // ---------------------------------------------------------------- сеть
    public static void handle(ServerPlayerEntity p, ShopActionPayload a) {
        String msg = null;
        switch (a.action()) {
            case "univ_enter" -> msg = enter(p, false);
            case "univ_leave" -> msg = leave(p);
            default -> { }
        }
        if (msg != null) p.sendMessage(Text.literal("§c" + msg), true);
        sendState(p, msg == null ? "" : msg);
    }

    public static void sendState(ServerPlayerEntity p, String msg) {
        JsonObject o = new JsonObject();
        o.addProperty("open", unlocked(p));
        o.addProperty("inside", inCity(p));
        o.addProperty("msg", msg);
        ServerPlayNetworking.send(p, new HudPayload("univ", o.toString()));
    }
}
