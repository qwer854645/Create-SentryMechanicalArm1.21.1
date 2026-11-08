package euphy.upo.sentrymechanicalarm.recipe;

import com.simibubi.create.AllItems;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import java.math.*;
import java.util.*;

/** Native powder ratio in mB, retained payload, and the configured metal share. */
public final class ReloadingCosts {
    public static final int MAX_MATERIAL_UNITS = 1024;
    public static final int MAX_ASSEMBLY_STEPS = 16;
    public record Cost(List<Ingredient> materials, int propellant) {}
    private ReloadingCosts() {}
    private static boolean metal(Item item) {
        return AmmoMetalCosts.isMetal(item);
    }
    /** Direct solid materials, excluding the separate fluid charge. */
    public static Optional<List<Ingredient>> derive(GunSmithTableRecipe recipe, double metalFactor) {
        return deriveFluid(recipe, metalFactor).map(Cost::materials);
    }
    public static Optional<Cost> deriveFluid(GunSmithTableRecipe recipe, double metalFactor) {
        int rounds = recipe.getOutput().getCount();
        if (rounds < 1 || recipe.getInputs().isEmpty() || !Double.isFinite(metalFactor) || metalFactor <= 0 || metalFactor > 1)
            return Optional.empty();
        var nativeTotals = new LinkedHashMap<Item, BigDecimal>();
        BigDecimal nativePowder = BigDecimal.ZERO;
        for (var material : recipe.getInputs()) {
            var ingredient = material.getIngredient();
            var candidates = ingredient.getItems();
            if (material.getCount() < 1 || !ingredient.isSimple() || candidates.length == 0) return Optional.empty();
            Item input = candidates[0].getItem();
            for (var candidate : candidates)
                if (candidate.isEmpty() || candidate.getItem() != input || candidate.isDamageableItem()
                        || !candidate.getComponentsPatch().isEmpty() || candidate.hasCraftingRemainingItem()) return Optional.empty();
            var units = BigDecimal.valueOf(material.getCount());
            if (input == Items.GUNPOWDER) { nativePowder = nativePowder.add(units); continue; }
            Item output = AmmoMetalCosts.canonical(input);
            units = units.multiply(BigDecimal.valueOf(AmmoMetalCosts.units(input)));
            nativeTotals.merge(output, units, BigDecimal::add);
        }
        if (nativePowder.signum() <= 0) return Optional.empty();
        var powderAmount = nativePowder.multiply(BigDecimal.valueOf(PropellantAmounts.PER_GUNPOWDER))
                .divide(BigDecimal.valueOf(rounds), 0, RoundingMode.CEILING);
        // Bound before converting to int or constructing steps; oversized recipes cannot overflow.
        if (powderAmount.compareTo(BigDecimal.valueOf((long) MAX_ASSEMBLY_STEPS * PropellantAmounts.MAX_FILL)) > 0) return Optional.empty();
        int fluid = powderAmount.intValueExact();
        var solids = new ArrayList<Ingredient>();
        var primary = AmmoMetalCosts.primary(nativeTotals).orElse(null);
        boolean savesMaterial = false;
        for (var entry : nativeTotals.entrySet()) {
            boolean isMetal = metal(entry.getKey());
            var allocated = entry.getKey()==primary ? entry.getValue().multiply(BigDecimal.valueOf(metalFactor)) : entry.getValue();
            // Only fractional direct metal is omitted; special payload ingredients are always retained.
            var count = isMetal && allocated.compareTo(BigDecimal.valueOf(rounds)) < 0 ? BigDecimal.ZERO
                    : allocated.divide(BigDecimal.valueOf(rounds), 0, RoundingMode.CEILING);
            if (count.compareTo(BigDecimal.valueOf(MAX_MATERIAL_UNITS - solids.size())) > 0) return Optional.empty();
            savesMaterial |= count.multiply(BigDecimal.valueOf(rounds)).compareTo(entry.getValue()) < 0;
            for (int i = 0; i < count.intValueExact(); i++) solids.add(Ingredient.of(entry.getKey()));
        }
        int fills = (fluid + PropellantAmounts.MAX_FILL - 1) / PropellantAmounts.MAX_FILL;
        int stages = 1 + CountedDeployerIngredient.compact(solids).size() + fills; // solids + fillings + press
        return savesMaterial && stages <= MAX_ASSEMBLY_STEPS ? Optional.of(new Cost(List.copyOf(solids), fluid)) : Optional.empty();
    }
}
