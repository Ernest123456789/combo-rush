package com.derevna230.comborush.armor;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.booster.BoosterApplier;
import com.derevna230.comborush.booster.EffectDef;
import com.derevna230.comborush.score.Conditions;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;

/** Игровые эффекты комплектов (атрибуты и статус-эффекты): при смене брони и раз в 5 секунд */
public final class ArmorSetApplier {
    private ArmorSetApplier() {}

    public static void sync(ServerPlayerEntity p) {
        Map<String, Integer> worn = ArmorSets.worn(p);
        for (ArmorSetDef def : ArmorSetRegistry.all()) {
            int n = worn.getOrDefault(def.id, 0);
            apply(p, def, def.piece_effects, "piece", n);
            apply(p, def, def.set_effects, "set", ArmorSets.full(def.id, n) && n > 0 ? 1 : 0);
        }
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    private static void apply(ServerPlayerEntity p, ArmorSetDef def, List<EffectDef> effects, String kind, int mult) {
        for (int i = 0; i < effects.size(); i++) {
            EffectDef e = effects.get(i);
            if (!e.isGame()) continue;
            Identifier id = Identifier.tryParse(e.id);
            if (id == null) continue;
            boolean active = mult > 0 && Conditions.met(e.condition, p);
            if ("attribute".equals(e.type)) {
                var attr = Registries.ATTRIBUTE.getEntry(id);
                if (attr.isEmpty()) continue;
                EntityAttributeInstance inst = p.getAttributeInstance(attr.get());
                if (inst == null) continue;
                Identifier modId = Identifier.of(ComboRush.MOD_ID, "armor_set/" + def.id + "/" + kind + "/" + i);
                if (active) inst.overwritePersistentModifier(new EntityAttributeModifier(modId, e.value * mult, BoosterApplier.op(e.operation)));
                else inst.removeModifier(modId);
            } else if ("status_effect".equals(e.type) && active) {
                Registries.STATUS_EFFECT.getEntry(id).ifPresent(eff ->
                    p.addStatusEffect(new StatusEffectInstance(eff, 400, e.amplifier, true, false, true)));
            }
        }
    }
}
