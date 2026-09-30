package com.shiroaki193.mod.gametest;

import com.shiroaki193.mod.ballistics.Ballistics;
import com.shiroaki193.mod.entity.CreeperShell;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * Shared helpers. All tests use the {@code mobarmsrace:arena} structure: 48 x 40 x 9 blocks with a
 * stone floor at y=0, so standing height is y=1; shots travel along +X down the lane at z=4.5.
 */
final class TestSupport {
    static final double GROUND = 1.0;
    static final int LANE_Z = 4;
    /** Minecraft yaw that faces +X (east). */
    static final float FACE_EAST = -90.0F;

    private TestSupport() {
    }

    static Vec3 lane(GameTestHelper helper, double x, double y) {
        return helper.absoluteVec(new Vec3(x, y, LANE_Z + 0.5));
    }

    /** Launches a shell like a mortar would: fixed pitch, speed solved for the target. */
    static CreeperShell launch(GameTestHelper helper, Vec3 from, Vec3 to) {
        double[] v = Ballistics.launchVelocity(from.x, from.y, from.z, to.x, to.y, to.z);
        if (v == null) {
            throw helper.assertionException("mobarmsrace.test.unreachable");
        }
        CreeperShell shell = new CreeperShell(helper.getLevel(), from.x, from.y, from.z);
        shell.setDeltaMovement(v[0], v[1], v[2]);
        helper.getLevel().addFreshEntity(shell);
        return shell;
    }

