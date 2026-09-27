package com.shiroaki193.mod.gametest;

import static com.shiroaki193.mod.gametest.TestSupport.GROUND;
import static com.shiroaki193.mod.gametest.TestSupport.LANE_Z;
import static com.shiroaki193.mod.gametest.TestSupport.horizontalDistance;
import static com.shiroaki193.mod.gametest.TestSupport.lane;
import static com.shiroaki193.mod.gametest.TestSupport.launch;
import static com.shiroaki193.mod.gametest.TestSupport.spawnAt;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.shiroaki193.mod.Config;
import com.shiroaki193.mod.MobArmsRace;
import com.shiroaki193.mod.entity.CreeperShell;
import com.shiroaki193.mod.entity.MortarCreeper;
import com.shiroaki193.mod.registry.ModEntities;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

/** Mortar shells vs. snow golem close-in defence. */
final class ArmsRaceTests {
    private ArmsRaceTests() {
    }

    /** The real entity must follow the predicted arc and land where it was aimed (38 blocks). */
    static void shellLandsOnTarget(GameTestHelper helper) {
        Vec3 target = lane(helper, 40.5, GROUND);
        CreeperShell shell = launch(helper, lane(helper, 2.5, 2.0), target);
        helper.succeedWhen(() -> {
            helper.assertTrue(shell.isDetonated(), "shell has not landed yet");
            double miss = horizontalDistance(shell.getDetonationPos(), target);
            MobArmsRace.LOGGER.info("[gametest] shell landed {} blocks from its aim point", String.format("%.2f", miss));
            helper.assertTrue(miss < 1.0, "shell landed " + miss + " blocks from target");
        });
    }

    /** A shell aimed next to a snow golem is shot down before it lands. Spread makes this probabilistic, so it gets retries. */
    static void snowGolemInterceptsShell(GameTestHelper helper) {
        spawnAt(helper, EntityType.SNOW_GOLEM, 40.5);
        CreeperShell shell = launch(helper, lane(helper, 2.5, 2.0), lane(helper, 38.5, GROUND));
        helper.succeedWhen(() -> {
            if (shell.isDetonated()) {
                helper.fail("shell reached the ground next to the snow golem");
            }
            helper.assertTrue(shell.isIntercepted(), "shell not intercepted yet");
            MobArmsRace.LOGGER.info("[gametest] shell intercepted at {}", shell.getDetonationPos());
        });
    }

    /** Shells landing outside the protected radius are not worth the ammo. */
    static void snowGolemIgnoresDistantShell(GameTestHelper helper) {
        spawnAt(helper, EntityType.SNOW_GOLEM, 5.5);
        CreeperShell shell = launch(helper, lane(helper, 2.5, 2.0), lane(helper, 44.5, GROUND));
        helper.succeedWhen(() -> {
            if (shell.isIntercepted()) {
                helper.fail("golem wasted fire on a shell landing 39 blocks away");
            }
            helper.assertTrue(shell.isDetonated(), "shell has not landed yet");
        });
    }

    /**
     * A mortar creeper finds a villager 37 blocks away and fires its whole salvo from where it stands.
     * (Once out of ammo it is a normal creeper again and may walk off; that part is not checked.)
     */
    static void mortarCreeperShellsVillager(GameTestHelper helper) {
        MortarCreeper mortar = spawnAt(helper, ModEntities.MORTAR_CREEPER.get(), 3.5);
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(40.5, GROUND, LANE_Z + 0.5));
        Vec3 mortarStart = mortar.position();
        double[] maxDriftWhileArmed = {0};
        helper.onEachTick(() -> {
            if (mortar.getAmmo() > 0) {
                maxDriftWhileArmed[0] = Math.max(maxDriftWhileArmed[0], horizontalDistance(mortar.position(), mortarStart));
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(mortar.getAmmo() == 0, "mortar still has " + mortar.getAmmo() + " shells");
            helper.assertTrue(villager.isDeadOrDying() || villager.getHealth() < villager.getMaxHealth(),
                    "villager was not hit");
            if (maxDriftWhileArmed[0] >= 3.0) {
                helper.fail("mortar walked " + maxDriftWhileArmed[0] + " blocks before its salvo was spent");
            }
        });
    }

