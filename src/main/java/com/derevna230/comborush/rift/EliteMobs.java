package com.derevna230.comborush.rift;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.cycle.CycleManager;
import com.derevna230.comborush.cycle.CyclePhase;
import com.derevna230.comborush.item.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Элитные мобы — «издания», как у карт Balatro:
 *  фольга (больше здоровья, ×3 очка), голограмма (быстрее и сильнее, ×4), полихром (очень живучий, ×6).
 * Появляются случайно среди обычных враждебных мобов (во время события чаще) и в разломах.
 */
public final class EliteMobs {
    private EliteMobs() {}

    public enum Edition {
        FOIL("foil", "Фольга", Formatting.AQUA, 3.0, 1.0, 0.0, 0.25),
        HOLO("holo", "Голограмма", Formatting.LIGHT_PURPLE, 4.0, 1.5, 0.2, 0.4),
        POLY("poly", "Полихром", Formatting.GOLD, 6.0, 2.5, 0.1, 0.6);

        public final String id, label; public final Formatting color;
        public final double scoreMult, healthBonus, speedBonus, damageBonus;
        Edition(String id, String label, Formatting color, double scoreMult, double healthBonus, double speedBonus, double damageBonus) {
            this.id = id; this.label = label; this.color = color; this.scoreMult = scoreMult;
            this.healthBonus = healthBonus; this.speedBonus = speedBonus; this.damageBonus = damageBonus;
        }
    }

    private static final String TAG = "cr_elite:";
    private static final String CHECKED = "cr_elite_checked";
    /** Шанс стать элитным: вне события / во время события */
    private static final double CHANCE_IDLE = 0.03, CHANCE_EVENT = 0.08;
    private static final Set<EntityType<?>> EXCLUDED = Set.of(EntityType.WITHER, EntityType.WARDEN, EntityType.ELDER_GUARDIAN,
        EntityType.ENDER_DRAGON);

    private static final List<LivingEntity> LIVE = new ArrayList<>();

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof HostileEntity mob)) return;
            Edition ed = editionOf(mob);
            if (ed != null) { LIVE.add(mob); return; }                 // элитный из сохранения — снова искрит
            if (mob.getCommandTags().contains(CHECKED) || mob.age > 5) return;
            mob.addCommandTag(CHECKED);
            if (EXCLUDED.contains(mob.getType()) || mob.getCommandTags().contains("cr_spawner")) return;
            double ch = CycleManager.phase() == CyclePhase.EVENT ? CHANCE_EVENT : CHANCE_IDLE;
            if (ThreadLocalRandom.current().nextDouble() < ch) make(mob, roll());
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            Edition ed = editionOf(entity);
            if (ed == null) return;
            LIVE.remove(entity);
            if (!(source.getAttacker() instanceof ServerPlayerEntity p)) return;
            drops(p, entity, ed);
        });
    }

    /** Случайное издание: фольга чаще всего, полихром редко */
    public static Edition roll() {
        double r = ThreadLocalRandom.current().nextDouble();
        return r < 0.60 ? Edition.FOIL : r < 0.90 ? Edition.HOLO : Edition.POLY;
    }

    public static Edition editionOf(Entity e) {
        for (String t : e.getCommandTags())
            if (t.startsWith(TAG)) for (Edition ed : Edition.values()) if (ed.id.equals(t.substring(TAG.length()))) return ed;
        return null;
    }

    public static double scoreMult(Entity e) {
        Edition ed = editionOf(e);
        return ed == null ? 1.0 : ed.scoreMult;
    }

    public static String label(Entity e) {
        Edition ed = editionOf(e);
        return ed == null ? "" : ed.label;
    }

    /** Сделать моба элитным */
    public static void make(HostileEntity mob, Edition ed) {
        if (editionOf(mob) != null) return;
        mob.addCommandTag(CHECKED);
        mob.addCommandTag(TAG + ed.id);
        modifier(mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH), "health", ed.healthBonus);
        modifier(mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED), "speed", ed.speedBonus);
        modifier(mob.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE), "damage", ed.damageBonus);
        if (ed == Edition.POLY) {
            modifier(mob.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE), "knockback", 0.5);
            mob.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, -1, 0, false, false));
        }
        mob.setHealth(mob.getMaxHealth());
        MutableText name = Text.literal("✦ " + ed.label + " ✦ ").formatted(ed.color, Formatting.BOLD)
            .append(mob.getType().getName().copy().formatted(Formatting.WHITE));
        mob.setCustomName(name);
        mob.setCustomNameVisible(true);
        LIVE.add(mob);
    }

    private static void modifier(EntityAttributeInstance inst, String key, double value) {
        if (inst == null || value <= 0) return;
        inst.addPersistentModifier(new EntityAttributeModifier(Identifier.of(ComboRush.MOD_ID, "elite_" + key), value,
            EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    private static void drops(ServerPlayerEntity p, LivingEntity e, Edition ed) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        switch (ed) {
            case FOIL -> {
                e.dropStack(new ItemStack(Items.EMERALD, 1 + r.nextInt(3)));
                if (r.nextDouble() < 0.10) e.dropStack(new ItemStack(ModItems.CASES.get("common")));
            }
            case HOLO -> {
                e.dropStack(new ItemStack(Items.EMERALD, 2 + r.nextInt(3)));
                e.dropStack(new ItemStack(Items.DIAMOND, 1));
                if (r.nextDouble() < 0.15) e.dropStack(new ItemStack(ModItems.CASES.get("common")));
                if (r.nextDouble() < 0.05) e.dropStack(new ItemStack(ModItems.CASES.get("rare")));
            }
            case POLY -> {
                e.dropStack(new ItemStack(Items.DIAMOND, 2 + r.nextInt(2)));
                e.dropStack(new ItemStack(Items.EMERALD, 4 + r.nextInt(4)));
                Item c = r.nextDouble() < 0.2 ? ModItems.CASES.get("epic") : ModItems.CASES.get("rare");
                if (r.nextDouble() < 0.35) e.dropStack(new ItemStack(c));
                p.getServer().getPlayerManager().broadcast(Text.literal("§6" + p.getName().getString()
                    + " §fодолел §6§lполихромного §f" + e.getType().getName().getString() + "!"), false);
            }
        }
        if (e.getWorld() instanceof ServerWorld w) {
            w.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, e.getX(), e.getBodyY(0.5), e.getZ(), 30, 0.4, 0.6, 0.4, 0.3);
            w.playSound(null, e.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.HOSTILE, 0.8f, 1.4f);
        }
    }

    /** Искры вокруг элитных мобов */
    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 8 != 0 || LIVE.isEmpty()) return;
        for (Iterator<LivingEntity> it = LIVE.iterator(); it.hasNext(); ) {
            LivingEntity e = it.next();
            if (e.isRemoved() || !e.isAlive()) { it.remove(); continue; }
            if (!(e.getWorld() instanceof ServerWorld w)) continue;
            Edition ed = editionOf(e);
            if (ed == null) { it.remove(); continue; }
            ParticleEffect pt = switch (ed) {
                case FOIL -> ParticleTypes.ELECTRIC_SPARK;
                case HOLO -> ParticleTypes.WITCH;
                case POLY -> server.getTicks() % 16 == 0 ? ParticleTypes.TOTEM_OF_UNDYING : ParticleTypes.END_ROD;
            };
            w.spawnParticles(pt, e.getX(), e.getBodyY(0.6), e.getZ(), ed == Edition.POLY ? 3 : 2,
                e.getWidth() * 0.5, e.getHeight() * 0.35, e.getWidth() * 0.5, 0.02);
        }
    }
}
