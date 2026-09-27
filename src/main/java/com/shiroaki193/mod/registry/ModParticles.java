package com.shiroaki193.mod.registry;

import com.shiroaki193.mod.MobArmsRace;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, MobArmsRace.MODID);

    /** Glowing red streak left by a creeper shell. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SHELL_TRAIL =
            PARTICLE_TYPES.register("shell_trail", () -> new SimpleParticleType(false));

    /** Short-lived white tracer left by an interceptor snowball. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> TRACER =
            PARTICLE_TYPES.register("tracer", () -> new SimpleParticleType(false));

    /** Landing-preview dots drawn while a creeper shell is held. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> AIM_MARKER =
            PARTICLE_TYPES.register("aim_marker", () -> new SimpleParticleType(true));

    private ModParticles() {
    }
}
