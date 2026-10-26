package com.derevna230.comborush.pet;

import com.derevna230.comborush.armor.ArmorSetDef;
import com.derevna230.comborush.armor.ArmorSetRegistry;
import com.derevna230.comborush.weapon.WeaponAbilities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Питомец игрока. Бессмертный, не толкается, по нему нельзя попасть.
 * Спокойные ходят следом; боевые (pet_damage > 0 в JSON) бьют тех, кто напал на хозяина или на кого напал хозяин.
 * Модель: geo/entity/pet/<id>.geo.json, анимации animation.cr_pet.idle / walk / attack.
 */
public class CRPetEntity extends PathAwareEntity implements GeoEntity {
    private static final TrackedData<String> PET = DataTracker.registerData(CRPetEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.cr_pet.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.cr_pet.walk");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.cr_pet.attack");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private UUID owner;

    public CRPetEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setPersistent();
        this.setInvulnerable(true);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 20)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
            .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 2)
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(PET, "pigeon");
    }

    public String petId() { return this.dataTracker.get(PET); }
    public String setId() { return "pet_" + petId(); }
    public UUID ownerUuid() { return owner; }

    public void setup(String petId, ServerPlayerEntity player) {
        this.dataTracker.set(PET, petId);
        this.owner = player.getUuid();
    }

    public ArmorSetDef def() { return ArmorSetRegistry.get(setId()); }

    public boolean isFighter() {
        ArmorSetDef d = def();
        return d != null && d.pet_damage > 0;
    }

    public PlayerEntity ownerEntity() {
        return owner == null ? null : this.getWorld().getPlayerByUuid(owner);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.35, true));
        this.goalSelector.add(3, new FollowOwner(this));
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8f));
        this.goalSelector.add(7, new LookAroundGoal(this));
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (this.age % 10 != 0) return;
        PlayerEntity o = ownerEntity();
        if (o == null) return;
        if (this.squaredDistanceTo(o) > 20 * 20) teleportNear(o);

        LivingEntity t = this.getTarget();
        if (t != null && (!t.isAlive() || t.squaredDistanceTo(o) > 20 * 20 || !isFighter())) { this.setTarget(null); t = null; }
        if (t == null && isFighter()) {
            LivingEntity c = o.getAttacker();
            if (!valid(c, o)) c = (o.age - o.getLastAttackTime() < 100) ? o.getAttacking() : null;
            if (!valid(c, o)) {
                List<HostileEntity> near = this.getWorld().getEntitiesByClass(HostileEntity.class, o.getBoundingBox().expand(8),
                    h -> h.isAlive() && h.getTarget() == o);
                c = near.isEmpty() ? null : near.get(0);
            }
            if (valid(c, o)) this.setTarget(c);
        }
    }

    private boolean valid(LivingEntity c, PlayerEntity o) {
        return c != null && c.isAlive() && c != o && c != this && !(c instanceof PlayerEntity) && !(c instanceof CRPetEntity);
    }

    void teleportNear(PlayerEntity o) {
        double a = this.random.nextDouble() * Math.PI * 2;
        this.refreshPositionAndAngles(o.getX() + Math.cos(a) * 1.5, o.getY(), o.getZ() + Math.sin(a) * 1.5, this.getYaw(), 0);
        this.getNavigation().stop();
    }

    @Override
    public boolean tryAttack(Entity target) {
        ArmorSetDef d = def();
        if (d == null || d.pet_damage <= 0 || !(ownerEntity() instanceof ServerPlayerEntity sp)) return false;
        float dmg = (float) d.pet_damage;
        var type = this.getWorld().getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DamageTypes.MOB_ATTACK);
        // атакующий — хозяин: убийство засчитывается ему (очки, опыт, добыча)
        boolean ok = target.damage(new DamageSource(type, this, sp), dmg);
        if (ok) {
            this.triggerAnim("attack", "attack");
            if (target instanceof LivingEntity le) WeaponAbilities.onPetHit(sp, this, le, d, dmg);
        }
        return ok;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL)) return super.damage(source, amount);
        return false;
    }

    @Override public boolean canHit() { return false; }
    @Override public boolean isFireImmune() { return true; }
    @Override public boolean isPushable() { return false; }
    @Override protected void pushAway(Entity entity) { }
    @Override public boolean cannotDespawn() { return true; }
    @Override public boolean canImmediatelyDespawn(double distanceSquared) { return false; }

    // ---------------- GeckoLib ----------------
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 4, state -> state.setAndContinue(state.isMoving() ? WALK : IDLE)));
        controllers.add(new AnimationController<>(this, "attack", 2, state -> PlayState.STOP).triggerableAnim("attack", ATTACK));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    /** Идти за хозяином */
    static class FollowOwner extends Goal {
        private final CRPetEntity pet;
        private int recalc;

        FollowOwner(CRPetEntity pet) {
            this.pet = pet;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            PlayerEntity o = pet.ownerEntity();
            return o != null && pet.getTarget() == null && pet.squaredDistanceTo(o) > 9;
        }

        @Override
        public boolean shouldContinue() {
            PlayerEntity o = pet.ownerEntity();
            return o != null && pet.getTarget() == null && !pet.getNavigation().isIdle() && pet.squaredDistanceTo(o) > 4;
        }

        @Override public void start() { recalc = 0; }
        @Override public void stop() { pet.getNavigation().stop(); }

        @Override
        public void tick() {
            PlayerEntity o = pet.ownerEntity();
            if (o == null) return;
            pet.getLookControl().lookAt(o, 10f, pet.getMaxLookPitchChange());
            if (--recalc > 0) return;
            recalc = 10;
            if (pet.squaredDistanceTo(o) > 12 * 12) pet.teleportNear(o);
            else pet.getNavigation().startMovingTo(o, 1.25);
        }
    }
}
