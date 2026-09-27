package com.shiroaki193.mod.entity.ai;

import java.util.EnumSet;

import com.shiroaki193.mod.Config;
import com.shiroaki193.mod.ballistics.Ballistics;
import com.shiroaki193.mod.entity.CreeperShell;
import com.shiroaki193.mod.entity.InterceptorSnowball;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Close-in weapon system: tracks creeper shells, and in their terminal phase hoses them with
 * fast interceptor snowballs aimed at the predicted meeting point.
 */
public class InterceptShellGoal extends Goal {
    private static final int SCAN_INTERVAL = 2;
    private static final int MAX_LEAD_TICKS = 30;
    private static final double BULLET_DRAG = 0.99;

    private final SnowGolem golem;
    private @Nullable CreeperShell target;
    private int fireCooldown;
    private int scanCooldown;

    public InterceptShellGoal(SnowGolem golem) {
        this.golem = golem;
        this.setFlags(EnumSet.of(Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!Config.CIWS_ENABLED.get() || --this.scanCooldown > 0) {
            return false;
        }
        this.scanCooldown = SCAN_INTERVAL;
        this.target = this.findThreat();
        return this.target != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.target == null || !this.target.isAlive() || !this.isThreat(this.target)) {
            this.target = this.findThreat();
        }
        return this.target != null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        this.target = null;
    }

    private @Nullable CreeperShell findThreat() {
        double range = Config.CIWS_DETECT_RANGE.get();
        CreeperShell best = null;
        double bestDist = Double.MAX_VALUE;
        for (CreeperShell shell : this.golem.level().getEntitiesOfClass(CreeperShell.class,
                this.golem.getBoundingBox().inflate(range), Entity::isAlive)) {
            if (!this.isThreat(shell)) {
                continue;
            }
            double d = shell.distanceToSqr(this.golem);
            if (d < bestDist) {
                bestDist = d;
                best = shell;
            }
        }
        return best;
    }

    /** A shell is worth engaging if it is coming down and will land inside the protected radius. */
    private boolean isThreat(CreeperShell shell) {
        if (shell.getDeltaMovement().y >= 0) {
            return false;
        }
        double engage = Config.CIWS_ENGAGE_RANGE.get();
        if (shell.distanceToSqr(this.golem) > engage * engage) {
            return false;
        }
        Ballistics.Landing landing = Ballistics.landing(shell.ballisticState(), this.golem.getY());
        if (landing == null) {
            return false;
        }
        return landing.horizontalDistanceFrom(this.golem.getX(), this.golem.getZ()) <= Config.CIWS_PROTECT_RADIUS.get();
    }

    @Override
    public void tick() {
        CreeperShell shell = this.target;
        if (shell == null) {
            return;
        }
        this.golem.getLookControl().setLookAt(shell, 90.0F, 90.0F);
        if (--this.fireCooldown > 0) {
            return;
        }
        this.fireCooldown = Config.CIWS_FIRE_INTERVAL.get();
        this.fireAt(shell);
    }

    private void fireAt(CreeperShell shell) {
        if (!(this.golem.level() instanceof ServerLevel level)) {
            return;
        }
        double speed = Config.CIWS_BULLET_SPEED.get();
        Vec3 muzzle = this.golem.getEyePosition();
        Ballistics.Intercept lead = Ballistics.lead(shell.ballisticState(), muzzle.x, muzzle.y, muzzle.z,
                speed, BULLET_DRAG, MAX_LEAD_TICKS);
        if (lead == null) {
            return;
        }
        InterceptorSnowball bullet = new InterceptorSnowball(level, this.golem);
        bullet.setPos(muzzle);
        // shoot() normalises the direction to 'speed'; its uncertainty unit is roughly one degree.
        bullet.shoot(lead.x() - muzzle.x, lead.y() - muzzle.y, lead.z() - muzzle.z, (float) speed,
                Config.CIWS_SPREAD.get().floatValue());
        level.addFreshEntity(bullet);
        this.golem.playSound(SoundEvents.SNOW_GOLEM_SHOOT, 1.0F, 1.6F + this.golem.getRandom().nextFloat() * 0.3F);
    }
}
