package com.derevna230.comborush.anti;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Помечает враждебных мобов, появившихся рядом со спавнером (тегом cr_spawner; тег сохраняется в мире).
 * За таких мобов даётся лишь часть очков, а статистика убийств не растёт.
 * Без миксинов: список загруженных спавнеров ведём по событиям блок-сущностей, а моб помечается при появлении в мире.
 */
public class SpawnerTracker {
    public static final String TAG = "cr_spawner";
    /** Радиус появления мобов у спавнера в Minecraft — 4 блока; берём с запасом */
    private static final double HORIZONTAL = 6.0, VERTICAL = 3.5;

    private static final Map<String, Map<Long, Set<BlockPos>>> SPAWNERS = new HashMap<>();

    public static void init() {
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((be, world) -> {
            if (be instanceof MobSpawnerBlockEntity) add(world, be.getPos());
        });
        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((be, world) -> {
            if (be instanceof MobSpawnerBlockEntity) remove(world, be.getPos());
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof HostileEntity) || entity.getCommandTags().contains(TAG)) return;
            if (nearSpawner(world, entity.getPos())) entity.addCommandTag(TAG);
        });
    }

    public static boolean isSpawnerMob(Entity e) { return e.getCommandTags().contains(TAG); }

    private static String key(ServerWorld w) { return w.getRegistryKey().getValue().toString(); }

    private static void add(ServerWorld w, BlockPos pos) {
        long ck = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
        SPAWNERS.computeIfAbsent(key(w), k -> new HashMap<>()).computeIfAbsent(ck, k -> new HashSet<>()).add(pos.toImmutable());
    }

    private static void remove(ServerWorld w, BlockPos pos) {
        Map<Long, Set<BlockPos>> m = SPAWNERS.get(key(w));
        if (m == null) return;
        Set<BlockPos> set = m.get(ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4));
        if (set != null) set.remove(pos);
    }

    private static boolean nearSpawner(ServerWorld w, Vec3d p) {
        Map<Long, Set<BlockPos>> m = SPAWNERS.get(key(w));
        if (m == null || m.isEmpty()) return false;
        int cx = (int) Math.floor(p.x) >> 4, cz = (int) Math.floor(p.z) >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Set<BlockPos> set = m.get(ChunkPos.toLong(cx + dx, cz + dz));
                if (set == null) continue;
                for (BlockPos s : set) {
                    double ddx = p.x - (s.getX() + 0.5), ddz = p.z - (s.getZ() + 0.5), ddy = p.y - (s.getY() + 0.5);
                    if (ddx * ddx + ddz * ddz <= HORIZONTAL * HORIZONTAL && Math.abs(ddy) <= VERTICAL) return true;
                }
            }
        }
        return false;
    }
}
