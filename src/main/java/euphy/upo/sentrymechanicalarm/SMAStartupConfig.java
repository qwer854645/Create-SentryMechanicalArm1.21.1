package euphy.upo.sentrymechanicalarm;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Loaded synchronously by NeoForge before deferred item registration. */
public final class SMAStartupConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLE_CASINGS;
    static {
        var builder=new ModConfigSpec.Builder();
        builder.push("casings");
        ENABLE_CASINGS=builder.comment("Register recoverable casing items and enable the casing system.",
                "Restart the game/server after changing. Client and server must use the same value.",
                "Disabling removes the item registration; existing casing stacks cannot be preserved while disabled.")
                .define("enabled",true);
        builder.pop();SPEC=builder.build();
    }
}
