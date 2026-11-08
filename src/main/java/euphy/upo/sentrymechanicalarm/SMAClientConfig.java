package euphy.upo.sentrymechanicalarm;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SMAClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SHELL_ANIMATIONS;
    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("effects");
        SHELL_ANIMATIONS = builder.comment("Render cosmetic sentry shell ejection. Does not change recoverable items or collection.")
                .define("shellAnimations", true);
        builder.pop();
        SPEC = builder.build();
    }
}
