package com.shiroaki193.mod;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.shiroaki193.mod.carry.CarryEvents;
import com.shiroaki193.mod.entity.MortarCreeper;
import com.shiroaki193.mod.entity.ai.InterceptShellGoal;
import com.shiroaki193.mod.entity.ai.ThrowCatGoal;
import com.shiroaki193.mod.gametest.ModGameTests;
import com.shiroaki193.mod.registry.ModEntities;
import com.shiroaki193.mod.registry.ModItems;
import com.shiroaki193.mod.registry.ModParticles;

import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@Mod(MobArmsRace.MODID)
public class MobArmsRace {
    public static final String MODID = "mobarmsrace";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MobArmsRace(IEventBus modEventBus, ModContainer modContainer) {
        ModEntities.ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModParticles.PARTICLE_TYPES.register(modEventBus);
        ModGameTests.register(modEventBus);
        CarryEvents.register();

        modEventBus.addListener(this::registerAttributes);
        modEventBus.addListener(this::addCreative);
        NeoForge.EVENT_BUS.addListener(this::onEntityJoinLevel);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.MORTAR_CREEPER.get(), MortarCreeper.createAttributes().build());
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.MORTAR_CREEPER_SPAWN_EGG);
        } else if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.CREEPER_SHELL);
        }
    }

    /** Vanilla golems join the arms race: snow golems get the anti-shell defence, iron golems throw cats. */
    private void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof SnowGolem golem) {
            golem.goalSelector.addGoal(0, new InterceptShellGoal(golem));
        } else if (event.getEntity() instanceof IronGolem golem) {
            golem.goalSelector.addGoal(1, new ThrowCatGoal(golem));
        } else if (event.getEntity() instanceof Cat cat && cat.entityTags().contains(ThrowCatGoal.TAG_AIRBORNE)) {
            ThrowCatGoal.release(cat);
        }
    }
}
