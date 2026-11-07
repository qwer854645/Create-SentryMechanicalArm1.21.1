package euphy.upo.sentrymechanicalarm.smoke;

import euphy.upo.sentrymechanicalarm.SMAServerConfig;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(modid=SentryMechanicalArm.MODID)
public final class AmmoCraftingConfigSmoke {
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("sentry.ammoCraftingDisabledSmoke")) return;
        if (SMAServerConfig.ENABLE_AMMO_CRAFTING.get()) throw new AssertionError("Disabled TOML was not loaded");
        if (event.getServer().getRecipeManager().getRecipes().stream().anyMatch(h ->
                h.id().getNamespace().equals(SentryMechanicalArm.MODID) &&
                (h.id().getPath().startsWith("ammo_cutting/") || h.id().getPath().startsWith("ammo_assembly/") || h.id().getPath().startsWith("ammo_pressing/")))) {
            throw new AssertionError("Ammo manufacturing recipes present while master config is disabled");
        }
        SentryMechanicalArm.LOGGER.info("AMMO_SWITCH PASS disabled TOML loaded and manufacturing recipes absent");
    }
}
