package com.derevna230.comborush.campaign;

import com.derevna230.comborush.anti.PlacedBlocks;
import net.minecraft.block.*;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.entity.EntityType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Сюжетные постройки ставятся рядом с игроком, когда задание началось.
 * Место выбирается на ровной природной земле: без воды, построек и блоков, поставленных игроками.
 * Координаты в постройке — локальные: x вправо, z вперёд (к игроку), y = 0 — уровень земли.
 */
public final class Structures {
    private Structures() {}

    /** Найденная площадка: центр на уровне земли (первый воздух) и куда смотрит фасад */
    public record Site(BlockPos base, Direction front) {
        public float yaw() { return front.asRotation(); }
        public BlockPos at(int x, int y, int z) {
            Direction right = front.rotateYCounterclockwise();
            return base.add(right.getOffsetX() * x + front.getOffsetX() * z, y, right.getOffsetZ() * x + front.getOffsetZ() * z);
        }
    }

    // ------------------------------------------------------------------ поиск места
    /**
     * @param angle направление поиска в радианах или NaN — любое
     * @param halfW полуширина площадки, back/fwd — глубина за центром и перед ним
     */
    public static Site findSite(ServerWorld w, BlockPos around, int minD, int maxD, double angle, double spread,
                                int halfW, int back, int fwd, int height, int maxDrop) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 48; attempt++) {
            double a = Double.isNaN(angle) ? r.nextDouble() * Math.PI * 2 : angle + (r.nextDouble() * 2 - 1) * spread;
            double d = minD + r.nextDouble() * Math.max(1, maxD - minD);
            int cx = around.getX() + (int) Math.round(Math.cos(a) * d), cz = around.getZ() + (int) Math.round(Math.sin(a) * d);
            Direction front = Direction.getFacing((float) (around.getX() - cx), 0f, (float) (around.getZ() - cz));
            if (front.getAxis() == Direction.Axis.Y) front = Direction.SOUTH;
            Site probe = new Site(new BlockPos(cx, 0, cz), front);
            List<Integer> ys = new ArrayList<>();
            boolean ok = true;
            for (int x = -halfW; x <= halfW && ok; x++)
                for (int z = -back; z <= fwd && ok; z++) {
                    BlockPos col = probe.at(x, 0, z);
                    Integer g = ground(w, col.getX(), col.getZ());
                    if (g == null) ok = false; else ys.add(g);
                }
            if (!ok || ys.isEmpty()) continue;
            Collections.sort(ys);
            if (ys.get(ys.size() - 1) - ys.get(0) > maxDrop) continue;
            int baseY = ys.get(ys.size() / 2) + 1;
            Site s = new Site(new BlockPos(cx, baseY, cz), front);
            if (clearable(w, s, halfW, back, fwd, height)) return s;
        }
        return null;
    }

    /** Верхний твёрдый природный блок столбца или null (вода, лава, постройка) */
    private static Integer ground(ServerWorld w, int x, int z) {
        int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        if (top <= w.getBottomY()) return null;
        BlockPos p = new BlockPos(x, top, z);
        BlockState st = w.getBlockState(p);
        if (!st.getFluidState().isEmpty() || artificial(w, p, st)) return null;
        return top;
    }

    private static boolean artificial(ServerWorld w, BlockPos p, BlockState st) {
        if (st.hasBlockEntity() || PlacedBlocks.isPlaced(w, p)) return true;
        return st.isIn(BlockTags.PLANKS) || st.isIn(BlockTags.WOOL) || st.isIn(BlockTags.SLABS) || st.isIn(BlockTags.STAIRS)
            || st.isIn(BlockTags.FENCES) || st.isIn(BlockTags.WALLS) || st.isIn(BlockTags.IMPERMEABLE) || st.isIn(BlockTags.BEDS)
            || st.isIn(BlockTags.DOORS) || st.isIn(BlockTags.LOGS) || st.isIn(BlockTags.WOOL_CARPETS) || st.isIn(BlockTags.RAILS)
            || st.isOf(Blocks.FARMLAND) || st.isOf(Blocks.DIRT_PATH) || st.isOf(Blocks.TORCH) || st.isOf(Blocks.COBBLESTONE)
            || st.isOf(Blocks.STONE_BRICKS) || st.isOf(Blocks.BRICKS);
    }

    private static boolean natural(BlockState st) {
        return st.isIn(BlockTags.DIRT) || st.isIn(BlockTags.SAND) || st.isIn(BlockTags.BASE_STONE_OVERWORLD)
            || st.isOf(Blocks.GRAVEL) || st.isOf(Blocks.SNOW_BLOCK) || st.isOf(Blocks.CLAY) || st.isOf(Blocks.SANDSTONE)
            || st.isIn(BlockTags.TERRACOTTA) || st.isOf(Blocks.POWDER_SNOW) || st.isOf(Blocks.MOSS_BLOCK);
    }

    private static boolean clearable(ServerWorld w, Site s, int halfW, int back, int fwd, int height) {
        for (int x = -halfW; x <= halfW; x++)
            for (int z = -back; z <= fwd; z++)
                for (int y = 0; y <= height; y++) {
                    BlockPos p = s.at(x, y, z);
                    BlockState st = w.getBlockState(p);
                    if (st.isAir() || st.isIn(BlockTags.LEAVES) || (st.isReplaceable() && st.getFluidState().isEmpty())) continue;
                    if (artificial(w, p, st) || !st.getFluidState().isEmpty()) return false;
                    if (!natural(st)) return false;            // срезаем только землю и камень
                }
        return true;
    }

    /** Выровнять: подсыпать землю до уровня, срезать лишнее и освободить место над площадкой */
    private static void prepare(ServerWorld w, Site s, int halfW, int back, int fwd, int height) {
        for (int x = -halfW; x <= halfW; x++)
            for (int z = -back; z <= fwd; z++) {
                BlockPos floor = s.at(x, -1, z);
                for (int y = -4; y <= -1; y++) {
                    BlockPos p = s.at(x, y, z);
                    BlockState st = w.getBlockState(p);
                    if (st.isAir() || st.isReplaceable() || st.isIn(BlockTags.LEAVES) || !st.getFluidState().isEmpty())
                        w.setBlockState(p, y == -1 ? Blocks.GRASS_BLOCK.getDefaultState() : Blocks.DIRT.getDefaultState());
                }
                if (w.getBlockState(floor).isOf(Blocks.DIRT)) w.setBlockState(floor, Blocks.GRASS_BLOCK.getDefaultState());
                for (int y = 0; y <= height; y++) w.setBlockState(s.at(x, y, z), Blocks.AIR.getDefaultState());
            }
    }

    private static void set(ServerWorld w, Site s, int x, int y, int z, BlockState st) { w.setBlockState(s.at(x, y, z), st); }

    private static void sign(ServerWorld w, Site s, int x, int y, int z, String l1, String l2, String l3, DyeColor color) {
        BlockPos p = s.at(x, y, z);
        w.setBlockState(p, Blocks.DARK_OAK_WALL_SIGN.getDefaultState().with(WallSignBlock.FACING, s.front()));
        if (w.getBlockEntity(p) instanceof SignBlockEntity be) {
            SignText t = new SignText().withMessage(0, Text.literal(l1)).withMessage(1, Text.literal(l2))
                .withMessage(2, Text.literal(l3)).withColor(color).withGlowing(true);
            be.setText(t, true);
            be.markDirty();
            w.updateListeners(p, w.getBlockState(p), w.getBlockState(p), Block.NOTIFY_ALL);
        }
    }

    private static void poof(ServerWorld w, Site s, int r) {
        BlockPos c = s.base();
        w.spawnParticles(ParticleTypes.CLOUD, c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5, 40, r * 0.5, 1, r * 0.5, 0.02);
        w.spawnParticles(ParticleTypes.END_ROD, c.getX() + 0.5, c.getY() + 2, c.getZ() + 0.5, 30, r * 0.4, 1, r * 0.4, 0.05);
        w.playSound(null, c, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.BLOCKS, 1.5f, 1.2f);
    }

    public static StoryEntity spawn(ServerWorld w, EntityType<StoryEntity> type, String kind, BlockPos pos, float yaw, UUID owner, int slot) {
        StoryEntity e = type.create(w);
        if (e == null) return null;
        e.setKind(kind);
        e.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
        e.setHomeYaw(yaw);
        if (owner != null) e.setOwner(owner, slot);
        w.spawnEntity(e);
        return e;
    }

    // ------------------------------------------------------------------ касса Билетёра
    public static Site findBooth(ServerWorld w, BlockPos near) {
        return findSite(w, near, 8, 18, Double.NaN, 0, 3, 3, 4, 4, 3);
    }

    /** Будка 5×5 с полосатой крышей, прилавком и вывеской. Возвращает место Билетёра */
    public static BlockPos buildBooth(ServerWorld w, Site s) {
        prepare(w, s, 3, 3, 4, 4);
        BlockState floor = Blocks.SPRUCE_PLANKS.getDefaultState(), wall = Blocks.DARK_OAK_PLANKS.getDefaultState();
        BlockState post = Blocks.STRIPPED_DARK_OAK_LOG.getDefaultState();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) set(w, s, x, -1, z, floor);
        for (int x = -1; x <= 1; x++) {                                         // дорожка к окошку
            set(w, s, x, -1, 3, Blocks.POLISHED_ANDESITE.getDefaultState());
            set(w, s, x, 0, 3, Blocks.RED_CARPET.getDefaultState());
        }
        for (int y = 0; y <= 2; y++) {
            for (int x = -2; x <= 2; x++) set(w, s, x, y, -2, wall);
            for (int z = -1; z <= 1; z++) {
                set(w, s, -2, y, z, y == 1 && z == -1 ? Blocks.GLASS_PANE.getDefaultState() : wall);
                set(w, s, 2, y, z, y == 1 && z == -1 ? Blocks.GLASS_PANE.getDefaultState() : wall);
            }
            set(w, s, -2, y, 2, post);
            set(w, s, 2, y, 2, post);
        }
        for (int x = -1; x <= 1; x++) {
            set(w, s, x, 0, 2, Blocks.SPRUCE_PLANKS.getDefaultState());       // прилавок
            set(w, s, x, 2, 2, wall);                                           // перемычка над окошком
        }
        // крыша: красно-белые полосы с навесом
        for (int x = -3; x <= 3; x++)
            for (int z = -3; z <= 3; z++)
                set(w, s, x, 3, z, ((x + 3) % 2 == 0 ? Blocks.RED_WOOL : Blocks.WHITE_WOOL).getDefaultState());
        for (int x = -3; x <= 3; x++) set(w, s, x, 4, 0, ((x + 3) % 2 == 0 ? Blocks.RED_WOOL : Blocks.WHITE_WOOL).getDefaultState());
        set(w, s, 0, 5, 0, Blocks.LIGHTNING_ROD.getDefaultState());
        // фонари под навесом и у входа
        set(w, s, -3, 2, 3, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
        set(w, s, 3, 2, 3, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
        set(w, s, -1, 2, -1, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
        // внутри: бочка и рулон билетов (свиток)
        set(w, s, -1, 0, -1, Blocks.BARREL.getDefaultState());
        set(w, s, 1, 0, -1, Blocks.BOOKSHELF.getDefaultState());
        set(w, s, 1, 1, -1, Blocks.POTTED_RED_TULIP.getDefaultState());
        // ограждение очереди: столбики с цепью
        for (int side : new int[]{-3, 3}) {
            set(w, s, side, 0, 4, Blocks.DARK_OAK_FENCE.getDefaultState());
            set(w, s, side, 1, 4, Blocks.LANTERN.getDefaultState());
        }
        sign(w, s, 0, 2, 3, "★ КАССА ★", "Combo Rush", "вход для участников", DyeColor.YELLOW);
        poof(w, s, 3);
        return s.at(0, 0, 1);
    }

    // ------------------------------------------------------------------ стойка адресата
    public static Site findPost(ServerWorld w, BlockPos near, double angle) {
        return findSite(w, near, 45, 80, angle, 0.5, 2, 2, 2, 3, 2);
    }

    public static BlockPos buildPost(ServerWorld w, Site s, int variant) {
        prepare(w, s, 2, 2, 2, 3);
        BlockState[] path = {Blocks.STONE_BRICKS.getDefaultState(), Blocks.MOSSY_STONE_BRICKS.getDefaultState(), Blocks.CRACKED_STONE_BRICKS.getDefaultState()};
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) set(w, s, x, -1, z, path[r.nextInt(path.length)]);
        set(w, s, -1, 0, -1, Blocks.BARREL.getDefaultState());
        BlockState pot = switch (variant) {
            case 0 -> Blocks.POTTED_DANDELION.getDefaultState();
            case 1 -> Blocks.POTTED_ALLIUM.getDefaultState();
            default -> Blocks.POTTED_CORNFLOWER.getDefaultState();
        };
        set(w, s, -1, 1, -1, pot);
        set(w, s, 1, 0, -1, Blocks.SPRUCE_FENCE.getDefaultState());
        set(w, s, 1, 1, -1, Blocks.SPRUCE_FENCE.getDefaultState());
        set(w, s, 1, 2, -1, Blocks.LANTERN.getDefaultState());
        set(w, s, 0, 0, -1, (variant == 0 ? Blocks.YELLOW_BANNER : variant == 1 ? Blocks.MAGENTA_BANNER : Blocks.LIGHT_BLUE_BANNER)
            .getDefaultState().with(BannerBlock.ROTATION, rotationOf(s.front())));
        poof(w, s, 2);
        return s.at(0, 0, 0);
    }

    private static int rotationOf(Direction d) {
        return switch (d) { case SOUTH -> 0; case WEST -> 4; case NORTH -> 8; default -> 12; };
    }

    // ------------------------------------------------------------------ афиша
    public static Site findPoster(ServerWorld w, BlockPos near, double angle) {
        return findSite(w, near, 50, 110, angle, 0.6, 2, 1, 2, 3, 2);
    }

    public static BlockPos buildPoster(ServerWorld w, Site s) {
        prepare(w, s, 2, 1, 2, 3);
        for (int x = -1; x <= 1; x++) set(w, s, x, -1, 0, Blocks.COARSE_DIRT.getDefaultState());
        set(w, s, 0, -1, 1, Blocks.COARSE_DIRT.getDefaultState());
        set(w, s, -2, 0, 1, Blocks.LANTERN.getDefaultState());
        set(w, s, 2, 0, 1, Blocks.LANTERN.getDefaultState());
        poof(w, s, 2);
        return s.at(0, 0, 0);
    }

    // ------------------------------------------------------------------ съёмочная площадка
    public static Site findStage(ServerWorld w, BlockPos near) {
        return findSite(w, near, 30, 60, Double.NaN, 0, 6, 6, 9, 5, 3);
    }

    /** Сцена 9×9 с занавесом, прожекторами, камерой и креслами зрителей. Возвращает центр сцены */
    public static BlockPos buildStage(ServerWorld w, Site s) {
        prepare(w, s, 6, 6, 9, 5);
        for (int x = -4; x <= 4; x++)
            for (int z = -4; z <= 4; z++) {
                boolean edge = Math.abs(x) == 4 || Math.abs(z) == 4;
                set(w, s, x, -1, z, (edge ? Blocks.POLISHED_BLACKSTONE_BRICKS : Blocks.DARK_OAK_PLANKS).getDefaultState());
            }
        // крестик-отметка для актёра и красная дорожка к зрителям
        for (int i = -1; i <= 1; i++) {
            set(w, s, i, 0, i, Blocks.YELLOW_CARPET.getDefaultState());
            set(w, s, i, 0, -i, Blocks.YELLOW_CARPET.getDefaultState());
        }
        for (int z = 2; z <= 8; z++) set(w, s, 0, 0, z, Blocks.RED_CARPET.getDefaultState());
        // занавес
        for (int x = -4; x <= 4; x++) {
            for (int y = 0; y <= 3; y++) set(w, s, x, y, -5, Blocks.RED_WOOL.getDefaultState());
            set(w, s, x, 4, -5, Blocks.STRIPPED_DARK_OAK_LOG.getDefaultState().with(PillarBlock.AXIS, s.front().rotateYClockwise().getAxis()));
        }
        // прожекторы по углам
        for (int sx : new int[]{-5, 5})
            for (int sz : new int[]{-5, 5}) {
                for (int y = 0; y <= 2; y++) set(w, s, sx, y, sz, Blocks.DARK_OAK_FENCE.getDefaultState());
                set(w, s, sx, 3, sz, Blocks.SEA_LANTERN.getDefaultState());
            }
        // камера на штативе смотрит на сцену
        set(w, s, 2, 0, 6, Blocks.DARK_OAK_FENCE.getDefaultState());
        set(w, s, 2, 1, 6, Blocks.OBSERVER.getDefaultState().with(ObserverBlock.FACING, s.front().getOpposite()));
        // кресла зрителей
        for (int x : new int[]{-4, -3, -2, 2, 3, 4})
            set(w, s, x, 0, 8, Blocks.CRIMSON_STAIRS.getDefaultState().with(StairsBlock.FACING, s.front()));
        sign(w, s, 1, 1, -4, "Съёмка №1", "«Первый акт»", "тишина на площадке!", DyeColor.WHITE);
        poof(w, s, 5);
        return s.at(0, 0, 0);
    }
}
