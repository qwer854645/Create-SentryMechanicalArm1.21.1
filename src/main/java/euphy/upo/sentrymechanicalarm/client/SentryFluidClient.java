package euphy.upo.sentrymechanicalarm.client;

import euphy.upo.sentrymechanicalarm.registry.SentryFluids;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.*;

@EventBusSubscriber(modid="sentrymechanicalarm", value=Dist.CLIENT)
public final class SentryFluidClient {
    @SubscribeEvent public static void extensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            private static final ResourceLocation TEXTURE = ResourceLocation.parse("minecraft:block/gray_concrete_powder");
            @Override public ResourceLocation getStillTexture() { return TEXTURE; }
            @Override public ResourceLocation getFlowingTexture() { return TEXTURE; }
            @Override public int getTintColor() { return 0xffc8c0b0; }
        }, SentryFluids.PROPELLANT_TYPE.get());
    }
}
