package com.shiroaki193.mod.gametest;

import static com.shiroaki193.mod.gametest.TestSupport.GROUND;
import static com.shiroaki193.mod.gametest.TestSupport.LANE_Z;
import static com.shiroaki193.mod.gametest.TestSupport.lane;
import static com.shiroaki193.mod.gametest.TestSupport.launch;
import static com.shiroaki193.mod.gametest.TestSupport.spawnAt;

import com.shiroaki193.mod.MobArmsRace;
import com.shiroaki193.mod.entity.CreeperShell;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

/** Shell fuzes: proximity air bursts, and intercepted shells detonating where they were hit. */
final class FuzeTests {
    private FuzeTests() {
    }

    /** A shell coming down on a villager bursts above it instead of waiting for the ground. */
    static void proximityFuzeAirBurstsOverVillager(GameTestHelper helper) {
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(40.5, GROUND, LANE_Z + 0.5));
        Vec3 feet = villager.position();
        CreeperShell shell = launch(helper, lane(helper, 2.5, 2.0), feet);
        helper.succeedWhen(() -> {
            helper.assertTrue(shell.isDetonated(), "shell has not gone off yet");
            helper.assertTrue(shell.isProximityBurst(), "shell went off on impact, not by proximity");
            double height = shell.getDetonationPos().y - feet.y;
            MobArmsRace.LOGGER.info("[gametest] proximity burst {} blocks above the villager's feet, villager health {}",
                    String.format("%.1f", height), villager.getHealth());
            helper.assertTrue(height > 1.0, "burst only " + height + " blocks above the villager's feet");
            helper.assertTrue(villager.getHealth() < villager.getMaxHealth(), "the air burst did not hurt the villager");
        });
    }

    /** Shot down two blocks above a golem's head: the warhead still goes off and hurts it. */
    static void lateInterceptHurtsGolem(GameTestHelper helper) {
        SnowGolem golem = spawnAt(helper, EntityType.SNOW_GOLEM, 20.5);
        CreeperShell shell = shellAbove(helper, golem, 2.0);
        helper.runAfterDelay(1, shell::intercept);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(shell.isIntercepted(), "shell was not intercepted");
            helper.assertTrue(golem.isDeadOrDying() || golem.getHealth() < golem.getMaxHealth(),
                    "a shell shot down 2 blocks above the golem did no damage");
            helper.succeed();
        });
    }

    /** Shot down 20 blocks up (a normal intercept): nothing below gets hurt. */
    static void highInterceptIsHarmless(GameTestHelper helper) {
        SnowGolem golem = spawnAt(helper, EntityType.SNOW_GOLEM, 20.5);
        CreeperShell shell = shellAbove(helper, golem, 20.0);
        helper.runAfterDelay(1, shell::intercept);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(shell.isIntercepted(), "shell was not intercepted");
            helper.assertTrue(golem.getHealth() == golem.getMaxHealth(), "a high intercept hurt the golem");
            helper.succeed();
        });
    }

    private static CreeperShell shellAbove(GameTestHelper helper, SnowGolem golem, double aboveHead) {
        Vec3 at = golem.position().add(0, golem.getBbHeight() + aboveHead, 0);
        CreeperShell shell = new CreeperShell(helper.getLevel(), at.x, at.y, at.z);
        shell.setNoGravity(true);
        helper.getLevel().addFreshEntity(shell);
        return shell;
    }
}
