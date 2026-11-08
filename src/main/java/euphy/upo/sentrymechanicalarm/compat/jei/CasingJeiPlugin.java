package euphy.upo.sentrymechanicalarm.compat.jei;

import euphy.upo.sentrymechanicalarm.content.*;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.SentryCasingProfiles;
import mezz.jei.api.*;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.*;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;

@JeiPlugin
public final class CasingJeiPlugin implements IModPlugin {
    private static IJeiRuntime runtime;
    public static final ISubtypeInterpreter<ItemStack> SUBTYPE=new ISubtypeInterpreter<>(){
        public Object getSubtypeData(ItemStack stack,UidContext context){return getLegacyStringSubtypeInfo(stack,context);}
        public String getLegacyStringSubtypeInfo(ItemStack stack,UidContext context){
            var ammo=EmptyCasingItem.ammoId(stack);if(ammo==null)return "";
            var progress=stack.get(com.simibubi.create.AllDataComponents.SEQUENCED_ASSEMBLY);
            // Recipe searches identify the caliber; actual bookmarked WIP stacks also identify their state.
            return context==UidContext.Ingredient&&progress!=null?ammo+"|"+progress.id()+"|"+progress.step():ammo.toString();
        }
    };
    public ResourceLocation getPluginUid(){return ResourceLocation.parse("sentrymechanicalarm:casings");}
    public void registerItemSubtypes(ISubtypeRegistration registration){
        if(SentryRegistry.CASING_MOLD.isBound())registration.registerSubtypeInterpreter(VanillaTypes.ITEM_STACK,SentryRegistry.CASING_MOLD.get(),SUBTYPE);
        if(SentryRegistry.EMPTY_CASING.isBound())registration.registerSubtypeInterpreter(VanillaTypes.ITEM_STACK,SentryRegistry.EMPTY_CASING.get(),SUBTYPE);
        if(SentryRegistry.RELOADING_CASING.isBound())registration.registerSubtypeInterpreter(VanillaTypes.ITEM_STACK,SentryRegistry.RELOADING_CASING.get(),SUBTYPE);
    }
    public void onRuntimeAvailable(IJeiRuntime value){runtime=value;refresh();}
    public void onRuntimeUnavailable(){runtime=null;}
    public static IJeiRuntime runtime(){return runtime;}
    public static void refresh(){
        if(runtime==null)return;var manager=runtime.getIngredientManager();
        var all=manager.getAllIngredients(VanillaTypes.ITEM_STACK).stream().filter(s->s.getItem() instanceof EmptyCasingItem).toList();
        var invalid=all.stream().filter(s->s.getItem() instanceof ReloadingCasingItem||EmptyCasingItem.ammoId(s)==null||!SentryCasingProfiles.clientSnapshot().contains(EmptyCasingItem.ammoId(s))).toList();
        if(!invalid.isEmpty())manager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK,invalid);
        if(!SentryRegistry.EMPTY_CASING.isBound())return;
        Set<ResourceLocation> present=new HashSet<>();for(var s:all)if(!invalid.contains(s))present.add(EmptyCasingItem.ammoId(s));
        var missing=SentryCasingProfiles.clientSnapshot().stream().filter(id->!present.contains(id)).sorted().map(id->EmptyCasingItem.create(id,1)).toList();
        if(!missing.isEmpty())manager.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK,missing);
        var level=net.minecraft.client.Minecraft.getInstance().level;
        if(SentryRegistry.CASING_MOLD.isBound()){
            var choices=level==null?java.util.List.<ResourceLocation>of():euphy.upo.sentrymechanicalarm.recipe.CasingFormingRecipes.choices(level.getRecipeManager());
            var molds=manager.getAllIngredients(VanillaTypes.ITEM_STACK).stream().filter(s->s.is(SentryRegistry.CASING_MOLD.get())).toList();
            var stale=molds.stream().filter(s->EmptyCasingItem.ammoId(s)!=null&&!choices.contains(EmptyCasingItem.ammoId(s))).toList();
            if(!stale.isEmpty())manager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK,stale);
            var add=choices.stream().filter(id->molds.stream().noneMatch(s->id.equals(EmptyCasingItem.ammoId(s)))).map(CasingMoldItem::create).toList();
            if(!add.isEmpty())manager.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK,add);
        }
    }
}
