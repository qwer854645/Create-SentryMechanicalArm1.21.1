package euphy.upo.sentrymechanicalarm.mixin;

import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.deployer.BeltDeployerCallbacks;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import euphy.upo.sentrymechanicalarm.util.ItemNBTHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BeltDeployerCallbacks.class)
public class BeltDeployerCallbacksMixin {

    @Unique
    private static final long FILL_COOLDOWN_TICKS = 60L;

    @Inject(method = "onItemReceived", at = @At("RETURN"), cancellable = true, remap = false)
    private static void onItemReceivedReturn(TransportedItemStack transported, TransportedItemStackHandlerBehaviour handler, DeployerBlockEntity deployer, CallbackInfoReturnable<BeltProcessingBehaviour.ProcessingResult> cir) {
        if (cir.getReturnValue() == BeltProcessingBehaviour.ProcessingResult.PASS && shouldCustomHold(transported, deployer)) {
            cir.setReturnValue(BeltProcessingBehaviour.ProcessingResult.HOLD);
        }
    }

    @Inject(method = "whenItemHeld", at = @At("RETURN"), cancellable = true, remap = false)
    private static void whenItemHeldReturn(TransportedItemStack transported, TransportedItemStackHandlerBehaviour handler, DeployerBlockEntity deployer, CallbackInfoReturnable<BeltProcessingBehaviour.ProcessingResult> cir) {
        if (cir.getReturnValue() == BeltProcessingBehaviour.ProcessingResult.PASS && shouldCustomHold(transported, deployer)) {
            BeltProcessingBehaviour.ProcessingResult result = tryActivate(transported, handler, deployer);
            cir.setReturnValue(result);
        }
    }

    @Unique
    private static boolean shouldCustomHold(TransportedItemStack transported, DeployerBlockEntity deployer) {
        if (deployer.getPlayer() == null) return false;
        ItemStack handStack = deployer.getPlayer().getMainHandItem();
        if (handStack.isEmpty()) return false;

        ItemStack beltStack = transported.stack;
        if (beltStack.isEmpty()) return false;

        if (IAmmo.getIAmmoOrNull(handStack) != null && beltStack.getItem() instanceof IAmmoBox) {
            return true;
        }

        return false;
    }

    @Unique
    private static BeltProcessingBehaviour.ProcessingResult tryActivate(TransportedItemStack transported, TransportedItemStackHandlerBehaviour handler, DeployerBlockEntity deployer) {
        if (deployer.getLevel() == null || deployer.getLevel().isClientSide || deployer.getPlayer() == null) return BeltProcessingBehaviour.ProcessingResult.PASS;
        ItemStack handStack = deployer.getPlayer().getMainHandItem();
        if (handStack.isEmpty()) return BeltProcessingBehaviour.ProcessingResult.PASS;

        ItemStack beltStack = transported.stack;
        long gameTime = deployer.getLevel().getGameTime();

        IAmmo heldAmmo = IAmmo.getIAmmoOrNull(handStack);
        if (heldAmmo != null && beltStack.getItem() instanceof IAmmoBox) {
            ItemStack newBoxStack = beltStack.copy();
            IAmmoBox boxItem = (IAmmoBox) newBoxStack.getItem();

            ResourceLocation boxId = boxItem.getAmmoId(newBoxStack);
            ResourceLocation bulletId = heldAmmo.getAmmoId(handStack);
            boolean isBoxEmpty = boxId == null || boxId.toString().equals("tacz:empty");

            if (!isBoxEmpty && !boxId.equals(bulletId)) {
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            int maxCapacity = getMaxAmmoCount(boxItem, newBoxStack);
            int currentCount = boxItem.getAmmoCount(newBoxStack);

            if (currentCount >= maxCapacity) {
                return BeltProcessingBehaviour.ProcessingResult.PASS;
            }

            if (isBoxEmpty) {
                boxItem.setAmmoId(newBoxStack, bulletId);
            }

            int space = maxCapacity - currentCount;
            int toAdd = Math.min(space, handStack.getCount());

            boxItem.setAmmoCount(newBoxStack, currentCount + toAdd);

            CompoundTag tag = ItemNBTHelper.getOrCreateTag(newBoxStack);
            if (gameTime - tag.getLong("SMAPCooldown") < FILL_COOLDOWN_TICKS) {
                return BeltProcessingBehaviour.ProcessingResult.HOLD;
            }
            tag.putLong("SMAPCooldown", gameTime);
            ItemNBTHelper.setTag(newBoxStack, tag);

            handStack.shrink(toAdd);

            TransportedItemStack newTransported = transported.copy();
            newTransported.stack = newBoxStack.copy();
            handler.handleProcessingOnItem(transported, TransportedResult.convertTo(newTransported));

            return BeltProcessingBehaviour.ProcessingResult.HOLD;
        }

        return BeltProcessingBehaviour.ProcessingResult.PASS;
    }

    @Unique
    private static int getMaxAmmoCount(IAmmoBox box, ItemStack stack) {
        if (box.isCreative(stack)) return Integer.MAX_VALUE;
        int level = box.getAmmoLevel(stack);
        return switch (level) {
            case 0 -> 180;
            case 1 -> 360;
            case 2 -> 540;
            default -> 180;
        };
    }
}
