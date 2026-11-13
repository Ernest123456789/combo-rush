package com.derevna230.comborush.match;

import com.derevna230.comborush.item.BombEntity;
import com.derevna230.comborush.item.BombItem;
import com.derevna230.comborush.item.GunDefs;
import com.derevna230.comborush.item.ModItems;
import com.derevna230.comborush.universe.DustMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Бот матча «Противостояния». Выглядит как игрок (скин по команде), держит ствол и стреляет «хитсканом» по тем же
 * правилам, что игрок (урон и темп из {@link GunDefs}, в голову ×2), но целится с ошибкой, растущей с дистанцией.
 * <ul>
 * <li><b>T</b>: бот с бомбой идёт на точку раунда и закладывает её (3,2 с), остальные идут туда же и держат точку после закладки;</li>
 * <li><b>CT</b>: держат точки A, B и центр; когда бомба заложена — бегут к ней, один обезвреживает, остальные прикрывают.</li>
 * </ul>
 * Мышление идёт только на сервере и только пока раунд в разгаре ({@link Match#live()}).
 */
public class BotEntity extends PathAwareEntity {
    private static final TrackedData<Integer> TEAM = DataTracker.registerData(BotEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final DustParticleEffect TRACER = new DustParticleEffect(new Vector3f(1.0f, 0.82f, 0.35f), 0.5f);
    private static final int PLANT_TICKS = BombItem.PLANT_TICKS;

    // ---- снаряжение
    private GunDefs.Gun gun;
    private int ammo, reloadUntil, nextShot, burstLeft = 3, burstPause;
    private boolean hasBomb, hasKit;
    /** Что бот защищает/атакует: 'A', 'B' или 'M' (центр) */
    private char role = 'A';

    // ---- состояние боя и движения
    private LivingEntity target;
    private int seenAge = -999, reactionUntil;
    private Vec3d lastSeen;
    private int strafeUntil;
    private double strafeDir;
    private int plantTicks;
    private Vec3d goal;
    private int goalAge, stuckTicks, patrolUntil;
    private int tStage;                 // Т: 0 — идём ко входу на площадку, 1 — на самой площадке
    private Vec3d stagePoint;
    private int stageDeadline = 20 * 45;
    private Vec3d lastPos = Vec3d.ZERO;

    public BotEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setPersistent();
        this.experiencePoints = 0;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 20)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.30)
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 120)
            .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.3)
            .add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(TEAM, 0);
    }

    public Team team() { return dataTracker.get(TEAM) == 0 ? Team.T : Team.CT; }

    /** Выдать бота: команда, ствол, набор, роль */
    public void setup(Team team, String gunId, boolean kit, char role) {
        dataTracker.set(TEAM, team == Team.T ? 0 : 1);
        this.gun = GunDefs.gun(gunId);
        if (this.gun == null) this.gun = GunDefs.gun(team == Team.T ? "ak47" : "m4a4");
        this.ammo = gun.mag();
        this.hasKit = kit;
        this.role = role;
        this.tStage = 0; this.stagePoint = null; this.stageDeadline = age + 20 * 45;
        net.minecraft.item.Item it = Registries.ITEM.get(Identifier.of(com.derevna230.comborush.ComboRush.MOD_ID, gun.id()));
        if (it != net.minecraft.item.Items.AIR) this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(it));
        this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0f);
    }

    public void giveBomb() { this.hasBomb = true; }
    public boolean hasBomb() { return hasBomb; }

    @Override public boolean canImmediatelyDespawn(double distanceSquared) { return false; }
    @Override public boolean cannotDespawn() { return true; }

    // ---------------------------------------------------------------- мышление
    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld sw) || !isAlive()) return;
        if (!Match.active()) { discard(); return; }
        if (!Match.live()) { getNavigation().stop(); return; }
        if (age % 5 == 0) scanTargets(sw);
        boolean fighting = target != null && target.isAlive() && age - seenAge <= 3;
        if (fighting) {
            plantTicks = 0;
            combat(sw);
        } else {
            if (target != null && (!target.isAlive() || age - seenAge > 60)) target = null;
            objective(sw);
        }
        if (age % 20 == 0) checkStuck();
    }

    // ---- цели
    private boolean canSee(ServerWorld sw, LivingEntity e) {
        Vec3d from = getEyePos();
        for (double k : new double[]{0.9, 0.5}) {
            Vec3d to = e.getPos().add(0, e.getHeight() * k, 0);
            BlockHitResult r = sw.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
            if (r.getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }

    private void scanTargets(ServerWorld sw) {
        Team mine = team();
        LivingEntity best = null;
        double bd = 1e9;
        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(64, 24, 64),
                x -> x != this && x.isAlive() && !x.isSpectator() && Match.teamOf(x) == mine.opposite())) {
            double d = squaredDistanceTo(e);
            if (d >= bd || d > 60 * 60) continue;
            if (!canSee(sw, e)) continue;
            best = e; bd = d;
        }
        if (best != null) {
            if (best != target) reactionUntil = age + 6 + random.nextInt(9);
            target = best;
            seenAge = age;
            lastSeen = best.getPos();
        }
    }

    // ---- бой
    private void combat(ServerWorld sw) {
        double dist = distanceTo(target);
        Vec3d aim = target.getPos().add(0, target.getHeight() * 0.75, 0);
        getLookControl().lookAt(aim.x, aim.y, aim.z, 40f, 40f);
        if (age >= reactionUntil) tryShoot(sw, aim, dist);

        // движение: на дальней дистанции подтягиваемся, на ближней и средней стоим и «качаемся» из стороны в сторону
        if (dist > 34) {
            goTo(target.getPos(), 1.0);
        } else {
            getNavigation().stop();
            if (age >= strafeUntil) {
                strafeDir = random.nextInt(4) == 0 ? 0 : (random.nextBoolean() ? 1 : -1);
                strafeUntil = age + 8 + random.nextInt(14);
            }
            if (strafeDir != 0 && isOnGround()) {
                Vec3d to = target.getPos().subtract(getPos());
                Vec3d perp = new Vec3d(-to.z, 0, to.x).normalize().multiply(0.13 * strafeDir);
                setVelocity(perp.x, getVelocity().y, perp.z);
                velocityModified = true;
            }
        }
    }

    private void tryShoot(ServerWorld sw, Vec3d aim, double dist) {
        if (gun == null) return;
        if (reloadUntil > age) return;
        if (ammo <= 0) {
            reloadUntil = age + gun.reloadTicks();
            ammo = gun.mag();
            sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.PLAYERS, 0.5f, 1.6f);
            return;
        }
        if (age < nextShot) return;
        if (gun.auto() && burstLeft <= 0) {
            if (age < burstPause) return;
            burstLeft = 3 + random.nextInt(4);
        }
        fire(sw, aim, dist);
        ammo--;
        if (gun.auto()) {
            burstLeft--;
            if (burstLeft <= 0) burstPause = age + 8 + random.nextInt(10);
        }
        double interval = Math.max(gun.cooldown(), 2.0) * (gun.auto() ? 1.5 : 1.3);
        nextShot = age + (int) Math.round(interval);
    }

    private void fire(ServerWorld sw, Vec3d aim, double dist) {
        Vec3d eye = getEyePos();
        Vec3d base = aim.subtract(eye).normalize();
        double moving = getVelocity().horizontalLengthSquared() > 0.003 ? 1.8 : 1.0;
        double sigma = (0.010 + dist * 0.0014) * moving;
        double range = Math.max(gun.range(), 8);
        Map<LivingEntity, Float> hits = new LinkedHashMap<>();
        Team mine = team();
        for (int i = 0; i < gun.pellets(); i++) {
            double s = sigma + gun.spread() * 1.5;
            Vec3d dir = base.add(random.nextGaussian() * s, random.nextGaussian() * s, random.nextGaussian() * s).normalize();
            Vec3d end = eye.add(dir.multiply(range));
            BlockHitResult bh = sw.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
            Vec3d stop = bh.getType() == HitResult.Type.MISS ? end : bh.getPos();
            EntityHitResult eh = ProjectileUtil.raycast(this, eye, stop, getBoundingBox().stretch(dir.multiply(range)).expand(1.0),
                e -> e instanceof LivingEntity && e.isAlive() && e != this && !e.isSpectator() && Match.teamOf(e) != mine,
                eye.squaredDistanceTo(stop));
            Vec3d hitPos = eh != null ? eh.getPos() : stop;
            double d = eye.distanceTo(hitPos);
            if (i == 0) {
                for (double t = 2.5; t < d; t += gun.pellets() > 1 ? 6.0 : 3.0) {
                    Vec3d q = eye.add(dir.multiply(t));
                    sw.spawnParticles(TRACER, q.x, q.y - 0.1, q.z, 1, 0, 0, 0, 0);
                }
            }
            if (eh != null) {
                Entity e = eh.getEntity();
                boolean head = hitPos.y >= e.getY() + e.getHeight() * 0.8;
                float dmg = gun.damage() * Match.BOT_DAMAGE * (head ? 2f : 1f);
                hits.merge((LivingEntity) e, dmg, Float::sum);
                sw.spawnParticles(ParticleTypes.CRIT, hitPos.x, hitPos.y, hitPos.z, 4, 0.1, 0.1, 0.1, 0.2);
            } else if (bh.getType() != HitResult.Type.MISS) {
                sw.spawnParticles(ParticleTypes.POOF, stop.x, stop.y, stop.z, 1, 0.05, 0.05, 0.05, 0.01);
            }
        }
        for (Map.Entry<LivingEntity, Float> h : hits.entrySet()) {
            h.getKey().timeUntilRegen = 0;
            h.getKey().damage(sw.getDamageSources().mobAttack(this), h.getValue());
        }
        if (gun.pellets() > 1) {
            sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.PLAYERS, 1.4f, 0.6f);
        } else {
            sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.5f, 1.6f);
            sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 1.0f, 0.5f);
        }
    }

    // ---- цели матча
    private void objective(ServerWorld sw) {
        if (team() == Team.T) objectiveT(sw); else objectiveCT(sw);
    }

    private Vec3d pointNear(double cx, double cz, double spread, double y) {
        return new Vec3d(cx + (random.nextDouble() * 2 - 1) * spread, y, cz + (random.nextDouble() * 2 - 1) * spread);
    }

    /** Случайная точка из списка (позиции карты проверены на проходимость, разброс только в пределах блока) */
    private Vec3d pick(java.util.List<DustMap.Pt> l, Vec3d fallback) {
        if (l.isEmpty()) return fallback;
        DustMap.Pt p = l.get(random.nextInt(l.size()));
        return pointNear(p.cx(), p.cz(), 0.35, p.y());
    }

    private boolean near2D(Vec3d p, double r) {
        double dx = getX() - p.x, dz = getZ() - p.z;
        return dx * dx + dz * dz <= r * r;
    }

    private void objectiveT(ServerWorld sw) {
        char site = Match.targetSite();
        double[] c = DustMap.siteCenter(site);
        double cy = DustMap.siteY(site);
        if (Match.bombPlanted()) {                              // держим площадку с разных позиций
            BombEntity b = Match.bomb(sw);
            if (b == null) return;
            if (age >= patrolUntil || getNavigation().isIdle() && age - goalAge > 40) {
                goTo(pick(DustMap.post(site), b.getPos()), 1.0);
                patrolUntil = age + 120 + random.nextInt(140);
            }
            return;
        }
        if (hasBomb) {
            if (DustMap.siteAt(getX(), getZ()) == site && tStage >= 1 && getNavigation().isIdle()) {     // на площадке: закладываем
                getNavigation().stop();
                getLookControl().lookAt(getX(), getY() - 1, getZ());
                plantTicks++;
                if (plantTicks % 16 == 0) sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.PLAYERS, 0.6f, 1.4f);
                if (plantTicks >= PLANT_TICKS) {
                    BombEntity.plantAt(sw, this);
                    hasBomb = false;
                    plantTicks = 0;
                }
            } else if (tStage == 0) {                           // сначала к точке входа на площадку
                if (stagePoint == null) stagePoint = pick(DustMap.entry(site), new Vec3d(c[0], cy, c[1]));
                if (near2D(stagePoint, 3.5) || age > stageDeadline) { tStage = 1; goal = null; }
                else goTo(stagePoint, 1.0);
            } else if (DustMap.siteAt(getX(), getZ()) == site) {
                if (squaredDistanceTo(c[0], getY(), c[1]) < 9) getNavigation().stop();
                else goTo(pointNear(c[0], c[1], 1.5, cy), 1.0);
            } else {
                goTo(pointNear(c[0], c[1], 1.5, cy), 1.0);
            }
            return;
        }
        // без бомбы: подобрать упавшую; иначе — пройти к входу и зачищать позиции площадки
        if (age % 10 == 0) {
            for (net.minecraft.entity.ItemEntity ie : sw.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
                    getBoundingBox().expand(64, 16, 64), i -> i.getStack().getItem() instanceof BombItem)) {
                if (squaredDistanceTo(ie) < 2.5) { ie.discard(); hasBomb = true; return; }
                goTo(ie.getPos(), 1.0);
                return;
            }
        }
        if (tStage == 0) {
            if (stagePoint == null) stagePoint = pick(DustMap.entry(site), new Vec3d(c[0], cy, c[1]));
            if (near2D(stagePoint, 3.5) || age > stageDeadline) { tStage = 1; goal = null; patrolUntil = 0; }
            else { goTo(stagePoint, 1.0); return; }
        }
        if (age >= patrolUntil || getNavigation().isIdle() && age - goalAge > 40) {
            goTo(pick(DustMap.hold(String.valueOf(site)), new Vec3d(c[0], cy, c[1])), 1.0);
            patrolUntil = age + 140 + random.nextInt(140);
        }
    }

    private void objectiveCT(ServerWorld sw) {
        if (Match.bombPlanted()) {
            BombEntity b = Match.bomb(sw);
            if (b == null) return;
            double d = Math.sqrt(squaredDistanceTo(b));
            if (d > 2.3) {
                goTo(b.getPos(), 1.0);
                return;
            }
            getNavigation().stop();
            getLookControl().lookAt(b.getX(), b.getY(), b.getZ());
            b.defuseStep(this, hasKit);
            return;
        }
        if (age >= patrolUntil || getNavigation().isIdle() && age - goalAge > 60) {
            double[] c = DustMap.siteCenter(role == 'B' ? 'B' : 'A');
            goTo(pick(DustMap.hold(String.valueOf(role)), new Vec3d(c[0], DustMap.siteY(role == 'B' ? 'B' : 'A'), c[1])), 1.0);
            patrolUntil = age + 200 + random.nextInt(260);
        }
    }

    // ---- движение
    private void goTo(Vec3d p, double speed) {
        if (goal == null || goal.squaredDistanceTo(p) > 4 || getNavigation().isIdle() && age - goalAge > 10) {
            getNavigation().startMovingTo(p.x, p.y, p.z, speed);
            goal = p;
            goalAge = age;
        }
    }

    private void checkStuck() {
        if (!getNavigation().isIdle() && getPos().squaredDistanceTo(lastPos) < 0.04) {
            if (++stuckTicks >= 2) {                           // две проверки подряд на месте — берём новую точку рядом
                stuckTicks = 0;
                goal = null;
                getJumpControl().setActive();
                Vec3d p = pointNear(getX(), getZ(), 4, getY());
                getNavigation().startMovingTo(p.x, p.y, p.z, 1.0);
            }
        } else stuckTicks = 0;
        lastPos = getPos();
    }

    // ---------------------------------------------------------------- смерть
    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        if (getWorld() instanceof ServerWorld) {
            if (hasBomb) { hasBomb = false; dropStack(new ItemStack(ModItems.BOMB)); }
            Match.onBotKilled(this, source);
        }
    }
}
