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

    /** {@code maxAttempts > 1} only where randomness is part of the design (CIWS spread). */
    private record TestSpec(String name, Identifier structure, int maxTicks, int maxAttempts, Consumer<GameTestHelper> body) {
        TestSpec(String name, int maxTicks, int maxAttempts, Consumer<GameTestHelper> body) {
            this(name, ARENA, maxTicks, maxAttempts, body);
        }

        TestSpec(String name, int maxTicks, Consumer<GameTestHelper> body) {
            this(name, ARENA, maxTicks, 1, body);
        }
    }

    private static final List<TestSpec> TESTS = List.of(
            // Mortar vs. snow golem
            new TestSpec("shell_lands_on_target", 200, ArmsRaceTests::shellLandsOnTarget),
            new TestSpec("snow_golem_intercepts_shell", 200, 3, ArmsRaceTests::snowGolemInterceptsShell),
            new TestSpec("snow_golem_ignores_shell_landing_elsewhere", 200, ArmsRaceTests::snowGolemIgnoresDistantShell),
            new TestSpec("mortar_creeper_shells_villager", 400, ArmsRaceTests::mortarCreeperShellsVillager),
            new TestSpec("snow_golems_defend_villager", 400, 2, ArmsRaceTests::snowGolemsDefendVillager),
            new TestSpec("snow_golems_stop_long_range_salvo", ARENA_LONG, 300, 2, ArmsRaceTests::snowGolemsStopLongRangeSalvo),
            new TestSpec("snow_golem_holds_post", 1300, ArmsRaceTests::snowGolemHoldsPost),
            new TestSpec("mortar_creeper_keeps_firing", 500, ArmsRaceTests::mortarCreeperKeepsFiring),
            new TestSpec("mortar_creeper_does_not_despawn", 20, ArmsRaceTests::mortarCreeperDoesNotDespawn),
            new TestSpec("golems_on_roofs_defend_village", ARENA_WIDE, 600, 1, VillageTests::golemsOnRoofsDefendVillage),
            // Spawning and targeting
            new TestSpec("natural_creepers_become_mortars", 1200, RosterTests::naturalCreepersBecomeMortars),
            new TestSpec("mortar_targets_snow_golem", 300, RosterTests::mortarTargetsSnowGolem),
            new TestSpec("snow_golem_guns_down_zombie", 400, RosterTests::snowGolemGunsDownZombie),
            new TestSpec("snow_golem_prefers_shells", 200, 3, RosterTests::snowGolemPrefersShells),
            new TestSpec("elevated_mortar_vs_roof_golems", ARENA_LONG, 600, 1, ArmsRaceTests::elevatedMortarVsRoofGolems),
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

    static {
        for (TestSpec spec : TESTS) {
            FUNCTIONS.register(spec.name(), () -> spec.body());
        }
        FUNCTIONS.register(MEASURE_CIWS, () -> ArmsRaceTests::measureCiws);
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
            register(event, spec.name(), event.registerEnvironment(id("solo/" + spec.name())), spec.structure(), spec.maxTicks(),
                    spec.maxAttempts());
        }
        // Balance measurement: only with MOBARMSRACE_MEASURE=1, in its own environment (= its own
        // batch) so the config values it changes never affect the regular tests.
        if ("1".equals(System.getenv("MOBARMSRACE_MEASURE"))) {
            register(event, MEASURE_CIWS, event.registerEnvironment(id("measure/ciws")), ARENA_LONG,
                    ArmsRaceTests.MEASURE_MAX_TICKS, 1);
        }
    }

    private static void register(RegisterGameTestsEvent event, String name, Holder<TestEnvironmentDefinition<?>> environment,
                                 Identifier structure, int maxTicks, int maxAttempts) {
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
                environment, structure, maxTicks, 0, true, Rotation.NONE, false, maxAttempts, 1, true, 0);
        event.registerTest(id(name), new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id(name)), data));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MobArmsRace.MODID, path);
    }
}
