package com.shiroaki193.mod.client;

import com.shiroaki193.mod.ballistics.ImpactPredictor;
import com.shiroaki193.mod.item.CreeperShellItem;
import com.shiroaki193.mod.registry.ModParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

/**
 * Landing crosshair for the hand-held creeper shell: a dotted arc plus a ring where it will hit,
 * and the distance in the action bar (or a warning when the arc is blocked before the aim point).
 * Uses the same {@link ImpactPredictor} the tests check.
 */
public final class AimPreview {
    private static final int UPDATE_INTERVAL = 2;
    private static final int PATH_DOT_EVERY = 3;
    private static final int RING_POINTS = 12;
    private static final double RING_RADIUS = 1.5;
    /** Impact this far from the aim point means the arc hits something on the way. */
    private static final double BLOCKED_TOLERANCE = 2.0;

    private AimPreview() {
    }

    public static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || minecraft.isPaused() || player.tickCount % UPDATE_INTERVAL != 0) {
            return;
        }
        if (!isHoldingShell(player)) {
            return;
        }
        Vec3 origin = CreeperShellItem.launchOrigin(player);
        Vec3 velocity = CreeperShellItem.launchVelocity(player);
        if (velocity == null) {
            player.sendOverlayMessage(Component.translatable("mobarmsrace.aim.no_impact"));
            return;
        }
        ImpactPredictor.Prediction prediction = ImpactPredictor.predict(level, origin, velocity);

        for (int i = PATH_DOT_EVERY; i < prediction.path().size() - 1; i += PATH_DOT_EVERY) {
            Vec3 p = prediction.path().get(i);
            level.addParticle(ModParticles.AIM_MARKER.get(), p.x, p.y, p.z, 0, 0, 0);
        }
        if (!prediction.hitsGround()) {
            player.sendOverlayMessage(Component.translatable("mobarmsrace.aim.no_impact"));
            return;
        }
        Vec3 impact = prediction.impact();
        for (int i = 0; i < RING_POINTS; i++) {
            double a = Math.PI * 2 * i / RING_POINTS;
            level.addParticle(ModParticles.AIM_MARKER.get(),
                    impact.x + Math.cos(a) * RING_RADIUS, impact.y + 0.1, impact.z + Math.sin(a) * RING_RADIUS, 0, 0, 0);
        }
        level.addParticle(ModParticles.AIM_MARKER.get(), impact.x, impact.y + 0.1, impact.z, 0, 0, 0);
        Vec3 aim = CreeperShellItem.aimPoint(player);
        if (aim != null && aim.distanceTo(impact) > BLOCKED_TOLERANCE) {
            player.sendOverlayMessage(Component.translatable("mobarmsrace.aim.blocked"));
            return;
        }
        double distance = Math.sqrt(Math.pow(impact.x - origin.x, 2) + Math.pow(impact.z - origin.z, 2));
        player.sendOverlayMessage(Component.translatable("mobarmsrace.aim.distance",
                String.format("%.0f", distance), String.format("%.1f", prediction.ticks() / 20.0)));
    }

    private static boolean isHoldingShell(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() instanceof CreeperShellItem) {
                return true;
            }
        }
        return false;
    }
}
