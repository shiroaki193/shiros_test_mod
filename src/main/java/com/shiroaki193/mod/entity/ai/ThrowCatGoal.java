package com.shiroaki193.mod.entity.ai;

import java.util.Comparator;
import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import com.shiroaki193.mod.Config;
import com.shiroaki193.mod.MobArmsRace;
import com.shiroaki193.mod.ballistics.Ballistics;
import com.shiroaki193.mod.carry.CreeperCarry;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

/**
 * Iron golem village defence: walk to a cat, lift it, and lob it next to a creeper. When the cat
 * lands, {@link CatScare} breaks the creepers up.
 */
public class ThrowCatGoal extends Goal {
    /** Marks a cat that is currently claimed, carried or in flight, so two golems never grab the same one. */
    public static final String TAG_AIRBORNE = MobArmsRace.MODID + "_airborne_cat";
    /** Held cats hover over the golem's head (vanilla mobs lose their goal flags while carrying a mob passenger). */
    private static final double CARRY_HEIGHT = 2.9;

    private static final int SCAN_INTERVAL = 20;
    private static final int HOLD_TICKS = 10;
    private static final int MAX_APPROACH_TICKS = 200;
    private static final int MAX_FLIGHT_TICKS = 80;
    private static final int COOLDOWN_TICKS = 60;
    private static final double PICKUP_DISTANCE = 2.5;
    private static final double LAUNCH_HEIGHT = 2.6;
    private static final double THROW_PITCH = Math.toRadians(40);
    private static final double MAX_THROW_SPEED = 4.0;

    private enum Phase { APPROACH, HOLD, FLIGHT, DONE }

    private final IronGolem golem;
    private @Nullable Cat cat;
    private @Nullable Creeper creeper;
    private Phase phase = Phase.DONE;
    private int phaseTicks;
    private int scanCooldown;
    private int cooldown;

