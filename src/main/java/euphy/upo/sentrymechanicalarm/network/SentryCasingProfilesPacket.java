package euphy.upo.sentrymechanicalarm.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Server-approved ammo IDs, including data-pack eligibility overrides. */
public record SentryCasingProfilesPacket(Set<ResourceLocation> ammo) implements CustomPacketPayload {
    public SentryCasingProfilesPacket{ammo=Set.copyOf(ammo);if(ammo.size()>4096)throw new IllegalArgumentException("Too many casing profiles");}
    public static final Type<SentryCasingProfilesPacket> TYPE=new Type<>(ResourceLocation.parse("sentrymechanicalarm:casing_profiles"));
    public static final StreamCodec<FriendlyByteBuf,SentryCasingProfilesPacket> CODEC=new StreamCodec<>(){
        public SentryCasingProfilesPacket decode(FriendlyByteBuf b){int n=b.readVarInt();if(n<0||n>4096)throw new IllegalArgumentException("Too many casing profiles");Set<ResourceLocation> ammo=new HashSet<>();for(int i=0;i<n;i++)ammo.add(b.readResourceLocation());return new SentryCasingProfilesPacket(ammo);}
        public void encode(FriendlyByteBuf b,SentryCasingProfilesPacket p){b.writeVarInt(p.ammo.size());p.ammo.stream().sorted().forEach(b::writeResourceLocation);}
    };
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
