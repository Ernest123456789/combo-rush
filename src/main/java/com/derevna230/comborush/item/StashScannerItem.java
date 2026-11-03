package com.derevna230.comborush.item;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.List;

/** Сканер тайников: ПКМ — показывает направление и расстояние до ближайшего нетронутого сундука с добычей */
public class StashScannerItem extends Item {
    public static final int RADIUS = 72;
    private static final String[] DIRS = {"юг", "юго-запад", "запад", "северо-запад", "север", "северо-восток", "восток", "юго-восток"};

    public StashScannerItem(Settings settings) { super(settings); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!(world instanceof ServerWorld sw) || !(user instanceof ServerPlayerEntity sp)) return TypedActionResult.success(stack, world.isClient);

        BlockPos origin = sp.getBlockPos();
        BlockPos best = null;
        double bestD = (double) RADIUS * RADIUS;
        int cr = (RADIUS >> 4) + 1, cx0 = origin.getX() >> 4, cz0 = origin.getZ() >> 4;
        for (int cx = cx0 - cr; cx <= cx0 + cr; cx++)
            for (int cz = cz0 - cr; cz <= cz0 + cr; cz++) {
                Chunk ch = sw.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (ch == null) continue;
                for (BlockPos pos : ch.getBlockEntityPositions()) {
                    double d = pos.getSquaredDistance(origin);
                    if (d >= bestD) continue;
                    BlockEntity be = sw.getBlockEntity(pos);
                    // нетронутый сундук с добычей: в данных ещё есть LootTable
                    if (!(be instanceof ChestBlockEntity)) continue;
                    if (!be.createNbt(sw.getRegistryManager()).contains("LootTable")) continue;
                    best = pos.toImmutable();
                    bestD = d;
                }
            }

        if (best == null) {
            sp.sendMessage(Text.literal("§7Сканер: в радиусе " + RADIUS + " блоков тайников нет"), true);
            sp.getItemCooldownManager().set(this, 30);
            sw.playSound(null, origin, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.7f, 0.6f);
            return TypedActionResult.success(stack, false);
        }

        double dx = best.getX() + 0.5 - sp.getX(), dz = best.getZ() + 0.5 - sp.getZ();
        // направление в мире: yaw 0 = юг (+Z), 90 = запад (−X)
        double deg = Math.toDegrees(Math.atan2(-dx, dz));
        int idx = MathHelper.floor((MathHelper.wrapDegrees(deg) + 360 + 22.5) / 45.0) % 8;
        int dist = (int) Math.round(Math.sqrt(bestD));
        int dy = best.getY() - origin.getY();
        String vert = dy > 4 ? ", выше на " + dy : dy < -4 ? ", ниже на " + (-dy) : "";
        sp.sendMessage(Text.literal("§bТайник: §f" + DIRS[idx] + "§b, " + dist + " бл." + vert), true);
        sw.playSound(null, origin, SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 1f, 1.6f);
        sp.getItemCooldownManager().set(this, 100);
        stack.damage(1, sp, hand == Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        return TypedActionResult.success(stack, false);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("§7Ловит сигнал нетронутых тайников."));
        tooltip.add(Text.literal("§bПКМ — направление и расстояние до ближайшего"));
        tooltip.add(Text.literal("§8Радиус " + RADIUS + " блоков, только прогруженные чанки"));
    }
}
