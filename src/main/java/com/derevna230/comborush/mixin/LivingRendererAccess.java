package com.derevna230.comborush.mixin;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Подменить модель уже созданного рендера (боец вместо обычной модели игрока) */
@Mixin(LivingEntityRenderer.class)
public interface LivingRendererAccess {
    @Mutable
    @Accessor("model")
    void setComborushModel(EntityModel<?> model);
}
