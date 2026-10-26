package com.derevna230.comborush.item;

import com.derevna230.comborush.armor.ArmorTooltips;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.List;

/** Лук из кейса. Стрелы помечаются при выстреле (WeaponAbilities), способности срабатывают при попадании */
public class CRBowItem extends BowItem {
    private final String setId;

    public CRBowItem(String setId, Settings settings) {
        super(settings);
        this.setId = setId;
    }

    public String setId() { return setId; }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        ArmorTooltips.append(setId, tooltip);
    }
}
