package com.shiroaki193.mod.entity;

import com.shiroaki193.mod.Config;
import com.shiroaki193.mod.ballistics.Ballistics;
import com.shiroaki193.mod.registry.ModEntities;

import com.shiroaki193.mod.registry.ModParticles;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The "firework + elytra creeper" mortar round. Flies the {@link Ballistics} arc and explodes on impact. */
public class CreeperShell extends ThrowableItemProjectile {
    private static final int TRAIL_PARTICLES_PER_TICK = 8;
    private static final double RENDER_DISTANCE = 256.0;

    /** Game time of this shell's last move, see {@link #hasMovedThisTick()}. */
    private long lastMoveTime = Long.MIN_VALUE;
    private boolean detonated;
    private boolean intercepted;
    private Vec3 detonationPos = Vec3.ZERO;

    public CreeperShell(EntityType<? extends CreeperShell> type, Level level) {
        super(type, level);
    }

    public CreeperShell(Level level, LivingEntity owner) {
        super(ModEntities.CREEPER_SHELL.get(), owner, level, Items.CREEPER_HEAD.getDefaultInstance());
    }

    public CreeperShell(Level level, double x, double y, double z) {
        super(ModEntities.CREEPER_SHELL.get(), x, y, z, level, Items.CREEPER_HEAD.getDefaultInstance());
    }

    @Override
    protected Item getDefaultItem() {
        return Items.CREEPER_HEAD;
    }

    @Override
    protected double getDefaultGravity() {
        return Ballistics.GRAVITY;
    }

    /**
     * Entities tick in spawn order, so a shell (spawned after the golems) usually has not moved yet
     * when a golem aims at it in the same game tick. Aiming must then account for that extra move,
     * otherwise every shot arrives one tick late, which misses fast-falling shells by 2+ blocks.
     */
    public boolean hasMovedThisTick() {
        return this.lastMoveTime == this.level().getGameTime();
    }

    /** Snapshot for prediction; matches what the next {@link #tick()} will do. */
    public Ballistics.State ballisticState() {
        Vec3 v = this.getDeltaMovement();
        return new Ballistics.State(this.getX(), this.getY(), this.getZ(), v.x, v.y, v.z);
    }

    /** Vanilla stops rendering small projectiles at 128 blocks; shells are watched from much farther. */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double max = RENDER_DISTANCE * getViewScale();
        return distance < max * max;
    }

    @Override
    public void tick() {
        super.tick();
        this.lastMoveTime = this.level().getGameTime();
        if (this.level().isClientSide() && this.isAlive()) {
            spawnTrail();
        }
    }

    private void spawnTrail() {
        // Fill the gap between last and current position so the trail reads as one continuous streak.
        for (int i = 0; i < TRAIL_PARTICLES_PER_TICK; i++) {
            double f = i / (double) TRAIL_PARTICLES_PER_TICK;
            this.level().addParticle(ModParticles.SHELL_TRAIL.get(),
                    this.xo + (this.getX() - this.xo) * f,
                    this.yo + (this.getY() - this.yo) * f,
                    this.zo + (this.getZ() - this.zo) * f,
                    0, 0, 0);
        }
        if (this.random.nextInt(3) == 0) {
            this.level().addParticle(ParticleTypes.FIREWORK, true, true, this.getX(), this.getY(), this.getZ(),
                    this.random.nextGaussian() * 0.02, -0.05, this.random.nextGaussian() * 0.02);
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        // Salvos must not detonate on each other or on the creeper that launched them.
        return !(entity instanceof CreeperShell) && !(entity instanceof Creeper) && super.canHitEntity(entity);
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (this.level() instanceof ServerLevel serverLevel && !this.isRemoved()) {
            this.detonated = true;
            this.detonationPos = hitResult.getLocation();
            float power = Config.SHELL_EXPLOSION_POWER.get().floatValue();
            if (power > 0) {
                serverLevel.explode(this, this.getX(), this.getY(), this.getZ(), power, Level.ExplosionInteraction.MOB);
            }
            this.discard();
        }
    }

    /** Shot down mid-air: an air burst with no damage to blocks or mobs. */
    public void intercept() {
        if (!(this.level() instanceof ServerLevel serverLevel) || this.isRemoved()) {
            return;
        }
        this.intercepted = true;
        this.detonationPos = this.position();
        serverLevel.sendParticles(ParticleTypes.FIREWORK, true, true, this.getX(), this.getY(), this.getZ(), 40, 0.2, 0.2, 0.2, 0.25);
        serverLevel.sendParticles(ParticleTypes.CLOUD, true, true, this.getX(), this.getY(), this.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
        serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.HOSTILE, 3.0F, 0.9F + this.random.nextFloat() * 0.2F);
        this.discard();
    }

    public boolean isDetonated() {
        return this.detonated;
    }

    public boolean isIntercepted() {
        return this.intercepted;
    }

    /** Where the shell exploded or was shot down; {@link Vec3#ZERO} while still flying. */
    public Vec3 getDetonationPos() {
        return this.detonationPos;
    }
}
