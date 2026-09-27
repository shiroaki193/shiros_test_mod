package com.shiroaki193.mod.gametest;

import static com.shiroaki193.mod.gametest.TestSupport.player;
import static com.shiroaki193.mod.gametest.TestSupport.removePlayer;
import static com.shiroaki193.mod.gametest.TestSupport.spawnAt;

import com.shiroaki193.mod.MobArmsRace;
import com.shiroaki193.mod.entity.MortarCreeper;
import com.shiroaki193.mod.item.HeldCreeperItem;
import com.shiroaki193.mod.registry.ModEntities;
import com.shiroaki193.mod.registry.ModItems;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** C1: picking creepers up by hand and throwing them. */
final class CarryTests {
    private CarryTests() {
    }

    /** Pick up (creeper goes into the hand), throw along the look direction, it blows up where it lands. */
    static void playerThrowsCreeper(GameTestHelper helper) {
        ServerPlayer player = player(helper, 4.5, -20.0F);
        Creeper original = spawnAt(helper, EntityType.CREEPER, 6.5);

        helper.assertTrue(HeldCreeperItem.pickUp(player, original), "pick-up refused");
        helper.assertTrue(original.isRemoved(), "picked-up creeper is still in the world");
        ItemStack held = player.getMainHandItem();
        helper.assertTrue(held.is(ModItems.HELD_CREEPER.get()), "main hand holds " + held + ", not a held creeper");

        Creeper thrown = HeldCreeperItem.throwFrom(helper.getLevel(), player, held);
        helper.assertTrue(thrown != null, "throw produced no creeper");
        Vec3[] lastPos = {thrown.position()};
        helper.onEachTick(() -> {
            if (!thrown.isRemoved()) {
                lastPos[0] = thrown.position();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(thrown.isRemoved(), "thrown creeper has not detonated yet");
            double forward = lastPos[0].x - player.getX();
            MobArmsRace.LOGGER.info("[gametest] thrown creeper exploded {} blocks in front of the player",
                    String.format("%.1f", forward));
            helper.assertTrue(forward > 4.0, "creeper only flew " + forward + " blocks");
            removePlayer(helper, player);
        });
    }

    /** The stack keeps the whole entity: a mortar creeper comes back as a mortar creeper with its remaining ammo. */
    static void heldCreeperKeepsItsData(GameTestHelper helper) {
        ServerPlayer player = player(helper, 4.5, -45.0F);
        MortarCreeper mortar = spawnAt(helper, ModEntities.MORTAR_CREEPER.get(), 6.5);
        mortar.setAmmo(2);
        helper.assertTrue(HeldCreeperItem.pickUp(player, mortar), "pick-up refused");
        Creeper thrown = HeldCreeperItem.throwFrom(helper.getLevel(), player, player.getMainHandItem());
        helper.assertTrue(thrown instanceof MortarCreeper, "thrown entity is " + thrown + ", not a mortar creeper");
        helper.assertTrue(((MortarCreeper) thrown).getAmmo() == 2, "ammo not preserved");
        helper.assertFalse(thrown.getUUID().equals(mortar.getUUID()), "thrown creeper reused the stored UUID");
        removePlayer(helper, player);
        helper.succeed();
    }

    /** Only an empty main hand can grab a creeper. */
    static void pickUpNeedsEmptyHand(GameTestHelper helper) {
        ServerPlayer player = player(helper, 4.5, 0.0F);
        Creeper creeper = spawnAt(helper, EntityType.CREEPER, 6.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, Items.STONE.getDefaultInstance());
        helper.assertFalse(HeldCreeperItem.pickUp(player, creeper), "picked up a creeper with a full hand");
        helper.assertFalse(creeper.isRemoved(), "creeper vanished anyway");
        removePlayer(helper, player);
        helper.succeed();
    }
}
