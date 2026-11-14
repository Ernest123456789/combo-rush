package com.derevna230.comborush.client.agent;

import com.derevna230.comborush.universe.Universes;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.registry.RegistryKey;
import net.minecraft.scoreboard.Team;
import net.minecraft.world.World;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Режим «Противостояние»: в меню и на карте матча игроки выглядят как бойцы.
 * Сторона берётся из команды матча (scoreboard-команды cr_t / cr_ct, их ставит сервер),
 * вне матча — свой выбор в меню (кнопки «Спецназ» / «Боевик»), остальные игроки — спецназ.
 */
public final class AgentClient {
    private AgentClient() {}

    private static final java.util.Map<String, AgentRenderer> RENDERERS = new java.util.HashMap<>();
    /** Выбор агентов других игроков: UUID → {t, ct} */
    private static final java.util.Map<java.util.UUID, String[]> REMOTE = new java.util.HashMap<>();
    private static String localT = "t", localCT = "ct";
    /** Показать в меню агента, которого ещё не надели (окно просмотра «Снаряжения») */
    public static String preview;
    private static Boolean menuCT;

    /** Вызывается при перезагрузке рендеров (из фабрики рендера ботов, там есть контекст) */
    public static void init(EntityRendererFactory.Context ctx) {
        RENDERERS.clear();
        for (String id : com.derevna230.comborush.lobby.AgentDefs.CT) RENDERERS.put(id, new AgentRenderer(ctx, id));
        for (String id : com.derevna230.comborush.lobby.AgentDefs.T) RENDERERS.put(id, new AgentRenderer(ctx, id));
    }

    public static boolean inMode(World w) {
        if (w == null) return false;
        RegistryKey<World> k = w.getRegistryKey();
        return k.equals(Universes.STANDOFF) || com.derevna230.comborush.universe.GameMaps.isMatchWorld(k);
    }

    /** null — обычный игрок; true — спецназ (КТ); false — боевик (Т) */
    public static Boolean side(AbstractClientPlayerEntity p) {
        if (!inMode(p.getWorld())) return null;
        Team team = p.getScoreboardTeam();
        if (team != null) {
            if (team.getName().equals("cr_t")) return false;
            if (team.getName().equals("cr_ct")) return true;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (p == mc.player || mc.player != null && p.getUuid().equals(mc.player.getUuid())) return menuCT();
        return true;
    }

    public static EntityRenderer<?> rendererFor(AbstractClientPlayerEntity p) {
        if (RENDERERS.isEmpty()) return null;
        Boolean s = side(p);
        if (s == null) return null;
        return RENDERERS.getOrDefault(agentFor(p, s), RENDERERS.get(s ? "ct" : "t"));
    }

    private static boolean isLocal(AbstractClientPlayerEntity p) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return p == mc.player || mc.player != null && p.getUuid().equals(mc.player.getUuid());
    }

    public static String agentFor(AbstractClientPlayerEntity p, boolean ct) {
        if (isLocal(p)) {
            if (preview != null) return preview;
            return ct ? localCT : localT;
        }
        String[] r = REMOTE.get(p.getUuid());
        return r == null ? (ct ? "ct" : "t") : ct ? r[1] : r[0];
    }

    public static void setLocal(String t, String ct) {
        if (t != null && com.derevna230.comborush.lobby.AgentDefs.T.contains(t)) localT = t; else localT = "t";
        if (ct != null && com.derevna230.comborush.lobby.AgentDefs.CT.contains(ct)) localCT = ct; else localCT = "ct";
    }

    public static void receive(com.google.gson.JsonObject o) {
        for (com.google.gson.JsonElement e : o.getAsJsonArray("list")) {
            com.google.gson.JsonObject j = e.getAsJsonObject();
            try { REMOTE.put(java.util.UUID.fromString(j.get("u").getAsString()), new String[]{j.get("t").getAsString(), j.get("ct").getAsString()}); }
            catch (IllegalArgumentException ignored) { }
        }
    }

    /** Область лица агента в его текстуре — для значков в «Снаряжении»: {u, v, w, h, texW, texH} */
    public static int[] icon(String id) { return AgentModels.icon(id); }

    // ---- перчатки: что надето в «Снаряжении» (стандартные — по стороне)
    private static String glove;
    private static Path gloveFile() { return FabricLoader.getInstance().getConfigDir().resolve("combo-rush-gloves.txt"); }

    public static String gloveFor(boolean ct) {
        if (glove == null) {
            glove = "";
            try { if (Files.exists(gloveFile())) glove = Files.readString(gloveFile()).trim(); } catch (Exception ignored) { }
        }
        if (glove.isEmpty() || com.derevna230.comborush.item.GunDefs.STD_GLOVES.contains(glove)) return ct ? "gloves_ct" : "gloves_t";
        return glove;
    }

    public static void setGlove(String id) {
        if (id == null) id = "";
        if (id.equals(glove)) return;
        glove = id;
        try { Files.writeString(gloveFile(), id); } catch (Exception ignored) { }
    }

    // ---- выбор бойца в меню (сохраняется в config/combo-rush-agent.txt)
    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("combo-rush-agent.txt"); }

    public static boolean menuCT() {
        if (menuCT == null) {
            menuCT = true;
            try { if (Files.exists(file())) menuCT = !Files.readString(file()).trim().equals("t"); } catch (Exception ignored) { }
        }
        return menuCT;
    }

    public static void setMenuCT(boolean v) {
        menuCT = v;
        try { Files.writeString(file(), v ? "ct" : "t"); } catch (Exception ignored) { }
    }
}
