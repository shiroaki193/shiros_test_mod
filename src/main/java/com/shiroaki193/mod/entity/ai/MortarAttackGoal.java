package com.shiroaki193.mod.entity.ai;

import java.util.EnumSet;

import com.shiroaki193.mod.Config;
import com.shiroaki193.mod.ballistics.Ballistics;
import com.shiroaki193.mod.entity.CreeperShell;
import com.shiroaki193.mod.entity.MortarCreeper;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** Stops, winds up (flashing like a primed creeper), then fires its shells as one salvo. */
public class MortarAttackGoal extends Goal {
    /** Kept below the creeper fuse (30 ticks) so the wind-up never detonates the mortar itself. */
    public static final int WINDUP_TICKS = 16;
    private static final double LAUNCH_HEIGHT = 1.9;

    private final MortarCreeper mortar;
    private int windup;
    private int nextShotIn;
    private boolean firing;

    public MortarAttackGoal(MortarCreeper mortar) {
        this.mortar = mortar;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // A cat nearby breaks the firing position (and aborts a salvo in progress).
        return this.mortar.getAmmo() > 0 && this.targetInRange() && !CatScare.isNearCat(this.mortar);
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private boolean targetInRange() {
        LivingEntity target = this.mortar.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        double dx = target.getX() - this.mortar.getX();
        double dz = target.getZ() - this.mortar.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        return distance >= Config.MORTAR_MIN_RANGE.get() && distance <= Config.MORTAR_MAX_RANGE.get();
    }

    @Override
    public void start() {
        this.mortar.getNavigation().stop();
        this.windup = WINDUP_TICKS;
        this.firing = false;
        this.mortar.setSwellDir(1);
    }

    @Override
    public void stop() {
        this.mortar.setSwellDir(-1);
        this.firing = false;
    }

    @Override
    public void tick() {
        LivingEntity target = this.mortar.getTarget();
        if (target == null) {
            return;
        }
        this.mortar.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (!this.firing) {
            if (--this.windup <= 0) {
                this.firing = true;
                this.nextShotIn = 0;
                this.mortar.setSwellDir(-1);
            }
            return;
        }
        if (--this.nextShotIn <= 0) {
            this.fireAt(target);
            this.nextShotIn = Config.MORTAR_SALVO_INTERVAL.get();
        }
    }

    private void fireAt(LivingEntity target) {
        if (!(this.mortar.level() instanceof ServerLevel level)) {
            return;
        }
        double scatter = Config.MORTAR_SCATTER.get();
        Vec3 from = this.mortar.position().add(0, LAUNCH_HEIGHT, 0);
        Vec3 aim = target.position().add(
                this.mortar.getRandom().nextGaussian() * scatter * 0.5, 0,
                this.mortar.getRandom().nextGaussian() * scatter * 0.5);
        double[] v = Ballistics.launchVelocity(from.x, from.y, from.z, aim.x, aim.y, aim.z,
                Math.toRadians(Ballistics.DEFAULT_PITCH_DEG), Config.MORTAR_MAX_RANGE.get());
        if (v == null) {
            return;
        }
        CreeperShell shell = new CreeperShell(level, this.mortar);
        shell.setPos(from);
        shell.setDeltaMovement(v[0], v[1], v[2]);
        level.addFreshEntity(shell);
        this.mortar.consumeAmmo();

        level.sendParticles(ParticleTypes.EXPLOSION, from.x, from.y, from.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.POOF, from.x, from.y - 0.5, from.z, 12, 0.4, 0.2, 0.4, 0.05);
        this.mortar.playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 3.0F, 0.8F + this.mortar.getRandom().nextFloat() * 0.2F);
    }
}
