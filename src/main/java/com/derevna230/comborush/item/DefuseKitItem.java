package com.derevna230.comborush.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.List;

/** Набор для обезвреживания: достаточно носить в инвентаре — бомба обезвреживается за 5 с вместо 10 */
public class DefuseKitItem extends Item {
    public DefuseKitItem(Settings settings) { super(settings); }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("§7Лежит в инвентаре — бомба обезвреживается за §a5 с§7 вместо 10."));
        tooltip.add(Text.literal("§7Обезвреживание: удерживать §fПКМ§7 на бомбе."));
    }
}
