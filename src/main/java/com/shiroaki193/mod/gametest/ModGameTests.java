package com.shiroaki193.mod.gametest;

import java.util.List;
import java.util.function.Consumer;

import com.shiroaki193.mod.MobArmsRace;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * In-game tests, run with {@code gradlew runGameTestServer} or {@code /test runall} in a dev client.
 * Test bodies live in {@link ArmsRaceTests}, {@link CarryTests}, {@link AimTests} and {@link CatTests}.
 */
public final class ModGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, MobArmsRace.MODID);
    private static final Identifier ARENA = id("arena");
    private static final Identifier ARENA_LONG = id("arena_long");
    private static final Identifier ARENA_WIDE = id("arena_wide");

    /**
     * No maxAttempts here: GameTestServer never retries (retries only apply to the in-game /test
     * command). Tests with random outcomes fire enough shells to judge the rate themselves.
     */
    private record TestSpec(String name, Identifier structure, int maxTicks, Consumer<GameTestHelper> body) {
        TestSpec(String name, int maxTicks, Consumer<GameTestHelper> body) {
            this(name, ARENA, maxTicks, body);
        }
    }

    private static final List<TestSpec> TESTS = List.of(
            // Mortar vs. snow golem
            new TestSpec("shell_lands_on_target", 200, ArmsRaceTests::shellLandsOnTarget),
            new TestSpec("snow_golem_intercepts_shell", 1000, ArmsRaceTests::snowGolemInterceptsShell),
            new TestSpec("snow_golem_ignores_shell_landing_elsewhere", 200, ArmsRaceTests::snowGolemIgnoresDistantShell),
            new TestSpec("mortar_creeper_shells_villager", 400, ArmsRaceTests::mortarCreeperShellsVillager),
            new TestSpec("snow_golems_defend_villager", 600, ArmsRaceTests::snowGolemsDefendVillager),
            new TestSpec("snow_golems_stop_long_range_salvo", ARENA_LONG, 900, ArmsRaceTests::snowGolemsStopLongRangeSalvo),
            new TestSpec("snow_golem_holds_post", 1300, ArmsRaceTests::snowGolemHoldsPost),
            new TestSpec("mortar_creeper_keeps_firing", 500, ArmsRaceTests::mortarCreeperKeepsFiring),
            new TestSpec("mortar_creeper_does_not_despawn", 20, ArmsRaceTests::mortarCreeperDoesNotDespawn),
            new TestSpec("golems_on_roofs_defend_village", ARENA_WIDE, 1600, VillageTests::golemsOnRoofsDefendVillage),
            new TestSpec("golems_survive_siege", ARENA_WIDE, 800, VillageTests::golemsSurviveSiege),
            // Spawning and targeting
            new TestSpec("natural_creepers_become_mortars", 1200, RosterTests::naturalCreepersBecomeMortars),
            new TestSpec("mortar_targets_snow_golem", 300, RosterTests::mortarTargetsSnowGolem),
            new TestSpec("snow_golem_guns_down_zombie", 400, RosterTests::snowGolemGunsDownZombie),
            new TestSpec("snow_golem_prefers_shells", 700, RosterTests::snowGolemPrefersShells),
            // Shell fuzes
            new TestSpec("proximity_fuze_air_bursts_over_villager", 200, FuzeTests::proximityFuzeAirBurstsOverVillager),
            new TestSpec("late_intercept_hurts_golem", 20, FuzeTests::lateInterceptHurtsGolem),
            new TestSpec("high_intercept_is_harmless", 20, FuzeTests::highInterceptIsHarmless),
            new TestSpec("elevated_mortar_vs_roof_golems", ARENA_LONG, 1600, ArmsRaceTests::elevatedMortarVsRoofGolems),
            // C1 carrying and throwing creepers
            new TestSpec("player_throws_creeper", 200, CarryTests::playerThrowsCreeper),
            new TestSpec("held_creeper_keeps_its_data", 20, CarryTests::heldCreeperKeepsItsData),
            new TestSpec("pick_up_needs_empty_hand", 20, CarryTests::pickUpNeedsEmptyHand),
            // C4 landing preview
            new TestSpec("shell_lands_where_aimed", 200, AimTests::shellLandsWhereAimed),
            new TestSpec("aim_preview_detects_obstruction", 100, AimTests::previewDetectsObstruction),
            // K1 iron golem cat throw
            new TestSpec("iron_golem_throws_cat_at_creeper", 400, CatTests::ironGolemThrowsCatAtCreeper),
            new TestSpec("iron_golem_leaves_tamed_cat_alone", 140, CatTests::ironGolemLeavesTamedCatAlone),
            new TestSpec("cat_stops_mortar_firing", 140, CatTests::catStopsMortarFiring));

    private static final String MEASURE_CIWS = "measure_ciws";
    private static final String MEASURE_VILLAGE = "measure_village";
    private static final String MEASURE_SIEGE = "measure_siege";

    static {
        for (TestSpec spec : TESTS) {
            FUNCTIONS.register(spec.name(), () -> spec.body());
        }
        FUNCTIONS.register(MEASURE_CIWS, () -> ArmsRaceTests::measureCiws);
        FUNCTIONS.register(MEASURE_VILLAGE, () -> VillageTests::measureVillage);
        FUNCTIONS.register(MEASURE_SIEGE, () -> VillageTests::measureSiege);
    }

    private ModGameTests() {
    }

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(ModGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        // Tests in one environment run side by side only 5 blocks apart, well inside golem and mortar
        // AI ranges (a golem once threw its cat at the neighbouring test's creeper). One environment
        // per test makes each its own batch, so they run one at a time.
        for (TestSpec spec : TESTS) {
            register(event, spec.name(), event.registerEnvironment(id("solo/" + spec.name())), spec.structure(), spec.maxTicks());
        }
        // Balance measurement: only with MOBARMSRACE_MEASURE=1, in its own environment (= its own
        // batch) so the config values it changes never affect the regular tests.
        // MOBARMSRACE_MEASURE=siege runs only the siege.
        String measure = System.getenv("MOBARMSRACE_MEASURE");
        if ("1".equals(measure)) {
            register(event, MEASURE_CIWS, event.registerEnvironment(id("measure/ciws")), ARENA_LONG,
                    ArmsRaceTests.MEASURE_MAX_TICKS);
            register(event, MEASURE_VILLAGE, event.registerEnvironment(id("measure/village")), ARENA_WIDE,
                    VillageTests.MEASURE_MAX_TICKS);
        }
        if ("1".equals(measure) || "siege".equals(measure)) {
            register(event, MEASURE_SIEGE, event.registerEnvironment(id("measure/siege")), ARENA_WIDE,
                    VillageTests.SIEGE_MAX_TICKS);
        }
    }

    private static void register(RegisterGameTestsEvent event, String name, Holder<TestEnvironmentDefinition<?>> environment,
                                 Identifier structure, int maxTicks) {
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
                environment, structure, maxTicks, 0, true, Rotation.NONE, false, 1, 1, true, 0);
        event.registerTest(id(name), new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id(name)), data));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MobArmsRace.MODID, path);
    }
}
