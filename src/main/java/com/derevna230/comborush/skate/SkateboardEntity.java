package com.derevna230.comborush.skate;

import com.derevna230.comborush.item.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Скейтборд, на котором стоит игрок. Управление — как у лошади (WASD), но едет игрок сам на своей стороне:
 * прыжок и планирование считаются на клиенте, а сервер рисует следы и искры для всех.
 * Слез (Shift) — скейт возвращается в инвентарь предметом с той же прокачкой.
 */
public class SkateboardEntity extends MobEntity implements GeoEntity {
    private static final TrackedData<Integer> SPEED = DataTracker.registerData(SkateboardEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> JUMP = DataTracker.registerData(SkateboardEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> GLIDE = DataTracker.registerData(SkateboardEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final RawAnimation ROLL = RawAnimation.begin().thenLoop("animation.cr_skate.roll");

    /** Клиент: зажат ли пробел (ставит клиентская часть мода каждый тик) */
    public static volatile boolean CLIENT_JUMP = false;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean jumpLatch = false;
    private boolean wasOnGround = true;
    private int sparkTicks = 0;
    private Vec3d lastPos = null;

    public SkateboardEntity(EntityType<? extends MobEntity> type, World world) {
        super(type, world);
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 10)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.26)
            .add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(SPEED, 0);
        builder.add(JUMP, 0);
        builder.add(GLIDE, 0);
    }

    public int speedLevel() { return dataTracker.get(SPEED); }
    public int jumpLevel() { return dataTracker.get(JUMP); }
    public int glideLevel() { return dataTracker.get(GLIDE); }

    public void loadFrom(ItemStack st) {
        dataTracker.set(SPEED, SkateData.get(st, SkateData.SPEED));
        dataTracker.set(JUMP, SkateData.get(st, SkateData.JUMP));
        dataTracker.set(GLIDE, SkateData.get(st, SkateData.GLIDE));
    }

    public ItemStack toStack() {
        ItemStack st = new ItemStack(ModItems.SKATEBOARD);
        SkateData.set(st, SkateData.SPEED, speedLevel());
        SkateData.set(st, SkateData.JUMP, jumpLevel());
        SkateData.set(st, SkateData.GLIDE, glideLevel());
        return st;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("SkSpeed", speedLevel()); nbt.putInt("SkJump", jumpLevel()); nbt.putInt("SkGlide", glideLevel());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        dataTracker.set(SPEED, nbt.getInt("SkSpeed")); dataTracker.set(JUMP, nbt.getInt("SkJump")); dataTracker.set(GLIDE, nbt.getInt("SkGlide"));
    }

    // ---------------------------------------------------------------- езда
    @Override
    public LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof PlayerEntity p ? p : null;
    }

    @Override
    protected void tickControlled(PlayerEntity player, Vec3d movementInput) {
        super.tickControlled(player, movementInput);
        this.setRotation(player.getYaw(), 0f);
        this.prevYaw = this.bodyYaw = this.headYaw = this.getYaw();
        if (!this.getWorld().isClient) return;
        // клиент сам двигает свой скейт: прыжок и планирование
        boolean jump = CLIENT_JUMP;
        Vec3d v = this.getVelocity();
        if (jump && this.isOnGround() && !jumpLatch) {
            this.setVelocity(v.x * 1.1, SkateData.jumpVelocity(jumpLevel()), v.z * 1.1);
            this.velocityDirty = true;
            jumpLatch = true;
        }
        if (!jump) jumpLatch = false;
        if (jump && !this.isOnGround() && glideLevel() > 0 && v.y < 0) {
            this.setVelocity(v.x * 1.012, Math.max(v.y, SkateData.glideFall(glideLevel())), v.z * 1.012);
            this.fallDistance = 0;
        }
    }

    @Override
    protected Vec3d getControlledMovementInput(PlayerEntity player, Vec3d movementInput) {
        float f = player.forwardSpeed;
        if (f < 0) f *= 0.4f;                                            // назад медленнее
        return new Vec3d(player.sidewaysSpeed * 0.35f, 0, f);
    }

    @Override
    protected float getSaddledSpeed(PlayerEntity player) { return SkateData.speed(speedLevel()); }

    /** Скейты игроков, погибших на них при keepInventory: выдаются после возрождения */
    private static final java.util.Map<java.util.UUID, ItemStack> PENDING = new java.util.HashMap<>();

