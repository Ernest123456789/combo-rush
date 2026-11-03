package com.derevna230.comborush.item;

import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

import java.util.List;

/** Энергетик «Неон»: скорость II, прыгучесть и спешка на минуту */
public class EnergyDrinkItem extends Item {
    public EnergyDrinkItem(Settings settings) { super(settings); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return ItemUsage.consumeHeldItem(world, user, hand);
    }

    @Override public UseAction getUseAction(ItemStack stack) { return UseAction.DRINK; }
    @Override public int getMaxUseTime(ItemStack stack, LivingEntity user) { return 28; }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (!world.isClient) {
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 60, 1));
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 20 * 60, 0));
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 20 * 60, 0));
            if (user instanceof ServerPlayerEntity sp) Criteria.CONSUME_ITEM.trigger(sp, stack);
        }
        if (!(user instanceof PlayerEntity p) || !p.isCreative()) stack.decrement(1);
        return stack;
    }

    @Override
    public boolean hasGlint(ItemStack stack) { return true; }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("§9Скорость II (1:00)"));
        tooltip.add(Text.literal("§9Прыгучесть (1:00)"));
        tooltip.add(Text.literal("§9Спешка (1:00)"));
        tooltip.add(Text.literal("§7Горит неоном и бьёт по вкусовым рецепторам"));
    }
}
