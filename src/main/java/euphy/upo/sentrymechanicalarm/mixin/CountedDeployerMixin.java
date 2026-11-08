package euphy.upo.sentrymechanicalarm.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.deployer.*;
import euphy.upo.sentrymechanicalarm.recipe.CountedDeployerIngredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeltDeployerCallbacks.class)
public class CountedDeployerMixin {
    @Unique private static CountedDeployerIngredient sentry$requirement(Recipe<?> recipe) {
        if (!(recipe instanceof ItemApplicationRecipe application) || application.shouldKeepHeldItem()) return null;
        return CountedDeployerIngredient.from(application.getRequiredHeldItem());
    }
    @Inject(method="activate", at=@At("HEAD"), cancellable=true, remap=false)
    private static void sentry$checkCount(TransportedItemStack transported, TransportedItemStackHandlerBehaviour handler,
            DeployerBlockEntity deployer, Recipe<?> recipe, CallbackInfo ci) {
        var requirement = sentry$requirement(recipe);
        if (requirement != null && (deployer.getPlayer() == null || !requirement.canConsume(deployer.getPlayer().getMainHandItem()))) ci.cancel();
    }
    // The first shrink consumes the workpiece; the second consumes the held consumable.
    @WrapOperation(method="activate", at=@At(value="INVOKE", target="Lnet/minecraft/world/item/ItemStack;shrink(I)V", ordinal=1), remap=false)
    private static void sentry$consumeCount(ItemStack stack, int originalAmount, Operation<Void> original,
            @Local(argsOnly=true) Recipe<?> recipe) {
        var requirement = sentry$requirement(recipe);
        original.call(stack, requirement == null ? originalAmount : requirement.count());
    }
}
