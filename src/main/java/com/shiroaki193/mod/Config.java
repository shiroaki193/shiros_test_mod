package com.shiroaki193.mod;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.push("mortarCreeper");
    }

    public static final ModConfigSpec.IntValue MORTAR_AMMO = BUILDER
            .comment("Shells per salvo (the mortar carries one salvo and reloads it, see reloadTicks).")
            .defineInRange("ammo", 4, 1, 64);

    public static final ModConfigSpec.DoubleValue MORTAR_NATURAL_SHARE = BUILDER
            .comment("Share of naturally spawning creepers that spawn as mortar creepers instead (0 = none, 1 = all).",
                    "Naturally spawned mortars despawn like other monsters; ones from spawn eggs or commands never despawn.")
            .defineInRange("naturalShare", 0.4, 0.0, 1.0);

    public static final ModConfigSpec.IntValue MORTAR_RELOAD_TICKS = BUILDER
            .comment("Ticks to reload a full salvo after the last shell. 0 = no reload: the mortar turns back into a normal creeper.")
            .defineInRange("reloadTicks", 160, 0, 12000);

    public static final ModConfigSpec.IntValue MORTAR_SHELL_LIMIT = BUILDER
            .comment("A mortar creeper dies after firing this many shells in its life (the count is saved with it). 0 = no limit.")
            .defineInRange("shellLimit", 10, 0, 10000);

    public static final ModConfigSpec.DoubleValue MORTAR_MAX_RANGE = BUILDER
            .comment("Maximum horizontal firing range in blocks.")
            .defineInRange("maxRange", 120.0, 16.0, 120.0);

    public static final ModConfigSpec.DoubleValue MORTAR_MIN_RANGE = BUILDER
            .comment("Targets closer than this are handled like a normal creeper would.")
            .defineInRange("minRange", 10.0, 0.0, 64.0);

    public static final ModConfigSpec.IntValue MORTAR_SALVO_INTERVAL = BUILDER
            .comment("Ticks between shells in one salvo.")
            .defineInRange("salvoInterval", 4, 1, 100);

    public static final ModConfigSpec.DoubleValue MORTAR_SCATTER = BUILDER
            .comment("Random offset (blocks) added to each shell's aim point.")
            .defineInRange("scatter", 1.5, 0.0, 16.0);

    public static final ModConfigSpec.DoubleValue SHELL_EXPLOSION_POWER = BUILDER
            .comment("Explosion power of a shell on impact (a creeper is 3). Block damage follows the mobGriefing rule.")
            .defineInRange("shellExplosionPower", 2.0, 0.0, 8.0);

    public static final ModConfigSpec.DoubleValue FRAGMENT_CHANCE = BUILDER
            .comment("Chance that a shell shot down by a snow golem breaks into bomblets instead of exploding where it was hit.")
            .defineInRange("fragmentChance", 0.5, 0.0, 1.0);

    public static final ModConfigSpec.IntValue FRAGMENTS_MIN = BUILDER
            .comment("Fewest bomblets a shell breaks into.")
            .defineInRange("fragmentsMin", 2, 1, 16);

    public static final ModConfigSpec.IntValue FRAGMENTS_MAX = BUILDER
            .comment("Most bomblets a shell breaks into.")
            .defineInRange("fragmentsMax", 5, 1, 16);

    public static final ModConfigSpec.DoubleValue FRAGMENT_POWER = BUILDER
            .comment("Explosion power of each bomblet (a shell is shellExplosionPower). Damage reaches 2 x power blocks from",
                    "a mob's feet: 1.0 = 2 blocks and up to ~15 damage, against 4 blocks and ~29 for a shell.")
            .defineInRange("fragmentPower", 1.0, 0.0, 8.0);

    public static final ModConfigSpec.DoubleValue SHELL_PROXIMITY_FUZE = BUILDER
            .comment("Proximity fuze: a descending shell air-bursts once a villager, golem or player is this close. 0 = impact fuze only.",
                    "Shells shot down by snow golems also detonate, so a late intercept still hurts whatever is nearby.")
            .defineInRange("proximityFuze", 3.0, 0.0, 8.0);

    static {
        BUILDER.pop().push("snowGolemCiws");
    }

    public static final ModConfigSpec.BooleanValue CIWS_ENABLED = BUILDER
            .comment("Give every snow golem the close-in anti-shell defence.")
            .define("enabled", true);

    public static final ModConfigSpec.DoubleValue CIWS_ANTI_MOB_RANGE = BUILDER
            .comment("When no shell is incoming, golems turn the same rapid fire on hostile mobs (zombies, skeletons...) this close. 0 = off.")
            .defineInRange("antiMobRange", 24.0, 0.0, 64.0);

    public static final ModConfigSpec.DoubleValue CIWS_ANTI_MOB_DAMAGE = BUILDER
            .comment("Damage per interceptor snowball against hostile mobs (vanilla snowballs deal 0; a zombie has 20 health).")
            .defineInRange("antiMobDamage", 2.0, 0.0, 20.0);

    public static final ModConfigSpec.DoubleValue CIWS_GOLEM_HEALTH = BUILDER
            .comment("Max health of snow golems (vanilla 4). A shell bursting next to a golem deals ~5, so with vanilla health",
                    "every shell that gets through kills a golem and the air defence collapses within a salvo or two.")
            .defineInRange("golemHealth", 4.0, 1.0, 100.0);

    public static final ModConfigSpec.BooleanValue CIWS_WEATHERPROOF = BUILDER
            .comment("Snow golems take no damage from rain, water or melting in hot biomes (deserts, savannas, the Nether).",
                    "Real fire, lava, attacks and explosions still hurt them.")
            .define("weatherproof", true);

    public static final ModConfigSpec.IntValue CIWS_POST_RADIUS = BUILDER
            .comment("Snow golems keep within this many blocks of where they were placed, so they stay next to what they guard",
                    "(they only defend shells landing within protectRadius of themselves). 0 = wander freely like vanilla.")
            .defineInRange("postRadius", 6, 0, 64);

    public static final ModConfigSpec.IntValue CIWS_ENGAGE_TICKS = BUILDER
            .comment("Golems open fire on a shell once it is predicted to land within this many ticks (80 = 4 s).")
            .defineInRange("engageTicks", 80, 5, 200);

    public static final ModConfigSpec.IntValue CIWS_CEASE_FIRE_TICKS = BUILDER
            .comment("Golems stop firing at a shell that will land within this many ticks: too close and fast to track.",
                    "Snowballs already in the air can still hit. Keeps intercepts high and lets some shells through.")
            .defineInRange("ceaseFireTicks", 50, 0, 100);

    public static final ModConfigSpec.DoubleValue CIWS_SELF_DEFENSE_RADIUS = BUILDER
            .comment("Last-ditch self-defence: shells predicted to land this close to the golem itself ignore the cease-fire",
                    "window (4 = the blast radius of a power 2 shell). Without it, every shell that gets through is one diving",
                    "at a golem, and the air defence collapses one golem at a time.")
            .defineInRange("selfDefenseRadius", 4.0, 0.0, 64.0);

    public static final ModConfigSpec.DoubleValue CIWS_GUN_RANGE = BUILDER
            .comment("Farthest a golem shoots at a shell (snowballs need ~0.25 s per 16 blocks, and miss more with distance).")
            .defineInRange("gunRange", 96.0, 8.0, 128.0);

    public static final ModConfigSpec.DoubleValue CIWS_PROTECT_RADIUS = BUILDER
            .comment("Only shells predicted to land within this radius of the golem are engaged.")
            .defineInRange("protectRadius", 24.0, 2.0, 64.0);

    public static final ModConfigSpec.IntValue CIWS_FIRE_INTERVAL = BUILDER
            .comment("Ticks between interceptor snowballs.")
            .defineInRange("fireInterval", 2, 1, 40);

    public static final ModConfigSpec.DoubleValue CIWS_BULLET_SPEED = BUILDER
            .comment("Interceptor snowball speed in blocks/tick (flies straight, no gravity).")
            .defineInRange("bulletSpeed", 4.0, 1.0, 8.0);

    public static final ModConfigSpec.DoubleValue CIWS_AIM_ERROR_START = BUILDER
            .comment("Fire control: aim error in degrees right after a golem picks up a shell. It shrinks while the golem",
                    "keeps tracking that shell, down to aimErrorSettled after trackingTicks. Early tracers visibly walk onto the shell.")
            .defineInRange("aimErrorStart", 15.0, 0.0, 45.0);

    public static final ModConfigSpec.IntValue CIWS_TRACKING_TICKS = BUILDER
            .comment("Ticks of tracking one shell before the aim error settles at aimErrorSettled.")
            .defineInRange("trackingTicks", 30, 0, 200);

    public static final ModConfigSpec.DoubleValue CIWS_AIM_ERROR_SETTLED = BUILDER
            .comment("Aim error in degrees once a shell has been tracked for trackingTicks (the best a golem gets).",
                    "With the defaults two golems on roofs stop ~70-75% of salvos from a hill 90 blocks off, and about half of",
                    "all shells while still 60+ blocks above the golems.")
            .defineInRange("aimErrorSettled", 5.0, 0.0, 30.0);

    static {
        BUILDER.pop().push("ironGolemCatThrow");
    }

    public static final ModConfigSpec.BooleanValue CAT_THROW_ENABLED = BUILDER
            .comment("Iron golems pick up nearby cats and throw them at creepers.")
            .define("enabled", true);

    public static final ModConfigSpec.DoubleValue CAT_THROW_RANGE = BUILDER
            .comment("Farthest creeper (from the golem) a cat will be thrown at.")
            .defineInRange("throwRange", 24.0, 4.0, 32.0);

    public static final ModConfigSpec.DoubleValue CAT_SEARCH_RANGE = BUILDER
            .comment("How far a golem walks to fetch a cat.")
            .defineInRange("catSearchRange", 16.0, 2.0, 32.0);

    public static final ModConfigSpec.DoubleValue CAT_SCARE_RADIUS = BUILDER
            .comment("Creepers this close to a landed cat are scared off (fuse reset, pushed away, mortars stop firing).")
            .defineInRange("scareRadius", 6.0, 1.0, 16.0);

    public static final ModConfigSpec.BooleanValue CAT_THROW_TAMED = BUILDER
            .comment("Whether golems may also throw players' tamed cats.")
            .define("throwTamedCats", false);

    static {
        BUILDER.pop();
    }

    /** Bump when changing a balance default, and add the value to {@link #migrate()}. */
    private static final int BALANCE_REVISION = 2;

    public static final ModConfigSpec.IntValue BALANCE_VERSION = BUILDER
            .comment("Internal: the balance revision this file was last updated to. Leave it alone.")
            .defineInRange("balanceVersion", 0, 0, Integer.MAX_VALUE);

    static final ModConfigSpec SPEC = BUILDER.build();

    /**
     * NeoForge keeps a value already written in the file when its default changes, so an existing
     * config would never pick up rebalanced defaults (1.0.1 users still fired on shells 3 s out).
     * Files from before a balance revision get those values reset to the new defaults once.
     */
    static void migrate() {
        int from = BALANCE_VERSION.get();
        if (from >= BALANCE_REVISION) {
            return;
        }
        if (from < 2) {
            // 1.0.3: engage 4 s out, cease fire 2.5 s before impact, sharper mid-range fire control.
            reset(CIWS_ENGAGE_TICKS);
            reset(CIWS_CEASE_FIRE_TICKS);
            reset(CIWS_AIM_ERROR_START);
            reset(CIWS_AIM_ERROR_SETTLED);
            reset(CIWS_TRACKING_TICKS);
        }
        BALANCE_VERSION.set(BALANCE_REVISION);
        SPEC.save();
        MobArmsRace.LOGGER.info("Updated snow golem fire control in the config to the balance of revision {} (was {})", BALANCE_REVISION, from);
    }

    private static <T> void reset(ModConfigSpec.ConfigValue<T> value) {
        value.set(value.getDefault());
    }
}
