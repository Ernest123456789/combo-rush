package com.derevna230.comborush.shop;

import com.derevna230.comborush.booster.BoosterDef;
import com.derevna230.comborush.booster.BoosterRegistry;
import com.derevna230.comborush.booster.Rarity;
import com.derevna230.comborush.consumable.ConsumableDef;
import com.derevna230.comborush.consumable.ConsumableRegistry;
import com.derevna230.comborush.cycle.CycleManager;
import com.derevna230.comborush.cycle.CyclePhase;
import com.derevna230.comborush.data.OwnedBooster;
import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.hud.HudSync;
import com.derevna230.comborush.promo.PromoManager;
import com.derevna230.comborush.score.BoosterEffects;
import com.derevna230.comborush.shop.ShopService.Res;
import com.derevna230.comborush.shop.ShopService.State;
import com.derevna230.comborush.species.SpeciesRegistry;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Магазин-сундук (sgui) для игроков без клиентского мода.
 * С клиентским модом вместо него открывается экран в стиле Balatro (см. ShopNet и клиентский ShopScreen).
 */
public class ShopGui {
    private static final int[] OFFER_SLOTS = {11, 13, 15};
    private static final int[] C_OFFER_SLOTS = {20, 24};
    private static final int REROLL_SLOT = 22, INFO_SLOT = 31, EXTRA_SLOT = 33;
    private static final int OWNED_START = 36;
    private static final int[] C_OWNED_SLOTS = {47, 51};

    private final ServerPlayerEntity player;
    private final SimpleGui gui;

    /** Вызывается после сброса: витрина генерируется заново */
    public static void invalidate(UUID id) { ShopService.invalidate(id); }

    public static void open(ServerPlayerEntity player) {
        if (CycleManager.phase() != CyclePhase.NORMAL) {
            player.sendMessage(Text.literal("§cМагазин закрыт во время события."), false);
            return;
        }
        if (HudSync.has(player)) { ShopNet.open(player); return; }     // красивый экран для клиентского мода
        new ShopGui(player).gui.open();
    }

    private ShopGui(ServerPlayerEntity player) {
        this.player = player;
        this.gui = new SimpleGui(ScreenHandlerType.GENERIC_9X6, player, false);
        gui.setTitle(Text.literal("Магазин Режиссёра"));
        state().pending = -1;
        render();
    }

    private State state() { return ShopService.state(player); }

    private void render() {
        PlayerProfile p = PlayerProfile.of(player);
        BoosterEffects fx = ShopService.fx(player);
        State st = state();

        for (int i = 0; i < 54; i++)
            gui.setSlot(i, new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE).setName(Text.literal(" ")));

