package com.derevna230.comborush.pet;

import com.derevna230.comborush.item.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Декоративное растение: стоит на месте и играет спокойную анимацию. Сохраняется в мире.
 * Удар игрока — растение выкапывается и возвращается предметом.
 * Модель: geo/entity/decor/<id>.geo.json, анимация animation.cr_decor.idle.
 */
public class DecorEntity extends PathAwareEntity implements GeoEntity {
    private static final TrackedData<String> DECOR = DataTracker.registerData(DecorEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.cr_decor.idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public DecorEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setAiDisabled(true);
        this.setPersistent();
        this.setSilent(true);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 10).add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(DECOR, "fern_pot");
    }

    public String decorId() { return this.dataTracker.get(DECOR); }

    /** Хитбокс зависит от вида декора (машина шире растения) */
    @Override
    public EntityDimensions getDimensions(EntityPose pose) {
        if (this.dataTracker == null) return super.getDimensions(pose);
        DecorSizes.Size s = DecorSizes.of(decorId());
        return EntityDimensions.fixed(s.width(), s.height());
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (DECOR.equals(data)) this.calculateDimensions();
    }
    public void setDecorId(String id) { this.dataTracker.set(DECOR, id); }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("CrDecor", decorId());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("CrDecor")) setDecorId(nbt.getString("CrDecor"));
        this.setAiDisabled(true);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL)) return super.damage(source, amount);
        if (source.getAttacker() instanceof PlayerEntity p && !this.getWorld().isClient && !this.isRemoved()) {
            Item item = Registries.ITEM.get(Identifier.of("combo-rush", "decor_" + decorId()));
            if (!p.isCreative() && item != null && ModItems.ITEM_SET.containsKey(item)) this.dropStack(new ItemStack(item));
            if (this.getWorld() instanceof ServerWorld w)
                w.spawnParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 0.4, getZ(), 6, 0.25, 0.2, 0.25, 0);
            this.getWorld().playSound(null, getBlockPos(), SoundEvents.BLOCK_AZALEA_BREAK, SoundCategory.BLOCKS, 1f, 1f);
            this.discard();
        }
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

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
