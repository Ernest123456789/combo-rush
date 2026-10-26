package com.derevna230.comborush.species;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.booster.BoosterApplier;
import com.derevna230.comborush.booster.EffectDef;
import com.derevna230.comborush.data.PlayerProfile;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class SpeciesApplier {
    /** Вешает эффекты выбранного существа и снимает у остальных */
    public static void sync(ServerPlayerEntity p) {
        String current = PlayerProfile.of(p).species();
        for (SpeciesDef def : SpeciesRegistry.all()) {
            boolean active = def.id.equals(current);
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
                        "species/" + def.id.replace(':', '/') + "/" + i);
                    if (active) inst.overwritePersistentModifier(
                        new EntityAttributeModifier(modId, e.value, BoosterApplier.op(e.operation)));
                    else inst.removeModifier(modId);
                } else if (e.type.equals("status_effect") && active) {
                    Registries.STATUS_EFFECT.getEntry(id).ifPresent(eff ->
                        p.addStatusEffect(new StatusEffectInstance(eff, 400, e.amplifier, true, false, false)));
                }
            }
        }
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }
}
