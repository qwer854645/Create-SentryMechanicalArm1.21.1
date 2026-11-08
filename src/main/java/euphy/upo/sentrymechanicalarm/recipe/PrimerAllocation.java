package euphy.upo.sentrymechanicalarm.recipe;

import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.*;

/** Primer cup costs are reserved before metal rounding; primer fluid is paid only once. */
public final class PrimerAllocation {
    public static final int CUPS_PER_NUGGET = 8;
    private PrimerAllocation() {}
    public record FluidPlan(List<Ingredient> materials, int additionalFluid) {}
    public static List<Ingredient> fromAutomaticCost(List<Ingredient> solids) {
        var result = new ArrayList<Ingredient>();
        result.add(Ingredient.of(SentryRegistry.PRIMER.get())); result.addAll(solids);
        return List.copyOf(result);
    }
    public static FluidPlan forFluidAssembly(ReloadingCosts.Cost cost) {
        if (cost.propellant() < PropellantAmounts.PER_PRIMER) throw new IllegalArgumentException("Missing primer fluid budget");
        return new FluidPlan(fromAutomaticCost(cost.materials()), cost.propellant() - PropellantAmounts.PER_PRIMER);
    }
}
