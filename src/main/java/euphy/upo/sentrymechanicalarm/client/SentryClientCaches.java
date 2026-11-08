package euphy.upo.sentrymechanicalarm.client;

import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.network.ClientPacketHandler;
import euphy.upo.sentrymechanicalarm.util.SentryGunProfiles;
import euphy.upo.sentrymechanicalarm.util.SentryTrailManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import java.util.Map;

@EventBusSubscriber(modid=SentryMechanicalArm.MODID,value=Dist.CLIENT)
public final class SentryClientCaches {
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        euphy.upo.sentrymechanicalarm.util.SentryCasingProfiles.receive(java.util.Set.of());
        CasingJeiBridge.refresh();
        EmptyCasingRenderer.clear(); SentryGunProfiles.receive(Map.of()); ClientPacketHandler.clearCaches(); SentryTrailManager.clear();
    }
}
