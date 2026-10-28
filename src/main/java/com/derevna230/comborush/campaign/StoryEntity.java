package com.derevna230.comborush.campaign;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * Сюжетные персонажи и предметы сцены: Билетёр, адресаты писем, афиши.
 * kind выбирает модель (geo/entity/story/<kind>.geo.json). Неуязвимы, стоят на месте, смотрят на игрока.
 * owner — у временных (адресаты курьера): исчезают, когда задание игрока закончилось.
 */
public class StoryEntity extends PathAwareEntity implements GeoEntity {
    private static final TrackedData<String> KIND = DataTracker.registerData(StoryEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.cr_story.idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private UUID owner = null;
    private int slot = -1;
    private float homeYaw = 0;

    public StoryEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setAiDisabled(true);
        this.setPersistent();
        this.setInvulnerable(true);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 20).add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(KIND, "ticketer");
    }

    public String kind() { return this.dataTracker.get(KIND); }
    public void setKind(String k) { this.dataTracker.set(KIND, k); }
    public boolean isProp() { return kind().startsWith("poster"); }
    public UUID owner() { return owner; }
    public int slot() { return slot; }
    public void setOwner(UUID owner, int slot) { this.owner = owner; this.slot = slot; }
    public void setHomeYaw(float yaw) {
        this.homeYaw = yaw;
        this.setYaw(yaw); this.setBodyYaw(yaw); this.setHeadYaw(yaw);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("CrKind", kind());
        nbt.putFloat("CrYaw", homeYaw);
        if (owner != null) { nbt.putUuid("CrOwner", owner); nbt.putInt("CrSlot", slot); }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("CrKind")) setKind(nbt.getString("CrKind"));
        homeYaw = nbt.getFloat("CrYaw");
        if (nbt.containsUuid("CrOwner")) { owner = nbt.getUuid("CrOwner"); slot = nbt.getInt("CrSlot"); }
        this.setAiDisabled(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        // тело всегда смотрит «из своей постройки», голова — на ближайшего игрока
        this.setBodyYaw(homeYaw);
        this.setYaw(homeYaw);
        if (!isProp() && age % 2 == 0) {
            PlayerEntity p = getWorld().getClosestPlayer(this, 8);
            float head = homeYaw, pitch = 0;
            if (p != null && !p.isSpectator()) {
                double dx = p.getX() - getX(), dz = p.getZ() - getZ(), dy = p.getEyeY() - getEyeY();
                float want = (float) (MathHelper.atan2(dz, dx) * (180 / Math.PI)) - 90f;
                float diff = MathHelper.clamp(MathHelper.wrapDegrees(want - homeYaw), -60f, 60f);
                head = homeYaw + diff;
                pitch = (float) -(MathHelper.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180 / Math.PI));
                pitch = MathHelper.clamp(pitch, -30f, 30f);
            }
            this.setHeadYaw(MathHelper.lerpAngleDegrees(0.35f, getHeadYaw(), head));
            this.setPitch(MathHelper.lerp(0.35f, getPitch(), pitch));
        }
        if (owner != null && age % 40 == 0 && !Campaign.keepTemp(this)) this.discard();
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (!getWorld().isClient && player instanceof ServerPlayerEntity sp) Campaign.onInteract(sp, this);
        return ActionResult.success(getWorld().isClient);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL)) return super.damage(source, amount);
        // удар тоже считается разговором — так проще новичку
        if (source.getAttacker() instanceof ServerPlayerEntity sp && !getWorld().isClient) Campaign.onInteract(sp, this);
        return false;
    }

    @Override public boolean isPushable() { return false; }
    @Override protected void pushAway(Entity entity) { }
    @Override public boolean cannotDespawn() { return true; }
    @Override public boolean canImmediatelyDespawn(double distanceSquared) { return false; }
    @Override public boolean isFireImmune() { return true; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, state -> state.setAndContinue(IDLE)));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
