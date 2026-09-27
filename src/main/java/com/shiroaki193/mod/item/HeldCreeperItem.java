package com.shiroaki193.mod.item;

import org.jspecify.annotations.Nullable;

import com.shiroaki193.mod.carry.CreeperCarry;
import com.shiroaki193.mod.registry.ModItems;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A creeper picked up by hand. Works like a bucket of axolotl: the whole entity (type, fuse,
 * a mortar creeper's ammo...) is stored in the stack, so dying or logging out loses nothing.
 * Right-click throws it; it explodes on the first thing it hits.
 *
 * <p>Vanilla does not allow entities to ride players, which is why this is an item rather than a
 * creeper sitting on the player's head.
 */
public class HeldCreeperItem extends Item {
    private static final String ENTITY_KEY = "HeldEntity";

    public static final double THROW_SPEED = 1.6;
    private static final double THROW_LIFT = 0.25;

    public HeldCreeperItem(Properties properties) {
        super(properties);
    }

    /** Server side. Only with an empty main hand; the creeper leaves the world and goes into the hand. */
    public static boolean pickUp(Player player, Creeper creeper) {
        if (!player.getMainHandItem().isEmpty() || !creeper.isAlive() || creeper.isPassenger() || creeper.isVehicle()) {
            return false;
        }
        creeper.setSwellDir(-1);
        CompoundTag data;
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(creeper.problemPath(), com.shiroaki193.mod.MobArmsRace.LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, creeper.registryAccess());
            if (!creeper.save(output)) {
                return false;
            }
            data = output.buildResult();
        }
        // A fresh UUID is assigned when it is thrown; a copied stack must not duplicate the id.
        data.remove("UUID");

        ItemStack stack = new ItemStack(ModItems.HELD_CREEPER.get());
        CompoundTag custom = new CompoundTag();
        custom.put(ENTITY_KEY, data);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.of(custom));
        if (creeper.hasCustomName()) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, creeper.getCustomName());
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        creeper.discard();
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP,
                SoundSource.PLAYERS, 0.8F, 0.6F);
        return true;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel serverLevel) {
            ItemStack stack = player.getItemInHand(hand);
            if (throwFrom(serverLevel, player, stack) != null) {
                stack.shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Server side. Recreates the stored creeper in front of the player and throws it along the look direction. */
    public static @Nullable Creeper throwFrom(ServerLevel level, Player player, ItemStack stack) {
        Creeper creeper = recreate(level, stack);
        if (creeper == null) {
            return null;
        }
        Vec3 look = player.getLookAngle();
        creeper.snapTo(player.getEyePosition().add(look).add(0, -0.4, 0), player.getYRot(), 0.0F);
        creeper.setDeltaMovement(look.scale(THROW_SPEED).add(0, THROW_LIFT, 0));
        creeper.resetFallDistance();
        CreeperCarry.markThrown(creeper);
        level.addFreshEntity(creeper);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 1.0F, 0.7F);
        return creeper;
    }

    private static @Nullable Creeper recreate(ServerLevel level, ItemStack stack) {
        CustomData custom = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        CompoundTag data = custom == null ? null : custom.copyTag().getCompoundOrEmpty(ENTITY_KEY);
        Entity entity;
        if (data == null || data.isEmpty()) {
            entity = EntityType.CREEPER.create(level, EntitySpawnReason.TRIGGERED);
        } else {
            try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(com.shiroaki193.mod.MobArmsRace.LOGGER)) {
                entity = EntityType.create(TagValueInput.create(reporter, level.registryAccess(), data), level,
                        EntitySpawnReason.TRIGGERED).orElse(null);
            }
        }
        return entity instanceof Creeper creeper ? creeper : null;
    }
}
