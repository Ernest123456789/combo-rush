package com.derevna230.comborush.boss;

import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.item.ModItems;
import com.derevna230.comborush.pet.ModEntities;
import com.derevna230.comborush.score.ScoreEngine;
import com.derevna230.comborush.score.ScoreSource;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.*;

/**
 * Босс «Редстоуновый голем». Призывается Сердцем голема (покупается у Торговца Редстоуна).
 * Две фазы: до 50% здоровья — пробуждение, удары, сокрушение, вихрь, глаз-луч, редстоуновый обстрел;
 * после — «перегрузка»: быстрее, злее, плюс Нова ядра (прятаться за блоками!), после неё голем оглушён и уязвим.
 * Все атаки раздаёт сервер: клиент только играет анимацию по синхронизированному номеру атаки.
 */
public class RedstoneGolemEntity extends HostileEntity implements GeoEntity {
    public static final int NONE = 0, SPAWN = 1, PUNCH_R = 2, PUNCH_L = 3, SLAM = 4, SPIN = 5, BEAM = 6,
        BARRAGE = 7, NOVA = 8, STAGGER = 9, ROAR = 10, DEATH = 11;
    public static final String[] ANIM = {"", "spawn", "punch_r", "punch_l", "slam", "spin", "beam", "barrage", "nova", "stagger", "roar", "death"};
    /** длительность каждой анимации в тиках: совпадает с animation_length в файле анимаций */
    private static final int[] LEN = {0, 80, 28, 28, 52, 70, 80, 100, 120, 90, 72, 120};

