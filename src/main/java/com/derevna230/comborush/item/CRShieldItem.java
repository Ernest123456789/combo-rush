package com.derevna230.comborush.item;

import com.derevna230.comborush.armor.ArmorTooltips;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.List;

/** Щит из кейса. Модель при блоке — models/item/<id>_blocking.json (предикат blocking регистрирует клиент) */
public class CRShieldItem extends ShieldItem {
    private final String setId;

    public CRShieldItem(String setId, Settings settings) {
        super(settings);
        this.setId = setId;
    }

    public String setId() { return setId; }

    @Override
    public String getTranslationKey(ItemStack stack) { return getTranslationKey(); }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        ArmorTooltips.append(setId, tooltip);
    }
}
