package euphy.upo.sentrymechanicalarm;

import net.neoforged.neoforge.common.ModConfigSpec;

public class SMAServerConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLE_AMMO_CRAFTING, ENABLE_RELOADING, AUTO_RELOADING;
    public static final ModConfigSpec.BooleanValue ENABLE_CASINGS, PLAYER_CASINGS, SENTRY_CASINGS, OTHER_CASINGS;
    public static final ModConfigSpec.DoubleValue CASING_CHANCE;
    public static final ModConfigSpec.IntValue CASING_ENTITY_LIMIT, CASING_LIFETIME;

    public static final ModConfigSpec.BooleanValue ENABLE_DYNAMIC_RECIPES;
    public static final ModConfigSpec.BooleanValue AUTO_MATCH_FROM_TACZ;
    public static final ModConfigSpec.EnumValue<StepScaling> STEP_SCALING;
    public static final ModConfigSpec.IntValue FIXED_SCALE_FACTOR;

    public enum StepScaling {
        RAW,
        GCD,
        FIXED
    }

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("casings");
        ENABLE_CASINGS = builder.comment("Legacy runtime drop gate. Registration master is casings.enabled in config/sentrymechanicalarm-startup.toml.")
                .define("enabled", true);
        PLAYER_CASINGS = builder.comment("Allow real players to drop casings after finite-ammo TaCZ shots.").define("players", true);
        SENTRY_CASINGS = builder.comment("Allow sentry arms, including moving mounts, to drop casings.").define("sentries", true);
        OTHER_CASINGS = builder.comment("Allow other living entities to drop casings after TaCZ events prove actual ammo consumption.", "Does not grant mobs guns or shooting AI. Unrelated FakePlayers remain excluded.").define("others", false);
        CASING_CHANCE = builder.defineInRange("dropChance", 1.0, 0.0, 1.0);
        CASING_ENTITY_LIMIT = builder.comment("Maximum casing item entities in one chunk; excess drops are skipped.")
                .defineInRange("maxEntitiesPerChunk", 64, 0, 1024);
        CASING_LIFETIME = builder.comment("Casing item lifetime in game ticks.")
                .defineInRange("lifetimeTicks", 1200, 20, 12000);
        builder.pop();

        builder.push("reloading");
        ENABLE_RELOADING=builder.comment("Enable casing reloading; also requires casing registration and ammo manufacturing. Reload data packs after changing.").define("enabled",true);
        AUTO_RELOADING=builder.comment("Conservatively derive per-round costs from simple vanilla-material TaCZ recipes; round up and do not refund metal.").define("autoFromTaCZ",true);
        builder.pop();

        builder.push("ammo_crafting");
        ENABLE_AMMO_CRAFTING = builder
                .comment("Enable this mod's bullet manufacturing: templates, cutting, sequenced assembly and legacy material processing.",
                        "Does not disable TaCZ's own recipes, sentry shooting, reloading or ammo-box/depot supply.",
                        "Restart the world/server after changing this option.")
                .worldRestart()
                .define("enableAmmoCrafting", true);
        builder.pop();

        builder.push("dynamic_recipes");
        ENABLE_DYNAMIC_RECIPES = builder
                .comment("Master switch for dynamic ammo recipe injection. Disable to remove all auto-generated ammo cutting/assembly/pressing recipes.")
                .define("enableDynamicRecipes", true);
        AUTO_MATCH_FROM_TACZ = builder
                .comment("Whether to auto-extract materials and output count from TaCZ GunSmithTable recipes. When disabled, only AmmoRecipeConfig OVERRIDES + category fallback are used.")
                .define("autoMatchFromTaCZ", true);
        builder.pop();

        builder.push("step_scaling");
        STEP_SCALING = builder
                .comment("How to scale assembly step counts from TaCZ recipe ingredient amounts.",
                        "RAW  - use the raw count (e.g. 110 copper steps for 9mm)",
                        "GCD  - divide all counts and output by their greatest common divisor",
                        "FIXED - divide all counts and output by fixedScaleFactor")
                .defineEnum("stepScaling", StepScaling.GCD);
        FIXED_SCALE_FACTOR = builder
                .comment("Divisor used when stepScaling = FIXED")
                .defineInRange("fixedScaleFactor", 10, 1, 1000);
        builder.pop();

        SPEC = builder.build();
    }
}
