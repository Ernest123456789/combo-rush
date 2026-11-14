package com.derevna230.comborush.match;

import com.derevna230.comborush.item.BombEntity;
import com.derevna230.comborush.item.BombItem;
import com.derevna230.comborush.item.GunDefs;
import com.derevna230.comborush.item.ModItems;
import com.derevna230.comborush.universe.GameMap;
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
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Бот матча. Стреляет «хитсканом» по тем же правилам, что игрок (урон по частям тела — {@link HitBoxes}).
 * <ul>
 * <li><b>Позиции.</b> У каждой карты есть позиции (HOLD/ENTRY/POST) с направлением взгляда. Бот занимает свободную,
 * стоит на ней и «держит угол» (смотрит туда, слегка поводя прицелом), время от времени меняет позицию.</li>
 * <li><b>КТ</b> держат A, B и центр; если кто-то из своих заметил противника у площадки — часть ботов перебегает туда;
 * после закладки — идут на бомбу и обезвреживают.</li>
 * <li><b>Т</b> собираются у входа на выбранную площадку, ждут своих (или 35 с), заходят вместе, занимая позиции на площадке;
 * один из Т может «обходить» — ждать у другой площадки. После закладки держат площадку с позиций POST.</li>
 * <li><b>Стрельба.</b> Ошибка прицела мала и почти не растёт с дистанцией, но первые выстрелы после обнаружения цели и стрельба
 * на бегу менее точны; со снайперской винтовкой — только стоя. На дальней дистанции бот не бежит на противника, а стреляет с места.</li>
 * </ul>
 */
