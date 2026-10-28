package com.derevna230.comborush.campaign;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/** Пропуск участника — он же журнал «Сценарий». ПКМ — открыть */
public class PassItem extends Item {
    public PassItem(Settings settings) { super(settings); }

    @Override public boolean hasGlint(ItemStack stack) { return true; }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient && user instanceof ServerPlayerEntity sp) Campaign.openJournal(sp);
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("§7Выдан Билетёром шоу «Combo Rush»"));
        tooltip.add(Text.literal("§eПКМ — журнал «Сценарий» §7(или клавиша J)"));
    }
}
