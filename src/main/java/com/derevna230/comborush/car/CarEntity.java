package com.derevna230.comborush.car;

import com.derevna230.comborush.boss.RedstoneGolemEntity;
import com.derevna230.comborush.cycle.CycleManager;
import com.derevna230.comborush.cycle.CyclePhase;
import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.item.ModItems;
import com.derevna230.comborush.score.ScoreEngine;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Машина. Едет по своей физике: разгон и торможение, руль с инерцией (поворот зависит от базы и скорости),
 * боковое сцепление (занос на высокой скорости и по ручнику), нитро. Кузов качается на «пружинах»
 * (клиентская подвеска: клёвок при торможении, крен в повороте, отскок при приземлении, плавный заезд на уступ).
 * <p>
 * Управление: W/S — газ и тормоз, A/D — руль, Пробел — ручник, Ctrl — нитро, Shift — выйти.
 * Физику считает клиент водителя (как у лодки), сервер получает готовую позицию; без водителя машину ведёт сервер.
 * ПКМ — сесть (первый за руль), Shift+ПКМ по пустой машине — гараж с прокачкой.
 */
public class CarEntity extends MobEntity implements GeoEntity {
    private static final RawAnimation DRIVE = RawAnimation.begin().thenLoop("animation.cr_car.drive");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.cr_car.idle");

    /** Клавиши водителя: выставляет клиентский тик (ComboRushClient) */
    public static volatile boolean CLIENT_HANDBRAKE = false;
    public static volatile boolean CLIENT_NITRO = false;

    private static final TrackedData<Integer> UPG = DataTracker.registerData(CarEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final CarDefs.Def def;

    /** Скорость по земле за последний тик (блоков/тик): колёса, звук, дым */
    private double hSpeed = 0;

    // ---- состояние физики (живёт у водителя на клиенте / у сервера без водителя)
    private double steer = 0;          // угол руля, градусы, влево — плюс
    private double yawRate = 0;        // рад/тик, влево — плюс
    private float nitro = -1;          // остаток нитро, тики (−1 — ещё не заполнено)
    private boolean nitroLock = false; // баллон опустел: ждём, пока наполнится до 30%
    private boolean boosting = false;

    // ---- клиентская подвеска (для отрисовки и посадки пассажиров)
    private float suspY, suspVY, prevSuspY;
    private float pitchA, pitchV, prevPitchA;
    private float rollA, rollV, prevRollA;
    private double obsVLong, aLongS, slopeS;
    private boolean wasOnGround = true;

    // ---- серверные эффекты
    private int driftTicks = 0;
    private int lastUpg = -1;
    private final Map<UUID, Integer> hitAge = new HashMap<>();
    private static final Map<UUID, Integer> DRIFT_PAID = new HashMap<>();

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

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(UPG, 0);
    }

    public CarDefs.Def def() { return def; }
    public double speed() { return hSpeed; }

    // ---------------------------------------------------------------- прокачка
    public int upgrades() { return dataTracker.get(UPG); }
    public int level(String key) { return CarUpgrades.get(upgrades(), key); }

    public void setUpgrades(int packed) {
        dataTracker.set(UPG, packed);
        applyUpgrades();
    }

    private void applyUpgrades() {
        int u = upgrades();
        lastUpg = u;
        EntityAttributeInstance step = this.getAttributeInstance(EntityAttributes.GENERIC_STEP_HEIGHT);
        if (step != null) step.setBaseValue(CarUpgrades.stepHeight(u));
        float cap = CarUpgrades.nitroCapacity(u);
        if (nitro < 0 || nitro > cap) nitro = cap;
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (UPG.equals(data)) applyUpgrades();
    }

    public ItemStack toStack() {
        ItemStack st = new ItemStack(ModItems.CARS.get(def.id()));
        CarUpgrades.write(st, upgrades());
        return st;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("CarUpg", upgrades());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        dataTracker.set(UPG, nbt.getInt("CarUpg"));
        applyUpgrades();
    }

