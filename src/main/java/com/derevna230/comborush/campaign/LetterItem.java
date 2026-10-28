package com.derevna230.comborush.campaign;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

import java.util.List;
import java.util.UUID;

/** Письмо Режиссёра для задания курьера. Кому и от кого — в CUSTOM_DATA */
public class LetterItem extends Item {
    public LetterItem(Settings settings) { super(settings); }

    public static ItemStack make(UUID owner, int slot, String to) {
        ItemStack st = new ItemStack(com.derevna230.comborush.item.ModItems.LETTER);
        NbtCompound n = new NbtCompound();
        n.putUuid("owner", owner);
        n.putInt("slot", slot);
        n.putString("to", to);
        st.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
        st.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§fПисьмо: §e" + to));
        return st;
    }

    private static NbtCompound data(ItemStack st) {
        NbtComponent c = st.get(DataComponentTypes.CUSTOM_DATA);
        return c == null ? new NbtCompound() : c.copyNbt();
    }

    public static boolean isFor(ItemStack st, UUID owner, int slot) {
        if (!(st.getItem() instanceof LetterItem)) return false;
        NbtCompound n = data(st);
        return n.containsUuid("owner") && n.getUuid("owner").equals(owner) && n.getInt("slot") == slot;
    }

    public static boolean ownedBy(ItemStack st, UUID owner) {
        if (!(st.getItem() instanceof LetterItem)) return false;
        NbtCompound n = data(st);
        return n.containsUuid("owner") && n.getUuid("owner").equals(owner);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        NbtCompound n = data(stack);
        tooltip.add(Text.literal("§7Запечатано сургучом Режиссёра"));
        if (n.contains("to")) tooltip.add(Text.literal("§7Кому: §f" + n.getString("to")));
        tooltip.add(Text.literal("§eОтнеси адресату, пока не вышло время"));
    }
}
