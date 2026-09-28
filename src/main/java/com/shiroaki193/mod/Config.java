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

    public static final ModConfigSpec.IntValue CIWS_POST_RADIUS = BUILDER
            .comment("Snow golems keep within this many blocks of where they were placed, so they stay next to what they guard",
                    "(they only defend shells landing within protectRadius of themselves). 0 = wander freely like vanilla.")
            .defineInRange("postRadius", 6, 0, 64);

    public static final ModConfigSpec.DoubleValue CIWS_DETECT_RANGE = BUILDER
            .comment("Range at which a snow golem starts tracking shells.")
            .defineInRange("detectRange", 64.0, 8.0, 128.0);

    public static final ModConfigSpec.DoubleValue CIWS_ENGAGE_RANGE = BUILDER
            .comment("Terminal-phase range: the golem only fires at descending shells this close.")
            .defineInRange("engageRange", 44.0, 4.0, 64.0);

    public static final ModConfigSpec.DoubleValue CIWS_PROTECT_RADIUS = BUILDER
            .comment("Only shells predicted to land within this radius of the golem are engaged.")
            .defineInRange("protectRadius", 24.0, 2.0, 64.0);

    public static final ModConfigSpec.IntValue CIWS_FIRE_INTERVAL = BUILDER
            .comment("Ticks between interceptor snowballs.")
            .defineInRange("fireInterval", 2, 1, 40);

    public static final ModConfigSpec.DoubleValue CIWS_BULLET_SPEED = BUILDER
            .comment("Interceptor snowball speed in blocks/tick (flies straight, no gravity).")
            .defineInRange("bulletSpeed", 4.0, 1.0, 8.0);

    public static final ModConfigSpec.DoubleValue CIWS_SPREAD = BUILDER
            .comment("Aim spread in degrees. Wider spreads send more snowballs into nearby roofs and trees in a village:",
                    "in the village regression test 10 stops every shell, 18 lets roughly 1 in 12 through.")
            .defineInRange("spreadDegrees", 10.0, 0.0, 30.0);

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

    static final ModConfigSpec SPEC = BUILDER.build();
}
