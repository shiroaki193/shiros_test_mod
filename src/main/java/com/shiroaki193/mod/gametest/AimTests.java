package com.shiroaki193.mod.gametest;

import static com.shiroaki193.mod.gametest.TestSupport.GROUND;
import static com.shiroaki193.mod.gametest.TestSupport.player;
import static com.shiroaki193.mod.gametest.TestSupport.removePlayer;

import com.shiroaki193.mod.MobArmsRace;
import com.shiroaki193.mod.ballistics.ImpactPredictor;
import com.shiroaki193.mod.entity.CreeperShell;
import com.shiroaki193.mod.item.CreeperShellItem;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** C4: aiming a hand-held shell, and a landing preview that shows exactly where it hits. */
final class AimTests {
    private static final double PLAYER_X = 2.5;
    private static final double TARGET_X = 36.5;
    /** Real vs. predicted impact: same code path, so only float noise is allowed. */
    private static final double PREVIEW_TOLERANCE = 0.3;
    /** Predicted impact vs. the spot the player looks at. */
    private static final double AIM_TOLERANCE = 1.0;

    private AimTests() {
    }

    /** Looking at the ground 34 blocks away lobs the shell onto that spot. */
    static void shellLandsWhereAimed(GameTestHelper helper) {
        ServerPlayer player = aimingPlayer(helper);
        Vec3 aim = CreeperShellItem.aimPoint(player);
        helper.assertTrue(aim != null, "player is not looking at anything");
        ImpactPredictor.Prediction prediction = fireAndCompare(helper, player);
        double miss = prediction.impact().distanceTo(aim);
        helper.assertTrue(miss < AIM_TOLERANCE, "preview impact is " + miss + " blocks from the aim point");
    }

    /**
     * A ceiling over the player's head: the lob hits it long before the aim point, and the preview
     * must show that (the client then warns "arc blocked") instead of the spot on the ground.
     */
    static void previewDetectsObstruction(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int z = 2; z <= 6; z++) {
                helper.setBlock(new BlockPos(x, 6, z), Blocks.STONE);
            }
        }
        ServerPlayer player = aimingPlayer(helper);
        ImpactPredictor.Prediction prediction = fireAndCompare(helper, player);
        double ceilingY = helper.absoluteVec(new Vec3(0, 6, 0)).y;
        helper.assertTrue(Math.abs(prediction.impact().y - ceilingY) < 0.01,
                "preview impact at y=" + prediction.impact().y + ", expected the ceiling underside at y=" + ceilingY);
    }

    /** Stands at x=2.5 and looks down the lane at the floor at x=36.5. */
    private static ServerPlayer aimingPlayer(GameTestHelper helper) {
        ServerPlayer probe = player(helper, PLAYER_X, 0.0F);
        double drop = probe.getEyeY() - helper.absoluteVec(new Vec3(0, GROUND, 0)).y;
        float pitch = (float) Math.toDegrees(Math.atan2(drop, TARGET_X - PLAYER_X));
        probe.snapTo(probe.getX(), probe.getY(), probe.getZ(), TestSupport.FACE_EAST, pitch);
        return probe;
    }

    private static ImpactPredictor.Prediction fireAndCompare(GameTestHelper helper, ServerPlayer player) {
        Vec3 velocity = CreeperShellItem.launchVelocity(player);
        helper.assertTrue(velocity != null, "no firing solution");
        ImpactPredictor.Prediction prediction = ImpactPredictor.predict(helper.getLevel(), CreeperShellItem.launchOrigin(player), velocity);
        helper.assertTrue(prediction.hitsGround(), "preview found no impact");
        CreeperShell shell = CreeperShellItem.fire(helper.getLevel(), player, velocity);
        helper.succeedWhen(() -> {
            helper.assertTrue(shell.isDetonated(), "shell has not landed yet");
            double miss = shell.getDetonationPos().distanceTo(prediction.impact());
            MobArmsRace.LOGGER.info("[gametest] preview vs real impact: {} blocks apart after {} ticks",
                    String.format("%.3f", miss), prediction.ticks());
            helper.assertTrue(miss < PREVIEW_TOLERANCE, "real impact " + miss + " blocks from the preview");
            removePlayer(helper, player);
        });
        return prediction;
    }
}
