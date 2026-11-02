package com.derevna230.comborush.boss;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/** Общие эффекты боссов и торговца: звуки по строковому id, кольца частиц, молнии без урона, титры */
final class Fx {
    private Fx() {}

    static final DustParticleEffect RED = new DustParticleEffect(new Vector3f(1f, 0.06f, 0.06f), 2.0f);
    static final DustParticleEffect RED_BIG = new DustParticleEffect(new Vector3f(1f, 0.1f, 0.1f), 3.2f);
    static final DustParticleEffect DARK = new DustParticleEffect(new Vector3f(0.45f, 0f, 0f), 1.8f);
    static final DustParticleEffect HOT = new DustParticleEffect(new Vector3f(1f, 0.6f, 0.15f), 1.6f);
    static final DustParticleEffect WHITE_HOT = new DustParticleEffect(new Vector3f(1f, 0.95f, 0.8f), 1.4f);

    private static final Map<String, SoundEvent> SOUNDS = new HashMap<>();

    /** Звук ванильного события по id («entity.warden.roar»). Нет такого — тишина, а не вылет */
    static void sound(ServerWorld w, double x, double y, double z, String id, float vol, float pitch) {
        SoundEvent ev = SOUNDS.get(id);
        if (ev == null) {
            ev = Registries.SOUND_EVENT.get(Identifier.ofVanilla(id));
            if (ev == null) return;
            SOUNDS.put(id, ev);
        }
        w.playSound(null, x, y, z, ev, SoundCategory.HOSTILE, vol, pitch);
    }

    static SoundEvent soundEvent(String id) {
        SoundEvent ev = SOUNDS.get(id);
        if (ev == null) {
            ev = Registries.SOUND_EVENT.get(Identifier.ofVanilla(id));
            if (ev != null) SOUNDS.put(id, ev);
        }
        return ev;
    }

    /** Кольцо частиц, разлетающихся наружу (count=0 → смещение это скорость) */
    static void ringOut(ServerWorld w, ParticleEffect p, double cx, double y, double cz, double r, int n, double speed, double up) {
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n, dx = Math.cos(a), dz = Math.sin(a);
            w.spawnParticles(p, cx + dx * r, y, cz + dz * r, 0, dx, up, dz, speed);
        }
    }

    /** Неподвижное кольцо (пыль) */
    static void ringStatic(ServerWorld w, ParticleEffect p, double cx, double y, double cz, double r, int n) {
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            w.spawnParticles(p, cx + Math.cos(a) * r, y, cz + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    static BlockStateParticleEffect debris(BlockState s) {
        return new BlockStateParticleEffect(ParticleTypes.BLOCK, s.isAir() ? Blocks.STONE.getDefaultState() : s);
    }
    static BlockStateParticleEffect redstoneDebris() { return debris(Blocks.REDSTONE_BLOCK.getDefaultState()); }

    /** Молния без урона и огня — только вспышка и гром */
    static void bolt(ServerWorld w, double x, double y, double z) {
        LightningEntity b = EntityType.LIGHTNING_BOLT.create(w);
        if (b == null) return;
        b.refreshPositionAfterTeleport(x, y, z);
        b.setCosmetic(true);
        w.spawnEntity(b);
    }

    /** Между точками нет блоков */
    static boolean clear(ServerWorld w, Vec3d a, Vec3d b, Entity ctx) {
        return w.raycast(new RaycastContext(a, b, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, ctx))
            .getType() == HitResult.Type.MISS;
    }

    static void title(ServerPlayerEntity p, String t1, String t2) {
        p.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 40, 12));
        p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(t1)));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(t2)));
    }
}
