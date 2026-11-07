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
    public static boolean enabled(ResourceLocation id) { return profiles.getOrDefault(id,false); }
    public static Map<ResourceLocation,Boolean> snapshot() { return profiles; }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void start(ServerStartedEvent event) { rebuild(event.getServer()); }
    @SubscribeEvent(priority=EventPriority.HIGH) public static void sync(OnDatapackSyncEvent event) {
        if(event.getPlayer()==null) { SentryCasingDrops.clear(); rebuild(event.getPlayerList().getServer()); }
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { profiles=Map.of();SentryCasingDrops.clear(); }
    public static void rebuild(MinecraftServer server) {
        if(!euphy.upo.sentrymechanicalarm.registry.SentryRegistry.EMPTY_CASING.isBound()){profiles=Map.of();return;}
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
            loaded.put(id,allow);
        }
        profiles=Map.copyOf(loaded);
        SentryMechanicalArm.LOGGER.info("Casing discovery: {} ammo entries, {} eligible",loaded.size(),loaded.values().stream().filter(Boolean::booleanValue).count());
    }
}
