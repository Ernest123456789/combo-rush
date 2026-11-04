package com.derevna230.comborush.item;

import com.derevna230.comborush.armor.ArmorTooltips;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Нож-коготь (керамбит) и его скины. Модель/анимации общие (geo/item/weapon/claw), текстура у скина своя.
 * Анимации: draw (при выборе), attack / attack2 (замах, чередуются), inspect (клавиша V).
 */
public class CRKnifeItem extends CRSwordItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final Map<UUID, Long> HELD = new HashMap<>();
    private static int lastSwing = -100;
    private static boolean flip;
    private final String base;

    public CRKnifeItem(String setId, Settings settings) {
        super(setId, settings);
        this.base = GunDefs.baseOf(setId);
        GeckoLibUtil.registerSyncedAnimatable(this);
    }

    private String anim(String n) { return "animation." + base + "." + n; }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        String id = setId();
        consumer.accept(new GeoRenderProvider() {
            private GeoItemRenderer<CRKnifeItem> renderer;

            @Override
            public BuiltinModelItemRenderer getGeoItemRenderer() {
                if (this.renderer == null) this.renderer = new GeoItemRenderer<>(new GunGeoModel<>(base, id));
                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<CRKnifeItem> c = new AnimationController<>(this, "main", 0, state -> PlayState.STOP);
        for (String n : new String[]{"draw", "attack", "attack2", "inspect"})
            c.triggerableAnim(n, RawAnimation.begin().thenPlay(anim(n)));
        controllers.add(c);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);
        if (world.isClient) {
            // замах виден только владельцу: GeckoLib сама рассылает серверные триггеры, а замах мы ловим по взмаху руки
            if (selected && entity instanceof PlayerEntity pl && pl.handSwinging && pl.handSwingTicks <= 0
                && pl.preferredHand == Hand.MAIN_HAND && pl.age - lastSwing > 5) {
                long id = GeoItem.getId(stack);
                if (id != -1) {
                    lastSwing = pl.age;
                    flip = !flip;
                    triggerAnim(pl, id, "main", flip ? "attack" : "attack2");
                }
            }
            return;
        }
        if (!(entity instanceof ServerPlayerEntity p) || !(world instanceof ServerWorld sw)) return;
        long id = GeoItem.getOrAssignId(stack, sw);
        if (selected) {
            Long last = HELD.get(p.getUuid());
            if (last == null || last != id) {
                HELD.put(p.getUuid(), id);
                triggerAnim(p, id, "main", "draw");
            }
        } else {
            HELD.remove(p.getUuid(), id);
        }
    }

    public void keyAction(ServerPlayerEntity p, ItemStack stack, String act) {
        if (!act.equals("gun_inspect") || !(p.getWorld() instanceof ServerWorld sw)) return;
        triggerAnim(p, GeoItem.getOrAssignId(stack, sw), "main", "inspect");
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (GunDefs.Skin sk : GunDefs.SKINS)
            if (sk.id().equals(setId())) { String rn = GunDefs.rarityName(sk.rarity()); if (!rn.isEmpty()) tooltip.add(Text.literal(rn)); break; }
        if (setId().equals(GunDefs.KNIFE)) tooltip.add(Text.literal(GunDefs.rarityName("mythic")));
        tooltip.add(Text.literal("§8V — осмотр"));
        ArmorTooltips.append(setId(), tooltip);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
