package com.derevna230.comborush.item;

import com.derevna230.comborush.armor.ArmorTooltips;
import com.derevna230.comborush.cases.CaseService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/** Кейс: ПКМ — открыть (рулетка на клиенте, награду выдаёт сервер) */
public class CaseItem extends Item {
    private final String caseId;

    public CaseItem(String caseId, Settings settings) {
        super(settings);
        this.caseId = caseId;
    }

    public String caseId() { return caseId; }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity sp) CaseService.open(sp, stack, caseId);
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        ArmorTooltips.appendCase(caseId, tooltip);
    }
}
