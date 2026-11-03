package com.derevna230.comborush.car;

import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Машины: масштаб модели (в блоках Minecraft ≈ 5,5 блока в длину), ходовые данные, число мест и их положение.
 * Места заданы в системе сущности: x > 0 — слева по ходу, z > 0 — вперёд; первое место — водительское.
 */
public final class CarDefs {
    private CarDefs() {}

    /**
     * @param scale       масштаб модели
     * @param topSpeed    максимальная скорость без нитро, блоков за тик (0,7 ≈ 14 блоков/с)
     * @param accel       разгон, блоков/тик² у нулевой скорости
     * @param wheelBase   расстояние между осями в блоках (определяет радиус поворота)
     * @param wheelCirc   длина окружности колеса в блоках (для анимации вращения)
     * @param width       ширина и длина хитбокса (в Minecraft он квадратный)
     * @param height      высота хитбокса
     * @param seats       места пассажиров, первое — водитель
     */
    public record Def(String id, float scale, double topSpeed, double accel, double wheelBase, double wheelCirc,
                      float width, float height, Vec3d[] seats) {
        public int seatCount() { return seats.length; }
    }

    public static final Map<String, Def> ALL = new LinkedHashMap<>();

    static {
        // американский масл-кар: руль слева, мощный разгон
        put(new Def("mustang", 0.90f, 0.70, 0.050, 2.6, 2.55, 2.4f, 1.6f, new Vec3d[]{
            new Vec3d(0.44, 0.16, -0.30), new Vec3d(-0.44, 0.16, -0.30)}));
        // гоночная версия: одно ковшеобразное кресло, быстрее и резвее
        put(new Def("mustang_modified", 0.90f, 0.88, 0.062, 2.6, 2.55, 2.4f, 1.6f, new Vec3d[]{
            new Vec3d(0.44, 0.16, -0.30)}));
        // японский хэтчбек: руль справа, диван сзади; легче в управлении, но ниже по скорости
        put(new Def("trueno", 0.80f, 0.76, 0.046, 3.15, 3.31, 2.6f, 1.9f, new Vec3d[]{
            new Vec3d(-0.40, 0.30, -0.12), new Vec3d(0.40, 0.30, -0.12),
            new Vec3d(-0.36, 0.30, -1.36), new Vec3d(0.36, 0.30, -1.36)}));
    }

    private static void put(Def d) { ALL.put(d.id(), d); }

    public static Def get(String id) { return ALL.get(id); }

    /** Определение по типу сущности (id вида combo-rush:car_<машина>) */
    public static Def ofType(EntityType<?> type) {
        String path = Registries.ENTITY_TYPE.getId(type).getPath();
        Def d = ALL.get(path.startsWith("car_") ? path.substring(4) : path);
        return d != null ? d : ALL.values().iterator().next();
    }
}
