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
        var powder=new ItemStack(euphy.upo.sentrymechanicalarm.registry.SentryRegistry.SMALL_GUNPOWDER.get());
        check(manager.getAllIngredients(VanillaTypes.ITEM_STACK).stream().anyMatch(s->s.is(powder.getItem())),"small powder missing from JEI");
        var powderFocus=runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT,VanillaTypes.ITEM_STACK,powder);
        for(String recipeType:List.of("minecraft:crafting","create:milling")){
            var conversionType=runtime.getRecipeManager().getRecipeType(ResourceLocation.parse(recipeType)).orElseThrow();
            check(runtime.getRecipeManager().createRecipeLookup(conversionType).limitFocus(List.of(powderFocus)).get().findAny().isPresent(),"powder conversion missing from JEI: "+recipeType);
        }
        var powderModel=mc.getItemRenderer().getModel(powder,mc.level,null,0);
        check(powderModel!=mc.getModelManager().getMissingModel(),"small powder missing item model");
        var materials=recipe.getSequence().stream().filter(s->s.getRecipe() instanceof com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe).map(s->s.getRecipe().getIngredients().get(1)).toList();
        check(materials.size()==3&&materials.get(0).test(new ItemStack(euphy.upo.sentrymechanicalarm.registry.SentryRegistry.PRIMER.get()))&&materials.get(1).test(powder)&&materials.get(2).test(new ItemStack(com.simibubi.create.AllItems.COPPER_NUGGET.get())),"client recipe retained old costs");
        euphy.upo.sentrymechanicalarm.SentryMechanicalArm.LOGGER.info("POWDER_CLIENT PASS item model, JEI crafting/milling conversions and single-round nugget/powder recipe");
        var primer=new ItemStack(euphy.upo.sentrymechanicalarm.registry.SentryRegistry.PRIMER.get());
        check(manager.getAllIngredients(VanillaTypes.ITEM_STACK).stream().anyMatch(s->s.is(primer.getItem())),"primer missing from JEI");
        var primerFocus=runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT,VanillaTypes.ITEM_STACK,primer);
        var pressType=runtime.getRecipeManager().getRecipeType(ResourceLocation.parse("create:deploying")).orElseThrow();
        check(runtime.getRecipeManager().createRecipeLookup(pressType).limitFocus(List.of(primerFocus)).get().findAny().isPresent(),"primer charging recipe missing from JEI");
        check(mc.getItemRenderer().getModel(primer,mc.level,null,0)!=mc.getModelManager().getMissingModel(),"primer model missing");
        var cup=new ItemStack(euphy.upo.sentrymechanicalarm.registry.SentryRegistry.PRIMER_CUP.get());
        var cupFocus=runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT,VanillaTypes.ITEM_STACK,cup);
        var cupType=runtime.getRecipeManager().getRecipeType(ResourceLocation.parse("create:pressing")).orElseThrow();
        check(runtime.getRecipeManager().createRecipeLookup(cupType).limitFocus(List.of(cupFocus)).get().findAny().isPresent(),"cup pressing missing from JEI");
        check(mc.getItemRenderer().getModel(cup,mc.level,null,0)!=mc.getModelManager().getMissingModel(),"primer cup model missing");
        var primerTips=new ArrayList<net.minecraft.network.chat.Component>();primer.getItem().appendHoverText(primer,Item.TooltipContext.of(mc.level),primerTips,TooltipFlag.NORMAL);
        check(primerTips.stream().anyMatch(c->c.getString().contains("底火壳")),"primer budget explanation missing");
        euphy.upo.sentrymechanicalarm.SentryMechanicalArm.LOGGER.info("PRIMER_CLIENT PASS cup pressing, primer charging, both models, tooltip and primer-first assembly");
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
        var normal=new ArrayList<net.minecraft.network.chat.Component>();a.getItem().appendHoverText(a,Item.TooltipContext.of(mc.level),normal,TooltipFlag.NORMAL);
        check(normal.stream().noneMatch(c->c.getString().contains("tacz:9mm")||c.getString().contains("后续阶段")),"debug ID or stale description in ordinary tooltip");
        var pack=com.tacz.guns.client.resource.ClientAssetsManager.INSTANCE.getPackInfo(ResourceLocation.parse("tacz:9mm"));check(pack!=null&&normal.stream().anyMatch(c->c.getString().equals(net.minecraft.network.chat.Component.translatable(pack.getName()).getString())&&c.getStyle().isItalic()),"missing TaCZ-style pack source");
        var advanced=new ArrayList<net.minecraft.network.chat.Component>();a.getItem().appendHoverText(a,Item.TooltipContext.of(mc.level),advanced,TooltipFlag.ADVANCED);check(advanced.stream().anyMatch(c->c.getString().contains("tacz:9mm")),"advanced tooltip lost AmmoId");
        euphy.upo.sentrymechanicalarm.SentryMechanicalArm.LOGGER.info("JEI_CASING PASS actual runtime: {} distinct variants, caliber-specific recipe lookup, WIP states, cosmetic normalization and tooltips",entries.size());return true;
    }
}
