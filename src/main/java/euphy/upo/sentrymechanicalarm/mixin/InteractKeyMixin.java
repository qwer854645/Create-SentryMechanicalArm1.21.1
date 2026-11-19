package euphy.upo.sentrymechanicalarm.mixin;

import com.tacz.guns.client.input.InteractKey;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlock;
import euphy.upo.sentrymechanicalarm.network.SentryInteractPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InteractKey.class)
public class InteractKeyMixin {

    @Group(name = "sentry$interact", min = 1, max = 1)
    @Inject(method = "interactBlock(Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/client/Minecraft;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void sentry$legacyInteract(BlockHitResult hit, LocalPlayer player, Minecraft mc, CallbackInfo ci) {
        sentry$interact(hit, player, false, ci);
    }

    @Group(name = "sentry$interact", min = 1, max = 1)
    @Inject(method = "interactBlock(Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/client/Minecraft;Z)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void sentry$hotfixInteract(BlockHitResult hit, LocalPlayer player, Minecraft mc,
            boolean vanillaHandlesUse, CallbackInfo ci) {
        sentry$interact(hit, player, vanillaHandlesUse, ci);
    }

    private static void sentry$interact(BlockHitResult blockHitResult, LocalPlayer player,
            boolean vanillaHandlesUse, CallbackInfo ci) {
        BlockPos blockPos = blockHitResult.getBlockPos();
        BlockState state = player.level().getBlockState(blockPos);
        if (state.getBlock() instanceof SentryArmBlock) {
            // Shared use bindings already invoke the normal block interaction.
            if (!vanillaHandlesUse)
                PacketDistributor.sendToServer(new SentryInteractPacket(blockPos, InteractionHand.MAIN_HAND, false));
            ci.cancel();
        }
    }
}