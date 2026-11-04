package com.derevna230.comborush.universe;

import com.derevna230.comborush.ComboRush;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
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

/** Генератор небесных миров («lobby» — остров, «standoff» — сцена меню). Блоки считает {@link SkyWorlds} */
public class SkyChunkGenerator extends ChunkGenerator {
    public static final MapCodec<SkyChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
        com.mojang.serialization.Codec.STRING.fieldOf("kind").forGetter(g -> g.kind)
    ).apply(i, i.stable(SkyChunkGenerator::new)));

    private final String kind;

    public SkyChunkGenerator(BiomeSource biomeSource, String kind) {
        super(biomeSource);
        this.kind = kind;
    }

    public static void register() {
        Registry.register(Registries.CHUNK_GENERATOR, Identifier.of(ComboRush.MOD_ID, "sky"), CODEC);
    }

    @Override protected MapCodec<? extends ChunkGenerator> getCodec() { return CODEC; }

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
        BlockState[] col = new BlockState[SkyWorlds.HEIGHT];
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                SkyWorlds.column(kind, sx + lx, sz + lz, col);
                for (int y = 0; y < col.length; y++) {
                    BlockState st = col[y];
                    if (st == null) continue;
                    chunk.setBlockState(pos.set(lx, y, lz), st, false);
                    floor.trackUpdate(lx, y, lz, st);
                    surface.trackUpdate(lx, y, lz, st);
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override public int getSeaLevel() { return 0; }
    @Override public int getMinimumY() { return 0; }
    @Override public int getWorldHeight() { return SkyWorlds.HEIGHT; }

    @Override
    public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world, NoiseConfig noiseConfig) {
        return SkyWorlds.topY(kind, x, z, new BlockState[SkyWorlds.HEIGHT]);
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
        BlockState[] col = new BlockState[SkyWorlds.HEIGHT];
        SkyWorlds.column(kind, x, z, col);
        BlockState[] states = new BlockState[SkyWorlds.HEIGHT];
        for (int y = 0; y < states.length; y++) states[y] = col[y] == null ? net.minecraft.block.Blocks.AIR.getDefaultState() : col[y];
        return new VerticalBlockSample(0, states);
    }

    @Override public void getDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) { }
}
