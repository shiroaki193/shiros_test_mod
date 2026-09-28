package com.shiroaki193.mod.entity.ai;

import com.shiroaki193.mod.Config;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.Ocelot;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

/** Cats vs. creepers: a landed cat breaks up nearby creepers, and mortars will not fire with one close by. */
public final class CatScare {
    private static final double PUSH = 0.9;

    private CatScare() {
    }

    /** @return how many creepers were scared */
    public static int scareAround(ServerLevel level, Cat cat) {
        double radius = Config.CAT_SCARE_RADIUS.get();
        int scared = 0;
        for (Creeper creeper : level.getEntitiesOfClass(Creeper.class, cat.getBoundingBox().inflate(radius), Entity::isAlive)) {
            creeper.setSwellDir(-1);
            creeper.setTarget(null);
            creeper.getNavigation().stop();
            Vec3 away = creeper.position().subtract(cat.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
            creeper.push(away.x * PUSH, 0.35, away.z * PUSH);
            creeper.hurtMarked = true;
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, true, true, creeper.getX(), creeper.getEyeY() + 0.4, creeper.getZ(), 3, 0.3, 0.2, 0.3, 0);
            scared++;
        }
        if (scared > 0) {
            cat.hiss();
        }
        return scared;
    }

    /** True when a cat or ocelot is within the scare radius of this mob. */
    public static boolean isNearCat(LivingEntity mob) {
        double radius = Config.CAT_SCARE_RADIUS.get();
        return !mob.level().getEntitiesOfClass(Cat.class, mob.getBoundingBox().inflate(radius), Entity::isAlive).isEmpty()
                || !mob.level().getEntitiesOfClass(Ocelot.class, mob.getBoundingBox().inflate(radius), Entity::isAlive).isEmpty();
    }
}
