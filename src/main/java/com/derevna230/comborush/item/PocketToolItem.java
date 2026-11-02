package com.derevna230.comborush.item;

import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.SmeltingRecipe;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;
import java.util.Optional;

/**
 * Карманные станки из Мастерской: верстак, наковальня и чаровальня открывают своё меню прямо из руки,
 * печь мгновенно переплавляет стопку во второй руке за уголь (1 уголь = 8 предметов).
 */
public class PocketToolItem extends Item {
    public enum Kind { CRAFTING, ANVIL, ENCHANTING, FURNACE }

    private final Kind kind;

    public PocketToolItem(Kind kind, Settings settings) {
        super(settings);
        this.kind = kind;
    }

    public Kind kind() { return kind; }

    @Override public boolean hasGlint(ItemStack stack) { return true; }

    /**
     * Предмет остался для старых сохранений: ПКМ превращает его в открытую навсегда кнопку справа от инвентаря.
     * Новые станки покупаются в Мастерской сразу как кнопки.
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack st = user.getStackInHand(hand);
        if (world.isClient || !(user instanceof ServerPlayerEntity p)) return TypedActionResult.success(st, true);
        com.derevna230.comborush.workshop.PocketTools.unlock(p, kind);
        if (!p.isCreative()) st.decrement(1);
        return TypedActionResult.success(st, false);
    }

    /** Открыть станок: верстак, наковальня и чаровальня — своё меню, печь — переплавить стопку */
    public static void openFor(ServerPlayerEntity p, Kind kind) {
        ServerWorld world = p.getServerWorld();
        ScreenHandlerContext ctx = ScreenHandlerContext.create(world, p.getBlockPos());
        switch (kind) {
            case CRAFTING -> p.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, inv, pl) ->
                new CraftingScreenHandler(id, inv, ctx) {
                    @Override public boolean canUse(PlayerEntity player) { return true; }
                }, Text.literal("Карманный верстак")));
            case ANVIL -> p.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, inv, pl) ->
                new AnvilScreenHandler(id, inv, ctx) {
                    @Override public boolean canUse(PlayerEntity player) { return true; }
                }, Text.literal("Карманная наковальня")));
            case ENCHANTING -> p.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, inv, pl) ->
                new EnchantmentScreenHandler(id, inv, ctx) {
                    @Override public boolean canUse(PlayerEntity player) { return true; }
                }, Text.literal("Карманная чаровальня")));
            case FURNACE -> smeltFromHands(p);
        }
    }

    /** Печь: переплавляется стопка на курсоре (если взята в инвентаре) или в основной руке, иначе в запасной */
    private static void smeltFromHands(ServerPlayerEntity p) {
        ItemStack cursor = p.currentScreenHandler.getCursorStack();
        if (!cursor.isEmpty()) { smelt(p, cursor); p.currentScreenHandler.sendContentUpdates(); return; }
        ItemStack main = p.getStackInHand(Hand.MAIN_HAND);
        if (!main.isEmpty()) { smelt(p, main); return; }
        smelt(p, p.getStackInHand(Hand.OFF_HAND));
    }

    /** Переплавить стопку; топливо берётся из инвентаря */
    private static void smelt(ServerPlayerEntity p, ItemStack input) {
        ServerWorld w = p.getServerWorld();
        if (input.isEmpty()) { p.sendMessage(Text.literal("§eВозьми курсором или в руку то, что нужно переплавить."), true); return; }
        Optional<RecipeEntry<SmeltingRecipe>> r = w.getRecipeManager().getFirstMatch(RecipeType.SMELTING, new SingleStackRecipeInput(input), w);
        if (r.isEmpty()) { p.sendMessage(Text.literal("§cЭто нельзя переплавить."), true); return; }
        ItemStack result = r.get().value().getResult(w.getRegistryManager());
        if (result.isEmpty()) return;

        int want = input.getCount();
        int capacity = fuelCapacity(p);
        int n = Math.min(want, capacity);
        if (n <= 0) { p.sendMessage(Text.literal("§cНужно топливо: уголь, древесный уголь, огненный стержень или блок угля."), true); return; }
        burnFuel(p, n);
        input.decrement(n);
        int total = result.getCount() * n;
        while (total > 0) {
            ItemStack out = result.copy();
            int c = Math.min(total, out.getMaxCount());
            out.setCount(c);
            total -= c;
            p.getInventory().offerOrDrop(out);
        }
        float xp = r.get().value().getExperience() * n;
        int orbs = (int) Math.floor(xp) + (Math.random() < xp - Math.floor(xp) ? 1 : 0);
        if (orbs > 0) ExperienceOrbEntity.spawn(w, p.getPos(), orbs);
        w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE, SoundCategory.PLAYERS, 1f, 1f);
        p.sendMessage(Text.literal("§6Переплавлено: §f" + n + (n < want ? " §7(не хватило топлива на остальное)" : "")), true);
    }

    /** Сколько предметов можно переплавить топливом из инвентаря */
    private static int fuelCapacity(ServerPlayerEntity p) {
        int sum = 0;
        for (int i = 0; i < p.getInventory().size(); i++) sum += perItem(p.getInventory().getStack(i)) * p.getInventory().getStack(i).getCount();
        return sum;
    }

    private static int perItem(ItemStack s) {
        if (s.isOf(Items.COAL) || s.isOf(Items.CHARCOAL)) return 8;
        if (s.isOf(Items.BLAZE_ROD)) return 12;
        if (s.isOf(Items.COAL_BLOCK)) return 80;
        return 0;
    }

    /** Сжечь топливо на n предметов — начиная с самого «мелкого» топлива */
    private static void burnFuel(ServerPlayerEntity p, int n) {
        int need = n;
        for (int per : new int[]{8, 12, 80}) {
            for (int i = 0; i < p.getInventory().size() && need > 0; i++) {
                ItemStack s = p.getInventory().getStack(i);
                if (perItem(s) != per) continue;
                while (!s.isEmpty() && need > 0) { s.decrement(1); need -= per; }
            }
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("§7Старый предмет: ПКМ — открыть этот станок навсегда"));
        tooltip.add(Text.literal("§7кнопкой справа от окна инвентаря."));
        tooltip.add(Text.literal("§8Теперь станки покупаются в Мастерской сразу как кнопки"));
    }
}
