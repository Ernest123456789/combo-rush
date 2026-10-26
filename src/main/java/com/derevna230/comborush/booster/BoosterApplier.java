package com.derevna230.comborush.booster;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.data.OwnedBooster;
import com.derevna230.comborush.data.PlayerProfile;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class BoosterApplier {
    /** Вызывается после покупки/продажи, при входе и раз в 5 секунд */
    public static void sync(ServerPlayerEntity p) {
        PlayerProfile prof = PlayerProfile.of(p);
        for (BoosterDef def : BoosterRegistry.all()) {
            OwnedBooster owned = prof.find(def.id);
            boolean active = owned != null && !owned.dropped().equals("game");

            for (int i = 0; i < def.effects.size(); i++) {
                EffectDef e = def.effects.get(i);
                Identifier id = Identifier.tryParse(e.id);
                if (!e.isGame() || id == null) continue;

                if (e.type.equals("attribute")) {
                    var attr = Registries.ATTRIBUTE.getEntry(id);
                    if (attr.isEmpty()) continue;
                    EntityAttributeInstance inst = p.getAttributeInstance(attr.get());
                    if (inst == null) continue;
                    Identifier modId = Identifier.of(ComboRush.MOD_ID,
                        "booster/" + def.id.replace(':', '/') + "/" + i);
                    if (active) inst.overwritePersistentModifier(
                        new EntityAttributeModifier(modId, e.value, op(e.operation)));
                    else inst.removeModifier(modId);
                } else if (e.type.equals("status_effect") && active) {
                    Registries.STATUS_EFFECT.getEntry(id).ifPresent(eff ->
                        p.addStatusEffect(new StatusEffectInstance(eff, 400, e.amplifier, true, false, false)));
                }
            }
        }
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    public static EntityAttributeModifier.Operation op(String s) {
        return switch (s) {
            case "add_multiplied_base"  -> EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case "add_multiplied_total" -> EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default -> EntityAttributeModifier.Operation.ADD_VALUE;
        };
    }
}