public class BotEntity extends PathAwareEntity {
    private static final TrackedData<Integer> TEAM = DataTracker.registerData(BotEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final DustParticleEffect TRACER = new DustParticleEffect(new Vector3f(1.0f, 0.82f, 0.35f), 0.5f);
    private static final int PLANT_TICKS = BombItem.PLANT_TICKS;

    // ---- снаряжение
    private GunDefs.Gun gun;
    private int ammo, reloadUntil, nextShot, burstLeft = 3, burstPause;
    private boolean hasBomb, hasKit;
    /** 'A', 'B', 'M' (центр) или для Т 'L' — обход */
    private char role = 'A';

    // ---- бой
    private LivingEntity target;
    private int seenAge = -999, reactionUntil, acquireAge;
    private boolean aimHead;
    private int strafeUntil;
    private double strafeDir;
    private int plantTicks;

    // ---- движение и позиции
    private Vec3d goal;
    private int goalAge, stuckTicks;
    private Vec3d lastPos = Vec3d.ZERO;
    private GameMap.Pt post;              // занятая позиция
    private String postList = "";         // из какого списка она
    private int postUntil;
    private boolean onPost;
    private int tStage;                   // Т: 0 — к месту сбора, 1 — заход на площадку
    private GameMap.Pt stage;
    private char rotatedTo;               // КТ: на какую площадку уже перебежали по тревоге

    public BotEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setPersistent();
        this.experiencePoints = 0;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 20)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.30)
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 160)
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
        this.tStage = 0; this.stage = null; this.post = null; this.rotatedTo = 0;
        net.minecraft.item.Item it = Registries.ITEM.get(Identifier.of(com.derevna230.comborush.ComboRush.MOD_ID, gun.id()));
        if (it != net.minecraft.item.Items.AIR) this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(it));
        this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0f);
    }

    private String statKey = "bot:?", botName = "Бот";
    public void setIdentity(String name) { this.botName = name; this.statKey = "bot:" + name; }
    public String statKey() { return statKey; }
    public String botName() { return botName; }

    public void giveBomb() { this.hasBomb = true; }
    public boolean hasBomb() { return hasBomb; }

    @Override public boolean canImmediatelyDespawn(double distanceSquared) { return false; }
    @Override public boolean cannotDespawn() { return true; }

    private GameMap map() { return Match.map(); }

    // ================================================================= мышление
    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld sw) || !isAlive()) return;
        if (!Match.active()) { discard(); return; }
        if (!Match.live()) { getNavigation().stop(); return; }
        if (age % 4 == 0) scanTargets(sw);
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

    // ---- обзор
    private boolean canSee(ServerWorld sw, LivingEntity e) {
        Vec3d from = getEyePos();
        for (double k : new double[]{0.92, 0.6, 0.3}) {
            Vec3d to = e.getPos().add(0, e.getHeight() * k, 0);
            BlockHitResult r = sw.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
            if (r.getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }

    private double maxRange() { return gun == null ? 60 : Math.min(150, Math.max(40, gun.range() + 10)); }

    private void scanTargets(ServerWorld sw) {
        Team mine = team();
        LivingEntity best = null;
        double bd = 1e9, mr = maxRange();
        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(mr, 30, mr),
                x -> x != this && x.isAlive() && !x.isSpectator() && Match.teamOf(x) == mine.opposite())) {
            double d = squaredDistanceTo(e);
            if (d >= bd || d > mr * mr) continue;
            // поле зрения: сзади (больше ~110° от взгляда) далёких не замечаем, близких — слышим
            Vec3d to = e.getPos().subtract(getPos());
            Vec3d look = getRotationVec(1f);
            if (d > 10 * 10 && look.x * to.x + look.z * to.z < -0.35 * Math.sqrt(to.x * to.x + to.z * to.z)) continue;
            if (!canSee(sw, e)) continue;
            best = e; bd = d;
        }
        if (best != null) {
            if (best != target) {
                reactionUntil = age + 5 + random.nextInt(8);
                acquireAge = age;
                Match.spotted(mine, best.getPos());
            }
            target = best;
            seenAge = age;
        }
    }

    // ================================================================= бой
    private void combat(ServerWorld sw) {
        double dist = distanceTo(target);
        if (age % 15 == 0) aimHead = random.nextInt(100) < 22;
        Vec3d aim = target.getPos().add(0, target.getHeight() * (aimHead ? 0.9 : 0.68), 0);
        getLookControl().lookAt(aim.x, aim.y, aim.z, 40f, 40f);
        boolean sniper = gun != null && gun.scope();
        boolean shotgun = gun != null && gun.pellets() > 1;
        double effective = shotgun ? 14 : gun == null ? 40 : gun.range() * 0.9;

        if (dist > effective) {
            goTo(target.getPos(), 1.0);                        // не достаём — подтягиваемся
        } else {
            getNavigation().stop();
            setSprinting(false);
            // на позиции и со снайперкой стоим, иначе изредка «качаемся»
            if (!onPost && !sniper && dist < 26) {
                if (age >= strafeUntil) {
                    strafeDir = random.nextInt(3) == 0 ? 0 : (random.nextBoolean() ? 1 : -1);
                    strafeUntil = age + 10 + random.nextInt(16);
                }
                if (strafeDir != 0 && isOnGround()) {
                    Vec3d to = target.getPos().subtract(getPos());
                    Vec3d perp = new Vec3d(-to.z, 0, to.x).normalize().multiply(0.12 * strafeDir);
                    setVelocity(perp.x, getVelocity().y, perp.z);
                    velocityModified = true;
                }
            }
        }
        boolean moving = getVelocity().horizontalLengthSquared() > 0.004;
        if (age >= reactionUntil && !(sniper && moving)) tryShoot(sw, aim, dist, moving);
    }

    private void tryShoot(ServerWorld sw, Vec3d aim, double dist, boolean moving) {
        if (gun == null || reloadUntil > age) return;
        if (ammo <= 0) {
            reloadUntil = age + gun.reloadTicks();
            ammo = gun.mag();
            sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.PLAYERS, 0.5f, 1.6f);
            return;
        }
        if (age < nextShot) return;
        if (gun.auto()) {
            if (burstLeft <= 0) {
                if (age < burstPause) return;
                burstLeft = dist > 30 ? 2 + random.nextInt(2) : 3 + random.nextInt(4);   // вдали — короткие очереди
            }
        }
        fire(sw, aim, dist, moving);
        ammo--;
        if (gun.auto()) {
            burstLeft--;
            if (burstLeft <= 0) burstPause = age + (dist > 30 ? 10 : 7) + random.nextInt(8);
        }
        double interval = Math.max(gun.cooldown(), 2.0) * (gun.auto() ? 1.4 : 1.25);
        nextShot = age + (int) Math.round(interval);
    }

    private void fire(ServerWorld sw, Vec3d aim, double dist, boolean moving) {
        Vec3d eye = getEyePos();
        Vec3d base = aim.subtract(eye).normalize();
        double settle = 0.035 * Math.max(0, 1 - (age - acquireAge) / 16.0);       // первые выстрелы после обнаружения — хуже
        double sigma = 0.0035 + dist * 0.00008 + settle + (moving ? 0.022 : 0) + (gun.scope() ? 0 : gun.spread() * 1.2)
            + (onPost ? 0 : 0.002);
        double range = Math.max(gun.range(), 8);
        Map<LivingEntity, Float> hits = new LinkedHashMap<>();
        Team mine = team();
        for (int i = 0; i < gun.pellets(); i++) {
            double s = gun.pellets() > 1 ? gun.spread() + 0.01 : sigma;
            Vec3d dir = base.add(random.nextGaussian() * s, random.nextGaussian() * s, random.nextGaussian() * s).normalize();
            Vec3d end = eye.add(dir.multiply(range));
            BlockHitResult bh = sw.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
            Vec3d stop = bh.getType() == HitResult.Type.MISS ? end : bh.getPos();
            HitBoxes.Hit eh = HitBoxes.trace(sw, this, eye, stop,
                e -> e instanceof LivingEntity && e.isAlive() && e != this && !e.isSpectator() && Match.teamOf(e) != mine);
            Vec3d hitPos = eh != null ? eh.pos() : stop;
            double d = eye.distanceTo(hitPos);
            if (i == 0) {
                for (double t = 2.5; t < d; t += gun.pellets() > 1 ? 6.0 : 3.0) {
                    Vec3d q = eye.add(dir.multiply(t));
                    sw.spawnParticles(TRACER, q.x, q.y - 0.1, q.z, 1, 0, 0, 0, 0);
                }
            }
            if (eh != null) {
                MatchStats.noteHit(eh.entity(), this, eh.part());
                hits.merge(eh.entity(), gun.damage() * Match.BOT_DAMAGE * eh.part().mult, Float::sum);
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

    // ================================================================= позиции
    private static Vec3d at(GameMap.Pt p) { return new Vec3d(p.cx(), p.y(), p.cz()); }

    /** Занять свободную позицию из списка (ближайшую из пары случайных — чтобы не бегать через всю карту) */
    private GameMap.Pt claimFrom(List<GameMap.Pt> list, String name) {
        List<GameMap.Pt> free = new ArrayList<>();
        for (GameMap.Pt p : list) if (!Match.claimedByOther(name + p.key(), getUuid()) && (post == null || !p.equals(post))) free.add(p);
        if (free.isEmpty()) return list.isEmpty() ? null : list.get(random.nextInt(list.size()));
        GameMap.Pt a = free.get(random.nextInt(free.size())), b = free.get(random.nextInt(free.size()));
        GameMap.Pt p = squaredDistanceTo(at(a)) <= squaredDistanceTo(at(b)) ? a : b;
        Match.claim(name + p.key(), getUuid());
        return p;
    }

    /** Идти на позицию и держать её. true — стоим на месте */
    private boolean holdPost(List<GameMap.Pt> list, String name, int minTicks, int extraTicks) {
        if (post == null || !postList.equals(name) || age >= postUntil) {
            GameMap.Pt np = claimFrom(list, name);
            if (np == null) return false;
            post = np; postList = name; onPost = false;
            postUntil = age + minTicks + random.nextInt(Math.max(1, extraTicks));
        }
        Vec3d p = at(post);
        double dx = getX() - p.x, dz = getZ() - p.z;
        if (dx * dx + dz * dz > 1.0) {
            onPost = false;
            goTo(p, 1.0);
            return false;
        }
        getNavigation().stop();
        setSprinting(false);
        if (!onPost) { onPost = true; }
        lookFrom(post);
        return true;
    }

    /** Держать угол: смотреть в сторону точки позиции, плавно поводя прицелом ±20° */
    private void lookFrom(GameMap.Pt p) {
        if (!p.hasLook()) return;
        double ang = Math.atan2(p.lookZ() - getZ(), p.lookX() - getX()) + Math.sin(age * 0.045) * 0.35;
        double y = getEyeY() + Math.sin(age * 0.03) * 0.3;
        getLookControl().lookAt(getX() + Math.cos(ang) * 10, y, getZ() + Math.sin(ang) * 10, 8f, 10f);
    }

    private void leavePost() { post = null; onPost = false; Match.release(getUuid()); }

    // ================================================================= цели раунда
    private void objective(ServerWorld sw) {
        if (team() == Team.T) objectiveT(sw); else objectiveCT(sw);
    }

    private void objectiveT(ServerWorld sw) {
        GameMap m = map();
        char site = Match.targetSite();
        if (Match.bombPlanted()) {                                   // держим площадку после закладки
            BombEntity b = Match.bomb(sw);
            char ps = b == null ? site : m.siteAt(b.getX(), b.getZ());
            if (ps == 0) ps = site;
            if (!holdPost(m.post(ps), "post" + ps, 20 * 15, 20 * 15) && post == null && b != null) goTo(b.getPos(), 1.0);
            return;
        }
        // упавшая бомба: подобрать
        if (!hasBomb && age % 10 == 0) {
            for (net.minecraft.entity.ItemEntity ie : sw.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
                    getBoundingBox().expand(48, 12, 48), i -> i.getStack().getItem() instanceof BombItem)) {
                if (squaredDistanceTo(ie) < 2.5) { ie.discard(); hasBomb = true; leavePost(); return; }
                goTo(ie.getPos(), 1.0);
                return;
            }
        }
        // обход: стоим у входа на другую площадку
        if (role == 'L' && !hasBomb) {
            char other = site == 'A' ? 'B' : 'A';
            holdPost(m.entry(other), "lurk" + other, 20 * 60, 20);
            return;
        }
        // сбор у входа
        if (tStage == 0) {
            if (Match.tGo()) { tStage = 1; leavePost(); return; }
            if (holdPost(m.entry(site), "entry" + site, 20 * 60, 20)) Match.tReady(getUuid());
            return;
        }
        // заход: носитель — к центру, закладывать; остальные — занимают позиции на площадке
        if (hasBomb) {
            double[] c = m.siteCenter(site);
            double cy = m.siteY(site);
            if (m.siteAt(getX(), getZ()) == site && squaredDistanceTo(c[0], getY(), c[1]) < 16) {
                getNavigation().stop();
                getLookControl().lookAt(getX(), getY() - 1, getZ());
                plantTicks++;
                if (plantTicks % 16 == 0) sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.PLAYERS, 0.6f, 1.4f);
                if (plantTicks >= PLANT_TICKS) {
                    BombEntity.plantAt(sw, this);
                    hasBomb = false;
                    plantTicks = 0;
                }
            } else {
                plantTicks = 0;
                goTo(new Vec3d(c[0], cy, c[1]), 1.0);
            }
            return;
        }
        holdPost(m.hold(String.valueOf(site)), "take" + site, 20 * 12, 20 * 10);
    }

    private void objectiveCT(ServerWorld sw) {
        GameMap m = map();
        if (Match.bombPlanted()) {
            BombEntity b = Match.bomb(sw);
            if (b == null) return;
            if (post != null) leavePost();
            double d = Math.sqrt(squaredDistanceTo(b));
            if (d > 2.3) { goTo(b.getPos(), 1.0); return; }
            getNavigation().stop();
            getLookControl().lookAt(b.getX(), b.getY(), b.getZ());
            b.defuseStep(this, hasKit);
            return;
        }
        // тревога: противника видели у площадки — центр и часть защитников другой площадки перебегают
        char alert = Match.alert(Team.CT);
        if (alert != 0 && rotatedTo != alert && role != alert && (role == 'M' || random.nextInt(2) == 0)) {
            rotatedTo = alert;
            role = alert;
            leavePost();
        }
        holdPost(m.hold(String.valueOf(role)), "hold" + role, 20 * 25, 20 * 25);
    }

    // ================================================================= движение
    /** Бег: к цели раунда — бегом (как игрок с Ctrl), в бою — шагом */
    private static final double RUN = 1.35;

    private void goTo(Vec3d p, double speed) {
        boolean fighting = target != null && target.isAlive() && age - seenAge <= 3;
        double sp = fighting ? speed : speed * RUN;
        setSprinting(!fighting);
        if (goal == null || goal.squaredDistanceTo(p) > 4 || getNavigation().isIdle() && age - goalAge > 10) {
            getNavigation().startMovingTo(p.x, p.y, p.z, sp);
            goal = p;
            goalAge = age;
        }
    }

    private void checkStuck() {
        if (!getNavigation().isIdle() && getPos().squaredDistanceTo(lastPos) < 0.04) {
            if (++stuckTicks >= 2) {
                stuckTicks = 0;
                goal = null;
                getJumpControl().setActive();
                if (post != null && random.nextInt(3) == 0) { leavePost(); return; }     // позиция недостижима — взять другую
                Vec3d p = getPos().add((random.nextDouble() * 2 - 1) * 4, 0, (random.nextDouble() * 2 - 1) * 4);
                getNavigation().startMovingTo(p.x, p.y, p.z, 1.0);
            }
        } else stuckTicks = 0;
        lastPos = getPos();
    }

    // ================================================================= смерть
    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        if (getWorld() instanceof ServerWorld) {
            Match.release(getUuid());
            if (hasBomb) { hasBomb = false; dropStack(new ItemStack(ModItems.BOMB)); }
            Match.onBotKilled(this, source);
        }
    }
}
