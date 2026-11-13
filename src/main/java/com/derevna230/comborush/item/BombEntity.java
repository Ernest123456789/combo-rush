package com.derevna230.comborush.item;

import com.derevna230.comborush.pet.CRPetEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Заложенная бомба. Таймер {@value #FUSE_TICKS} тиков (40 с). Обезвреживание — удержание ПКМ на бомбе: ваниль повторяет
 * взаимодействие каждые 4 тика, пока кнопка зажата, поэтому прогресс идёт, пока {@link #interact} приходит без пауз;
 * с набором ({@link DefuseKitItem} в инвентаре) нужно {@value #DEFUSE_KIT_TICKS} тиков (5 с), без — {@value #DEFUSE_TICKS} (10 с).
 * Отпустил, отошёл или отвернулся — прогресс сбрасывается.
 */
public class BombEntity extends Entity {
    public static final int FUSE_TICKS = 40 * 20;
    public static final int DEFUSE_KIT_TICKS = 5 * 20;
    public static final int DEFUSE_TICKS = 10 * 20;
    private static final double RADIUS = 14.0, MAX_DAMAGE = 60.0;
    private static final DustParticleEffect LED = new DustParticleEffect(new Vector3f(1f, 0.1f, 0.1f), 0.8f);

    /** События бомбы для режима матча (null — не слушают) */
    public interface Listener {
        void planted(ServerWorld w, BombEntity bomb, Entity owner);
        void exploded(ServerWorld w);
        void defused(ServerWorld w, LivingEntity by);
    }
    public static volatile Listener LISTENER;

    private int remain = FUSE_TICKS;
    private UUID owner;
    private UUID defuser;
    private int progress, need, lastInteract;
    private String lastLabel = "";

    public BombEntity(EntityType<? extends BombEntity> type, World world) {
        super(type, world);
        this.noClip = false;
        this.setNoGravity(true);
    }

    /** Заложить бомбу под игроком */
    public static BombEntity plant(ServerWorld sw, ServerPlayerEntity p) { return plantAt(sw, p); }

    /** Заложить бомбу там, где стоит owner (игрок или бот) */
    public static BombEntity plantAt(ServerWorld sw, Entity p) {
        BombEntity b = new BombEntity(com.derevna230.comborush.pet.ModEntities.BOMB, sw);
        b.owner = p.getUuid();
        b.setPosition(p.getX(), p.getY(), p.getZ());
        b.refreshLabel();
        sw.spawnEntity(b);
        sw.playSound(null, b.getX(), b.getY(), b.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 1.5f, 0.6f);
        Text msg = Text.literal("§c§lБомба заложена! §fДо взрыва 40 секунд.");
        for (ServerPlayerEntity o : sw.getPlayers()) o.sendMessage(msg, false);
        Listener l = LISTENER;
        if (l != null) l.planted(sw, b, p);
        return b;
    }

    /** Полоска прогресса для строки над хотбаром */
    public static String bar(float f) {
        int n = Math.round(Math.max(0, Math.min(1, f)) * 20);
        return "§a" + "█".repeat(n) + "§8" + "█".repeat(20 - n) + " §f" + Math.round(f * 100) + "%";
    }

    @Override protected void initDataTracker(DataTracker.Builder builder) { }
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) { }
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) { }

    @Override public boolean isPushable() { return false; }
    @Override public boolean canHit() { return true; }                  // прицел «ловит» бомбу — на ней можно держать ПКМ
    @Override public boolean isAttackable() { return false; }
    @Override public boolean isInvulnerable() { return true; }
    @Override public boolean damage(DamageSource source, float amount) { return false; }

    /** Секунд до взрыва (для текста над бомбой) */
    private void refreshLabel() {
        int s = (remain + 19) / 20;
        String t = String.format("§c§l%d:%02d", s / 60, s % 60);
        if (defuser != null && need > 0) t += " §e⚙ " + Math.round(100f * progress / need) + "%";
        if (!t.equals(lastLabel)) { lastLabel = t; setCustomName(Text.literal(t)); setCustomNameVisible(true); }
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (getWorld().isClient) return ActionResult.SUCCESS;
        if (!(player instanceof ServerPlayerEntity p) || player.isSpectator()) return ActionResult.PASS;
        if (!defuseStep(p, hasKit(p))) p.sendMessage(Text.literal("§cЕё уже обезвреживает другой."), true);
        return ActionResult.SUCCESS;
    }

    /** Один шаг обезвреживания (игрок — по повторному ПКМ, бот — каждый тик). @return false, если бомбу уже обезвреживает другой */
    public boolean defuseStep(LivingEntity who, boolean kit) {
        if (defuser == null) {
            defuser = who.getUuid();
            progress = 0;
            need = kit ? DEFUSE_KIT_TICKS : DEFUSE_TICKS;
            getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.PLAYERS, 1f, 1.6f);
        } else if (!defuser.equals(who.getUuid())) {
            return false;
        }
        lastInteract = age;
        return true;
    }

    public int remainTicks() { return remain; }
    public boolean beingDefused() { return defuser != null; }

    private static boolean hasKit(ServerPlayerEntity p) {
        return p.getInventory().contains(s -> s.getItem() instanceof DefuseKitItem);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld sw)) return;
        if (remain <= 0) { explode(sw); discard(); return; }
        remain--;

        // сигнал: чем ближе взрыв, тем чаще
        int interval = remain > 600 ? 20 : remain > 400 ? 16 : remain > 200 ? 12 : remain > 100 ? 8 : 4;
        if (remain % interval == 0) {
            sw.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 1.2f, remain > 100 ? 1.8f : 2.0f);
            sw.spawnParticles(LED, getX(), getY() + 0.45, getZ(), 2, 0.05, 0.02, 0.05, 0);
        }

        // обезвреживание
        if (defuser != null) {
            Entity de = sw.getEntity(defuser);
            LivingEntity d = de instanceof LivingEntity le ? le : null;
            ServerPlayerEntity dp = d instanceof ServerPlayerEntity sp ? sp : null;
            boolean ok = d != null && d.isAlive() && !d.isSpectator() && age - lastInteract <= 6 && d.getEyePos().distanceTo(getPos()) <= 4.5
                && d.getWorld() == getWorld();
            if (!ok) {
                if (dp != null && age - lastInteract > 6) dp.sendMessage(Text.literal("§cОбезвреживание прервано."), true);
                defuser = null; progress = 0;
            } else {
                progress++;
                if (dp != null && progress % 4 == 0) dp.sendMessage(Text.literal("§6Обезвреживание §f" + bar(progress / (float) need)), true);
                if (progress % 10 == 0) sw.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.PLAYERS, 0.8f, 1.5f);
                if (progress >= need) { defused(sw, d); return; }
            }
        }
        if (remain % 4 == 0 || defuser != null) refreshLabel();
    }

    private void defused(ServerWorld sw, LivingEntity by) {
        sw.playSound(null, getX(), getY(), getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.2f, 1.0f);
        sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 0.4, getZ(), 18, 0.4, 0.2, 0.4, 0.0);
        Text msg = Text.literal("§a§lБомба обезврежена! §f" + by.getName().getString());
        for (ServerPlayerEntity o : sw.getPlayers()) o.sendMessage(msg, false);
        discard();
        Listener l = LISTENER;
        if (l != null) l.defused(sw, by);
    }

    private boolean blocked(ServerWorld sw, Vec3d from, Vec3d to) {
        BlockHitResult r = sw.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
        return r.getType() != HitResult.Type.MISS;
    }

    private void explode(ServerWorld sw) {
        Vec3d c = getPos().add(0, 0.4, 0);
        for (int i = 0; i < 3; i++) sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x + (i - 1) * 1.2, c.y, c.z + (i % 2) * 1.0, 1, 0, 0, 0, 0);
        sw.spawnParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 0.5, c.z, 60, 1.5, 1.0, 1.5, 0.08);
        sw.spawnParticles(ParticleTypes.FLAME, c.x, c.y + 0.3, c.z, 60, 1.2, 0.6, 1.2, 0.15);
        sw.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 6.0f, 0.7f);
        sw.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 5.0f, 0.8f);
        ServerPlayerEntity op = owner == null ? null : sw.getServer().getPlayerManager().getPlayer(owner);
        DamageSource src = op != null ? sw.getDamageSources().playerAttack(op) : sw.getDamageSources().generic();
        Box area = new Box(c.x - RADIUS, c.y - RADIUS, c.z - RADIUS, c.x + RADIUS, c.y + RADIUS, c.z + RADIUS);
        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, area, e -> e.isAlive() && !(e instanceof CRPetEntity) && !e.isSpectator())) {
            Vec3d t = e.getPos().add(0, e.getHeight() * 0.5, 0);
            double d = t.distanceTo(c);
            if (d > RADIUS) continue;
            float k = (float) (1.0 - d / RADIUS);
            float dmg = (float) (MAX_DAMAGE * Math.pow(k, 0.8));
            if (blocked(sw, c, t)) dmg *= 0.4f;            // стены ослабляют, но не защищают полностью
            if (dmg < 1f) continue;
            e.timeUntilRegen = 0;
            e.damage(src, dmg);
            Vec3d push = t.subtract(c).normalize().multiply(1.4 * k);
            e.addVelocity(push.x, 0.5 * k, push.z);
            e.velocityModified = true;
        }
        Listener l = LISTENER;
        if (l != null) l.exploded(sw);
    }
}
