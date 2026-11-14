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

    private static AgentRenderer ct, t;
    private static Boolean menuCT;

    /** Вызывается при перезагрузке рендеров (из фабрики рендера ботов, там есть контекст) */
    public static void init(EntityRendererFactory.Context ctx) {
        ct = new AgentRenderer(ctx, "ct");
        t = new AgentRenderer(ctx, "t");
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
        if (ct == null) return null;
        Boolean s = side(p);
        return s == null ? null : s ? ct : t;
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
