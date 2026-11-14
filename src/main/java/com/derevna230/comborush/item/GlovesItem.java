package com.derevna230.comborush.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.List;

/** Перчатки «Противостояния»: надеваются в «Снаряжении» → «Перчатки», видны на руках от первого лица */
public class GlovesItem extends Item {
    private final String id;

    public GlovesItem(String id, Settings settings) { super(settings); this.id = id; }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        String r = SkinData.rarityOf(id);
        if (!r.isEmpty()) tooltip.add(Text.literal(GunDefs.rarityName(r)));
        SkinData.tooltip(stack, tooltip);
        tooltip.add(Text.literal("§7Перчатки: надеть — «Противостояние» → «Снаряжение» → «Перчатки»"));
        tooltip.add(Text.literal("§8Видны на руках от первого лица со стволом, ножом и гранатой"));
    }
}
