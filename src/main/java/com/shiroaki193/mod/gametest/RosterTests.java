package com.shiroaki193.mod.gametest;

import static com.shiroaki193.mod.gametest.TestSupport.GROUND;
import static com.shiroaki193.mod.gametest.TestSupport.LANE_Z;
import static com.shiroaki193.mod.gametest.TestSupport.lane;
import static com.shiroaki193.mod.gametest.TestSupport.launch;
import static com.shiroaki193.mod.gametest.TestSupport.removePlayer;
import static com.shiroaki193.mod.gametest.TestSupport.spawnAt;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.shiroaki193.mod.Config;
import com.shiroaki193.mod.MobArmsRace;
import com.shiroaki193.mod.entity.CreeperBomblet;
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

    /**
     * Snow golems in rain (plains), standing in water, and in a desert. First with weatherproof
     * off, to prove the setup really hurts them (vanilla), then on: no damage at all.
     */
    static void snowGolemsAreWeatherproof(GameTestHelper helper) {
        BlockPos from = helper.absolutePos(new BlockPos(32, 0, 0));
        BlockPos to = helper.absolutePos(new BlockPos(47, 8, 8));
        command(helper, "fillbiome " + from.getX() + " " + from.getY() + " " + from.getZ() + " "
                + to.getX() + " " + to.getY() + " " + to.getZ() + " minecraft:desert");
        command(helper, "weather rain");
        for (int y = 1; y <= 2; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0) {
                        helper.setBlock(new BlockPos(22 + dx, y, LANE_Z + dz), Blocks.GLASS);
                    }
                }
            }
        }
        helper.setBlock(new BlockPos(22, 1, LANE_Z), Blocks.WATER);
        List<SnowGolem> golems = List.of(sturdy(spawnAt(helper, EntityType.SNOW_GOLEM, 6.5)),
                sturdy(spawnAt(helper, EntityType.SNOW_GOLEM, 22.5)), sturdy(spawnAt(helper, EntityType.SNOW_GOLEM, 40.5)));
        List<String> where = List.of("in rain", "in water", "in the desert");
        // Really burning still hurts (in the desert, so rain cannot put the fire out).
        SnowGolem burning = sturdy(spawnAt(helper, EntityType.SNOW_GOLEM, 45.5));
        boolean original = Config.CIWS_WEATHERPROOF.get();
        Config.CIWS_WEATHERPROOF.set(false);
        boolean[] hurtByVanilla = new boolean[3];
        helper.runAtTickTime(60, () -> {
            Config.CIWS_WEATHERPROOF.set(true);
            for (int i = 0; i < 3; i++) {
                hurtByVanilla[i] = golems.get(i).getHealth() < golems.get(i).getMaxHealth();
                golems.get(i).setHealth(golems.get(i).getMaxHealth());
            }
            burning.setHealth(burning.getMaxHealth());
            burning.setRemainingFireTicks(200);
        });
        helper.runAtTickTime(160, () -> {
            command(helper, "weather clear");
            Config.CIWS_WEATHERPROOF.set(original);
            for (int i = 0; i < 3; i++) {
                SnowGolem golem = golems.get(i);
                helper.assertTrue(hurtByVanilla[i], "setup: vanilla golem " + where.get(i) + " took no damage");
                helper.assertTrue(golem.getHealth() == golem.getMaxHealth(),
                        "weatherproof golem " + where.get(i) + " lost " + (golem.getMaxHealth() - golem.getHealth()) + " health");
            }
            helper.assertTrue(burning.getHealth() < burning.getMaxHealth(), "a burning weatherproof golem took no fire damage (fire ticks "
                    + burning.getRemainingFireTicks() + ", in water or rain " + burning.isInWaterOrRain() + ", at "
                    + helper.relativeVec(burning.position()) + ", biome " + helper.getLevel().getBiome(burning.blockPosition()).getRegisteredName() + ")");
            MobArmsRace.LOGGER.info("[gametest] snow golems unhurt for 5 s in rain, water and a desert (vanilla: all hurt); a burning one lost {}",
                    burning.getMaxHealth() - burning.getHealth());
            helper.succeed();
        });
    }

    /** Enough health to take the vanilla phase of the weatherproof test without dying. */
    private static SnowGolem sturdy(SnowGolem golem) {
        golem.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(40.0);
        golem.setHealth(golem.getMaxHealth());
        return golem;
    }

    private static void command(GameTestHelper helper, String command) {
        var server = helper.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    /**
     * No shells, only bomblets: ten pairs dropped from 38 blocks up, one landing next to the golem
     * and one 16 blocks away (both inside its protected radius). The near one goes first: in pairs
     * where both are shot down, the near one must usually fall first, and it must never be the
     * one left standing more often.
     */
    static void snowGolemShootsNearestBombletsFirst(GameTestHelper helper) {
        TestSupport.shielded(spawnAt(helper, EntityType.SNOW_GOLEM, 24.5));
        List<CreeperBomblet> near = new java.util.ArrayList<>();
        List<CreeperBomblet> far = new java.util.ArrayList<>();
        java.util.Map<CreeperBomblet, Long> downAt = new java.util.HashMap<>();
        Vec3 down = new Vec3(0, -0.3, 0);
        boolean[] judged = {false};
        helper.onEachTick(() -> {
            if (helper.getTick() % 40 == 0 && near.size() < 10) {
                near.add(drop(helper, lane(helper, 25.5, 38), down));
                far.add(drop(helper, lane(helper, 8.5, 38), down));
            }
            java.util.stream.Stream.concat(near.stream(), far.stream())
                    .filter(b -> b.isIntercepted() && !downAt.containsKey(b)).forEach(b -> downAt.put(b, helper.getTick()));
            boolean done = near.size() == 10 && java.util.stream.Stream.concat(near.stream(), far.stream()).allMatch(CreeperBomblet::isRemoved);
            if (!done || judged[0]) {
                return;
            }
            judged[0] = true;
            int nearFirst = 0;
            int farFirst = 0;
            for (int i = 0; i < 10; i++) {
                Long n = downAt.get(near.get(i));
                Long f = downAt.get(far.get(i));
                if (n != null && (f == null || n < f)) {
                    nearFirst++;
                } else if (f != null && (n == null || f < n)) {
                    farFirst++;
                }
            }
            MobArmsRace.LOGGER.info("[gametest] bomblet pairs: near one shot down first in {}, far one first in {} (shot down: {}/10 near, {}/10 far)",
                    nearFirst, farFirst, near.stream().filter(downAt::containsKey).count(), far.stream().filter(downAt::containsKey).count());
            if (nearFirst < 6 || nearFirst <= farFirst * 2) {
                helper.fail("near bomblet first in only " + nearFirst + " of 10 pairs (far first in " + farFirst + ")");
            }
            helper.succeed();
        });
    }

    /**
     * While a shell threatens the golem, bomblets are left alone. Shells that the golem can shoot
     * die within a few ticks and leave gaps, so the shell here hovers right above the golem, behind
     * a small roof: always a threat (coming down on the golem itself), never a clear shot, so the
     * golem fires nothing at all. Four bomblets land 20 blocks away, in plain view: none may be shot
     * down. Then the shell goes, and four more bomblets prove the golem can reach them.
     */
    static void snowGolemIgnoresBombletsWhileShellsIncoming(GameTestHelper helper) {
        int x = 24;
        int z = LANE_Z;
        // Fences keep the golem in place (too high to jump) but, 1.5 high, stay under its line of fire.
        helper.setBlock(new BlockPos(x - 1, 1, z), Blocks.OAK_FENCE);
        helper.setBlock(new BlockPos(x + 1, 1, z), Blocks.OAK_FENCE);
        helper.setBlock(new BlockPos(x, 1, z - 1), Blocks.OAK_FENCE);
        helper.setBlock(new BlockPos(x, 1, z + 1), Blocks.OAK_FENCE);
        for (int dz = -1; dz <= 1; dz++) {
            helper.setBlock(new BlockPos(x - 1, 4, z + dz), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 4, z + dz), Blocks.STONE);
        }
        TestSupport.shielded(spawnAt(helper, EntityType.SNOW_GOLEM, x + 0.5));
        Vec3 above = lane(helper, x + 0.5, 30);
        CreeperShell hovering = new CreeperShell(helper.getLevel(), above.x, above.y, above.z);
        hovering.setNoGravity(true);
        helper.getLevel().addFreshEntity(hovering);
        List<CreeperBomblet> withShell = new java.util.ArrayList<>();
        List<CreeperBomblet> without = new java.util.ArrayList<>();
        Vec3 down = new Vec3(0, -0.3, 0);
        for (int i = 0; i < 4; i++) {
            helper.runAtTickTime(20 + i * 30, () -> withShell.add(drop(helper, lane(helper, 44.5, 20), down)));
            helper.runAtTickTime(160 + i * 30, () -> without.add(drop(helper, lane(helper, 44.5, 20), down)));
        }
        helper.runAtTickTime(140, hovering::discard);
        helper.runAtTickTime(300, () -> {
            long hitWithShell = withShell.stream().filter(CreeperBomblet::isIntercepted).count();
            long hitWithout = without.stream().filter(CreeperBomblet::isIntercepted).count();
            MobArmsRace.LOGGER.info("[gametest] bomblets shot down: {}/4 while a shell threatened the golem, {}/4 after", hitWithShell, hitWithout);
            helper.assertTrue(hitWithShell == 0, hitWithShell + "/4 bomblets shot down while a shell threatened the golem");
            helper.assertTrue(hitWithout > 0, "no bomblet shot down even without a shell: the setup blocks the shots");
            helper.succeed();
        });
    }

    private static CreeperBomblet drop(GameTestHelper helper, Vec3 at, Vec3 velocity) {
        CreeperBomblet bomblet = new CreeperBomblet(helper.getLevel(), at, velocity);
        helper.getLevel().addFreshEntity(bomblet);
        return bomblet;
    }

    /** A (shielded, so it stays) zombie in view does not distract the golem from incoming shells. */
    static void snowGolemPrefersShells(GameTestHelper helper) {
        TestSupport.shielded(spawnAt(helper, EntityType.SNOW_GOLEM, 40.5));
        TestSupport.shielded(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(30.5, GROUND, LANE_Z + 0.5)));
        TestSupport.ShellTally tally = new TestSupport.ShellTally(6);
        int[] fired = {0};
        helper.onEachTick(() -> {
            if (helper.getTick() % 80 == 0 && fired[0] < 6) {
                fired[0]++;
                tally.add(helper, launch(helper, lane(helper, 2.5, 2.0), lane(helper, 38.5, GROUND)));
            }
        });
        tally.succeedWhenAtLeast(helper, 3, "shells with a zombie in view", () -> "");
    }
}
