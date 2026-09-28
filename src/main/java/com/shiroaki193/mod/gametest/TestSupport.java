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
        private final int count;

        ShellTally(GameTestHelper helper, int count) {
            this.count = count;
            // Shells arc far above the test structure, so search well above it.
            helper.onEachTick(() -> {
                for (CreeperShell shell : helper.getLevel().getEntitiesOfClass(CreeperShell.class, helper.getBounds().inflate(0, 200, 0))) {
                    if (this.shells.size() < count && !this.shells.contains(shell)) {
                        this.shells.add(shell);
                    }
                }
            });
        }

        java.util.List<CreeperShell> shells() {
            return this.shells;
        }

        long intercepted() {
            return this.shells.stream().filter(CreeperShell::isIntercepted).count();
        }

        void succeedWhenAtLeast(GameTestHelper helper, int min, String what, java.util.function.Supplier<String> details) {
            helper.succeedWhen(() -> {
                helper.assertTrue(this.shells.size() == this.count && this.shells.stream().allMatch(CreeperShell::isRemoved),
                        this.shells.size() + "/" + this.count + " shells fired, some still in flight");
                long hit = this.intercepted();
                com.shiroaki193.mod.MobArmsRace.LOGGER.info("[gametest] {}: {}/{} intercepted{}", what, hit, this.count, details.get());
                if (hit < min) {
                    helper.fail(what + ": only " + hit + "/" + this.count + " intercepted (need " + min + ")");
                }
            });
        }
    }

    static void removePlayer(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }
}
