package euphy.upo.sentrymechanicalarm.recipe;

import euphy.upo.sentrymechanicalarm.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.*;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import java.util.*;

/** Owns only the new spent-casing reloading recipes; native TaCZ crafting stays untouched. */
@EventBusSubscriber(modid="sentrymechanicalarm")
public final class DynamicRecipeManager {
    private static final Map<RecipeManager,Map<ResourceLocation,RecipeHolder<?>>> GENERATED=new WeakHashMap<>();
    @SubscribeEvent public static void start(ServerAboutToStartEvent event){SentryCasingProfiles.rebuild(event.getServer());rebuild(event.getServer().getRecipeManager());}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void sync(OnDatapackSyncEvent event){if(event.getPlayer()!=null)return;SentryCasingProfiles.rebuild(event.getPlayerList().getServer());rebuild(event.getPlayerList().getServer().getRecipeManager());SentryFakePlayer.invalidateGunData();}
    @SubscribeEvent public static void stop(ServerStoppedEvent event){GENERATED.clear();}
    public static void rebuild(RecipeManager manager){
        var previous=GENERATED.getOrDefault(manager,Map.of());var combined=new LinkedHashMap<ResourceLocation,RecipeHolder<?>>();
        manager.getRecipes().stream().filter(h->previous.get(h.id())!=h).forEach(h->combined.put(h.id(),h));
        Map<ResourceLocation,RecipeHolder<?>> owned=new HashMap<>();
        for(var h:ReloadingRecipes.build(manager))if(combined.putIfAbsent(h.id(),h)==null)owned.put(h.id(),h);
        manager.replaceRecipes(combined.values());GENERATED.put(manager,owned);
    }
}
