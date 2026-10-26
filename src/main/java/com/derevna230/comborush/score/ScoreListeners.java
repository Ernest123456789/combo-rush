package com.derevna230.comborush.score;

import com.derevna230.comborush.anti.PlacedBlocks;
import com.derevna230.comborush.anti.SpawnerTracker;
import com.derevna230.comborush.cycle.CycleManager;
import com.derevna230.comborush.cycle.CyclePhase;
import com.derevna230.comborush.director.DirectorManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.block.CropBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.TypedActionResult;

public class ScoreListeners {
    private static final double SHOOT_MIN_DISTANCE_SQ = 10 * 10;
    /** Доля очков за моба из спавнера */
    private static final double SPAWNER_MULT = 0.25;

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            if (!(player instanceof ServerPlayerEntity sp)) return;
            String id = Registries.BLOCK.getId(state.getBlock()).toString();

            // босс «Хрупкость»: инструмент изнашивается вдвое быстрее
            if (DirectorManager.hasDebuff(sp, "fragile") && CycleManager.phase() == CyclePhase.EVENT)
                sp.getMainHandStack().damage(1, sp, EquipmentSlot.MAINHAND);

            // поставленные игроком руды очков не дают (защита от шёлкового касания)
            boolean placedByPlayer = PlacedBlocks.consume(world, pos);

            if (state.isIn(ConventionalBlockTags.ORES)) {
                if (placedByPlayer) {
                    if (CycleManager.phase() == CyclePhase.EVENT)
                        sp.sendMessage(Text.literal("§7Поставленная руда очков не даёт."), true);
                } else {
                    ScoreEngine.award(sp, ScoreSource.MINE, id);
                }
            } else if (state.getBlock() instanceof CropBlock crop && crop.isMature(state)) {
                ScoreEngine.award(sp, ScoreSource.HARVEST, id);
            }
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.getCommandTags().contains("cr_hunter"))
                DirectorManager.onHunterKilled(entity.getServer(),
                    source.getAttacker() instanceof ServerPlayerEntity k ? k : null);
            if (source.getAttacker() instanceof ServerPlayerEntity sp && entity instanceof HostileEntity) {
                String type = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
                if (SpawnerTracker.isSpawnerMob(entity))
                    ScoreEngine.award(sp, ScoreSource.KILL, "spawner:" + type, SPAWNER_MULT, "Спавнер");
                else
                    ScoreEngine.award(sp, ScoreSource.KILL, type);
            }
        });

        // Выстрел издалека: снаряд попал во враждебного моба с дистанции 10+ блоков
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (blocked || damageTaken <= 0) return;
            if (!(source.getAttacker() instanceof ServerPlayerEntity sp)) return;
            if (!(source.getSource() instanceof PersistentProjectileEntity)) return;
            if (!(entity instanceof HostileEntity)) return;
            if (sp.squaredDistanceTo(entity) < SHOOT_MIN_DISTANCE_SQ) return;
            String type = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
            if (SpawnerTracker.isSpawnerMob(entity))
                ScoreEngine.award(sp, ScoreSource.SHOOT, "spawner:" + type, SPAWNER_MULT, "Спавнер");
            else
                ScoreEngine.award(sp, ScoreSource.SHOOT, type);
        });

        // босс «Голод»: во время события нельзя есть, пока не набрано достаточно очков
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!world.isClient && player instanceof ServerPlayerEntity sp
                    && stack.get(DataComponentTypes.FOOD) != null && DirectorManager.blocksFood(sp)) {
                sp.sendMessage(Text.literal("§cГолод: нельзя есть, пока не наберёшь очков!"), true);
                return TypedActionResult.fail(stack);
            }
            return TypedActionResult.pass(stack);
        });
    }
}
