package com.derevna230.comborush.item;

import com.derevna230.comborush.consumable.ConsumableDef;
import com.derevna230.comborush.consumable.ConsumableRegistry;
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

/** Чип данных: ПКМ — расшифровать и получить случайный расходник (если есть свободный слот) */
public class DataChipItem extends Item {
    public DataChipItem(Settings settings) { super(settings); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient || !(user instanceof ServerPlayerEntity sp)) return TypedActionResult.success(stack, world.isClient);
        PlayerProfile pr = PlayerProfile.of(sp);
        if (pr.consumables().size() >= PlayerProfile.CONSUMABLE_SLOTS) {
            sp.sendMessage(Text.literal("§cСлоты расходников заняты — освободи место."), true);
            return TypedActionResult.fail(stack);
        }
        String id = ConsumableRegistry.randomId(sp);
        ConsumableDef def = ConsumableRegistry.get(id);
        if (def == null) return TypedActionResult.fail(stack);
        sp.setAttached(PlayerProfile.TYPE, pr.addConsumable(id));
        if (!sp.isCreative()) stack.decrement(1);
        world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.8f, 1.8f);
        sp.sendMessage(Text.literal("§bЧип расшифрован: §f" + def.name), true);
        return TypedActionResult.success(stack, false);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("§7Зашифрованный носитель с уличного рынка."));
        tooltip.add(Text.literal("§bПКМ — получить случайный расходник"));
    }
}
