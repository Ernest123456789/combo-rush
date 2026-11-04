package com.derevna230.comborush.item;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.armor.ArmorTooltips;
import com.derevna230.comborush.pet.CRPetEntity;
import com.derevna230.comborush.hud.HudPayload;
import com.derevna230.comborush.hud.HudSync;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
 * Огнестрел. Стволы «Арсенала» (def != null): ЛКМ — выстрел (клиент шлёт gun_fire), ПКМ — прицел или глушитель, R — перезарядка, V — осмотр;
 * магазин, темп стрельбы, разброс от движения и длины очереди, отдача камеры, попадание в голову ×2. Анимации запускает клиент
 * (GeckoLib шлёт серверные триггеры по имени класса, а у нас много предметов одного класса — они бы попадали не в тот ствол).
 * Старое оружие (def == null): ПКМ — выстрел лучом, как раньше.
 */
public class CRGunItem extends Item implements GeoItem {
    private final String setId;
    private final float damage;
    private final int pellets;
    private final double spread;
    private final double cooldown;
    private final double range;
    private static final net.minecraft.particle.DustParticleEffect TRACER = new net.minecraft.particle.DustParticleEffect(new org.joml.Vector3f(1.0f, 0.82f, 0.35f), 0.5f);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Новые стволы (Arsenal): модель, анимации и глушитель. null — старое оружие без анимаций */
    private final GunDefs.Gun def;
    private final String base;
    /** Кто какой ствол держит (id стека) — чтобы проигрывать «доставание» только при смене предмета */
    private static final Map<UUID, Long> HELD = new HashMap<>();

    public CRGunItem(String setId, Settings settings, float damage, int pellets, double spread, int cooldownTicks, double range) {
        this(setId, settings, damage, pellets, spread, (double) cooldownTicks, range, null);
    }

    /** Ствол из GunDefs; skin-предметы создаются с тем же def (характеристики ствола), но своим setId */
    public CRGunItem(String setId, Settings settings, GunDefs.Gun g) {
        this(setId, settings, g.damage(), g.pellets(), g.spread(), g.cooldown(), g.range(), g);
    }

    private CRGunItem(String setId, Settings settings, float damage, int pellets, double spread, double cooldownTicks, double range, GunDefs.Gun def) {
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
    /** Ствол «Арсенала» с магазином и новым управлением */
    public boolean modern() { return def != null; }
    public GunDefs.Gun def() { return def; }

    /** Изменение NBT предмета в руке (патроны, глушитель) не должно давать анимацию «заново достаёт» */
    public boolean allowComponentsUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack, ItemStack newStack) {
        return def == null;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (def != null) return TypedActionResult.fail(stack);          // ПКМ у новых стволов — прицел/глушитель, их ведёт клиент
        if (!world.isClient && user instanceof ServerPlayerEntity p) {
            shoot(p, damage, false, 0.0, false);
            p.getItemCooldownManager().set(this, (int) cooldown);
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    // ---------------- состояние стрелка на сервере ----------------
    private static final class St { double nextShot, bloom; long bloomTick, reloadUntil, reloadId, busyUntil; }
    private static final Map<UUID, St> STATE = new HashMap<>();

    private static St st(ServerPlayerEntity p) { return STATE.computeIfAbsent(p.getUuid(), k -> new St()); }

    public static void forget(UUID id) { STATE.remove(id); }

    public int ammo(ItemStack stack) {
        NbtComponent c = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (c == null) return def.mag();
        NbtCompound n = c.copyNbt();
        return n.contains("ammo") ? n.getInt("ammo") : def.mag();
    }

    private void setAmmo(ItemStack stack, int v) { NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, n -> n.putInt("ammo", v)); }

    private static long idOf(ItemStack stack) {
        NbtComponent c = stack.get(DataComponentTypes.CUSTOM_DATA);
        return c == null ? 0L : c.copyNbt().getLong("GeckoLibID");
    }

    /** Выстрел: клиент прислал gun_fire (scoped — игрок смотрит в прицел) */
    public void fire(ServerPlayerEntity p, ItemStack stack, boolean scoped) {
        if (def == null) return;
        long now = p.getServer().getTicks();
        St s = st(p);
        if (s.reloadUntil > now || now < s.busyUntil || now + 1 < s.nextShot) return;
        int ammo = ammo(stack);
        if (ammo <= 0) { startReload(p, stack, s, now); return; }
        s.nextShot = Math.max(s.nextShot, now) + def.cooldown();
        s.bloom = Math.max(0, s.bloom - 0.045 * (now - s.bloomTick));
        s.bloomTick = now;

        double hv = Math.hypot(p.getX() - p.prevX, p.getZ() - p.prevZ);          // блоков за тик
        double sp = def.spread() * (p.isSneaking() ? 0.6 : 1.0) + def.bloom() * s.bloom;
        double move = Math.min(0.06, hv * 0.3) + (p.isOnGround() ? 0 : 0.05);
        if (def.scope()) sp = scoped ? sp * 0 + move * 0.35 : 0.07 + move;           // AWP без прицела стреляет «от бедра»
        else sp += move;
        s.bloom = Math.min(1.0, s.bloom + 0.11);

        boolean off = silencerOff(stack);
        boolean silenced = def.silenceable() && !off;
        shoot(p, def.silenceable() && off ? damage * 1.15f : damage, silenced, sp, true);
        setAmmo(stack, ammo - 1);
        if (ammo - 1 <= 0) { p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.5f, 1.6f); }
    }

