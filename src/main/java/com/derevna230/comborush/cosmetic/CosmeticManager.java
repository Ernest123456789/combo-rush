package com.derevna230.comborush.cosmetic;

import com.derevna230.comborush.data.PlayerProfile;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Всё серверное: питомцы — обычные мобы Minecraft, поэтому ставить моды на клиент не нужно */
public class CosmeticManager {
    private static final String PET_TAG = "cr_pet";
    private static final String TEAM_PREFIX = "crt_";

    private static final Map<UUID, UUID> PETS = new HashMap<>();      // владелец → UUID питомца
    private static final Map<UUID, Vec3d> LAST_POS = new HashMap<>();

    private static CosmeticDef equipped(PlayerProfile prof, String type) {
        return CosmeticRegistry.get(prof.equippedId(type));
    }

    public static void tick(MinecraftServer server) {
        int t = server.getTicks();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            PlayerProfile prof = PlayerProfile.of(p);
            if (t % 4 == 0)   trail(p, prof);
            if (t % 20 == 0)  pet(server, p, prof);
            if (t % 100 == 0) title(server, p, prof);
        }
    }

    /** Применить изменения сразу (после покупки/надевания) */
    public static void refresh(ServerPlayerEntity p) {
        PlayerProfile prof = PlayerProfile.of(p);
        title(p.getServer(), p, prof);
        pet(p.getServer(), p, prof);
    }

    public static void onDisconnect(ServerPlayerEntity p) {
        MobEntity pet = findPet(p.getServerWorld(), p);
        if (pet != null) pet.discard();
        removeTracked(p.getServer(), p.getUuid());
        LAST_POS.remove(p.getUuid());
    }

    // ---------------- следы ----------------
    private static void trail(ServerPlayerEntity p, PlayerProfile prof) {
        Vec3d pos = p.getPos();
        Vec3d last = LAST_POS.put(p.getUuid(), pos);
        CosmeticDef def = equipped(prof, "trail");
        if (def == null || last == null || last.squaredDistanceTo(pos) < 0.01) return;
        Identifier id = Identifier.tryParse(def.particle);
        if (id == null) return;
        ParticleType<?> type = Registries.PARTICLE_TYPE.get(id);
        if (type instanceof ParticleEffect effect)
            p.getServerWorld().spawnParticles(effect, pos.x, pos.y + 0.1, pos.z,
                Math.max(1, def.count), 0.25, 0.05, 0.25, 0.01);
    }

    // ---------------- титулы (префикс команды: виден в чате, над головой и в табе) ----------------
    private static void title(MinecraftServer server, ServerPlayerEntity p, PlayerProfile prof) {
        Scoreboard sb = server.getScoreboard();
        String holder = p.getNameForScoreboard();
        AbstractTeam cur = p.getScoreboardTeam();
        if (cur != null && !cur.getName().startsWith(TEAM_PREFIX)) return; // не трогаем чужие команды

        CosmeticDef def = equipped(prof, "title");
        if (def == null) {
            if (cur instanceof Team t) sb.removeScoreHolderFromTeam(holder, t);
            return;
        }
        String name = TEAM_PREFIX + Integer.toHexString(def.id.hashCode());
        Team team = sb.getTeam(name);
        if (team == null) team = sb.addTeam(name);
        team.setPrefix(Text.literal(def.prefix));
        if (cur != team) sb.addScoreHolderToTeam(holder, team);
    }

    // ---------------- питомцы ----------------
    private static String ownerTag(UUID owner) { return PET_TAG + "_" + owner; }

    private static MobEntity findPet(ServerWorld w, ServerPlayerEntity owner) {
        String tag = ownerTag(owner.getUuid());
        List<MobEntity> list = w.getEntitiesByClass(MobEntity.class,
            owner.getBoundingBox().expand(64), m -> m.getCommandTags().contains(tag));
        for (int i = 1; i < list.size(); i++) list.get(i).discard(); // дубликаты после перезапуска
        return list.isEmpty() ? null : list.get(0);
    }

    private static void removeTracked(MinecraftServer server, UUID owner) {
        UUID petId = PETS.remove(owner);
        if (petId == null) return;
        for (ServerWorld w : server.getWorlds()) {
            Entity e = w.getEntity(petId);
            if (e != null) e.discard();
        }
    }

    private static boolean sameKind(MobEntity pet, CosmeticDef def) {
        return Registries.ENTITY_TYPE.getId(pet.getType()).toString().equals(def.entity) && pet.isBaby() == def.baby;
    }

    private static void pet(MinecraftServer server, ServerPlayerEntity p, PlayerProfile prof) {
        CosmeticDef def = equipped(prof, "pet");
        ServerWorld w = p.getServerWorld();
        MobEntity pet = findPet(w, p);

        if (def == null) {
            if (pet != null) pet.discard();
            removeTracked(server, p.getUuid());
            return;
        }
        if (pet != null && !sameKind(pet, def)) { pet.discard(); pet = null; }
        if (pet == null) {
            removeTracked(server, p.getUuid());   // питомец мог остаться в другом измерении
            pet = spawn(w, p, def);
            if (pet == null) return;
        }

        double d2 = pet.squaredDistanceTo(p);
        if (d2 > 24 * 24) {
            pet.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw(), 0);
            pet.getNavigation().stop();
        } else if (d2 > 9) {
            pet.getNavigation().startMovingTo(p, 1.25);
        }
        if (pet instanceof ChickenEntity c) c.eggLayTime = 6000;                 // не несёт яйца
        if (def.baby && pet instanceof PassiveEntity pe) pe.setBreedingAge(-24000); // не вырастает
    }

    private static MobEntity spawn(ServerWorld w, ServerPlayerEntity p, CosmeticDef def) {
        Identifier id = Identifier.tryParse(def.entity);
        if (id == null || !Registries.ENTITY_TYPE.containsId(id)) return null;
        EntityType<?> type = Registries.ENTITY_TYPE.get(id);
        Entity e = type.create(w);
        if (!(e instanceof MobEntity mob)) return null;

        mob.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw(), 0);
        mob.setInvulnerable(true);   // бессмертна (кроме /kill и бездны — тогда появится новая)
        mob.setPersistent();
        mob.setSilent(true);
        mob.setCustomName(Text.literal("§e" + def.pet_name.replace("{player}", p.getName().getString())));
        mob.setCustomNameVisible(true);
        mob.addCommandTag(PET_TAG);
        mob.addCommandTag(ownerTag(p.getUuid()));
        if (def.baby && mob instanceof PassiveEntity pe) pe.setBaby(true);
        w.spawnEntity(mob);
        PETS.put(p.getUuid(), mob.getUuid());
        return mob;
    }
}
