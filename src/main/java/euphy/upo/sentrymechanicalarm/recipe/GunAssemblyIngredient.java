package euphy.upo.sentrymechanicalarm.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import euphy.upo.sentrymechanicalarm.registry.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.*;
import java.util.stream.Stream;

/** A selected, unprocessed receiver. Identity is checked before the first operation. */
public record GunAssemblyIngredient(ResourceLocation recipe, Ingredient receiver) implements ICustomIngredient {
    public static final MapCodec<GunAssemblyIngredient> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceLocation.CODEC.fieldOf("recipe").forGetter(GunAssemblyIngredient::recipe),
            Ingredient.CODEC.fieldOf("receiver").forGetter(GunAssemblyIngredient::receiver)
    ).apply(i, GunAssemblyIngredient::new));
    public ItemStack prepared() {
        var stack = new ItemStack(GunParts.WORKPIECE.get());
        stack.set(ModDataComponents.GUN_WORK.get(), recipe);
        return stack;
    }
    public boolean test(ItemStack stack) {
        return stack.is(GunParts.WORKPIECE.get()) && recipe.equals(stack.get(ModDataComponents.GUN_WORK.get()))
                && !stack.has(com.simibubi.create.AllDataComponents.SEQUENCED_ASSEMBLY);
    }
    public Stream<ItemStack> getItems() { return Stream.of(prepared()); }
    public boolean isSimple() { return false; }
    public IngredientType<?> getType() { return SentryRecipeSerializers.GUN_ASSEMBLY.get(); }
}
