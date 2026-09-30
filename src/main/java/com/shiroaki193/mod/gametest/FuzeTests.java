package com.shiroaki193.mod.gametest;

import static com.shiroaki193.mod.gametest.TestSupport.GROUND;
import static com.shiroaki193.mod.gametest.TestSupport.LANE_Z;
import static com.shiroaki193.mod.gametest.TestSupport.lane;
import static com.shiroaki193.mod.gametest.TestSupport.launch;
import static com.shiroaki193.mod.gametest.TestSupport.spawnAt;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.shiroaki193.mod.Config;
import com.shiroaki193.mod.MobArmsRace;
import com.shiroaki193.mod.entity.CreeperBomblet;
import com.shiroaki193.mod.entity.CreeperShell;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

/** Shell fuzes: proximity air bursts, intercepted shells detonating where they were hit, or breaking into bomblets. */
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
        interceptWhole(helper, shell);
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
        interceptWhole(helper, shell);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(shell.isIntercepted(), "shell was not intercepted");
            helper.assertTrue(golem.getHealth() == golem.getMaxHealth(), "a high intercept hurt the golem");
            helper.succeed();
        });
    }

    /**
     * A shell coming down fast (as in a late intercept) and shot down 25 blocks up always breaks
     * here: into 2-5 bomblets that scatter and each explode on landing.
     */
    static void shellBreaksIntoBomblets(GameTestHelper helper) {
        Runnable restore = fragmentChance(1.0);
        Vec3 at = helper.absoluteVec(new Vec3(60.5, 25, 16.5));
        CreeperShell shell = new CreeperShell(helper.getLevel(), at.x, at.y, at.z);
        shell.setDeltaMovement(0.5, -1.2, 0);
        helper.getLevel().addFreshEntity(shell);
        // Counted from the shell's own list: an area scan once also caught a stray bomblet.
        Set<CreeperBomblet> seen = new LinkedHashSet<>();
        helper.onEachTick(() -> seen.addAll(
                helper.getLevel().getEntitiesOfClass(CreeperBomblet.class, helper.getBounds().inflate(0, 64, 0))));
        helper.runAfterDelay(1, () -> {
            shell.intercept();
            restore.run();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(shell.isIntercepted(), "shell was not shot down");
            List<CreeperBomblet> bomblets = shell.getFragments();
            int fragments = bomblets.size();
            helper.assertTrue(fragments >= 2 && fragments <= 5, "shell broke into " + fragments + " bomblets");
            helper.assertTrue(bomblets.stream().allMatch(CreeperBomblet::isExploded), "bomblets still falling");
            if (!bomblets.containsAll(seen)) {
                MobArmsRace.LOGGER.warn("[gametest] stray bomblets over the test area: {}",
                        seen.stream().filter(b -> !bomblets.contains(b)).map(b -> b.getType().toShortString() + "@" + b.position()).toList());
            }
            double spread = 0;
            for (CreeperBomblet a : bomblets) {
                for (CreeperBomblet b : bomblets) {
                    Vec3 d = a.getExplosionPos().subtract(b.getExplosionPos());
                    spread = Math.max(spread, Math.sqrt(d.x * d.x + d.z * d.z));
                }
            }
            MobArmsRace.LOGGER.info("[gametest] shell shot down 25 blocks up broke into {} bomblets, landing up to {} blocks apart",
                    fragments, String.format("%.1f", spread));
            helper.assertTrue(spread > 2.0, "bomblets landed within " + spread + " blocks of each other");
        });
    }

    /**
     * Four villagers: a shell and a bomblet each landing 1.5 blocks from one (proximity fuze off, so
     * both go off on the ground), a bomblet striking one directly, and a bomblet landing 3 blocks
     * from one (inside a shell's 4 block blast, outside a bomblet's 2). The bomblet must hurt less
     * at the same spot, still hurt on a direct hit, and do nothing 3 blocks away.
     */
    static void bombletHurtsLessThanShell(GameTestHelper helper) {
        double originalFuze = Config.SHELL_PROXIMITY_FUZE.get();
        Config.SHELL_PROXIMITY_FUZE.set(0.0);
        Villager shellSide = sturdyVillager(helper, 6.5);
        Villager bombletSide = sturdyVillager(helper, 18.5);
        Villager struck = sturdyVillager(helper, 30.5);
        Villager bystander = sturdyVillager(helper, 42.5);
        Vec3 down = new Vec3(0, -1.0, 0);
        Vec3 side = new Vec3(1.5, 6, 0);
        Vec3 shellFrom = shellSide.position().add(side);
        CreeperShell shell = new CreeperShell(helper.getLevel(), shellFrom.x, shellFrom.y, shellFrom.z);
        shell.setDeltaMovement(down);
        helper.getLevel().addFreshEntity(shell);
        List<CreeperBomblet> bomblets = List.of(
                new CreeperBomblet(helper.getLevel(), bombletSide.position().add(side), down),
                new CreeperBomblet(helper.getLevel(), struck.position().add(0, 6, 0), down),
                new CreeperBomblet(helper.getLevel(), bystander.position().add(3, 6, 0), down));
        bomblets.forEach(helper.getLevel()::addFreshEntity);
        helper.succeedWhen(() -> {
            helper.assertTrue(shell.isDetonated() && bomblets.stream().allMatch(CreeperBomblet::isExploded), "still falling");
            Config.SHELL_PROXIMITY_FUZE.set(originalFuze);
            float shellDamage = shellSide.getMaxHealth() - shellSide.getHealth();
            float bombletDamage = bombletSide.getMaxHealth() - bombletSide.getHealth();
            float struckDamage = struck.getMaxHealth() - struck.getHealth();
            float nearDamage = bystander.getMaxHealth() - bystander.getHealth();
            MobArmsRace.LOGGER.info("[gametest] damage 1.5 blocks away: shell {}, bomblet {}; bomblet direct hit {}, 3 blocks away {}",
                    shellDamage, bombletDamage, struckDamage, nearDamage);
            if (!(bombletDamage < shellDamage * 0.5)) {
                helper.fail("1.5 blocks away a bomblet did " + bombletDamage + " damage, a shell " + shellDamage);
            } else if (struckDamage <= 0) {
                helper.fail("a bomblet striking a villager did no damage");
            } else if (nearDamage != 0) {
                helper.fail("a bomblet 3 blocks away did " + nearDamage + " damage");
            }
        });
    }

    /** Shot down without breaking apart: tests of the warhead itself. */
    private static void interceptWhole(GameTestHelper helper, CreeperShell shell) {
        helper.runAfterDelay(1, () -> {
            Runnable restore = fragmentChance(0.0);
            shell.intercept();
            restore.run();
        });
    }

    /** Sets the break-up chance; run the returned restore before any assertion can fail. */
    private static Runnable fragmentChance(double chance) {
        double original = Config.FRAGMENT_CHANCE.get();
        Config.FRAGMENT_CHANCE.set(chance);
        return () -> Config.FRAGMENT_CHANCE.set(original);
    }

    private static Villager sturdyVillager(GameTestHelper helper, double x) {
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(x, GROUND, LANE_Z + 0.5));
        villager.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        villager.setHealth(villager.getMaxHealth());
        return villager;
    }

    private static CreeperShell shellAbove(GameTestHelper helper, SnowGolem golem, double aboveHead) {
        Vec3 at = golem.position().add(0, golem.getBbHeight() + aboveHead, 0);
        CreeperShell shell = new CreeperShell(helper.getLevel(), at.x, at.y, at.z);
        shell.setNoGravity(true);
        helper.getLevel().addFreshEntity(shell);
        return shell;
    }
}
