package euphy.upo.sentrymechanicalarm.recipe;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipeBuilder;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import euphy.upo.sentrymechanicalarm.registry.SentryFluids;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.*;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import euphy.upo.sentrymechanicalarm.*;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.SentryCasingProfiles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import java.util.*;

/** One casing always becomes exactly one round; explicit data-pack costs are never rescaled. */
public final class ReloadingRecipes {
    public static boolean enabled(){return SentryRegistry.EMPTY_CASING.isBound()&&SentryRegistry.RELOADING_CASING.isBound()&&SMAServerConfig.ENABLE_RELOADING.get();}
    public static ResourceLocation id(ResourceLocation ammo){return ResourceLocation.fromNamespaceAndPath(SentryMechanicalArm.MODID,"casing_reloading/"+ammo.getNamespace()+"/"+ammo.getPath());}
    public static Optional<RecipeHolder<?>> find(RecipeManager manager,ResourceLocation ammo){
        var canonical=manager.byKey(id(ammo)).filter(h->h.value() instanceof com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe);
        if(canonical.isPresent())return canonical;
        String prefix=id(ammo).getPath()+"/v_";
        return manager.getRecipes().stream().filter(h->h.id().getNamespace().equals(SentryMechanicalArm.MODID)&&h.id().getPath().startsWith(prefix)
                &&h.value() instanceof com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe).sorted(Comparator.comparing(RecipeHolder::id)).findFirst();
    }
    private static JsonElement canonical(JsonElement value){
        if(value.isJsonObject()){var result=new JsonObject();value.getAsJsonObject().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->result.add(e.getKey(),canonical(e.getValue())));return result;}
        if(value.isJsonArray()){var result=new JsonArray();value.getAsJsonArray().forEach(e->result.add(canonical(e)));return result;}return value;
    }
    private static ResourceLocation versionedId(ResourceLocation ammo,Plan plan,net.minecraft.server.MinecraftServer server)throws Exception{
        var ops=server.registryAccess().createSerializationContext(JsonOps.INSTANCE);var json=new JsonObject();var inputs=new JsonArray();
        for(var ingredient:plan.steps())inputs.add(Ingredient.CODEC.encodeStart(ops,ingredient).getOrThrow());json.add("materials",inputs);json.add("output",ItemStack.CODEC.encodeStart(ops,plan.output()).getOrThrow());
        json.addProperty("propellant",plan.fluid());
        json.addProperty("assemblyFormat", "fluid-v2-primerless");
        var digest=java.security.MessageDigest.getInstance("SHA-256").digest(canonical(json).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return ResourceLocation.fromNamespaceAndPath(SentryMechanicalArm.MODID,id(ammo).getPath()+"/v_"+java.util.HexFormat.of().formatHex(digest).substring(0,24));
    }
    private record Plan(List<Ingredient> steps,ItemStack output,int fluid){}
    public static List<RecipeHolder<?>> build(RecipeManager manager){
        var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if(server==null||!enabled())return List.of();
        var recipes=AmmoMaterialSources.collect(manager,server).stream().map(RecipeHolder::value).toList();
        var result=new ArrayList<RecipeHolder<?>>();
        boolean automatic=SMAServerConfig.AUTO_RELOADING.get();
        for(var entry:TimelessAPI.getAllCommonAmmoIndex().stream().sorted(Map.Entry.comparingByKey()).toList()){
            var ammo=entry.getKey();if(!SentryCasingProfiles.enabled(ammo))continue;
            // Full native data-pack replacements have the highest recipe-source priority.
            if(ManufacturingRules.nativeRecipePresent(manager,server,id(ammo))||find(manager,ammo).isPresent())continue;
            var override=ManufacturingRules.section(server,ammo,"reloading","sentry_reloading");
            try{
                Plan plan=null;
                if(override.present()){
                    if(!override.valid())continue;
                    {
                        var json=override.json();
                        ManufacturingRules.requireKeys(json,Set.of("enabled","materials","propellant"));
                        if(!ManufacturingRules.enabled(json))continue;
                        var steps=new ArrayList<Ingredient>();
                        var materials=json.has("materials")?json.getAsJsonArray("materials"):new JsonArray();
                        for(var raw:materials){
                            var material=raw.getAsJsonObject();ManufacturingRules.requireKeys(material,Set.of("ingredient","count"));int count=material.has("count")?material.get("count").getAsBigDecimal().intValueExact():1;
                            if(count<1||count>ReloadingCosts.MAX_MATERIAL_UNITS||steps.size()+count>ReloadingCosts.MAX_MATERIAL_UNITS)throw new IllegalArgumentException("Material count must total 1..1024");
                            var ingredient=Ingredient.CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE),material.get("ingredient")).getOrThrow();
                            if(ingredient.getItems().length==0||ingredient.test(ItemStack.EMPTY)||Arrays.stream(ingredient.getItems()).anyMatch(ItemStack::isEmpty))throw new IllegalArgumentException("Empty material ingredient");
                            for(int i=0;i<count;i++)steps.add(ingredient);
                        }
                        int fluid=json.has("propellant")?json.get("propellant").getAsBigDecimal().intValueExact():0;
                        if(fluid<0||fluid>ReloadingCosts.MAX_ASSEMBLY_STEPS*PropellantAmounts.MAX_FILL)throw new IllegalArgumentException("Propellant must be 0..16000 mB");
                        if(steps.isEmpty()&&fluid==0)throw new IllegalArgumentException("Reloading cannot be free");
                        var nativeOutput=recipes.stream().map(GunSmithTableRecipe::getOutput).filter(stack->{var api=IAmmo.getIAmmoOrNull(stack);return api!=null&&ammo.equals(api.getAmmoId(stack));}).findFirst().map(stack->stack.copyWithCount(1)).orElseGet(()->com.tacz.guns.api.item.builder.AmmoItemBuilder.create().setId(ammo).setCount(1).build());
                        plan=new Plan(steps,nativeOutput,fluid);
                    }
                }else if(automatic){
                    for(var recipe:recipes){var output=recipe.getOutput();var api=IAmmo.getIAmmoOrNull(output);if(api==null||!ammo.equals(api.getAmmoId(output)))continue;plan=fromRecipe(recipe);if(plan!=null)break;}
                }
                if(plan==null){SentryMechanicalArm.LOGGER.debug("No automatic reloading cost for {}; requires a usable powder-bearing material recipe or explicit override",ammo);continue;}
                plan=new Plan(CountedDeployerIngredient.compact(plan.steps()),plan.output(),plan.fluid());
                if(plan.steps().size()+1+(plan.fluid()+PropellantAmounts.MAX_FILL-1)/PropellantAmounts.MAX_FILL>ReloadingCosts.MAX_ASSEMBLY_STEPS)throw new IllegalArgumentException("Too many assembly stages after grouping");
                var builder=new SequencedAssemblyRecipeBuilder(versionedId(ammo,plan,server)).require(new CasingIngredient(ammo).toVanilla()).transitionTo(SentryRegistry.RELOADING_CASING.get()).addOutput(plan.output(),1).loops(1);
                addFluidSteps(builder,plan.fluid());
                for(var ingredient:plan.steps())builder.addStep(DeployerApplicationRecipe::new,rb->rb.require(ingredient));
                builder.addStep(PressingRecipe::new,rb->rb);
                var built=builder.build();
                ((euphy.upo.sentrymechanicalarm.mixin.SequencedAssemblyRecipeAccessor)(Object)built.value()).sentry$setTransition(new com.simibubi.create.content.processing.recipe.ProcessingOutput(SentryRegistry.RELOADING_CASING.get(),1,net.minecraft.core.component.DataComponentPatch.builder().set(euphy.upo.sentrymechanicalarm.registry.ModDataComponents.CASING_AMMO.get(),ammo).build(),1));
                // Programmatic builders keep the transition item as step-zero input. Accept
                // the spent case for the first step too; the outer ingredient still checks AmmoId.
                built.value().getSequence().getFirst().getRecipe().getIngredients().set(0,Ingredient.of(SentryRegistry.EMPTY_CASING.get(),SentryRegistry.RELOADING_CASING.get()));
                result.add(built);
            }catch(Exception error){SentryMechanicalArm.LOGGER.warn("Skipping reloading recipe for {} ({}): {}; no automatic fallback",ammo,override.present()?override.source():"automatic",error.toString());}
        }
        SentryMechanicalArm.LOGGER.info("Reloading recipes: {} eligible plans",result.size());return result;
    }
    private static void addFluidSteps(SequencedAssemblyRecipeBuilder builder,int amount){
        while(amount>0){int batch=Math.min(amount,PropellantAmounts.MAX_FILL);
            builder.addStep(FillingRecipe::new,rb->rb.require(SentryFluids.PROPELLANT.get(),batch));amount-=batch;}
    }
    private static Plan fromRecipe(GunSmithTableRecipe recipe){
        return ReloadingCosts.deriveFluid(recipe, SMAServerConfig.RELOADING_METAL_FACTOR.get())
                .map(cost -> new Plan(cost.materials(),recipe.getOutput().copyWithCount(1),cost.propellant())).orElse(null);
    }
}
