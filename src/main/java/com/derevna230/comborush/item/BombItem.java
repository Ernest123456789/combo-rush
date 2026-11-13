package com.derevna230.comborush.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

import java.util.List;

/**
 * Бомба (C4). Закладывается удержанием ПКМ на земле ({@value #PLANT_TICKS} тиков = 3,2 с): по окончании под ногами появляется
 * {@link BombEntity} с таймером {@value BombEntity#FUSE_TICKS} тиков (40 с). Отпустил раньше — закладка отменяется.
 */
public class BombItem extends Item {
    public static final int PLANT_TICKS = 64;

    public BombItem(Settings settings) { super(settings); }

    /** На карте матча бомбу можно закладывать только на площадках A и B; в остальных мирах ограничений нет */
    private static boolean onSite(World world, LivingEntity user) {
        return !world.getRegistryKey().equals(com.derevna230.comborush.universe.Universes.DUST)
            || com.derevna230.comborush.universe.DustMap.siteAt(user.getX(), user.getZ()) != 0;
    }

    private static final String NOT_SITE = "§cБомбу можно закладывать только на точках §eA§c и §eB§c.";

    @Override public UseAction getUseAction(ItemStack stack) { return UseAction.NONE; }
    @Override public int getMaxUseTime(ItemStack stack, LivingEntity user) { return PLANT_TICKS; }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!user.isOnGround() || user.isTouchingWater() || user.hasVehicle()) {
            if (!world.isClient) user.sendMessage(Text.literal("§cБомбу можно закладывать только стоя на земле."), true);
            return TypedActionResult.fail(stack);
        }
        if (!onSite(world, user)) {
            if (!world.isClient) user.sendMessage(Text.literal(NOT_SITE), true);
            return TypedActionResult.fail(stack);
        }
        user.setCurrentHand(hand);
        if (!world.isClient) world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.PLAYERS, 0.8f, 1.2f);
        return TypedActionResult.consume(stack);
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (world.isClient || !(user instanceof ServerPlayerEntity p)) return;
        if (!onSite(world, p)) { p.stopUsingItem(); p.sendMessage(Text.literal(NOT_SITE), true); return; }   // вышел с площадки — закладка сорвана
        int done = PLANT_TICKS - remainingUseTicks;
        if (done % 4 == 0) p.sendMessage(Text.literal("§6Закладка бомбы §f" + BombEntity.bar(done / (float) PLANT_TICKS)), true);
        if (done % 16 == 0) world.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.PLAYERS, 0.6f, 1.4f);
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (world instanceof ServerWorld sw && user instanceof ServerPlayerEntity p) {
            if (!p.isOnGround()) { p.sendMessage(Text.literal("§cБомбу можно закладывать только стоя на земле."), true); return stack; }
            if (!onSite(world, p)) { p.sendMessage(Text.literal(NOT_SITE), true); return stack; }
            BombEntity.plant(sw, p);
            if (!p.isCreative()) stack.decrement(1);
        }
        return stack;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("§7Удерживайте §fПКМ§7 на земле 3,2 с — бомба заложена."));
        tooltip.add(Text.literal("§7На карте матча — только на точках §eA§7 и §eB§7."));
        tooltip.add(Text.literal("§7Взрыв через §c40 с§7, радиус §f14 блоков§7, стены ослабляют."));
        tooltip.add(Text.literal("§7Обезвредить: удерживать ПКМ на бомбе — §f5 с§7 с набором, §f10 с§7 без."));
    }
}
