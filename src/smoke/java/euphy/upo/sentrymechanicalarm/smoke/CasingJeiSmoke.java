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
        int covered=0;
        for(var casing:entries){
            var ammo=EmptyCasingItem.ammoId(casing);var expected=ReloadingRecipes.find(mc.level.getRecipeManager(),ammo);
            check(expected.isPresent(),"client lacks fluid reloading "+ammo);
            var caliberFocus=runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.INPUT,VanillaTypes.ITEM_STACK,casing);
            int found=0;
            for(var value:runtime.getRecipeManager().createRecipeLookup(type).limitFocus(List.of(caliberFocus)).get().toList()){
                Object unwrapped=value instanceof net.minecraft.world.item.crafting.RecipeHolder<?> h?h.value():value;
                if(unwrapped instanceof SequencedAssemblyRecipe sequence){var output=sequence.getResultItem(mc.level.registryAccess());var api=com.tacz.guns.api.item.IAmmo.getIAmmoOrNull(output);
                    check(api!=null&&ammo.equals(api.getAmmoId(output))&&output.getCount()==1,"JEI caliber leaked into another recipe "+ammo);found++;}
            }
            check(found==1,"JEI missing or duplicates recipe for "+ammo+": "+found);covered++;
        }
        check(covered==37,"client recipe coverage "+covered);
        euphy.upo.sentrymechanicalarm.SentryMechanicalArm.LOGGER.info("FULL_RELOADING_JEI PASS 37 synchronized caliber-specific assembly lookups");
        var focus=runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.INPUT,VanillaTypes.ITEM_STACK,a);
        var matches=runtime.getRecipeManager().createRecipeLookup(type).limitFocus(List.of(focus)).get().toList();int count=0;
        for(var value:matches){Object unwrapped=value instanceof net.minecraft.world.item.crafting.RecipeHolder<?> h?h.value():value;if(unwrapped instanceof SequencedAssemblyRecipe seq){var output=seq.getResultItem(mc.level.registryAccess());var api=com.tacz.guns.api.item.IAmmo.getIAmmoOrNull(output);if(api!=null){check(ResourceLocation.parse("tacz:9mm").equals(api.getAmmoId(output)),"JEI returns a wrong-caliber recipe");count++;}}}
        check(count==1,"JEI 9mm usage lookup returned "+count+" ammo recipes");
        var mixingType=runtime.getRecipeManager().getRecipeType(ResourceLocation.parse("create:mixing")).orElseThrow();
        check(runtime.getRecipeManager().createRecipeLookup(mixingType).get().anyMatch(value->{Object raw=value instanceof net.minecraft.world.item.crafting.RecipeHolder<?> h?h.value():value;
            return raw instanceof com.simibubi.create.content.kinetics.mixer.MixingRecipe mixing&&mixing.getFluidResults().stream().anyMatch(f->f.getFluid()==euphy.upo.sentrymechanicalarm.registry.SentryFluids.PROPELLANT.get());}),"propellant mixing missing from JEI");
        var materials=recipe.getSequence().stream().filter(step->step.getRecipe() instanceof com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe).map(step->step.getRecipe().getIngredients().get(1)).toList();
        check(materials.isEmpty(),"removed primer or fractional pistol copper retained");
        check(((com.simibubi.create.content.fluids.transfer.FillingRecipe)recipe.getSequence().getFirst().getRecipe()).getRequiredFluid().amount()==40,"full pistol fluid not synchronized");
        for(String id:List.of("primer","primer_cup","small_gunpowder"))check(!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("sentrymechanicalarm:"+id)),"removed item registered: "+id);
        var listedMolds=manager.getAllIngredients(VanillaTypes.ITEM_STACK).stream().filter(stack->stack.is(euphy.upo.sentrymechanicalarm.registry.SentryRegistry.CASING_MOLD.get())).toList();
        check(listedMolds.size()==1&&EmptyCasingItem.ammoId(listedMolds.getFirst())==null,"configured molds still flood sidebar");
        var mold=euphy.upo.sentrymechanicalarm.content.CasingMoldItem.create(ResourceLocation.parse("tacz:9mm"));
        var otherMold=euphy.upo.sentrymechanicalarm.content.CasingMoldItem.create(ResourceLocation.parse("tacz:12g"));
        check(!helper.getUniqueId(mold,UidContext.Ingredient).equals(helper.getUniqueId(otherMold,UidContext.Ingredient)),"JEI merged mold calibers");
        var formingType=runtime.getRecipeManager().getRecipeType(ResourceLocation.parse("create:deploying")).orElseThrow();
        var caseFocus=runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT,VanillaTypes.ITEM_STACK,a);
        check(runtime.getRecipeManager().createRecipeLookup(formingType).limitFocus(List.of(caseFocus)).get().findAny().isPresent(),"fresh case recipe missing from JEI");
        check(mc.getItemRenderer().getModel(mold,mc.level,null,0)!=mc.getModelManager().getMissingModel(),"mold model missing");
        check(euphy.upo.sentrymechanicalarm.recipe.CasingFormingRecipes.choices(mc.level.getRecipeManager()).contains(ResourceLocation.parse("tacz:9mm")),"client selector lacks 9mm");
        var selector=new euphy.upo.sentrymechanicalarm.client.CasingMoldScreen(net.minecraft.world.InteractionHand.MAIN_HAND);
        selector.init(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
        check(selector.children().size()==10,"selector failed to build search, choices and pagination");
        var search=selector.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance).map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow();
        search.setValue("tacz:9mm");
        long visibleRows=selector.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance).map(net.minecraft.client.gui.components.Button.class::cast).filter(button->button.visible&&button.getWidth()==280).count();
        check(visibleRows==1,"mold selector search failed");
        euphy.upo.sentrymechanicalarm.SentryMechanicalArm.LOGGER.info("MOLD_CLIENT PASS unique variants, case forming JEI recipe, model, {} synchronized choices and selector search",euphy.upo.sentrymechanicalarm.recipe.CasingFormingRecipes.choices(mc.level.getRecipeManager()).size());
        var shot=(SequencedAssemblyRecipe)ReloadingRecipes.find(mc.level.getRecipeManager(),ResourceLocation.parse("tacz:12g")).orElseThrow().value();
        check(shot.getSequence().size()==4,"client retained the long shotgun sequence");
        var charge=(com.simibubi.create.content.fluids.transfer.FillingRecipe)shot.getSequence().getFirst().getRecipe();
        check(charge.getRequiredFluid().amount()==334,"client/JEI lost proportional fluid quantity");
        check(new java.util.HashSet<>(euphy.upo.sentrymechanicalarm.recipe.CasingFormingRecipes.choices(mc.level.getRecipeManager())).equals(euphy.upo.sentrymechanicalarm.util.SentryCasingProfiles.clientSnapshot()),"client mold list excludes eligible cases");
        euphy.upo.sentrymechanicalarm.SentryMechanicalArm.LOGGER.info("FLUID_CLIENT PASS primerless shotgun four stages, 334 mB full charge and all eligible molds");
        var normal=new ArrayList<net.minecraft.network.chat.Component>();a.getItem().appendHoverText(a,Item.TooltipContext.of(mc.level),normal,TooltipFlag.NORMAL);
        check(normal.stream().noneMatch(c->c.getString().contains("tacz:9mm")||c.getString().contains("后续阶段")),"debug ID or stale description in ordinary tooltip");
        var pack=com.tacz.guns.client.resource.ClientAssetsManager.INSTANCE.getPackInfo(ResourceLocation.parse("tacz:9mm"));check(pack!=null&&normal.stream().anyMatch(c->c.getString().equals(net.minecraft.network.chat.Component.translatable(pack.getName()).getString())&&c.getStyle().isItalic()),"missing TaCZ-style pack source");
        var advanced=new ArrayList<net.minecraft.network.chat.Component>();a.getItem().appendHoverText(a,Item.TooltipContext.of(mc.level),advanced,TooltipFlag.ADVANCED);check(advanced.stream().anyMatch(c->c.getString().contains("tacz:9mm")),"advanced tooltip lost AmmoId");
        euphy.upo.sentrymechanicalarm.SentryMechanicalArm.LOGGER.info("JEI_CASING PASS actual runtime: {} distinct variants, caliber-specific recipe lookup, WIP states, cosmetic normalization and tooltips",entries.size());return true;
    }
}
