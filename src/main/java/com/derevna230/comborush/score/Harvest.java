package com.derevna230.comborush.score;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Что считается урожаем: зрелые пшеница/морковь/картофель/свёкла и прочие CropBlock, незерский нарост,
 * какао, сладкие ягоды, тыквы и арбузы. Плюс сбор правым кликом (ягоды, моды «правый клик — собрать»):
 * если после клика зрелое растение «помолодело» — это урожай.
 */
public final class Harvest {
    private Harvest() {}

    private record Pending(ServerPlayerEntity player, ServerWorld world, BlockPos pos, BlockState before, int tick) {}
    private static final List<Pending> PENDING = new ArrayList<>();

    public static boolean isRipe(BlockState s) {
        Block b = s.getBlock();
        if (b instanceof CropBlock crop) return crop.isMature(s);
        if (b instanceof NetherWartBlock) return s.get(NetherWartBlock.AGE) >= 3;
        if (b instanceof CocoaBlock) return s.get(CocoaBlock.AGE) >= 2;
        if (b instanceof SweetBerryBushBlock) return s.get(SweetBerryBushBlock.AGE) >= 2;
        return b == Blocks.MELON || b == Blocks.PUMPKIN;
    }

    /** Только то, что собирают кликом, не ломая блок */
    private static boolean rightClickable(BlockState s) {
        Block b = s.getBlock();
        return b instanceof CropBlock || b instanceof NetherWartBlock || b instanceof CocoaBlock || b instanceof SweetBerryBushBlock;
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity sp) || !(world instanceof ServerWorld sw)) return ActionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            BlockState st = world.getBlockState(pos);
            if (rightClickable(st) && isRipe(st)) PENDING.add(new Pending(sp, sw, pos.toImmutable(), st, sw.getServer().getTicks()));
            return ActionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (PENDING.isEmpty()) return;
            int now = server.getTicks();
            List<Pending> done = new ArrayList<>();
            for (Pending p : PENDING) {
                if (now - p.tick() < 1) continue;
                done.add(p);
                BlockState after = p.world().getBlockState(p.pos());
                boolean harvested = !after.equals(p.before()) && !(after.isOf(p.before().getBlock()) && isRipe(after));
                // сломанный блок уже засчитан событием поломки
                if (harvested && after.isOf(p.before().getBlock()) && !p.player().isRemoved())
                    ScoreEngine.award(p.player(), ScoreSource.HARVEST, Registries.BLOCK.getId(p.before().getBlock()).toString());
            }
            PENDING.removeAll(done);
        });
    }
}
