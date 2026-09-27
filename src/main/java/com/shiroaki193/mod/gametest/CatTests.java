package com.shiroaki193.mod.gametest;

import static com.shiroaki193.mod.gametest.TestSupport.GROUND;
import static com.shiroaki193.mod.gametest.TestSupport.LANE_Z;
import static com.shiroaki193.mod.gametest.TestSupport.horizontalDistance;
import static com.shiroaki193.mod.gametest.TestSupport.spawnAt;

import com.shiroaki193.mod.MobArmsRace;
import com.shiroaki193.mod.entity.MortarCreeper;
import com.shiroaki193.mod.entity.ai.ThrowCatGoal;
import com.shiroaki193.mod.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** K1: iron golems throwing cats at creepers. */
final class CatTests {
    private static final double LANDING_TOLERANCE = 4.0;

    private CatTests() {
    }

    /** The golem fetches the cat and lands it next to a creeper 20 blocks away. */
    static void ironGolemThrowsCatAtCreeper(GameTestHelper helper) {
        spawnAt(helper, EntityType.IRON_GOLEM, 6.5);
        Cat cat = spawnAt(helper, EntityType.CAT, 9.5);
        Creeper creeper = helper.spawnWithNoFreeWill(EntityType.CREEPER, new Vec3(26.5, GROUND, LANE_Z + 0.5));
        boolean[] carried = {false};
        boolean[] airborne = {false};
        helper.onEachTick(() -> {
            if (ThrowCatGoal.isCarried(cat)) {
                carried[0] = true;
            } else if (carried[0] && !cat.onGround()) {
                airborne[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(carried[0], "golem has not picked the cat up");
            helper.assertTrue(airborne[0] && cat.onGround(), "cat has not been thrown and landed yet");
            double miss = horizontalDistance(cat.position(), creeper.position());
            MobArmsRace.LOGGER.info("[gametest] thrown cat landed {} blocks from the creeper", String.format("%.1f", miss));
            helper.assertTrue(miss < LANDING_TOLERANCE, "cat landed " + miss + " blocks from the creeper");
            helper.assertTrue(cat.isAlive() && cat.getHealth() == cat.getMaxHealth(), "cat got hurt");
        });
    }

    /** Players' pets are off limits by default. */
    static void ironGolemLeavesTamedCatAlone(GameTestHelper helper) {
        spawnAt(helper, EntityType.IRON_GOLEM, 6.5);
        Cat cat = spawnAt(helper, EntityType.CAT, 9.5);
        cat.setTame(true, false);
        helper.spawnWithNoFreeWill(EntityType.CREEPER, new Vec3(26.5, GROUND, LANE_Z + 0.5));
        boolean[] carried = {false};
        helper.onEachTick(() -> carried[0] |= ThrowCatGoal.isCarried(cat));
        helper.runAtTickTime(120, () -> {
            helper.assertFalse(carried[0], "golem picked up a tamed cat");
            helper.succeed();
        });
    }

    /**
     * A cat next to a mortar creeper stops it firing. The mortar is walled in so it cannot simply
     * flee out of range (vanilla creepers run from cats); unhindered it fires within ~40 ticks.
     */
    static void catStopsMortarFiring(GameTestHelper helper) {
        for (int y = 1; y <= 3; y++) {
            for (int x = 3; x <= 5; x++) {
                for (int z = 3; z <= 6; z++) {
                    if (x == 3 || x == 5 || z == 3 || z == 6) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.GLASS);
                    }
                }
            }
        }
        MortarCreeper mortar = helper.spawn(ModEntities.MORTAR_CREEPER.get(), new Vec3(4.5, GROUND, 4.5));
        int startAmmo = mortar.getAmmo();
        helper.spawnWithNoFreeWill(EntityType.CAT, new Vec3(4.5, GROUND, 5.5));
        helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(40.5, GROUND, LANE_Z + 0.5));
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(mortar.getAmmo() == startAmmo,
                    "mortar fired " + (startAmmo - mortar.getAmmo()) + " shells with a cat next to it");
            helper.succeed();
        });
    }
}
