package euphy.upo.sentrymechanicalarm.recipe;

import com.simibubi.create.content.kinetics.deployer.*;
import euphy.upo.sentrymechanicalarm.registry.ModDataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.*;

/** A selected unprocessed workpiece is a reusable target-setting tool. */
@EventBusSubscriber(modid = "sentrymechanicalarm")
public final class GunAssemblyCopying {
    public static RecipeHolder<DeployerApplicationRecipe> recipe(GunAssemblyRecipes.Choice choice, ItemStack actualReceiver) {
        var result = choice.input().prepared();
        if (!actualReceiver.isEmpty()) result.set(ModDataComponents.GUN_RECEIVER.get(), net.minecraft.world.item.component.ItemContainerContents.fromItems(List.of(actualReceiver.copyWithCount(1))));
        var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm",
                (euphy.upo.sentrymechanicalarm.util.AssemblyTargets.attachment(choice)?"gun_manufacturing/select_attachment/":"gun_manufacturing/select/") + choice.gun().getNamespace() + "/" + choice.gun().getPath() + "/" + choice.id().getPath().substring(choice.id().getPath().lastIndexOf('/') + 1));
        var recipe = new ItemApplicationRecipe.Builder<>(DeployerApplicationRecipe::new, id)
                .require(choice.input().receiver()).require(choice.input().toVanilla()).output(result).toolNotConsumed().build();
        return new RecipeHolder<>(id, recipe);
    }
    @SubscribeEvent public static void search(DeployerRecipeSearchEvent event) {
        var level = event.getBlockEntity().getLevel();
        if (level == null || level.isClientSide) return;
        var input = event.getInventory().getItem(0); var tool = event.getInventory().getItem(1);
        var identity = tool.get(ModDataComponents.GUN_WORK.get()); if (identity == null) return;
        var choice = GunAssemblyRecipes.choices(level).stream().filter(c -> c.id().equals(identity)
                && euphy.upo.sentrymechanicalarm.util.AssemblyTargets.enabled(c) && c.input().test(tool) && c.input().receiver().test(input)).findFirst();
        choice.ifPresent(c -> event.addRecipe(() -> Optional.of(recipe(c, input)), 200));
    }
}
