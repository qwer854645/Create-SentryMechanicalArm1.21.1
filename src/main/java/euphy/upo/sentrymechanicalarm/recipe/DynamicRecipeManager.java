package euphy.upo.sentrymechanicalarm.recipe;

import euphy.upo.sentrymechanicalarm.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.*;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import java.util.*;

/** Owns generated manufacturing recipes and explicitly restored legacy ammo recipes. */
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
        // Remove the previous generated set first; disabled restores and /reload cannot retain stale recipes.
        manager.replaceRecipes(combined.values());
        var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        int restored=0;
        if(server!=null&&euphy.upo.sentrymechanicalarm.SMAServerConfig.RESTORE_LEGACY_AMMO_RECIPES.get())
            for(var holder:AmmoMaterialSources.legacy(manager,server))if(combined.putIfAbsent(holder.id(),holder)==null){owned.put(holder.id(),holder);restored++;}
        manager.replaceRecipes(combined.values());
        euphy.upo.sentrymechanicalarm.SentryMechanicalArm.LOGGER.info("Restored {} legacy native TaCZ ammo recipes",restored);
        for(var h:ReloadingRecipes.build(manager))if(combined.putIfAbsent(h.id(),h)==null)owned.put(h.id(),h);
        // Forming prices must see this rebuild's real reloading recipes, including explicit data-pack costs.
        manager.replaceRecipes(combined.values());
        for(var h:CasingFormingRecipes.build(manager))if(combined.putIfAbsent(h.id(),h)==null)owned.put(h.id(),h);
        manager.replaceRecipes(combined.values());GENERATED.put(manager,owned);
    }
}
