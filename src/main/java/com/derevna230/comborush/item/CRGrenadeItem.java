package com.derevna230.comborush.item;

import com.derevna230.comborush.ComboRush;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
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
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Гранаты: осколочная, светошумовая, дымовая и коктейль Молотова. ЛКМ — бросок, ПКМ — бросок вполсилы (как в CS).
 * Клиент шлёт gun_throw (a = 1 — сильный бросок) и проигрывает анимацию; сервер через 7 тиков (на моменте «отпустил» в анимации)
 * создаёт {@link GrenadeEntity} и забирает одну гранату. Если игрок за это время сменил предмет — бросок отменяется.
 */
public class CRGrenadeItem extends Item implements GeoItem {
    public enum Kind {
        HE("grenade_he", 32), FLASH("grenade_flash", 30), SMOKE("grenade_smoke", 28), MOLOTOV("grenade_molotov", 70);
        public final String id;
        /** Тиков до взрыва (у «молотова» — запас: он разбивается при ударе о землю или о цель) */
        public final int fuse;
        Kind(String id, int fuse) { this.id = id; this.fuse = fuse; }
    }

    public static final int THROW_DELAY = 7;

    private final Kind kind;
    /** id предмета: у обычной гранаты совпадает с kind.id, у скина — свой (текстура другая, модель и анимации общие) */
    private final String itemId;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public CRGrenadeItem(Kind kind, Settings settings, String itemId) {
        super(settings);
        this.kind = kind;
        this.itemId = itemId;
        GeckoLibUtil.registerSyncedAnimatable(this);
    }

    public Kind kind() { return kind; }
    public String itemId() { return itemId; }
    /** Номер скина для летящей гранаты (0 — обычная) */
    public int skinIndex() { return GunDefs.grenadeSkinIds().indexOf(itemId) + 1; }

