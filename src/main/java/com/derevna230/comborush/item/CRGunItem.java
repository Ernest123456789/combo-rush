package com.derevna230.comborush.item;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.armor.ArmorTooltips;
import com.derevna230.comborush.pet.CRPetEntity;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Огнестрел: ПКМ — мгновенный выстрел лучом (без патронов, с перезарядкой).
 * Дробь — несколько лучей с разбросом; урон по одной цели суммируется в один удар.
 */
public class CRGunItem extends Item implements GeoItem {
    private final String setId;
    private final float damage;
    private final int pellets;
    private final double spread;
    private final int cooldown;
    private final double range;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Новые стволы (Arsenal): модель, анимации и глушитель. null — старое оружие без анимаций */
    private final GunDefs.Gun def;
    private final String base;
    /** Кто какой ствол держит (id стека) — чтобы проигрывать «доставание» только при смене предмета */
    private static final Map<UUID, Long> HELD = new HashMap<>();

    public CRGunItem(String setId, Settings settings, float damage, int pellets, double spread, int cooldownTicks, double range) {
        this(setId, settings, damage, pellets, spread, cooldownTicks, range, null);
    }

    /** Ствол из GunDefs; skin-предметы создаются с тем же def (характеристики ствола), но своим setId */
    public CRGunItem(String setId, Settings settings, GunDefs.Gun g) {
        this(setId, settings, g.damage(), g.pellets(), g.spread(), g.cooldown(), g.range(), g);
    }

    private CRGunItem(String setId, Settings settings, float damage, int pellets, double spread, int cooldownTicks, double range, GunDefs.Gun def) {
        super(settings);
        this.setId = setId;
        this.damage = damage;
        this.pellets = pellets;
        this.spread = spread;
        this.cooldown = cooldownTicks;
        this.range = range;
        this.def = def;
        this.base = GunDefs.baseOf(setId);
        if (def != null) GeckoLibUtil.registerSyncedAnimatable(this);
    }

