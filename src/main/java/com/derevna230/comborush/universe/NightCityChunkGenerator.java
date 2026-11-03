package com.derevna230.comborush.universe;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.universe.NightCity.B;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Direction;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Генератор измерения «Ночной город»: ставит блоки из {@link NightCity}. Ландшафта и пещер нет —
 * чанк целиком собирается из детерминированной застройки, поэтому генерация быстрая и не зависит от соседей.
 */
public class NightCityChunkGenerator extends ChunkGenerator {
    public static final MapCodec<NightCityChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
    ).apply(i, i.stable(NightCityChunkGenerator::new)));

    private static final BlockState[] STATES = new BlockState[B.values().length];

    static {
        for (B b : B.values()) STATES[b.ordinal()] = state(b);
    }

    public NightCityChunkGenerator(BiomeSource biomeSource) { super(biomeSource); }

    /** Зарегистрировать тип генератора до заморозки реестров (вызывать из onInitialize) */
    public static void register() {
        Registry.register(Registries.CHUNK_GENERATOR, Identifier.of(ComboRush.MOD_ID, "night_city"), CODEC);
    }

    private static BlockState stairs(net.minecraft.block.Block b, Direction facing) {
        return b.getDefaultState().with(Properties.HORIZONTAL_FACING, facing);
    }

    private static BlockState chest(Direction facing) {
        return Blocks.CHEST.getDefaultState().with(Properties.HORIZONTAL_FACING, facing);
    }

    private static BlockState state(B b) {
        return switch (b) {
            case AIR -> Blocks.AIR.getDefaultState();
            case BEDROCK -> Blocks.BEDROCK.getDefaultState();
            case DEEPSLATE -> Blocks.DEEPSLATE.getDefaultState();
            case DARK_STONE -> Blocks.BLACKSTONE.getDefaultState();
            case ASPHALT -> Blocks.BLACK_CONCRETE.getDefaultState();
            case SIDEWALK_A -> Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();
            case SIDEWALK_B -> Blocks.GRAY_CONCRETE.getDefaultState();
            case LINE_WHITE -> Blocks.WHITE_CONCRETE.getDefaultState();
            case LINE_YELLOW -> Blocks.YELLOW_CONCRETE.getDefaultState();
            case ALLEY -> Blocks.POLISHED_BLACKSTONE.getDefaultState();
            case PLAZA_A -> Blocks.BLACK_CONCRETE.getDefaultState();
            case PLAZA_B -> Blocks.DEEPSLATE_TILES.getDefaultState();
            case PLAZA_LINE -> Blocks.SEA_LANTERN.getDefaultState();
            case POLE -> Blocks.POLISHED_BLACKSTONE_WALL.getDefaultState();
            case IRON -> Blocks.IRON_BARS.getDefaultState();
            case L_CYAN, L_BLUE, LANTERN_BASE -> Blocks.SEA_LANTERN.getDefaultState();
            case L_MAGENTA, L_PURPLE -> Blocks.PEARLESCENT_FROGLIGHT.getDefaultState();
            case L_LIME -> Blocks.VERDANT_FROGLIGHT.getDefaultState();
            case L_ORANGE -> Blocks.OCHRE_FROGLIGHT.getDefaultState();
            case L_WARM, LAMP_WHITE -> Blocks.GLOWSTONE.getDefaultState();
            case G_CYAN -> Blocks.CYAN_STAINED_GLASS.getDefaultState();
            case G_MAGENTA -> Blocks.MAGENTA_STAINED_GLASS.getDefaultState();
            case G_LIME -> Blocks.LIME_STAINED_GLASS.getDefaultState();
            case G_ORANGE -> Blocks.ORANGE_STAINED_GLASS.getDefaultState();
            case G_BLUE -> Blocks.BLUE_STAINED_GLASS.getDefaultState();
            case G_PURPLE -> Blocks.PURPLE_STAINED_GLASS.getDefaultState();
            case G_TINT -> Blocks.TINTED_GLASS.getDefaultState();
            case G_WHITE -> Blocks.WHITE_STAINED_GLASS.getDefaultState();
            case G_RED -> Blocks.RED_STAINED_GLASS.getDefaultState();
            case C_CYAN -> Blocks.CYAN_CONCRETE.getDefaultState();
            case C_MAGENTA -> Blocks.MAGENTA_CONCRETE.getDefaultState();
            case C_LIME -> Blocks.LIME_CONCRETE.getDefaultState();
            case C_ORANGE -> Blocks.ORANGE_CONCRETE.getDefaultState();
            case C_BLUE -> Blocks.BLUE_CONCRETE.getDefaultState();
            case C_PURPLE -> Blocks.PURPLE_CONCRETE.getDefaultState();
            case BLACK -> Blocks.BLACK_CONCRETE.getDefaultState();
            case GRAY -> Blocks.GRAY_CONCRETE.getDefaultState();
            case LIGHT_GRAY -> Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();
            case WHITE -> Blocks.WHITE_CONCRETE.getDefaultState();
            case FLOOR -> Blocks.POLISHED_DEEPSLATE.getDefaultState();
            case FLOOR_LIGHT -> Blocks.POLISHED_ANDESITE.getDefaultState();
            case ROOF -> Blocks.DEEPSLATE_BRICKS.getDefaultState();
            case QUARTZ -> Blocks.QUARTZ_BLOCK.getDefaultState();
            case ANTENNA -> Blocks.LIGHTNING_ROD.getDefaultState();
            case BEACON_RED -> Blocks.REDSTONE_LAMP.getDefaultState().with(Properties.LIT, true);
            case GRASS -> Blocks.GRASS_BLOCK.getDefaultState();
            case PATH -> Blocks.SMOOTH_STONE.getDefaultState();
            case LOG -> Blocks.CHERRY_LOG.getDefaultState();
            case LEAVES -> Blocks.CHERRY_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true);
            case WATER -> Blocks.WATER.getDefaultState();
            case SAND -> Blocks.SAND.getDefaultState();
            case DIRT -> Blocks.DIRT.getDefaultState();
            case STONE -> Blocks.STONE.getDefaultState();
            case ANDESITE -> Blocks.ANDESITE.getDefaultState();
            case SNOW -> Blocks.SNOW_BLOCK.getDefaultState();
            case RED_CONC -> Blocks.RED_CONCRETE.getDefaultState();
            case PODZOL -> Blocks.PODZOL.getDefaultState();
            case COARSE -> Blocks.COARSE_DIRT.getDefaultState();
            case MOSS -> Blocks.MOSS_BLOCK.getDefaultState();
            case MUD -> Blocks.MUD.getDefaultState();
            case GRAVEL -> Blocks.GRAVEL.getDefaultState();
            case PATH_DIRT -> Blocks.DIRT_PATH.getDefaultState();
            case COBBLE -> Blocks.COBBLESTONE.getDefaultState();
            case MOSSY_COBBLE -> Blocks.MOSSY_COBBLESTONE.getDefaultState();
            case CRACKED -> Blocks.CRACKED_STONE_BRICKS.getDefaultState();
            case STONE_BRICKS -> Blocks.STONE_BRICKS.getDefaultState();
            case BRICKS -> Blocks.BRICKS.getDefaultState();
            case BARRIER -> Blocks.BARRIER.getDefaultState();
            case GRASS_T -> Blocks.SHORT_GRASS.getDefaultState();
            case FERN -> Blocks.FERN.getDefaultState();
            case FL_POPPY -> Blocks.POPPY.getDefaultState();
            case FL_ORCHID -> Blocks.BLUE_ORCHID.getDefaultState();
            case FL_ALLIUM -> Blocks.ALLIUM.getDefaultState();
            case FL_AZURE -> Blocks.AZURE_BLUET.getDefaultState();
            case FL_CORN -> Blocks.CORNFLOWER.getDefaultState();
            case FL_TULIP -> Blocks.PINK_TULIP.getDefaultState();
            case FL_DAISY -> Blocks.OXEYE_DAISY.getDefaultState();
            case SHROOM_R -> Blocks.RED_MUSHROOM.getDefaultState();
            case SHROOM_B -> Blocks.BROWN_MUSHROOM.getDefaultState();
            case SPRUCE_LOG -> Blocks.SPRUCE_LOG.getDefaultState();
            case SPRUCE_LEAVES -> Blocks.SPRUCE_LEAVES.getDefaultState().with(Properties.PERSISTENT, true);
            case BUSH -> Blocks.FLOWERING_AZALEA_LEAVES.getDefaultState().with(Properties.PERSISTENT, true);
            case W_A -> Blocks.SPRUCE_PLANKS.getDefaultState();
            case W_B -> Blocks.COBBLESTONE.getDefaultState();
            case W_C -> Blocks.BIRCH_PLANKS.getDefaultState();
            case POST_A -> Blocks.STRIPPED_SPRUCE_LOG.getDefaultState();
            case POST_B -> Blocks.DARK_OAK_LOG.getDefaultState();
            case POST_C -> Blocks.OAK_LOG.getDefaultState();
            case FLOOR_A -> Blocks.DARK_OAK_PLANKS.getDefaultState();
            case FLOOR_B -> Blocks.SPRUCE_PLANKS.getDefaultState();
            case FLOOR_C -> Blocks.OAK_PLANKS.getDefaultState();
            case ROOF_A_N -> stairs(Blocks.DARK_OAK_STAIRS, Direction.NORTH);
            case ROOF_A_E -> stairs(Blocks.DARK_OAK_STAIRS, Direction.EAST);
            case ROOF_A_S -> stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH);
            case ROOF_A_W -> stairs(Blocks.DARK_OAK_STAIRS, Direction.WEST);
            case ROOF_B_N -> stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.NORTH);
            case ROOF_B_E -> stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.EAST);
            case ROOF_B_S -> stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.SOUTH);
            case ROOF_B_W -> stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.WEST);
            case ROOF_C_N -> stairs(Blocks.BRICK_STAIRS, Direction.NORTH);
            case ROOF_C_E -> stairs(Blocks.BRICK_STAIRS, Direction.EAST);
            case ROOF_C_S -> stairs(Blocks.BRICK_STAIRS, Direction.SOUTH);
            case ROOF_C_W -> stairs(Blocks.BRICK_STAIRS, Direction.WEST);
            case RIDGE_A -> Blocks.DARK_OAK_PLANKS.getDefaultState();
            case RIDGE_B -> Blocks.DEEPSLATE_TILES.getDefaultState();
            case RIDGE_C -> Blocks.BRICKS.getDefaultState();
            case CHEST_N -> chest(Direction.NORTH);
            case CHEST_E -> chest(Direction.EAST);
            case CHEST_S -> chest(Direction.SOUTH);
            case CHEST_W -> chest(Direction.WEST);
            case BOOKSHELF -> Blocks.BOOKSHELF.getDefaultState();
            case CRAFTING -> Blocks.CRAFTING_TABLE.getDefaultState();
            case BARREL -> Blocks.BARREL.getDefaultState();
            case HAY -> Blocks.HAY_BLOCK.getDefaultState();
            case PUMPKIN -> Blocks.PUMPKIN.getDefaultState();
            case FENCE -> Blocks.SPRUCE_FENCE.getDefaultState();
            case LANTERN_S -> Blocks.LANTERN.getDefaultState();
            case LANTERN_H -> Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true);
            case CAMPFIRE -> Blocks.CAMPFIRE.getDefaultState();
        };
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> getCodec() { return CODEC; }

    @Override
    public void carve(ChunkRegion chunkRegion, long seed, NoiseConfig noiseConfig, BiomeAccess biomeAccess,
                      StructureAccessor structureAccessor, Chunk chunk, GenerationStep.Carver carverStep) { }

    @Override
    public void buildSurface(ChunkRegion region, StructureAccessor structures, NoiseConfig noiseConfig, Chunk chunk) { }

    @Override
    public void populateEntities(ChunkRegion region) { }

    @Override
    public CompletableFuture<Chunk> populateNoise(Blender blender, NoiseConfig noiseConfig, StructureAccessor structureAccessor, Chunk chunk) {
        int sx = chunk.getPos().getStartX(), sz = chunk.getPos().getStartZ();
        Heightmap floor = chunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG);
        NightCity.Col col = new NightCity.Col();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                NightCity.column(sx + lx, sz + lz, col);
                for (int y = 0; y <= col.top; y++) {
                    B b = col.a[y];
                    if (b == B.AIR) continue;
                    BlockState st = STATES[b.ordinal()];
                    chunk.setBlockState(pos.set(lx, y, lz), st, false);
                    floor.trackUpdate(lx, y, lz, st);
                    surface.trackUpdate(lx, y, lz, st);
                }
                // сундуки с лутом: данные блока-сущности с таблицей добычи (после установки блока — заменяют заглушку)
                for (int[] c : col.chests) {
                    NbtCompound nbt = new NbtCompound();
                    nbt.putString("id", "minecraft:chest");
                    nbt.putInt("x", sx + lx); nbt.putInt("y", c[0]); nbt.putInt("z", sz + lz);
                    nbt.putString("LootTable", ComboRush.MOD_ID + ":chests/" + NightCity.CHEST_TABLES[c[1]]);
                    nbt.putLong("LootTableSeed", ((long) (sx + lx) * 341873128712L) ^ ((long) (sz + lz) * 132897987541L) ^ c[0]);
                    chunk.addPendingBlockEntityNbt(nbt);
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getSeaLevel() { return NightCity.GROUND; }

    @Override
    public int getMinimumY() { return 0; }

    @Override
    public int getWorldHeight() { return NightCity.HEIGHT; }

    @Override
    public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world, NoiseConfig noiseConfig) {
        NightCity.Col col = new NightCity.Col();
        NightCity.column(x, z, col);
        int y = col.top;
        while (y > 0 && col.a[y] == B.AIR) y--;
        return y + 1;
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
        NightCity.Col col = new NightCity.Col();
        NightCity.column(x, z, col);
        BlockState[] states = new BlockState[NightCity.HEIGHT];
        for (int y = 0; y < states.length; y++) states[y] = STATES[col.a[y].ordinal()];
        return new VerticalBlockSample(0, states);
    }

    @Override
    public void getDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) { }
}
