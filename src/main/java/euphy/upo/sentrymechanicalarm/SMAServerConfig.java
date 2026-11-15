package euphy.upo.sentrymechanicalarm;

import net.neoforged.neoforge.common.ModConfigSpec;

public class SMAServerConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue GUN_MANUFACTURING, AUTO_GUN_MANUFACTURING, REMOVE_NATIVE_GUN_RECIPES;
    public static final ModConfigSpec.IntValue SENTRY_RELOAD_DELAY;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> EXTRA_TARGETS;
    public static final ModConfigSpec.BooleanValue ENABLE_RELOADING, AUTO_RELOADING, LEGACY_RELOADING_SOURCES, RESTORE_LEGACY_AMMO_RECIPES, ENABLE_CASING_FORMING, GENERIC_CASING_FORMING;
    public static final ModConfigSpec.BooleanValue ENABLE_CASINGS, PLAYER_CASINGS, SENTRY_CASINGS, OTHER_CASINGS, PLAYER_CASINGS_TO_INVENTORY;
    public static final ModConfigSpec.BooleanValue FORCE_ALL_CASING_AMMO;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> FORCED_CASING_AMMO;
    public static final ModConfigSpec.DoubleValue CASING_CHANCE, RELOADING_METAL_FACTOR;
    public static final ModConfigSpec.IntValue CASING_ENTITY_LIMIT, CASING_LIFETIME, DEFAULT_CASING_YIELD;

    public static final ModConfigSpec.BooleanValue ATTACHMENT_MANUFACTURING, AUTO_ATTACHMENT_MANUFACTURING, REMOVE_NATIVE_ATTACHMENT_RECIPES;
    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("sentry");
        SENTRY_RELOAD_DELAY = builder.comment("Reload delay in game ticks. -1 uses the gun's empty reload feed and cooldown time; 0 disables the added delay.")
                .defineInRange("reloadDelayTicks", -1, -1, 72000);
        EXTRA_TARGETS = builder.comment("Additional default targets, alongside hostile mobs: entity type IDs or exact display names. Explicit fire-control safety lists still apply.")
                .defineListAllowEmpty("extraTargets", java.util.List.<String>of(), () -> "minecraft:wandering_trader", value -> value instanceof String);
        builder.pop();
        builder.push("casings");
        ENABLE_CASINGS = builder.comment("Legacy runtime drop gate. Registration master is casings.enabled in config/sentrymechanicalarm-startup.toml.")
                .define("enabled", true);
        FORCE_ALL_CASING_AMMO = builder.comment("Allow every loaded ammunition ID to produce a recoverable casing, bypassing automatic gun ejection declarations and shell-asset reference checks.",
                "Explicit per-ammo data-pack rules take priority. Registration, shooter gates, finite-ammo checks, drop chance and collection settings still apply. Reload data packs after changing.")
                .define("forceAllAmmo", false);
        FORCED_CASING_AMMO = builder.comment("Ammunition IDs allowed to produce casings even when automatic detection rejects them, e.g. tacz:9mm.",
                "These are TaCZ ammo IDs, not the shared empty_casing item ID. Explicit per-ammo rules still take priority. Reload data packs after changing.")
                .defineListAllowEmpty("forceAmmoIds", java.util.List.<String>of(), () -> "tacz:9mm",
                        value -> value instanceof String id && net.minecraft.resources.ResourceLocation.tryParse(id) != null);
        PLAYER_CASINGS = builder.comment("Allow real players to drop casings after finite-ammo TaCZ shots.").define("players", true);
        PLAYER_CASINGS_TO_INVENTORY=builder.comment("Insert real-player casing drops into their inventory instead of ejecting. Full-inventory remainder follows vanilla /give: drop at the player, no pickup delay, owned by that player.").define("playersToInventory",true);
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
        AUTO_RELOADING=builder.comment("Derive single-round metal, proportional fluid propellant and retained solid payload costs from TaCZ material recipes.").define("autoFromTaCZ",true);
        RELOADING_METAL_FACTOR=builder.comment("Main-metal share used for automatic reloading, after converting ingots and blocks to nuggets.", "Default 0.5 applies only to the case main metal (copper, else iron, else gold). Other projectile metals retain native costs. Direct metal below one nugget per round is omitted; other costs round up.", "Only automatic costs change; explicit data-pack costs are used exactly. Reload data packs after changing.").defineInRange("metalCostMultiplier",0.5,0.01,1.0);
        LEGACY_RELOADING_SOURCES=builder.comment("Read simple legacy TaCZ recipes/ material data from active packs when modern recipes are absent. Does not register native recipes.", "Conditional, NBT, component and complex legacy sources are skipped; modern resources always win. Reload after changing.").define("readLegacyMaterialRecipes",true);
        builder.pop();

        builder.push("gunpackCompatibility");
        RESTORE_LEGACY_AMMO_RECIPES=builder.comment("Restore strictly convertible legacy TaCZ ammo recipes from active recipes/ resources as native recipes.", "Modern resources and loaded recipes win. Conditional, component/NBT and unresolved material recipes are skipped. Reload after changing.").define("restoreLegacyAmmoRecipes",true);
        builder.pop();

        builder.push("casingManufacturing");
        ENABLE_CASING_FORMING=builder.comment("Enable mold-based casing manufacture priced from native main metal minus actual reloading metal. Reload data packs after changing.").define("enabled",true);
        GENERIC_CASING_FORMING=builder.comment("Allow every eligible physical casing to use the generic copper-nugget forming rule only if no native metal source is available, never to bypass an unrepresentable known cost. Does not grant live-ammo reloading recipes.").define("allowGenericCopperFallback",true);
        DEFAULT_CASING_YIELD=builder.comment("Cases per copper nugget for generic fallback only; native-budget yields remain automatic.").defineInRange("fallbackCasesPerCopperNugget",1,1,64);
        builder.pop();

        builder.push("gunManufacturing");
        GUN_MANUFACTURING = builder.comment("Enable shared gun-part sequences and selected firearm sequenced assembly. Reload data packs after changing.").define("enabled", true);
        AUTO_GUN_MANUFACTURING = builder.comment("Generate standard firearm routes from loaded native gun recipes. Explicit sentry_guns rules remain available.").define("autoFromTaCZ", true);
        REMOVE_NATIVE_GUN_RECIPES = builder.comment("Remove loaded TaCZ gunsmith-table recipes whose output is a firearm, including gunpack recipes.",
                "Native recipes remain available internally as sources for generated assembly costs and output components. Ammo, attachments and Create assembly recipes are retained.",
                "Independent of enabled/autoFromTaCZ. Set false and reload to restore native recipes; data-pack removals are respected. Reload data packs after changing.")
                .define("removeNativeGunRecipes", false);
        builder.pop();
        builder.push("attachmentManufacturing");
        ATTACHMENT_MANUFACTURING=builder.comment("Enable attachment selection and sequenced assembly, including shared component production. Reload after changing.").define("enabled",true);
        AUTO_ATTACHMENT_MANUFACTURING=builder.comment("Generate external attachment recipes from loaded TaCZ recipes. Hidden and built-in attachments require explicit rules.").define("autoFromTaCZ",true);
        REMOVE_NATIVE_ATTACHMENT_RECIPES=builder.comment("Remove native attachment recipes only when an equivalent assembly output was generated successfully. Reload after changing.").define("removeNativeAttachmentRecipes",false);
        builder.pop();
        SPEC = builder.build();
    }
}
