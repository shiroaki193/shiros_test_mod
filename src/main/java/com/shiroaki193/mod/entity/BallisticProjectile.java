package com.shiroaki193.mod.entity;

import com.shiroaki193.mod.ballistics.Ballistics;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A projectile flying the {@link Ballistics} arc that snow golems can shoot down: creeper shells
 * and the bomblets a shell breaks into. Holds what fire control needs to lead it.
 */
public abstract class BallisticProjectile extends ThrowableItemProjectile {
    private static final double RENDER_DISTANCE = 256.0;

    /** Game time of the last move, see {@link #hasMovedThisTick()}. */
    private long lastMoveTime = Long.MIN_VALUE;
    private boolean intercepted;

    protected BallisticProjectile(EntityType<? extends BallisticProjectile> type, Level level) {
        super(type, level);
    }

    protected BallisticProjectile(EntityType<? extends BallisticProjectile> type, LivingEntity owner, Level level, ItemStack item) {
        super(type, owner, level, item);
    }

    protected BallisticProjectile(EntityType<? extends BallisticProjectile> type, double x, double y, double z, Level level, ItemStack item) {
        super(type, x, y, z, level, item);
    }

    @Override
    protected double getDefaultGravity() {
        return Ballistics.GRAVITY;
    }

    @Override
    public void tick() {
        super.tick();
        this.lastMoveTime = this.level().getGameTime();
    }

    /**
     * Entities tick in spawn order, so a projectile spawned after the golems usually has not moved
     * yet when a golem aims at it in the same game tick. Aiming must then account for that extra
     * move, otherwise every shot arrives one tick late, which misses fast-falling shells by 2+ blocks.
     */
    public boolean hasMovedThisTick() {
        return this.lastMoveTime == this.level().getGameTime();
    }

    /** Snapshot for prediction; matches what the next {@link #tick()} will do. */
    public Ballistics.State ballisticState() {
        var v = this.getDeltaMovement();
        return new Ballistics.State(this.getX(), this.getY(), this.getZ(), v.x, v.y, v.z);
    }

    /** Vanilla stops rendering small projectiles at 128 blocks; these are watched from much farther. */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double max = RENDER_DISTANCE * getViewScale();
        return distance < max * max;
    }

    /** Shot down by an interceptor snowball. */
    public abstract void intercept();

    protected void markIntercepted() {
        this.intercepted = true;
    }

    public boolean isIntercepted() {
        return this.intercepted;
    }
}
