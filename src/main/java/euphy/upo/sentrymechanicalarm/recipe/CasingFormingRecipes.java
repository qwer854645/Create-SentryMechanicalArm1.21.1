package euphy.upo.sentrymechanicalarm.recipe;

import com.simibubi.create.AllItems;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.content.kinetics.deployer.*;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import euphy.upo.sentrymechanicalarm.SMAServerConfig;
import euphy.upo.sentrymechanicalarm.content.EmptyCasingItem;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.SentryCasingProfiles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import java.math.*;
import java.util.*;

/** New cases pay the remaining native main-metal budget; recovered cases avoid that cost. */
public final class CasingFormingRecipes {
    public static final String PREFIX = "casing_forming/";
    public static boolean enabled() { return SentryRegistry.CASING_MOLD.isBound() && SMAServerConfig.ENABLE_CASING_FORMING.get(); }
    public static ResourceLocation id(ResourceLocation ammo) { return ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", PREFIX + ammo.getNamespace() + "/" + ammo.getPath()); }
    public static Optional<RecipeHolder<?>> find(RecipeManager manager, ResourceLocation ammo) { return manager.byKey(id(ammo)); }
    public static List<ResourceLocation> choices(RecipeManager manager) {
        return manager.getRecipes().stream().filter(h -> h.id().getNamespace().equals("sentrymechanicalarm") && h.id().getPath().startsWith(PREFIX))
                .map(h -> EmptyCasingItem.ammoId(h.value().getResultItem(net.minecraft.core.RegistryAccess.EMPTY)))
                .filter(Objects::nonNull).distinct().sorted().toList();
    }
    public record Plan(Item metal, int yield) {}
    public static Optional<Plan> plan(GunSmithTableRecipe original) {
        var automatic=ReloadingCosts.derive(original,SMAServerConfig.RELOADING_METAL_FACTOR.get());
        return plan(original,automatic.map(CasingFormingRecipes::solidMetalCosts).orElse(Map.of()));
    }
    private static Map<Item,BigDecimal> solidMetalCosts(List<Ingredient> ingredients){
        var totals=new HashMap<Item,BigDecimal>();
        for(var ingredient:ingredients){
            var candidates=ingredient.getItems();if(candidates.length==0)return Map.of();
            Item metal=AmmoMetalCosts.canonical(candidates[0].getItem());int units=AmmoMetalCosts.units(candidates[0].getItem());
            if(Arrays.stream(candidates).anyMatch(s->AmmoMetalCosts.canonical(s.getItem())!=metal||AmmoMetalCosts.units(s.getItem())!=units))return Map.of();
            if(AmmoMetalCosts.isMetal(metal))totals.merge(metal,BigDecimal.valueOf((long)CountedDeployerIngredient.amount(ingredient)*units),BigDecimal::add);
        }
        return Map.copyOf(totals);
    }
    private static Optional<Plan> plan(GunSmithTableRecipe original,Map<Item,BigDecimal> reloading){
        if(original.getOutput().getCount()<1)return Optional.empty();
        var nativeMetals=AmmoMetalCosts.nativeTotals(original);var primary=AmmoMetalCosts.primary(nativeMetals);
        if(primary.isEmpty())return Optional.empty();
        Item metal=primary.get();var rounds=BigDecimal.valueOf(original.getOutput().getCount());
        // All main-metal cost omitted by rounding or discount belongs to a NEW case.
        var budget=nativeMetals.get(metal).subtract(reloading.getOrDefault(metal,BigDecimal.ZERO).multiply(rounds));
        // Explicitly expensive reloads cannot make manufacture free; conservatively price the whole native metal share.
        if(budget.signum()<=0)budget=nativeMetals.get(metal);
        Plan best=null;int bestUnits=0;
        for(var denomination:AmmoMetalCosts.denominations(metal)){
            var yield=BigDecimal.valueOf(denomination.units()).multiply(rounds).divide(budget,0,RoundingMode.FLOOR).min(BigDecimal.valueOf(64));
            if(yield.signum()<=0)continue;
            int count=yield.intValueExact();
            // Smallest overpayment; ties keep the smaller denomination. floor guarantees no underpricing.
            if(best==null||(long)denomination.units()*best.yield()<(long)bestUnits*count){best=new Plan(denomination.item(),count);bestUnits=denomination.units();}
        }
        return Optional.ofNullable(best);
    }
    private static Optional<Map<Item,BigDecimal>> actualReloading(RecipeManager manager,ResourceLocation ammo){
        var holder=ReloadingRecipes.find(manager,ammo);if(holder.isEmpty())return Optional.empty();
        var sequence=(com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe)holder.get().value();
        var materials=sequence.getSequence().stream().filter(step->step.getRecipe() instanceof DeployerApplicationRecipe)
                .map(step->step.getRecipe().getIngredients().get(1)).toList();
        return Optional.of(solidMetalCosts(materials));
    }
    public static List<RecipeHolder<?>> build(RecipeManager manager) {
        if (!enabled()) return List.of();
        var result = new ArrayList<RecipeHolder<?>>();
        var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        var inputs = net.minecraft.core.NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.STICK));
        var moldId=ResourceLocation.parse("sentrymechanicalarm:casing_mold");
        if(!ManufacturingRules.nativeRecipePresent(manager,server,moldId))result.add(new RecipeHolder<>(moldId,new ShapelessRecipe("",CraftingBookCategory.MISC,new ItemStack(SentryRegistry.CASING_MOLD.get()),inputs)));
        var sources = new LinkedHashMap<ResourceLocation, List<GunSmithTableRecipe>>();
        for (var holder : AmmoMaterialSources.collect(manager,net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer())) {
            var original = holder.value();
            var api = IAmmo.getIAmmoOrNull(original.getOutput()); if (api == null) continue;
            var ammo = api.getAmmoId(original.getOutput());
            sources.computeIfAbsent(ammo, key -> new ArrayList<>()).add(original);
        }
        int nativeCount = 0, fallbackCount = 0, customCount=0;
        for (var ammo : SentryCasingProfiles.eligible().stream().sorted().toList()) {
            if(ManufacturingRules.nativeRecipePresent(manager,server,id(ammo)))continue;
            var custom=ManufacturingRules.section(server,ammo,"forming",null);
            if(custom.present()){
                if(custom.valid())try{
                    var json=custom.json();ManufacturingRules.requireKeys(json,Set.of("enabled","input","output_count"));
                    if(!ManufacturingRules.enabled(json))continue;
                    if(!json.has("input"))throw new IllegalArgumentException("forming.input is required");
                    if(json.get("input").isJsonObject()&&json.getAsJsonObject("input").has("count"))throw new IllegalArgumentException("Forming consumes one input item; input.count is unsupported");
                    var input=Ingredient.CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE),json.get("input")).getOrThrow();
                    if(input.getItems().length==0||input.test(ItemStack.EMPTY)||Arrays.stream(input.getItems()).anyMatch(stack->stack.isEmpty()||stack.getCount()!=1))throw new IllegalArgumentException("Forming input must represent one nonempty item");
                    int count=json.has("output_count")?json.get("output_count").getAsBigDecimal().intValueExact():1;
                    if(count<1||count>64)throw new IllegalArgumentException("forming.output_count must be 1..64");
                    var recipe=new ItemApplicationRecipe.Builder<>(DeployerApplicationRecipe::new,id(ammo)).require(input).require(new MoldIngredient(ammo)).output(EmptyCasingItem.create(ammo,count)).toolNotConsumed().build();
                    result.add(new RecipeHolder<>(id(ammo),recipe));customCount++;
                }catch(Exception error){euphy.upo.sentrymechanicalarm.SentryMechanicalArm.LOGGER.warn("Invalid forming rule {}: {}; no automatic fallback",custom.source(),error.toString());}
                continue;
            }
            var originals = sources.getOrDefault(ammo, List.of());
            // Use the same automatic source, and deduct the actual active recipe rather than a theoretical discount.
            var understood=originals.stream().filter(r->ReloadingCosts.derive(r,SMAServerConfig.RELOADING_METAL_FACTOR.get()).isPresent()).findFirst();
            var source=understood.isPresent()?understood:originals.stream().filter(r->!AmmoMetalCosts.nativeTotals(r).isEmpty()).findFirst();
            var actual=actualReloading(manager,ammo);
            Optional<Plan> plan=source.flatMap(original->actual.isPresent()?plan(original,actual.get()):plan(original));
            if(plan.isPresent())nativeCount++;
            else if(source.isEmpty()&&SMAServerConfig.GENERIC_CASING_FORMING.get()){
                plan=Optional.of(new Plan(AllItems.COPPER_NUGGET.get(),SMAServerConfig.DEFAULT_CASING_YIELD.get()));fallbackCount++;
            }else continue;
            var p = plan.get();
            var recipe = new ItemApplicationRecipe.Builder<>(DeployerApplicationRecipe::new, id(ammo))
                    .require(p.metal()).require(new MoldIngredient(ammo)).output(EmptyCasingItem.create(ammo, p.yield())).toolNotConsumed().build();
            result.add(new RecipeHolder<>(id(ammo), recipe));
        }
        euphy.upo.sentrymechanicalarm.SentryMechanicalArm.LOGGER.info("Casing manufacture: {} custom, {} native metal budgets, {} generic copper rules",customCount, nativeCount, fallbackCount);
        return List.copyOf(result);
    }
}