    static double horizontalDistance(Vec3 a, Vec3 b) {
        return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z));
    }

    static <T extends Entity> T spawnAt(GameTestHelper helper, EntityType<T> type, double x) {
        return helper.spawn(type, new Vec3(x, GROUND, LANE_Z + 0.5));
    }

    /** A real server player standing in the lane, looking east with the given pitch (negative = up). */
    @SuppressWarnings("removal")
    static ServerPlayer player(GameTestHelper helper, double x, float pitch) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 pos = lane(helper, x, GROUND);
        player.snapTo(pos.x, pos.y, pos.z, FACE_EAST, pitch);
        player.setYHeadRot(FACE_EAST);
        return player;
    }

    /**
     * Where interceptions happened: mean and lowest height above the target's ground, and mean
     * distance from the nearest golem. Low, close intercepts read as near-misses over the village.
     */
    static String interceptStats(GameTestHelper helper, java.util.Collection<CreeperShell> shells,
                                 java.util.List<? extends Entity> golems) {
        double ground = helper.absoluteVec(new Vec3(0, GROUND, 0)).y;
        java.util.List<Vec3> points = shells.stream().filter(CreeperShell::isIntercepted).map(CreeperShell::getDetonationPos).toList();
        if (points.isEmpty()) {
            return "no intercepts";
        }
        double meanHeight = points.stream().mapToDouble(p -> p.y - ground).average().orElse(0);
        double minHeight = points.stream().mapToDouble(p -> p.y - ground).min().orElse(0);
        double meanRange = points.stream()
                .mapToDouble(p -> golems.stream().mapToDouble(g -> g.getEyePosition().distanceTo(p)).min().orElse(0))
                .average().orElse(0);
        double health = golems.stream().filter(g -> g instanceof net.minecraft.world.entity.LivingEntity)
                .mapToDouble(g -> ((net.minecraft.world.entity.LivingEntity) g).getHealth()).sum();
        double maxHealth = golems.stream().filter(g -> g instanceof net.minecraft.world.entity.LivingEntity)
                .mapToDouble(g -> ((net.minecraft.world.entity.LivingEntity) g).getMaxHealth()).sum();
        return String.format("intercept height mean %.1f / min %.1f blocks, %.1f blocks from the golem, golem health %.1f/%.1f",
                meanHeight, minHeight, meanRange, health, maxHealth);
    }

    /**
     * Balance tests with a single mortar fire more than its life allows (shellLimit); this lifts the
     * limit and returns the restore, to run when the test is done. The restore must run before any
     * assertion that can fail, or the next test would find no limit.
     */
    static Runnable noShellLimit() {
        int limit = com.shiroaki193.mod.Config.MORTAR_SHELL_LIMIT.get();
        com.shiroaki193.mod.Config.MORTAR_SHELL_LIMIT.set(0);
        return () -> com.shiroaki193.mod.Config.MORTAR_SHELL_LIMIT.set(limit);
    }

    /** Height above a golem that counts as "high" for {@link #highKills}. */
    static final double HIGH = 60.0;

    /**
     * Of the shells that climbed at least {@link #HIGH} blocks above the golems (standing at
     * relative height {@code golemY}), how many were shot down while still that high.
     */
    static String highKills(GameTestHelper helper, java.util.Collection<CreeperShell> shells, double golemY) {
        double ref = helper.absoluteVec(new Vec3(0, golemY, 0)).y + HIGH;
        long reached = shells.stream().filter(s -> s.getPeakY() >= ref).count();
        long killed = shells.stream().filter(s -> s.getPeakY() >= ref && s.isIntercepted() && s.getDetonationPos().y >= ref).count();
        return String.format("high (60+ above golem): %d/%d shot down there (%d%%)", killed, reached, killed * 100 / Math.max(1, reached));
    }

    /**
     * Keeps a mob alive through salvos (Resistance V). Interception tests use it on golems so one
     * leaking shell does not kill a golem and halve the defence for the rest of the test.
     * Invulnerable would not do: mortars never target invulnerable mobs.
     */
    static <T extends net.minecraft.world.entity.LivingEntity> T shielded(T mob) {
        mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE,
                net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION, 4));
        return mob;
    }

    /**
     * Tallies the first {@code count} shells fired over the test area (in launch order) and passes
     * the test once they are all down if at least {@code min} were intercepted. Interception is
     * random by design (~70%), and GameTestServer never retries a test (maxAttempts only applies
     * to the in-game /test command), so a test must fire enough shells to judge the rate itself.
     */
    static final class ShellTally {
        private final java.util.List<CreeperShell> shells = new java.util.ArrayList<>();
        private final java.util.List<Long> seenAt = new java.util.ArrayList<>();
        private final int count;
        private Runnable whenDone = () -> { };

        /**
         * Tallies shells that this test launches itself, registered with {@link #add}. Searching
         * the area misses a shell that is gone before the next scan (e.g. it hit something on its
         * first tick), leaving the tally one short until the test times out.
         */
        ShellTally(int count) {
            this.count = count;
        }

        /** Tallies the first {@code count} shells found over the test area (fired by mortars). */
        ShellTally(GameTestHelper helper, int count) {
            this.count = count;
            // Shells arc far above the test structure, so search well above it.
            helper.onEachTick(() -> {
                for (CreeperShell shell : helper.getLevel().getEntitiesOfClass(CreeperShell.class, helper.getBounds().inflate(0, 200, 0))) {
                    if (this.shells.size() < count && !this.shells.contains(shell)) {
                        this.shells.add(shell);
                        this.seenAt.add(helper.getTick());
                    }
                }
            });
        }

        java.util.List<CreeperShell> shells() {
            return this.shells;
        }

        /** Runs once all shells are down, before judging (e.g. to restore a config value). */
        ShellTally whenDone(Runnable action) {
            this.whenDone = action;
            return this;
        }

        CreeperShell add(GameTestHelper helper, CreeperShell shell) {
            this.shells.add(shell);
            this.seenAt.add(helper.getTick());
            return shell;
        }

        /** Tick each shell was first seen, and what became of it (to diagnose a failed tally). */
        private String describe(GameTestHelper helper) {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < this.shells.size(); i++) {
                CreeperShell shell = this.shells.get(i);
                String state = shell.isIntercepted() ? "intercepted" : shell.isDetonated() ? "detonated"
                        : shell.isRemoved() ? "removed:" + shell.getRemovalReason()
                        : "flying@" + helper.relativeVec(shell.position()) + " v=" + shell.getDeltaMovement() + " age " + shell.tickCount
                                + (shell.isNoGravity() ? " noGravity" : "") + (helper.getLevel().isPositionEntityTicking(shell.blockPosition()) ? "" : " NOT-TICKING");
                out.append(" [t").append(this.seenAt.get(i)).append(' ').append(state).append(']');
            }
            return out.toString();
        }

        long intercepted() {
            return this.shells.stream().filter(CreeperShell::isIntercepted).count();
        }

        /**
         * Down, or frozen: once (in 1 of 7 runs) a shell hung at the same spot 8 blocks beside the
         * arena for 1000 ticks, most likely knocked there by nearby explosions into a chunk the test
         * server does not tick. Such a shell is out of play and counts as not intercepted.
         */
        private static boolean finished(GameTestHelper helper, CreeperShell shell) {
            return shell.isRemoved() || !helper.getLevel().isPositionEntityTicking(shell.blockPosition());
        }

        void succeedWhenAtLeast(GameTestHelper helper, int min, String what, java.util.function.Supplier<String> details) {
            helper.succeedWhen(() -> {
                helper.assertTrue(this.shells.size() == this.count && this.shells.stream().allMatch(s -> finished(helper, s)),
                        this.shells.size() + "/" + this.count + " shells fired, some still in flight: " + this.describe(helper));
                this.whenDone.run();
                // Mortars left standing would keep shelling whatever test runs next nearby.
                helper.getLevel().getEntitiesOfClass(com.shiroaki193.mod.entity.MortarCreeper.class, helper.getBounds().inflate(0, 64, 0))
                        .forEach(net.minecraft.world.entity.Entity::discard);
                long hit = this.intercepted();
                long frozen = this.shells.stream().filter(s -> !s.isRemoved()).count();
                if (frozen > 0) {
                    com.shiroaki193.mod.MobArmsRace.LOGGER.warn("[gametest] {}: {} shell(s) frozen in a non-ticking chunk:{}", what, frozen, this.describe(helper));
                }
                com.shiroaki193.mod.MobArmsRace.LOGGER.info("[gametest] {}: {}/{} intercepted{}", what, hit, this.count, details.get());
                if (hit < min) {
                    helper.fail(what + ": only " + hit + "/" + this.count + " intercepted (need " + min + ")");
                }
            });
        }
    }

    /**
     * GameTestServer only clears a test's area between batches of one environment, and every test
     * here has its own, so mobs and shells of finished tests stay in the world next door. Mortars
     * target without line of sight (the barrier walls no longer hide the neighbours), so a mortar
     * would pick a leftover villager 17 blocks away over its own (and a leftover mortar battery keeps
     * firing). Tests run one at a time, so everything around outside this test's area is a leftover.
     */
    static void clearLeftovers(GameTestHelper helper) {
        net.minecraft.world.phys.AABB own = helper.getBounds();
        helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, own.inflate(256, 256, 256),
                        e -> !(e instanceof net.minecraft.world.entity.player.Player) && !own.intersects(e.getBoundingBox()))
                .forEach(net.minecraft.world.entity.Entity::discard);
    }

    static void removePlayer(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }
}
