package com.derevna230.comborush.boss;

import com.derevna230.comborush.hud.HudSync;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Торговец Редстоуна: бродит рядом с игроком, как странствующий торговец, продаёт Сердце голема и редкий декор.
 * Приходит сам (TraderSpawner), уходит через 10 минут. Неуязвим.
 */
public class GolemTraderEntity extends PathAwareEntity implements GeoEntity {
    public static final int LIFE = 12000;
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.cr_trader.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.cr_trader.walk");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** Скидка на кейсы и сколько штук каждого кейса торговец привёз */
    public static final int DISCOUNT_PERCENT = 25, STOCK = 4, OFFERS = 3;

    private int left = LIFE, ticks = 0, pauseUntil = 0;
    private boolean warned = false;
    private final java.util.List<String> offers = new java.util.ArrayList<>();
    private final int[] stock = new int[OFFERS];

    public GolemTraderEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setPersistent();
        this.setInvulnerable(true);
        this.setCustomName(Text.literal("§c§lТорговец Редстоуна"));
        this.setCustomNameVisible(true);
        rollOffers();
    }

    /** Каждый торговец привозит свои 3 кейса со скидкой — от визита к визиту набор другой */
    private void rollOffers() {
        java.util.List<String> all = new java.util.ArrayList<>(com.derevna230.comborush.item.ModItems.CASE_IDS);
        all.removeIf(id -> { var d = com.derevna230.comborush.cases.CaseRegistry.get(id); return d != null && d.noShop; });
        java.util.Collections.shuffle(all, new java.util.Random(this.getRandom().nextLong()));
        offers.clear();
        for (int i = 0; i < OFFERS && i < all.size(); i++) { offers.add(all.get(i)); stock[i] = STOCK; }
    }

    public java.util.List<String> offers() { return offers; }
    public int stock(int idx) { return idx >= 0 && idx < OFFERS ? stock[idx] : 0; }
    public void takeStock(int idx) { if (idx >= 0 && idx < OFFERS && stock[idx] > 0) stock[idx]--; }
    public int leftTicks() { return left; }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 40)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.5);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(4, new WanderAroundFarGoal(this, 0.45));
        this.goalSelector.add(5, new LookAtEntityGoal(this, PlayerEntity.class, 8f));
        this.goalSelector.add(6, new LookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld w)) return;
        ticks++;
        if (ticks < pauseUntil) getNavigation().stop();                 // пока с ним торгуют — стоит на месте
        if (ticks % 40 == 0) w.spawnParticles(Fx.RED, getX(), getY() + 0.1, getZ(), 2, 0.5, 0.05, 0.5, 0);
        if (--left == 1200 && !warned) {
            warned = true;
            for (ServerPlayerEntity p : w.getPlayers())
                if (p.squaredDistanceTo(this) < 48 * 48) p.sendMessage(Text.literal("§c[Торговец Редстоуна] §fСкоро ухожу — успейте купить, что нужно."), false);
        }
        if (left <= 0) leave(w);
    }

    private void leave(ServerWorld w) {
        w.spawnParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 30, 0.4, 0.8, 0.4, 0.05);
        w.spawnParticles(Fx.RED_BIG, getX(), getY() + 1, getZ(), 20, 0.5, 0.9, 0.5, 0);
        Fx.sound(w, getX(), getY(), getZ(), "entity.wandering_trader.disappeared", 1.2f, 0.9f);
        for (ServerPlayerEntity p : w.getPlayers())
            if (p.squaredDistanceTo(this) < 48 * 48) p.sendMessage(Text.literal("§c[Торговец Редстоуна] §7ушёл по своим делам."), false);
        discard();
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (!getWorld().isClient && player instanceof ServerPlayerEntity sp) {
            pauseUntil = ticks + 400;
            getNavigation().stop();
            lookAtEntity(player, 30f, 30f);
            if (getWorld() instanceof ServerWorld w) Fx.sound(w, getX(), getY(), getZ(), "entity.wandering_trader.yes", 1f, 1f);
            if (HudSync.has(sp)) TraderNet.open(sp, this);
            else TraderGui.open(sp, this);
        }
        return ActionResult.success(getWorld().isClient);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL)) return super.damage(source, amount);
        return false;
    }

    @Override protected SoundEvent getAmbientSound() { return Fx.soundEvent("entity.wandering_trader.ambient"); }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return Fx.soundEvent("entity.wandering_trader.hurt"); }
    @Override public boolean cannotDespawn() { return true; }
    @Override public boolean canImmediatelyDespawn(double distanceSquared) { return false; }
    @Override protected void pushAway(Entity entity) { }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("TraderLeft", left);
        net.minecraft.nbt.NbtList l = new net.minecraft.nbt.NbtList();
        for (String s : offers) l.add(net.minecraft.nbt.NbtString.of(s));
        nbt.put("TraderOffers", l);
        nbt.putIntArray("TraderStock", stock);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("TraderLeft")) left = nbt.getInt("TraderLeft");
        if (nbt.contains("TraderOffers")) {
            offers.clear();
            for (net.minecraft.nbt.NbtElement e : nbt.getList("TraderOffers", net.minecraft.nbt.NbtElement.STRING_TYPE)) offers.add(e.asString());
            int[] st = nbt.getIntArray("TraderStock");
            for (int i = 0; i < OFFERS; i++) stock[i] = i < st.length ? st[i] : 0;
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 5, state -> state.setAndContinue(state.isMoving() ? WALK : IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
