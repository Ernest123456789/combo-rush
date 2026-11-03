package com.derevna230.comborush.score;

import com.derevna230.comborush.car.CarEntity;
import com.derevna230.comborush.skate.SkateboardEntity;
import com.derevna230.comborush.universe.NightCity;
import com.derevna230.comborush.universe.Universes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

public class Conditions {
    public static boolean met(String c, ServerPlayerEntity p) {
        if (c == null || c.isEmpty()) return true;
        ServerWorld w = p.getServerWorld();
        return switch (c) {
            case "night"       -> w.isNight();
            case "day"         -> w.isDay();
            case "low_health"  -> p.getHealth() <= p.getMaxHealth() * 0.3f;
            case "full_health" -> p.getHealth() >= p.getMaxHealth();
            case "overworld"   -> w.getRegistryKey() == World.OVERWORLD;
            case "nether"      -> w.getRegistryKey() == World.NETHER;
            case "end"         -> w.getRegistryKey() == World.END;
            case "underground" -> p.getY() < 50 && !w.isSkyVisible(p.getBlockPos());
            case "high"        -> p.getY() > 100;
            case "sneaking"    -> p.isSneaking();
            case "sprinting"   -> p.isSprinting();
            case "raining"     -> w.isRaining();
            case "hungry"      -> p.getHungerManager().getFoodLevel() <= 6;
            case "no_armor"    -> p.getArmor() == 0;
            case "dark"        -> w.getLightLevel(p.getBlockPos()) <= 7;
            case "night_city"  -> w.getRegistryKey().equals(Universes.NIGHT_CITY);
            // за чертой города: холмы, леса, трассы
            case "city_wild"   -> w.getRegistryKey().equals(Universes.NIGHT_CITY)
                && (p.getBlockX() < NightCity.CITY_MIN || p.getBlockX() > NightCity.CITY_MAX
                 || p.getBlockZ() < NightCity.CITY_MIN || p.getBlockZ() > NightCity.CITY_MAX);
            // внутри городских кварталов
            case "city_urban"  -> w.getRegistryKey().equals(Universes.NIGHT_CITY)
                && p.getBlockX() >= NightCity.CITY_MIN && p.getBlockX() <= NightCity.CITY_MAX
                && p.getBlockZ() >= NightCity.CITY_MIN && p.getBlockZ() <= NightCity.CITY_MAX;
            // за рулём машины или на скейте
            case "driving"     -> p.getVehicle() instanceof CarEntity || p.getVehicle() instanceof SkateboardEntity;
            default            -> true;
        };
    }
}
