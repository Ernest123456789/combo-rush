package com.derevna230.comborush.hud;

import com.derevna230.comborush.ComboRush;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Действие игрока в клиентском экране магазина.
 * action: buy (a — номер предложения, c — режим: "", "game", "score"), buyc (a), reroll, sell (a), sellc (a),
 * usec (a — номер расходника, b — номер усилителя или -1), slot, sync
 */
public record ShopActionPayload(String action, int a, int b, String c) implements CustomPayload {
    public static final CustomPayload.Id<ShopActionPayload> ID =
        new CustomPayload.Id<>(Identifier.of(ComboRush.MOD_ID, "shop_action"));

    public static final PacketCodec<RegistryByteBuf, ShopActionPayload> CODEC = PacketCodec.tuple(
        PacketCodecs.STRING, ShopActionPayload::action,
        PacketCodecs.INTEGER, ShopActionPayload::a,
        PacketCodecs.INTEGER, ShopActionPayload::b,
        PacketCodecs.STRING, ShopActionPayload::c,
        ShopActionPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
