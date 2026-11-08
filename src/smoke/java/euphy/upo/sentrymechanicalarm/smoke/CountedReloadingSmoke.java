package euphy.upo.sentrymechanicalarm.smoke;

import com.simibubi.create.*;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.kinetics.deployer.*;
import com.simibubi.create.content.kinetics.press.*;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.tacz.guns.api.item.IAmmo;
import euphy.upo.sentrymechanicalarm.recipe.*;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import java.util.*;
import static euphy.upo.sentrymechanicalarm.smoke.ReloadingSmoke.*;

final class CountedReloadingSmoke {
    static final ResourceLocation SHOT=ResourceLocation.parse("tacz:12g");
    static final BlockPos POS=DEPOT.offset(0,0,4);
    static DepotBlockEntity depot;static DeployerBlockEntity machine;static ItemStack initial;static int start=-1;static boolean finished;
    static void verify(){
        test("shotgun sequence has proportional fluid and retained projectile iron",()->{
            var holder=ReloadingRecipes.find(level.getRecipeManager(),SHOT).orElseThrow();var seq=(SequencedAssemblyRecipe)holder.value();
            check(seq.getSequence().size()==4,"wrong shotgun stages");
            check(((FillingRecipe)seq.getSequence().get(0).getRecipe()).getRequiredFluid().amount()==334,"wrong shotgun fluid");
            var copper=seq.getSequence().get(1).getRecipe().getIngredients().get(1);
            check(CountedDeployerIngredient.amount(copper)==4&&copper.getItems()[0].getCount()==4,"copper quantity lost");
            var ops=level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
            var copy=(SequencedAssemblyRecipe)Recipe.CODEC.parse(ops,Recipe.CODEC.encodeStart(ops,seq).getOrThrow()).getOrThrow();
            check(((FillingRecipe)copy.getSequence().getFirst().getRecipe()).getRequiredFluid().amount()==334,"JSON lost fluid");
            var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());
            try{RecipeHolder.STREAM_CODEC.encode(buffer,holder);var restored=(SequencedAssemblyRecipe)RecipeHolder.STREAM_CODEC.decode(buffer).value();
                check(((FillingRecipe)restored.getSequence().getFirst().getRecipe()).getRequiredFluid().amount()==334,"network lost fluid");
                check(CountedDeployerIngredient.amount(restored.getSequence().get(1).getRecipe().getIngredients().get(1))==4,"network lost metal count");
            }finally{buffer.release();}
        });
        test("large fluid charges split without changing total",()->{
            var seq=(SequencedAssemblyRecipe)ReloadingRecipes.find(level.getRecipeManager(),ResourceLocation.parse("metis:20x145rs")).orElseThrow().value();
            int total=0,count=0;for(var step:seq.getSequence())if(step.getRecipe() instanceof FillingRecipe filling){int amount=filling.getRequiredFluid().amount();check(amount>0&&amount<=1000,"spout overflow");total+=amount;count++;}
            check(total==5000&&count==5,"large charge truncated or amplified");
        });
        test("tools containers and reusable molds retain their semantics",()->{
            var mold=(DeployerApplicationRecipe)CasingFormingRecipes.find(level.getRecipeManager(),AMMO).orElseThrow().value();
            check(mold.shouldKeepHeldItem()&&CountedDeployerIngredient.from(mold.getRequiredHeldItem())==null,"mold consumed");
            for(Item item:List.of(Items.IRON_PICKAXE,Items.MILK_BUCKET))check(CountedDeployerIngredient.compact(List.of(Ingredient.of(item),Ingredient.of(item))).size()==2,"special item grouped");
        });
    }
    static void setup(){
        // Prepare charged WIP through native filling; this fixture isolates counted metal consumption.
        initial=SequencedAssemblyRecipe.getRecipe(level,new SingleRecipeInput(casing(SHOT,1)),AllRecipeTypes.FILLING.getType(),FillingRecipe.class).orElseThrow().value().rollResults(level.random).getFirst();
        level.setBlock(POS,AllBlocks.DEPOT.get().defaultBlockState(),3);depot=(DepotBlockEntity)level.getBlockEntity(POS);depot.setHeldItem(initial.copy());
        level.setBlock(POS.above(2),AllBlocks.DEPLOYER.get().defaultBlockState().setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING,Direction.DOWN),3);machine=(DeployerBlockEntity)level.getBlockEntity(POS.above(2));
    }
    static void tick(){
        if(finished||depot==null)return;
        if(machine!=null){machine.setSpeed(256);if(machine.getPlayer()==null)return;if(start<0){start=ticks;machine.getPlayer().setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.COPPER_NUGGET.get(),3));}}
        if(start<0)return;int elapsed=ticks-start;
        if(elapsed==120){test("insufficient metal neither consumes stock nor advances WIP",()->{check(ItemStack.matches(depot.getHeldItem(),initial),"underpaid WIP advanced");check(machine.getPlayer().getMainHandItem().getCount()==3,"partial stock consumed");});machine.getPlayer().setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.COPPER_NUGGET.get(),7));}
        if(elapsed==240){test("counted metal consumes four nuggets exactly",()->{check(machine.getPlayer().getMainHandItem().getCount()==3,"wrong metal debit");check(depot.getHeldItem().get(AllDataComponents.SEQUENCED_ASSEMBLY).step()==2,"wrong progress");});machine.getPlayer().setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_NUGGET,1));}
        if(elapsed==360){test("projectile iron retains its native one-nugget charge",()->check(machine.getPlayer().getMainHandItem().isEmpty()&&depot.getHeldItem().get(AllDataComponents.SEQUENCED_ASSEMBLY).step()==3,"wrong projectile charge"));machine=null;level.setBlock(POS.above(2),AllBlocks.MECHANICAL_PRESS.get().defaultBlockState(),3);}
        if(elapsed>=361&&elapsed<=540&&level.getBlockEntity(POS.above(2)) instanceof MechanicalPressBlockEntity press)press.setSpeed(256);
        if(elapsed==540){test("fluid shotgun sequence outputs one correct round",()->{var output=depot.getHeldItem();var api=IAmmo.getIAmmoOrNull(output);check(api!=null&&SHOT.equals(api.getAmmoId(output))&&output.getCount()==1,"wrong output");});finished=true;}
    }
}
