package com.shiroaki193.mod.carry;

import com.shiroaki193.mod.item.HeldCreeperItem;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.monster.Creeper;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Empty-hand right-click on a creeper picks it up; throwing is {@link HeldCreeperItem#use}. */
public final class CarryEvents {
    private CarryEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(CarryEvents::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(CarryEvents::onEntityTick);
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getTarget() instanceof Creeper creeper)
                || !event.getEntity().getMainHandItem().isEmpty()) {
            return;
        }
        if (!event.getLevel().isClientSide()) {
            HeldCreeperItem.pickUp(event.getEntity(), creeper);
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    private static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Creeper creeper && !creeper.level().isClientSide()) {
            CreeperCarry.tick(creeper);
        }
    }
}
