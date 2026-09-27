package com.shiroaki193.mod.entity;

import com.shiroaki193.mod.registry.ModEntities;
import com.shiroaki193.mod.registry.ModParticles;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Fast, straight-flying snowball fired by a snow golem's close-in defence. Only reacts to creeper shells. */
public class InterceptorSnowball extends ThrowableItemProjectile {
    /** Shell and bullet count as touching when their centres pass this close within one tick. */
    public static final double HIT_RADIUS = 1.1;
    private static final int MAX_LIFETIME = 40;
    private static final double SEARCH_RADIUS = 12.0;
    private static final int TRACER_PARTICLES_PER_TICK = 8;

    public InterceptorSnowball(EntityType<? extends InterceptorSnowball> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public InterceptorSnowball(Level level, LivingEntity owner) {
        super(ModEntities.INTERCEPTOR_SNOWBALL.get(), owner, level, Items.SNOWBALL.getDefaultInstance());
        this.setNoGravity(true);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.SNOWBALL;
    }

    @Override
    public void tick() {
        if (this.level() instanceof ServerLevel && this.tryIntercept()) {
            return;
        }
        super.tick();
        if (this.level().isClientSide()) {
            for (int i = 0; i < TRACER_PARTICLES_PER_TICK; i++) {
                double f = i / (double) TRACER_PARTICLES_PER_TICK;
                this.level().addParticle(ModParticles.TRACER.get(),
                        this.xo + (this.getX() - this.xo) * f,
                        this.yo + (this.getY() - this.yo) * f,
                        this.zo + (this.getZ() - this.zo) * f,
                        0, 0, 0);
            }
        } else if (this.tickCount > MAX_LIFETIME) {
            this.discard();
        }
    }

    /**
     * Both objects move several blocks per tick, so a plain bounding-box test would let them pass
     * through each other. Instead check the closest approach of their relative motion across the
     * previous and the coming tick (entity tick order within a server tick is not fixed).
     */
    private boolean tryIntercept() {
        Vec3 myVel = this.getDeltaMovement();
        for (CreeperShell shell : this.level().getEntitiesOfClass(CreeperShell.class,
                this.getBoundingBox().inflate(SEARCH_RADIUS), Entity::isAlive)) {
            Vec3 offset = shell.position().subtract(this.position());
            Vec3 relVel = shell.getDeltaMovement().subtract(myVel);
            double speedSq = relVel.lengthSqr();
            double t = speedSq < 1.0e-9 ? 0 : Math.clamp(-offset.dot(relVel) / speedSq, -1.0, 1.0);
            if (offset.add(relVel.scale(t)).lengthSqr() <= HIT_RADIUS * HIT_RADIUS) {
                shell.intercept();
                this.discard();
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        // Pass through villagers, golems and players; shells are handled by tryIntercept().
        return false;
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!this.level().isClientSide()) {
            this.level().broadcastEntityEvent(this, (byte) 3);
            this.discard();
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 3) {
            for (int i = 0; i < 6; i++) {
                this.level().addParticle(ParticleTypes.ITEM_SNOWBALL, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
            }
        } else {
            super.handleEntityEvent(id);
        }
    }
}
