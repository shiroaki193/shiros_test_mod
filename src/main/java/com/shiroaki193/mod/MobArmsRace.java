package com.shiroaki193.mod;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.shiroaki193.mod.carry.CarryEvents;
import com.shiroaki193.mod.entity.MortarCreeper;
import com.shiroaki193.mod.entity.NaturalMortarSpawns;
import com.shiroaki193.mod.entity.ai.InterceptShellGoal;
import com.shiroaki193.mod.entity.ai.ThrowCatGoal;
import com.shiroaki193.mod.gametest.ModGameTests;
import com.shiroaki193.mod.registry.ModEntities;
import com.shiroaki193.mod.registry.ModItems;
import com.shiroaki193.mod.registry.ModParticles;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

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
        NaturalMortarSpawns.register();

        modEventBus.addListener(this::registerAttributes);
        modEventBus.addListener(this::addCreative);
        NeoForge.EVENT_BUS.addListener(this::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(this::onIncomingDamage);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modEventBus.addListener((ModConfigEvent.Loading event) -> {
            if (event.getConfig().getSpec() == Config.SPEC) {
                Config.migrate();
            }
        });
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
            applyGolemHealth(golem);
            // The first place a golem shows up becomes its post (saved with the golem, so it sticks).
            int post = Config.CIWS_POST_RADIUS.get();
            if (post > 0 && !golem.hasHome()) {
                golem.setHomeTo(golem.blockPosition(), post);
            }
        } else if (event.getEntity() instanceof IronGolem golem) {
            golem.goalSelector.addGoal(1, new ThrowCatGoal(golem));
        } else if (event.getEntity() instanceof Cat cat && cat.entityTags().contains(ThrowCatGoal.TAG_AIRBORNE)) {
            ThrowCatGoal.release(cat);
        }
    }

    /** Weatherproof snow golems: no damage from rain, water or melting (see {@link #isWeatherDamage}). */
    private void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof SnowGolem golem && Config.CIWS_WEATHERPROOF.get() && isWeatherDamage(golem, event.getSource())) {
            event.setCanceled(true);
        }
    }

    /**
     * Vanilla hurts snow golems with drown damage in water or rain, and with on-fire damage while
     * standing in a biome where they melt. On-fire damage also comes from really burning, so it is
     * only treated as melting while the golem is not on fire.
     */
    public static boolean isWeatherDamage(SnowGolem golem, DamageSource source) {
        if (source.is(DamageTypes.DROWN)) {
            return true;
        }
        return source.is(DamageTypes.ON_FIRE) && golem.getRemainingFireTicks() <= 0
                && golem.level() instanceof ServerLevel level
                && level.environmentAttributes().getValue(EnvironmentAttributes.SNOW_GOLEM_MELTS, golem.position());
    }

    private static final Identifier GOLEM_HEALTH = Identifier.fromNamespaceAndPath(MODID, "ciws_health");

    /**
     * Raises the golem's max health to the configured value. The modifier is permanent (saved with
     * the golem) so a reloaded golem keeps its health; it is re-applied on every join to follow
     * config changes. A golem getting it for the first time starts at full health.
     */
    private static void applyGolemHealth(SnowGolem golem) {
        AttributeInstance maxHealth = golem.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        boolean first = !maxHealth.hasModifier(GOLEM_HEALTH);
        double bonus = Config.CIWS_GOLEM_HEALTH.get() - maxHealth.getBaseValue();
        maxHealth.addOrReplacePermanentModifier(new AttributeModifier(GOLEM_HEALTH, bonus, AttributeModifier.Operation.ADD_VALUE));
        if (first) {
            golem.setHealth(golem.getMaxHealth());
        } else if (golem.getHealth() > golem.getMaxHealth()) {
            golem.setHealth(golem.getMaxHealth());
        }
    }
}
