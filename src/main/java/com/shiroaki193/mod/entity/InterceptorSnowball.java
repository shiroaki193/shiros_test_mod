package com.shiroaki193.mod.entity;

import com.shiroaki193.mod.Config;
import com.shiroaki193.mod.ballistics.Ballistics;
import com.shiroaki193.mod.registry.ModEntities;
import com.shiroaki193.mod.registry.ModParticles;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Fast, straight-flying snowball fired by a snow golem's close-in defence: downs creeper shells and hurts hostile mobs. */
public class InterceptorSnowball extends ThrowableItemProjectile {
    /** Shell and bullet count as touching when their paths pass this close. */
    public static final double HIT_RADIUS = 1.1;
    private static final int MAX_LIFETIME = 40;
    private static final double SEARCH_RADIUS = 12.0;
    /** Vanilla throwable inertia, applied to the velocity before each move. */
    private static final double DRAG = 0.99;
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
     * Both objects move several blocks per tick, so a bounding-box test would let them pass through
     * each other. Instead compare paths: this bullet's move for the coming tick against the shell's
     * path from last tick through the next. Which of the two ticks first in a server tick varies
     * (usually the older shell), and comparing "positions now" was then a tick apart: a shell
     * falling 2+ blocks/tick was never hit even when the bullet flew straight through its path.
     */
    private boolean tryIntercept() {
        double[] from = {this.getX(), this.getY(), this.getZ()};
        Vec3 move = this.getDeltaMovement().scale(DRAG);
        double[] to = {from[0] + move.x, from[1] + move.y, from[2] + move.z};
        for (CreeperShell shell : this.level().getEntitiesOfClass(CreeperShell.class,
                this.getBoundingBox().inflate(SEARCH_RADIUS), Entity::isAlive)) {
            double[] prev = {shell.xo, shell.yo, shell.zo};
            double[] now = {shell.getX(), shell.getY(), shell.getZ()};
            Ballistics.State next = shell.ballisticState().step();
            double[] ahead = {next.x(), next.y(), next.z()};
            double miss = Math.min(Ballistics.segmentDistance(from, to, prev, now),
                    Ballistics.segmentDistance(from, to, now, ahead));
            if (miss <= HIT_RADIUS) {
                shell.intercept();
                this.discard();
                return true;
            }
        }
        return false;
    }

    /** Hostile mobs only: villagers, golems and players are passed through. Shells are handled by tryIntercept(). */
    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity instanceof Enemy && super.canHitEntity(entity);
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        if (this.level() instanceof ServerLevel level) {
            Entity target = hitResult.getEntity();
            // A 10 shots/s weapon: without this, the target's post-hit invulnerability (10 ticks)
            // would swallow four of every five hits.
            target.invulnerableTime = 0;
            // Vanilla knockback on every hit would shove the mob out of range within seconds.
            Vec3 motion = target.getDeltaMovement();
            float damage = Config.CIWS_ANTI_MOB_DAMAGE.get().floatValue();
            target.hurtServer(level, this.damageSources().thrown(this, this.getOwner()), damage);
            target.setDeltaMovement(motion);
        }
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
                this.level().addParticle(ParticleTypes.ITEM_SNOWBALL, true, true, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
            }
        } else {
            super.handleEntityEvent(id);
        }
    }
}
