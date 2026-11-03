package com.derevna230.comborush.car;

import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.item.ModItems;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Гараж: Shift+ПКМ по пустой машине. Прокачка за эссенцию и кнопка «забрать в инвентарь» (улучшения сохраняются) */
public final class CarGarageGui {
    private static final int[] SLOTS = {10, 11, 12, 13, 14};
    private static final double MAX_DIST_SQ = 10 * 10;

    private final ServerPlayerEntity player;
    private final CarEntity car;
    private final SimpleGui gui;

    public static void open(ServerPlayerEntity player, CarEntity car) {
        new CarGarageGui(player, car).gui.open();
    }

    private CarGarageGui(ServerPlayerEntity player, CarEntity car) {
        this.player = player;
        this.car = car;
        this.gui = new SimpleGui(ScreenHandlerType.GENERIC_9X3, player, false);
        gui.setTitle(Text.literal("Гараж"));
        render();
    }

    private void render() {
        PlayerProfile pr = PlayerProfile.of(player);
        for (int i = 0; i < 27; i++)
            gui.setSlot(i, new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE).setName(Text.literal(" ")));

        gui.setSlot(4, new GuiElementBuilder(ModItems.CARS.get(car.def().id()))
            .setName(Text.literal("§e§l" + carName()))
            .setLore(List.of(
                Text.literal("§7Улучшено: §f" + CarUpgrades.total(car.upgrades()) + " §7из §f" + (CarUpgrades.MAX * CarUpgrades.KEYS.length)),
                Text.literal("§7Мест: §f" + car.def().seatCount()),
                Text.literal("§7Пробел — ручник, Ctrl — нитро"))));

        for (int i = 0; i < CarUpgrades.KEYS.length; i++) {
            final String key = CarUpgrades.KEYS[i];
            int lvl = car.level(key);
            int cost = CarUpgrades.cost(key, lvl);
            boolean maxed = lvl >= CarUpgrades.MAX || cost < 0;
            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal("§7Уровень: " + bar(lvl)));
            lore.add(Text.literal("§f" + CarUpgrades.levelText(key, lvl)));
            if (!maxed) {
                lore.add(Text.literal("§8Дальше: §7" + CarUpgrades.levelText(key, lvl + 1)));
                lore.add(Text.literal("§dЦена: " + cost + " эссенции §7(у вас " + pr.essence() + ")"));
                lore.add(Text.literal("§aЛКМ §7— улучшить"));
            } else lore.add(Text.literal("§6Максимальный уровень"));
            gui.setSlot(SLOTS[i], new GuiElementBuilder(CarUpgrades.icon(key))
                .setName(Text.literal((maxed ? "§6" : "§b") + CarUpgrades.title(key)))
                .setLore(lore)
                .glow(lvl > 0)
                .setCallback((slot, type, action) -> buy(key)));
        }

        gui.setSlot(16, new GuiElementBuilder(Items.CHEST_MINECART)
            .setName(Text.literal("§aЗабрать машину"))
            .setLore(List.of(Text.literal("§7Машина вернётся в инвентарь"), Text.literal("§7вместе со всеми улучшениями.")))
            .setCallback((slot, type, action) -> {
                if (!alive()) return;
                gui.close();
                car.pickUp(player);
            }));

        gui.setSlot(22, new GuiElementBuilder(Items.AMETHYST_SHARD)
            .setName(Text.literal("§dЭссенция: " + pr.essence()))
            .setLore(List.of(Text.literal("§7Эссенцию дают события и награды."))));
    }

    private boolean alive() {
        if (car.isRemoved() || car.hasPassengers() || player.squaredDistanceTo(car) > MAX_DIST_SQ) {
            player.sendMessage(Text.literal("§cМашина недоступна."), true);
            gui.close();
            return false;
        }
        return true;
    }

    private void buy(String key) {
        if (!alive()) return;
        int lvl = car.level(key);
        int cost = CarUpgrades.cost(key, lvl);
        PlayerProfile pr = PlayerProfile.of(player);
        String msg; boolean ok = false;
        if (lvl >= CarUpgrades.MAX || cost < 0) msg = "Уже максимальный уровень.";
        else if (pr.essence() < cost) msg = "Не хватает эссенции: нужно " + cost + ".";
        else {
            player.setAttached(PlayerProfile.TYPE, pr.addEssence(-cost));
            car.setUpgrades(CarUpgrades.set(car.upgrades(), key, lvl + 1));
            msg = CarUpgrades.title(key) + ": " + CarUpgrades.levelText(key, lvl + 1);
            ok = true;
            player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 0.6f, 1.4f);
            player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 1f, 1.2f);
        }
        player.sendMessage(Text.literal((ok ? "§a" : "§c") + msg), true);
        render();
    }

    private static String bar(int lvl) {
        StringBuilder sb = new StringBuilder("§e");
        for (int i = 0; i < CarUpgrades.MAX; i++) sb.append(i < lvl ? "▮" : "§8▯");
        return sb.append(" §7(").append(lvl).append('/').append(CarUpgrades.MAX).append(')').toString();
    }

    private String carName() {
        return switch (car.def().id()) {
            case "mustang" -> "Красный масл-кар";
            case "mustang_modified" -> "Зелёный гоночный масл-кар";
            case "trueno" -> "Белый дрифт-хэтчбек";
            default -> car.def().id();
        };
    }
}
