package com.shiroaki193.mod.gametest;

import java.util.ArrayList;
import java.util.List;

import com.shiroaki193.mod.MobArmsRace;
import com.shiroaki193.mod.entity.CreeperShell;
import com.shiroaki193.mod.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.phys.Vec3;

/**
 * Close-in defence in a village, modelled on the user's test world (arena_wide, 128 x 33): the
 * mortar fires from a hill 40 blocks up and 90 blocks away; each golem stands on a stair roof
 * 12-14 blocks to the side of the target, with a tree in between. Open-field tests missed the
 * failure modes seen there: golems shooting into their own roof and through foliage.
 */
final class VillageTests {
    static final int SHELLS = 8;

    private VillageTests() {
    }

    static void golemsOnRoofsDefendVillage(GameTestHelper helper) {
        for (int y = 1; y <= 41; y++) {
            helper.setBlock(new BlockPos(8, y, 16), Blocks.STONE);
        }
        house(helper, 95, 2);
        house(helper, 88, 25);
        tree(helper, 97, 10);
        tree(helper, 93, 21);

        helper.spawn(ModEntities.MORTAR_CREEPER.get(), new Vec3(8.5, 42, 16.5));
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(98.5, 1, 16.5));
        villager.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 4));
        List<SnowGolem> golems = List.of(
                helper.spawn(EntityType.SNOW_GOLEM, new Vec3(97.5, 6, 4.5)),
                helper.spawn(EntityType.SNOW_GOLEM, new Vec3(90.5, 6, 27.5)));

        List<CreeperShell> shells = new ArrayList<>();
        helper.onEachTick(() -> {
            for (CreeperShell shell : helper.getLevel().getEntitiesOfClass(CreeperShell.class, helper.getBounds().inflate(0, 200, 0))) {
                if (shells.size() < SHELLS && !shells.contains(shell)) {
                    shells.add(shell);
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(shells.size() == SHELLS && shells.stream().allMatch(CreeperShell::isRemoved), "salvos still in flight");
            long intercepted = shells.stream().filter(CreeperShell::isIntercepted).count();
            MobArmsRace.LOGGER.info("[gametest] village defence: {}/{} intercepted, {}", intercepted, SHELLS,
                    TestSupport.interceptStats(helper, shells, golems));
            if (intercepted < SHELLS * 3 / 4) {
                helper.fail("only " + intercepted + "/" + SHELLS + " shells intercepted over the village");
            }
        });
    }

    /** 5 x 5 plank house, 4 high, with a flat stair roof at y=5 for a golem to stand on. */
    private static void house(GameTestHelper helper, int x0, int z0) {
        for (int x = x0; x < x0 + 5; x++) {
            for (int z = z0; z < z0 + 5; z++) {
                for (int y = 1; y <= 4; y++) {
                    boolean wall = x == x0 || x == x0 + 4 || z == z0 || z == z0 + 4;
                    if (wall) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.OAK_PLANKS);
                    }
                }
                helper.setBlock(new BlockPos(x, 5, z), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
            }
        }
    }

    private static void tree(GameTestHelper helper, int x, int z) {
        for (int y = 1; y <= 5; y++) {
            helper.setBlock(new BlockPos(x, y, z), Blocks.OAK_LOG);
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int y = 5; y <= 7; y++) {
                    if ((dx != 0 || dz != 0 || y > 5) && Math.abs(dx) + Math.abs(dz) <= 3) {
                        helper.setBlock(new BlockPos(x + dx, y, z + dz), Blocks.OAK_LEAVES);
                    }
                }
            }
        }
    }
}
