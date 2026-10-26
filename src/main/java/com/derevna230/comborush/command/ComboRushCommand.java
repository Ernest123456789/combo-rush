package com.derevna230.comborush.command;

import com.derevna230.comborush.cosmetic.CosmeticsGui;
import com.derevna230.comborush.cycle.CycleManager;
import com.derevna230.comborush.director.DirectorManager;
import com.derevna230.comborush.leaderboard.Leaderboard;
import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.prestige.PrestigeManager;
import com.derevna230.comborush.prestige.StatsView;
import com.derevna230.comborush.promo.PromoDef;
import com.derevna230.comborush.promo.PromoManager;
import com.derevna230.comborush.score.ScoreEngine;
import com.derevna230.comborush.score.ScoreSource;
import com.derevna230.comborush.shop.ShopGui;
import com.derevna230.comborush.species.SpeciesGui;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class ComboRushCommand {
    private static final SuggestionProvider<ServerCommandSource> PROMO_IDS =
        (ctx, builder) -> CommandSource.suggestMatching(PromoManager.ids(), builder);

    private static final SuggestionProvider<ServerCommandSource> EVENT_IDS =
        (ctx, builder) -> CommandSource.suggestMatching(DirectorManager.ids(), builder);

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(CommandManager.literal("shop").executes(c -> {
            ShopGui.open(c.getSource().getPlayerOrThrow()); return 1; }));
        d.register(CommandManager.literal("species").executes(c -> {
            SpeciesGui.open(c.getSource().getPlayerOrThrow()); return 1; }));
        d.register(topNode());   // короткая команда /top
        d.register(CommandManager.literal("cosmetics").executes(c -> {
            CosmeticsGui.open(c.getSource().getPlayerOrThrow()); return 1; }));

        d.register(root("cr"));
        d.register(root("comborush"));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> topNode() {
        return CommandManager.literal("top")
            .executes(c -> { Leaderboard.send(c.getSource(), "best"); return 1; })
            .then(CommandManager.argument("board", StringArgumentType.greedyString())
                .suggests((ctx, b) -> CommandSource.suggestMatching(Leaderboard.BOARD_NAMES, b))
                .executes(c -> { Leaderboard.send(c.getSource(), StringArgumentType.getString(c, "board")); return 1; }));
    }

    private static boolean op(ServerCommandSource s) { return s.hasPermissionLevel(2); }

    private static LiteralArgumentBuilder<ServerCommandSource> root(String name) {
        return CommandManager.literal(name)
            // --- для всех игроков ---
            .then(CommandManager.literal("stats").executes(c -> {
                StatsView.send(c.getSource(), c.getSource().getPlayerOrThrow()); return 1; }))
            .then(CommandManager.argument("player", EntityArgumentType.player())
                .then(CommandManager.literal("stats").executes(c -> {
                    StatsView.send(c.getSource(), EntityArgumentType.getPlayer(c, "player")); return 1; })))
            .then(CommandManager.literal("reset")
                .executes(c -> { PrestigeManager.request(c.getSource().getPlayerOrThrow()); return 1; })
                .then(CommandManager.literal("confirm").executes(c -> {
                    PrestigeManager.confirm(c.getSource().getPlayerOrThrow()); return 1; })))
            .then(CommandManager.literal("promo")
                .executes(c -> { listPromos(c.getSource()); return 1; })
                .then(CommandManager.literal("list").executes(c -> { listPromos(c.getSource()); return 1; }))
                .then(CommandManager.literal("start").requires(ComboRushCommand::op)
                    .then(CommandManager.argument("id", StringArgumentType.word()).suggests(PROMO_IDS)
                        .executes(c -> promo(c.getSource(), StringArgumentType.getString(c, "id"), 1))))
                .then(CommandManager.literal("stop").requires(ComboRushCommand::op)
                    .then(CommandManager.argument("id", StringArgumentType.word()).suggests(PROMO_IDS)
                        .executes(c -> promo(c.getSource(), StringArgumentType.getString(c, "id"), 2))))
                .then(CommandManager.literal("auto").requires(ComboRushCommand::op)
                    .then(CommandManager.argument("id", StringArgumentType.word()).suggests(PROMO_IDS)
                        .executes(c -> promo(c.getSource(), StringArgumentType.getString(c, "id"), 3)))))

            .then(topNode())
            .then(CommandManager.literal("event")
                .executes(c -> { DirectorManager.describe(c.getSource()); return 1; })
                .then(CommandManager.literal("list").requires(ComboRushCommand::op).executes(c -> {
                    for (String id : DirectorManager.ids()) {
                        var d = DirectorManager.get(id);
                        c.getSource().sendFeedback(() -> Text.literal("§f" + id + " §7— " + d.name
                            + " §8(вес " + d.weight + ", от события " + d.min_event + ")"), false);
                    }
                    return 1; }))
                .then(CommandManager.literal("next").requires(ComboRushCommand::op)
                    .then(CommandManager.argument("id", StringArgumentType.word()).suggests(EVENT_IDS)
                        .executes(c -> {
                            String id = StringArgumentType.getString(c, "id");
                            boolean ok = DirectorManager.setNext(id);
                            c.getSource().sendFeedback(() -> Text.literal(ok ? "§aСледующее событие: " + id
                                : "§cНет такого события: " + id), false);
                            return ok ? 1 : 0; }))))

            // --- служебное: вызывается функциями датапака (скрытые достижения), нужен уровень 2 ---
            .then(CommandManager.literal("internal").requires(ComboRushCommand::op)
                .then(internal("brew", ScoreSource.BREW))
                .then(internal("enchant", ScoreSource.ENCHANT))
                .then(internal("breed", ScoreSource.BREED))
                .then(internal("trade", ScoreSource.TRADE))
                .then(internal("fish", ScoreSource.FISH)))

            // --- отладка (op) ---
            .then(CommandManager.literal("start").requires(ComboRushCommand::op).executes(c -> {
                CycleManager.startEvent(c.getSource().getServer()); return 1; }))
            .then(CommandManager.literal("end").requires(ComboRushCommand::op).executes(c -> {
                CycleManager.endEvent(c.getSource().getServer()); return 1; }))
            .then(CommandManager.literal("coins").requires(ComboRushCommand::op)
                .then(CommandManager.argument("n", IntegerArgumentType.integer()).executes(c -> {
                    var p = c.getSource().getPlayerOrThrow();
                    p.setAttached(PlayerProfile.TYPE,
                        PlayerProfile.of(p).addCoins(IntegerArgumentType.getInteger(c, "n")));
                    return 1; })))
            .then(CommandManager.literal("essence").requires(ComboRushCommand::op)
                .then(CommandManager.argument("n", IntegerArgumentType.integer()).executes(c -> {
                    var p = c.getSource().getPlayerOrThrow();
                    p.setAttached(PlayerProfile.TYPE,
                        PlayerProfile.of(p).addEssence(IntegerArgumentType.getInteger(c, "n")));
                    return 1; })))
            .then(CommandManager.literal("addscore").requires(ComboRushCommand::op)
                .then(CommandManager.argument("n", LongArgumentType.longArg()).executes(c -> {
                    var p = c.getSource().getPlayerOrThrow();
                    p.setAttached(PlayerProfile.TYPE,
                        PlayerProfile.of(p).addTotalScore(LongArgumentType.getLong(c, "n")));
                    return 1; })))
            .then(CommandManager.literal("setevents").requires(ComboRushCommand::op)
                .then(CommandManager.argument("n", IntegerArgumentType.integer(0)).executes(c -> {
                    var p = c.getSource().getPlayerOrThrow();
                    p.setAttached(PlayerProfile.TYPE,
                        PlayerProfile.of(p).withEvents(IntegerArgumentType.getInteger(c, "n")));
                    return 1; })));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> internal(String name, ScoreSource src) {
        return CommandManager.literal(name).executes(c -> {
            ServerPlayerEntity p = c.getSource().getPlayer();
            if (p != null) ScoreEngine.award(p, src, name);
            return 1;
        });
    }

    private static void listPromos(ServerCommandSource src) {
        var active = PromoManager.activeNames();
        src.sendFeedback(() -> Text.literal(active.isEmpty() ? "§7Активных акций нет."
            : "§d★ Активны: §f" + String.join(", ", active)), false);
        for (String id : PromoManager.ids()) {
            PromoDef d = PromoManager.get(id);
            boolean on = PromoManager.isActive(id);
            src.sendFeedback(() -> Text.literal((on ? "§a● " : "§8○ ") + "§f" + id + " §7— " + d.name
                + (d.enabled ? "" : " §c(выключена в файле)")), false);
        }
    }

    /** mode: 1 — включить, 2 — выключить, 3 — вернуть автоматический режим по датам */
    private static int promo(ServerCommandSource src, String id, int mode) {
        boolean ok = switch (mode) {
            case 1 -> PromoManager.start(src.getServer(), id);
            case 2 -> PromoManager.stop(src.getServer(), id);
            default -> PromoManager.auto(src.getServer(), id);
        };
        src.sendFeedback(() -> Text.literal(ok ? "§aГотово: " + id : "§cАкция не найдена: " + id), false);
        return ok ? 1 : 0;
    }
}
