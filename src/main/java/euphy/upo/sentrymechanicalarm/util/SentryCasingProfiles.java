package euphy.upo.sentrymechanicalarm.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tacz.guns.api.TimelessAPI;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.*;

/** Discover ammo identity from loaded gun packs; never requires client classes on the server. */
@EventBusSubscriber(modid=SentryMechanicalArm.MODID)
public final class SentryCasingProfiles {
    private static volatile Map<ResourceLocation,Boolean> profiles = Map.of();
    private static volatile Set<ResourceLocation> client=Set.of();
    private static volatile Map<ResourceLocation,CasingDropRule> drops=Map.of();
    private record EjectionPolicy(Map<ResourceLocation,ResourceLocation> guns, Set<ResourceLocation> overriddenAmmo) {
        private static EjectionPolicy empty(){return new EjectionPolicy(Map.of(),Set.of());}
    }
    private static volatile EjectionPolicy ejection=EjectionPolicy.empty();
    public static CasingDropRule drop(ResourceLocation ammo){return drops.getOrDefault(ammo,CasingDropRule.disabled());}
    /** Explicit ammo/drop rules bypass the automatic per-gun display requirement. */
    public static boolean allowsGun(ResourceLocation gun,ResourceLocation ammo){
        var policy=ejection;
        return policy.overriddenAmmo().contains(ammo)||ammo.equals(policy.guns().get(gun));
    }
    public static void receive(Set<ResourceLocation> ids){client=Set.copyOf(ids);}
    public static Set<ResourceLocation> clientSnapshot(){return client;}
    public static Set<ResourceLocation> eligible(){return profiles.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toUnmodifiableSet());}
    public static boolean creativeEligible(ResourceLocation id){return net.neoforged.fml.loading.FMLEnvironment.dist==net.neoforged.api.distmarker.Dist.CLIENT?client.contains(id):enabled(id);}
    public static boolean enabled(ResourceLocation id) { return profiles.getOrDefault(id,false); }
    public static Map<ResourceLocation,Boolean> snapshot() { return profiles; }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void start(ServerStartedEvent event) { rebuild(event.getServer()); }
    @SubscribeEvent(priority=EventPriority.HIGH) public static void sync(OnDatapackSyncEvent event) {
        // DynamicRecipeManager rebuilds profiles at HIGHEST before generating recipes.
        // Re-read neither gun assets nor rules here: synchronize that same snapshot.
        if(event.getPlayer()==null) SentryCasingDrops.clear();
        var packet=new euphy.upo.sentrymechanicalarm.network.SentryCasingProfilesPacket(eligible());
        if(event.getPlayer()!=null)net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(event.getPlayer(),packet);
        else for(var player:event.getPlayerList().getPlayers())net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,packet);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { profiles=Map.of();drops=Map.of();ejection=EjectionPolicy.empty();euphy.upo.sentrymechanicalarm.recipe.ManufacturingRules.clear();SentryCasingDrops.clear(); }
    public static void rebuild(MinecraftServer server) {
        if(!euphy.upo.sentrymechanicalarm.registry.SentryRegistry.EMPTY_CASING.isBound()){profiles=Map.of();drops=Map.of();ejection=EjectionPolicy.empty();euphy.upo.sentrymechanicalarm.recipe.ManufacturingRules.clear();return;}
        euphy.upo.sentrymechanicalarm.recipe.ManufacturingRules.reload(server);
        var resources=server.getResourceManager();var packs=resources.listPacks().toList();
        boolean forceAll=euphy.upo.sentrymechanicalarm.SMAServerConfig.FORCE_ALL_CASING_AMMO.get();
        var forced=euphy.upo.sentrymechanicalarm.SMAServerConfig.FORCED_CASING_AMMO.get().stream()
                .map(ResourceLocation::parse).collect(java.util.stream.Collectors.toSet());
        var ejectingGuns=new HashMap<ResourceLocation,ResourceLocation>();
        var ammoShells=new HashMap<ResourceLocation,Boolean>();
        for(var entry:TimelessAPI.getAllCommonGunIndex())try{
            var index=entry.getValue();var ammo=index.getGunData().getAmmoId();
            var ammoIndex=TimelessAPI.getCommonAmmoIndex(ammo);
            if(ammoIndex.isEmpty())continue;
            var display=SentryGunProfiles.asset(packs,index.getPojo().getDisplay(),"display/guns/");
            if(display==null||!display.has("shell")||!display.get("shell").isJsonObject())continue;
            // TaCZ requires a gun shell declaration. A complete gun-level pair wins;
            // otherwise ShellRender falls back to the entire ammo-level pair.
            boolean supported=hasShellAssets(display.getAsJsonObject("shell"));
            if(!supported){
                var cached=ammoShells.get(ammo);
                if(cached==null){
                    var ammoDisplay=SentryGunProfiles.asset(packs,ammoIndex.get().getPojo().getDisplay(),"display/ammo/");
                    cached=ammoDisplay!=null&&ammoDisplay.has("shell")&&ammoDisplay.get("shell").isJsonObject()
                            &&hasShellAssets(ammoDisplay.getAsJsonObject("shell"));
                    ammoShells.put(ammo,cached);
                }
                supported=cached;
            }
            if(supported)ejectingGuns.put(entry.getKey(),ammo);
        }catch(Exception error){SentryMechanicalArm.LOGGER.warn("Casing gun data rejected for {}: {}",entry.getKey(),error.toString());}
        var automaticAmmo=new HashSet<>(ejectingGuns.values());
        var overriddenAmmo=new HashSet<ResourceLocation>();
        Map<ResourceLocation,Boolean> loaded=new LinkedHashMap<>();
        for(var entry:TimelessAPI.getAllCommonAmmoIndex()) {
            var id=entry.getKey();boolean explicit=forceAll||forced.contains(id);
            boolean allow=explicit||automaticAmmo.contains(id);
            try {
                var override=resources.getResource(ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"sentry_casings/"+id.getPath()+".json"));
                if(override.isPresent())try(var reader=override.get().openAsReader()) {
                    var json=JsonParser.parseReader(reader).getAsJsonObject();allow=json.get("enabled").getAsBoolean();
                    explicit=allow;
                }
            } catch(Exception e) { allow=false;SentryMechanicalArm.LOGGER.warn("Casing rule rejected for {}: {}",id,e.toString()); }
            var unified=euphy.upo.sentrymechanicalarm.recipe.ManufacturingRules.casingOverride(id);
            if(unified.isPresent()){allow=unified.get();explicit=allow;}
            if(allow&&explicit)overriddenAmmo.add(id);
            loaded.put(id,allow);
        }
        for(var id:forced)if(!loaded.containsKey(id))SentryMechanicalArm.LOGGER.warn("Forced casing ammunition is not loaded: {}",id);
        var defaults=Map.copyOf(loaded);
        var dropRules=new HashMap<ResourceLocation,CasingDropRule>();
        for(var entry:TimelessAPI.getAllCommonAmmoIndex()){
            var id=entry.getKey();
            var rule=euphy.upo.sentrymechanicalarm.recipe.ManufacturingRules.section(server,id,"drops",null);
            var drop=CasingDropRule.automatic(id,defaults.getOrDefault(id,false));
            if(rule.present()){
                drop=CasingDropRule.disabled();
                if(rule.valid())try{drop=CasingDropRule.read(server,id,rule.json());}
                catch(Exception error){SentryMechanicalArm.LOGGER.warn("Invalid casing drop {}: {}; no automatic fallback",rule.source(),error.getMessage());}
                if(drop.enabled()&&drop.casing()!=null)loaded.put(drop.casing(),true);
                if(drop.enabled())overriddenAmmo.add(id);
            }
            dropRules.put(id,drop);
        }
        ejection=new EjectionPolicy(Map.copyOf(ejectingGuns),Set.copyOf(overriddenAmmo));
        profiles=Map.copyOf(loaded);drops=Map.copyOf(dropRules);
        SentryMechanicalArm.LOGGER.info("Casing discovery: {} ejecting guns, {} ammo entries, {} eligible",ejectingGuns.size(),loaded.size(),loaded.values().stream().filter(Boolean::booleanValue).count());
    }
    private static boolean hasShellAssets(JsonObject shell){
        for(String field:List.of("model","texture")){
            var value=shell.get(field);
            if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString()
                    ||value.getAsString().isBlank()||ResourceLocation.tryParse(value.getAsString())==null)return false;
        }
        return true;
    }
}
