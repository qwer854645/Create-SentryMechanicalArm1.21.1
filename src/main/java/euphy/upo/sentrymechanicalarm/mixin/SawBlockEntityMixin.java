package euphy.upo.sentrymechanicalarm.mixin;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingInventory;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.tacz.guns.api.item.IAmmo;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.ItemNBTHelper;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Mixin(SawBlockEntity.class)
public abstract class SawBlockEntityMixin {

    @Shadow
    private FilteringBehaviour filtering;

    @Inject(method = "getRecipes", at = @At("RETURN"), cancellable = true, remap = false)
    private void onGetRecipes(CallbackInfoReturnable<List<RecipeHolder<? extends Recipe<?>>>> cir) {
        SawBlockEntity self = (SawBlockEntity) (Object) this;

        ItemStack filterStack = filtering.getFilter();
        if (filterStack.isEmpty()) return;

        ResourceLocation filterAmmoId = getFilterAmmoId(filterStack);
        if (filterAmmoId == null) return;

        ProcessingInventory inventory = self.inventory;
        ItemStack inputStack = inventory.getStackInSlot(0);
        if (inputStack.isEmpty()) return;

        List<RecipeHolder<? extends Recipe<?>>> cuttingRecipes = getCuttingRecipesFromManager(self);

        List<RecipeHolder<? extends Recipe<?>>> filtered = cuttingRecipes.stream()
                .filter(r -> r.id().getNamespace().equals(SentryMechanicalArm.MODID) && r.id().getPath().startsWith("ammo_cutting/"))
                .filter(r -> firstIngredientMatches(r, inputStack))
                .filter(r -> {
                    ItemStack output = r.value().getResultItem(self.getLevel().registryAccess());
                    if (output.isEmpty() || output.getItem() != SentryRegistry.UNFINISHED_AMMO.get()) {
                        return false;
                    }
                    ResourceLocation outputAmmoId = getFilterAmmoId(output);
                    return outputAmmoId != null && filterAmmoId.equals(outputAmmoId);
                })
                .collect(Collectors.toList());

        if (!filtered.isEmpty()) {
            cir.setReturnValue(filtered);
        }
    }

    private List<RecipeHolder<? extends Recipe<?>>> getCuttingRecipesFromManager(SawBlockEntity self) {
        List<RecipeHolder<? extends Recipe<?>>> result = new ArrayList<>();
        if (self.getLevel() == null) return result;
        RecipeManager recipeManager = self.getLevel().getRecipeManager();
        RecipeType<?> cuttingType = AllRecipeTypes.CUTTING.getType();
        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            if (holder.value().getType() == cuttingType) result.add(holder);
        }
        return result;
    }

    private boolean firstIngredientMatches(RecipeHolder<? extends Recipe<?>> holder, ItemStack input) {
        Recipe<?> recipe = holder.value();
        if (recipe instanceof StandardProcessingRecipe<?> processingRecipe) {
            NonNullList<Ingredient> ingredients = processingRecipe.getIngredients();
            if (!ingredients.isEmpty()) {
                return ingredients.get(0).test(input);
            }
        }
        return false;
    }

    private ResourceLocation getFilterAmmoId(ItemStack stack) {
        if (stack.getItem() == SentryRegistry.UNFINISHED_AMMO.get()) {
            CompoundTag tag = ItemNBTHelper.getTag(stack);
            if (tag != null && tag.contains("AmmoId")) {
                return ResourceLocation.tryParse(tag.getString("AmmoId"));
            }
        }
        IAmmo ammo = IAmmo.getIAmmoOrNull(stack);
        if (ammo != null) {
            return ammo.getAmmoId(stack);
        }
        return null;
    }
}