    public static void registerEvents() {
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            ItemStack st = PENDING.remove(newPlayer.getUuid());
            if (st != null) newPlayer.getInventory().offerOrDrop(st);
        });
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!this.getWorld().isClient && !this.isRemoved()) {
            if (passenger instanceof ServerPlayerEntity sp && sp.isAlive()) sp.getInventory().offerOrDrop(toStack());
            else if (passenger instanceof ServerPlayerEntity dead
                    && dead.getServerWorld().getGameRules().getBoolean(net.minecraft.world.GameRules.KEEP_INVENTORY))
                PENDING.put(dead.getUuid(), toStack());          // умер на скейте при сохранении инвентаря — вернём после возрождения
            else this.dropStack(toStack());
            this.discard();
        }
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!this.hasPassengers() && !player.hasVehicle()) {
            if (!this.getWorld().isClient) player.startRiding(this);
            return ActionResult.success(this.getWorld().isClient);
        }
        return super.interactMob(player, hand);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL)) return super.damage(source, amount);
        if (!this.getWorld().isClient && !this.hasPassengers() && source.getAttacker() instanceof PlayerEntity && !this.isRemoved()) {
            this.dropStack(toStack());
            this.discard();
        }
        return false;
    }

    @Override public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean cannotDespawn() { return true; }
    @Override public boolean canImmediatelyDespawn(double distanceSquared) { return false; }

    // ---------------------------------------------------------------- эффекты (сервер — видно всем)
    @Override
    public void tick() {
        super.tick();
        if (!(this.getWorld() instanceof ServerWorld w)) return;
        if (!this.hasPassengers() && this.age > 40) {                    // пустой скейт не валяется в мире
            this.dropStack(toStack());
            this.discard();
            return;
        }
        Vec3d pos = this.getPos();
        Vec3d d = lastPos == null ? Vec3d.ZERO : pos.subtract(lastPos);
        lastPos = pos;
        double hs = Math.sqrt(d.x * d.x + d.z * d.z);
        boolean ground = this.isOnGround();

        if (speedLevel() > 0 && hs > 0.18) {                             // след полихрома
            float hue = (this.age * 0.035f) % 1f;
            for (int i = 0; i < speedLevel(); i++) {
                float h = (hue + i * 0.12f) % 1f;
                w.spawnParticles(new DustParticleEffect(hsv(h), 1.1f), getX() - d.x * i * 0.5, getY() + 0.12, getZ() - d.z * i * 0.5,
                    1, 0.12, 0.02, 0.12, 0);
            }
            if (this.age % 3 == 0) w.spawnParticles(ParticleTypes.END_ROD, getX(), getY() + 0.15, getZ(), 1, 0.1, 0.05, 0.1, 0.005);
        }
        if (wasOnGround && !ground && d.y > 0.2) {                        // прыжок
            w.playSound(null, getBlockPos(), SoundEvents.ENTITY_BREEZE_JUMP, SoundCategory.PLAYERS, 0.6f, 1.4f);
            if (jumpLevel() > 0) {
                w.spawnParticles(ParticleTypes.WHITE_ASH, getX(), getY() + 0.1, getZ(), 25, 0.4, 0.05, 0.4, 0.02);
                w.spawnParticles(ParticleTypes.END_ROD, getX(), getY() + 0.1, getZ(), 6 + jumpLevel() * 3, 0.3, 0.05, 0.3, 0.06);
                sparkTicks = 12;
            }
        }
        if (sparkTicks > 0) {
            sparkTicks--;
            w.spawnParticles(ParticleTypes.WHITE_ASH, getX(), getY() + 0.1, getZ(), 3, 0.2, 0.05, 0.2, 0.01);
        }
        if (!ground && glideLevel() > 0 && d.y < 0 && d.y > -0.12)     // планирование
            w.spawnParticles(ParticleTypes.CLOUD, getX(), getY() + 0.05, getZ(), 1, 0.25, 0, 0.25, 0.005);
        if (!wasOnGround && ground)
            w.playSound(null, getBlockPos(), SoundEvents.BLOCK_WOOD_STEP, SoundCategory.PLAYERS, 0.8f, 0.7f);
        if (ground && hs > 0.15 && this.age % 8 == 0)
            w.playSound(null, getBlockPos(), SoundEvents.ENTITY_MINECART_INSIDE, SoundCategory.PLAYERS, 0.05f, 1.8f);
        wasOnGround = ground;
    }

    private static Vector3f hsv(float h) {
        float r = Math.abs(h * 6 - 3) - 1, g = 2 - Math.abs(h * 6 - 2), b = 2 - Math.abs(h * 6 - 4);
        return new Vector3f(clamp(r), clamp(g), clamp(b));
    }
    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

    // ---------------------------------------------------------------- GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "roll", 2, state -> {
            if (!state.isMoving() && this.getVelocity().horizontalLengthSquared() < 1e-4) return PlayState.STOP;
            return state.setAndContinue(ROLL);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
