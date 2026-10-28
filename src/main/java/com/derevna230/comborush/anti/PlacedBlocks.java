package com.derevna230.comborush.anti;

import com.derevna230.comborush.ComboRush;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Защита от фарма «шёлковое касание → поставил руду → сломал снова».
 * Запоминает рудные блоки, поставленные игроками; такие блоки не дают очков. Данные хранятся в файле мира.
 * Без миксинов: ловим правый клик блоком-рудой и на следующем тике проверяем, появился ли блок.
 */
public class PlacedBlocks {
    private static final int MAX_PER_WORLD = 200_000;
    private static final Gson GSON = new GsonBuilder().create();

    private record Pending(ServerWorld world, BlockPos pos, Block block) {}

    private static final Map<String, LinkedHashSet<Long>> DATA = new HashMap<>();
    private static final List<Pending> PENDING = new ArrayList<>();
    private static Path file;
    private static boolean dirty = false;

    public static void init() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient || !(world instanceof ServerWorld sw)) return ActionResult.PASS;
            ItemStack st = player.getStackInHand(hand);
            if (!(st.getItem() instanceof BlockItem bi)) return ActionResult.PASS;
            Block block = bi.getBlock();
            if (!block.getDefaultState().isIn(ConventionalBlockTags.ORES)) return ActionResult.PASS;

            // куда встанет блок: в заменяемый блок (трава, вода), иначе рядом с кликнутой гранью
            BlockPos clicked = hit.getBlockPos();
            BlockPos target = world.getBlockState(clicked).isReplaceable() ? clicked : clicked.offset(hit.getSide());
            if (!world.getBlockState(target).isReplaceable()) return ActionResult.PASS;   // место занято — не поставится
            PENDING.add(new Pending(sw, target, block));
            return ActionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (PENDING.isEmpty()) return;
            for (Pending p : PENDING)
                if (p.world().getBlockState(p.pos()).isOf(p.block())) add(p.world(), p.pos());
            PENDING.clear();
            if (dirty && server.getTicks() % 1200 == 0) save();
        });
        ServerLifecycleEvents.SERVER_STARTED.register(PlacedBlocks::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> save());
    }

    private static String key(World w) { return w.getRegistryKey().getValue().toString(); }

    private static void add(World w, BlockPos pos) {
        LinkedHashSet<Long> set = DATA.computeIfAbsent(key(w), k -> new LinkedHashSet<>());
        set.add(pos.asLong());
        if (set.size() > MAX_PER_WORLD) {              // старейшие записи вытесняются
            Iterator<Long> it = set.iterator();
            it.next(); it.remove();
        }
        dirty = true;
    }

    /** Поставлен ли блок игроком (без удаления записи) — чтобы сюжетные постройки не ломали чужое */
    public static boolean isPlaced(World w, BlockPos pos) {
        Set<Long> set = DATA.get(key(w));
        return set != null && set.contains(pos.asLong());
    }

    /** @return true, если блок был поставлен игроком (запись при этом удаляется) */
    public static boolean consume(World w, BlockPos pos) {
        Set<Long> set = DATA.get(key(w));
        if (set != null && set.remove(pos.asLong())) { dirty = true; return true; }
        return false;
    }

    private static void load(MinecraftServer server) {
        file = server.getSavePath(WorldSavePath.ROOT).resolve("combo-rush-placed.json");
        DATA.clear();
        try {
            if (Files.exists(file)) {
                Map<String, long[]> m = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, long[]>>() {}.getType());
                if (m != null) m.forEach((k, v) -> {
                    LinkedHashSet<Long> set = new LinkedHashSet<>();
                    for (long l : v) set.add(l);
                    DATA.put(k, set);
                });
            }
        } catch (Exception e) {
            ComboRush.LOGGER.error("Не удалось прочитать список поставленных руд", e);
        }
    }

    private static void save() {
        if (file == null) return;
        try {
            Map<String, long[]> out = new HashMap<>();
            DATA.forEach((k, set) -> out.put(k, set.stream().mapToLong(Long::longValue).toArray()));
            Files.writeString(file, GSON.toJson(out), StandardCharsets.UTF_8);
            dirty = false;
        } catch (Exception e) {
            ComboRush.LOGGER.error("Не удалось сохранить список поставленных руд", e);
        }
    }
}
