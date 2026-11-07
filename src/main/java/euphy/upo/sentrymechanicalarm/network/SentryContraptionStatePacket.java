package euphy.upo.sentrymechanicalarm.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-owned actor state, also sent periodically for clients that start tracking later. */
public record SentryContraptionStatePacket(int entityId,BlockPos localPos,CompoundTag state) implements CustomPacketPayload {
    public static final Type<SentryContraptionStatePacket> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm","contraption_state"));
    public static final StreamCodec<FriendlyByteBuf,SentryContraptionStatePacket> CODEC=StreamCodec.of(
            (buf,p)->{buf.writeVarInt(p.entityId);buf.writeBlockPos(p.localPos);buf.writeNbt(p.state);},
            buf->new SentryContraptionStatePacket(buf.readVarInt(),buf.readBlockPos(),buf.readNbt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
