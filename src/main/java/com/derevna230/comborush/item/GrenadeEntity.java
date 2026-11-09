package com.derevna230.comborush.item;

import com.derevna230.comborush.hud.HudPayload;
import com.derevna230.comborush.hud.HudSync;
import com.derevna230.comborush.pet.CRPetEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Брошенная граната. Физика считается только на сервере (гравитация, отскоки от стен и пола с потерей скорости, трение), клиент
 * получает позиции от трекера. Когда взрыв/вспышка сработали, сущность либо исчезает (HE, светошумовая), либо остаётся «зоной»
 * (дым — 15 с, огонь «молотова» — 7 с) и модель скрывается.
 */
public class GrenadeEntity extends Entity {
    private static final TrackedData<Integer> KIND = DataTracker.registerData(GrenadeEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> SKIN = DataTracker.registerData(GrenadeEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> DONE = DataTracker.registerData(GrenadeEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final DustParticleEffect SMOKE_DUST = new DustParticleEffect(new Vector3f(0.80f, 0.80f, 0.83f), 4.0f);
    private static final int SMOKE_LIFE = 300, FIRE_LIFE = 140;
    private static final double FIRE_R = 3.0, SMOKE_R = 3.2, HE_R = 7.0;

    private UUID owner;
    private int life;
    /** Клиент: накопленный угол кувырка в полёте */
    public float spin, prevSpin;

    public GrenadeEntity(EntityType<? extends GrenadeEntity> type, World world) {
        super(type, world);
    }

    public void setup(CRGrenadeItem.Kind kind, UUID owner, int skin) {
        this.dataTracker.set(KIND, kind.ordinal());
        this.dataTracker.set(SKIN, skin);
        this.owner = owner;
    }

    public CRGrenadeItem.Kind kind() {
        int k = dataTracker.get(KIND);
        CRGrenadeItem.Kind[] all = CRGrenadeItem.Kind.values();
        return all[Math.max(0, Math.min(all.length - 1, k))];
    }

    /** id предмета, которым бросили (скин), или id обычной гранаты */
    public String itemId() {
        int s = dataTracker.get(SKIN);
        java.util.List<String> all = GunDefs.grenadeSkinIds();
        return s >= 1 && s <= all.size() ? all.get(s - 1) : kind().id;
    }

    /** Уже сработала (модель не рисуем) */
    public boolean done() { return dataTracker.get(DONE); }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(KIND, 0);
        builder.add(SKIN, 0);
        builder.add(DONE, false);
    }

    @Override protected void readCustomDataFromNbt(NbtCompound nbt) { }
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) { }

    @Override
    public boolean isPushable() { return false; }

    // ---------------------------------------------------------------- тик
    @Override
    public void tick() {
        super.tick();
        life++;
        World world = getWorld();
        if (world.isClient) {
            prevSpin = spin;
            double sp = Math.hypot(getX() - prevX, Math.hypot(getY() - prevY, getZ() - prevZ));
            if (!done() && sp > 0.04) spin += (float) Math.min(48, sp * 90);
            return;
        }
        if (!(world instanceof ServerWorld sw)) return;
        if (!done()) flight(sw); else zone(sw);
    }

    private void flight(ServerWorld sw) {
        Vec3d v = getVelocity().add(0, -0.04, 0);
        Vec3d before = getPos();
        move(MovementType.SELF, v);
        Vec3d got = getPos().subtract(before);
        double nx = v.x, ny = v.y, nz = v.z;
        boolean hitAny = false, landed = false;
        if (Math.abs(got.x - v.x) > 1e-4) { nx = -v.x * 0.55; hitAny = true; }
        if (Math.abs(got.z - v.z) > 1e-4) { nz = -v.z * 0.55; hitAny = true; }
        if (Math.abs(got.y - v.y) > 1e-4) {
            hitAny = true;
            if (v.y < 0) {
                landed = true;
                ny = -v.y * 0.4;
                if (Math.abs(ny) < 0.07) ny = 0;
                nx *= 0.72; nz *= 0.72;
            } else ny = -v.y * 0.3;
        }
        setVelocity(new Vec3d(nx * 0.99, ny * 0.98, nz * 0.99));
        if (hitAny && v.length() > 0.2)
            sw.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_STONE_HIT, SoundCategory.PLAYERS, 0.5f, 1.5f);

        CRGrenadeItem.Kind k = kind();
        boolean boom = life >= k.fuse;
        if (k == CRGrenadeItem.Kind.MOLOTOV && !boom) {
            if (landed && life > 2) boom = true;
            else if (life > 4 && !sw.getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(0.35),
                e -> e.isAlive() && !(e instanceof CRPetEntity) && !e.isSpectator()).isEmpty()) boom = true;
        }
        if (boom) detonate(sw, k);
    }

    // ---------------------------------------------------------------- срабатывание
    private ServerPlayerEntity ownerPlayer(ServerWorld sw) {
        return owner == null ? null : sw.getServer().getPlayerManager().getPlayer(owner);
    }

    private DamageSource source(ServerWorld sw) {
        ServerPlayerEntity op = ownerPlayer(sw);
        return op != null ? sw.getDamageSources().playerAttack(op) : sw.getDamageSources().generic();
    }

    private boolean blocked(ServerWorld sw, Vec3d from, Vec3d to) {
        BlockHitResult r = sw.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
        return r.getType() != HitResult.Type.MISS;
    }

    private void detonate(ServerWorld sw, CRGrenadeItem.Kind k) {
        setVelocity(Vec3d.ZERO);
        switch (k) {
            case HE -> { explode(sw); discard(); }
            case FLASH -> { flash(sw); discard(); }
            case SMOKE -> {
                dataTracker.set(DONE, true); life = 0;
                Vec3d c = getPos().add(0, 1.1, 0);
                sw.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXTINGUISH_FIRE, SoundCategory.PLAYERS, 1.4f, 0.6f);
                sw.spawnParticles(SMOKE_DUST, c.x, c.y, c.z, 140, 1.5, 1.2, 1.5, 0.01);
            }
            case MOLOTOV -> {
                if (isTouchingWater()) {                                  // в воде огонь не разгорается
                    sw.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 1f, 1f);
                    discard();
                    return;
                }
                dataTracker.set(DONE, true); life = 0;
                sw.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.2f, 0.8f);
                sw.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.2f, 0.8f);
                sw.spawnParticles(ParticleTypes.FLAME, getX(), getY() + 0.3, getZ(), 70, 1.4, 0.25, 1.4, 0.06);
                sw.spawnParticles(ParticleTypes.LAVA, getX(), getY() + 0.3, getZ(), 10, 1.0, 0.1, 1.0, 0.0);
            }
        }
    }

    private void explode(ServerWorld sw) {
        Vec3d c = getPos().add(0, 0.2, 0);
        sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        sw.spawnParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 0.3, c.z, 25, 0.6, 0.5, 0.6, 0.05);
        sw.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 3.0f, 1.0f);
        DamageSource src = source(sw);
        Box area = new Box(c.x - HE_R, c.y - HE_R, c.z - HE_R, c.x + HE_R, c.y + HE_R, c.z + HE_R);
        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, area, e -> e.isAlive() && !(e instanceof CRPetEntity) && !e.isSpectator())) {
            Vec3d t = e.getPos().add(0, e.getHeight() * 0.5, 0);
            double d = t.distanceTo(c);
            if (d > HE_R || blocked(sw, c, t)) continue;
            float k = (float) (1.0 - d / HE_R);
            float dmg = (float) (18.0 * Math.pow(k, 1.1));
            if (dmg < 1f) continue;
            e.timeUntilRegen = 0;
            e.damage(src, dmg);
            Vec3d push = t.subtract(c).normalize().multiply(0.7 * k);
            e.addVelocity(push.x, 0.25 * k, push.z);
            e.velocityModified = true;
        }
    }

    private void flash(ServerWorld sw) {
        Vec3d c = getPos().add(0, 0.25, 0);
        sw.spawnParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        sw.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.PLAYERS, 3.0f, 1.6f);
        sw.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.2f, 1.8f);
        for (ServerPlayerEntity p : sw.getPlayers()) {
            Vec3d eye = p.getEyePos();
            double dist = eye.distanceTo(c);
            if (dist > 40 || p.isSpectator() || blocked(sw, c, eye)) continue;
            double dot = c.subtract(eye).normalize().dotProduct(p.getRotationVec(1f));
            double look = dot > 0.7 ? 1.0 : dot > 0.2 ? 0.65 : dot > -0.3 ? 0.35 : 0.18;      // смотрел прямо на вспышку — слепнет сильнее
            double near = 1.0 - Math.min(1.0, dist / 40.0) * 0.55;
            int ticks = (int) (90 * look * near);
            if (ticks > 0 && HudSync.has(p)) ServerPlayNetworking.send(p, new HudPayload("flash", "{\"t\":" + ticks + "}"));
        }
        Box area = new Box(c.x - 24, c.y - 24, c.z - 24, c.x + 24, c.y + 24, c.z + 24);
        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, area,
            e -> e.isAlive() && !(e instanceof ServerPlayerEntity) && !(e instanceof CRPetEntity))) {
            Vec3d t = e.getEyePos();
            if (t.distanceTo(c) > 24 || blocked(sw, c, t)) continue;
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 80, 0));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 0));
        }
    }

    // ---------------------------------------------------------------- зона (дым, огонь)
    private void zone(ServerWorld sw) {
        CRGrenadeItem.Kind k = kind();
        Vec3d p = getPos();
        if (k == CRGrenadeItem.Kind.SMOKE) {
            if (life >= SMOKE_LIFE) { discard(); return; }
            if (life % 2 == 0) {
                int n = life > SMOKE_LIFE - 60 ? 6 : 14;                   // под конец облако редеет
                sw.spawnParticles(SMOKE_DUST, p.x, p.y + 1.1, p.z, n, 1.4, 1.1, 1.4, 0.004);
            }
            if (life % 10 == 5) {
                Box b = new Box(p.x - SMOKE_R, p.y - 1, p.z - SMOKE_R, p.x + SMOKE_R, p.y + 3.5, p.z + SMOKE_R);
                for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, b, LivingEntity::isOnFire)) e.extinguish();
                for (GrenadeEntity g : sw.getEntitiesByClass(GrenadeEntity.class, b, g -> g != this && g.done() && g.kind() == CRGrenadeItem.Kind.MOLOTOV))
                    g.discard();                                            // дым гасит огонь «молотова»
            }
            return;
        }
        // огонь «молотова»
        if (life >= FIRE_LIFE) { discard(); return; }
        if (life % 3 == 0) sw.spawnParticles(ParticleTypes.FLAME, p.x, p.y + 0.15, p.z, 10, 1.4, 0.12, 1.4, 0.01);
        if (life % 4 == 0) sw.spawnParticles(ParticleTypes.SMOKE, p.x, p.y + 0.8, p.z, 3, 1.3, 0.4, 1.3, 0.02);
        if (life % 20 == 0) sw.playSound(null, p.x, p.y, p.z, SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.PLAYERS, 1.0f, 0.9f);
        if (life % 6 == 0) {
            DamageSource src = source(sw);
            Box b = new Box(p.x - FIRE_R, p.y - 0.5, p.z - FIRE_R, p.x + FIRE_R, p.y + 2.0, p.z + FIRE_R);
            for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, b, e -> e.isAlive() && !(e instanceof CRPetEntity) && !e.isSpectator())) {
                if (Math.hypot(e.getX() - p.x, e.getZ() - p.z) > FIRE_R) continue;
                e.setOnFireFor(3);
                e.timeUntilRegen = 0;
                e.damage(src, 1.5f);
            }
        }
    }
}
