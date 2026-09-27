package com.shiroaki193.mod;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.push("mortarCreeper");
    }

    public static final ModConfigSpec.IntValue MORTAR_AMMO = BUILDER
            .comment("Shells a mortar creeper carries. It turns back into a normal creeper once they are spent.")
            .defineInRange("ammo", 4, 1, 64);

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

    public static final ModConfigSpec.DoubleValue CIWS_DETECT_RANGE = BUILDER
            .comment("Range at which a snow golem starts tracking shells.")
            .defineInRange("detectRange", 48.0, 8.0, 128.0);

    public static final ModConfigSpec.DoubleValue CIWS_ENGAGE_RANGE = BUILDER
            .comment("Terminal-phase range: the golem only fires at descending shells this close.")
            .defineInRange("engageRange", 28.0, 4.0, 64.0);

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
            .comment("Aim spread in degrees. Measured with two golems vs 4-shell salvos: 16 -> 96%, 18 -> 90%, 20 -> 67% intercepted.")
            .defineInRange("spreadDegrees", 18.0, 0.0, 30.0);

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
