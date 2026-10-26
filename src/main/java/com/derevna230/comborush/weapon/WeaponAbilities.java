package com.derevna230.comborush.weapon;

import com.derevna230.comborush.armor.ArmorSetDef;
import com.derevna230.comborush.armor.ArmorSetRegistry;
import com.derevna230.comborush.item.ModItems;
import com.derevna230.comborush.pet.CRPetEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Особые способности оружия, щитов и питомцев (описаны в JSON, поле abilities) */
public final class WeaponAbilities {
    private WeaponAbilities() {}
    private static final String ARROW_TAG = "cr_w:";
    private static int busy = 0;

    private static final class Dot {
        final LivingEntity target; final UUID attacker; final float damage; int left;
        Dot(LivingEntity t, UUID a, float d, int l) { target = t; attacker = a; damage = d; left = l; }
    }
    private static final List<Dot> DOTS = new ArrayList<>();

    public static void register() {
        // удар оружием / попадание стрелой / блок щитом
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (busy > 0) return;
            if (blocked && entity instanceof ServerPlayerEntity sp) { onBlock(sp, source, baseDamage); return; }
            if (damageTaken <= 0 || !(source.getAttacker() instanceof ServerPlayerEntity p) || entity == p) return;
            ArmorSetDef def = ArmorSetRegistry.get(weaponOf(p, source));
            if (def != null) fire(p, entity, def, "hit", damageTaken, null);
        });
        // убийство
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(source.getAttacker() instanceof ServerPlayerEntity p) || entity == p) return;
            String set = source.getSource() instanceof CRPetEntity pet ? pet.setId() : weaponOf(p, source);
            ArmorSetDef def = ArmorSetRegistry.get(set);
            if (def != null) fire(p, entity, def, "kill", 0, null);
        });
        // выстрел из лука из кейса: пометить стрелу и применить «arrow»
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof PersistentProjectileEntity arrow) || entity.age > 1) return;
            if (!(arrow.getOwner() instanceof ServerPlayerEntity p)) return;
            String set = bowIn(p.getMainHandStack());
            if (set == null) set = bowIn(p.getOffHandStack());
            if (set == null) return;
            arrow.addCommandTag(ARROW_TAG + set);
            ArmorSetDef def = ArmorSetRegistry.get(set);
            if (def == null) return;
            for (AbilityDef a : def.abilities) {
                if (!"arrow".equals(a.trigger) || !roll(a)) continue;
                if ("arrow_damage".equals(a.type)) arrow.setDamage(arrow.getDamage() * a.value);
            }
        });
    }

    private static String bowIn(ItemStack st) {
        String set = ModItems.ITEM_SET.get(st.getItem());
        return set != null && ModItems.kindOf(set).equals("bow") ? set : null;
    }

    /** Каким оружием из кейса нанесён урон (null — не нашим) */
    private static String weaponOf(ServerPlayerEntity p, DamageSource source) {
        if (source.getSource() instanceof PersistentProjectileEntity arrow) {
            for (String tag : arrow.getCommandTags()) if (tag.startsWith(ARROW_TAG)) return tag.substring(ARROW_TAG.length());
            return null;
        }
        if (source.getSource() == p && source.isOf(DamageTypes.PLAYER_ATTACK)) {
            String set = ModItems.ITEM_SET.get(p.getMainHandStack().getItem());
            if (set != null && ModItems.isMainHand(set) && !ModItems.kindOf(set).equals("bow")) return set;
        }
        return null;
    }

    /** Удар боевого питомца */
    public static void onPetHit(ServerPlayerEntity owner, CRPetEntity pet, LivingEntity target, ArmorSetDef def, float damage) {
        if (def != null) fire(owner, target, def, "hit", damage, pet);
    }

    private static void onBlock(ServerPlayerEntity p, DamageSource source, float base) {
        String set = ModItems.ITEM_SET.get(p.getActiveItem().getItem());
        if (set == null || !ModItems.kindOf(set).equals("shield")) return;
        ArmorSetDef def = ArmorSetRegistry.get(set);
        if (def == null) return;
        LivingEntity attacker = source.getAttacker() instanceof LivingEntity le && le != p ? le : null;
        for (AbilityDef a : def.abilities) {
            if (!"block".equals(a.trigger) || !roll(a)) continue;
            switch (a.type) {
                case "reflect" -> {
                    if (attacker != null) {
                        bonus(p, attacker, (float) (base * a.value));
                        particles(p.getServerWorld(), ParticleTypes.ENCHANTED_HIT, attacker, 10);
                    }
                }
                case "heal" -> p.heal((float) a.value);
                case "self_effect" -> effect(p, p, a);
                case "effect" -> { if (attacker != null) effect(p, attacker, a); }
                case "knockback" -> { if (attacker != null) attacker.takeKnockback(a.value, p.getX() - attacker.getX(), p.getZ() - attacker.getZ()); }
                case "shockwave", "blast" -> area(p, p, a, false, null);
                default -> { }
            }
        }
    }

    private static boolean roll(AbilityDef a) { return a.chance >= 1 || ThreadLocalRandom.current().nextDouble() < a.chance; }

    private static void fire(ServerPlayerEntity p, LivingEntity target, ArmorSetDef def, String trigger, float dmg, Entity from) {
        if (def.abilities.isEmpty()) return;
        busy++;
        try {
            ServerWorld w = p.getServerWorld();
            for (AbilityDef a : def.abilities) {
                if (!trigger.equals(a.trigger) || !roll(a)) continue;
                int ticks = (int) Math.round(a.duration * 20);
                switch (a.type) {
                    case "effect" -> { if (target.isAlive()) effect(p, target, a); }
                    case "self_effect" -> effect(p, p, a);
                    case "ignite" -> {
                        target.setFireTicks(Math.max(target.getFireTicks(), ticks));
                        particles(w, ParticleTypes.FLAME, target, 8);
                    }
                    case "freeze" -> {
                        target.setFrozenTicks(Math.max(target.getFrozenTicks(), 140 + ticks));
                        Registries.STATUS_EFFECT.getEntry(Identifier.ofVanilla("slowness")).ifPresent(e ->
                            target.addStatusEffect(new StatusEffectInstance(e, ticks, a.amplifier), p));
                        particles(w, ParticleTypes.SNOWFLAKE, target, 14);
                    }
                    case "lightning" -> {
                        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(w);
                        if (bolt != null) {
                            bolt.refreshPositionAfterTeleport(target.getPos());
                            bolt.setCosmetic(true);
                            w.spawnEntity(bolt);
                        }
                        bonus(p, target, (float) a.value);
                    }
                    case "lifesteal" -> p.heal((float) (dmg * a.value));
                    case "heal" -> p.heal((float) a.value);
                    case "bleed" -> {
                        if (target.isAlive()) DOTS.add(new Dot(target, p.getUuid(), (float) a.value, Math.max(1, (int) Math.round(a.duration))));
                        particles(w, ParticleTypes.DAMAGE_INDICATOR, target, 4);
                    }
                    case "crit" -> {
                        bonus(p, target, (float) (dmg * a.value));
                        particles(w, ParticleTypes.CRIT, target, 14);
                        sound(w, target, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);
                    }
                    case "execute" -> {
                        if (target.isAlive() && target.getHealth() <= target.getMaxHealth() * 0.3f) {
                            bonus(p, target, (float) (dmg * a.value));
                            particles(w, ParticleTypes.SOUL, target, 10);
                        }
                    }
                    case "shockwave" -> {
                        area(p, target, a, true, null);
                        sound(w, target, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 1.4f, 0.5f + ThreadLocalRandom.current().nextFloat());
                        particles(w, ParticleTypes.NOTE, target, 8);
                    }
                    case "cleave" -> area(p, target, a, false, target);
                    case "blast" -> {
                        area(p, target, a, true, null);
                        w.spawnParticles(ParticleTypes.EXPLOSION, target.getX(), target.getBodyY(0.5), target.getZ(), 1, 0, 0, 0, 0);
                        sound(w, target, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 0.7f, 1.3f);
                    }
                    case "knockback" -> target.takeKnockback(a.value, p.getX() - target.getX(), p.getZ() - target.getZ());
                    case "drop_item" -> {
                        var item = Registries.ITEM.get(Identifier.tryParse(a.id));
                        if (item != null && !(item.getDefaultStack().isEmpty())) target.dropStack(new ItemStack(item, Math.max(1, (int) a.value)));
                    }
                    default -> { }
                }
            }
        } finally {
            busy--;
        }
    }

    private static void effect(ServerPlayerEntity p, LivingEntity to, AbilityDef a) {
        Identifier id = Identifier.tryParse(a.id);
        if (id == null) return;
        int ticks = (int) Math.round(a.duration * 20);
        Registries.STATUS_EFFECT.getEntry(id).ifPresent(e -> to.addStatusEffect(new StatusEffectInstance(e, ticks, a.amplifier), p));
    }

    /** Урон по врагам вокруг center. withCenter — бить и сам центр; except — кого пропустить */
    private static void area(ServerPlayerEntity p, LivingEntity center, AbilityDef a, boolean withCenter, LivingEntity except) {
        ServerWorld w = p.getServerWorld();
        List<LivingEntity> list = w.getEntitiesByClass(LivingEntity.class, center.getBoundingBox().expand(a.radius),
            e -> e.isAlive() && e != p && e != except && e instanceof Monster && (withCenter || e != center));
        for (LivingEntity e : list) {
            bonus(p, e, (float) a.value);
            e.takeKnockback(0.5, center.getX() - e.getX(), center.getZ() - e.getZ());
            if (except != null) particles(w, ParticleTypes.SWEEP_ATTACK, e, 1);
        }
    }

    /** Дополнительный урон: сбрасываем неуязвимость после удара, чтобы урон не потерялся */
    private static void bonus(ServerPlayerEntity p, LivingEntity target, float amount) {
        if (amount <= 0 || !target.isAlive()) return;
        target.timeUntilRegen = 0;
        target.damage(p.getDamageSources().indirectMagic(p, p), amount);
    }

    private static void particles(ServerWorld w, ParticleEffect type, Entity e, int n) {
        w.spawnParticles(type, e.getX(), e.getBodyY(0.6), e.getZ(), n, e.getWidth() * 0.4, e.getHeight() * 0.3, e.getWidth() * 0.4, 0.05);
    }

    private static void sound(ServerWorld w, Entity e, SoundEvent s, float vol, float pitch) {
        w.playSound(null, e.getBlockPos(), s, SoundCategory.PLAYERS, vol, pitch);
    }

    /** Кровотечение: урон раз в секунду */
    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 0 || DOTS.isEmpty()) return;
        busy++;
        try {
            for (Iterator<Dot> it = DOTS.iterator(); it.hasNext(); ) {
                Dot d = it.next();
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(d.attacker);
                if (p == null || !d.target.isAlive() || d.target.isRemoved() || d.left-- <= 0) { it.remove(); continue; }
                bonus(p, d.target, d.damage);
                if (d.target.getWorld() instanceof ServerWorld w) particles(w, ParticleTypes.DAMAGE_INDICATOR, d.target, 3);
            }
        } finally {
            busy--;
        }
    }
}
