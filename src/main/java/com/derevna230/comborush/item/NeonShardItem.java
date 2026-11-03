package com.derevna230.comborush.item;

import com.derevna230.comborush.data.PlayerProfile;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/** Неоновый осколок: ПКМ — обменять на эссенцию. Прячется в тайниках Ночного города */
public class NeonShardItem extends Item {
    public static final int ESSENCE = 3;

    public NeonShardItem(Settings settings) { super(settings); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient || !(user instanceof ServerPlayerEntity sp)) return TypedActionResult.success(stack, world.isClient);
        sp.setAttached(PlayerProfile.TYPE, PlayerProfile.of(sp).addEssence(ESSENCE));
        if (!sp.isCreative()) stack.decrement(1);
        world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1f, 1.5f);
        sp.sendMessage(Text.literal("§d+" + ESSENCE + " эссенции §7(неоновый осколок растворился)"), true);
        return TypedActionResult.success(stack, false);
    }

    @Override
    public boolean hasGlint(ItemStack stack) { return true; }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("§7Застывший свет ночного города."));
        tooltip.add(Text.literal("§dПКМ — получить " + ESSENCE + " эссенции"));
    }
}
