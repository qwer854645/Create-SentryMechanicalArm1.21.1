package euphy.upo.sentrymechanicalarm.recipe;

import euphy.upo.sentrymechanicalarm.util.*;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.crafting.GunSmithTableRecipe;
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
    private record HiddenNativeRecipes(net.minecraft.server.packs.resources.ResourceManager resources, Map<ResourceLocation,RecipeHolder<?>> recipes) {}
    private static final Map<RecipeManager,HiddenNativeRecipes> HIDDEN_NATIVE=new WeakHashMap<>();
    @SubscribeEvent public static void start(ServerAboutToStartEvent event){SentryCasingProfiles.rebuild(event.getServer());rebuild(event.getServer().getRecipeManager());}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void sync(OnDatapackSyncEvent event){if(event.getPlayer()!=null)return;SentryCasingProfiles.rebuild(event.getPlayerList().getServer());rebuild(event.getPlayerList().getServer().getRecipeManager());SentryFakePlayer.invalidateGunData();}
    @SubscribeEvent public static void stop(ServerStoppedEvent event){GENERATED.clear();HIDDEN_NATIVE.clear();}
    public static void rebuild(RecipeManager manager){
        var previous=GENERATED.getOrDefault(manager,Map.of());var combined=new LinkedHashMap<ResourceLocation,RecipeHolder<?>>();
        manager.getRecipes().stream().filter(h->previous.get(h.id())!=h).forEach(h->combined.put(h.id(),h));
        var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        var hidden=HIDDEN_NATIVE.remove(manager);
        // Repeated rebuilds of the same data must still see the original firearm and attachment costs.
        // A new resource reload owns a new snapshot: never resurrect a deleted data-pack recipe.
        if(hidden!=null&&server!=null&&hidden.resources()==server.getResourceManager())
            hidden.recipes().forEach(combined::putIfAbsent);
        Map<ResourceLocation,RecipeHolder<?>> owned=new HashMap<>();
        // Remove the previous generated set first; disabled restores and /reload cannot retain stale recipes.
        manager.replaceRecipes(combined.values());
        int restored=0;
        if(server!=null&&euphy.upo.sentrymechanicalarm.SMAServerConfig.RESTORE_LEGACY_AMMO_RECIPES.get())
            for(var holder:AmmoMaterialSources.legacy(manager,server))if(combined.putIfAbsent(holder.id(),holder)==null){owned.put(holder.id(),holder);restored++;}
        manager.replaceRecipes(combined.values());
        euphy.upo.sentrymechanicalarm.SentryMechanicalArm.LOGGER.info("Restored {} legacy native TaCZ ammo recipes",restored);
        for(var h:ReloadingRecipes.build(manager))if(combined.putIfAbsent(h.id(),h)==null)owned.put(h.id(),h);
        // Forming prices must see this rebuild's real reloading recipes, including explicit data-pack costs.
        manager.replaceRecipes(combined.values());
        for(var h:CasingFormingRecipes.build(manager))if(combined.putIfAbsent(h.id(),h)==null)owned.put(h.id(),h);
        manager.replaceRecipes(combined.values());
        for(var h:GunManufacturingRecipes.build(manager))if(combined.putIfAbsent(h.id(),h)==null)owned.put(h.id(),h);
        var attachments=AttachmentAssemblyRecipes.build(manager,server);
        for(var h:attachments.recipes())if(combined.putIfAbsent(h.id(),h)==null)owned.put(h.id(),h);
        var activeAttachmentOutputs=attachments.recipes().stream().filter(h->combined.get(h.id())==h
                && h.value() instanceof com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe)
                .map(h->h.value().getResultItem(server.registryAccess())).toList();
        if(server!=null&&(euphy.upo.sentrymechanicalarm.SMAServerConfig.REMOVE_NATIVE_GUN_RECIPES.get()
                ||euphy.upo.sentrymechanicalarm.SMAServerConfig.REMOVE_NATIVE_ATTACHMENT_RECIPES.get())) {
            var removed=new LinkedHashMap<ResourceLocation,RecipeHolder<?>>();
            for(var holder:combined.values())
                if(holder.value() instanceof GunSmithTableRecipe recipe
                        &&((euphy.upo.sentrymechanicalarm.SMAServerConfig.REMOVE_NATIVE_GUN_RECIPES.get()&&IGun.getIGunOrNull(recipe.getOutput())!=null)
                        ||(euphy.upo.sentrymechanicalarm.SMAServerConfig.REMOVE_NATIVE_ATTACHMENT_RECIPES.get()&&attachments.replacedNative().contains(holder.id())
                            &&activeAttachmentOutputs.stream().anyMatch(output->net.minecraft.world.item.ItemStack.matches(output,recipe.getOutput())))))
                    removed.put(holder.id(),holder);
            removed.keySet().forEach(combined::remove);
            HIDDEN_NATIVE.put(manager,new HiddenNativeRecipes(server.getResourceManager(),Map.copyOf(removed)));
            euphy.upo.sentrymechanicalarm.SentryMechanicalArm.LOGGER.info("Removed {} native TaCZ firearm/attachment recipes; retained internal assembly sources",removed.size());
        }
        manager.replaceRecipes(combined.values());GENERATED.put(manager,owned);
    }
}
