package com.shiroaki193.mod.carry;

import java.util.List;

import com.shiroaki193.mod.MobArmsRace;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;

/**
 * Thrown creepers (see {@code HeldCreeperItem}) explode on the first thing they hit. The state is a
 * scoreboard tag plus the throw time in persistent data, so it survives a save mid-flight.
 */
public final class CreeperCarry {
    public static final String TAG_THROWN = MobArmsRace.MODID + "_thrown";
    private static final String THROWN_AT = MobArmsRace.MODID + "_thrown_at";

    /** Ticks after release during which the creeper cannot detonate (it starts right next to the thrower). */
    private static final int ARMING_TICKS = 3;
    private static final int MAX_FLIGHT_TICKS = 100;

    private CreeperCarry() {
    }

    public static void markThrown(Creeper creeper) {
        creeper.addTag(TAG_THROWN);
        creeper.getPersistentData().putLong(THROWN_AT, creeper.level().getGameTime());
    }

    /** Server side, every tick for every creeper. */
    public static void tick(Creeper creeper) {
        if (!creeper.entityTags().contains(TAG_THROWN) || !(creeper.level() instanceof ServerLevel level)) {
            return;
        }
        long flying = level.getGameTime() - creeper.getPersistentData().getLongOr(THROWN_AT, level.getGameTime());
        if (flying < ARMING_TICKS) {
            return;
        }
        if (flying > MAX_FLIGHT_TICKS || creeper.onGround() || creeper.horizontalCollision
                || creeper.verticalCollision || creeper.isInWater() || hitsLivingEntity(creeper)) {
            detonate(level, creeper);
        }
    }

    private static boolean hitsLivingEntity(Creeper creeper) {
        List<LivingEntity> hit = creeper.level().getEntitiesOfClass(LivingEntity.class,
                creeper.getBoundingBox().inflate(0.3), e -> e != creeper && e.isAlive() && !e.isSpectator());
        return !hit.isEmpty();
    }

    private static void detonate(ServerLevel level, Creeper creeper) {
        float radius = creeper.isPowered() ? 6.0F : 3.0F;
        creeper.removeTag(TAG_THROWN);
        level.explode(creeper, creeper.getX(), creeper.getY(), creeper.getZ(), radius, Level.ExplosionInteraction.MOB);
        creeper.discard();
    }
}