    /** Изменение числа гранат в стеке не должно давать анимацию «заново достаёт» */
    public boolean allowComponentsUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack, ItemStack newStack) { return false; }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return TypedActionResult.fail(user.getStackInHand(hand));       // бросок ведёт клиент пакетом gun_throw
    }

    // ---------------- бросок (сервер) ----------------
    private record Pend(long at, boolean hard, CRGrenadeItem item) {}
    private static final Map<UUID, Pend> PENDING = new HashMap<>();
    private static final Map<UUID, Long> NEXT = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(CRGrenadeItem::tick);
    }

    public static void forget(UUID id) { PENDING.remove(id); NEXT.remove(id); }

    /** Действие с клиента (WeaponKeys): gun_throw */
    public void keyAction(ServerPlayerEntity p, ItemStack stack, String act, int a) {
        if (!act.equals("gun_throw")) return;
        long now = p.getServer().getTicks();
        if (PENDING.containsKey(p.getUuid()) || NEXT.getOrDefault(p.getUuid(), 0L) > now) return;
        PENDING.put(p.getUuid(), new Pend(now + THROW_DELAY, a == 1, this));
        NEXT.put(p.getUuid(), now + 22);
        p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.PLAYERS, 0.5f, 1.7f);
    }

    private static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        long now = server.getTicks();
        for (Iterator<Map.Entry<UUID, Pend>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Pend> e = it.next();
            Pend pd = e.getValue();
            if (pd.at() > now) continue;
            it.remove();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
            if (p == null || !p.isAlive()) continue;
            ItemStack st = p.getMainHandStack();
            if (st.getItem() != pd.item()) continue;                    // сменил предмет — бросок сорван
            throwGrenade(p, pd.item().kind, pd.hard(), pd.item().skinIndex());
            if (!p.getAbilities().creativeMode) st.decrement(1);
        }
    }

    private static void throwGrenade(ServerPlayerEntity p, Kind kind, boolean hard, int skin) {
        ServerWorld w = p.getServerWorld();
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1f);
        Vec3d start = eye.add(look.multiply(0.55)).add(0, -0.22, 0);
        BlockHitResult bh = w.raycast(new RaycastContext(eye, start, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        if (bh.getType() != HitResult.Type.MISS) start = eye.add(look.multiply(0.1));        // бросок в упор в стену — граната падает у ног
        Vec3d dir = look.add(0, hard ? 0.14 : 0.3, 0).normalize();
        double sp = hard ? 1.15 : 0.55;
        Vec3d vel = dir.multiply(sp).add((p.getX() - p.prevX) * 0.5, 0, (p.getZ() - p.prevZ) * 0.5);
        GrenadeEntity g = new GrenadeEntity(ModEntitiesRef.type(), w);
        g.setup(kind, p.getUuid(), skin);
        g.setPosition(start.x, start.y - 0.12, start.z);
        g.setVelocity(vel);
        w.spawnEntity(g);
        w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.8f, hard ? 0.7f : 0.9f);
    }

    /** Ссылка на тип сущности без жёсткой зависимости при загрузке класса предмета */
    private static final class ModEntitiesRef {
        static net.minecraft.entity.EntityType<GrenadeEntity> type() { return com.derevna230.comborush.pet.ModEntities.GRENADE; }
    }

    // ---------------- описание ----------------
    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        switch (kind) {
            case HE -> {
                tooltip.add(Text.literal("§7Взрывается через §f1,6 с§7 после броска."));
                tooltip.add(Text.literal("§7До §f18 урона§7 в радиусе §f7 блоков§7, стены защищают."));
            }
            case FLASH -> {
                tooltip.add(Text.literal("§7Вспышка через §f1,5 с§7: игроки, смотрящие на неё, слепнут"));
                tooltip.add(Text.literal("§7до §f4,5 с§7 (белый экран). Мобы теряют цель."));
            }
            case SMOKE -> {
                tooltip.add(Text.literal("§7Через §f1,4 с§7 даёт плотное облако на §f15 с§7."));
                tooltip.add(Text.literal("§7Закрывает обзор и тушит огонь (в том числе «молотов»)."));
            }
            case MOLOTOV -> {
                tooltip.add(Text.literal("§7Разбивается при ударе о землю или цель."));
                tooltip.add(Text.literal("§7Огонь радиусом §f3 блока§7 горит §f7 с§7 и жжёт всех внутри."));
            }
        }
        for (GunDefs.Skin sk : GunDefs.SKINS) if (sk.id().equals(itemId)) { tooltip.add(Text.literal(GunDefs.rarityName(sk.rarity()))); break; }
        tooltip.add(Text.literal("§8ЛКМ — бросок, ПКМ — бросок вполсилы, V — осмотр."));
    }

    // ---------------- модель и анимации ----------------
    private String anim(String n) { return "animation." + kind.id + "." + n; }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        String id = itemId;
        String baseId = kind.id;
        consumer.accept(new GeoRenderProvider() {
            private GeoItemRenderer<CRGrenadeItem> renderer;

            @Override
            public BuiltinModelItemRenderer getGeoItemRenderer() {
                if (this.renderer == null) this.renderer = new GeoItemRenderer<CRGrenadeItem>(new GunGeoModel<CRGrenadeItem>(baseId, id));
                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<CRGrenadeItem> c = new AnimationController<>(this, "main", 0, state -> PlayState.STOP);
        for (String n : new String[]{"draw", "throw", "inspect"})
            c.triggerableAnim(n, RawAnimation.begin().thenPlay(anim(n)));
        controllers.add(c);
    }

    /** Проиграть анимацию у владельца (клиент) — по номеру GeckoLib из стека, как у стволов */
    public boolean playLocal(ItemStack stack, String name) {
        long id = GeoItem.getId(stack);
        try {
            var mgr = getAnimatableInstanceCache().getManagerForId(id);
            if (mgr == null) return false;
            mgr.tryTriggerAnimation("main", name);
            return true;
        } catch (RuntimeException e) { return false; }
    }

    /** Сервер: назначает номер GeckoLib (без него клиент не отличит одну гранату от другой) */
    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);
        if (world instanceof ServerWorld sw && entity instanceof ServerPlayerEntity) GeoItem.getOrAssignId(stack, sw);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
