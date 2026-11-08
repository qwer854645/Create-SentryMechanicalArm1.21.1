package euphy.upo.sentrymechanicalarm.recipe;

import com.simibubi.create.AllItems;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.SMAServerConfig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Game-balance policy for understood materials, independent of gunpack and caliber names. */
public final class ReloadingCosts {
    public static final int POWDER_PORTIONS = 32;
    public static final int MAX_MATERIAL_UNITS = 1024;
    public static final int MAX_ASSEMBLY_STEPS = 16;

    private ReloadingCosts() {}
    private static boolean metal(Item item) {
        return item == AllItems.COPPER_NUGGET.get() || item == Items.IRON_NUGGET || item == Items.GOLD_NUGGET;
    }

    public static Optional<List<Ingredient>> derive(GunSmithTableRecipe recipe, double metalFactor) {
        int rounds = recipe.getOutput().getCount();
        if (rounds < 1 || recipe.getInputs().isEmpty()
                || !Double.isFinite(metalFactor) || metalFactor <= 0 || metalFactor > 1) return Optional.empty();
        Map<Item, BigDecimal> totals = new LinkedHashMap<>();
        Map<Item, BigDecimal> originalTotals = new LinkedHashMap<>();
        BigDecimal nativePowder = BigDecimal.ZERO;
        for (var material : recipe.getInputs()) {
            var ingredient = material.getIngredient();
            var candidates = ingredient.getItems();
            if (material.getCount() < 1 || !ingredient.isSimple() || candidates.length == 0) return Optional.empty();
            Item input = candidates[0].getItem();
            for (var candidate : candidates) {
                if (candidate.isEmpty() || candidate.getItem() != input || candidate.isDamageableItem()
                        || !candidate.getComponentsPatch().isEmpty() || candidate.hasCraftingRemainingItem()) return Optional.empty();
            }
            Item output = input;
            BigDecimal units = BigDecimal.valueOf(material.getCount());
            if (input == Items.GUNPOWDER) {
                nativePowder = nativePowder.add(units);
                output = SentryRegistry.SMALL_GUNPOWDER.get();
                units = units.multiply(BigDecimal.valueOf(POWDER_PORTIONS));
            } else if (input != Items.PAPER) {
                if (input == Items.COPPER_INGOT) output = AllItems.COPPER_NUGGET.get();
                else if (input == Items.IRON_INGOT) output = Items.IRON_NUGGET;
                else if (input == Items.GOLD_INGOT) output = Items.GOLD_NUGGET;
                if (output != input) units = units.multiply(BigDecimal.valueOf(9));
            }
            originalTotals.merge(output, units, BigDecimal::add);
            if (metal(output)) units = units.multiply(BigDecimal.valueOf(metalFactor));
            // Merge equal materials before rounding, including ingot/nugget entries of the same metal.
            totals.merge(output, units, BigDecimal::add);
        }
        if (nativePowder.signum() <= 0) return Optional.empty();
        int powderCharge;
        try { powderCharge = PowderChargeBands.portions(nativePowder, rounds, SMAServerConfig.RELOADING_POWDER_BANDS.get()); }
        catch (IllegalArgumentException error) { return Optional.empty(); }
        var steps = new ArrayList<Ingredient>();
        boolean savesMaterial = false;
        var primerCopper = BigDecimal.ONE.divide(BigDecimal.valueOf(PrimerAllocation.CUPS_PER_NUGGET));
        for (var entry : totals.entrySet()) {
            BigDecimal allocated = entry.getValue();
            boolean copper = entry.getKey() == AllItems.COPPER_NUGGET.get();
            if (copper) allocated = allocated.subtract(primerCopper.multiply(BigDecimal.valueOf(rounds))).max(BigDecimal.ZERO);
            BigDecimal count = entry.getKey() == SentryRegistry.SMALL_GUNPOWDER.get() ? BigDecimal.valueOf(powderCharge)
                    : allocated.divide(BigDecimal.valueOf(rounds), 0, RoundingMode.CEILING);
            if (count.compareTo(BigDecimal.valueOf(MAX_MATERIAL_UNITS - steps.size())) > 0) return Optional.empty();
            BigDecimal actual = copper ? count.add(primerCopper) : count;
            savesMaterial |= actual.multiply(BigDecimal.valueOf(rounds)).compareTo(originalTotals.get(entry.getKey())) < 0;
            for (int i = 0; i < count.intValueExact(); i++) steps.add(Ingredient.of(entry.getKey()));
        }
        // Minimum one-item charges cannot economically represent every cheap native recipe.
        // Do not offer an automatic recipe that costs at least as much of EVERY material.
        return savesMaterial && CountedDeployerIngredient.compact(PrimerAllocation.fromAutomaticCost(steps)).size() + 1 <= MAX_ASSEMBLY_STEPS
                ? Optional.of(List.copyOf(steps)) : Optional.empty();
    }
}
