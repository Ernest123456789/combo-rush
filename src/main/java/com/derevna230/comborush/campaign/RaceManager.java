package com.derevna230.comborush.campaign;

import com.derevna230.comborush.skate.SkateboardEntity;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.*;

/**
 * Осколок скорости (Акт II): гонка на скейте по 8 кольцам за 75 секунд.
 * Старт — проехать на скейте через зелёное кольцо. Кольца рисуются частицами только для тех, у кого задание активно.
 */
public final class RaceManager {
    private RaceManager() {}

    static final String QUEST = "act2_shard_speed";
    private static final int RINGS = 8, STEP = 13, LIMIT = 20 * 75;
    private static final double RADIUS = 2.3, HIT = 2.7;

    private static final class Run { int next = 1; long start; }
    private static final Map<UUID, Run> RUNS = new HashMap<>();
    private static final Map<UUID, Long> NAG = new HashMap<>();
    private static int retry = 0;

    private static final DustParticleEffect GREEN = new DustParticleEffect(new Vector3f(0.3f, 1f, 0.45f), 1.6f);
    private static final DustParticleEffect GOLD = new DustParticleEffect(new Vector3f(1f, 0.82f, 0.25f), 1.6f);
    private static final DustParticleEffect DIM = new DustParticleEffect(new Vector3f(0.55f, 0.45f, 0.9f), 1.0f);

