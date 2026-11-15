package euphy.upo.sentrymechanicalarm.recipe;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import euphy.upo.sentrymechanicalarm.SMAServerConfig;
import euphy.upo.sentrymechanicalarm.content.AttachmentBaseItem;
import euphy.upo.sentrymechanicalarm.registry.GunParts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import java.util.*;
import static euphy.upo.sentrymechanicalarm.recipe.GunManufacturingRecipes.*;

/** One route per attachment identity; native batch sizes and full output data are retained. */
public final class AttachmentAssemblyRecipes {
    public record Result(List<RecipeHolder<?>> recipes,Set<ResourceLocation> replacedNative) {}
    public static Result build(RecipeManager manager,MinecraftServer server){
        if(server==null||!SMAServerConfig.ATTACHMENT_MANUFACTURING.get())return new Result(List.of(),Set.of());
        var sources=new LinkedHashMap<ResourceLocation,List<RecipeHolder<?>>>();
        for(var holder:manager.getRecipes().stream().sorted(Comparator.comparing(RecipeHolder::id)).toList())
            if(holder.value() instanceof GunSmithTableRecipe nativeRecipe){
                var api=IAttachment.getIAttachmentOrNull(nativeRecipe.getOutput());
                if(api!=null)sources.computeIfAbsent(api.getAttachmentId(nativeRecipe.getOutput()),id->new ArrayList<>()).add(holder);
            }
        var recipes=new ArrayList<RecipeHolder<?>>();var replaced=new HashSet<ResourceLocation>();
        for(var entry:TimelessAPI.getAllCommonAttachmentIndex().stream().sorted(Map.Entry.comparingByKey()).toList()){
            var id=entry.getKey();var index=entry.getValue();
            var canonical=ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm",PREFIX+"attachments/"+id.getNamespace()+"/"+id.getPath());
            var resource=ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"sentry_attachments/"+id.getPath()+".json");
            if(ManufacturingRules.nativeRecipePresent(manager,server,canonical))continue;
            try{
                var rule=read(server,resource);var candidates=sources.getOrDefault(id,List.of());
                if(rule==null&&(!SMAServerConfig.AUTO_ATTACHMENT_MANUFACTURING.get()||candidates.isEmpty()
                        ||index.getPojo().isHidden()||index.getPojo().isBuiltinAttachment()))continue;
                if(rule!=null){
                    ManufacturingRules.requireKeys(rule,Set.of("enabled","tier","component_tiers","component_counts","spring_count","fastener_count","input","materials","extra_materials","steps","output"));
                    if(!ManufacturingRules.enabled(rule))continue;
                    if(rule.has("steps")&&(rule.has("materials")||rule.has("extra_materials")))throw new IllegalArgumentException("steps replace all operations");
                    if((rule.has("component_counts")||rule.has("spring_count")||rule.has("fastener_count"))&&(rule.has("materials")||rule.has("steps")))throw new IllegalArgumentException("Automatic count overrides cannot combine with materials/steps");
                }
                if(index.getType()==com.tacz.guns.api.item.attachment.AttachmentType.NONE)throw new IllegalArgumentException("Attachment type NONE is not manufacturable");
                GunSmithTableRecipe original=candidates.isEmpty()?null:(GunSmithTableRecipe)candidates.getFirst().value();
                var output=original==null?AttachmentItemBuilder.create().setId(id).build():original.getOutput().copy();
                if(rule!=null&&rule.has("output"))output=ItemStack.CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE),rule.get("output")).getOrThrow();
                var api=IAttachment.getIAttachmentOrNull(output);
                if(api==null||!id.equals(api.getAttachmentId(output))||output.getCount()<1||output.getCount()>64)
                    throw new IllegalArgumentException("Output must contain 1..64 matching attachments");
                boolean explicitMaterials=rule!=null&&(rule.has("materials")||rule.has("steps"));
                if(original!=null&&!explicitMaterials&&output.getCount()!=original.getOutput().getCount())throw new IllegalArgumentException("Changing native batch yield requires complete materials/steps");
                JsonObject display=null;
                if(index.getType()==com.tacz.guns.api.item.attachment.AttachmentType.SCOPE)try{
                    display=euphy.upo.sentrymechanicalarm.util.SentryGunProfiles.asset(server.getResourceManager().listPacks().toList(),index.getPojo().getDisplay(),"display/attachments/");
                }catch(Exception error){euphy.upo.sentrymechanicalarm.SentryMechanicalArm.LOGGER.warn("Attachment optics metadata unavailable for {}; using basic optics: {}",id,error.toString());}
                var requirements=AttachmentComponentRequirements.derive(index.getType(),index.getData(),display);
                var grades=new LinkedHashMap<>(requirements.grades());var counts=new LinkedHashMap<>(requirements.counts());
                int springs=rule!=null&&rule.has("spring_count")?integer(rule.get("spring_count"),0,64):requirements.springs();
                int fasteners=rule!=null&&rule.has("fastener_count")?integer(rule.get("fastener_count"),0,64):1;
                if(rule!=null&&rule.has("tier")){
                    var grade=tier(rule.get("tier"));for(String family:grades.keySet())grades.put(family,grade);
                }
                if(rule!=null&&rule.has("component_tiers")){
                    var values=rule.getAsJsonObject("component_tiers");ManufacturingRules.requireKeys(values,Set.copyOf(GunParts.allFamilies()));
                    for(var value:values.entrySet())grades.put(value.getKey(),tier(value.getValue()));
                }
                if(rule!=null&&rule.has("component_counts")){
                    var values=rule.getAsJsonObject("component_counts");ManufacturingRules.requireKeys(values,Set.copyOf(GunParts.allFamilies()));
                    for(var value:values.entrySet())counts.put(value.getKey(),value.getKey().equals("attachment_base")?integer(value.getValue(),1,1):integer(value.getValue(),0,64));
                }
                Ingredient base=Ingredient.of(GunParts.item(grades.get("attachment_base").component("attachment_base")));
                if(rule!=null&&rule.has("input"))base=ingredient(server,rule.get("input"));
                if(!GunAssemblyRecipes.validReceiver(base)||Arrays.stream(base.getItems()).anyMatch(s->!(s.getItem() instanceof AttachmentBaseItem)))throw new IllegalArgumentException("input must match single attachment bases");
                int batch=output.getCount();var costs=new ArrayList<Ingredient>();
                for(String family:GunParts.allFamilies())if(!family.equals("attachment_base")&&counts.get(family)>0)
                    repeat(costs,Ingredient.of(GunParts.item(grades.get(family).component(family))),Math.multiplyExact(counts.get(family),batch));
                if(fasteners>0)repeat(costs,Ingredient.of(GunParts.item("gun_fastener")),Math.multiplyExact(fasteners,batch));
                if(springs>0)repeat(costs,Ingredient.of(GunParts.item("gun_spring")),Math.multiplyExact(springs,batch));
                if(original!=null&&!explicitMaterials)for(var material:original.getInputs()){
                    var samples=material.getIngredient().getItems();
                    if(samples.length==0)throw new IllegalArgumentException("Unresolved native material");
                    boolean ordinary=Arrays.stream(samples).allMatch(s->!s.isEmpty()&&s.getComponentsPatch().isEmpty()
                            &&(AmmoMetalCosts.isMetal(s.getItem())||s.is(Items.LAPIS_LAZULI)||s.is(ItemTags.LOGS)||s.is(ItemTags.PLANKS)));
                    if(!ordinary)repeat(costs,material.getIngredient(),material.getCount());
                }
                if(rule!=null){
                    if(rule.has("materials"))costs=new ArrayList<>(materials(server,rule.getAsJsonArray("materials")));
                    if(rule.has("extra_materials"))costs.addAll(materials(server,rule.getAsJsonArray("extra_materials")));
                }
                // The selected input pays for the first base. Automatic batches pay the rest here.
                if(!explicitMaterials&&batch>1)repeat(costs,base,batch-1);
                var holder=gunSequence(server,canonical,base,costs,output,rule!=null&&rule.has("steps")?rule.getAsJsonArray("steps"):null);
                var sequence=(SequencedAssemblyRecipe)holder.value();
                var start=(GunAssemblyIngredient)sequence.getIngredient().getCustomIngredient();
                var copy=GunAssemblyCopying.recipe(new GunAssemblyRecipes.Choice(holder.id(),id,start,output,sequence),ItemStack.EMPTY);
                recipes.add(holder);recipes.add(copy);
                // Never remove a variant or a different batch output that this route does not reproduce.
                for(var candidate:candidates)if(ItemStack.matches(((GunSmithTableRecipe)candidate.value()).getOutput(),output))replaced.add(candidate.id());
            }catch(Exception error){reject(resource,error);}
        }
        euphy.upo.sentrymechanicalarm.SentryMechanicalArm.LOGGER.info("Attachment manufacturing: {} sequences/copy recipes, {} replaceable native recipes",recipes.size(),replaced.size());
        return new Result(List.copyOf(recipes),Set.copyOf(replaced));
    }
}