    // ---------------------------------------------------------------- езда: посадка
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
        this.setPitch(0f);
    }

    @Override
    protected float getSaddledSpeed(PlayerEntity player) { return (float) def.topSpeed(); }

    /** Пассажиры сидят вместе с кузовом: заезд на уступ и приземление не дёргают камеру */
    @Override
    protected void updatePassengerPosition(Entity passenger, Entity.PositionUpdater positionUpdater) {
        final double off = this.getWorld().isClient ? suspY : 0;
        super.updatePassengerPosition(passenger, (e, x, y, z) -> positionUpdater.accept(e, x, y + off, z));
    }

    // ---------------------------------------------------------------- езда: физика
    @Override
    public void travel(Vec3d movementInput) {
        PlayerEntity driver = this.getControllingPassenger() instanceof PlayerEntity p ? p : null;
        boolean client = this.getWorld().isClient;
        if (driver != null && !client) return;                       // сервер: позицию присылает водитель

        float thr = 0, steerIn = 0;
        boolean hand = false, nitroKey = false;
        if (driver != null) {
            thr = MathHelper.clamp(driver.forwardSpeed, -1f, 1f);
            steerIn = MathHelper.clamp(driver.sidewaysSpeed, -1f, 1f);   // A — влево
            hand = CLIENT_HANDBRAKE;
            nitroKey = CLIENT_NITRO;
        }
        simulate(driver, thr, steerIn, hand, nitroKey);
    }

    private void simulate(PlayerEntity driver, float thr, float steerIn, boolean hand, boolean nitroKey) {
        final int upg = upgrades();
        final boolean ground = this.isOnGround();
        final boolean water = this.isTouchingWater() || this.isInLava();
        final double topBase = def.topSpeed() * CarUpgrades.topSpeedMult(upg);
        final int nitroLvl = CarUpgrades.get(upg, CarUpgrades.NITRO);
        final int tiresLvl = CarUpgrades.get(upg, CarUpgrades.TIRES);
        final float cap = CarUpgrades.nitroCapacity(upg);
        if (nitro < 0 || nitro > cap) nitro = cap;

        Vec3d vel = this.getVelocity();
        double yr = Math.toRadians(this.getYaw());
        double fx = -Math.sin(yr), fz = Math.cos(yr);
        double vLong0 = vel.x * fx + vel.z * fz;

        // ---- нитро
        boosting = false;
        if (driver != null) {
            if (nitroKey && thr > 0 && !nitroLock && nitro > 0 && vLong0 > 0.04 && !water) {
                boosting = true;
                nitro -= 1f;
                if (nitro <= 0) { nitro = 0; nitroLock = true; }
            } else {
                nitro = Math.min(cap, nitro + 0.30f + 0.10f * nitroLvl);
                if (nitroLock && nitro > cap * 0.3f) nitroLock = false;
            }
        }

        // ---- руль: угол сходится к цели с ограниченной скоростью, макс. угол падает со скоростью
        double speedFrac = Math.min(1.0, Math.abs(vLong0) / topBase);
        double maxSteer = 34.0 - 27.0 * Math.pow(speedFrac, 0.75);
        double target = steerIn * maxSteer;
        double rate = (Math.abs(target) > Math.abs(steer) ? 3.2 : 6.5) * (1.0 + 0.10 * tiresLvl);
        steer += MathHelper.clamp(target - steer, -rate, rate);

        // ---- поворот кузова (велосипедная модель), с инерцией рыскания
        double handBoost = hand && Math.abs(vLong0) > 0.10 ? 1.6 : 1.0;
        double wTarget = ground
            ? (vLong0 / def.wheelBase()) * Math.tan(Math.toRadians(steer)) * handBoost
            : yawRate * 0.98;
        if (water) wTarget *= 0.6;
        yawRate += (wTarget - yawRate) * 0.40;
        float dYaw = (float) -Math.toDegrees(yawRate);               // влево = уменьшение yaw
        if (Math.abs(dYaw) > 0.001f) {
            this.setYaw(this.getYaw() + dYaw);
            if (driver != null) {                                     // камера едет вместе с машиной
                driver.setYaw(driver.getYaw() + dYaw);
                driver.prevYaw += dYaw;
                driver.bodyYaw += dYaw; driver.prevBodyYaw += dYaw;
                driver.headYaw += dYaw; driver.prevHeadYaw += dYaw;
            }
        }

        // ---- скорость в системе кузова после поворота: импульс сохраняется, поэтому машину «сносит»
        yr = Math.toRadians(this.getYaw());
        fx = -Math.sin(yr); fz = Math.cos(yr);
        double lx = Math.cos(yr), lz = Math.sin(yr);                 // налево
        double vL = vel.x * fx + vel.z * fz;
        double vT = vel.x * lx + vel.z * lz;

        // ---- двигатель и тормоза
        double acc = def.accel() * CarUpgrades.powerMult(upg);
        double top = topBase;
        if (boosting) { acc *= CarUpgrades.nitroBoost(upg); top *= 1.28 + 0.04 * nitroLvl; }
        if (water) { acc *= 0.35; top *= 0.45; }
        double air = ground ? 1.0 : 0.15;
        if (thr > 0.05) {
            if (vL < -0.03) vL += 0.07 * air;                         // едем назад, жмём газ — тормозим
            else vL += acc * thr * Math.max(0.0, 1.0 - Math.pow(Math.max(vL, 0.0) / top, 1.6)) * air;
        } else if (thr < -0.05) {
            if (vL > 0.03) vL -= 0.06 * (ground ? 1.0 : 0.2);        // тормоз
            else vL -= 0.024 * Math.max(0.0, 1.0 - Math.pow(Math.max(-vL, 0.0) / (top * 0.32), 1.6)) * air;
        } else {
            vL *= 0.99;                                               // накат
            vL -= Math.signum(vL) * Math.min(Math.abs(vL), 0.002);
        }
        if (hand && ground) vL -= Math.signum(vL) * Math.min(Math.abs(vL), 0.02);
        if (vL > top) vL += (top - vL) * 0.05;                        // после нитро скорость плавно спадает

        // ---- боковое сцепление: чем резче поворот на скорости, тем больше сносит
        double surface = 1.0;
        if (ground) {
            BlockPos below = this.getVelocityAffectingPos();
            float slip = this.getWorld().getBlockState(below).getBlock().getSlipperiness();
            if (slip > 0.9f) surface = 0.25;
        }
        if (water) surface = 0.3;
        double gripMult = CarUpgrades.gripMult(upg);
        double limit = 0.022 * gripMult * surface;
        double over = Math.min(1.0, Math.max(0.0, (Math.abs(vL * yawRate) - limit) / 0.012));
        double grip = MathHelper.lerp(over, 0.24 * Math.min(gripMult, 1.5) * surface, 0.07 * surface);
        if (hand && Math.abs(vL) > 0.08) grip = 0.035 * surface;
        if (!ground) grip = 0.02;
        grip = MathHelper.clamp(grip, 0.01, 0.6);
        vT *= (1.0 - grip);
        vL -= Math.signum(vL) * Math.min(Math.abs(vL), Math.abs(vT) * 0.05);   // занос съедает скорость

        // ---- вертикаль
        double vy = vel.y;
        if (water) vy = Math.min(vy * 0.8 + 0.05, 0.10);
        else { vy -= 0.08; vy *= 0.98; }

        // ---- перемещение
        double hx = vL * fx + vT * lx, hz = vL * fz + vT * lz;
        double before = Math.sqrt(hx * hx + hz * hz);
        this.setVelocity(hx, vy, hz);
        this.move(MovementType.SELF, this.getVelocity());
        if (this.horizontalCollision && before > 0.40) {
            Vec3d after = this.getVelocity();
            if (Math.sqrt(after.x * after.x + after.z * after.z) < before * 0.5) {
                this.getWorld().playSound(this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_IRON_GOLEM_DAMAGE,
                    SoundCategory.NEUTRAL, 0.8f, 0.7f, false);       // удар о стену (только клиент)
                yawRate *= 0.3;
            }
        }
    }

    // ---------------------------------------------------------------- взаимодействие
    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (player.isSneaking() && !this.hasPassengers()) {
            if (player instanceof ServerPlayerEntity sp) CarGarageGui.open(sp, this);
            return ActionResult.success(this.getWorld().isClient);
        }
        if (this.getPassengerList().size() < def.seatCount() && !player.hasVehicle()) {
            if (!this.getWorld().isClient) player.startRiding(this);
            return ActionResult.success(this.getWorld().isClient);
        }
        return ActionResult.PASS;
    }

    /** Убрать машину в инвентарь вместе с прокачкой */
    public void pickUp(PlayerEntity player) {
        if (this.getWorld().isClient || this.isRemoved()) return;
        if (!player.isCreative()) player.getInventory().offerOrDrop(toStack());
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

    // ---------------------------------------------------------------- тик
    @Override
    public void tick() {
        super.tick();
        if (lastUpg != upgrades()) applyUpgrades();
        double dx = this.getX() - this.prevX, dz = this.getZ() - this.prevZ;
        this.hSpeed = Math.sqrt(dx * dx + dz * dz);
        this.prevBodyYaw = this.prevYaw;                           // кузов всегда смотрит туда же, куда машина
        this.bodyYaw = this.getYaw();
        this.prevHeadYaw = this.prevYaw;
        this.headYaw = this.getYaw();
        this.setPitch(0f);

        if (this.getWorld().isClient) tickSuspension(dx, dz);
        else if (this.getWorld() instanceof ServerWorld w) tickServer(w, dx, dz);
        wasOnGround = this.isOnGround();
    }

    // ---- клиент: пружины кузова
    private void tickSuspension(double dx, double dz) {
        prevSuspY = suspY; prevPitchA = pitchA; prevRollA = rollA;
        double dy = this.getY() - this.prevY;
        double yr = Math.toRadians(this.getYaw());
        double vLong = dx * -Math.sin(yr) + dz * Math.cos(yr);
        float yawRate = MathHelper.wrapDegrees(this.getYaw() - this.prevYaw);       // град/тик, вправо — плюс
        double aLong = vLong - obsVLong;
        obsVLong = vLong;
        aLongS += (aLong - aLongS) * 0.5;
        double aLat = vLong * Math.toRadians(yawRate);                               // влево — минус

        int softLvl = CarUpgrades.get(upgrades(), CarUpgrades.SUSPENSION);
        boolean ground = this.isOnGround();

        // ступенька: настоящая позиция скачет, а кузов должен подниматься плавно
        if (dy > 0.2 && dy < 2.6 && (ground || wasOnGround)) suspY = MathHelper.clamp(suspY - (float) dy, -2.2f, 1.0f);
        else if (dy < -0.2 && dy > -2.6 && ground && wasOnGround) suspY = MathHelper.clamp(suspY - (float) dy, -2.2f, 1.0f);
        // приземление: толчок вниз
        if (ground && !wasOnGround && dy < -0.25) suspVY += (float) Math.max(-0.6, dy * 0.55) * (1f - 0.25f * softLvl);

        float k = 0.26f, c = 0.40f;
        suspVY += -k * suspY - c * suspVY;
        suspY += suspVY;
        suspY = MathHelper.clamp(suspY, -2.2f, 1.0f);

        // уклон: нос поднимается на подъёме
        double hs = Math.max(this.hSpeed, 0.15);
        double slopeT = ground ? MathHelper.clamp(dy / hs, -1.0, 1.0) : 0.0;
        slopeS += (slopeT - slopeS) * 0.15;
        float terrain = (float) MathHelper.clamp(Math.toDegrees(Math.atan(slopeS)), -20, 20);

        float rollCut = 1f - 0.18f * softLvl;
        float pTarget = MathHelper.clamp((float) aLongS * 60f, -5f, 5f) + terrain;
        float rTarget = MathHelper.clamp((float) aLat * 95f, -6f, 6f) * rollCut;
        pitchV += -0.22f * (pitchA - pTarget) - 0.35f * pitchV;
        pitchA += pitchV;
        rollV += -0.22f * (rollA - rTarget) - 0.35f * rollV;
        rollA += rollV;

        if (this.hSpeed > 0.015 || Math.abs(suspY) > 0.01) return;
        suspY = suspVY = 0;                                          // на месте: без дрожания
    }

    public float suspY(float pt) { return MathHelper.lerp(pt, prevSuspY, suspY); }
    /** Положительный угол — нос вверх (в системе отрисовки модели) */
    public float pitchDeg(float pt) { return MathHelper.lerp(pt, prevPitchA, pitchA); }
    /** Положительный угол — правый борт вверх */
    public float rollDeg(float pt) { return MathHelper.lerp(pt, prevRollA, rollA); }

    // ---- сервер: звуки, дым, таран, очки за дрифт
    private void tickServer(ServerWorld w, double dx, double dz) {
        if (CycleManager.phase() != CyclePhase.EVENT && !DRIFT_PAID.isEmpty()) DRIFT_PAID.clear();
        ServerPlayerEntity driver = this.getControllingPassenger() instanceof ServerPlayerEntity sp ? sp : null;
        if (driver == null) { driftTicks = 0; return; }

        double yr = Math.toRadians(this.getYaw());
        double fx = -Math.sin(yr), fz = Math.cos(yr);
        double slipDeg = 0;
        if (hSpeed > 0.05) {
            double dot = Math.abs((dx * fx + dz * fz) / hSpeed);
            slipDeg = Math.toDegrees(Math.acos(MathHelper.clamp(dot, 0.0, 1.0)));
        }
        double normalTop = def.topSpeed() * CarUpgrades.topSpeedMult(upgrades());
        boolean nitroNow = hSpeed > normalTop * 1.06;
        int nitroLvl = level(CarUpgrades.NITRO);

        if (hSpeed > 0.03 && this.age % 5 == 0)
            w.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_MINECART_RIDING, SoundCategory.NEUTRAL,
                0.35f, (float) Math.min(1.4, 0.45 + hSpeed * 1.6));
        if (hSpeed > 0.02 && this.age % 2 == 0) {                  // выхлоп сзади
            double bx = this.getX() - fx * 2.4, bz = this.getZ() - fz * 2.4;
            w.spawnParticles(ParticleTypes.SMOKE, bx, this.getY() + 0.35, bz, 1, 0.05, 0.02, 0.05, 0.01);
            if (nitroNow) {
                w.spawnParticles(nitroLvl >= 3 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                    bx - fx * 0.3, this.getY() + 0.4, bz - fz * 0.3, 3 + nitroLvl, 0.08, 0.05, 0.08, 0.04);
                if (this.age % 6 == 0)
                    w.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.NEUTRAL, 0.5f, 1.6f);
            }
        }
        // занос: дым из-под задних колёс и визг
        boolean drifting = slipDeg > 18 && hSpeed > 0.30 && this.isOnGround();
        if (drifting) {
            double lx = Math.cos(yr), lz = Math.sin(yr);
            for (int s = -1; s <= 1; s += 2) {
                double px = this.getX() - fx * 1.5 + lx * 0.95 * s, pz = this.getZ() - fz * 1.5 + lz * 0.95 * s;
                w.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, px, this.getY() + 0.15, pz, 1, 0.1, 0.02, 0.1, 0.005);
            }
            if (this.age % 6 == 0)
                w.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.NEUTRAL, 0.25f, 1.8f);
            driftTicks++;
        } else if (hSpeed > 0.38 && this.isOnGround() && this.age % 3 == 0) {
            w.spawnParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.1, this.getZ(), 1, 0.9, 0.02, 0.9, 0.01);
            driftTicks = Math.max(0, driftTicks - 2);
        } else driftTicks = Math.max(0, driftTicks - 2);

        if (driftTicks >= 40) {
            driftTicks = 0;
            payDrift(driver);
        }
        if (hSpeed > 0.30 && this.age % 2 == 0) ram(w, driver, fx, fz);
    }

    private void payDrift(ServerPlayerEntity p) {
        if (CycleManager.phase() != CyclePhase.EVENT) return;
        int paid = DRIFT_PAID.getOrDefault(p.getUuid(), 0);
        if (paid >= 8) return;                                       // потолок за событие
        DRIFT_PAID.put(p.getUuid(), paid + 1);
        int eventNo = PlayerProfile.of(p).eventsDone() + 1;
        double pts = ScoreEngine.bronzeTarget(eventNo) * 0.01 * (level(CarUpgrades.TIRES) >= 3 ? 2 : 1);
        ScoreEngine.addBonus(p, pts, "DRIFT", "Дрифт!");
    }

    /** Таран: на скорости машина бьёт врагов на пути. Урон идёт от имени водителя — убийства засчитываются */
    private void ram(ServerWorld w, ServerPlayerEntity driver, double fx, double fz) {
        if (this.age % 40 == 0) hitAge.values().removeIf(t -> this.age - t > 40);
        int armor = level(CarUpgrades.ARMOR);
        Box box = this.getBoundingBox().offset(fx * 1.1, 0, fz * 1.1).expand(0.25, 0.1, 0.25);
        for (Entity e : w.getOtherEntities(this, box, ent -> ent instanceof Monster && ent.isAlive()
                && !(ent instanceof RedstoneGolemEntity) && !this.hasPassenger(ent))) {
            if (!(e instanceof LivingEntity le)) continue;
            Integer last = hitAge.get(le.getUuid());
            if (last != null && this.age - last < 8) continue;
            hitAge.put(le.getUuid(), this.age);
            float dmg = (float) ((2.5 + hSpeed * 14.0) * CarUpgrades.rammingMult(upgrades()));
            le.damage(w.getDamageSources().playerAttack(driver), dmg);
            double kb = 0.35 + hSpeed * (armor >= 2 ? 1.6 : 1.0);
            le.addVelocity(fx * kb, 0.25 + 0.08 * armor, fz * kb);
            le.velocityModified = true;
            if (armor >= 3) le.setOnFireFor(4);
            w.spawnParticles(ParticleTypes.CRIT, le.getX(), le.getBodyY(0.5), le.getZ(), 8, 0.3, 0.3, 0.3, 0.2);
            w.playSound(null, le.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.NEUTRAL, 0.9f, 0.8f);
        }
    }

    // ---------------------------------------------------------------- надпись над хотбаром у водителя (клиент)
    public String hudText() {
        int kmh = (int) Math.round(hSpeed * 20.0 * 3.6);
        float cap = CarUpgrades.nitroCapacity(upgrades());
        int bars = Math.round(10f * Math.max(0f, nitro) / cap);
        StringBuilder sb = new StringBuilder("§f").append(kmh).append(" км/ч   ");
        sb.append(nitroLock ? "§c" : (boosting ? "§6" : "§b")).append("НИТРО ");
        for (int i = 0; i < 10; i++) sb.append(i < bars ? "▮" : "▯");
        if (hSpeed < 0.02) sb.append("   §7W/S газ · A/D руль · Пробел ручник · Ctrl нитро");
        return sb.toString();
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