    private static final TrackedData<Integer> ATTACK = DataTracker.registerData(RedstoneGolemEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> PHASE = DataTracker.registerData(RedstoneGolemEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.cr_golem.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.cr_golem.walk");
    private static final RawAnimation SPIN_TURN = RawAnimation.begin().thenPlay("animation.cr_golem.spin_turn");
    private static final RawAnimation[] PLAY = new RawAnimation[ANIM.length];
    static {
        for (int i = 1; i < ANIM.length; i++) PLAY[i] = RawAnimation.begin().thenPlay("animation.cr_golem." + ANIM[i]);
    }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossBar bar = new ServerBossBar(Text.translatable("entity.combo-rush.redstone_golem"), BossBar.Color.RED, BossBar.Style.NOTCHED_20);

    private int ticks = 0, atk = 0, cooldown = 70, lastKind = 0, novaCd = 360, stepTimer = 0, idleTicks = 0;
    private boolean awake = false, dying = false, punchLeft = false;
    private double beamYaw0 = 0, beamPitch = 0, beamDir = 1;
    private final Map<UUID, Float> damageBy = new HashMap<>();
    private final List<Strike> strikes = new ArrayList<>();
    private final List<Wave> waves = new ArrayList<>();

    private record Strike(double x, double y, double z, int at, int total, float dmg) {}
    private static final class Wave {
        final Vec3d c; final int start; final float dmg; final Set<UUID> hit = new HashSet<>();
        Wave(Vec3d c, int start, float dmg) { this.c = c; this.start = start; this.dmg = dmg; }
    }

    public RedstoneGolemEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 0;
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 800)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.23)
            .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
            .add(EntityAttributes.GENERIC_ARMOR, 10)
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48)
            .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 20)
            .add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.6);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(ATTACK, NONE);
        builder.add(PHASE, 1);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 40f));
        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, false));
    }

    public int attackId() { return this.dataTracker.get(ATTACK); }
    public int phase() { return this.dataTracker.get(PHASE); }
    private boolean enraged() { return phase() >= 2; }

    // ------------------------------------------------------------------ призыв
    /** Вызывает Сердце голема и команда /cr golem. Здоровье растёт с числом игроков рядом и зависит от сложности */
    public static RedstoneGolemEntity summon(ServerWorld w, Vec3d pos, float yaw) {
        RedstoneGolemEntity g = ModEntities.GOLEM.create(w);
        if (g == null) return null;
        g.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0);
        g.setHeadYaw(yaw); g.setBodyYaw(yaw);
        int n = 0;
        for (ServerPlayerEntity p : w.getPlayers()) if (!p.isSpectator() && p.squaredDistanceTo(pos) < 48 * 48) n++;
        double hp = 800 + 400 * Math.max(0, n - 1);
        if (w.getDifficulty() == Difficulty.EASY) hp *= 0.7;
        else if (w.getDifficulty() == Difficulty.HARD) hp *= 1.25;
        EntityAttributeInstance a = g.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (a != null) a.setBaseValue(hp);
        g.setHealth((float) hp);
        w.spawnEntity(g);
        return g;
    }

    public static boolean bossNear(ServerWorld w, Vec3d pos, double r) {
        return !w.getEntitiesByClass(RedstoneGolemEntity.class, Box.of(pos, r * 2, r * 2, r * 2), e -> true).isEmpty();
    }

    // ------------------------------------------------------------------ боссбар
    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player) {
        super.onStartedTrackingBy(player);
        bar.addPlayer(player);
    }

    @Override
    public void onStoppedTrackingBy(ServerPlayerEntity player) {
        super.onStoppedTrackingBy(player);
        bar.removePlayer(player);
    }

    private void barName() {
        if (dying) bar.setName(Text.literal("§7Редстоуновый голем рассыпается…"));
        else if (attack() == STAGGER) bar.setName(Text.literal("§e§lЯдро открыто! §cБейте!"));
        else if (enraged()) bar.setName(Text.literal("§4§lРедстоуновый голем §c⚡ ПЕРЕГРУЗКА"));
        else bar.setName(Text.literal("§c§lРедстоуновый голем"));
        bar.setColor(attack() == STAGGER ? BossBar.Color.YELLOW : enraged() ? BossBar.Color.PURPLE : BossBar.Color.RED);
    }

    // ------------------------------------------------------------------ урон и смерть
    private int attack() { return attackId(); }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isOf(DamageTypes.GENERIC_KILL)) return super.damage(source, amount);
        int a = attack();
        if (dying || a == SPAWN || a == ROAR) return false;
        float cap = Math.max(30f, getMaxHealth() * 0.05f);          // одним ударом босса не снести
        amount = Math.min(amount, cap);
        if (a == STAGGER) amount *= 1.75f;                           // оглушён — ядро открыто
        boolean r = super.damage(source, amount);
        if (r && source.getAttacker() instanceof ServerPlayerEntity sp) damageBy.merge(sp.getUuid(), amount, Float::sum);
        return r;
    }

    /** Здоровье не падает до нуля обычным путём: вместо смерти играет анимация распада */
    @Override
    public void setHealth(float health) {
        if (!dying && health <= 0f && getWorld() instanceof ServerWorld && !isRemoved()) {
            super.setHealth(1f);
            beginDeath();
            return;
        }
        super.setHealth(dying ? Math.max(1f, health) : health);
    }

    private void beginDeath() {
        dying = true;
        strikes.clear(); waves.clear();
        setTarget(null);
        getNavigation().stop();
        startAttack(DEATH);
    }

    @Override public boolean isPushable() { return false; }
    @Override protected void pushAway(Entity entity) { }
    @Override public boolean isFireImmune() { return true; }
    @Override public boolean cannotDespawn() { return true; }
    @Override public boolean canImmediatelyDespawn(double distanceSquared) { return false; }

    private SoundEvent snd(String id) { return Fx.soundEvent(id); }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return snd("entity.iron_golem.hurt"); }
    @Override protected SoundEvent getDeathSound() { return null; }
    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { }

    // ------------------------------------------------------------------ тик
    @Override
    public void tick() {
        super.tick();
        if (getWorld() instanceof ServerWorld w) serverTick(w);
    }

    private void serverTick(ServerWorld w) {
        ticks++;
        if (w.getDifficulty() == Difficulty.PEACEFUL) { bar.clearPlayers(); discard(); return; }
        bar.setPercent(MathHelper.clamp(getHealth() / getMaxHealth(), 0f, 1f));
        if (ticks % 10 == 1) {
            barName();
            bar.setDarkenSky(enraged() && !dying);
            for (ServerPlayerEntity p : w.getPlayers())
                if (!p.isSpectator() && p.squaredDistanceTo(this) < 64 * 64) bar.addPlayer(p); else bar.removePlayer(p);
        }
        if (!awake && !dying) { awake = true; startAttack(SPAWN); }
        tickStrikes(w);
        tickWaves(w);
        ambient(w);
        if (attack() != NONE) { tickAttack(w); return; }
        if (!dying) tickAi(w);
    }

    private void startAttack(int id) {
        atk = 0;
        this.dataTracker.set(ATTACK, id);
        getNavigation().stop();
    }

    private void endAttack() {
        int a = attack();
        if (a == DEATH) return;
        lastKind = a;
        if (a == NOVA) { novaCd = 700 + random.nextInt(300); startAttack(STAGGER); return; }
        if (a == ROAR) { cooldown = 20; novaCd = 240; }
        else if (a == STAGGER) cooldown = 25;
        else if (a == SPAWN) cooldown = 20;
        else cooldown = enraged() ? 12 + random.nextInt(16) : 28 + random.nextInt(24);
        startAttack(NONE);
    }

    // ------------------------------------------------------------------ ИИ между атаками
    private void tickAi(ServerWorld w) {
        if (!roarDone() && getHealth() < getMaxHealth() * 0.5f) { startAttack(ROAR); return; }
        if (enraged() && novaCd > 0) novaCd--;
        LivingEntity t = getTarget();
        if (t == null || !t.isAlive()) {
            if (++idleTicks > 600 && getHealth() < getMaxHealth() && ticks % 20 == 0) heal(getMaxHealth() * 0.02f);
            return;
        }
        idleTicks = 0;
        double d = horiz(t);
        face(t.getPos(), 35f);
        if (cooldown > 0) cooldown--;
        if (d > 3.4) getMoveControl().moveTo(t.getX(), t.getY(), t.getZ(), enraged() ? 1.4 : 1.0);
        if (cooldown <= 0 && d < 38) pickAttack(w, t, d);
    }

    private boolean roarDone() { return enraged(); }

    private void pickAttack(ServerWorld w, LivingEntity t, double d) {
        int near = 0;
        for (ServerPlayerEntity p : w.getPlayers()) if (!p.isSpectator() && p.isAlive() && p.squaredDistanceTo(this) < 8 * 8) near++;
        List<int[]> pool = new ArrayList<>();
        if (enraged() && novaCd <= 0) pool.add(new int[]{NOVA, 12});
        if (d < 6.8) {
            pool.add(new int[]{punchLeft ? PUNCH_L : PUNCH_R, 5});
            pool.add(new int[]{SLAM, 4});
        }
        if (d < 7.5 || near >= 2) pool.add(new int[]{SPIN, enraged() ? 4 : 3});
        if (d >= 5.5) { pool.add(new int[]{BEAM, 4}); pool.add(new int[]{BARRAGE, 4}); }
        else pool.add(new int[]{BARRAGE, 1});
        List<int[]> pick0 = new ArrayList<>();
        for (int[] p : pool) {
            boolean same = p[0] == lastKind || (lastKind == PUNCH_R && p[0] == PUNCH_L) || (lastKind == PUNCH_L && p[0] == PUNCH_R);
            if (!same || p[0] == NOVA) pick0.add(p);
        }
        if (!pick0.isEmpty()) pool = pick0;
        int total = 0;
        for (int[] p : pool) total += p[1];
        int roll = random.nextInt(Math.max(1, total)), pick = pool.get(0)[0];
        for (int[] p : pool) { roll -= p[1]; if (roll < 0) { pick = p[0]; break; } }
        if (pick == PUNCH_R || pick == PUNCH_L) punchLeft = !punchLeft;
        startAttack(pick);
    }

    // ------------------------------------------------------------------ атаки
    private void tickAttack(ServerWorld w) {
        int a = attack();
        int t = atk;
        LivingEntity target = getTarget();
        switch (a) {
            case SPAWN -> doSpawn(w, t);
            case PUNCH_R, PUNCH_L -> doPunch(w, t, target);
            case SLAM -> doSlam(w, t, target);
            case SPIN -> doSpin(w, t, target);
            case BEAM -> doBeam(w, t, target);
            case BARRAGE -> doBarrage(w, t, target);
            case NOVA -> doNova(w, t);
            case STAGGER -> doStagger(w, t);
            case ROAR -> doRoar(w, t);
            case DEATH -> doDeath(w, t);
            default -> { }
        }
        atk++;
        if (a != DEATH && atk >= LEN[a]) endAttack();
    }

    private void doSpawn(ServerWorld w, int t) {
        double x = getX(), y = getY(), z = getZ();
        if (t == 0) {
            Fx.sound(w, x, y, z, "entity.warden.emerge", 4f, 0.6f);
            for (ServerPlayerEntity p : w.getPlayers())
                if (p.squaredDistanceTo(this) < 64 * 64) Fx.title(p, "§4§lРЕДСТОУНОВЫЙ ГОЛЕМ", "§cПробуждение…");
        }
        if (t < 56 && t % 2 == 0) {
            BlockState below = w.getBlockState(getBlockPos().down());
            w.spawnParticles(Fx.debris(below), x, y + 0.3, z, 16, 1.7, 0.2, 1.7, 0.12);
            w.spawnParticles(Fx.RED_BIG, x, y + 0.2, z, 6, 1.5, 0.1, 1.5, 0);
            if (t % 6 == 0) Fx.sound(w, x, y, z, "block.stone.break", 2.5f, 0.5f);
            if (t % 10 == 0) Fx.ringOut(w, ParticleTypes.CLOUD, x, y + 0.2, z, 1.5 + t * 0.05, 18, 0.25, 0.02);
        }
        if (t == 30 || t == 44) for (int i = 0; i < 3; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = 7 + random.nextDouble() * 4;
            Fx.bolt(w, x + Math.cos(a) * r, y, z + Math.sin(a) * r);
        }
        if (t == 62) {
            Fx.sound(w, x, y, z, "entity.warden.roar", 5f, 0.55f);
            Fx.sound(w, x, y, z, "entity.generic.explode", 3f, 0.6f);
            w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, x, y + 0.5, z, 3, 1.5, 0.2, 1.5, 0);
            w.spawnParticles(ParticleTypes.FLASH, x, y + 3, z, 1, 0, 0, 0, 0);
            Fx.ringOut(w, ParticleTypes.CLOUD, x, y + 0.3, z, 2, 48, 0.7, 0.03);
            Fx.ringOut(w, ParticleTypes.ELECTRIC_SPARK, x, y + 1, z, 2, 36, 0.9, 0.1);
            waves.add(new Wave(getPos(), ticks, 0f));
            for (ServerPlayerEntity p : w.getPlayers())
                if (!p.isSpectator() && p.squaredDistanceTo(this) < 11 * 11) launch(p, p.getX() - x, p.getZ() - z, 1.25, 0.6);
        }
        if (t >= 62 && t <= 74 && t % 3 == 0) w.spawnParticles(Fx.RED_BIG, x, y + 4, z, 10, 1.4, 1.2, 1.4, 0);
    }

    private void doPunch(ServerWorld w, int t, LivingEntity target) {
        if (t <= 8 && target != null) face(target.getPos(), 30f);
        Vec3d f = forward();
        if (t == 4) Fx.sound(w, getX(), getY(), getZ(), "entity.iron_golem.attack", 2f, 0.55f);
        if (t == 11) {
            Vec3d fist = getPos().add(f.multiply(4.4)).add(0, 1.4, 0);
            w.spawnParticles(ParticleTypes.EXPLOSION, fist.x, fist.y, fist.z, 2, 0.4, 0.4, 0.4, 0);
            w.spawnParticles(ParticleTypes.CRIT, fist.x, fist.y, fist.z, 24, 0.7, 0.5, 0.7, 0.5);
            w.spawnParticles(Fx.debris(w.getBlockState(BlockPos.ofFloored(fist.x, getY() - 1, fist.z))), fist.x, getY() + 0.3, fist.z, 22, 0.8, 0.1, 0.8, 0.15);
            Fx.ringOut(w, ParticleTypes.CLOUD, fist.x, getY() + 0.3, fist.z, 0.8, 14, 0.35, 0.02);
            Fx.sound(w, fist.x, fist.y, fist.z, "entity.generic.explode", 1.4f, 1.15f);
            Fx.sound(w, fist.x, fist.y, fist.z, "entity.player.attack.strong", 2f, 0.5f);
            for (ServerPlayerEntity p : w.getPlayers()) {
                if (p.isSpectator() || !p.isAlive()) continue;
                double dx = p.getX() - getX(), dz = p.getZ() - getZ(), d = Math.sqrt(dx * dx + dz * dz);
                if (d > 6.4 || Math.abs(p.getY() - getY()) > 3.2) continue;
                if (d > 1.0 && (dx * f.x + dz * f.z) / d < 0.5) continue;
                hurt(w, p, 16f, false);
                launch(p, f.x, f.z, 1.55, 0.45);
            }
        }
    }

    private void doSlam(ServerWorld w, int t, LivingEntity target) {
        double x = getX(), y = getY(), z = getZ();
        if (t <= 20 && target != null) face(target.getPos(), 14f);
        if (t == 6) Fx.sound(w, x, y, z, "entity.warden.sonic_charge", 3f, 1.3f);
        if (t >= 10 && t < 30 && t % 3 == 0) {
            Vec3d f = forward();
            w.spawnParticles(Fx.HOT, x + f.x * 1.2, y + 6.0, z + f.z * 1.2, 6, 1.4, 0.5, 1.4, 0);
            w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x + f.x * 1.2, y + 5.6, z + f.z * 1.2, 5, 1.0, 0.4, 1.0, 0.3);
        }
        if (t == 30) {
            Vec3d f = forward();
            double ix = x + f.x * 3.2, iz = z + f.z * 3.2;
            Fx.sound(w, ix, y, iz, "block.anvil.land", 3f, 0.4f);
            Fx.sound(w, ix, y, iz, "entity.generic.explode", 3f, 0.7f);
            Fx.sound(w, ix, y, iz, "entity.iron_golem.attack", 3f, 0.4f);
            w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, ix, y + 0.3, iz, 1, 0, 0, 0, 0);
            w.spawnParticles(Fx.debris(w.getBlockState(BlockPos.ofFloored(ix, y - 1, iz))), ix, y + 0.4, iz, 60, 1.6, 0.2, 1.6, 0.35);
            w.spawnParticles(Fx.redstoneDebris(), ix, y + 0.4, iz, 30, 1.2, 0.2, 1.2, 0.3);
            Fx.ringOut(w, ParticleTypes.CLOUD, ix, y + 0.2, iz, 1.5, 40, 0.8, 0.02);
            waves.add(new Wave(new Vec3d(ix, y, iz), ticks, 18f));
            if (enraged()) waves.add(new Wave(new Vec3d(ix, y, iz), ticks + 9, 14f));
        }
    }

    private void doSpin(ServerWorld w, int t, LivingEntity target) {
        double x = getX(), y = getY(), z = getZ();
        if (t < 12 && target != null) face(target.getPos(), 30f);
        if (t == 8) Fx.sound(w, x, y, z, "entity.ravager.roar", 3f, 0.6f);
        if (t >= 14 && t <= 54) {
            if (target != null && t % 2 == 0) getMoveControl().moveTo(target.getX(), target.getY(), target.getZ(), 0.55);
            Fx.ringOut(w, ParticleTypes.CRIT, x, y + 2.0, z, 4.3, 8, 0.2, 0.02);
            if (t % 2 == 0) Fx.ringStatic(w, Fx.RED, x, y + 2.8, z, 4.6, 12);
            if (t % 4 == 2) {
                Fx.sound(w, x, y, z, "entity.player.attack.sweep", 2.2f, 0.5f);
                Fx.ringOut(w, ParticleTypes.SWEEP_ATTACK, x, y + 1.6, z, 3.8, 6, 0, 0);
                for (ServerPlayerEntity p : w.getPlayers()) {
                    if (p.isSpectator() || !p.isAlive()) continue;
                    double dx = p.getX() - x, dz = p.getZ() - z;
                    if (dx * dx + dz * dz > 5.9 * 5.9 || Math.abs(p.getY() - y) > 3.6) continue;
                    hurt(w, p, 9f, false);
                    launch(p, dx, dz, 1.15, 0.38);
                }
            }
        }
        if (t == 56) Fx.ringOut(w, ParticleTypes.CLOUD, x, y + 0.3, z, 2.0, 24, 0.5, 0.02);
    }

    private void doBeam(ServerWorld w, int t, LivingEntity target) {
        double x = getX(), y = getY(), z = getZ();
        Vec3d eye = new Vec3d(x, y + 4.0, z).add(forward().multiply(1.5));
        if (t == 0) Fx.sound(w, x, y, z, "entity.warden.sonic_charge", 4f, 0.7f);
        if (t <= 20 && target != null) face(target.getPos(), 22f);
        if (t >= 2 && t < 24) {
            for (int i = 0; i < 6; i++) {
                double a = random.nextDouble() * Math.PI * 2, b = (random.nextDouble() - 0.5) * Math.PI, r = 3.8;
                double px = eye.x + Math.cos(a) * Math.cos(b) * r, py = eye.y + Math.sin(b) * r, pz = eye.z + Math.sin(a) * Math.cos(b) * r;
                w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, px, py, pz, 0, (eye.x - px) * 0.12, (eye.y - py) * 0.12, (eye.z - pz) * 0.12, 1.0);
            }
            w.spawnParticles(Fx.WHITE_HOT, eye.x, eye.y, eye.z, 3, 0.25, 0.25, 0.25, 0);
        }
        if (t == 20) {
            beamYaw0 = getYaw();
            beamDir = random.nextBoolean() ? 1 : -1;
            if (target != null) {
                double dy = target.getEyeY() - eye.y, dh = Math.max(1, horiz(target));
                beamPitch = MathHelper.clamp(-Math.toDegrees(Math.atan2(dy, dh)), -8, 38);
            } else beamPitch = 12;
        }
        if (t >= 24 && t <= 64) {
            float yaw = (float) (beamYaw0 + beamDir * (-48 + 96.0 * (t - 24) / 40.0));
            setYaw(yaw); setBodyYaw(yaw); setHeadYaw(yaw);
            Vec3d dir = Vec3d.fromPolar((float) beamPitch, yaw);
            Vec3d from = eye, to = eye.add(dir.multiply(38));
            Vec3d end = to;
            var hit = w.raycast(new net.minecraft.world.RaycastContext(from, to, net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE, this));
            if (hit.getType() != net.minecraft.util.hit.HitResult.Type.MISS) end = hit.getPos();
            double len = end.subtract(from).length();
            if (t == 24) {
                Fx.sound(w, x, y, z, "entity.warden.sonic_boom", 5f, 0.5f);
                Fx.sound(w, x, y, z, "entity.lightning_bolt.thunder", 3f, 1.5f);
                w.spawnParticles(ParticleTypes.FLASH, eye.x, eye.y, eye.z, 1, 0, 0, 0, 0);
            }
            if (t % 8 == 0) Fx.sound(w, x, y, z, "entity.guardian.attack", 3f, 0.6f);
            int step = (t % 2 == 0) ? 0 : 1;
            for (double d = 1.2 + step * 0.5; d < len; d += 1.0) {
                Vec3d p = from.add(dir.multiply(d));
                w.spawnParticles(Fx.RED_BIG, p.x, p.y, p.z, 1, 0.12, 0.12, 0.12, 0);
                if (((int) d + t) % 3 == 0) w.spawnParticles(Fx.WHITE_HOT, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
                if (((int) d + t) % 5 == 0) w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 2, 0.3, 0.3, 0.3, 0.2);
            }
            w.spawnParticles(ParticleTypes.EXPLOSION, end.x, end.y, end.z, 1, 0.5, 0.5, 0.5, 0);
            w.spawnParticles(ParticleTypes.FLAME, end.x, end.y, end.z, 10, 0.5, 0.4, 0.5, 0.12);
            w.spawnParticles(ParticleTypes.LARGE_SMOKE, end.x, end.y, end.z, 4, 0.4, 0.3, 0.4, 0.04);
            if (t % 4 == 0) {
                for (ServerPlayerEntity p : w.getPlayers()) {
                    if (p.isSpectator() || !p.isAlive()) continue;
                    if (p.getBoundingBox().expand(0.45).raycast(from, end).isPresent()) {
                        hurt(w, p, 8f, true);
                        p.setOnFireFor(4);
                    }
                }
            }
        }
    }

    private void doBarrage(ServerWorld w, int t, LivingEntity target) {
        double x = getX(), y = getY(), z = getZ();
        if (t < 8 && target != null) face(target.getPos(), 20f);
        for (int hit : new int[]{14, 32, 50, 68}) {
            if (t != hit) continue;
            Fx.sound(w, x, y, z, "block.anvil.land", 3f, 0.45f);
            Fx.sound(w, x, y, z, "entity.iron_golem.attack", 3f, 0.5f);
            Vec3d f = forward();
            double fx = x + f.x * 3.0, fz = z + f.z * 3.0;
            w.spawnParticles(Fx.debris(w.getBlockState(BlockPos.ofFloored(fx, y - 1, fz))), fx, y + 0.4, fz, 40, 1.4, 0.2, 1.4, 0.3);
            Fx.ringOut(w, ParticleTypes.CLOUD, fx, y + 0.2, fz, 1.2, 24, 0.55, 0.02);
            Fx.sound(w, x, y, z, "entity.generic.explode", 2f, 0.9f);
            int delay = enraged() ? 22 : 30;
            int maxT = enraged() ? 4 : 2;
            List<ServerPlayerEntity> ps = new ArrayList<>();
            for (ServerPlayerEntity p : w.getPlayers())
                if (!p.isSpectator() && p.isAlive() && p.squaredDistanceTo(this) < 40 * 40) ps.add(p);
            ps.sort(Comparator.comparingDouble(p -> p.squaredDistanceTo(this)));
            for (int i = 0; i < Math.min(maxT, ps.size()); i++) addStrike(w, ps.get(i).getX(), ps.get(i).getY(), ps.get(i).getZ(), delay);
            if (enraged()) for (int i = 0; i < 2; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = 5 + random.nextDouble() * 8;
                addStrike(w, x + Math.cos(a) * r, y, z + Math.sin(a) * r, delay + 4);
            }
        }
        if (t >= 6 && t < 68 && t % 4 == 0) w.spawnParticles(Fx.HOT, x, y + 6.2, z, 5, 1.6, 0.4, 1.6, 0);
    }

    private void addStrike(ServerWorld w, double sx, double sy, double sz, int delay) {
        BlockPos bp = BlockPos.ofFloored(sx, sy, sz);
        int dropped = 0;
        while (dropped < 8 && w.getBlockState(bp.down()).isAir()) { bp = bp.down(); dropped++; }
        strikes.add(new Strike(sx, bp.getY(), sz, ticks + delay, delay, 16f));
    }

    private void doNova(ServerWorld w, int t) {
        double x = getX(), y = getY(), z = getZ();
        Vec3d core = new Vec3d(x, y + 2.7, z).add(forward().multiply(-0.9));
        if (t == 0) {
            Fx.sound(w, x, y, z, "block.beacon.activate", 4f, 0.5f);
            for (ServerPlayerEntity p : w.getPlayers())
                if (p.squaredDistanceTo(this) < 64 * 64) {
                    Fx.title(p, "§4§l⚠ ПЕРЕГРУЗКА ЯДРА", "§eУкройтесь за блоками или бегите за красную черту!");
                    p.sendMessage(Text.literal("§c[Редстоуновый голем] §fЯдро накаляется! Спрячьтесь за стеной (§eбез прямой видимости§f урона нет) или отбегите дальше §c16 блоков§f."), false);
                }
        }
        if (t >= 14 && t < 108) {
            double prog = (t - 14) / 94.0;
            for (int i = 0; i < 8; i++) {
                double a = random.nextDouble() * Math.PI * 2, b = (random.nextDouble() - 0.3) * Math.PI * 0.7, r = 9 - 6 * prog + random.nextDouble() * 2;
                double px = core.x + Math.cos(a) * Math.cos(b) * r, py = core.y + Math.sin(b) * r, pz = core.z + Math.sin(a) * Math.cos(b) * r;
                w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, px, py, pz, 0, (core.x - px) * 0.1, (core.y - py) * 0.1, (core.z - pz) * 0.1, 1.0);
            }
            w.spawnParticles(Fx.RED_BIG, core.x, core.y, core.z, 3 + (int) (prog * 10), 0.3 + prog * 1.2, 0.3 + prog * 1.2, 0.3 + prog * 1.2, 0);
            if (t % 4 == 0) Fx.ringStatic(w, Fx.RED, x, y + 0.15, z, 16.0, 70);
            if (t % 10 == 0) Fx.sound(w, x, y, z, "entity.warden.heartbeat", 3f, 0.7f + (float) prog * 0.9f);
        }
        if (t == 108) {
            Fx.sound(w, x, y, z, "entity.generic.explode", 6f, 0.5f);
            Fx.sound(w, x, y, z, "entity.lightning_bolt.thunder", 6f, 0.6f);
            Fx.sound(w, x, y, z, "entity.warden.sonic_boom", 6f, 0.4f);
            w.spawnParticles(ParticleTypes.FLASH, core.x, core.y, core.z, 3, 0.3, 0.3, 0.3, 0);
            for (int i = 0; i < 8; i++)
                w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, x + (random.nextDouble() - 0.5) * 8, y + 1 + random.nextDouble() * 4, z + (random.nextDouble() - 0.5) * 8, 1, 0, 0, 0, 0);
            Fx.ringOut(w, ParticleTypes.CLOUD, x, y + 0.4, z, 2, 64, 1.6, 0.04);
            Fx.ringOut(w, ParticleTypes.FLAME, x, y + 1.2, z, 2, 48, 1.3, 0.05);
            Fx.ringOut(w, ParticleTypes.ELECTRIC_SPARK, x, y + 2.5, z, 2, 48, 1.8, 0.1);
            for (int i = 0; i < 8; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = 5 + random.nextDouble() * 9;
                Fx.bolt(w, x + Math.cos(a) * r, y, z + Math.sin(a) * r);
            }
            waves.add(new Wave(getPos(), ticks, 0f));
            waves.add(new Wave(getPos(), ticks + 5, 0f));
            for (ServerPlayerEntity p : w.getPlayers()) {
                if (p.isSpectator() || !p.isAlive()) continue;
                double d = p.distanceTo(this);
                if (d > 18) continue;
                Vec3d pe = p.getEyePos();
                if (!Fx.clear(w, core, pe, this) && !Fx.clear(w, core, p.getPos().add(0, 0.3, 0), this)) {
                    p.sendMessage(Text.literal("§aУкрытие спасло вас от взрыва ядра!"), true);
                    continue;
                }
                hurt(w, p, (float) (38.0 * Math.max(0.35, 1.0 - d / 24.0)), true);
                launch(p, p.getX() - x, p.getZ() - z, 2.0, 0.85);
            }
        }
    }

    private void doStagger(ServerWorld w, int t) {
        double x = getX(), y = getY(), z = getZ();
        if (t == 0) {
            Fx.sound(w, x, y, z, "entity.iron_golem.death", 4f, 0.5f);
            for (ServerPlayerEntity p : w.getPlayers())
                if (p.squaredDistanceTo(this) < 64 * 64) p.sendMessage(Text.literal("§e§lГолем оглушён! §fЯдро открыто — урон по нему ×1.75."), true);
        }
        Vec3d core = new Vec3d(x, y + 1.6, z).add(forward().multiply(-1.0));
        if (t % 3 == 0) {
            w.spawnParticles(ParticleTypes.LARGE_SMOKE, core.x, core.y + 0.5, core.z, 4, 0.5, 0.4, 0.5, 0.03);
            w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, core.x, core.y, core.z, 6, 0.7, 0.5, 0.7, 0.25);
        }
        if (t % 8 == 0) Fx.sound(w, x, y, z, "block.redstone_torch.burnout", 2f, 0.6f);
        if (t % 12 == 0) w.spawnParticles(Fx.RED, core.x, core.y, core.z, 8, 0.6, 0.5, 0.6, 0);
    }

    private void doRoar(ServerWorld w, int t) {
        double x = getX(), y = getY(), z = getZ();
        if (t == 0) {
            Fx.sound(w, x, y, z, "entity.ender_dragon.growl", 4f, 0.5f);
            for (ServerPlayerEntity p : w.getPlayers())
                if (p.squaredDistanceTo(this) < 64 * 64) Fx.title(p, "§4§lПЕРЕГРУЗКА", "§cЯдро голема раскалилось");
        }
        if (t == 18) {
            this.dataTracker.set(PHASE, 2);
            barName();
            Fx.sound(w, x, y, z, "entity.warden.roar", 6f, 0.5f);
            Fx.sound(w, x, y, z, "entity.generic.explode", 4f, 0.55f);
            w.spawnParticles(ParticleTypes.FLASH, x, y + 3, z, 2, 0.3, 0.3, 0.3, 0);
            for (int i = 0; i < 4; i++)
                w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, x + (random.nextDouble() - 0.5) * 5, y + 0.5, z + (random.nextDouble() - 0.5) * 5, 1, 0, 0, 0, 0);
            Fx.ringOut(w, ParticleTypes.CLOUD, x, y + 0.3, z, 2, 56, 1.1, 0.03);
            Fx.ringOut(w, ParticleTypes.FLAME, x, y + 0.6, z, 2, 40, 0.9, 0.04);
            for (int i = 0; i < 6; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = 4 + random.nextDouble() * 8;
                Fx.bolt(w, x + Math.cos(a) * r, y, z + Math.sin(a) * r);
            }
            waves.add(new Wave(getPos(), ticks, 0f));
            for (ServerPlayerEntity p : w.getPlayers())
                if (!p.isSpectator() && p.squaredDistanceTo(this) < 14 * 14) launch(p, p.getX() - x, p.getZ() - z, 1.4, 0.7);
        }
        if (t > 18 && t < 58) {
            if (t % 4 == 0) w.spawnParticles(Fx.RED_BIG, x, y + 4, z, 8, 1.6, 1.8, 1.6, 0);
            if (t % 12 == 0) {
                double a = random.nextDouble() * Math.PI * 2, r = 6 + random.nextDouble() * 6;
                Fx.bolt(w, x + Math.cos(a) * r, y, z + Math.sin(a) * r);
            }
        }
    }

    private void doDeath(ServerWorld w, int t) {
        double x = getX(), y = getY(), z = getZ();
        if (t == 0) {
            Fx.sound(w, x, y, z, "entity.iron_golem.death", 5f, 0.45f);
            Fx.sound(w, x, y, z, "entity.warden.death", 4f, 0.6f);
            bar.setPercent(0f);
        }
        if (t >= 4 && t < 66 && t % 2 == 0) {
            w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 2.4, z, 10, 1.4, 1.8, 1.4, 0.3);
            w.spawnParticles(Fx.RED, x, y + 2.4, z, 6, 1.5, 1.8, 1.5, 0);
            w.spawnParticles(ParticleTypes.LARGE_SMOKE, x, y + 3.5, z, 2, 1.2, 1.0, 1.2, 0.03);
        }
        if (t >= 8 && t < 70 && t % 7 == 0) {
            double px = x + (random.nextDouble() - 0.5) * 3, py = y + 1 + random.nextDouble() * 3, pz = z + (random.nextDouble() - 0.5) * 3;
            w.spawnParticles(ParticleTypes.EXPLOSION, px, py, pz, 1, 0, 0, 0, 0);
            Fx.sound(w, px, py, pz, "entity.generic.explode", 1.6f, 1.2f + random.nextFloat() * 0.4f);
        }
        if (t == 30 || t == 52) {
            double a = random.nextDouble() * Math.PI * 2;
            Fx.bolt(w, x + Math.cos(a) * 8, y, z + Math.sin(a) * 8);
        }
        if (t == 66) {
            Fx.sound(w, x, y, z, "entity.iron_golem.death", 5f, 0.35f);
            Fx.ringOut(w, ParticleTypes.CLOUD, x, y + 0.3, z, 1.5, 36, 0.7, 0.03);
            w.spawnParticles(Fx.redstoneDebris(), x, y + 2, z, 80, 1.8, 1.8, 1.8, 0.4);
        }
        if (t >= 66 && t < 116 && t % 3 == 0) {
            BlockState below = w.getBlockState(getBlockPos().down());
            w.spawnParticles(Fx.debris(below), x, y + 4.0, z, 22, 1.6, 0.8, 1.6, 0.05);
            w.spawnParticles(ParticleTypes.CLOUD, x, y + 0.5, z, 6, 1.5, 0.2, 1.5, 0.05);
        }
        if (t == 100) {
            Fx.sound(w, x, y, z, "entity.firework_rocket.blast", 4f, 0.7f);
            w.spawnParticles(ParticleTypes.FLASH, x, y + 2, z, 1, 0, 0, 0, 0);
            w.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y + 2, z, 60, 1.2, 1.2, 1.2, 0.5);
        }
        if (t >= 118) finishDeath(w);
    }

    private void finishDeath(ServerWorld w) {
        double x = getX(), y = getY(), z = getZ();
        Fx.sound(w, x, y, z, "entity.generic.explode", 6f, 0.5f);
        Fx.sound(w, x, y, z, "entity.lightning_bolt.thunder", 6f, 0.7f);
        Fx.sound(w, x, y, z, "ui.toast.challenge_complete", 3f, 1f);
        for (int i = 0; i < 6; i++)
            w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, x + (random.nextDouble() - 0.5) * 6, y + 1 + random.nextDouble() * 3, z + (random.nextDouble() - 0.5) * 6, 1, 0, 0, 0, 0);
        Fx.ringOut(w, ParticleTypes.CLOUD, x, y + 0.4, z, 2, 64, 1.5, 0.04);
        Fx.ringOut(w, ParticleTypes.FLAME, x, y + 1.0, z, 2, 48, 1.2, 0.08);
        w.spawnParticles(ParticleTypes.FIREWORK, x, y + 3, z, 90, 2.5, 2.5, 2.5, 0.25);
        w.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y + 3, z, 120, 2.5, 2.5, 2.5, 0.6);
        for (int i = 0; i < 4; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = 4 + random.nextDouble() * 4;
            Fx.bolt(w, x + Math.cos(a) * r, y, z + Math.sin(a) * r);
        }
        reward(w);
        bar.clearPlayers();
        ExperienceOrbEntity.spawn(w, getPos().add(0, 1.5, 0), 1500);
        discard();
    }

    private void reward(ServerWorld w) {
        Set<ServerPlayerEntity> win = new LinkedHashSet<>();
        ServerPlayerEntity top = null; float topDmg = 0;
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (p.isSpectator()) continue;
            float d = damageBy.getOrDefault(p.getUuid(), 0f);
            if (d > 0 || p.squaredDistanceTo(this) < 40 * 40) win.add(p);
            if (d > topDmg) { topDmg = d; top = p; }
        }
        Item epic = ModItems.CASES.get("epic"), legendary = ModItems.CASES.get("legendary");
        Item figurine = Registries.ITEM.get(Identifier.of("combo-rush", "decor_redstone_golem"));
        for (ServerPlayerEntity p : win) {
            PlayerProfile pr = PlayerProfile.of(p);
            p.setAttached(PlayerProfile.TYPE, pr.addCoins(600).addEssence(120));
            if (epic != null) p.getInventory().offerOrDrop(new ItemStack(epic));
            if (legendary != null && random.nextFloat() < 0.35f) p.getInventory().offerOrDrop(new ItemStack(legendary));
            p.getInventory().offerOrDrop(new ItemStack(Items.DIAMOND, 6 + random.nextInt(7)));
            p.getInventory().offerOrDrop(new ItemStack(Items.REDSTONE_BLOCK, 8));
            if (p == top && figurine != null && figurine != Items.AIR) p.getInventory().offerOrDrop(new ItemStack(figurine));
            ScoreEngine.award(p, ScoreSource.KILL, "combo-rush:redstone_golem", 10.0, "Босс");
            Fx.title(p, "§6§lГОЛЕМ ПОВЕРЖЕН", "§fРедстоуновое ядро ваше");
            p.sendMessage(Text.literal("§6[Редстоуновый голем] §fПобеда! §7Награда: §e+600 монет§7, §d+120 эссенции§7, эпический кейс, алмазы и редстоун"
                + (p == top ? "§7, §6фигурка голема§7 (больше всех урона)" : "") + "§7."), false);
        }
    }

    // ------------------------------------------------------------------ отложенные удары и волны
    private void tickStrikes(ServerWorld w) {
        if (strikes.isEmpty()) return;
        Iterator<Strike> it = strikes.iterator();
        while (it.hasNext()) {
            Strike s = it.next();
            int left = s.at() - ticks;
            if (left > 0) {
                double outer = 3.2 + left * 0.12;
                if (left % 2 == 0) {
                    Fx.ringStatic(w, Fx.RED, s.x(), s.y() + 0.12, s.z(), 3.2, 22);
                    Fx.ringStatic(w, Fx.RED_BIG, s.x(), s.y() + 0.12, s.z(), outer, 18);
                }
                if (left % 3 == 0) w.spawnParticles(ParticleTypes.END_ROD, s.x(), s.y() + 0.3, s.z(), 2, 0.5, 0.1, 0.5, 0.12);
                if (left == s.total() - 1) Fx.sound(w, s.x(), s.y(), s.z(), "block.note_block.bell", 2f, 0.6f);
                if (left == 12) Fx.sound(w, s.x(), s.y(), s.z(), "entity.warden.sonic_charge", 1.6f, 1.8f);
                continue;
            }
            it.remove();
            Fx.bolt(w, s.x(), s.y(), s.z());
            Fx.sound(w, s.x(), s.y(), s.z(), "entity.generic.explode", 2.4f, 0.8f);
            w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, s.x(), s.y() + 0.5, s.z(), 1, 0, 0, 0, 0);
            w.spawnParticles(ParticleTypes.FLAME, s.x(), s.y() + 0.4, s.z(), 40, 1.2, 0.3, 1.2, 0.18);
            w.spawnParticles(Fx.RED_BIG, s.x(), s.y() + 1.5, s.z(), 20, 0.8, 1.5, 0.8, 0);
            w.spawnParticles(Fx.redstoneDebris(), s.x(), s.y() + 0.5, s.z(), 24, 1.0, 0.3, 1.0, 0.35);
            Fx.ringOut(w, ParticleTypes.CLOUD, s.x(), s.y() + 0.3, s.z(), 1.0, 20, 0.6, 0.02);
            for (ServerPlayerEntity p : w.getPlayers()) {
                if (p.isSpectator() || !p.isAlive()) continue;
                double dx = p.getX() - s.x(), dz = p.getZ() - s.z();
                if (dx * dx + dz * dz > 3.3 * 3.3 || Math.abs(p.getY() - s.y()) > 3.5) continue;
                hurt(w, p, s.dmg(), false);
                launch(p, dx, dz, 0.5, 0.75);
            }
        }
    }

    private void tickWaves(ServerWorld w) {
        if (waves.isEmpty()) return;
        Iterator<Wave> it = waves.iterator();
        while (it.hasNext()) {
            Wave v = it.next();
            int k = ticks - v.start;
            if (k < 0) continue;
            if (k > 16) { it.remove(); continue; }
            double r = 1.5 + k * 0.85;
            int n = Math.max(14, (int) (r * 5));
            Fx.ringStatic(w, Fx.RED_BIG, v.c.x, v.c.y + 0.15, v.c.z, r, n);
            if (k % 2 == 0) Fx.ringOut(w, ParticleTypes.CLOUD, v.c.x, v.c.y + 0.25, v.c.z, r, Math.max(10, n / 2), 0.12, 0.01);
            if (k % 3 == 0) w.spawnParticles(Fx.debris(w.getBlockState(BlockPos.ofFloored(v.c.x + r, v.c.y - 1, v.c.z))), v.c.x + r, v.c.y + 0.3, v.c.z, 6, 0.3, 0.1, 0.3, 0.2);
            if (v.dmg <= 0f) continue;
            for (ServerPlayerEntity p : w.getPlayers()) {
                if (p.isSpectator() || !p.isAlive() || v.hit.contains(p.getUuid())) continue;
                double dx = p.getX() - v.c.x, dz = p.getZ() - v.c.z, d = Math.sqrt(dx * dx + dz * dz);
                if (Math.abs(d - r) > 1.25 || p.getY() > v.c.y + 1.25) continue;      // в прыжке над волной — не задело
                v.hit.add(p.getUuid());
                hurt(w, p, v.dmg, false);
                launch(p, dx, dz, 0.95, 0.6);
            }
        }
    }

    // ------------------------------------------------------------------ фоновые эффекты
    private void ambient(ServerWorld w) {
        double x = getX(), y = getY(), z = getZ();
        int a = attack();
        if (dying || a == SPAWN) return;
        Vec3d f = forward();
        if (ticks % 3 == 0) {
            w.spawnParticles(Fx.RED, x - f.x * 1.1, y + 2.9, z - f.z * 1.1, 2, 0.35, 0.35, 0.35, 0);
            if (enraged()) {
                w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 2.4, z, 2, 1.3, 1.8, 1.3, 0.12);
                w.spawnParticles(ParticleTypes.FLAME, x, y + 5.0, z, 1, 1.4, 0.2, 0.8, 0.02);
            }
        }
        if (ticks % 5 == 0) w.spawnParticles(enraged() ? Fx.WHITE_HOT : Fx.HOT, x + f.x * 1.5, y + 4.1, z + f.z * 1.5, 1, 0.4, 0.15, 0.1, 0);
        Vec3d v = getVelocity();
        if (isOnGround() && v.x * v.x + v.z * v.z > 0.0008 && a == NONE) {
            if (++stepTimer >= 11) {
                stepTimer = 0;
                Fx.sound(w, x, y, z, "entity.iron_golem.step", 2.2f, 0.45f);
                Fx.sound(w, x, y, z, "block.stone.break", 1.2f, 0.5f);
                w.spawnParticles(Fx.debris(w.getBlockState(getBlockPos().down())), x, y + 0.2, z, 14, 1.0, 0.1, 1.0, 0.1);
                w.spawnParticles(ParticleTypes.CLOUD, x, y + 0.1, z, 3, 0.9, 0.05, 0.9, 0.01);
            }
        }
        if (getHealth() < getMaxHealth() * 0.25f && ticks % 30 == 0) Fx.sound(w, x, y, z, "entity.warden.heartbeat", 2.5f, 0.8f);
    }

    // ------------------------------------------------------------------ помощники
    private double horiz(Entity e) {
        double dx = e.getX() - getX(), dz = e.getZ() - getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private Vec3d forward() { return Vec3d.fromPolar(0f, getYaw()); }

    private void face(Vec3d p, float step) {
        double dx = p.x - getX(), dz = p.z - getZ();
        float want = (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90f;
        float diff = MathHelper.clamp(MathHelper.wrapDegrees(want - getYaw()), -step, step);
        float yaw = getYaw() + diff;
        setYaw(yaw); setBodyYaw(yaw); setHeadYaw(yaw);
    }

    private float diffMult(ServerWorld w) {
        return switch (w.getDifficulty()) { case EASY -> 0.65f; case HARD -> 1.3f; default -> 1.0f; };
    }

    private void hurt(ServerWorld w, LivingEntity t, float base, boolean magic) {
        t.damage(magic ? w.getDamageSources().magic() : w.getDamageSources().mobAttack(this), base * diffMult(w));
    }

    private void launch(Entity t, double dx, double dz, double h, double v) {
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4) { dx = 0; dz = 1; len = 1; }
        t.setVelocity(dx / len * h, v, dz / len * h);
        if (t instanceof ServerPlayerEntity sp) sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp));
    }

    // ------------------------------------------------------------------ сохранение
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("GolemPhase", phase());
        nbt.putBoolean("GolemAwake", awake);
        nbt.putInt("NovaCd", novaCd);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("GolemPhase")) this.dataTracker.set(PHASE, Math.max(1, nbt.getInt("GolemPhase")));
        awake = nbt.getBoolean("GolemAwake");
        if (nbt.contains("NovaCd")) novaCd = nbt.getInt("NovaCd");
    }

    // ------------------------------------------------------------------ GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            int a = attackId();
            if (a != NONE && a < PLAY.length) return state.setAndContinue(PLAY[a]);
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }));
        // оборот вихря крутит бёдра на три витка — отдельным контроллером без плавного перехода, чтобы после атаки не «отматывался»
        controllers.add(new AnimationController<>(this, "spin", 0, state ->
            attackId() == SPIN ? state.setAndContinue(SPIN_TURN) : PlayState.STOP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
