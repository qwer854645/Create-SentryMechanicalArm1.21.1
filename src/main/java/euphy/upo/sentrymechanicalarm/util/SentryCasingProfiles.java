package euphy.upo.sentrymechanicalarm.util;

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
    public static CasingDropRule drop(ResourceLocation ammo){return drops.getOrDefault(ammo,CasingDropRule.disabled());}
    public static void receive(Set<ResourceLocation> ids){client=Set.copyOf(ids);}
    public static Set<ResourceLocation> clientSnapshot(){return client;}
    public static Set<ResourceLocation> eligible(){return profiles.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toUnmodifiableSet());}
    public static boolean creativeEligible(ResourceLocation id){return net.neoforged.fml.loading.FMLEnvironment.dist==net.neoforged.api.distmarker.Dist.CLIENT?client.contains(id):enabled(id);}
    public static boolean enabled(ResourceLocation id) { return profiles.getOrDefault(id,false); }
    public static Map<ResourceLocation,Boolean> snapshot() { return profiles; }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void start(ServerStartedEvent event) { rebuild(event.getServer()); }
    @SubscribeEvent(priority=EventPriority.HIGH) public static void sync(OnDatapackSyncEvent event) {
        if(event.getPlayer()==null) { SentryCasingDrops.clear(); rebuild(event.getPlayerList().getServer()); }
        var packet=new euphy.upo.sentrymechanicalarm.network.SentryCasingProfilesPacket(eligible());
        if(event.getPlayer()!=null)net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(event.getPlayer(),packet);
        else for(var player:event.getPlayerList().getPlayers())net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,packet);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { profiles=Map.of();drops=Map.of();euphy.upo.sentrymechanicalarm.recipe.ManufacturingRules.clear();SentryCasingDrops.clear(); }
    public static void rebuild(MinecraftServer server) {
        if(!euphy.upo.sentrymechanicalarm.registry.SentryRegistry.EMPTY_CASING.isBound()){profiles=Map.of();drops=Map.of();euphy.upo.sentrymechanicalarm.recipe.ManufacturingRules.clear();return;}
        euphy.upo.sentrymechanicalarm.recipe.ManufacturingRules.reload(server);
        var resources=server.getResourceManager();var packs=resources.listPacks().toList();
        Map<ResourceLocation,Boolean> loaded=new LinkedHashMap<>();
        for(var entry:TimelessAPI.getAllCommonAmmoIndex()) {
            var id=entry.getKey();boolean allow=false;
            try {
                var display=SentryGunProfiles.asset(packs,entry.getValue().getPojo().getDisplay(),"display/ammo/");
                // An explicit shell asset is stronger evidence than a caliber name.
                if(display!=null&&display.has("shell")&&display.get("shell").isJsonObject()) {
                    var shell=display.getAsJsonObject("shell");
                    allow=shell.has("model")&&shell.has("texture")&&!shell.get("model").isJsonNull()&&!shell.get("texture").isJsonNull();
                }
                String path=id.getPath().toLowerCase(Locale.ROOT);
                if(path.matches(".*(rocket|missile|fuel|battery|energy|plasma|flame|caseless|grenade|gernade|gas_pistol).*")) allow=false;
                var override=resources.getResource(ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"sentry_casings/"+id.getPath()+".json"));
                if(override.isPresent())try(var reader=override.get().openAsReader()) {
                    var json=JsonParser.parseReader(reader).getAsJsonObject();allow=json.get("enabled").getAsBoolean();
                }
            } catch(Exception e) { allow=false;SentryMechanicalArm.LOGGER.warn("Casing rule rejected for {}: {}",id,e.toString()); }
            allow=euphy.upo.sentrymechanicalarm.recipe.ManufacturingRules.casingOverride(id).orElse(allow);
            loaded.put(id,allow);
        }
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
            }
            dropRules.put(id,drop);
        }
        profiles=Map.copyOf(loaded);drops=Map.copyOf(dropRules);
        SentryMechanicalArm.LOGGER.info("Casing discovery: {} ammo entries, {} eligible",loaded.size(),loaded.values().stream().filter(Boolean::booleanValue).count());
    }
}