    public String setId() { return setId; }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity p) {
            boolean off = silencerOff(stack);
            boolean silenced = def != null && def.silenceable() && !off;
            shoot(p, def != null && def.silenceable() && off ? damage * 1.15f : damage, silenced);
            play(p, stack, "shoot");
            p.getItemCooldownManager().set(this, cooldown);
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    private void shoot(ServerPlayerEntity p, float damage, boolean silenced) {
        ServerWorld w = p.getServerWorld();
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1f);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Map<LivingEntity, Float> hits = new LinkedHashMap<>();

        for (int i = 0; i < pellets; i++) {
            Vec3d dir = spread <= 0 ? look : look.add(rnd.nextGaussian() * spread, rnd.nextGaussian() * spread, rnd.nextGaussian() * spread).normalize();
            Vec3d end = eye.add(dir.multiply(range));
            BlockHitResult bh = w.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
            Vec3d stop = bh.getType() == HitResult.Type.MISS ? end : bh.getPos();
            EntityHitResult eh = ProjectileUtil.raycast(p, eye, stop, p.getBoundingBox().stretch(dir.multiply(range)).expand(1.0),
                e -> e instanceof LivingEntity && e.isAlive() && e != p && !(e instanceof CRPetEntity) && !e.isSpectator(),
                eye.squaredDistanceTo(stop));
            Vec3d hitPos = eh != null ? eh.getPos() : stop;
            double dist = eye.distanceTo(hitPos);
            for (double t = 1.2; t < dist; t += 1.4) {
                Vec3d q = eye.add(dir.multiply(t));
                w.spawnParticles(ParticleTypes.SMOKE, q.x, q.y - 0.1, q.z, 1, 0, 0, 0, 0);
            }
            if (eh != null) {
                Entity e = eh.getEntity();
                hits.merge((LivingEntity) e, damage, Float::sum);
                w.spawnParticles(ParticleTypes.CRIT, hitPos.x, hitPos.y, hitPos.z, 6, 0.1, 0.1, 0.1, 0.2);
            } else if (bh.getType() != HitResult.Type.MISS) {
                w.spawnParticles(ParticleTypes.POOF, stop.x, stop.y, stop.z, 2, 0.05, 0.05, 0.05, 0.01);
            }
        }
        for (Map.Entry<LivingEntity, Float> h : hits.entrySet()) {
            LivingEntity e = h.getKey();
            e.timeUntilRegen = 0;
            e.damage(p.getDamageSources().playerAttack(p), h.getValue());
        }

        Vec3d m = eye.add(look.multiply(1.1));
        if (!silenced) w.spawnParticles(ParticleTypes.FLAME, m.x, m.y - 0.15, m.z, 3, 0.03, 0.03, 0.03, 0.02);
        w.spawnParticles(ParticleTypes.LARGE_SMOKE, m.x, m.y - 0.15, m.z, silenced ? 1 : 2, 0.05, 0.05, 0.05, 0.01);
        if (pellets > 1) {
            w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.PLAYERS, 1.6f, 0.6f);
            w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.35f, 1.9f);
            p.addVelocity(-look.x * 0.35, 0.05, -look.z * 0.35);       // отдача
            p.velocityModified = true;
        } else {
            float v = silenced ? 0.18f : 1f;
            w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.6f * v, silenced ? 2.0f : 1.6f);
            w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 1.2f * v, silenced ? 1.2f : 0.5f);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        String dmg = pellets > 1 ? pellets + "×" + fmt(damage) : fmt(damage);
        tooltip.add(Text.literal("§7ПКМ — выстрел: §f" + dmg + " урона§7, дальность " + (int) range));
        tooltip.add(Text.literal("§7Перезарядка " + fmt(cooldown / 20f) + " с, патроны не нужны"));
        if (def != null) {
            if (def.silenceable()) tooltip.add(Text.literal("§7Глушитель (G): " + (silencerOff(stack) ? "§cснят §7(+15% урона, шумно)" : "§aустановлен §7(тихо)")));
            tooltip.add(Text.literal("§8R — перезарядка (анимация), V — осмотр"));
        }
        if (GunDefs.isSkin(setId)) {
            for (GunDefs.Skin sk : GunDefs.SKINS) if (sk.id().equals(setId)) { tooltip.add(Text.literal(GunDefs.rarityName(sk.rarity()))); break; }
        }
        ArmorTooltips.append(setId, tooltip);
    }

    private static String fmt(float v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        String id = setId;
        consumer.accept(new GeoRenderProvider() {
            private GeoItemRenderer<CRGunItem> renderer;

            @Override
            public BuiltinModelItemRenderer getGeoItemRenderer() {
                if (this.renderer == null) {
                    GeoModel<CRGunItem> model = def != null
                        ? new GunGeoModel<>(base, id)
                        : new DefaultedItemGeoModel<>(Identifier.of(ComboRush.MOD_ID, "weapon/" + id));
                    this.renderer = new GeoItemRenderer<>(model);
                }
                return this.renderer;
            }
        });
    }

    // ---------------- анимации ----------------
    private String anim(String name) { return "animation." + base + "." + name; }

    private static boolean silencerOff(ItemStack stack) {
        NbtComponent c = stack.get(DataComponentTypes.CUSTOM_DATA);
        return c != null && c.copyNbt().getBoolean("nosil");
    }

    /** Имя анимации с учётом глушителя: без него — вариант *_ns (кость глушителя скрыта) */
    private String variant(ItemStack stack, String name) {
        return def != null && def.silenceable() && silencerOff(stack) ? name + "_ns" : name;
    }

    private void play(ServerPlayerEntity p, ItemStack stack, String name) {
        if (def == null || !(p.getWorld() instanceof ServerWorld sw)) return;
        triggerAnim(p, GeoItem.getOrAssignId(stack, sw), "main", variant(stack, name));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        if (def == null) return;
        AnimationController<CRGunItem> c = new AnimationController<>(this, "main", 0, state -> {
            if (def.silenceable()) {
                ItemStack st = state.getData(DataTickets.ITEMSTACK);
                if (st != null && silencerOff(st)) {
                    state.setAndContinue(RawAnimation.begin().thenLoop(anim("idle_ns")));
                    return PlayState.CONTINUE;
                }
            }
            return PlayState.STOP;
        });
        for (String n : new String[]{"draw", "shoot", "reload", "inspect"}) {
            c.triggerableAnim(n, RawAnimation.begin().thenPlay(anim(n)));
            if (def.silenceable()) c.triggerableAnim(n + "_ns", RawAnimation.begin().thenPlay(anim(n + "_ns")));
        }
        if (def.silenceable()) {
            c.triggerableAnim("sil_off", RawAnimation.begin().thenPlay(anim("sil_off")));
            c.triggerableAnim("sil_on", RawAnimation.begin().thenPlay(anim("sil_on")));
        }
        controllers.add(c);
    }

    /** «Доставание» при выборе ствола в руку */
    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);
        if (def == null || world.isClient || !(entity instanceof ServerPlayerEntity p) || !(world instanceof ServerWorld sw)) return;
        long id = GeoItem.getOrAssignId(stack, sw);
        if (selected) {
            Long last = HELD.get(p.getUuid());
            if (last == null || last != id) {
                HELD.put(p.getUuid(), id);
                play(p, stack, "draw");
            }
        } else {
            HELD.remove(p.getUuid(), id);
        }
    }

    /** Клавиши R / V / G (приходят с клиента пакетом gun_*) */
    public void keyAction(ServerPlayerEntity p, ItemStack stack, String act) {
        if (def == null || p.getItemCooldownManager().isCoolingDown(this)) return;
        ServerWorld w = p.getServerWorld();
        switch (act) {
            case "gun_reload" -> {
                play(p, stack, "reload");
                p.getItemCooldownManager().set(this, def.reloadTicks());
                w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.PLAYERS, 0.5f, 1.6f);
            }
            case "gun_inspect" -> play(p, stack, "inspect");
            case "gun_silencer" -> {
                if (!def.silenceable()) return;
                NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
                boolean nowOff = !nbt.getBoolean("nosil");
                if (nowOff) nbt.putBoolean("nosil", true); else nbt.remove("nosil");
                stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
                if (!(p.getWorld() instanceof ServerWorld sw)) return;
                triggerAnim(p, GeoItem.getOrAssignId(stack, sw), "main", nowOff ? "sil_off" : "sil_on");
                p.getItemCooldownManager().set(this, 26);
                w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.PLAYERS, 0.6f, 1.3f);
            }
            default -> { }
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
