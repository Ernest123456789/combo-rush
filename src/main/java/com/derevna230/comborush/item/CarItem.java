package com.derevna230.comborush.item;

import com.derevna230.comborush.car.CarEntity;
import com.derevna230.comborush.pet.ModEntities;
import net.minecraft.entity.EntityType;
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

/** Машина в инвентаре: ПКМ по верхней грани блока — поставить, дальше на ней можно ездить */
public class CarItem extends Item {
    private final String carId;

    public CarItem(String carId, Settings settings) {
        super(settings);
        this.carId = carId;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        if (ctx.getSide() != Direction.UP) return ActionResult.PASS;
        World world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos().up();
        if (world instanceof ServerWorld sw) {
            EntityType<CarEntity> type = ModEntities.CARS.get(carId);
            CarEntity e = type == null ? null : type.create(sw);
            if (e == null) return ActionResult.FAIL;
            float yaw = ctx.getPlayer() != null ? ctx.getPlayer().getYaw() : 0f;
            e.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
            e.setHeadYaw(yaw);
            e.setBodyYaw(yaw);
            if (!sw.isSpaceEmpty(e)) {
                if (ctx.getPlayer() != null) ctx.getPlayer().sendMessage(Text.literal("§cЗдесь тесно: машине нужно место 2×2 блока."), true);
                return ActionResult.FAIL;
            }
            sw.spawnEntity(e);
            sw.playSound(null, pos, SoundEvents.BLOCK_IRON_DOOR_OPEN, SoundCategory.NEUTRAL, 1f, 0.8f);
            ItemStack st = ctx.getStack();
            if (ctx.getPlayer() == null || !ctx.getPlayer().isCreative()) st.decrement(1);
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("§7Транспорт: можно ездить"));
        tooltip.add(Text.literal("§eПКМ по блоку — поставить"));
        tooltip.add(Text.literal("§7ПКМ по машине — сесть (первый садится за руль)"));
        tooltip.add(Text.literal("§7W/S — газ и тормоз, A/D — руль, Shift — выйти"));
        tooltip.add(Text.literal("§7Shift+ПКМ по пустой машине — убрать в инвентарь"));
    }
}
