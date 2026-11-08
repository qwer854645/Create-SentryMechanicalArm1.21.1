package euphy.upo.sentrymechanicalarm.network;

import euphy.upo.sentrymechanicalarm.recipe.CasingFormingRecipes;
import euphy.upo.sentrymechanicalarm.registry.*;
import euphy.upo.sentrymechanicalarm.util.SentryCasingProfiles;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

public record CasingMoldSelectPacket(boolean offhand, ResourceLocation ammo) implements CustomPacketPayload {
    public static final Type<CasingMoldSelectPacket> TYPE = new Type<>(ResourceLocation.parse("sentrymechanicalarm:select_casing_mold"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CasingMoldSelectPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, CasingMoldSelectPacket::offhand, ResourceLocation.STREAM_CODEC, CasingMoldSelectPacket::ammo, CasingMoldSelectPacket::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(CasingMoldSelectPacket packet, ServerPlayer player) {
        if (!CasingFormingRecipes.enabled() || player.isSpectator() || !SentryCasingProfiles.enabled(packet.ammo())) return;
        var stack = player.getItemInHand(packet.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!stack.is(SentryRegistry.CASING_MOLD.get()) || CasingFormingRecipes.find(player.serverLevel().getRecipeManager(), packet.ammo()).isEmpty()) return;
        stack.set(ModDataComponents.CASING_AMMO.get(), packet.ammo());
        player.getInventory().setChanged(); player.containerMenu.broadcastChanges();
    }
}
