package com.derevna230.comborush.lobby;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.*;

/**
 * Группа игроков для мини-игр: лидер + до 4 друзей. Живёт в памяти сервера (после перезапуска группы нет).
 * Приглашение действует минуту; принять можно из меню «Противостояния» или кнопкой в чате.
 */
public final class Party {
    public static final int MAX = 5;
    private static final int INVITE_TICKS = 20 * 60;

    private UUID leader;
    private final LinkedHashSet<UUID> members = new LinkedHashSet<>();

    private static final Map<UUID, Party> BY_MEMBER = new HashMap<>();
    private record Invite(UUID from, long until) {}
    private static final Map<UUID, Invite> INVITES = new HashMap<>();

    private Party(UUID leader) { this.leader = leader; members.add(leader); }

    public UUID leader() { return leader; }
    public List<UUID> members() { return new ArrayList<>(members); }
    public int size() { return members.size(); }

    public static Party of(ServerPlayerEntity p) { return BY_MEMBER.get(p.getUuid()); }
    public static boolean isLeader(ServerPlayerEntity p) { Party q = of(p); return q != null && q.leader.equals(p.getUuid()); }

    /** Имя приглашающего, если у игрока есть действующее приглашение */
    public static String pendingFrom(ServerPlayerEntity p) {
        Invite iv = INVITES.get(p.getUuid());
        if (iv == null) return null;
        if (p.getServer().getTicks() > iv.until) { INVITES.remove(p.getUuid()); return null; }
        ServerPlayerEntity from = p.getServer().getPlayerManager().getPlayer(iv.from);
        return from == null ? null : from.getName().getString();
    }

    // ---------------------------------------------------------------- действия
    /** @return null или причина отказа */
    public static String invite(ServerPlayerEntity p, String targetName) {
        MinecraftServer server = p.getServer();
        ServerPlayerEntity t = server.getPlayerManager().getPlayer(targetName);
        if (t == null) return "Игрок «" + targetName + "» не в сети.";
        if (t == p) return "Себя приглашать не нужно.";
        if (!com.derevna230.comborush.universe.Universes.inLobby(t) && !com.derevna230.comborush.universe.Universes.inStandoff(t))
            return t.getName().getString() + " сейчас не в Лобби.";
        Party mine = of(p);
        if (mine != null && !mine.leader.equals(p.getUuid())) return "Приглашать в группу может только её лидер.";
        if (mine != null && mine.size() >= MAX) return "Группа уже полная (" + MAX + ").";
        if (of(t) != null) return t.getName().getString() + " уже в группе.";
        INVITES.put(t.getUuid(), new Invite(p.getUuid(), server.getTicks() + INVITE_TICKS));
        p.sendMessage(Text.literal("§7Приглашение отправлено: §f" + t.getName().getString()), false);
        MutableText msg = Text.literal("§b" + p.getName().getString() + " §fприглашает вас в группу. ");
        msg.append(Text.literal("§a[Принять]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cr party accept"))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Войти в группу")))));
        msg.append(Text.literal(" "));
        msg.append(Text.literal("§c[Отклонить]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cr party decline"))));
        t.sendMessage(msg, false);
        StandoffNet.sendState(t);
        return null;
    }

    public static String accept(ServerPlayerEntity p) {
        String from = pendingFrom(p);
        Invite iv = INVITES.remove(p.getUuid());
        if (iv == null || from == null) return "Приглашения нет или оно устарело.";
        if (of(p) != null) return "Вы уже в группе.";
        ServerPlayerEntity lead = p.getServer().getPlayerManager().getPlayer(iv.from);
        if (lead == null) return "Пригласивший вышел из игры.";
        Party q = of(lead);
        if (q == null) { q = new Party(lead.getUuid()); BY_MEMBER.put(lead.getUuid(), q); }
        else if (!q.leader.equals(lead.getUuid())) return "Пригласивший больше не лидер группы.";
        if (q.size() >= MAX) return "Группа уже полная.";
        q.members.add(p.getUuid());
        BY_MEMBER.put(p.getUuid(), q);
        q.say(p.getServer(), "§b" + p.getName().getString() + " §fвошёл в группу.");
        q.sync(p.getServer());
        return null;
    }

    public static String decline(ServerPlayerEntity p) {
        Invite iv = INVITES.remove(p.getUuid());
        if (iv == null) return "Приглашения нет.";
        ServerPlayerEntity lead = p.getServer().getPlayerManager().getPlayer(iv.from);
        if (lead != null) lead.sendMessage(Text.literal("§7" + p.getName().getString() + " отклонил приглашение."), false);
        StandoffNet.sendState(p);
        return null;
    }

    /** Выход из группы; лидерство переходит следующему, группа из одного человека распускается */
    public static void leave(ServerPlayerEntity p, boolean announce) {
        MinecraftServer server = p.getServer();
        INVITES.remove(p.getUuid());
        Party q = BY_MEMBER.remove(p.getUuid());
        if (q == null) return;
        q.members.remove(p.getUuid());
        if (announce) p.sendMessage(Text.literal("§7Вы вышли из группы."), false);
        q.afterRemoval(server, p.getName().getString(), " вышел из группы.");
        StandoffNet.sendState(p);
    }

    public static String kick(ServerPlayerEntity p, String name) {
        Party q = of(p);
        if (q == null || !q.leader.equals(p.getUuid())) return "Исключать может только лидер.";
        ServerPlayerEntity t = p.getServer().getPlayerManager().getPlayer(name);
        if (t == null || t == p || !q.members.contains(t.getUuid())) return "Такого игрока в группе нет.";
        q.members.remove(t.getUuid());
        BY_MEMBER.remove(t.getUuid());
        t.sendMessage(Text.literal("§cВас исключили из группы."), false);
        StandoffNet.sendState(t);
        q.afterRemoval(p.getServer(), t.getName().getString(), " исключён из группы.");
        return null;
    }

    private void afterRemoval(MinecraftServer server, String who, String what) {
        if (members.size() <= 1) {
            for (UUID id : members) {
                BY_MEMBER.remove(id);
                ServerPlayerEntity m = server.getPlayerManager().getPlayer(id);
                if (m != null) { m.sendMessage(Text.literal("§7Группа распущена."), false); StandoffNet.sendState(m); }
            }
            members.clear();
            return;
        }
        if (!members.contains(leader)) leader = members.iterator().next();
        say(server, "§7" + who + what);
        sync(server);
    }

    private void say(MinecraftServer server, String text) {
        for (UUID id : members) {
            ServerPlayerEntity m = server.getPlayerManager().getPlayer(id);
            if (m != null) m.sendMessage(Text.literal(text), false);
        }
    }

    public void sync(MinecraftServer server) {
        for (UUID id : members) {
            ServerPlayerEntity m = server.getPlayerManager().getPlayer(id);
            if (m != null) StandoffNet.sendState(m);
        }
    }

    /** Остальные члены группы, которые сейчас в сети */
    public List<ServerPlayerEntity> online(MinecraftServer server, ServerPlayerEntity except) {
        List<ServerPlayerEntity> out = new ArrayList<>();
        for (UUID id : members) {
            ServerPlayerEntity m = server.getPlayerManager().getPlayer(id);
            if (m != null && m != except) out.add(m);
        }
        return out;
    }
}
