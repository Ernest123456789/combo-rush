package com.derevna230.comborush.car;

import com.derevna230.comborush.item.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Машина: ездит, как лошадь под седлом, но руль — клавиши A/D (камера поворачивается вместе с машиной).
 * ПКМ — сесть (первый — за руль), Shift — выйти, Shift+ПКМ по пустой машине — убрать в инвентарь.
 * Стоит в мире, пока её не заберут.
 */
public class CarEntity extends MobEntity implements GeoEntity {
    private static final RawAnimation DRIVE = RawAnimation.begin().thenLoop("animation.cr_car.drive");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.cr_car.idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final CarDefs.Def def;
    /** Скорость по земле за последний тик (блоков/тик): колёса, звук, дым */
    private double hSpeed = 0;

    public CarEntity(EntityType<? extends MobEntity> type, World world) {
        super(type, world);
        this.def = CarDefs.ofType(type);
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 40)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
            .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
            .add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.0);
    }

    public CarDefs.Def def() { return def; }
    public double speed() { return hSpeed; }

    // ---------------------------------------------------------------- езда
    @Override
    public LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof PlayerEntity p ? p : null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengerList().size() < def.seatCount();
    }

    @Override
    protected void tickControlled(PlayerEntity player, Vec3d movementInput) {
        super.tickControlled(player, movementInput);
        if (!this.getWorld().isClient) return;
        // руль считает клиент водителя: A — налево, D — направо; задним ходом руль работает наоборот
        float steer = player.sidewaysSpeed;
        if (steer != 0f) {
            Vec3d v = this.getVelocity();
            double yawRad = Math.toRadians(this.getYaw());
            double along = v.x * -Math.sin(yawRad) + v.z * Math.cos(yawRad);
            float dir = along < -0.02 ? -1f : 1f;
            double sp = Math.sqrt(v.x * v.x + v.z * v.z);
            float rate = 5.2f * (float) Math.min(1.0, 0.35 + sp / 0.22);
            float delta = -Math.signum(steer) * rate * dir;
            this.setYaw(this.getYaw() + delta);
            player.setYaw(player.getYaw() + delta);               // камера едет вместе с машиной
            player.prevYaw += delta;
            player.bodyYaw += delta; player.prevBodyYaw += delta;
            player.headYaw += delta; player.prevHeadYaw += delta;
        }
        this.setPitch(0f);
        this.prevYaw = this.getYaw();
        this.prevBodyYaw = this.bodyYaw = this.getYaw();
        this.prevHeadYaw = this.headYaw = this.getYaw();
    }

    @Override
    protected Vec3d getControlledMovementInput(PlayerEntity player, Vec3d movementInput) {
        float f = player.forwardSpeed;
        if (f < 0) f *= 0.45f;                                    // назад медленнее
        return new Vec3d(0, 0, f);
    }

    @Override
    protected float getSaddledSpeed(PlayerEntity player) { return (float) def.speed(); }

    // ---------------------------------------------------------------- взаимодействие
    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (player.isSneaking() && !this.hasPassengers()) {
            if (!this.getWorld().isClient) pickUp(player);
            return ActionResult.success(this.getWorld().isClient);
        }
        if (this.getPassengerList().size() < def.seatCount() && !player.hasVehicle()) {
            if (!this.getWorld().isClient) player.startRiding(this);
            return ActionResult.success(this.getWorld().isClient);
        }
        return ActionResult.PASS;
    }

    private void pickUp(PlayerEntity player) {
        if (!player.isCreative()) player.getInventory().offerOrDrop(new ItemStack(ModItems.CARS.get(def.id())));
        this.getWorld().playSound(null, this.getBlockPos(), SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.NEUTRAL, 0.8f, 1.2f);
        this.discard();
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL)) return super.damage(source, amount);
        if (!this.getWorld().isClient && !this.isRemoved() && !this.hasPassengers()
                && source.getAttacker() instanceof PlayerEntity p && p.isCreative()) {
            this.discard();                                        // креатив: удар убирает машину
        }
        return false;
    }

    @Override public boolean isCollidable() { return true; }       // на крышу можно встать, как на лодку
    @Override public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isFireImmune() { return true; }
    @Override public boolean cannotDespawn() { return true; }
    @Override public boolean canImmediatelyDespawn(double distanceSquared) { return false; }

    // ---------------------------------------------------------------- тик: корпус по курсу, звук, дым
    @Override
    public void tick() {
        super.tick();
        this.hSpeed = Math.sqrt(MathHelper.square(this.getX() - this.prevX) + MathHelper.square(this.getZ() - this.prevZ));
        this.prevBodyYaw = this.prevYaw;                           // кузов всегда смотрит туда же, куда машина
        this.bodyYaw = this.getYaw();
        this.prevHeadYaw = this.prevYaw;
        this.headYaw = this.getYaw();

        if (!(this.getWorld() instanceof ServerWorld w) || this.getControllingPassenger() == null) return;
        if (hSpeed > 0.03 && this.age % 5 == 0)
            w.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_MINECART_RIDING, SoundCategory.NEUTRAL,
                0.35f, (float) Math.min(1.4, 0.45 + hSpeed * 1.8));
        if (hSpeed > 0.02 && this.age % 2 == 0) {                  // выхлоп сзади
            double yawRad = Math.toRadians(this.getYaw());
            double bx = this.getX() + Math.sin(yawRad) * 1.9, bz = this.getZ() - Math.cos(yawRad) * 1.9;
            w.spawnParticles(ParticleTypes.SMOKE, bx, this.getY() + 0.28, bz, 1, 0.05, 0.02, 0.05, 0.01);
        }
        if (hSpeed > 0.38 && this.isOnGround() && this.age % 3 == 0)
            w.spawnParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.1, this.getZ(), 1, 0.7, 0.02, 0.7, 0.01);
    }

    // ---------------------------------------------------------------- GeckoLib: колёса крутятся по скорости
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "drive", 2, state -> {
            if (hSpeed < 0.012) return state.setAndContinue(IDLE);
            state.getController().setAnimationSpeed(Math.max(0.12, hSpeed * 10.0 / def.wheelCirc()));
            return state.setAndContinue(DRIVE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
