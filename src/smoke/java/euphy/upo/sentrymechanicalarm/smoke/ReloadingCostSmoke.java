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
        for (var ingredient : steps) result.merge(ingredient.getItems()[0].getItem(), CountedDeployerIngredient.amount(ingredient), Integer::sum);
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
                    Map.of(AllItems.COPPER_NUGGET.get(), 1, SentryRegistry.SMALL_GUNPOWDER.get(), 1)), "wrong 9mm costs");
            check(counts(ReloadingCosts.derive(original("tacz:556x45"), .5).orElseThrow()).equals(
                    Map.of(AllItems.COPPER_NUGGET.get(), 2, SentryRegistry.SMALL_GUNPOWDER.get(), 2)), "wrong 5.56 costs");
            check(counts(ReloadingCosts.derive(original("tacz:12g"), .5).orElseThrow()).equals(
                    Map.of(AllItems.COPPER_NUGGET.get(), 4, Items.IRON_NUGGET, 1, SentryRegistry.SMALL_GUNPOWDER.get(), 4)), "wrong shotgun costs");
        });
        test("material splitting and ingot nugget mixtures round only after aggregation", () -> {
            var merged = synthetic(50, material(Items.IRON_INGOT, 10), material(Items.GUNPOWDER, 2));
            var split = synthetic(50, material(Items.IRON_INGOT, 5), material(Items.IRON_INGOT, 4),
                    material(Items.IRON_NUGGET, 9), material(Items.GUNPOWDER, 1), material(Items.GUNPOWDER, 1));
            check(counts(ReloadingCosts.derive(merged, .5).orElseThrow()).equals(
                    counts(ReloadingCosts.derive(split, .5).orElseThrow())), "split entries inflate costs");
        });
        test("complex materials invalid factors and excessive costs are rejected", () -> {
            check(counts(ReloadingCosts.derive(synthetic(1, material(Items.DIAMOND, 1), material(Items.GUNPOWDER, 1)), .5).orElseThrow()).get(Items.DIAMOND)==1, "solid payload was discarded or discounted");
            check(ReloadingCosts.derive(synthetic(1, material(Items.COPPER_INGOT, Integer.MAX_VALUE), material(Items.GUNPOWDER, 1)), .5).isEmpty(), "overflow or step budget bypass");
            for (double factor : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, 2})
                check(ReloadingCosts.derive(original("tacz:9mm"), factor).isEmpty(), "accepted invalid factor");
        });
        test("rounding cannot create a strictly uneconomic automatic recipe", () -> {
            check(ReloadingCosts.derive(synthetic(100, material(Items.COPPER_INGOT, 10), material(Items.GUNPOWDER, 2)), .5).isEmpty(), "one nugget and one portion would exceed both native costs");
            check(counts(ReloadingCosts.derive(original("tacz:9mm"), 1).orElseThrow()).get(AllItems.COPPER_NUGGET.get())==2, "factor one incorrectly discounts metal");
        });
        test("all eligible native or legacy material sources reload except uneconomic tiny caliber", () -> {
            var eligible=euphy.upo.sentrymechanicalarm.util.SentryCasingProfiles.eligible();
            var missing=eligible.stream().filter(a->ReloadingRecipes.find(level.getRecipeManager(),a).isEmpty()).collect(java.util.stream.Collectors.toSet());
            check(missing.equals(Set.of(ResourceLocation.parse("tacz:22wmr"))),"unexplained missing reloading "+missing);
            check(eligible.size()==37,"unexpected test pack coverage");
            var nativeIds=level.getRecipeManager().getRecipes().stream().filter(h->h.value() instanceof GunSmithTableRecipe).map(RecipeHolder::id).collect(java.util.stream.Collectors.toSet());
            var all=AmmoMaterialSources.collect(level.getRecipeManager(),level.getServer());
            check(all.stream().filter(h->h.id().getPath().startsWith("legacy_materials/")).count()==10,"missing legacy sources");
            check(all.stream().filter(h->{var api=IAmmo.getIAmmoOrNull(h.value().getOutput());return api!=null&&ResourceLocation.parse("tacz:9mm").equals(api.getAmmoId(h.value().getOutput()));}).count()==1,"legacy duplicates modern recipe");
            boolean previous=SMAServerConfig.LEGACY_RELOADING_SOURCES.get();
            try{SMAServerConfig.LEGACY_RELOADING_SOURCES.set(false);DynamicRecipeManager.rebuild(level.getRecipeManager());
                check(ReloadingRecipes.find(level.getRecipeManager(),ResourceLocation.parse("re:40sw")).isEmpty(),"legacy switch ignored");
                check(ReloadingRecipes.find(level.getRecipeManager(),ResourceLocation.parse("tacz:338")).isPresent(),"legacy switch removed modern source");
            }finally{SMAServerConfig.LEGACY_RELOADING_SOURCES.set(previous);DynamicRecipeManager.rebuild(level.getRecipeManager());}
            check(nativeIds.equals(level.getRecipeManager().getRecipes().stream().filter(h->h.value() instanceof GunSmithTableRecipe).map(RecipeHolder::id).collect(java.util.stream.Collectors.toSet())),"native gunpack recipes modified");
        });
        test("powder bands use exact inclusive boundaries and reject invalid tables", () -> {
            var rules=SMAServerConfig.RELOADING_POWDER_BANDS.get();
            for(var sample:Map.of("0.05",1,"0.0500001",2,"0.10",2,"0.100001",3,"0.20",3,"0.40",4,"0.80",5,"0.800001",6).entrySet())
                check(PowderChargeBands.portions(new java.math.BigDecimal(sample.getKey()),1,rules)==sample.getValue(),"wrong boundary "+sample.getKey());
            check(PowderChargeBands.portions(java.math.BigDecimal.valueOf(5),100,rules)==1,"ratio not normalized by output count");
            for(var invalid:List.of(List.of("0.1=1"),List.of("0.2=2","0.1=1","*=3"),List.of("*=1","0.1=2"),List.of("0.1=0","*=2"))){
                boolean rejected=false;try{PowderChargeBands.portions(java.math.BigDecimal.ONE,1,invalid);}catch(IllegalArgumentException expected){rejected=true;}
                check(rejected,"invalid bands accepted "+invalid);
            }
        });
        test("powder config rebuild changes recipe fingerprint and restores cleanly", () -> {
            var old=ReloadingRecipes.find(level.getRecipeManager(),ResourceLocation.parse("tacz:12g")).orElseThrow();
            var previous=SMAServerConfig.RELOADING_POWDER_BANDS.get();
            try{SMAServerConfig.RELOADING_POWDER_BANDS.set(List.of("*=2"));DynamicRecipeManager.rebuild(level.getRecipeManager());
                var changed=ReloadingRecipes.find(level.getRecipeManager(),ResourceLocation.parse("tacz:12g")).orElseThrow();
                check(!changed.id().equals(old.id())&&level.getRecipeManager().byKey(old.id()).isEmpty(),"band change retained old WIP identity");
            }finally{SMAServerConfig.RELOADING_POWDER_BANDS.set(previous);DynamicRecipeManager.rebuild(level.getRecipeManager());}
            check(ReloadingRecipes.find(level.getRecipeManager(),ResourceLocation.parse("tacz:12g")).orElseThrow().id().equals(old.id()),"band restore unstable");
        });
        test("payload and oversized metal groups keep amounts within stack capacity", () -> {
            var costs=counts(ReloadingCosts.derive(original("tacz:338"),.5).orElseThrow());
            check(costs.get(Items.LAPIS_LAZULI)==1&&costs.get(SentryRegistry.SMALL_GUNPOWDER.get())==5,"special payload or heavy band lost");
            var compact=CountedDeployerIngredient.compact(java.util.Collections.nCopies(135,Ingredient.of(AllItems.COPPER_NUGGET.get())));
            check(compact.size()==3&&CountedDeployerIngredient.amount(compact.get(0))==64&&CountedDeployerIngredient.amount(compact.get(1))==64&&CountedDeployerIngredient.amount(compact.get(2))==7,"oversized stack failed to split");
            check(ReloadingCosts.derive(synthetic(1,material(Items.MILK_BUCKET,1),material(Items.GUNPOWDER,1)),.5).isEmpty(),"container payload lost return semantics");
            check(ReloadingCosts.derive(synthetic(1,material(Items.IRON_PICKAXE,1),material(Items.GUNPOWDER,1)),.5).isEmpty(),"damageable payload accepted");
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
                check(seq.getSequence().size() == 4, "quarter-metal 5.56 cost should be 1 primer + 2 powder + 1 nugget + pressing");
                check(original("tacz:9mm").getOutput().getCount() == 50, "native crafting was changed");
            } finally {
                SMAServerConfig.RELOADING_METAL_FACTOR.set(previous);
                DynamicRecipeManager.rebuild(level.getRecipeManager());
            }
            check(ReloadingRecipes.find(level.getRecipeManager(), ammo).orElseThrow().id().equals(old.id()), "restored costs have unstable ID");
        });
    }
}
