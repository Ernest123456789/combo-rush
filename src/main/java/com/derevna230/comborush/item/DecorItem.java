package com.derevna230.comborush.item;

import com.derevna230.comborush.armor.ArmorTooltips;
import com.derevna230.comborush.pet.DecorEntity;
import com.derevna230.comborush.pet.ModEntities;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.List;

/** Декоративное растение: ПКМ по верхней грани блока — посадить */
public class DecorItem extends Item {
    private final String decorId;

    public DecorItem(String decorId, Settings settings) {
        super(settings);
        this.decorId = decorId;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        if (ctx.getSide() != Direction.UP) return ActionResult.PASS;
        World world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos().up();
        if (!world.getBlockState(pos).isReplaceable()) return ActionResult.FAIL;
        if (world instanceof ServerWorld sw) {
            DecorEntity e = ModEntities.DECOR.create(sw);
            if (e == null) return ActionResult.FAIL;
            e.setDecorId(decorId);
            float yaw = ctx.getPlayer() != null ? ctx.getPlayer().getYaw() + 180f : 0f;
            e.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
            e.setHeadYaw(yaw);
            e.setBodyYaw(yaw);
            sw.spawnEntity(e);
            sw.playSound(null, pos, SoundEvents.BLOCK_AZALEA_PLACE, SoundCategory.BLOCKS, 1f, 1f);
            ItemStack st = ctx.getStack();
            if (ctx.getPlayer() == null || !ctx.getPlayer().isCreative()) st.decrement(1);
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        ArmorTooltips.append("decor_" + decorId, tooltip);
        tooltip.add(Text.literal("§eПКМ по блоку — посадить, удар — выкопать"));
    }
}
