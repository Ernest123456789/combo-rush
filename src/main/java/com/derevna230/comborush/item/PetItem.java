package com.derevna230.comborush.item;

import com.derevna230.comborush.armor.ArmorTooltips;
import com.derevna230.comborush.pet.PetManager;
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

/** Питомец: ПКМ — призвать или отозвать. Пока предмет в инвентаре, питомец ходит за хозяином */
public class PetItem extends Item {
    private final String petId;

    public PetItem(String petId, Settings settings) {
        super(settings);
        this.petId = petId;
    }

    public String petId() { return petId; }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity sp) PetManager.toggle(sp, petId);
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public boolean hasGlint(ItemStack stack) { return false; }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        ArmorTooltips.append("pet_" + petId, tooltip);
        tooltip.add(Text.literal("§eПКМ — призвать / отозвать"));
    }
}
