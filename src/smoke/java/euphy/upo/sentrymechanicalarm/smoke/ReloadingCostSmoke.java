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
        test("proportional fluid rounds upward once and fractional metal is omitted", () -> {
            var tiny=ReloadingCosts.deriveFluid(original("tacz:22wmr"),.5).orElseThrow();
            check(tiny.materials().isEmpty()&&tiny.propellant()==20,"tiny ammo must use no direct metal and 20 mB");
            var pistol=ReloadingCosts.deriveFluid(original("tacz:9mm"),.5).orElseThrow();
            check(pistol.materials().isEmpty()&&pistol.propellant()==40,"wrong pistol fluid ratio");
            var rifle=ReloadingCosts.deriveFluid(original("tacz:556x45"),.5).orElseThrow();
            check(counts(rifle.materials()).equals(Map.of(AllItems.COPPER_NUGGET.get(),2))&&rifle.propellant()==67,"wrong rifle ratio");
            var shot=ReloadingCosts.deriveFluid(original("tacz:12g"),.5).orElseThrow();
            check(counts(shot.materials()).equals(Map.of(AllItems.COPPER_NUGGET.get(),4,Items.IRON_NUGGET,1))&&shot.propellant()==334,"projectile iron discounted or fluid rounded incorrectly");
        });
        test("equal metal and powder entries merge before rounding", () -> {
            var merged=synthetic(50,material(Items.IRON_INGOT,10),material(Items.GUNPOWDER,2));
            var split=synthetic(50,material(Items.IRON_INGOT,5),material(Items.IRON_INGOT,4),material(Items.IRON_NUGGET,9),material(Items.GUNPOWDER,1),material(Items.GUNPOWDER,1));
            var a=ReloadingCosts.deriveFluid(merged,.5).orElseThrow();var b=ReloadingCosts.deriveFluid(split,.5).orElseThrow();
            check(counts(a.materials()).equals(counts(b.materials()))&&a.propellant()==b.propellant(),"split entries inflate costs");
        });
        test("invalid factors excessive materials and non-solid payloads are rejected", () -> {
            check(ReloadingCosts.derive(synthetic(1,material(Items.COPPER_INGOT,Integer.MAX_VALUE),material(Items.GUNPOWDER,1)),.5).isEmpty(),"overflow material accepted");
            check(ReloadingCosts.derive(synthetic(1,material(Items.COPPER_INGOT,2),material(Items.GUNPOWDER,Integer.MAX_VALUE)),.5).isEmpty(),"overflow fluid accepted");
            for(double factor:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,2})check(ReloadingCosts.derive(original("tacz:9mm"),factor).isEmpty(),"invalid factor accepted");
            for(Item item:List.of(Items.MILK_BUCKET,Items.IRON_PICKAXE))check(ReloadingCosts.derive(synthetic(1,material(item,1),material(Items.GUNPOWDER,1)),.5).isEmpty(),"container or tool payload accepted");
        });
        test("all eligible test-pack ammo has a proportional recipe without changing native crafting", () -> {
            var eligible=euphy.upo.sentrymechanicalarm.util.SentryCasingProfiles.eligible();
            check(eligible.size()==37&&eligible.stream().allMatch(a->ReloadingRecipes.find(level.getRecipeManager(),a).isPresent()),"missing proportional recipe");
            var nativeIds=level.getRecipeManager().getRecipes().stream().filter(h->h.value() instanceof GunSmithTableRecipe).map(RecipeHolder::id).collect(java.util.stream.Collectors.toSet());
            boolean previous=SMAServerConfig.LEGACY_RELOADING_SOURCES.get(),restore=SMAServerConfig.RESTORE_LEGACY_AMMO_RECIPES.get();
            try{SMAServerConfig.RESTORE_LEGACY_AMMO_RECIPES.set(false);SMAServerConfig.LEGACY_RELOADING_SOURCES.set(false);DynamicRecipeManager.rebuild(level.getRecipeManager());
                check(ReloadingRecipes.find(level.getRecipeManager(),ResourceLocation.parse("re:40sw")).isEmpty(),"legacy switch ignored");
                check(ReloadingRecipes.find(level.getRecipeManager(),ResourceLocation.parse("tacz:338")).isPresent(),"modern source removed");
            }finally{SMAServerConfig.RESTORE_LEGACY_AMMO_RECIPES.set(restore);SMAServerConfig.LEGACY_RELOADING_SOURCES.set(previous);DynamicRecipeManager.rebuild(level.getRecipeManager());}
            check(nativeIds.equals(level.getRecipeManager().getRecipes().stream().filter(h->h.value() instanceof GunSmithTableRecipe).map(RecipeHolder::id).collect(java.util.stream.Collectors.toSet())),"native recipes changed");
        });
        test("solid payload is retained and large stacks split at capacity", () -> {
            var cost=ReloadingCosts.deriveFluid(original("tacz:338"),.5).orElseThrow();
            check(counts(cost.materials()).get(Items.LAPIS_LAZULI)==1&&cost.propellant()==445,"special payload lost");
            var compact=CountedDeployerIngredient.compact(Collections.nCopies(135,Ingredient.of(AllItems.COPPER_NUGGET.get())));
            check(compact.size()==3&&CountedDeployerIngredient.amount(compact.get(0))==64&&CountedDeployerIngredient.amount(compact.get(2))==7,"stack splitting lost quantity");
        });
        test("metal configuration refreshes fingerprints and can remove tiny direct metal", () -> {
            var ammo=ResourceLocation.parse("tacz:556x45");var old=ReloadingRecipes.find(level.getRecipeManager(),ammo).orElseThrow();double previous=SMAServerConfig.RELOADING_METAL_FACTOR.get();
            try{SMAServerConfig.RELOADING_METAL_FACTOR.set(.25);DynamicRecipeManager.rebuild(level.getRecipeManager());var changed=ReloadingRecipes.find(level.getRecipeManager(),ammo).orElseThrow();
                check(!changed.id().equals(old.id())&&level.getRecipeManager().byKey(old.id()).isEmpty(),"old recipe retained");
                check(((SequencedAssemblyRecipe)changed.value()).getSequence().size()==2,"fractional quarter-metal charge retained");
            }finally{SMAServerConfig.RELOADING_METAL_FACTOR.set(previous);DynamicRecipeManager.rebuild(level.getRecipeManager());}
            check(ReloadingRecipes.find(level.getRecipeManager(),ammo).orElseThrow().id().equals(old.id()),"restored fingerprint differs");
        });
    }
}
