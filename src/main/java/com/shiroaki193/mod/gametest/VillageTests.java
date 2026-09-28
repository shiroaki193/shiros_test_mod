package com.shiroaki193.mod.gametest;

import java.util.ArrayList;
import java.util.List;

import com.shiroaki193.mod.MobArmsRace;
import com.shiroaki193.mod.entity.CreeperShell;
import com.shiroaki193.mod.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.phys.Vec3;

/**
 * Close-in defence in a village, modelled on the user's test world (arena_wide, 128 x 33): the
 * mortar fires from a hill 40 blocks up and 90 blocks away; each golem stands on a stair roof
 * 12-14 blocks to the side of the target, with a tree in between. Open-field tests missed the
 * failure modes seen there: golems shooting into their own roof and through foliage.
 */
final class VillageTests {
    static final int SHELLS = 24;

    private VillageTests() {
    }

    static void golemsOnRoofsDefendVillage(GameTestHelper helper) {
        for (int y = 1; y <= 41; y++) {
            helper.setBlock(new BlockPos(8, y, 16), Blocks.STONE);
        }
        house(helper, 95, 2);
        house(helper, 88, 25);
        tree(helper, 97, 10);
        tree(helper, 93, 21);

        helper.spawn(ModEntities.MORTAR_CREEPER.get(), new Vec3(8.5, 42, 16.5));
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(98.5, 1, 16.5));
        villager.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 4));
        List<SnowGolem> golems = List.of(
                TestSupport.shielded(helper.spawn(EntityType.SNOW_GOLEM, new Vec3(97.5, 6, 4.5))),
                TestSupport.shielded(helper.spawn(EntityType.SNOW_GOLEM, new Vec3(90.5, 6, 27.5))));

        // Six salvos: ~62-70% expected (the mortar picks the nearest golem as its target), so at least 8 of 24.
        TestSupport.ShellTally tally = new TestSupport.ShellTally(helper, SHELLS);
        tally.succeedWhenAtLeast(helper, 8, "village defence",
                () -> ", " + TestSupport.interceptStats(helper, tally.shells(), golems));
    }

    // ---- balance measurement in the village (MOBARMSRACE_MEASURE=1) ---------------------------

    /** Cease-fire windows to compare; override with MOBARMSRACE_MEASURE_CEASEFIRE="0,10,20". */
    private static final int[] CEASE_FIRE = java.util.Arrays.stream(
                    System.getenv().getOrDefault("MOBARMSRACE_MEASURE_CEASEFIRE", "23,27,31").split(","))
            .mapToInt(v -> Integer.parseInt(v.trim())).toArray();
    private static final int SALVOS = 12;
    private static final int PERIOD = 160;
    static final int MEASURE_MAX_TICKS = CEASE_FIRE.length * SALVOS * PERIOD + 400;

    /**
     * The same village as {@link #golemsOnRoofsDefendVillage}, but salvos are fired directly (no
     * mortar AI) from the hill, 12 per setting, with fresh golems for each setting. This, not the
     * open-field measurement, is what balance is tuned on: open ground with golems right next to
     * the target flatters the defence.
     */
    static void measureVillage(GameTestHelper helper) {
        for (int y = 1; y <= 41; y++) {
            helper.setBlock(new BlockPos(8, y, 16), Blocks.STONE);
        }
        house(helper, 95, 2);
        house(helper, 88, 25);
        tree(helper, 97, 10);
        tree(helper, 93, 21);
        int originalCeaseFire = com.shiroaki193.mod.Config.CIWS_CEASE_FIRE_TICKS.get();
        double originalPower = com.shiroaki193.mod.Config.SHELL_EXPLOSION_POWER.get();
        com.shiroaki193.mod.Config.SHELL_EXPLOSION_POWER.set(0.0);
        List<List<CreeperShell>> perSetting = new ArrayList<>();
        List<SnowGolem> golems = new ArrayList<>();
        int[] tick = {0};
        int total = CEASE_FIRE.length * SALVOS * PERIOD;
        helper.onEachTick(() -> {
            int t = tick[0]++;
            if (t >= total) {
                return;
            }
            int block = t / (SALVOS * PERIOD);
            int inBlock = t % (SALVOS * PERIOD);
            if (inBlock == 0) {
                golems.forEach(SnowGolem::discard);
                golems.clear();
                golems.add(helper.spawn(EntityType.SNOW_GOLEM, new Vec3(97.5, 6, 4.5)));
                golems.add(helper.spawn(EntityType.SNOW_GOLEM, new Vec3(90.5, 6, 27.5)));
                com.shiroaki193.mod.Config.CIWS_CEASE_FIRE_TICKS.set(CEASE_FIRE[block]);
                perSetting.add(new ArrayList<>());
            }
            int inSalvo = inBlock % PERIOD;
            if (inSalvo < 16 && inSalvo % 4 == 0) {
                var random = helper.getLevel().getRandom();
                Vec3 from = helper.absoluteVec(new Vec3(8.5, 42, 16.5));
                // MOBARMSRACE_MEASURE_AIM=golem: fire at the golem on the far roof instead of the villager.
                Vec3 target = "golem".equals(System.getenv("MOBARMSRACE_MEASURE_AIM")) ? new Vec3(90.5, 6, 27.5) : new Vec3(98.5, 1, 16.5);
                Vec3 to = helper.absoluteVec(target.add(random.nextGaussian() * 0.75, 0, random.nextGaussian() * 0.75));
                perSetting.get(block).add(TestSupport.launch(helper, from, to));
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(tick[0] >= total + 150, "still measuring");
            com.shiroaki193.mod.Config.CIWS_CEASE_FIRE_TICKS.set(originalCeaseFire);
            com.shiroaki193.mod.Config.SHELL_EXPLOSION_POWER.set(originalPower);
            for (int i = 0; i < CEASE_FIRE.length; i++) {
                List<CreeperShell> shells = perSetting.get(i);
                long hit = shells.stream().filter(CreeperShell::isIntercepted).count();
                MobArmsRace.LOGGER.info("[measure-village] cease fire {} | intercepted {}/{} ({}%), {}", CEASE_FIRE[i], hit, shells.size(),
                        hit * 100 / Math.max(1, shells.size()), TestSupport.interceptStats(helper, shells, List.of()));
            }
        });
    }

    // ---- siege: a battery of mortars against unshielded golems ----------------------------------

    /**
     * The failure seen in the user's world: several mortars on a hill against golems that can die.
     * With a cease-fire window and nothing else, every shell that got through was one diving at a
     * golem (it had stopped shooting at it), so one mortar killed two of three golems in 35 s and
     * four wiped out the village. Last-ditch self-defence ({@code selfDefenseRadius}) fixes that.
     * Also covers mortars behind a parapet, which never acquired a target when targeting needed
     * line of sight. Four mortars for 30 s: all fire, and at least two golems must be left.
     */
    static void golemsSurviveSiege(GameTestHelper helper) {
        siege(helper, 4, 600, false, result -> {
            helper.assertTrue(result.shells() >= 32, "mortars behind the parapet fired only " + result.shells() + " shells");
            helper.assertTrue(result.golemsAlive() >= 2, "siege killed " + (3 - result.golemsAlive()) + " of 3 golems: " + result);
        });
    }

    /** Mortars on the hill; override with MOBARMSRACE_MEASURE_MORTARS. */
    private static final int SIEGE_MORTARS = Integer.parseInt(System.getenv().getOrDefault("MOBARMSRACE_MEASURE_MORTARS", "4"));
    private static final int SIEGE_TICKS = 1200;
    static final int SIEGE_MAX_TICKS = SIEGE_TICKS + 200;

    /**
     * 60 s siege with MOBARMSRACE_MEASURE_MORTARS mortars, logging deaths and the intercept rate.
     * Uses the config as loaded, so run it once per config file to compare settings.
     * MOBARMSRACE_MEASURE_SIEGE_FRONT=1 puts a villager at the front of the village, so it is the
     * mortars' nearest target instead of the golem on the near roof.
     */
    static void measureSiege(GameTestHelper helper) {
        boolean front = "1".equals(System.getenv("MOBARMSRACE_MEASURE_SIEGE_FRONT"));
        siege(helper, SIEGE_MORTARS, SIEGE_TICKS, front, result -> MobArmsRace.LOGGER.info("[measure-siege] {} mortars, {} s | {} | {}",
                SIEGE_MORTARS, SIEGE_TICKS / 20, result, result.stats()));
    }

    record SiegeResult(int shells, long intercepted, long golemsAlive, long villagersAlive, String deaths, String stats) {
        @Override
        public String toString() {
            return "shells " + this.shells + " intercepted " + this.intercepted + " (" + this.intercepted * 100 / Math.max(1, this.shells)
                    + "%) | golems alive " + this.golemsAlive + "/3, villagers alive " + this.villagersAlive + "/3 | deaths:" + this.deaths;
        }
    }

    /** Mortars on a fenced platform 40 blocks up (fence = parapet), three golems, three villagers. */
    private static void siege(GameTestHelper helper, int mortars, int ticks, boolean front, java.util.function.Consumer<SiegeResult> verdict) {
        for (int y = 1; y <= 40; y++) {
            helper.setBlock(new BlockPos(8, y, 16), Blocks.STONE);
        }
        for (int x = 4; x <= 12; x++) {
            for (int z = 12; z <= 20; z++) {
                helper.setBlock(new BlockPos(x, 41, z), Blocks.STONE);
                if (x == 4 || x == 12 || z == 12 || z == 20) {
                    helper.setBlock(new BlockPos(x, 42, z), Blocks.OAK_FENCE);
                }
            }
        }
        house(helper, 95, 2);
        house(helper, 88, 25);
        tree(helper, 97, 10);
        tree(helper, 93, 21);
        for (int i = 0; i < mortars; i++) {
            helper.spawn(ModEntities.MORTAR_CREEPER.get(), new Vec3(5.5 + (i % 3) * 3, 42, 13.5 + (i / 3 % 3) * 3));
        }
        List<SnowGolem> golems = List.of(
                helper.spawn(EntityType.SNOW_GOLEM, new Vec3(97.5, 6, 4.5)),
                helper.spawn(EntityType.SNOW_GOLEM, new Vec3(90.5, 6, 27.5)),
                helper.spawn(EntityType.SNOW_GOLEM, new Vec3(104.5, 1, 16.5)));
        List<Villager> villagers = List.of(
                helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(front ? 84.5 : 98.5, 1, 16.5)),
                helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(101.5, 1, 12.5)),
                helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(101.5, 1, 20.5)));
        java.util.Set<CreeperShell> shells = new java.util.LinkedHashSet<>();
        java.util.Map<Object, Long> deaths = new java.util.LinkedHashMap<>();
        helper.onEachTick(() -> {
            shells.addAll(helper.getLevel().getEntitiesOfClass(CreeperShell.class, helper.getBounds().inflate(0, 200, 0)));
            for (var mob : java.util.stream.Stream.concat(golems.stream(), villagers.stream()).toList()) {
                if (!mob.isAlive() && !deaths.containsKey(mob)) {
                    deaths.put(mob, helper.getTick());
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() >= ticks, "sieging");
            StringBuilder log = new StringBuilder();
            deaths.forEach((mob, t) -> log.append(mob instanceof SnowGolem ? " golem@" : " villager@").append(t / 20.0).append("s"));
            verdict.accept(new SiegeResult(shells.size(), shells.stream().filter(CreeperShell::isIntercepted).count(),
                    golems.stream().filter(SnowGolem::isAlive).count(), villagers.stream().filter(Villager::isAlive).count(),
                    log.toString(), TestSupport.interceptStats(helper, List.copyOf(shells), List.of())));
        });
    }

    /** 5 x 5 plank house, 4 high, with a flat stair roof at y=5 for a golem to stand on. */
    private static void house(GameTestHelper helper, int x0, int z0) {
        for (int x = x0; x < x0 + 5; x++) {
            for (int z = z0; z < z0 + 5; z++) {
                for (int y = 1; y <= 4; y++) {
                    boolean wall = x == x0 || x == x0 + 4 || z == z0 || z == z0 + 4;
                    if (wall) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.OAK_PLANKS);
                    }
                }
                helper.setBlock(new BlockPos(x, 5, z), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
            }
        }
    }

    private static void tree(GameTestHelper helper, int x, int z) {
        for (int y = 1; y <= 5; y++) {
            helper.setBlock(new BlockPos(x, y, z), Blocks.OAK_LOG);
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int y = 5; y <= 7; y++) {
                    if ((dx != 0 || dz != 0 || y > 5) && Math.abs(dx) + Math.abs(dz) <= 3) {
                        helper.setBlock(new BlockPos(x + dx, y, z + dz), Blocks.OAK_LEAVES);
                    }
                }
            }
        }
    }
}
