package com.shiroaki193.mod.gametest;

import static com.shiroaki193.mod.gametest.TestSupport.GROUND;
import static com.shiroaki193.mod.gametest.TestSupport.LANE_Z;
import static com.shiroaki193.mod.gametest.TestSupport.lane;
import static com.shiroaki193.mod.gametest.TestSupport.launch;
import static com.shiroaki193.mod.gametest.TestSupport.removePlayer;
import static com.shiroaki193.mod.gametest.TestSupport.spawnAt;

import java.util.HashSet;
import java.util.Set;

import com.shiroaki193.mod.Config;
import com.shiroaki193.mod.MobArmsRace;
import com.shiroaki193.mod.entity.CreeperShell;
import com.shiroaki193.mod.entity.MortarCreeper;
import com.shiroaki193.mod.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Who spawns and who shoots at whom: natural mortar spawns, mortar targets, golem anti-mob fire. */
final class RosterTests {
    private static final int CREEPERS_TO_SAMPLE = 120;

    private RosterTests() {
    }

    /**
     * Drives vanilla's own natural spawner (not a shortcut through our event handler) in a dark,
     * closed room with a player 30 blocks away, and checks the share of creepers that came out as
     * mortars. 120 samples at p = 0.4 give a standard deviation of ~4.5%.
     */
    static void naturalCreepersBecomeMortars(GameTestHelper helper) {
        for (int x = 20; x <= 44; x++) {
            for (int z = 1; z <= 7; z++) {
                for (int y = 1; y <= 4; y++) {
                    boolean shell = x == 20 || x == 44 || z == 1 || z == 7 || y == 4;
                    if (shell) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                    }
                }
            }
        }
        ServerPlayer player = TestSupport.player(helper, 2.5, 0.0F);
        BlockPos room = helper.absolutePos(new BlockPos(32, 1, LANE_Z));
        Set<Integer> creepers = new HashSet<>();
        Set<Integer> mortars = new HashSet<>();
        int[] unmarked = {0};
        helper.onEachTick(() -> {
            if (helper.getTick() < 20) {
                return; // let the lighting in the closed room settle first
            }
            for (int i = 0; i < 20; i++) {
                NaturalSpawner.spawnCategoryForPosition(MobCategory.MONSTER, helper.getLevel(), room);
            }
            for (Entity e : helper.getLevel().getEntitiesOfClass(Entity.class, helper.getBounds(), e -> !(e instanceof ServerPlayer))) {
                if (e instanceof MortarCreeper mortar) {
                    if (mortars.add(mortar.getId()) && !mortar.isNaturalSpawn()) {
                        unmarked[0]++;
                    }
                } else if (e instanceof Creeper) {
                    creepers.add(e.getId());
                }
                e.discard(); // keep the room empty so spawning never runs out of space
            }
        });
        helper.succeedWhen(() -> {
            int total = creepers.size() + mortars.size();
            helper.assertTrue(total >= CREEPERS_TO_SAMPLE, "only " + total + " creepers spawned so far");
            double share = mortars.size() / (double) total;
            MobArmsRace.LOGGER.info("[gametest] natural spawning: {} of {} creepers were mortars ({}%)",
                    mortars.size(), total, Math.round(share * 100));
            if (unmarked[0] > 0) {
                helper.fail(unmarked[0] + " naturally spawned mortars are not marked natural (they would never despawn)");
            }
            double expected = Config.MORTAR_NATURAL_SHARE.get();
            if (Math.abs(share - expected) > 0.15) {
                helper.fail("mortar share " + share + " is far from the configured " + expected);
            }
            removePlayer(helper, player);
        });
    }

    /** With no villager around, a lone snow golem (the air defence) is a target in its own right. */
    static void mortarTargetsSnowGolem(GameTestHelper helper) {
        MortarCreeper mortar = spawnAt(helper, ModEntities.MORTAR_CREEPER.get(), 3.5);
        SnowGolem golem = spawnAt(helper, EntityType.SNOW_GOLEM, 40.5);
        int full = mortar.getAmmo();
        helper.succeedWhen(() -> {
            helper.assertTrue(mortar.getTarget() == golem, "mortar is targeting " + mortar.getTarget());
            helper.assertTrue(mortar.getAmmo() < full || mortar.isReloading(), "mortar has not fired at the golem yet");
        });
    }

    /** The CIWS turns on a zombie 16 blocks away, and snowballs pass harmlessly through a villager beside it. */
    static void snowGolemGunsDownZombie(GameTestHelper helper) {
        helper.setTime(18000); // midnight: the zombie must not burn in the sun
        spawnAt(helper, EntityType.SNOW_GOLEM, 10.5);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(26.5, GROUND, LANE_Z + 0.5));
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(26.5, GROUND, LANE_Z + 1.8));
        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.isDeadOrDying(), "zombie still alive with " + zombie.getHealth() + " health");
            MobArmsRace.LOGGER.info("[gametest] snow golem killed a zombie 16 blocks away in {} ticks", helper.getTick());
            if (villager.getHealth() < villager.getMaxHealth()) {
                helper.fail("the villager next to the zombie got hit");
            }
        });
    }

    /** A (shielded, so it stays) zombie in view does not distract the golem from incoming shells. */
    static void snowGolemPrefersShells(GameTestHelper helper) {
        TestSupport.shielded(spawnAt(helper, EntityType.SNOW_GOLEM, 40.5));
        TestSupport.shielded(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(30.5, GROUND, LANE_Z + 0.5)));
        TestSupport.ShellTally tally = new TestSupport.ShellTally(helper, 6);
        int[] fired = {0};
        helper.onEachTick(() -> {
            if (helper.getTick() % 80 == 0 && fired[0] < 6) {
                fired[0]++;
                launch(helper, lane(helper, 2.5, 2.0), lane(helper, 38.5, GROUND));
            }
        });
        tally.succeedWhenAtLeast(helper, 3, "shells with a zombie in view", () -> "");
    }
}
