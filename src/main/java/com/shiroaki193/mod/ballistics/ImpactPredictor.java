package com.shiroaki193.mod.ballistics;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Traces a creeper shell's flight against the world's blocks, tick by tick, exactly like
 * {@code ThrowableProjectile#tick()} does (move vector clipped against block colliders).
 * Entities are ignored: the crosshair shows where the shell hits the terrain.
 */
public final class ImpactPredictor {
    private static final int MAX_TICKS = 400;

    private ImpactPredictor() {
    }

    /**
     * @param path   position after every tick, ending at the impact point
     * @param impact where the shell hits a block, or {@code null} if it never does (leaves the world)
     */
    public record Prediction(List<Vec3> path, Vec3 impact, int ticks) {
        public boolean hitsGround() {
            return this.impact != null;
        }
    }

    public static Prediction predict(Level level, Vec3 start, Vec3 velocity) {
        List<Vec3> path = new ArrayList<>();
        Ballistics.State state = new Ballistics.State(start.x, start.y, start.z, velocity.x, velocity.y, velocity.z);
        Vec3 from = start;
        for (int tick = 1; tick <= MAX_TICKS; tick++) {
            state = state.step();
            Vec3 to = new Vec3(state.x(), state.y(), state.z());
            BlockHitResult hit = level.clipIncludingBorder(
                    new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() != HitResult.Type.MISS) {
                path.add(hit.getLocation());
                return new Prediction(path, hit.getLocation(), tick);
            }
            path.add(to);
            if (to.y < level.getMinY()) {
                break;
            }
            from = to;
        }
        return new Prediction(path, null, MAX_TICKS);
    }
}
