package euphy.upo.sentrymechanicalarm.recipe;

import com.google.gson.*;
import com.tacz.guns.api.TimelessAPI;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeManager;
import java.util.*;

/** Per-ammo data-pack rules. A present invalid/disabled rule never falls back to automatic costs. */
public final class ManufacturingRules {
    private ManufacturingRules() {}
    private record Entry(JsonObject json,String source,boolean valid) {}
    public record Section(boolean present,boolean valid,JsonObject json,String source) {}
    private static volatile Map<ResourceLocation,Entry> entries=Map.of();
    private static Section absent(){return new Section(false,true,new JsonObject(),"");}
    public static void clear(){entries=Map.of();}
    public static void reload(MinecraftServer server){
        var loaded=new HashMap<ResourceLocation,Entry>();
        for(var ammo:TimelessAPI.getAllCommonAmmoIndex()){
            var id=ammo.getKey();var location=ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"sentry_manufacturing/"+id.getPath()+".json");
            var resource=server.getResourceManager().getResource(location);if(resource.isEmpty())continue;
            try(var reader=resource.get().openAsReader()){
                var json=JsonParser.parseReader(reader).getAsJsonObject();
                requireKeys(json,Set.of("enabled","reloading","forming","drops"));enabled(json);
                loaded.put(id,new Entry(json,location.toString(),true));
            }catch(Exception error){
                loaded.put(id,new Entry(new JsonObject(),location.toString(),false));
                SentryMechanicalArm.LOGGER.warn("Invalid manufacturing rule {}: {}; managed rules disabled",location,error.getMessage());
            }
        }
        entries=Map.copyOf(loaded);
    }
    public static Optional<Boolean> casingOverride(ResourceLocation ammo){
        var entry=entries.get(ammo);if(entry==null)return Optional.empty();
        if(!entry.valid())return Optional.of(false);
        return entry.json().has("enabled")?Optional.of(enabled(entry.json())):Optional.empty();
    }
    public static Section section(MinecraftServer server,ResourceLocation ammo,String name,String legacyDirectory){
        var entry=entries.get(ammo);
        if(entry!=null){
            if(!entry.valid())return new Section(true,false,new JsonObject(),entry.source());
            if(!enabled(entry.json())){var disabled=new JsonObject();disabled.addProperty("enabled",false);return new Section(true,true,disabled,entry.source());}
            if(entry.json().has(name)){
                var raw=entry.json().get(name);
                if(!raw.isJsonObject()){
                    SentryMechanicalArm.LOGGER.warn("Invalid {} section in {}: expected object",name,entry.source());
                    return new Section(true,false,new JsonObject(),entry.source());
                }
                return new Section(true,true,raw.getAsJsonObject().deepCopy(),entry.source()+"#"+name);
            }
        }
        if(legacyDirectory==null||server==null)return absent();
        var location=ResourceLocation.fromNamespaceAndPath(ammo.getNamespace(),legacyDirectory+"/"+ammo.getPath()+".json");
        var resource=server.getResourceManager().getResource(location);if(resource.isEmpty())return absent();
        try(var reader=resource.get().openAsReader()){
            return new Section(true,true,JsonParser.parseReader(reader).getAsJsonObject(),location.toString());
        }catch(Exception error){
            SentryMechanicalArm.LOGGER.warn("Invalid manufacturing rule {}: {}; no fallback",location,error.getMessage());
            return new Section(true,false,new JsonObject(),location.toString());
        }
    }
    public static boolean enabled(JsonObject json){
        if(!json.has("enabled"))return true;
        if(!json.get("enabled").isJsonPrimitive()||!json.getAsJsonPrimitive("enabled").isBoolean())throw new IllegalArgumentException("enabled must be a boolean");
        return json.get("enabled").getAsBoolean();
    }
    public static void requireKeys(JsonObject json,Set<String> allowed){
        for(String key:json.keySet())if(!allowed.contains(key))throw new IllegalArgumentException("Unknown field: "+key);
    }
    /** Native canonical data-pack recipes, including conditional/invalid resources, are authoritative. */
    public static boolean nativeRecipePresent(RecipeManager manager,MinecraftServer server,ResourceLocation id){
        return manager.byKey(id).isPresent()||server!=null&&server.getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"recipe/"+id.getPath()+".json")).isPresent();
    }
}