    private void startReload(ServerPlayerEntity p, ItemStack stack, St s, long now) {
        if (ammo(stack) >= def.mag() || s.reloadUntil > now) return;
        s.reloadUntil = now + def.reloadTicks();
        s.reloadId = idOf(stack);
        p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.PLAYERS, 0.5f, 1.6f);
    }

    /** Сколько тиков осталось до конца перезарядки (для клиентского HUD берём по пакету, тут — только сервер) */
    public static boolean reloading(ServerPlayerEntity p) { St s = STATE.get(p.getUuid()); return s != null && s.reloadUntil > p.getServer().getTicks(); }

    private void shoot(ServerPlayerEntity p, float damage, boolean silenced, double spreadNow, boolean modern) {
        ServerWorld w = p.getServerWorld();
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1f);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Map<LivingEntity, Float> hits = new LinkedHashMap<>();
        boolean head = false;
        double spread = modern ? spreadNow : this.spread;

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
            if (modern) {
                // трассер: короткие жёлтые искры вдоль луча
                for (double t = 2.5; t < dist; t += 2.2) {
                    Vec3d q = eye.add(dir.multiply(t));
                    w.spawnParticles(TRACER, q.x, q.y - 0.12, q.z, 1, 0, 0, 0, 0);
                }
            } else {
                for (double t = 1.2; t < dist; t += 1.4) {
                    Vec3d q = eye.add(dir.multiply(t));
                    w.spawnParticles(ParticleTypes.SMOKE, q.x, q.y - 0.1, q.z, 1, 0, 0, 0, 0);
                }
            }
            if (eh != null) {
                Entity e = eh.getEntity();
                boolean hs = modern && hitPos.y >= e.getY() + e.getHeight() * 0.8;
                head |= hs;
                hits.merge((LivingEntity) e, hs ? damage * 2f : damage, Float::sum);
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
        if (modern && !hits.isEmpty() && HudSync.has(p))
            ServerPlayNetworking.send(p, new HudPayload("gun_hit", head ? "{\"h\":1}" : "{\"h\":0}"));

        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d right = look.crossProduct(up).normalize();
        Vec3d m = eye.add(look.multiply(1.25)).add(right.multiply(0.22)).add(0, -0.16, 0);
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
        if (def == null) {
            tooltip.add(Text.literal("§7ПКМ — выстрел: §f" + dmg + " урона§7, дальность " + (int) range));
            tooltip.add(Text.literal("§7Перезарядка " + fmt((float) cooldown / 20f) + " с, патроны не нужны"));
        } else {
            tooltip.add(Text.literal("§7ЛКМ — " + (def.auto() ? "очередь" : "выстрел") + ": §f" + dmg + " урона§7, в голову ×2"));
            tooltip.add(Text.literal("§7Темп: §f" + fmt((float) (1200.0 / cooldown)) + " выстр./мин§7, магазин §f" + def.mag() + "§7 (запас без лимита)"));
            tooltip.add(Text.literal("§7Патроны: §f" + ammo(stack) + " / " + def.mag()));
            tooltip.add(Text.literal(def.scope() ? "§7ПКМ — оптический прицел (без него разброс огромный)" : def.silenceable() ? "§7ПКМ — снять / надеть глушитель" + (silencerOff(stack) ? ": §cснят §7(+15% урона, шумно)" : ": §aустановлен §7(тихо)") : "§7ПКМ — нет"));
            tooltip.add(Text.literal("§8R — перезарядка, V — осмотр. В движении и в прыжке разброс больше; присев — меньше."));
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

    /**
     * Проиграть анимацию у владельца (клиент). Идёт по менеджеру именно этого предмета с номером GeckoLib из стека.
     * Возвращает false, если номера ещё нет (сервер назначит его в ближайший тик).
     */
    public boolean playLocal(ItemStack stack, String name) {
        if (def == null) return false;
        long id = idOf(stack);
        if (id == 0L) return false;
        String n = name.startsWith("sil_") ? name : variant(stack, name);
        try {
            var mgr = getAnimatableInstanceCache().getManagerForId(id);
            return mgr != null && mgr.tryTriggerAnimation("main", n);
        } catch (RuntimeException e) { return false; }
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

    /** Сервер: назначает номер GeckoLib и завершает перезарядку */
    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);
        if (def == null || world.isClient || !(entity instanceof ServerPlayerEntity p) || !(world instanceof ServerWorld sw)) return;
        long id = GeoItem.getOrAssignId(stack, sw);
        St s = STATE.get(p.getUuid());
        if (s != null && s.reloadUntil > 0 && s.reloadId == id) {
            if (!selected) s.reloadUntil = 0;                           // убрали ствол — перезарядка сорвана
            else if (p.getServer().getTicks() >= s.reloadUntil) { setAmmo(stack, def.mag()); s.reloadUntil = 0; }
        }
    }

    /** Действия с клиента: gun_reload, gun_silencer (стрельба идёт через fire) */
    public void keyAction(ServerPlayerEntity p, ItemStack stack, String act, boolean scoped) {
        if (def == null) return;
        ServerWorld w = p.getServerWorld();
        long now = p.getServer().getTicks();
        St s = st(p);
        switch (act) {
            case "gun_fire" -> fire(p, stack, scoped);
            case "gun_reload" -> startReload(p, stack, s, now);
            case "gun_silencer" -> {
                if (!def.silenceable() || s.reloadUntil > now || now < s.busyUntil) return;
                NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> {
                    if (nbt.getBoolean("nosil")) nbt.remove("nosil"); else nbt.putBoolean("nosil", true);
                });
                s.busyUntil = now + 26;
                w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.PLAYERS, 0.6f, 1.3f);
            }
            default -> { }
        }
    }

    /** Предмет-вид снаряжения на ПКМ: что делает правая кнопка */
    public String secondary() { return def == null ? "" : def.scope() ? "scope" : def.silenceable() ? "silencer" : ""; }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
