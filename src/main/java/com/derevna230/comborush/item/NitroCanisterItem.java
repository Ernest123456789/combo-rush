package com.derevna230.comborush.item;

import com.derevna230.comborush.car.CarEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/** Нитро-баллон: ПКМ за рулём машины — полностью заправляет нитро */
public class NitroCanisterItem extends Item {
    public NitroCanisterItem(Settings settings) { super(settings); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!(user.getVehicle() instanceof CarEntity car)) {
            if (!world.isClient) user.sendMessage(Text.literal("§cСядь за руль машины, чтобы заправить нитро."), true);
            return TypedActionResult.fail(stack);
        }
        if (!world.isClient) {
            car.refillNitro();
            if (!user.isCreative()) stack.decrement(1);
            world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.9f, 1.7f);
            user.sendMessage(Text.literal("§bНитро заправлено до краёв!"), true);
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("§7Сжатая закись азота в неоновой колбе."));
        tooltip.add(Text.literal("§bПКМ за рулём — полностью заправить нитро"));
    }
}
