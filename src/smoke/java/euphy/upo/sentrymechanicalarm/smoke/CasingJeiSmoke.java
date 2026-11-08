package euphy.upo.sentrymechanicalarm.smoke;

import euphy.upo.sentrymechanicalarm.compat.jei.CasingJeiPlugin;
import euphy.upo.sentrymechanicalarm.content.*;
import euphy.upo.sentrymechanicalarm.recipe.ReloadingRecipes;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import java.util.*;

public final class CasingJeiSmoke {
    static boolean done;static int waiting;
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static boolean verify(){
        var runtime=CasingJeiPlugin.runtime();if(runtime==null){if(++waiting>1200)throw new AssertionError("JEI did not start");return false;}
        if(done)return true;done=true;
        var manager=runtime.getIngredientManager();var helper=manager.getIngredientHelper(VanillaTypes.ITEM_STACK);
        var a=EmptyCasingItem.create(ResourceLocation.parse("tacz:9mm"),1);var b=EmptyCasingItem.create(ResourceLocation.parse("tacz:12g"),1);
        check(!helper.getUniqueId(a,UidContext.Ingredient).equals(helper.getUniqueId(b,UidContext.Ingredient)),"JEI merged calibers");
        var renamed=a.copyWithCount(5);renamed.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Mine"));check(helper.getUniqueId(a,UidContext.Ingredient).equals(helper.getUniqueId(renamed,UidContext.Ingredient)),"cosmetic data breaks ingredient identity");
        var entries=manager.getAllIngredients(VanillaTypes.ITEM_STACK).stream().filter(s->s.getItem() instanceof EmptyCasingItem).toList();
        check(entries.size()==euphy.upo.sentrymechanicalarm.util.SentryCasingProfiles.clientSnapshot().size(),"JEI variant count="+entries.size());
        check(entries.stream().map(s->helper.getUniqueId(s,UidContext.Ingredient)).distinct().count()==entries.size(),"JEI contains duplicate UIDs");
        for(var s:entries)check(!(s.getItem() instanceof ReloadingCasingItem)&&euphy.upo.sentrymechanicalarm.util.SentryCasingProfiles.clientSnapshot().contains(EmptyCasingItem.ammoId(s)),"ineligible JEI ingredient");
        var mc=Minecraft.getInstance();var holder=ReloadingRecipes.find(mc.level.getRecipeManager(),ResourceLocation.parse("tacz:9mm")).orElseThrow();var recipe=(SequencedAssemblyRecipe)holder.value();
        var progress=recipe.getTransitionalItem().copy();check(ResourceLocation.parse("tacz:9mm").equals(EmptyCasingItem.ammoId(progress)),"transition template lost caliber");
        var p1=progress.copy();var p2=progress.copy();p1.set(com.simibubi.create.AllDataComponents.SEQUENCED_ASSEMBLY,new SequencedAssemblyRecipe.SequencedAssembly(holder.id(),1,.3f));p2.set(com.simibubi.create.AllDataComponents.SEQUENCED_ASSEMBLY,new SequencedAssemblyRecipe.SequencedAssembly(holder.id(),2,.6f));
        check(!helper.getUniqueId(p1,UidContext.Ingredient).equals(helper.getUniqueId(p2,UidContext.Ingredient)),"WIP progress identity lost");check(helper.getUniqueId(p1,UidContext.Recipe).equals(helper.getUniqueId(progress,UidContext.Recipe)),"WIP recipe search is step-specific");
        var type=runtime.getRecipeManager().getRecipeType(ResourceLocation.parse("create:sequenced_assembly")).orElseThrow();
        var focus=runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.INPUT,VanillaTypes.ITEM_STACK,a);
        var matches=runtime.getRecipeManager().createRecipeLookup(type).limitFocus(List.of(focus)).get().toList();int count=0;
        for(var value:matches){Object unwrapped=value instanceof net.minecraft.world.item.crafting.RecipeHolder<?> h?h.value():value;if(unwrapped instanceof SequencedAssemblyRecipe seq){var output=seq.getResultItem(mc.level.registryAccess());var api=com.tacz.guns.api.item.IAmmo.getIAmmoOrNull(output);if(api!=null){check(ResourceLocation.parse("tacz:9mm").equals(api.getAmmoId(output)),"JEI returns a wrong-caliber recipe");count++;}}}
        check(count==1,"JEI 9mm usage lookup returned "+count+" ammo recipes");
        var normal=new ArrayList<net.minecraft.network.chat.Component>();a.getItem().appendHoverText(a,Item.TooltipContext.of(mc.level),normal,TooltipFlag.NORMAL);
        check(normal.stream().noneMatch(c->c.getString().contains("tacz:9mm")||c.getString().contains("后续阶段")),"debug ID or stale description in ordinary tooltip");
        var pack=com.tacz.guns.client.resource.ClientAssetsManager.INSTANCE.getPackInfo(ResourceLocation.parse("tacz:9mm"));check(pack!=null&&normal.stream().anyMatch(c->c.getString().equals(net.minecraft.network.chat.Component.translatable(pack.getName()).getString())&&c.getStyle().isItalic()),"missing TaCZ-style pack source");
        var advanced=new ArrayList<net.minecraft.network.chat.Component>();a.getItem().appendHoverText(a,Item.TooltipContext.of(mc.level),advanced,TooltipFlag.ADVANCED);check(advanced.stream().anyMatch(c->c.getString().contains("tacz:9mm")),"advanced tooltip lost AmmoId");
        euphy.upo.sentrymechanicalarm.SentryMechanicalArm.LOGGER.info("JEI_CASING PASS actual runtime: {} distinct variants, caliber-specific recipe lookup, WIP states, cosmetic normalization and tooltips",entries.size());return true;
    }
}
