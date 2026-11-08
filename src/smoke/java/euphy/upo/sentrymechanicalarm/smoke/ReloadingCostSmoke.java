package euphy.upo.sentrymechanicalarm.smoke;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import euphy.upo.sentrymechanicalarm.SMAServerConfig;
import euphy.upo.sentrymechanicalarm.recipe.*;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import java.util.*;

import static euphy.upo.sentrymechanicalarm.smoke.ReloadingSmoke.*;

final class ReloadingCostSmoke {
    static Map<Item, Integer> counts(List<Ingredient> steps) {
        Map<Item, Integer> result = new HashMap<>();
        for (var ingredient : steps) result.merge(ingredient.getItems()[0].getItem(), 1, Integer::sum);
        return result;
    }
    static GunSmithTableRecipe original(String ammo) {
        var id = ResourceLocation.parse(ammo);
        return level.getRecipeManager().getRecipes().stream().map(RecipeHolder::value)
                .filter(GunSmithTableRecipe.class::isInstance).map(GunSmithTableRecipe.class::cast)
                .filter(r -> {var api = IAmmo.getIAmmoOrNull(r.getOutput()); return api != null && id.equals(api.getAmmoId(r.getOutput()));})
                .findFirst().orElseThrow();
    }
    static GunSmithTableRecipe synthetic(int rounds, GunSmithTableIngredient... inputs) {
        var base = original("tacz:9mm");
        return new GunSmithTableRecipe(base.getResult(), List.of(inputs)) {
            @Override public ItemStack getOutput() { return base.getOutput().copyWithCount(rounds); }
        };
    }
    static GunSmithTableIngredient material(Item item, int count) {
        return new GunSmithTableIngredient(Ingredient.of(item), count);
    }
    static void verify() {
        test("single-round 9mm and 5.56 costs use nuggets and fractional powder", () -> {
            check(counts(ReloadingCosts.derive(original("tacz:9mm"), .5).orElseThrow()).equals(
                    Map.of(AllItems.COPPER_NUGGET.get(), 1, SentryRegistry.SMALL_GUNPOWDER.get(), 2)), "wrong 9mm costs");
            check(counts(ReloadingCosts.derive(original("tacz:556x45"), .5).orElseThrow()).equals(
                    Map.of(AllItems.COPPER_NUGGET.get(), 2, SentryRegistry.SMALL_GUNPOWDER.get(), 3)), "wrong 5.56 costs");
            check(counts(ReloadingCosts.derive(original("tacz:12g"), .5).orElseThrow()).equals(
                    Map.of(AllItems.COPPER_NUGGET.get(), 4, Items.IRON_NUGGET, 1, SentryRegistry.SMALL_GUNPOWDER.get(), 11)), "wrong shotgun costs");
        });
        test("material splitting and ingot nugget mixtures round only after aggregation", () -> {
            var merged = synthetic(50, material(Items.IRON_INGOT, 10), material(Items.GUNPOWDER, 2));
            var split = synthetic(50, material(Items.IRON_INGOT, 5), material(Items.IRON_INGOT, 4),
                    material(Items.IRON_NUGGET, 9), material(Items.GUNPOWDER, 1), material(Items.GUNPOWDER, 1));
            check(counts(ReloadingCosts.derive(merged, .5).orElseThrow()).equals(
                    counts(ReloadingCosts.derive(split, .5).orElseThrow())), "split entries inflate costs");
        });
        test("complex materials invalid factors and excessive costs are rejected", () -> {
            check(ReloadingCosts.derive(synthetic(1, material(Items.DIAMOND, 1), material(Items.GUNPOWDER, 1)), .5).isEmpty(), "guessed complex cost");
            check(ReloadingCosts.derive(synthetic(1, material(Items.GUNPOWDER, Integer.MAX_VALUE)), .5).isEmpty(), "overflow or step budget bypass");
            for (double factor : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, 2})
                check(ReloadingCosts.derive(original("tacz:9mm"), factor).isEmpty(), "accepted invalid factor");
        });
        test("rounding cannot create a strictly uneconomic automatic recipe", () -> {
            check(ReloadingCosts.derive(synthetic(100, material(Items.COPPER_INGOT, 10), material(Items.GUNPOWDER, 2)), .5).isEmpty(), "one nugget and one portion would exceed both native costs");
            check(ReloadingCosts.derive(original("tacz:9mm"), 1).isEmpty(), "no-saving cost should require manual override");
        });
        test("powder crafting and milling each output exactly 32 portions", () -> {
            var crafting = level.getRecipeManager().byKey(ResourceLocation.parse("sentrymechanicalarm:small_gunpowder")).orElseThrow();
            var input = CraftingInput.of(1, 1, List.of(new ItemStack(Items.GUNPOWDER)));
            @SuppressWarnings("unchecked") var craft = (Recipe<CraftingInput>) crafting.value();
            check(craft.matches(input, level), "split crafting does not match");
            var result = craft.assemble(input, level.registryAccess());
            check(result.is(SentryRegistry.SMALL_GUNPOWDER.get()) && result.getCount() == 32, "wrong powder split yield");
            var milling = (com.simibubi.create.content.kinetics.millstone.MillingRecipe) level.getRecipeManager()
                    .byKey(ResourceLocation.parse("sentrymechanicalarm:milling_small_gunpowder")).orElseThrow().value();
            var inv = new net.neoforged.neoforge.items.ItemStackHandler(1); inv.setStackInSlot(0, new ItemStack(Items.GUNPOWDER));
            check(milling.matches(new net.neoforged.neoforge.items.wrapper.RecipeWrapper(inv), level), "milling does not match");
            var milled = milling.rollResults(level.random);
            check(milled.size() == 1 && ItemStack.matches(result, milled.getFirst()), "milling and crafting yields differ");
        });
        test("metal configuration changes recipe identity and preserves native crafting", () -> {
            var ammo = ResourceLocation.parse("tacz:556x45");
            var old = ReloadingRecipes.find(level.getRecipeManager(), ammo).orElseThrow();
            double previous = SMAServerConfig.RELOADING_METAL_FACTOR.get();
            try {
                SMAServerConfig.RELOADING_METAL_FACTOR.set(.25);
                DynamicRecipeManager.rebuild(level.getRecipeManager());
                var changed = ReloadingRecipes.find(level.getRecipeManager(), ammo).orElseThrow();
                check(!changed.id().equals(old.id()) && level.getRecipeManager().byKey(old.id()).isEmpty(), "old cost ID retained");
                var seq = (SequencedAssemblyRecipe) changed.value();
                check(seq.getSequence().size() == 5, "quarter-metal 5.56 cost should be 1 primer + 2 powder + 1 nugget + pressing");
                check(original("tacz:9mm").getOutput().getCount() == 50, "native crafting was changed");
            } finally {
                SMAServerConfig.RELOADING_METAL_FACTOR.set(previous);
                DynamicRecipeManager.rebuild(level.getRecipeManager());
            }
            check(ReloadingRecipes.find(level.getRecipeManager(), ammo).orElseThrow().id().equals(old.id()), "restored costs have unstable ID");
        });
    }
}
