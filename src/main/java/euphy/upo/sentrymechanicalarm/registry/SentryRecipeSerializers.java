package euphy.upo.sentrymechanicalarm.registry;

import euphy.upo.sentrymechanicalarm.recipe.AmmoTemplateRecipe;
import euphy.upo.sentrymechanicalarm.recipe.ClipboardCopyRecipe;
import euphy.upo.sentrymechanicalarm.recipe.UnfinishedAmmoIngredient;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.common.crafting.IngredientType;
import java.util.function.Supplier;

public class SentryRecipeSerializers {
    public static final DeferredRegister<IngredientType<?>> INGREDIENTS =
            DeferredRegister.create(NeoForgeRegistries.INGREDIENT_TYPES, "sentrymechanicalarm");
    public static final Supplier<IngredientType<UnfinishedAmmoIngredient>> UNFINISHED_AMMO =
            INGREDIENTS.register("unfinished_ammo", () -> new IngredientType<>(UnfinishedAmmoIngredient.CODEC));

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, "sentrymechanicalarm");

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ClipboardCopyRecipe>> CLIPBOARD_COPY =
            SERIALIZERS.register("clipboard_copy",
                    () -> new SimpleCraftingRecipeSerializer<>(ClipboardCopyRecipe::new));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AmmoTemplateRecipe>> AMMO_TEMPLATE =
            SERIALIZERS.register("ammo_template",
                    () -> new SimpleCraftingRecipeSerializer<>(AmmoTemplateRecipe::new));

    public static void register(IEventBus eventBus) {
        INGREDIENTS.register(eventBus);
        SERIALIZERS.register(eventBus);
    }
}