        for (int i = 0; i < OFFER_SLOTS.length; i++) {
            if (i >= st.offers.size()) continue;
            BoosterDef d = BoosterRegistry.get(st.offers.get(i));
            if (d == null) continue;
            final int idx = i;
            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal(Rarity.color(d.rarity) + Rarity.label(d.rarity)));
            lore.add(Text.literal("§7" + d.description));
            lore.add(Text.literal("§eЦена: " + ShopService.price(p, d, fx) + " монет"));
            lore.add(Text.literal("§aЛКМ §7— купить"));
            if (d.canBeNegative()) {
                lore.add(Text.literal("§cПКМ §7— «негативный»: +1 слот, без игровой части"));
                lore.add(Text.literal("§cShift+ПКМ §7— «негативный»: +1 слот, без очковой части"));
            }
            gui.setSlot(OFFER_SLOTS[i], new GuiElementBuilder(icon(d.icon))
                .setName(Text.literal(Rarity.color(d.rarity) + d.name)).setLore(lore)
                .glow(d.rarity.equals("epic") || d.rarity.equals("legendary"))
                .setCallback((slot, type, action) -> buy(idx, type)));
        }

        for (int i = 0; i < C_OFFER_SLOTS.length; i++) {
            if (i >= st.cOffers.size()) continue;
            ConsumableDef d = ConsumableRegistry.get(st.cOffers.get(i));
            if (d == null) continue;
            final int idx = i;
            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal("§8Расходник · " + Rarity.color(d.rarity) + Rarity.label(d.rarity)));
            lore.add(Text.literal("§7" + d.description));
            lore.add(Text.literal("§eЦена: " + ShopService.cPrice(p, d, fx) + " монет"));
            lore.add(Text.literal("§aЛКМ §7— купить (слотов расходников: "
                + p.consumables().size() + "/" + PlayerProfile.CONSUMABLE_SLOTS + ")"));
            gui.setSlot(C_OFFER_SLOTS[i], new GuiElementBuilder(icon(d.icon))
                .setName(Text.literal(Rarity.color(d.rarity) + d.name)).setLore(lore)
                .setCallback((slot, type, action) -> done(ShopService.buyConsumable(player, idx))));
        }

        gui.setSlot(REROLL_SLOT, new GuiElementBuilder(Items.ENDER_EYE)
            .setName(Text.literal("§bПерекрутить витрину"))
            .setLore(List.of(Text.literal("§eЦена: " + ShopService.rerollCost(st, fx) + " монет"),
                             Text.literal("§7Обновляет и усилители, и расходники")))
            .setCallback((slot, type, action) -> done(ShopService.reroll(player))));

        gui.setSlot(INFO_SLOT, new GuiElementBuilder(Items.GOLD_INGOT)
            .setName(Text.literal("§6Монеты: " + p.coins()))
            .setLore(List.of(
                Text.literal("§7Существо: " + SpeciesRegistry.get(p.species()).name + " §8(ранг " + p.rank() + ")"),
                Text.literal("§7Уровень: " + p.level()),
                Text.literal("§7Слотов: " + p.boosters().size() + "/" + p.slotCapacity()),
                Text.literal("§7Событий: " + p.eventsDone() + "/50"),
                Text.literal("§7Сбросов: " + p.resets() + "  Эссенция: " + p.essence()),
                Text.literal(PromoManager.activeNames().isEmpty() ? "§8Акций нет"
                    : "§d★ " + String.join(", ", PromoManager.activeNames())))));

        gui.setSlot(EXTRA_SLOT, new GuiElementBuilder(Items.CHEST)
            .setName(Text.literal("§dКупить доп. слот"))
            .setLore(List.of(Text.literal("§eЦена: " + ShopService.slotCost(p) + " монет"),
                             Text.literal("§7Куплено: " + p.extraSlots() + "/4")))
            .setCallback((slot, type, action) -> done(ShopService.buyExtraSlot(player))));

        ConsumableDef pending = st.pending >= 0 && st.pending < p.consumables().size()
            ? ConsumableRegistry.get(p.consumables().get(st.pending)) : null;
        for (int i = 0; i < 9; i++) {
            int gs = OWNED_START + i;
            if (i < p.boosters().size()) {
                final int idx = i;
                OwnedBooster o = p.boosters().get(i);
                BoosterDef d = BoosterRegistry.get(o.id());
                if (d == null) continue;
                List<Text> lore = new ArrayList<>();
                lore.add(Text.literal(Rarity.color(d.rarity) + Rarity.label(d.rarity)));
                lore.add(Text.literal("§7" + d.description));
                lore.add(Text.literal("§7Уровень: §f" + o.level() + "/" + OwnedBooster.MAX_LEVEL
                    + (o.level() > 0 ? " §8(+" + Math.round(o.level() * BoosterEffects.LEVEL_STEP * 100) + "% к плюсам)" : "")));
                if (!o.edition().isEmpty())
                    lore.add(Text.literal("§bИздание: §f" + OwnedBooster.editionName(o.edition())
                        + " §8(" + OwnedBooster.editionDesc(o.edition()) + ")"));
                if (o.negative()) lore.add(Text.literal("§cНегативный: потеряна часть «" + o.dropped() + "»"));
                if (pending != null) lore.add(Text.literal("§e▶ ЛКМ — применить «" + pending.name + "»"));
                else lore.add(Text.literal("§cПКМ §7— продать за §e" + ShopService.sellValue(o, fx)));
                gui.setSlot(gs, new GuiElementBuilder(icon(d.icon))
                    .setName(Text.literal((o.negative() ? "§c" : Rarity.color(d.rarity)) + d.name
                        + (o.level() > 0 ? " §7(ур. " + o.level() + ")" : "")))
                    .setLore(lore).glow(pending != null || !o.edition().isEmpty())
                    .setCallback((slot, type, action) -> {
                        if (state().pending >= 0) { if (type.isLeft) useOn(idx); }
                        else if (type.isRight) done(ShopService.sell(player, idx));
                    }));
            } else if (i < p.slotCapacity()) {
                gui.setSlot(gs, new GuiElementBuilder(Items.LIGHT_GRAY_STAINED_GLASS_PANE)
                    .setName(Text.literal("§7Свободный слот")));
            } else {
                gui.setSlot(gs, new GuiElementBuilder(Items.BARRIER)
                    .setName(Text.literal("§8Закрыто"))
                    .setLore(List.of(Text.literal("§7Слот 4: уровень 15, слот 5: уровень 30"))));
            }
        }

        for (int i = 0; i < C_OWNED_SLOTS.length; i++) {
            if (i < p.consumables().size()) {
                final int idx = i;
                ConsumableDef d = ConsumableRegistry.get(p.consumables().get(i));
                if (d == null) continue;
                boolean selected = st.pending == i;
                List<Text> lore = new ArrayList<>();
                lore.add(Text.literal("§8Расходник · " + Rarity.color(d.rarity) + Rarity.label(d.rarity)));
                lore.add(Text.literal("§7" + d.description));
                lore.add(Text.literal(selected ? "§eВыбери усилитель ниже (ЛКМ). Нажми снова, чтобы отменить."
                    : d.needsTarget() ? "§aЛКМ §7— использовать (потом выбрать усилитель)" : "§aЛКМ §7— использовать"));
                lore.add(Text.literal("§cПКМ §7— продать за §e" + ShopService.cSellValue(d)));
                gui.setSlot(C_OWNED_SLOTS[i], new GuiElementBuilder(icon(d.icon))
                    .setName(Text.literal(Rarity.color(d.rarity) + d.name)).setLore(lore).glow(selected)
                    .setCallback((slot, type, action) -> useConsumable(idx, type)));
            } else {
                gui.setSlot(C_OWNED_SLOTS[i], new GuiElementBuilder(Items.LIGHT_GRAY_STAINED_GLASS_PANE)
                    .setName(Text.literal("§7Слот расходника")));
            }
        }
    }

    private void done(Res r) {
        if (!r.ok()) msg("§c" + r.msg());
        else if (!r.msg().isEmpty()) msg("§a" + r.msg());
        render();
    }

    private void buy(int idx, ClickType click) {
        String mode = "";
        if (click.isRight) mode = click.shift ? "score" : "game";
        done(ShopService.buy(player, idx, mode));
    }

    private void useConsumable(int idx, ClickType click) {
        State st = state();
        PlayerProfile p = PlayerProfile.of(player);
        if (idx >= p.consumables().size()) return;
        ConsumableDef d = ConsumableRegistry.get(p.consumables().get(idx));
        if (d == null) return;

        if (click.isRight) { st.pending = -1; done(ShopService.sellConsumable(player, idx)); return; }
        if (d.needsTarget()) {
            st.pending = st.pending == idx ? -1 : idx;
            if (p.boosters().isEmpty()) { st.pending = -1; msg("§cУ тебя нет усилителей."); }
            render();
            return;
        }
        done(ShopService.useConsumable(player, idx, -1));
    }

    private void useOn(int boosterIdx) {
        State st = state();
        if (st.pending < 0) return;
        Res r = ShopService.useConsumable(player, st.pending, boosterIdx);
        if (r.ok()) st.pending = -1;
        done(r);
    }

    private static Item icon(String s) {
        Identifier id = Identifier.tryParse(s);
        return id == null ? Items.PAPER : Registries.ITEM.get(id);
    }
    private void msg(String s) { player.sendMessage(Text.literal(s), true); }
}
