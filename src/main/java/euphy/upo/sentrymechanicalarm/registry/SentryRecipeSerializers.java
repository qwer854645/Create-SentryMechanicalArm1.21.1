package euphy.upo.sentrymechanicalarm.registry;

import euphy.upo.sentrymechanicalarm.recipe.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.common.crafting.IngredientType;
import java.util.function.Supplier;

public class SentryRecipeSerializers {
    public static final DeferredRegister<IngredientType<?>> INGREDIENTS=DeferredRegister.create(NeoForgeRegistries.INGREDIENT_TYPES,"sentrymechanicalarm");
    public static final Supplier<IngredientType<CountedDeployerIngredient>> COUNTED_DEPLOYING=INGREDIENTS.register("counted_deploying",()->new IngredientType<>(CountedDeployerIngredient.CODEC));
    public static final Supplier<IngredientType<MoldIngredient>> MOLD=INGREDIENTS.register("casing_mold",()->new IngredientType<>(MoldIngredient.CODEC));
    public static final Supplier<IngredientType<CasingIngredient>> CASING=INGREDIENTS.register("casing",()->new IngredientType<>(CasingIngredient.CODEC));
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS=DeferredRegister.create(Registries.RECIPE_SERIALIZER,"sentrymechanicalarm");
    public static final DeferredHolder<RecipeSerializer<?>,RecipeSerializer<ClipboardCopyRecipe>> CLIPBOARD_COPY=SERIALIZERS.register("clipboard_copy",()->new SimpleCraftingRecipeSerializer<>(ClipboardCopyRecipe::new));
    public static final DeferredHolder<RecipeSerializer<?>,RecipeSerializer<RecoverCasingRecipe>> RECOVER_CASING=SERIALIZERS.register("recover_casing",()->new SimpleCraftingRecipeSerializer<>(RecoverCasingRecipe::new));
    public static void register(IEventBus bus){INGREDIENTS.register(bus);SERIALIZERS.register(bus);}
}
