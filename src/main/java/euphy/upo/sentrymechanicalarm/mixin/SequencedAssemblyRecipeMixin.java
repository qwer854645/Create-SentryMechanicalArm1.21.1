package euphy.upo.sentrymechanicalarm.mixin;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import euphy.upo.sentrymechanicalarm.recipe.ReloadingRecipes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SequencedAssemblyRecipe.class)
public class SequencedAssemblyRecipeMixin {
    @Inject(method="appliesTo",at=@At("HEAD"),cancellable=true)
    private void sentry$disableReloading(ResourceLocation id,ItemStack input,CallbackInfoReturnable<Boolean> cir){if(id.getNamespace().equals("sentrymechanicalarm")&&id.getPath().startsWith("casing_reloading/")&&!ReloadingRecipes.enabled())cir.setReturnValue(false);}
}
