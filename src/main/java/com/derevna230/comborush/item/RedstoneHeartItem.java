package com.derevna230.comborush.item;

import com.derevna230.comborush.boss.RedstoneGolemEntity;
import com.derevna230.comborush.pet.ModEntities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;

import java.util.List;

/** Сердце голема: ПКМ по земле пробуждает босса «Редстоуновый голем». Продаётся Торговцем Редстоуна */
public class RedstoneHeartItem extends Item {
    public RedstoneHeartItem(Settings settings) { super(settings); }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();
        PlayerEntity pl = ctx.getPlayer();
        if (!(world instanceof ServerWorld sw)) return ActionResult.success(true);
        if (sw.getDifficulty() == Difficulty.PEACEFUL) { say(pl, "§cНа мирной сложности голем не просыпается."); return ActionResult.FAIL; }
        BlockPos pos = ctx.getBlockPos().offset(ctx.getSide());
        Vec3d at = Vec3d.ofBottomCenter(pos);
        if (RedstoneGolemEntity.bossNear(sw, at, 100)) { say(pl, "§cРядом уже есть Редстоуновый голем."); return ActionResult.FAIL; }
        float yaw = pl != null ? pl.getYaw() + 180f : 0f;
        RedstoneGolemEntity probe = ModEntities.GOLEM.create(sw);
        if (probe == null) return ActionResult.FAIL;
        probe.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0);
        if (!sw.isSpaceEmpty(probe)) {
            say(pl, "§cГолему не хватает места: нужна ровная открытая площадка примерно 4×4 и 5 блоков высотой.");
            return ActionResult.FAIL;
        }
        RedstoneGolemEntity g = RedstoneGolemEntity.summon(sw, at, yaw);
        if (g == null) return ActionResult.FAIL;
        if (pl == null || !pl.isCreative()) ctx.getStack().decrement(1);
        for (ServerPlayerEntity p : sw.getPlayers())
            if (p.squaredDistanceTo(at) < 64 * 64)
                p.sendMessage(Text.literal("§c§l[!] §fЗемля трескается — §cРедстоуновый голем§f просыпается. Готовьтесь!"), false);
        return ActionResult.success(false);
    }

    private static void say(PlayerEntity p, String msg) { if (p != null) p.sendMessage(Text.literal(msg), true); }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("§7Пульсирующее ядро древнего голема."));
        tooltip.add(Text.literal("§eПКМ по земле — пробудить §cРедстоунового голема"));
        tooltip.add(Text.literal("§8Нужна открытая площадка 4×4. Босс опасен: берите друзей."));
    }
}
