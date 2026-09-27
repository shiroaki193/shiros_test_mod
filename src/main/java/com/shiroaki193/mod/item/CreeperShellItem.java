package com.shiroaki193.mod.item;

import org.jspecify.annotations.Nullable;

import com.shiroaki193.mod.ballistics.Ballistics;
import com.shiroaki193.mod.entity.CreeperShell;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A hand-held "firework + elytra creeper" round. Look at a spot on the ground (up to 120 blocks)
 * and right-click: the shell is lobbed on the same 60 degree arc the mortar creeper uses and comes
 * down on that spot. While held, the client previews the arc and impact (see {@code AimPreview}).
 */
public class CreeperShellItem extends Item {
    public static final double MAX_RANGE = Ballistics.MAX_RANGE;
    public static final int COOLDOWN_TICKS = 20;

    public CreeperShellItem(Properties properties) {
        super(properties);
    }

    /** Where a shell fired by this player starts; shared with the landing preview. */
    public static Vec3 launchOrigin(Player player) {
        return new Vec3(player.getX(), player.getEyeY() - 0.1, player.getZ());
    }

    /** The block the player is looking at, if it is within range. */
    public static @Nullable Vec3 aimPoint(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(MAX_RANGE + 8));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) {
            return null;
        }
        return hit.getLocation();
    }

    /** Velocity that lobs a shell onto the aim point, or {@code null} if there is none or it is out of range. */
    public static @Nullable Vec3 launchVelocity(Player player) {
        Vec3 target = aimPoint(player);
        if (target == null) {
            return null;
        }
        Vec3 from = launchOrigin(player);
        double[] v = Ballistics.launchVelocity(from.x, from.y, from.z, target.x, target.y, target.z);
        return v == null ? null : new Vec3(v[0], v[1], v[2]);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        Vec3 velocity = launchVelocity(player);
        if (velocity == null) {
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel serverLevel) {
            ItemStack stack = player.getItemInHand(hand);
            fire(serverLevel, player, velocity);
            player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }

    public static CreeperShell fire(ServerLevel level, Player player, Vec3 velocity) {
        CreeperShell shell = new CreeperShell(level, player);
        shell.setPos(launchOrigin(player));
        shell.setDeltaMovement(velocity);
        level.addFreshEntity(shell);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH,
                SoundSource.PLAYERS, 2.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
        return shell;
    }
}
