package com.shiroaki193.mod.entity;

import com.shiroaki193.mod.Config;
import com.shiroaki193.mod.registry.ModEntities;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/**
 * Replaces a share of naturally spawning creepers ({@link Config#MORTAR_NATURAL_SHARE}, 40% by
 * default) with mortar creepers at the same spot. Only plain creepers from natural spawning are
 * touched: spawners, eggs, commands and structures keep what they spawn.
 */
public final class NaturalMortarSpawns {
    private NaturalMortarSpawns() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(NaturalMortarSpawns::onFinalizeSpawn);
    }

    private static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (event.getEntity().getType() != EntityType.CREEPER || event.getSpawnType() != EntitySpawnReason.NATURAL
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (level.getRandom().nextDouble() >= Config.MORTAR_NATURAL_SHARE.get()) {
            return;
        }
        MortarCreeper mortar = ModEntities.MORTAR_CREEPER.get().create(level, EntitySpawnReason.NATURAL);
        if (mortar == null) {
            return;
        }
        event.setSpawnCancelled(true);
        mortar.snapTo(event.getX(), event.getY(), event.getZ(), event.getEntity().getYRot(), 0.0F);
        // Fires FinalizeSpawnEvent for the mortar too (other mods see it); this handler ignores non-creepers.
        EventHooks.finalizeMobSpawn(mortar, level, event.getDifficulty(), EntitySpawnReason.NATURAL, null);
        mortar.markNaturalSpawn();
        level.addFreshEntityWithPassengers(mortar);
    }
}
