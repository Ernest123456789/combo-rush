package com.derevna230.comborush.cycle;

import com.derevna230.comborush.director.DirectorManager;
import com.derevna230.comborush.hud.HudSync;
import com.derevna230.comborush.score.ScoreEngine;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

public class CycleManager {
    public static int NORMAL_SECONDS = 5 * 60;   // подготовка между событиями (config: pause_minutes)
    public static int EVENT_SECONDS  = 5 * 60;   // длительность события (config: event_minutes)

    private static CyclePhase phase = CyclePhase.NORMAL;
    private static int ticksLeft = NORMAL_SECONDS * 20;
    private static final ServerBossBar BAR =
        new ServerBossBar(Text.empty(), BossBar.Color.BLUE, BossBar.Style.PROGRESS);

    public static void init() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> BAR.removePlayer(handler.player));
    }

    public static CyclePhase phase() { return phase; }

    /** Из файла настроек; текущая фаза не обрывается, если только она не длиннее новой */
    public static void configure(int normalSeconds, int eventSeconds) {
        NORMAL_SECONDS = normalSeconds;
        EVENT_SECONDS = eventSeconds;
        int max = (phase == CyclePhase.EVENT ? EVENT_SECONDS : NORMAL_SECONDS) * 20;
        if (ticksLeft > max) ticksLeft = max;
    }
    public static int secondsLeft() { return ticksLeft / 20; }

    public static void tick(MinecraftServer server) {
        if (server.getPlayerManager().getPlayerList().isEmpty()) return; // пауза без игроков
        ticksLeft--;

        if (ticksLeft % 20 == 0) {
            int sec = ticksLeft / 20;
            updateBar(server);
            if (phase == CyclePhase.NORMAL) {
                if (sec == 300 && NORMAL_SECONDS > 300) announce(server, "§eСобытие начнётся через 5 минут!");
                if (sec == 120) announce(server, "§eСобытие начнётся через 2 минуты!");
                if (sec == 60)  announce(server, "§6Событие начнётся через 1 минуту!");
                if (sec == 30)  announce(server, "§cСобытие начнётся через 30 секунд!");
            } else if (sec == 60) {
                announce(server, "§eДо конца события 1 минута!");
            }
        }
        if (ticksLeft <= 0) {
            if (phase == CyclePhase.NORMAL) startEvent(server); else endEvent(server);
        }
    }

    public static void startEvent(MinecraftServer server) {
        phase = CyclePhase.EVENT;
        ticksLeft = EVENT_SECONDS * 20;
        ScoreEngine.reset();
        announce(server, "§a§lСОБЫТИЕ НАЧАЛОСЬ! §fНабирайте очки 5 минут!");
        DirectorManager.start(server);
        com.derevna230.comborush.rift.RiftManager.onEventStart(server);
        HudSync.closeShop(server);
        updateBar(server);
    }

    public static void endEvent(MinecraftServer server) {
        ScoreEngine.finishEvent(server);
        DirectorManager.end(server);
        com.derevna230.comborush.rift.RiftManager.onEventEnd(server);
        phase = CyclePhase.NORMAL;
        ticksLeft = NORMAL_SECONDS * 20;
        announce(server, "§bСобытие завершено. Подготовка: " + (NORMAL_SECONDS / 60) + " мин. Магазин: §f/shop");
        updateBar(server);
    }

    private static void updateBar(MinecraftServer server) {
        boolean event = phase == CyclePhase.EVENT;
        int total = (event ? EVENT_SECONDS : NORMAL_SECONDS) * 20;
        BAR.setPercent(Math.max(0f, Math.min(1f, ticksLeft / (float) total)));
        BAR.setColor(event ? BossBar.Color.GREEN : BossBar.Color.BLUE);
        int s = secondsLeft();
        BAR.setName(Text.literal((event ? "Событие" + DirectorManager.suffix() + ": " : "До события: ")
            + String.format("%d:%02d", s / 60, s % 60)));
        server.getPlayerManager().getPlayerList().forEach(BAR::addPlayer);
    }

    private static void announce(MinecraftServer server, String msg) {
        server.getPlayerManager().broadcast(Text.literal(msg), false);
    }
}
