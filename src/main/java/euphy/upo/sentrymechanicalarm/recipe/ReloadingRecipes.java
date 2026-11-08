package euphy.upo.sentrymechanicalarm.recipe;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipeBuilder;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
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
    public static Optional<RecipeHolder<?>> find(RecipeManager manager,ResourceLocation ammo){String prefix=id(ammo).getPath()+"/v_";return manager.getRecipes().stream().filter(h->h.id().getNamespace().equals(SentryMechanicalArm.MODID)&&h.id().getPath().startsWith(prefix)).findFirst();}
    private static JsonElement canonical(JsonElement value){
        if(value.isJsonObject()){var result=new JsonObject();value.getAsJsonObject().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->result.add(e.getKey(),canonical(e.getValue())));return result;}
        if(value.isJsonArray()){var result=new JsonArray();value.getAsJsonArray().forEach(e->result.add(canonical(e)));return result;}return value;
    }
    private static ResourceLocation versionedId(ResourceLocation ammo,Plan plan,net.minecraft.server.MinecraftServer server)throws Exception{
        var ops=server.registryAccess().createSerializationContext(JsonOps.INSTANCE);var json=new JsonObject();var inputs=new JsonArray();
        for(var ingredient:plan.steps())inputs.add(Ingredient.CODEC.encodeStart(ops,ingredient).getOrThrow());json.add("materials",inputs);json.add("output",ItemStack.CODEC.encodeStart(ops,plan.output()).getOrThrow());
        var digest=java.security.MessageDigest.getInstance("SHA-256").digest(canonical(json).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return ResourceLocation.fromNamespaceAndPath(SentryMechanicalArm.MODID,id(ammo).getPath()+"/v_"+java.util.HexFormat.of().formatHex(digest).substring(0,24));
    }
    private record Plan(List<Ingredient> steps,ItemStack output){}
    public static List<RecipeHolder<?>> build(RecipeManager manager){
        var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if(server==null||!enabled())return List.of();
        var recipes=manager.getRecipes().stream().sorted(Comparator.comparing(RecipeHolder::id)).map(RecipeHolder::value).filter(GunSmithTableRecipe.class::isInstance).map(GunSmithTableRecipe.class::cast).toList();
        var result=new ArrayList<RecipeHolder<?>>();
        for(var entry:TimelessAPI.getAllCommonAmmoIndex().stream().sorted(Map.Entry.comparingByKey()).toList()){
            var ammo=entry.getKey();if(!SentryCasingProfiles.enabled(ammo))continue;
            try{
                Plan plan=null;
                var override=server.getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath(ammo.getNamespace(),"sentry_reloading/"+ammo.getPath()+".json"));
                if(override.isPresent()){
                    try(var reader=override.get().openAsReader()){
                        var json=JsonParser.parseReader(reader).getAsJsonObject();
                        if(json.has("enabled")&&!json.get("enabled").getAsBoolean())continue;
                        var steps=new ArrayList<Ingredient>();
                        for(var raw:json.getAsJsonArray("materials")){
                            var material=raw.getAsJsonObject();int count=material.has("count")?material.get("count").getAsBigDecimal().intValueExact():1;
                            if(count<1||count>16||steps.size()+count>16)throw new IllegalArgumentException("Material count must total 1..16");
                            var ingredient=Ingredient.CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE),material.get("ingredient")).getOrThrow();
                            if(ingredient.getItems().length==0||ingredient.test(ItemStack.EMPTY)||Arrays.stream(ingredient.getItems()).anyMatch(ItemStack::isEmpty))throw new IllegalArgumentException("Empty material ingredient");
                            for(int i=0;i<count;i++)steps.add(ingredient);
                        }
                        if(steps.isEmpty())throw new IllegalArgumentException("Reloading cannot be free");
                        var nativeOutput=recipes.stream().map(GunSmithTableRecipe::getOutput).filter(stack->{var api=IAmmo.getIAmmoOrNull(stack);return api!=null&&ammo.equals(api.getAmmoId(stack));}).findFirst().map(stack->stack.copyWithCount(1)).orElseGet(()->com.tacz.guns.api.item.builder.AmmoItemBuilder.create().setId(ammo).setCount(1).build());
                        plan=new Plan(steps,nativeOutput);
                    }
                }else if(SMAServerConfig.AUTO_RELOADING.get()){
                    for(var recipe:recipes){var output=recipe.getOutput();var api=IAmmo.getIAmmoOrNull(output);if(api==null||!ammo.equals(api.getAmmoId(output)))continue;plan=fromRecipe(recipe);if(plan!=null)break;}
                }
                if(plan==null)continue;
                var builder=new SequencedAssemblyRecipeBuilder(versionedId(ammo,plan,server)).require(new CasingIngredient(ammo).toVanilla()).transitionTo(SentryRegistry.RELOADING_CASING.get()).addOutput(plan.output(),1).loops(1);
                for(var ingredient:plan.steps())builder.addStep(DeployerApplicationRecipe::new,rb->rb.require(ingredient));
                builder.addStep(PressingRecipe::new,rb->rb);
                var built=builder.build();
                ((euphy.upo.sentrymechanicalarm.mixin.SequencedAssemblyRecipeAccessor)(Object)built.value()).sentry$setTransition(new com.simibubi.create.content.processing.recipe.ProcessingOutput(SentryRegistry.RELOADING_CASING.get(),1,net.minecraft.core.component.DataComponentPatch.builder().set(euphy.upo.sentrymechanicalarm.registry.ModDataComponents.CASING_AMMO.get(),ammo).build(),1));
                // Programmatic builders keep the transition item as step-zero input. Accept
                // the spent case for the first step too; the outer ingredient still checks AmmoId.
                built.value().getSequence().getFirst().getRecipe().getIngredients().set(0,Ingredient.of(SentryRegistry.EMPTY_CASING.get(),SentryRegistry.RELOADING_CASING.get()));
                result.add(built);
            }catch(Exception error){SentryMechanicalArm.LOGGER.warn("Skipping reloading recipe for {}: {}",ammo,error.toString());}
        }
        SentryMechanicalArm.LOGGER.info("Reloading recipes: {} eligible plans",result.size());return result;
    }
    private static Plan fromRecipe(GunSmithTableRecipe recipe){
        return ReloadingCosts.derive(recipe, SMAServerConfig.RELOADING_METAL_FACTOR.get())
                .map(steps -> new Plan(PrimerAllocation.fromAutomaticCost(steps), recipe.getOutput().copyWithCount(1))).orElse(null);
    }
}
