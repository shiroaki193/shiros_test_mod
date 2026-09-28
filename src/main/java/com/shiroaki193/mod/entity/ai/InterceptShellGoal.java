package com.shiroaki193.mod.entity.ai;

import java.util.Comparator;
import java.util.EnumSet;

import com.shiroaki193.mod.Config;
import com.shiroaki193.mod.ballistics.Ballistics;
import com.shiroaki193.mod.entity.CreeperShell;
import com.shiroaki193.mod.entity.InterceptorSnowball;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Close-in weapon system. Incoming creeper shells come first: in their terminal phase the golem
 * hoses them with fast interceptor snowballs aimed at the predicted meeting point. With nothing
 * incoming, the same rapid fire goes at hostile mobs nearby (replacing the vanilla snowball throw).
 */
public class InterceptShellGoal extends Goal {
    private static final int SCAN_INTERVAL = 2;
    private static final int MAX_LEAD_TICKS = 30;
    private static final double BULLET_DRAG = 0.99;

    private final SnowGolem golem;
    /** What the golem is looking at: the most urgent shell, or else the nearest hostile mob. */
    private @Nullable Entity lookTarget;
    private int fireCooldown;
    private int scanCooldown;

    public InterceptShellGoal(SnowGolem golem) {
        this.golem = golem;
        // MOVE too: a golem stands still while engaging instead of strolling away mid-intercept.
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!Config.CIWS_ENABLED.get() || --this.scanCooldown > 0) {
            return false;
        }
        this.scanCooldown = SCAN_INTERVAL;
        this.lookTarget = this.findLookTarget();
        return this.lookTarget != null;
    }

    @Override
    public boolean canContinueToUse() {
        this.lookTarget = this.findLookTarget();
        return this.lookTarget != null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.golem.getNavigation().stop();
    }

    @Override
    public void stop() {
        this.lookTarget = null;
    }

    private @Nullable Entity findLookTarget() {
        CreeperShell shell = this.nearestThreat();
        return shell != null ? shell : this.nearestHostile();
    }

    private @Nullable CreeperShell nearestThreat() {
        double range = Config.CIWS_DETECT_RANGE.get();
        return this.golem.level().getEntitiesOfClass(CreeperShell.class, this.golem.getBoundingBox().inflate(range),
                        shell -> shell.isAlive() && this.isThreat(shell))
                .stream().min(Comparator.comparingDouble(s -> s.distanceToSqr(this.golem))).orElse(null);
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

    private @Nullable Mob nearestHostile() {
        double range = Config.CIWS_ANTI_MOB_RANGE.get();
        if (range <= 0) {
            return null;
        }
        return this.golem.level().getEntitiesOfClass(Mob.class, this.golem.getBoundingBox().inflate(range),
                        mob -> mob instanceof Enemy && mob.isAlive() && mob.distanceToSqr(this.golem) <= range * range
                                && this.golem.getSensing().hasLineOfSight(mob))
                .stream().min(Comparator.comparingDouble(m -> m.distanceToSqr(this.golem))).orElse(null);
    }

    @Override
    public void tick() {
        Entity look = this.lookTarget;
        if (look == null) {
            return;
        }
        this.golem.getLookControl().setLookAt(look, 90.0F, 90.0F);
        if (--this.fireCooldown > 0) {
            return;
        }
        this.fireCooldown = Config.CIWS_FIRE_INTERVAL.get();
        if (!(this.golem.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 aim = this.bestShellAim(level);
        if (aim == null && !(look instanceof CreeperShell)) {
            aim = this.hostileAim(level, look);
        }
        if (aim != null) {
            this.fire(level, aim);
        }
    }

    /** A firing solution: where to aim, and how soon the shell would have landed otherwise. */
    private record Solution(Vec3 aim, double landingTicks) {
    }

    /**
     * Aim point on the most urgent shell that has a clear line of fire. The nearest shell is often
     * already below the rooftops; shooting at it just puts snowballs into roofs and trees (seen in
     * the user's village), so skip blocked solutions and take the next shell instead.
     */
    private @Nullable Vec3 bestShellAim(ServerLevel level) {
        double speed = Config.CIWS_BULLET_SPEED.get();
        Vec3 muzzle = this.golem.getEyePosition();
        Solution best = null;
        double range = Config.CIWS_DETECT_RANGE.get();
        for (CreeperShell shell : level.getEntitiesOfClass(CreeperShell.class, this.golem.getBoundingBox().inflate(range), Entity::isAlive)) {
            if (!this.isThreat(shell)) {
                continue;
            }
            // The bullet is added mid-tick, so it first moves next tick; line the shell up with that.
            Ballistics.State shellNow = shell.ballisticState();
            if (!shell.hasMovedThisTick()) {
                shellNow = shellNow.step();
            }
            Ballistics.Intercept lead = Ballistics.lead(shellNow, muzzle.x, muzzle.y, muzzle.z, speed, BULLET_DRAG, MAX_LEAD_TICKS);
            if (lead == null) {
                continue;
            }
            Vec3 aim = new Vec3(lead.x(), lead.y(), lead.z());
            if (!this.hasClearShot(level, muzzle, aim)) {
                continue;
            }
            Ballistics.Landing landing = Ballistics.landing(shellNow, this.golem.getY());
            double landingTicks = landing == null ? Double.MAX_VALUE : landing.ticks();
            if (best == null || landingTicks < best.landingTicks()) {
                best = new Solution(aim, landingTicks);
            }
        }
        return best == null ? null : best.aim();
    }

    /** Aim at the mob's body, led by its current walking velocity. */
    private @Nullable Vec3 hostileAim(ServerLevel level, Entity mob) {
        Vec3 muzzle = this.golem.getEyePosition();
        Vec3 body = mob.position().add(0, mob.getBbHeight() * 0.5, 0);
        double ticks = muzzle.distanceTo(body) / Config.CIWS_BULLET_SPEED.get();
        Vec3 aim = body.add(mob.getDeltaMovement().multiply(ticks, 0, ticks));
        return this.hasClearShot(level, muzzle, aim) ? aim : null;
    }

    private void fire(ServerLevel level, Vec3 aim) {
        Vec3 muzzle = this.golem.getEyePosition();
        InterceptorSnowball bullet = new InterceptorSnowball(level, this.golem);
        bullet.setPos(muzzle);
        // shoot() normalises the direction to 'speed'; its uncertainty unit is roughly one degree.
        bullet.shoot(aim.x - muzzle.x, aim.y - muzzle.y, aim.z - muzzle.z, Config.CIWS_BULLET_SPEED.get().floatValue(),
                Config.CIWS_SPREAD.get().floatValue());
        level.addFreshEntity(bullet);
        this.golem.playSound(SoundEvents.SNOW_GOLEM_SHOOT, 1.0F, 1.6F + this.golem.getRandom().nextFloat() * 0.3F);
    }

    private boolean hasClearShot(ServerLevel level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.golem))
                .getType() == HitResult.Type.MISS;
    }
}
