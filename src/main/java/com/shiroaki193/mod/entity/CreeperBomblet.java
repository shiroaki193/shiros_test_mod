package com.shiroaki193.mod.entity;

import com.shiroaki193.mod.Config;
import com.shiroaki193.mod.registry.ModEntities;
import com.shiroaki193.mod.registry.ModParticles;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A piece of a shell that broke apart when shot down ({@link CreeperShell#intercept()}). Falls on
 * the same physics as the shell and explodes on impact with a much smaller charge. Snow golems
 * shoot at bomblets only while no whole shell is coming at them, nearest landing first; shot down,
 * a bomblet bursts in the air. It never splits again.
 */
public class CreeperBomblet extends BallisticProjectile {
    private static final int TRAIL_PARTICLES_PER_TICK = 3;

    private boolean exploded;
    private Vec3 explosionPos = Vec3.ZERO;

    public CreeperBomblet(EntityType<? extends CreeperBomblet> type, Level level) {
        super(type, level);
    }

    public CreeperBomblet(Level level, Vec3 at, Vec3 velocity) {
        super(ModEntities.CREEPER_BOMBLET.get(), at.x, at.y, at.z, level, Items.CREEPER_HEAD.getDefaultInstance());
        this.setDeltaMovement(velocity);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.CREEPER_HEAD;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide() && this.isAlive()) {
            for (int i = 0; i < TRAIL_PARTICLES_PER_TICK; i++) {
                double f = i / (double) TRAIL_PARTICLES_PER_TICK;
                this.level().addParticle(ModParticles.SHELL_TRAIL.get(),
                        this.xo + (this.getX() - this.xo) * f,
                        this.yo + (this.getY() - this.yo) * f,
                        this.zo + (this.getZ() - this.zo) * f,
                        0, 0, 0);
            }
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        // Pieces of one shell fly together; they must not set each other (or other shells) off.
        return !(entity instanceof BallisticProjectile) && !(entity instanceof Creeper) && super.canHitEntity(entity);
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
            // Explosion damage is measured to a mob's feet and a small charge reaches only ~2 blocks,
            // so a bomblet striking a villager's head would miss it: go off at the middle of the mob.
            Vec3 at = hitResult instanceof EntityHitResult hit
                    ? hit.getEntity().position().add(0, hit.getEntity().getBbHeight() / 2, 0)
                    : this.position();
            this.explode(level, at);
        }
    }

    /** Shot down: the small charge bursts where it was hit (harmless unless it was nearly down). */
    @Override
    public void intercept() {
        if (!(this.level() instanceof ServerLevel level) || this.isRemoved()) {
            return;
        }
        this.markIntercepted();
        level.sendParticles(ParticleTypes.FIREWORK, true, true, this.getX(), this.getY(), this.getZ(), 12, 0.1, 0.1, 0.1, 0.15);
        this.explode(level, this.position());
    }

    private void explode(ServerLevel level, Vec3 at) {
        this.exploded = true;
        this.explosionPos = at;
        float power = Config.FRAGMENT_POWER.get().floatValue();
        if (power > 0) {
            level.explode(this, at.x, at.y, at.z, power, Level.ExplosionInteraction.MOB);
        }
        this.discard();
    }

    /** Went off, by impact or shot down. */
    public boolean isExploded() {
        return this.exploded;
    }

    /** Where it went off; {@link Vec3#ZERO} while still falling. */
    public Vec3 getExplosionPos() {
        return this.explosionPos;
    }
}
