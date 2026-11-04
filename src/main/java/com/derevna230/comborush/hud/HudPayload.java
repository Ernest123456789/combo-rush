package com.derevna230.comborush.hud;

import com.derevna230.comborush.ComboRush;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** kind: "boosters" | "state" | "pop"; json — тело (так формат можно менять, не трогая сетевой слой) */
public record HudPayload(String kind, String json) implements CustomPayload {
    public static final CustomPayload.Id<HudPayload> ID =
        new CustomPayload.Id<>(Identifier.of(ComboRush.MOD_ID, "hud"));

    public static final PacketCodec<RegistryByteBuf, HudPayload> CODEC = PacketCodec.tuple(
        PacketCodecs.STRING, HudPayload::kind,
        PacketCodecs.string(400_000), HudPayload::json,   // тело может быть большим (подсказки всех комплектов и кейсов); лимит пакета — 1 МиБ
        HudPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
