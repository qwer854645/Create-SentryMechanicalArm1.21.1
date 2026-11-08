package euphy.upo.sentrymechanicalarm.recipe;

import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.ArrayList;
import java.util.List;

/** Reserves a powder charge for the universal primer; its copper housing is budgeted before rounding. */
public final class PrimerAllocation {
    public static final int CUPS_PER_NUGGET = 8;
    private PrimerAllocation() {}

    public static List<Ingredient> fromAutomaticCost(List<Ingredient> materials) {
        var powder = new ItemStack(SentryRegistry.SMALL_GUNPOWDER.get());
        var result = new ArrayList<Ingredient>();
        result.add(Ingredient.of(SentryRegistry.PRIMER.get()));
        boolean reserved = false;
        for (var ingredient : materials) {
            if (!ingredient.test(powder)) continue;
            if (!reserved) reserved = true;
            else result.add(ingredient);
        }
        if (!reserved) throw new IllegalArgumentException("Automatic primer allocation requires a powder charge");
        // Install primer, charge remaining powder, then add the original metal/paper materials.
        for (var ingredient : materials) if (!ingredient.test(powder)) result.add(ingredient);
        return List.copyOf(result);
    }
}
