package com.derevna230.comborush.item;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Бонус в сундуках с добычей (данжи, деревни, корабли…): при первом открытии
 * с шансом 50% в сундуке оказывается бутылка воды.
 */
public final class LootBonus {
    private LootBonus() {}
    public static final double BOTTLE_CHANCE = 0.5;

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient || hand != Hand.MAIN_HAND || player.isSpectator()) return ActionResult.PASS;
            if (!(world.getBlockEntity(hit.getBlockPos()) instanceof LootableContainerBlockEntity box)) return ActionResult.PASS;
            if (box.getLootTable() == null) return ActionResult.PASS;          // уже открывали или сундук игрока
            box.generateLoot(player);                                         // сначала обычная добыча
            if (ThreadLocalRandom.current().nextDouble() >= BOTTLE_CHANCE) return ActionResult.PASS;
            List<Integer> empty = new ArrayList<>();
            for (int i = 0; i < box.size(); i++) if (box.getStack(i).isEmpty()) empty.add(i);
            if (!empty.isEmpty())
                box.setStack(empty.get(ThreadLocalRandom.current().nextInt(empty.size())), new ItemStack(ModItems.WATER_BOTTLE));
            return ActionResult.PASS;
        });
    }
}
