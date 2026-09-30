package com.shiroaki193.mod.entity;

import java.util.ArrayList;
import java.util.List;

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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The "firework + elytra creeper" mortar round. Flies the {@link Ballistics} arc with two fuzes:
 * impact (explodes on the first block or mob it hits) and proximity (air-bursts on the way down
 * next to a villager, golem or player). A shell shot down by a snow golem either detonates where
 * it was hit, so an intercept close to the ground still does damage, or breaks into a few
 * {@link CreeperBomblet}s that fall on and explode with a much smaller charge.
 */
public class CreeperShell extends BallisticProjectile {
    private static final int TRAIL_PARTICLES_PER_TICK = 8;
    /** Shells shot down lower than this (blocks above the ground) never break apart. */
    private static final double MIN_BREAK_HEIGHT = 20.0;

    private boolean detonated;
    private boolean proximityBurst;
    private final List<CreeperBomblet> fragments = new ArrayList<>();
    /** Highest point reached so far (for balance measurements). */
    private double peakY = Double.NEGATIVE_INFINITY;
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
    public void tick() {
        super.tick();
        this.peakY = Math.max(this.peakY, this.getY());
        if (this.level().isClientSide() && this.isAlive()) {
            spawnTrail();
        } else if (this.level() instanceof ServerLevel level && this.isAlive() && this.proximityFuzeTriggered()) {
            this.proximityBurst = true;
            this.detonate(level, this.position());
        }
    }

    /**
     * Armed only on the way down, so the launching mortar and anything near it are safe. Measured
     * to the target's feet on purpose: vanilla explosion damage reaches 2 x power blocks from the
     * feet (4 at the default power 2), so bursting further out, e.g. 3 blocks above a villager's
     * head, would hurt nothing.
     */
    private boolean proximityFuzeTriggered() {
        double radius = Config.SHELL_PROXIMITY_FUZE.get();
        if (radius <= 0 || this.getDeltaMovement().y >= 0) {
            return false;
        }
        return !this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(radius),
                e -> e.isAlive() && !e.isSpectator() && isFuzeTarget(e) && e.distanceToSqr(this) <= radius * radius).isEmpty();
    }

    private static boolean isFuzeTarget(LivingEntity entity) {
        return MortarCreeper.isVillageTarget(entity) || entity instanceof Player;
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
        return !(entity instanceof BallisticProjectile) && !(entity instanceof Creeper) && super.canHitEntity(entity);
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (this.level() instanceof ServerLevel serverLevel && !this.isRemoved()) {
            this.detonate(serverLevel, hitResult.getLocation());
        }
    }

    /** Impact or proximity fuze: the shell reached its target. */
    private void detonate(ServerLevel level, Vec3 at) {
        this.detonated = true;
        this.detonationPos = at;
        this.explodeWarhead(level);
        this.discard();
    }

    /**
     * Shot down mid-air. Either the warhead goes off (high up a harmless burst, but a late intercept
     * close to a golem or a roof hurts), or the shell breaks apart into bomblets that keep most of
     * its momentum and scatter. The white firework shows it was shot down.
     */
    @Override
    public void intercept() {
        if (!(this.level() instanceof ServerLevel serverLevel) || this.isRemoved()) {
            return;
        }
        this.markIntercepted();
        this.detonationPos = this.position();
        serverLevel.sendParticles(ParticleTypes.FIREWORK, true, true, this.getX(), this.getY(), this.getZ(), 40, 0.2, 0.2, 0.2, 0.25);
        serverLevel.sendParticles(ParticleTypes.CLOUD, true, true, this.getX(), this.getY(), this.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
        serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.HOSTILE, 3.0F, 0.9F + this.random.nextFloat() * 0.2F);
        if (this.heightAboveGround(serverLevel) >= MIN_BREAK_HEIGHT && this.random.nextDouble() < Config.FRAGMENT_CHANCE.get()) {
            this.breakApart(serverLevel);
        } else {
            this.explodeWarhead(serverLevel);
        }
        this.discard();
    }

    /**
     * Low down, the bomblets of a shell shot down right above a golem (last-ditch self-defence)
     * would have no room to scatter and rain on the golem itself; there the warhead goes off whole.
     */
    private double heightAboveGround(ServerLevel level) {
        return this.getY() - level.getHeight(Heightmap.Types.MOTION_BLOCKING, this.getBlockX(), this.getBlockZ());
    }

    private void breakApart(ServerLevel level) {
        int min = Config.FRAGMENTS_MIN.get();
        int max = Math.max(min, Config.FRAGMENTS_MAX.get());
        int count = min + this.random.nextInt(max - min + 1);
        Vec3 velocity = this.getDeltaMovement().scale(0.8);
        for (int i = 0; i < count; i++) {
            // Spread evenly around, with some randomness, plus a small kick up from the break.
            double angle = (i + this.random.nextDouble() * 0.5) * Math.PI * 2 / count;
            double push = 0.3 + this.random.nextDouble() * 0.2;
            Vec3 kick = new Vec3(Math.cos(angle) * push, 0.1 + this.random.nextDouble() * 0.2, Math.sin(angle) * push);
            CreeperBomblet bomblet = new CreeperBomblet(level, this.position(), velocity.add(kick));
            level.addFreshEntity(bomblet);
            this.fragments.add(bomblet);
        }
        level.sendParticles(ParticleTypes.SMOKE, true, true, this.getX(), this.getY(), this.getZ(), 12, 0.2, 0.2, 0.2, 0.05);
    }

    private void explodeWarhead(ServerLevel level) {
        float power = Config.SHELL_EXPLOSION_POWER.get().floatValue();
        if (power > 0) {
            level.explode(this, this.getX(), this.getY(), this.getZ(), power, Level.ExplosionInteraction.MOB);
        }
    }

    /** Reached a target: impact or proximity burst (not shot down). */
    public boolean isDetonated() {
        return this.detonated;
    }

    /** Detonated by the proximity fuze rather than on impact. */
    public boolean isProximityBurst() {
        return this.proximityBurst;
    }

    public double getPeakY() {
        return this.peakY;
    }

    /** Bomblets this shell broke into when shot down; empty if its warhead exploded instead. */
    public List<CreeperBomblet> getFragments() {
        return this.fragments;
    }

    /** Where the shell exploded or was shot down; {@link Vec3#ZERO} while still flying. */
    public Vec3 getDetonationPos() {
        return this.detonationPos;
    }
}