    /**
     * Full arms race: a salvo of 4 shells against two snow golems guarding a villager. With the
     * default 18 degree spread about 90% of shells are stopped, so at least 2 of 4 must be.
     */
    static void snowGolemsDefendVillager(GameTestHelper helper) {
        MortarCreeper mortar = spawnAt(helper, ModEntities.MORTAR_CREEPER.get(), 3.5);
        helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(40.5, GROUND, LANE_Z + 0.5));
        helper.spawn(EntityType.SNOW_GOLEM, new Vec3(38.5, GROUND, LANE_Z - 1.5));
        helper.spawn(EntityType.SNOW_GOLEM, new Vec3(38.5, GROUND, LANE_Z + 2.5));

        Set<CreeperShell> seen = new LinkedHashSet<>();
        helper.onEachTick(() -> seen.addAll(helper.getEntities(ModEntities.CREEPER_SHELL.get())));
        helper.succeedWhen(() -> {
            helper.assertTrue(mortar.getAmmo() == 0, "salvo not finished");
            helper.assertTrue(seen.stream().allMatch(CreeperShell::isRemoved), "shells still in flight");
            long intercepted = seen.stream().filter(CreeperShell::isIntercepted).count();
            MobArmsRace.LOGGER.info("[gametest] snow golems intercepted {}/{} shells", intercepted, seen.size());
            if (seen.size() != 4) {
                helper.fail("expected 4 shells, saw " + seen.size());
            }
            if (intercepted < 2) {
                helper.fail("only " + intercepted + "/4 shells intercepted");
            }
        });
    }

    // ---- balance measurement (MOBARMSRACE_MEASURE=1 gradlew runGameTestServer) ----------------

    private static final double[] MEASURED_SPREADS = {6.0, 12.0, 16.0, 18.0, 20.0};
    private static final int SALVOS_PER_SPREAD = 8;
    private static final int SALVO_PERIOD = 90;
    static final int MEASURE_MAX_TICKS = MEASURED_SPREADS.length * SALVOS_PER_SPREAD * SALVO_PERIOD + 400;

    /** Fires mortar-like salvos (4 shells, 4 ticks apart, 1.5 block scatter) at two golems for each spread value. */
    static void measureCiwsSpread(GameTestHelper helper) {
        helper.spawn(EntityType.SNOW_GOLEM, new Vec3(38.5, GROUND, LANE_Z - 1.5));
        helper.spawn(EntityType.SNOW_GOLEM, new Vec3(38.5, GROUND, LANE_Z + 2.5));
        double original = Config.CIWS_SPREAD.get();
        double originalPower = Config.SHELL_EXPLOSION_POWER.get();
        // Leaking shells must not kill the golems mid-measurement.
        Config.SHELL_EXPLOSION_POWER.set(0.0);
        int[] tick = {0};
        List<Set<CreeperShell>> perSpread = new ArrayList<>();
        for (int i = 0; i < MEASURED_SPREADS.length; i++) {
            perSpread.add(new LinkedHashSet<>());
        }
        int total = MEASURED_SPREADS.length * SALVOS_PER_SPREAD * SALVO_PERIOD;
        helper.onEachTick(() -> {
            int t = tick[0]++;
            if (t >= total) {
                return;
            }
            int block = t / (SALVOS_PER_SPREAD * SALVO_PERIOD);
            Config.CIWS_SPREAD.set(MEASURED_SPREADS[block]);
            int inSalvo = t % SALVO_PERIOD;
            if (inSalvo < 16 && inSalvo % 4 == 0) {
                Vec3 aim = lane(helper, 40.5 + helper.getLevel().getRandom().nextGaussian() * 0.75, GROUND)
                        .add(0, 0, helper.getLevel().getRandom().nextGaussian() * 0.75);
                perSpread.get(block).add(launch(helper, lane(helper, 3.5, 2.9), aim));
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(tick[0] >= total + 100, "still measuring");
            Config.CIWS_SPREAD.set(original);
            Config.SHELL_EXPLOSION_POWER.set(originalPower);
            for (int i = 0; i < MEASURED_SPREADS.length; i++) {
                Set<CreeperShell> shells = perSpread.get(i);
                long hit = shells.stream().filter(CreeperShell::isIntercepted).count();
                MobArmsRace.LOGGER.info("[measure] spread {} deg: intercepted {}/{} ({}%)",
                        MEASURED_SPREADS[i], hit, shells.size(), hit * 100 / Math.max(1, shells.size()));
            }
        });
    }
}
