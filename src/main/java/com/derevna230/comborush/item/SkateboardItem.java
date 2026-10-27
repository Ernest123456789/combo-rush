package com.derevna230.comborush.item;

import com.derevna230.comborush.skate.SkateData;
import com.derevna230.comborush.skate.SkateNet;
import com.derevna230.comborush.skate.SkateboardEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.List;

/** Зачарованный скейтборд: ПКМ по полу — встать и поехать; Shift+ПКМ в воздух — меню прокачки */
public class SkateboardItem extends Item {
    public SkateboardItem(Settings settings) { super(settings); }

    @Override public boolean hasGlint(ItemStack stack) { return true; }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        PlayerEntity player = ctx.getPlayer();
        if (player == null || player.isSneaking() || ctx.getSide() != Direction.UP || player.hasVehicle()) return ActionResult.PASS;
        World world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos().up();
        if (!world.getBlockState(pos).isReplaceable() || !world.isAir(pos.up())) return ActionResult.FAIL;
        if (world instanceof ServerWorld sw) {
            SkateboardEntity e = com.derevna230.comborush.pet.ModEntities.SKATE.create(sw);
            if (e == null) return ActionResult.FAIL;
            ItemStack st = ctx.getStack();
            e.loadFrom(st);
            e.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYaw(), 0);
            sw.spawnEntity(e);
            player.startRiding(e);
            sw.playSound(null, pos, SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.PLAYERS, 0.8f, 1.3f);
            sw.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, 1.6f);
            st.decrement(1);
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack st = user.getStackInHand(hand);
        if (user.isSneaking() && hand == Hand.MAIN_HAND) {
            if (!world.isClient && user instanceof ServerPlayerEntity sp) SkateNet.open(sp);
            return TypedActionResult.success(st, world.isClient);
        }
        return TypedActionResult.pass(st);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("§7ПКМ по полу — встать на скейт, Shift — слезть"));
        tooltip.add(Text.literal("§7Пробел — прыжок" + (SkateData.get(stack, SkateData.GLIDE) > 0 ? ", держи — планирование" : "")));
        for (String k : SkateData.KEYS) {
            int lvl = SkateData.get(stack, k);
            String name = switch (k) { case SkateData.SPEED -> "Скорость"; case SkateData.JUMP -> "Прыжок"; default -> "Планирование"; };
            tooltip.add(Text.literal("§d" + name + " " + "★".repeat(lvl) + "§8" + "☆".repeat(SkateData.max(k) - lvl)));
        }
        tooltip.add(Text.literal("§eShift+ПКМ — прокачка за эссенцию"));
    }
}
