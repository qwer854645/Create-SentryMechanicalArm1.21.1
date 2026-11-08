package euphy.upo.sentrymechanicalarm.client;

import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.network.ClientPacketHandler;
import euphy.upo.sentrymechanicalarm.util.SentryTrailManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@EventBusSubscriber(modid = SentryMechanicalArm.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SentryClientReloads {
    @SubscribeEvent
    public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> {
            ClientPacketHandler.clearCaches();
            EmptyCasingRenderer.clear();
            SentryTrailManager.clear();
        });
    }
}
