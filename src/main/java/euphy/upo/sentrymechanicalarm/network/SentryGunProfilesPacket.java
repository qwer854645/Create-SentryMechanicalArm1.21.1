package euphy.upo.sentrymechanicalarm.network;

import euphy.upo.sentrymechanicalarm.util.SentryGunProfiles;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public record SentryGunProfilesPacket(Map<ResourceLocation,SentryGunProfiles.Profile> profiles) implements CustomPacketPayload {
    public static final Type<SentryGunProfilesPacket> TYPE = new Type<>(ResourceLocation.parse("sentrymechanicalarm:gun_profiles"));
    public static final StreamCodec<FriendlyByteBuf,SentryGunProfilesPacket> CODEC = new StreamCodec<>() {
        public SentryGunProfilesPacket decode(FriendlyByteBuf b) {
            int n=b.readVarInt(); if (n<0 || n>4096) throw new IllegalArgumentException("Too many gun profiles");
            Map<ResourceLocation,SentryGunProfiles.Profile> profiles=new LinkedHashMap<>();
            for (int i=0;i<n;i++) profiles.put(b.readResourceLocation(),new SentryGunProfiles.Profile(
                    new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),
                    b.readBoolean(),b.readBoolean(),b.readBoolean()));
            return new SentryGunProfilesPacket(Map.copyOf(profiles));
        }
        public void encode(FriendlyByteBuf b,SentryGunProfilesPacket p) {
            b.writeVarInt(p.profiles.size());
            p.profiles.forEach((id,g)-> {
                b.writeResourceLocation(id); b.writeDouble(g.muzzle().x); b.writeDouble(g.muzzle().y); b.writeDouble(g.muzzle().z);
                b.writeDouble(g.forward().x); b.writeDouble(g.forward().y); b.writeDouble(g.forward().z);
                b.writeBoolean(g.visibleProjectile()); b.writeBoolean(g.minigunMount()); b.writeBoolean(g.fromModel());
            });
        }
    };
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