    public ThrowCatGoal(IronGolem golem) {
        this.golem = golem;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!Config.CAT_THROW_ENABLED.get()) {
            return false;
        }
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        if (--this.scanCooldown > 0) {
            return false;
        }
        this.scanCooldown = SCAN_INTERVAL;
        this.creeper = this.findCreeper();
        this.cat = this.creeper == null ? null : this.findCat();
        return this.creeper != null && this.cat != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.phase != Phase.DONE && this.cat != null && this.cat.isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.phase = Phase.APPROACH;
        this.phaseTicks = 0;
        this.cat.addTag(TAG_AIRBORNE);
    }

    /** True while a golem is holding this cat over its head. */
    public static boolean isCarried(Cat cat) {
        return cat.entityTags().contains(TAG_AIRBORNE) && cat.isNoGravity();
    }

    /** Gives a cat its AI and gravity back; also used on load in case the game was saved mid-carry. */
    public static void release(Cat cat) {
        cat.setNoAi(false);
        cat.setNoGravity(false);
        cat.removeTag(TAG_AIRBORNE);
    }

    @Override
    public void stop() {
        if (this.cat != null) {
            if (isCarried(this.cat)) {
                this.cat.setDeltaMovement(Vec3.ZERO);
            }
            release(this.cat);
        }
        this.golem.getNavigation().stop();
        this.phase = Phase.DONE;
        this.cat = null;
        this.creeper = null;
    }

    private @Nullable Creeper findCreeper() {
        double range = Config.CAT_THROW_RANGE.get();
        return this.golem.level().getEntitiesOfClass(Creeper.class, this.golem.getBoundingBox().inflate(range),
                        c -> c.isAlive() && !c.isPassenger() && !c.entityTags().contains(CreeperCarry.TAG_THROWN)
                                && c.distanceToSqr(this.golem) > 9 && !CatScare.isNearCat(c))
                .stream().min(Comparator.comparingDouble(c -> c.distanceToSqr(this.golem))).orElse(null);
    }

    private @Nullable Cat findCat() {
        double range = Config.CAT_SEARCH_RANGE.get();
        boolean tamedAllowed = Config.CAT_THROW_TAMED.get();
        return this.golem.level().getEntitiesOfClass(Cat.class, this.golem.getBoundingBox().inflate(range),
                        c -> c.isAlive() && !c.isPassenger() && !c.isVehicle() && c.onGround()
                                && !c.entityTags().contains(TAG_AIRBORNE) && (tamedAllowed || !c.isTame()))
                .stream().min(Comparator.comparingDouble(c -> c.distanceToSqr(this.golem))).orElse(null);
    }

    @Override
    public void tick() {
        Cat cat = this.cat;
        if (cat == null) {
            this.phase = Phase.DONE;
            return;
        }
        this.phaseTicks++;
        switch (this.phase) {
            case APPROACH -> this.tickApproach(cat);
            case HOLD -> this.tickHold(cat);
            case FLIGHT -> this.tickFlight(cat);
            case DONE -> { }
        }
    }

    private void tickApproach(Cat cat) {
        this.golem.getLookControl().setLookAt(cat, 30.0F, 30.0F);
        if (this.golem.distanceToSqr(cat) <= PICKUP_DISTANCE * PICKUP_DISTANCE) {
            this.golem.getNavigation().stop();
            cat.getNavigation().stop();
            cat.setNoAi(true);
            cat.setNoGravity(true);
            this.enter(Phase.HOLD);
            return;
        }
        if (this.phaseTicks > MAX_APPROACH_TICKS) {
            this.phase = Phase.DONE;
        } else if (this.phaseTicks % 10 == 1) {
            this.golem.getNavigation().moveTo(cat, 1.0);
        }
    }

    private void tickHold(Cat cat) {
        Creeper target = this.creeper;
        if (target == null || !target.isAlive()) {
            target = this.findCreeper();
            this.creeper = target;
        }
        if (target == null) {
            this.phase = Phase.DONE;
            return;
        }
        this.golem.getLookControl().setLookAt(target, 30.0F, 30.0F);
        cat.setPos(this.golem.position().add(0, CARRY_HEIGHT, 0));
        cat.setDeltaMovement(Vec3.ZERO);
        if (this.phaseTicks >= HOLD_TICKS) {
            this.throwCat(cat, target);
            this.enter(Phase.FLIGHT);
        }
    }

    private void throwCat(Cat cat, Creeper target) {
        // Mobs only simulate movement with their AI on.
        cat.setNoAi(false);
        cat.setNoGravity(false);
        Vec3 toTarget = target.position().subtract(this.golem.position()).multiply(1, 0, 1).normalize();
        Vec3 from = this.golem.position().add(toTarget.scale(0.8)).add(0, LAUNCH_HEIGHT, 0);
        double[] v = Ballistics.launchVelocity(from.x, from.y, from.z, target.getX(), target.getY(), target.getZ(),
                THROW_PITCH, Config.CAT_THROW_RANGE.get() + 4, Ballistics.Model.MOB, MAX_THROW_SPEED);
        Vec3 velocity = v != null
                ? new Vec3(v[0], v[1], v[2])
                : toTarget.scale(MAX_THROW_SPEED * Math.cos(THROW_PITCH)).add(0, MAX_THROW_SPEED * Math.sin(THROW_PITCH), 0);
        cat.setPos(from);
        cat.setOnGround(false);
        cat.setDeltaMovement(velocity);
        cat.hurtMarked = true;
        this.golem.level().broadcastEntityEvent(this.golem, (byte) 4);
        this.golem.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 1.2F);
    }

    private void tickFlight(Cat cat) {
        if (this.golem.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD, true, true, cat.getX(), cat.getY() + 0.3, cat.getZ(), 1, 0.05, 0.05, 0.05, 0);
            boolean landed = this.phaseTicks > 2 && (cat.onGround() || cat.isInWater());
            if (landed || this.phaseTicks > MAX_FLIGHT_TICKS) {
                CatScare.scareAround(level, cat);
                this.cooldown = COOLDOWN_TICKS;
                this.phase = Phase.DONE;
            }
        }
    }

    private void enter(Phase next) {
        this.phase = next;
        this.phaseTicks = 0;
    }
}
