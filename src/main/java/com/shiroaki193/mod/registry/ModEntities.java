package com.shiroaki193.mod.registry;

import com.shiroaki193.mod.MobArmsRace;
import com.shiroaki193.mod.entity.CreeperBomblet;
import com.shiroaki193.mod.entity.CreeperShell;
import com.shiroaki193.mod.entity.InterceptorSnowball;
import com.shiroaki193.mod.entity.MortarCreeper;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(MobArmsRace.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<MortarCreeper>> MORTAR_CREEPER = ENTITIES.registerEntityType(
            "mortar_creeper", MortarCreeper::new, MobCategory.MONSTER,
            b -> b.sized(0.6F, 1.7F).eyeHeight(1.445F).clientTrackingRange(8));

    // Shells fly up to 120 blocks and ~70 blocks high: track them far and sync every tick for a smooth arc.
    public static final DeferredHolder<EntityType<?>, EntityType<CreeperShell>> CREEPER_SHELL = ENTITIES.registerEntityType(
            "creeper_shell", CreeperShell::new, MobCategory.MISC,
            b -> b.noLootTable().sized(0.5F, 0.5F).clientTrackingRange(16).updateInterval(1));

    public static final DeferredHolder<EntityType<?>, EntityType<CreeperBomblet>> CREEPER_BOMBLET = ENTITIES.registerEntityType(
            "creeper_bomblet", CreeperBomblet::new, MobCategory.MISC,
            b -> b.noLootTable().sized(0.3F, 0.3F).clientTrackingRange(16).updateInterval(1));

    public static final DeferredHolder<EntityType<?>, EntityType<InterceptorSnowball>> INTERCEPTOR_SNOWBALL = ENTITIES.registerEntityType(
            "interceptor_snowball", InterceptorSnowball::new, MobCategory.MISC,
            b -> b.noLootTable().sized(0.25F, 0.25F).clientTrackingRange(8).updateInterval(1));

    private ModEntities() {
    }
}
