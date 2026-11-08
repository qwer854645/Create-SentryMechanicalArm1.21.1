package euphy.upo.sentrymechanicalarm;

import net.neoforged.neoforge.common.ModConfigSpec;

public class SMAServerConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLE_RELOADING, AUTO_RELOADING, ENABLE_CASING_FORMING, GENERIC_CASING_FORMING;
    public static final ModConfigSpec.BooleanValue ENABLE_CASINGS, PLAYER_CASINGS, SENTRY_CASINGS, OTHER_CASINGS;
    public static final ModConfigSpec.DoubleValue CASING_CHANCE, RELOADING_METAL_FACTOR;
    public static final ModConfigSpec.IntValue CASING_ENTITY_LIMIT, CASING_LIFETIME, DEFAULT_CASING_YIELD;

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
        ENABLE_RELOADING=builder.comment("Enable casing reloading; requires casing registration. Reload data packs after changing.").define("enabled",true);
        AUTO_RELOADING=builder.comment("Derive single-round nugget and small-gunpowder costs from simple TaCZ material recipes.").define("autoFromTaCZ",true);
        RELOADING_METAL_FACTOR=builder.comment("Metal share used for automatic reloading costs, after converting ingots to nuggets.", "Default 0.5 is a balance policy, not inferred casing composition. Each material rounds up per round.", "Only automatic costs change; explicit data-pack costs are used exactly. Reload data packs after changing.").defineInRange("metalCostMultiplier",0.5,0.01,1.0);
        builder.pop();

        builder.push("casingManufacturing");
        ENABLE_CASING_FORMING=builder.comment("Enable mold-based casing manufacture from understood native TaCZ costs. Reload data packs after changing.").define("enabled",true);
        GENERIC_CASING_FORMING=builder.comment("Allow every eligible physical casing to use the generic copper-nugget forming rule if no native metal budget is available. Does not grant live-ammo reloading recipes.").define("allowGenericCopperFallback",true);
        DEFAULT_CASING_YIELD=builder.comment("Cases per copper nugget for generic fallback only; native-budget yields remain automatic.").defineInRange("fallbackCasesPerCopperNugget",1,1,64);
        builder.pop();

        SPEC = builder.build();
    }
}
