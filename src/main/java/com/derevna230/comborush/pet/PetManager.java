package com.derevna230.comborush.pet;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.armor.ArmorSetApplier;
import com.derevna230.comborush.armor.ArmorSetDef;
import com.derevna230.comborush.armor.ArmorSetRegistry;
import com.derevna230.comborush.item.PetItem;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Призванные питомцы. Какой питомец выбран, хранится у игрока (переживает перезаход и смерть),
 * а сама сущность в мире не сохраняется: менеджер создаёт её заново рядом с хозяином.
 * Питомец работает, только пока его предмет лежит в инвентаре.
 */
public final class PetManager {
    private PetManager() {}

    public static final AttachmentType<String> ACTIVE = AttachmentRegistry.create(
        Identifier.of(ComboRush.MOD_ID, "active_pet"),
        b -> b.persistent(Codec.STRING).copyOnDeath());

    private static final Map<UUID, CRPetEntity> LIVE = new HashMap<>();

    /** Загрузить класс при старте, чтобы тип данных игрока был зарегистрирован до загрузки мира */
    public static void init() { }

    /** id «комплекта» призванного питомца (pet_<id>) — для бонусов */
    public static String activeSet(ServerPlayerEntity p) {
        String id = p.getAttached(ACTIVE);
        return id == null || id.isEmpty() ? null : "pet_" + id;
    }

    public static void toggle(ServerPlayerEntity p, String petId) {
        String cur = p.getAttached(ACTIVE);
        ArmorSetDef def = ArmorSetRegistry.get("pet_" + petId);
        String name = def == null ? petId : def.name;
        if (petId.equals(cur)) {
            p.removeAttached(ACTIVE);
            despawn(p);
            p.sendMessage(Text.literal("§7" + name + " отправился отдыхать."), true);
        } else {
            p.setAttached(ACTIVE, petId);
            despawn(p);
            spawn(p, petId);
            p.sendMessage(Text.literal("§a" + name + " теперь с тобой!"), true);
        }
        ArmorSetApplier.sync(p);
    }

    private static boolean hasItem(ServerPlayerEntity p, String petId) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.getItem() instanceof PetItem pi && pi.petId().equals(petId)) return true;
        }
        return false;
    }

    private static void spawn(ServerPlayerEntity p, String petId) {
        if (!p.isAlive() || p.isSpectator()) return;
        ServerWorld w = p.getServerWorld();
        CRPetEntity e = ModEntities.PET.create(w);
        if (e == null) return;
        e.setup(petId, p);
        e.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw(), 0);
        w.spawnEntity(e);
        w.spawnParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 0.5, p.getZ(), 8, 0.4, 0.3, 0.4, 0);
        w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.6f, 0.7f);
        LIVE.put(p.getUuid(), e);
    }

    public static void despawn(ServerPlayerEntity p) {
        CRPetEntity e = LIVE.remove(p.getUuid());
        if (e != null && !e.isRemoved()) e.discard();
    }

    /** Раз в полсекунды: держать питомца рядом с хозяином */
    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 10 != 0) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            String id = p.getAttached(ACTIVE);
            CRPetEntity e = LIVE.get(p.getUuid());
            if (id == null || id.isEmpty()) {
                if (e != null) despawn(p);
                continue;
            }
            if (!hasItem(p, id)) {
                p.removeAttached(ACTIVE);
                despawn(p);
                ArmorSetApplier.sync(p);
                p.sendMessage(Text.literal("§7Питомец ушёл: его предмета больше нет в инвентаре."), true);
                continue;
            }
            if (!p.isAlive() || p.isSpectator()) { if (e != null) despawn(p); continue; }
            if (e == null || e.isRemoved() || e.getWorld() != p.getWorld() || !e.petId().equals(id)) {
                despawn(p);
                spawn(p, id);
            }
        }
        LIVE.values().removeIf(CRPetEntity::isRemoved);
    }
}
