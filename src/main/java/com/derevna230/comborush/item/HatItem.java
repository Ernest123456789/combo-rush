package com.derevna230.comborush.item;

import com.derevna230.comborush.armor.ArmorTooltips;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Equipment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * Шляпа: обычный предмет, который надевается на голову. Minecraft сам рисует его модель на голове
 * (раздел display.head в models/item/<шляпа>.json), поэтому GeckoLib здесь не нужен.
 */
public class HatItem extends Item implements Equipment {
    private final String setId;

    public HatItem(String setId, Settings settings) {
        super(settings);
        this.setId = setId;
    }

    public String setId() { return setId; }

    @Override
    public EquipmentSlot getSlotType() { return EquipmentSlot.HEAD; }

    @Override
    public RegistryEntry<SoundEvent> getEquipSound() { return SoundEvents.ITEM_ARMOR_EQUIP_LEATHER; }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return this.equipAndSwap(this, world, user, hand);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        ArmorTooltips.append(setId, tooltip);
    }
}
