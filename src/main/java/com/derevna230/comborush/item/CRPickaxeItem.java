package com.derevna230.comborush.item;

import com.derevna230.comborush.armor.ArmorTooltips;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.List;

/** Кирка из кейса: копает как незеритовая */
public class CRPickaxeItem extends PickaxeItem {
    private final String setId;

    public CRPickaxeItem(String setId, Settings settings) {
        super(ToolMaterials.NETHERITE, settings);
        this.setId = setId;
    }

    public String setId() { return setId; }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        ArmorTooltips.append(setId, tooltip);
    }
}
