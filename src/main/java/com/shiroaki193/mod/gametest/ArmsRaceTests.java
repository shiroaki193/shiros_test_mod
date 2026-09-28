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

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
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

    /**
     * Keeps a target villager alive through several salvos. (Invulnerable would not do: mobs never
     * pick invulnerable entities as targets, so the mortar would not fire at all.)
     */
    private static void shielded(Villager villager) {
        villager.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 4));
    }

    /** Mortars are fired from far off; they must not despawn like ordinary hostile mobs do there. */
    static void mortarCreeperDoesNotDespawn(GameTestHelper helper) {
        MortarCreeper mortar = spawnAt(helper, ModEntities.MORTAR_CREEPER.get(), 3.5);
        double far = 200;
        helper.assertFalse(mortar.removeWhenFarAway(far * far), "mortar creeper would despawn 200 blocks from players");
        mortar.markNaturalSpawn();
        helper.assertTrue(mortar.removeWhenFarAway(far * far), "naturally spawned mortars must despawn like other monsters");
        helper.succeed();
    }

    /** Snow golems stay near where they were placed, next to what they guard. */
    static void snowGolemHoldsPost(GameTestHelper helper) {
        SnowGolem golem = spawnAt(helper, EntityType.SNOW_GOLEM, 20.5);
        Vec3 post = golem.position();
        double[] maxDrift = {0};
        helper.onEachTick(() -> maxDrift[0] = Math.max(maxDrift[0], horizontalDistance(golem.position(), post)));
        helper.runAtTickTime(1200, () -> {
            MobArmsRace.LOGGER.info("[gametest] snow golem strayed at most {} blocks from its post in 60 s",
                    String.format("%.1f", maxDrift[0]));
            helper.assertTrue(maxDrift[0] <= Config.CIWS_POST_RADIUS.get() + 1.5,
                    "golem wandered " + maxDrift[0] + " blocks from its post");
            helper.succeed();
        });
    }

    /** Sustained fire: after a salvo the mortar reloads in place and fires again. */
    static void mortarCreeperKeepsFiring(GameTestHelper helper) {
        MortarCreeper mortar = spawnAt(helper, ModEntities.MORTAR_CREEPER.get(), 3.5);
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(40.5, GROUND, LANE_Z + 0.5));
        shielded(villager);
        Vec3 start = mortar.position();
        Set<CreeperShell> seen = new LinkedHashSet<>();
        double[] maxDrift = {0};
        helper.onEachTick(() -> {
            seen.addAll(helper.getEntities(ModEntities.CREEPER_SHELL.get()));
            maxDrift[0] = Math.max(maxDrift[0], horizontalDistance(mortar.position(), start));
        });
        int salvo = Config.MORTAR_AMMO.get();
        helper.succeedWhen(() -> {
            helper.assertTrue(seen.size() >= 2 * salvo, "only " + seen.size() + " shells fired so far");
            MobArmsRace.LOGGER.info("[gametest] mortar fired {} shells in {} ticks", seen.size(), helper.getTick());
            if (maxDrift[0] >= 3.0) {
                helper.fail("mortar left its firing position (" + maxDrift[0] + " blocks)");
            }
        });
    }

    /** A 115 block salvo (steep, fast descent) against two golems guarding the target. */
    static void snowGolemsStopLongRangeSalvo(GameTestHelper helper) {
        spawnAt(helper, ModEntities.MORTAR_CREEPER.get(), 3.5);
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(118.5, GROUND, LANE_Z + 0.5));
        shielded(villager);
        helper.spawn(EntityType.SNOW_GOLEM, new Vec3(116.5, GROUND, LANE_Z - 1.5));
        helper.spawn(EntityType.SNOW_GOLEM, new Vec3(116.5, GROUND, LANE_Z + 2.5));
        List<CreeperShell> firstSalvo = new ArrayList<>();
        int salvo = Config.MORTAR_AMMO.get();
        helper.onEachTick(() -> {
            // Shells arc ~70 blocks above the 12 block tall arena, so search well above it.
            for (CreeperShell shell : helper.getLevel().getEntitiesOfClass(CreeperShell.class, helper.getBounds().inflate(0, 200, 0))) {
                if (firstSalvo.size() < salvo && !firstSalvo.contains(shell)) {
                    firstSalvo.add(shell);
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(firstSalvo.size() == salvo && firstSalvo.stream().allMatch(CreeperShell::isRemoved),
                    "first salvo still in flight");
            long intercepted = firstSalvo.stream().filter(CreeperShell::isIntercepted).count();
            MobArmsRace.LOGGER.info("[gametest] 115 block salvo: {}/{} intercepted", intercepted, salvo);
            if (intercepted < salvo / 2) {
                helper.fail("only " + intercepted + "/" + salvo + " long-range shells intercepted");
            }
        });
    }

    /**
     * Reproduces the user's test world: the mortar fires from a hill 40 blocks above the village,
     * 88 blocks away; the golems stand on roofs 5 blocks above the villagers, ~10-12 blocks from them.
     */
    static void elevatedMortarVsRoofGolems(GameTestHelper helper) {
        for (int y = 1; y <= 41; y++) {
            helper.setBlock(new BlockPos(3, y, LANE_Z), Blocks.STONE);
        }
        for (int y = 1; y <= 5; y++) {
            helper.setBlock(new BlockPos(80, y, 1), Blocks.STONE);
            helper.setBlock(new BlockPos(82, y, 8), Blocks.STONE);
        }
        helper.spawn(ModEntities.MORTAR_CREEPER.get(), new Vec3(3.5, 42, LANE_Z + 0.5));
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(91.5, GROUND, LANE_Z + 0.5));
        shielded(villager);
        List<SnowGolem> golems = List.of(
                helper.spawn(EntityType.SNOW_GOLEM, new Vec3(80.5, 6, 1.5)),
                helper.spawn(EntityType.SNOW_GOLEM, new Vec3(82.5, 6, 8.5)));
        Set<CreeperShell> seen = new LinkedHashSet<>();
        helper.onEachTick(() -> seen.addAll(
                helper.getLevel().getEntitiesOfClass(CreeperShell.class, helper.getBounds().inflate(0, 200, 0))));
        helper.succeedWhen(() -> {
            helper.assertTrue(seen.size() >= 8 && seen.stream().limit(8).allMatch(CreeperShell::isRemoved), "two salvos not done");
            long intercepted = seen.stream().limit(8).filter(CreeperShell::isIntercepted).count();
            MobArmsRace.LOGGER.info("[gametest] elevated mortar vs roof golems: {}/8 intercepted, {}", intercepted,
                    TestSupport.interceptStats(helper, seen.stream().limit(8).toList(), golems));
            if (intercepted < 6) {
                helper.fail("only " + intercepted + "/8 shells from an elevated mortar intercepted");
            }
        });
    }

    // ---- balance measurement (MOBARMSRACE_MEASURE=1 gradlew runGameTestServer) ----------------

    /** A CIWS setting to try: terminal engage range and aim spread. */
    private record Setting(double engageRange, double spread) {
    }

    private static final Setting[] MEASURED_SETTINGS = {
            new Setting(28, 18), new Setting(28, 10), new Setting(36, 10), new Setting(28, 6),
    };
    /** Shot distances, measured in the 128 block {@code arena_long}. */
    private static final int[] MEASURED_RANGES = {40, 80, 115};
    private static final int SALVOS_PER_BLOCK = 10;
    private static final int SALVO_PERIOD = 140;
    private static final int BLOCK_TICKS = SALVOS_PER_BLOCK * SALVO_PERIOD;
    private static final int MEASURE_BLOCKS = MEASURED_SETTINGS.length * MEASURED_RANGES.length;
    static final int MEASURE_MAX_TICKS = MEASURE_BLOCKS * BLOCK_TICKS + 400;

    /**
     * Interception rate for each setting against mortar-like salvos (4 shells, 4 ticks apart,
     * ~1.5 block scatter) from 40, 80 and 115 blocks, against two fresh golems next to the aim point.
     * Golems keep to their post (Config postRadius); before that they strolled out of their protect
     * radius within a minute, which is what made interception look broken in play.
     */
    static void measureCiws(GameTestHelper helper) {
        double originalEngage = Config.CIWS_ENGAGE_RANGE.get();
        double originalSpread = Config.CIWS_SPREAD.get();
        double originalPower = Config.SHELL_EXPLOSION_POWER.get();
        // Leaking shells must not kill the golems mid-measurement.
        Config.SHELL_EXPLOSION_POWER.set(0.0);
        int[] tick = {0};
        List<Set<CreeperShell>> perBlock = new ArrayList<>();
        List<SnowGolem> golems = new ArrayList<>();
        for (int i = 0; i < MEASURE_BLOCKS; i++) {
            perBlock.add(new LinkedHashSet<>());
        }
        helper.onEachTick(() -> {
            int t = tick[0]++;
            if (t >= MEASURE_BLOCKS * BLOCK_TICKS) {
                return;
            }
            int block = t / BLOCK_TICKS;
            Setting setting = MEASURED_SETTINGS[block / MEASURED_RANGES.length];
            int range = MEASURED_RANGES[block % MEASURED_RANGES.length];
            int inBlock = t % BLOCK_TICKS;
            if (inBlock == 0) {
                golems.forEach(SnowGolem::discard);
                golems.clear();
                double golemX = 3.5 + range - 2;
                golems.add(helper.spawn(EntityType.SNOW_GOLEM, new Vec3(golemX, GROUND, LANE_Z - 1.5)));
                golems.add(helper.spawn(EntityType.SNOW_GOLEM, new Vec3(golemX, GROUND, LANE_Z + 2.5)));
                Config.CIWS_ENGAGE_RANGE.set(setting.engageRange());
                Config.CIWS_SPREAD.set(setting.spread());
            }
            int inSalvo = inBlock % SALVO_PERIOD;
            if (inSalvo < 16 && inSalvo % 4 == 0) {
                double aimX = 3.5 + range + helper.getLevel().getRandom().nextGaussian() * 0.75;
                Vec3 aim = lane(helper, aimX, GROUND).add(0, 0, helper.getLevel().getRandom().nextGaussian() * 0.75);
                perBlock.get(block).add(launch(helper, lane(helper, 3.5, 2.9), aim));
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(tick[0] >= MEASURE_BLOCKS * BLOCK_TICKS + 150, "still measuring");
            Config.CIWS_ENGAGE_RANGE.set(originalEngage);
            Config.CIWS_SPREAD.set(originalSpread);
            Config.SHELL_EXPLOSION_POWER.set(originalPower);
            for (int i = 0; i < MEASURE_BLOCKS; i++) {
                Setting setting = MEASURED_SETTINGS[i / MEASURED_RANGES.length];
                Set<CreeperShell> shells = perBlock.get(i);
                long hit = shells.stream().filter(CreeperShell::isIntercepted).count();
                MobArmsRace.LOGGER.info("[measure] engage {} spread {} range {}: intercepted {}/{} ({}%)",
                        setting.engageRange(), setting.spread(), MEASURED_RANGES[i % MEASURED_RANGES.length],
                        hit, shells.size(), hit * 100 / Math.max(1, shells.size()));
            }
        });
    }
}
