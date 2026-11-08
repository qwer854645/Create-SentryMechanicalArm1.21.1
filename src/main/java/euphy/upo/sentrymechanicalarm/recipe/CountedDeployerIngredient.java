package euphy.upo.sentrymechanicalarm.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import euphy.upo.sentrymechanicalarm.registry.SentryRecipeSerializers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.*;
import java.util.*;
import java.util.stream.Stream;

/** Ingredient identity remains count-independent; the execution hook enforces the full quantity. */
public record CountedDeployerIngredient(Ingredient ingredient, int count) implements ICustomIngredient {
    public static final MapCodec<CountedDeployerIngredient> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(CountedDeployerIngredient::ingredient),
            Codec.intRange(2, 64).fieldOf("count").forGetter(CountedDeployerIngredient::count)
    ).apply(i, CountedDeployerIngredient::new));

    public CountedDeployerIngredient {
        if (count < 2 || count > 64) throw new IllegalArgumentException("Deployer count must be 2..64");
    }
    public boolean test(ItemStack stack) { return safe(stack, count) && ingredient.test(stack); }
    public boolean canConsume(ItemStack stack) { return test(stack) && stack.getCount() >= count; }
    public Stream<ItemStack> getItems() { return Arrays.stream(ingredient.getItems()).filter(s -> safe(s, count)).map(s -> s.copyWithCount(count)); }
    public boolean isSimple() { return false; }
    public IngredientType<?> getType() { return SentryRecipeSerializers.COUNTED_DEPLOYING.get(); }
    private static boolean safe(ItemStack stack, int count) {
        return !stack.isEmpty() && stack.getMaxStackSize() >= count && !stack.isDamageableItem() && !stack.hasCraftingRemainingItem();
    }
    public static CountedDeployerIngredient from(Ingredient ingredient) {
        return ingredient.getCustomIngredient() instanceof CountedDeployerIngredient counted ? counted : null;
    }
    public static int amount(Ingredient ingredient) { var counted = from(ingredient); return counted == null ? 1 : counted.count(); }
    private static boolean equivalent(Ingredient first, Ingredient second) {
        if (first == second || first.equals(second)) return true;
        if (!first.isSimple() || !second.isSimple()) return false;
        var a = first.getItems(); var b = second.getItems();
        return a.length > 0 && a.length == b.length
                && Arrays.stream(a).allMatch(x -> Arrays.stream(b).anyMatch(y -> ItemStack.isSameItemSameComponents(x, y)))
                && Arrays.stream(b).allMatch(x -> Arrays.stream(a).anyMatch(y -> ItemStack.isSameItemSameComponents(x, y)));
    }

    /** Only identical ordinary consumable ingredients are grouped; tools and remainders stay native. */
    public static List<Ingredient> compact(List<Ingredient> materials) {
        var result = new ArrayList<Ingredient>();
        for (int i = 0; i < materials.size();) {
            var material = materials.get(i); int end = i + 1;
            while (end < materials.size() && equivalent(material, materials.get(end))) end++;
            int count = end - i;
            var samples = material.getItems();
            if (material.isSimple() && samples.length > 0 && Arrays.stream(samples).allMatch(s -> safe(s, 1))) {
                int limit = Math.min(64, Arrays.stream(samples).mapToInt(ItemStack::getMaxStackSize).min().orElse(1));
                while (count > 0) {
                    int batch = Math.min(count, limit);
                    result.add(batch == 1 ? material : new CountedDeployerIngredient(material, batch).toVanilla());
                    count -= batch;
                }
            } else result.addAll(materials.subList(i, end));
            i = end;
        }
        return List.copyOf(result);
    }
}
