package euphy.upo.sentrymechanicalarm.mixin;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(SequencedAssemblyRecipe.class)
public interface SequencedAssemblyRecipeAccessor {
    @Accessor("transitionalItem") void sentry$setTransition(ProcessingOutput output);
}
