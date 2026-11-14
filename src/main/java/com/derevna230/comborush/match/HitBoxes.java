package com.derevna230.comborush.match;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Попадания по частям тела. У людей и ботов вместо одной коробки — набор коробок, повёрнутых по телу:
 * голова, грудь, живот, ноги, руки (по бокам и вытянутые вперёд со стволом). У каждой части — свой множитель урона.
 * Остальные существа — по их обычной коробке (голова — верхние 20 %).
 */
public final class HitBoxes {
    private HitBoxes() {}

    public enum Part {
        HEAD("голова", 3.5f), CHEST("грудь", 1.0f), STOMACH("живот", 1.25f), ARM("рука", 0.9f), LEG("нога", 0.75f);
        public final String title; public final float mult;
        Part(String title, float mult) { this.title = title; this.mult = mult; }
    }

    public record Hit(LivingEntity entity, Part part, Vec3d pos, double dist) {}

    // локальные коробки: x — вбок, y — вверх (для роста 1,8), z — вперёд
    private record B(Part part, double x0, double x1, double y0, double y1, double z0, double z1) {}
    private static final B[] BODY = {
        new B(Part.HEAD, -0.27, 0.27, 1.40, 1.88, -0.27, 0.27),
        new B(Part.CHEST, -0.29, 0.29, 1.05, 1.42, -0.18, 0.18),
        new B(Part.STOMACH, -0.29, 0.29, 0.72, 1.05, -0.17, 0.17),
        new B(Part.LEG, -0.29, 0.29, 0.0, 0.72, -0.17, 0.17),
        new B(Part.ARM, 0.27, 0.53, 0.66, 1.42, -0.18, 0.18),
        new B(Part.ARM, -0.53, -0.27, 0.66, 1.42, -0.18, 0.18),
    };
    /** руки, вытянутые вперёд (ствол в руках) */
    private static final B[] AIM_ARMS = {
        new B(Part.ARM, -0.42, 0.42, 1.06, 1.40, 0.16, 0.66),
    };

    private static boolean humanoid(Entity e) { return e instanceof PlayerEntity || e instanceof BotEntity; }

    /** Луч from→to (to — уже обрезан по блокам): ближайшее попадание по существам из фильтра */
    public static Hit trace(World w, Entity shooter, Vec3d from, Vec3d to, Predicate<Entity> filter) {
        Box area = new Box(from, to).expand(1.2);
        Hit best = null;
        double len = from.distanceTo(to);
        for (Entity e : w.getOtherEntities(shooter, area, x -> x instanceof LivingEntity && filter.test(x))) {
            LivingEntity le = (LivingEntity) e;
            Hit h = humanoid(e) ? traceBody(le, from, to, len) : traceBox(le, from, to);
            if (h != null && (best == null || h.dist < best.dist)) best = h;
        }
        return best;
    }

    private static Hit traceBox(LivingEntity e, Vec3d from, Vec3d to) {
        Optional<Vec3d> r = e.getBoundingBox().expand(0.1).raycast(from, to);
        if (r.isEmpty()) return null;
        Vec3d p = r.get();
        Part part = p.y >= e.getY() + e.getHeight() * 0.8 ? Part.HEAD : p.y >= e.getY() + e.getHeight() * 0.4 ? Part.CHEST : Part.LEG;
        return new Hit(e, part, p, from.distanceTo(p));
    }

    private static Hit traceBody(LivingEntity e, Vec3d from, Vec3d to, double len) {
        double yaw = Math.toRadians(e.bodyYaw);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);          // вперёд
        double rx = -fz, rz = fx;                                // вбок
        double k = e.getHeight() / 1.8;
        Vec3d o = e.getPos();
        // луч в локальных координатах тела
        double ax = (from.x - o.x) * rx + (from.z - o.z) * rz, ay = from.y - o.y, az = (from.x - o.x) * fx + (from.z - o.z) * fz;
        double bx = (to.x - o.x) * rx + (to.z - o.z) * rz, by = to.y - o.y, bz = (to.x - o.x) * fx + (to.z - o.z) * fz;
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double bestT = 2; Part bestP = null;
        boolean armed = !e.getMainHandStack().isEmpty();
        for (int pass = 0; pass < 2; pass++) {
            B[] set = pass == 0 ? BODY : AIM_ARMS;
            if (pass == 1 && !armed) break;
            for (B b : set) {
                double t = slab(ax, ay, az, dx, dy, dz, b.x0, b.x1, b.y0 * k, b.y1 * k, b.z0, b.z1);
                if (t >= 0 && t < bestT) { bestT = t; bestP = b.part; }
            }
        }
        if (bestP == null) return null;
        Vec3d p = from.add(to.subtract(from).multiply(bestT));
        return new Hit(e, bestP, p, bestT * len);
    }

    /** Параметр t∈[0,1] входа отрезка в коробку или −1 */
    private static double slab(double ox, double oy, double oz, double dx, double dy, double dz,
                               double x0, double x1, double y0, double y1, double z0, double z1) {
        double tmin = 0, tmax = 1;
        double[] o = {ox, oy, oz}, d = {dx, dy, dz}, lo = {x0, y0, z0}, hi = {x1, y1, z1};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-9) { if (o[i] < lo[i] || o[i] > hi[i]) return -1; continue; }
            double t1 = (lo[i] - o[i]) / d[i], t2 = (hi[i] - o[i]) / d[i];
            if (t1 > t2) { double t = t1; t1 = t2; t2 = t; }
            tmin = Math.max(tmin, t1); tmax = Math.min(tmax, t2);
            if (tmin > tmax) return -1;
        }
        return tmin;
    }
}
