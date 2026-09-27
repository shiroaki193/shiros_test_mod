package com.shiroaki193.mod.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;

/** A motionless, self-lit dot that fades out; chained per tick it draws a glowing streak. */
public class GlowTrailParticle extends SingleQuadParticle {
    private final float startAlpha;

    protected GlowTrailParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites,
                                float r, float g, float b, int lifetime, float size) {
        super(level, x, y, z, sprites.first());
        this.hasPhysics = false;
        this.gravity = 0;
        this.friction = 1.0F;
        this.setColor(r, g, b);
        this.setLifetime(lifetime);
        this.quadSize = size;
        this.startAlpha = 0.95F;
        this.setAlpha(this.startAlpha);
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    public int getLightCoords(float partialTick) {
        return LightCoordsUtil.FULL_BRIGHT;
    }

    @Override
    public void tick() {
        super.tick();
        this.setAlpha(this.startAlpha * (1.0F - (float) this.age / this.lifetime));
    }

    public static class ShellTrailProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public ShellTrailProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z,
                                       double xa, double ya, double za, RandomSource random) {
            return new GlowTrailParticle(level, x, y, z, this.sprites, 1.0F, 0.12F + random.nextFloat() * 0.08F, 0.08F,
                    26 + random.nextInt(8), 0.28F);
        }
    }

    public static class AimMarkerProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public AimMarkerProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z,
                                       double xa, double ya, double za, RandomSource random) {
            // Lives just past the 2-tick refresh so the preview looks steady without smearing.
            return new GlowTrailParticle(level, x, y, z, this.sprites, 1.0F, 0.75F, 0.15F, 3, 0.18F);
        }
    }

    public static class TracerProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public TracerProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z,
                                       double xa, double ya, double za, RandomSource random) {
            return new GlowTrailParticle(level, x, y, z, this.sprites, 1.0F, 1.0F, 1.0F, 5 + random.nextInt(3), 0.14F);
        }
    }
}
