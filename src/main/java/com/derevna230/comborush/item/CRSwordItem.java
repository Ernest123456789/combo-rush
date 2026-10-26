package com.derevna230.comborush.item;

import com.derevna230.comborush.armor.ArmorTooltips;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.List;

/** Меч из кейса с обычной моделью предмета. Способности — в data/<modid>/weapons/<id>.json */
public class CRSwordItem extends SwordItem {
    private final String setId;

    public CRSwordItem(String setId, Settings settings) {
        super(ToolMaterials.NETHERITE, settings);
        this.setId = setId;
    }

    public String setId() { return setId; }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        ArmorTooltips.append(setId, tooltip);
    }
}
