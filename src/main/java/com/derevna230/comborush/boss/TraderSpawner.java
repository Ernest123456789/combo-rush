package com.derevna230.comborush.boss;

import com.derevna230.comborush.ModConfig;
import com.derevna230.comborush.pet.ModEntities;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Торговец Редстоуна приходит к игрокам сам, как странствующий торговец: примерно раз в trader_minutes минут
 * (по умолчанию 30) с шансом, который растёт после каждой неудачной попытки. Один торговец на игрока.
 */
public final class TraderSpawner {
    private TraderSpawner() {}

    private static final Map<UUID, Integer> WAIT = new HashMap<>();
    private static final Map<UUID, Integer> FAILS = new HashMap<>();
    private static final Random R = new Random();
    private static final String[] COMPASS = {"севернее", "северо-восточнее", "восточнее", "юго-восточнее", "южнее", "юго-западнее", "западнее", "северо-западнее"};

    private static int interval() {
        int minutes = Math.max(2, ModConfig.get().trader_minutes);
        return (int) (minutes * 1200 * (0.7 + R.nextDouble() * 0.6));
    }

    public static void tick(MinecraftServer server) {
        if (!ModConfig.get().trader_enabled || server.getTicks() % 20 != 0) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            if (!(p.getWorld() instanceof ServerWorld w) || w.getRegistryKey() != World.OVERWORLD) continue;
            UUID id = p.getUuid();
            int left = WAIT.computeIfAbsent(id, k -> (int) (interval() * (0.3 + R.nextDouble() * 0.4))) - 20;
            if (left > 0) { WAIT.put(id, left); continue; }
            if (hasTraderNear(w, p) || RedstoneGolemEntity.bossNear(w, p.getPos(), 80)) { WAIT.put(id, 2400); continue; }
            double chance = Math.min(0.9, 0.35 + 0.15 * FAILS.getOrDefault(id, 0));
            if (R.nextDouble() < chance && spawnNear(w, p)) { FAILS.remove(id); WAIT.put(id, interval()); }
            else { FAILS.merge(id, 1, Integer::sum); WAIT.put(id, 2400 + R.nextInt(2400)); }
        }
    }

    private static boolean hasTraderNear(ServerWorld w, ServerPlayerEntity p) {
        return !w.getEntitiesByClass(GolemTraderEntity.class, Box.of(p.getPos(), 256, 256, 256), e -> true).isEmpty();
    }

    /** Ищет ровное открытое место в 22–34 блоках от игрока и ставит торговца. false — не нашёл */
    public static boolean spawnNear(ServerWorld w, ServerPlayerEntity p) {
        for (int i = 0; i < 16; i++) {
            double a = R.nextDouble() * Math.PI * 2, r = 22 + R.nextDouble() * 12;
            int x = (int) Math.floor(p.getX() + Math.cos(a) * r), z = (int) Math.floor(p.getZ() + Math.sin(a) * r);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (!w.getBlockState(pos.down()).isSolidBlock(w, pos.down()) || !w.getFluidState(pos.down()).isEmpty()) continue;
            if (!w.getBlockState(pos).isAir() || !w.getBlockState(pos.up()).isAir()) continue;
            GolemTraderEntity t = ModEntities.TRADER.create(w);
            if (t == null) return false;
            t.refreshPositionAndAngles(x + 0.5, y, z + 0.5, R.nextFloat() * 360f, 0);
            t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20 * 90, 0, false, false));
            w.spawnEntity(t);
            Fx.bolt(w, x + 0.5, y, z + 0.5);
            w.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, x + 0.5, y + 1, z + 0.5, 30, 0.4, 0.8, 0.4, 0.3);
            w.spawnParticles(Fx.RED_BIG, x + 0.5, y + 1, z + 0.5, 20, 0.5, 1.0, 0.5, 0);
            Fx.sound(w, x + 0.5, y, z + 0.5, "entity.wandering_trader.reappeared", 1.5f, 0.8f);
            double dx = x + 0.5 - p.getX(), dz = z + 0.5 - p.getZ();
            int dir = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0), 8);
            int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
            p.sendMessage(Text.literal("§c§l⚡ §fВ ваших краях появился §cТорговец Редстоуна§f! §7Он примерно в §e" + dist + " блоках §7" + COMPASS[dir]
                + " от вас и светится. Уйдёт через 10 минут. Он продаёт §cСердце голема§7 — призыв босса."), false);
            return true;
        }
        return false;
    }
}