    /** Проложить трассу от Закулисья (или от игрока), если её ещё нет */
    static void ensureCourse(ServerPlayerEntity p) {
        CampaignState st = CampaignState.get(p.getServer());
        if (!st.race.isEmpty() || --retry > 0) return;
        retry = 10;
        ServerWorld w = p.getServerWorld();
        if (w.getRegistryKey() != World.OVERWORLD) return;
        BlockPos origin = st.backstage != null ? st.backstage : p.getBlockPos();
        Random r = new Random();
        double ang = r.nextDouble() * Math.PI * 2;
        double x = origin.getX() + Math.cos(ang) * 10, z = origin.getZ() + Math.sin(ang) * 10;
        List<BlockPos> rings = new ArrayList<>();
        for (int i = 0; i < RINGS; i++) {
            int gx = (int) Math.round(x), gz = (int) Math.round(z);
            int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, gx, gz);
            int lift = (i % 3 == 2) ? 3 : 1;                 // каждое третье кольцо — повыше, через него прыгают
            rings.add(new BlockPos(gx, top + lift, gz));
            ang += (r.nextDouble() * 2 - 1) * 0.45;
            x += Math.cos(ang) * STEP; z += Math.sin(ang) * STEP;
        }
        st.race.addAll(rings);
        st.markDirty();
        for (ServerPlayerEntity o : p.getServer().getPlayerManager().getPlayerList())
            if (Campaign.isActive(o, QUEST))
                Campaign.say(o, "prompter", List.of("Трасса готова: восемь колец, семьдесят пять секунд. Встань на скейт и въезжай в зелёное кольцо — "
                    + Campaign.direction(o, rings.get(0)) + "."));
    }

    static BlockPos target(ServerPlayerEntity p) {
        CampaignState st = CampaignState.get(p.getServer());
        if (st.race.isEmpty()) return st.backstage;
        Run run = RUNS.get(p.getUuid());
        return st.race.get(run == null ? 0 : Math.min(run.next, st.race.size() - 1));
    }

    static int timeLeft(ServerPlayerEntity p) {
        Run run = RUNS.get(p.getUuid());
        return run == null ? -1 : (int) Math.max(0, (LIMIT - (p.getServer().getTicks() - run.start)) / 20);
    }

    static void tick(MinecraftServer server) {
        CampaignState st = CampaignState.get(server);
        if (st.race.size() < 2) { RUNS.clear(); return; }
        int now = server.getTicks();
        ServerWorld w = server.getOverworld();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            Run run = RUNS.get(p.getUuid());
            if (p.getServerWorld() != w || p.isSpectator() || !Campaign.isActive(p, QUEST)) { RUNS.remove(p.getUuid()); continue; }
            boolean onSkate = p.getVehicle() instanceof SkateboardEntity;
            Vec3d at = p.getPos().add(0, 1, 0);
            if (now % 4 == 0 && p.squaredDistanceTo(Vec3d.ofCenter(st.race.get(0))) < 160 * 160) draw(w, p, st.race, run);
            if (run == null) {
                if (at.squaredDistanceTo(Vec3d.ofCenter(st.race.get(0))) < HIT * HIT) {
                    if (!onSkate) {
                        if (now - NAG.getOrDefault(p.getUuid(), 0L) > 60) {
                            NAG.put(p.getUuid(), (long) now);
                            p.sendMessage(Text.literal("§eГонка только на скейте! Встань на него и проедь через зелёное кольцо."), true);
                        }
                        continue;
                    }
                    run = new Run();
                    run.start = now;
                    RUNS.put(p.getUuid(), run);
                    title(p, "§a§lГОНКА!", "§fвосемь колец · 75 секунд");
                    w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 1.2f, 1.2f);
                }
                continue;
            }
            if (now - run.start > LIMIT) { fail(p, "§cВремя вышло! Возвращайся к зелёному кольцу."); continue; }
            if (!onSkate) { fail(p, "§cСвалился со скейта — гонка сорвана."); continue; }
            BlockPos ring = st.race.get(run.next);
            if (at.squaredDistanceTo(Vec3d.ofCenter(ring)) < HIT * HIT) {
                run.next++;
                w.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, ring.getX() + 0.5, ring.getY() + 0.5, ring.getZ() + 0.5, 25, 0.8, 0.8, 0.8, 0.3);
                w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 1.2f,
                    (float) Math.pow(2, (run.next - 1) / 12.0 * 2));
                if (run.next >= st.race.size()) {
                    RUNS.remove(p.getUuid());
                    int sec = (now - run.start) / 20;
                    title(p, "§6§lФИНИШ!", "§f" + sec + " с");
                    Campaign.trigger(p, "race", "", 1);
                } else p.sendMessage(Text.literal("§aКольцо " + (run.next - 1) + "/" + (st.race.size() - 1)
                    + " §7· осталось " + Math.max(0, (LIMIT - (now - run.start)) / 20) + " с"), true);
            }
        }
    }

    private static void fail(ServerPlayerEntity p, String msg) {
        RUNS.remove(p.getUuid());
        title(p, "§c§lМИМО", "");
        p.sendMessage(Text.literal(msg), false);
        p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 1f, 0.8f);
    }

    /** Кольцо — вертикальная окружность поперёк направления к следующему кольцу */
    private static void draw(ServerWorld w, ServerPlayerEntity p, List<BlockPos> rings, Run run) {
        int next = run == null ? 0 : run.next;
        for (int i = 0; i < rings.size(); i++) {
            DustParticleEffect fx;
            int points;
            if (i == next) { fx = run == null ? GREEN : GOLD; points = 24; }
            else if (i > next && i <= next + 2) { fx = DIM; points = 12; }
            else continue;
            BlockPos c = rings.get(i);
            BlockPos toward = rings.get(Math.min(i + 1, rings.size() - 1));
            if (i == rings.size() - 1) toward = c.add(c.subtract(rings.get(i - 1)));
            double dx = toward.getX() - c.getX(), dz = toward.getZ() - c.getZ();
            double len = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
            double sx = -dz / len, sz = dx / len;                       // ось «вбок»
            double cx = c.getX() + 0.5, cy = c.getY() + 0.5, cz = c.getZ() + 0.5;
            for (int k = 0; k < points; k++) {
                double a = k * Math.PI * 2 / points;
                double px = cx + Math.cos(a) * RADIUS * sx, py = cy + Math.sin(a) * RADIUS, pz = cz + Math.cos(a) * RADIUS * sz;
                w.spawnParticles(p, fx, true, px, py, pz, 1, 0, 0, 0, 0);
            }
        }
    }

    private static void title(ServerPlayerEntity p, String a, String b) {
        p.networkHandler.sendPacket(new TitleFadeS2CPacket(3, 30, 8));
        p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(a)));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(b)));
    }
}
