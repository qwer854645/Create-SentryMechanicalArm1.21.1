package euphy.upo.sentrymechanicalarm.network;

import euphy.upo.sentrymechanicalarm.content.ReceiverComponentItem;
import euphy.upo.sentrymechanicalarm.recipe.*;
import euphy.upo.sentrymechanicalarm.registry.ModDataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

public record GunAssemblySelectPacket(boolean offhand, ResourceLocation recipe) implements CustomPacketPayload {
    public static final Type<GunAssemblySelectPacket> TYPE = new Type<>(ResourceLocation.parse("sentrymechanicalarm:select_gun_assembly"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GunAssemblySelectPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, GunAssemblySelectPacket::offhand, ResourceLocation.STREAM_CODEC, GunAssemblySelectPacket::recipe, GunAssemblySelectPacket::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(GunAssemblySelectPacket packet, ServerPlayer player) {
        if (player.isSpectator()) return;
        var hand = packet.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        var held = player.getItemInHand(hand);
        var choice = GunAssemblyRecipes.choices(player.serverLevel()).stream().filter(c -> c.id().equals(packet.recipe())).findFirst();
        if (!(held.getItem() instanceof ReceiverComponentItem) || choice.isEmpty() || !euphy.upo.sentrymechanicalarm.util.AssemblyTargets.enabled(choice.get()) || !choice.get().input().receiver().test(held)) {
            player.displayClientMessage(Component.translatable("screen.sentrymechanicalarm."+(held.getItem() instanceof euphy.upo.sentrymechanicalarm.content.AttachmentBaseItem?"attachment_assembly":"gun_assembly")+".changed"), true);
            return;
        }
        var prepared = choice.get().input().prepared();
        prepared.set(ModDataComponents.GUN_RECEIVER.get(), net.minecraft.world.item.component.ItemContainerContents.fromItems(java.util.List.of(held.copyWithCount(1))));
        if (held.getCount() == 1) player.setItemInHand(hand, prepared);
        else {
            held.shrink(1);
            player.getInventory().placeItemBackInInventory(prepared);
        }
        player.getInventory().setChanged(); player.containerMenu.broadcastChanges();
    }
}
