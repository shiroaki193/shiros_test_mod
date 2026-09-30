package com.shiroaki193.mod;

import com.shiroaki193.mod.client.AimPreview;
import com.shiroaki193.mod.client.GlowTrailParticle;
import com.shiroaki193.mod.registry.ModEntities;
import com.shiroaki193.mod.registry.ModParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = MobArmsRace.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = MobArmsRace.MODID, value = Dist.CLIENT)
public class MobArmsRaceClient {
    public MobArmsRaceClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.MORTAR_CREEPER.get(), CreeperRenderer::new);
        // The shell renders as a flying creeper head; full-bright so it stays visible at night.
        event.registerEntityRenderer(ModEntities.CREEPER_SHELL.get(), ctx -> new ThrownItemRenderer<>(ctx, 1.5F, true));
        event.registerEntityRenderer(ModEntities.CREEPER_BOMBLET.get(), ctx -> new ThrownItemRenderer<>(ctx, 0.6F, true));
        event.registerEntityRenderer(ModEntities.INTERCEPTOR_SNOWBALL.get(), ctx -> new ThrownItemRenderer<>(ctx, 0.6F, true));
    }

    @SubscribeEvent
    static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SHELL_TRAIL.get(), GlowTrailParticle.ShellTrailProvider::new);
        event.registerSpriteSet(ModParticles.TRACER.get(), GlowTrailParticle.TracerProvider::new);
        event.registerSpriteSet(ModParticles.AIM_MARKER.get(), GlowTrailParticle.AimMarkerProvider::new);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        AimPreview.tick(Minecraft.getInstance());
    }
}
