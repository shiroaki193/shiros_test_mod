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

    static void removePlayer(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }
}
