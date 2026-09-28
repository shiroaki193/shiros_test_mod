package com.shiroaki193.mod.ballistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class BallisticsTest {
    private static final double PITCH = Math.toRadians(Ballistics.DEFAULT_PITCH_DEG);

    @Test
    void stepMatchesVanillaThrowableOrder() {
        // ThrowableProjectile#tick: add gravity, scale by inertia, then move.
        Ballistics.State s = new Ballistics.State(0, 10, 0, 1, 2, 0).step(0.05, 0.99);
        assertEquals(0.99, s.vx(), 1e-12);
        assertEquals((2 - 0.05) * 0.99, s.vy(), 1e-12);
        assertEquals(0.99, s.x(), 1e-12);
        assertEquals(10 + (2 - 0.05) * 0.99, s.y(), 1e-12);
    }

    @ParameterizedTest(name = "distance {0}, height difference {1}")
    @CsvSource({
            "8, 0", "24, 0", "40, 0", "80, 0", "120, 0",
            "40, -1", "40, 12", "40, -20", "100, 15", "120, -30",
    })
    void solvedLaunchLandsOnTarget(double distance, double dy) {
        double[] v = Ballistics.launchVelocity(0, 0, 0, distance * 0.6, dy, distance * 0.8);
        assertNotNull(v, "target should be reachable");
        Ballistics.Landing landing = Ballistics.landing(new Ballistics.State(0, 0, 0, v[0], v[1], v[2]), dy);
        assertNotNull(landing);
        double miss = Math.hypot(landing.x() - distance * 0.6, landing.z() - distance * 0.8);
        assertTrue(miss < 0.05, "missed by " + miss);
    }

    @Test
    void mobModelMatchesVanillaTravelInAir() {
        // LivingEntity#travelInAir with no input: move, then gravity 0.08, then x0.98 vertical / x0.91 horizontal.
        Ballistics.State s = new Ballistics.State(0, 10, 0, 1, 0.5, 0).step(Ballistics.Model.MOB);
        assertEquals(1.0, s.x(), 1e-12);
        assertEquals(10.5, s.y(), 1e-12);
        assertEquals(0.91, s.vx(), 1e-12);
        assertEquals((0.5 - 0.08) * 0.98, s.vy(), 1e-12);
    }

    @ParameterizedTest(name = "cat thrown {0} blocks, height difference {1}")
    @CsvSource({"6, 0", "12, -2.6", "20, -2.6", "24, 0"})
    void catThrowLandsOnTarget(double distance, double dy) {
        double pitch = Math.toRadians(40);
        double[] v = Ballistics.launchVelocity(0, 0, 0, distance, dy, 0, pitch, 40, Ballistics.Model.MOB, 4.0);
        assertNotNull(v, "cat throw should reach " + distance + " blocks");
        Ballistics.Landing landing = Ballistics.landing(new Ballistics.State(0, 0, 0, v[0], v[1], v[2]), dy, Ballistics.Model.MOB);
        assertNotNull(landing);
        assertEquals(distance, landing.x(), 0.05);
    }

    @Test
    void segmentDistanceCases() {
        double[] o = {0, 0, 0};
        // Crossing segments (a horizontal bullet path through a vertical shell path) touch.
        assertEquals(0.0, Ballistics.segmentDistance(new double[] {-2, 5, 0}, new double[] {2, 5, 0},
                new double[] {0, 7, 0}, new double[] {0, 3, 0}), 1e-12);
        // Parallel segments one block apart.
        assertEquals(1.0, Ballistics.segmentDistance(o, new double[] {4, 0, 0},
                new double[] {0, 1, 0}, new double[] {4, 1, 0}), 1e-12);
        // Closest points are endpoints.
        assertEquals(Math.sqrt(2), Ballistics.segmentDistance(o, new double[] {1, 0, 0},
                new double[] {2, 1, 0}, new double[] {3, 1, 0}), 1e-12);
        // Degenerate segments (points).
        assertEquals(5.0, Ballistics.segmentDistance(o, o, new double[] {3, 4, 0}, new double[] {3, 4, 0}), 1e-12);
    }

    /**
     * The in-game bug: a shell falling 2.2 blocks/tick moves before the bullet in each tick, so
     * comparing "shell now" with "bullet now" is off by a tick. Path segments are order-agnostic.
     */
    @Test
    void fastFallingShellIsHitWhateverTheTickOrder() {
        double[] meet = {10, 20, 0};
        double[] shellBefore = {10, 22.2, 0};        // shell path this tick ends at the meeting point
        double[] bulletBefore = {6, 20, 0};           // bullet path this tick ends there too
        double[] shellAfter = {10, 17.8, 0};          // shell already moved on (it ticks first)
        assertEquals(0.0, Math.min(
                Ballistics.segmentDistance(bulletBefore, meet, shellBefore, meet),
                Ballistics.segmentDistance(bulletBefore, meet, meet, shellAfter)), 1e-12);
    }

    @Test
    void maxRangeShotMatchesDesign() {
        double speed = Ballistics.solveSpeed(120, 0, PITCH);
        Ballistics.Landing landing = Ballistics.landing(
                new Ballistics.State(0, 0, 0, speed * Math.cos(PITCH), speed * Math.sin(PITCH), 0), 0);
        assertNotNull(landing);
        // Design doc: ~5.4 s flight, ~70 block apex, steep (~75 degree) descent.
        assertEquals(108, landing.ticks(), 3);
        assertEquals(70, landing.apexY(), 4);
        assertEquals(75, landing.impactAngleDeg(), 2);
    }

    @Test
    void descentIsSteeperThanAscent() {
        double speed = Ballistics.solveSpeed(80, 0, PITCH);
        Ballistics.Landing landing = Ballistics.landing(
                new Ballistics.State(0, 0, 0, speed * Math.cos(PITCH), speed * Math.sin(PITCH), 0), 0);
        assertNotNull(landing);
        assertTrue(landing.impactAngleDeg() > Ballistics.DEFAULT_PITCH_DEG + 5,
                "drag should make the dive steeper than the launch, got " + landing.impactAngleDeg());
    }

    @Test
    void beyondMaxRangeIsRejected() {
        assertNull(Ballistics.launchVelocity(0, 0, 0, 121, 0, 0));
        assertNotNull(Ballistics.launchVelocity(0, 0, 0, 119, 0, 0));
    }

    @Test
    void targetAboveApexIsUnreachable() {
        assertTrue(Double.isNaN(Ballistics.solveSpeed(10, 500, PITCH)));
    }

    @Test
    void straightFlightDistanceMatchesTickByTick() {
        double speed = 4.0;
        double v = speed;
        double travelled = 0;
        for (int tick = 1; tick <= 20; tick++) {
            v *= 0.99;
            travelled += v;
            assertEquals(travelled, Ballistics.straightFlightDistance(speed, tick, 0.99), 1e-9);
        }
    }

    @Test
    void leadPointIsWhereBothArriveOnTheSameTick() {
        double speed = Ballistics.solveSpeed(40, 0, PITCH);
        Ballistics.State shell = new Ballistics.State(0, 0, 0, speed * Math.cos(PITCH), speed * Math.sin(PITCH), 0).advance(40);
        double mx = 40;
        double my = 1.7;
        double mz = 2;
        Ballistics.Intercept lead = Ballistics.lead(shell, mx, my, mz, 4.0, 0.99, 30);
        assertNotNull(lead);
        Ballistics.State atMeeting = shell.advance(lead.ticks());
        assertEquals(atMeeting.x(), lead.x(), 1e-9);
        double bulletNeeded = Math.sqrt(Math.pow(lead.x() - mx, 2) + Math.pow(lead.y() - my, 2) + Math.pow(lead.z() - mz, 2));
        assertTrue(Ballistics.straightFlightDistance(4.0, lead.ticks(), 0.99) >= bulletNeeded);
        Ballistics.State before = shell.advance(lead.ticks() - 1);
        double neededBefore = Math.sqrt(Math.pow(before.x() - mx, 2) + Math.pow(before.y() - my, 2) + Math.pow(before.z() - mz, 2));
        assertTrue(Ballistics.straightFlightDistance(4.0, lead.ticks() - 1, 0.99) < neededBefore,
                "lead should be the earliest reachable tick");
    }
}
