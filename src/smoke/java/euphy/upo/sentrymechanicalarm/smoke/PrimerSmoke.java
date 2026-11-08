package euphy.upo.sentrymechanicalarm.smoke;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.press.*;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import euphy.upo.sentrymechanicalarm.SMAServerConfig;
import euphy.upo.sentrymechanicalarm.recipe.*;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import java.util.*;

import static euphy.upo.sentrymechanicalarm.smoke.ReloadingSmoke.*;

final class PrimerSmoke {
    private static final BlockPos POS = DEPOT.offset(4, 0, 0);
    private static final BlockPos ASSEMBLY = POS.offset(2, 0, 0);
    private static DepotBlockEntity primerDepot, assemblyDepot;
    private static net.neoforged.neoforge.fluids.capability.IFluidHandler primerFluid;
    private static boolean loaded, supplied;

    static void verify() {
        test("primer allocation reserves powder and retains budgeted direct metals", () -> {
            int checked = 0;
            for (var holder : level.getRecipeManager().getRecipes()) {
                if (!holder.id().getNamespace().equals("sentrymechanicalarm") || !holder.id().getPath().startsWith("casing_reloading/")) continue;
                var sequence = (SequencedAssemblyRecipe) holder.value();
                var output = sequence.getResultItem(level.registryAccess());
                var ammo = IAmmo.getIAmmoOrNull(output).getAmmoId(output);
                var original = AmmoMaterialSources.collect(level.getRecipeManager(),level.getServer()).stream()
                        .map(RecipeHolder::value).filter(GunSmithTableRecipe.class::isInstance).map(GunSmithTableRecipe.class::cast)
                        .filter(r -> {var api = IAmmo.getIAmmoOrNull(r.getOutput()); return api != null && ammo.equals(api.getAmmoId(r.getOutput()));})
                        .map(r -> ReloadingCosts.derive(r, SMAServerConfig.RELOADING_METAL_FACTOR.get()))
                        .flatMap(Optional::stream).findFirst().orElseThrow();
                var expected = ReloadingCostSmoke.counts(original);
                var inputs = sequence.getSequence().stream().filter(s -> s.getRecipe() instanceof DeployerApplicationRecipe)
                        .map(s -> s.getRecipe().getIngredients().get(1)).toList();
                var actual = ReloadingCostSmoke.counts(inputs);
                check(actual.getOrDefault(SentryRegistry.PRIMER.get(), 0) == 1, "wrong primer count: " + ammo);
                actual.remove(SentryRegistry.PRIMER.get());

                check(actual.equals(expected), "primer double-charges materials: " + ammo);
                check(inputs.getFirst().test(new ItemStack(SentryRegistry.PRIMER.get())), "primer is not installed first");
                check(output.getCount() == 1, "primer amplified output");
                checked++;
            }
            check(checked > 0, "no automatic recipes checked");
            euphy.upo.sentrymechanicalarm.SentryMechanicalArm.LOGGER.info("PRIMER_BUDGET PASS {} caliber recipes reserve one powder charge and account for 1/8 copper per primer", checked);
        });
        test("primer fluid is reserved once", () -> {
            var allocation=PrimerAllocation.forFluidAssembly(new ReloadingCosts.Cost(List.of(),10));
            check(allocation.additionalFluid()==0&&allocation.materials().size()==1,"minimum primer counted twice");
            check(PrimerAllocation.forFluidAssembly(new ReloadingCosts.Cost(List.of(),40)).additionalFluid()==30,"wrong remaining charge");
        });
        test("uninstalled casing refuses powder and requires a primer", () -> {
            var inv = new net.neoforged.neoforge.items.ItemStackHandler(2);
            inv.setStackInSlot(0, casing(AMMO, 1));
            inv.setStackInSlot(1, new ItemStack(SentryRegistry.SMALL_GUNPOWDER.get()));
            var wrapper = new net.neoforged.neoforge.items.wrapper.RecipeWrapper(inv);
            check(SequencedAssemblyRecipe.getRecipe(level, wrapper, AllRecipeTypes.DEPLOYING.getType(), DeployerApplicationRecipe.class).isEmpty(), "powder bypasses primer");
            inv.setStackInSlot(1, new ItemStack(SentryRegistry.PRIMER.get()));
            check(SequencedAssemblyRecipe.getRecipe(level, wrapper, AllRecipeTypes.DEPLOYING.getType(), DeployerApplicationRecipe.class).isPresent(), "primer cannot start assembly");
        });
        test("primer cup pressing and charging have exact yields", () -> {
            var press = (PressingRecipe) level.getRecipeManager().byKey(ResourceLocation.parse("sentrymechanicalarm:pressing_primer_cup")).orElseThrow().value();
            check(press.matches(new SingleRecipeInput(new ItemStack(com.simibubi.create.AllItems.COPPER_NUGGET.get())), level), "copper nugget rejected");
            check(!press.matches(new SingleRecipeInput(new ItemStack(SentryRegistry.SMALL_GUNPOWDER.get())), level), "prototype powder pressing still present");
            var output = press.rollResults(level.random);
            check(output.size() == 1 && output.getFirst().is(SentryRegistry.PRIMER_CUP.get()) && output.getFirst().getCount() == 8, "wrong cup yield");
            var filling=(com.simibubi.create.content.fluids.transfer.FillingRecipe)level.getRecipeManager().byKey(ResourceLocation.parse("sentrymechanicalarm:filling_primer")).orElseThrow().value();
            check(filling.getRequiredFluid().amount()==10&&filling.matches(new SingleRecipeInput(new ItemStack(SentryRegistry.PRIMER_CUP.get())),level),"wrong primer filling");
            var charged=filling.rollResults(level.random);check(charged.size()==1&&charged.getFirst().is(SentryRegistry.PRIMER.get()),"wrong primer output");
            var mixing=(com.simibubi.create.content.kinetics.mixer.MixingRecipe)level.getRecipeManager().byKey(ResourceLocation.parse("sentrymechanicalarm:mixing_propellant")).orElseThrow().value();
            check(mixing.getIngredients().size()==1&&mixing.getIngredients().getFirst().test(new ItemStack(Items.GUNPOWDER))&&mixing.getFluidIngredients().isEmpty(),"propellant introduced water or another input");
            check(mixing.getFluidResults().size()==1&&mixing.getFluidResults().getFirst().getAmount()==1000,"wrong direct powder yield");
        });
        test("primer copper is reserved before rounding direct metal", () -> {
            var cost = ReloadingCosts.derive(ReloadingCostSmoke.synthetic(4,
                    ReloadingCostSmoke.material(Items.COPPER_INGOT, 1), ReloadingCostSmoke.material(Items.GUNPOWDER, 1)), .5).orElseThrow();
            check(ReloadingCostSmoke.counts(cost).get(com.simibubi.create.AllItems.COPPER_NUGGET.get()) == 1,
                    "primer housing was not deducted before copper rounding");
        });
    }

    static void setup() {
        level.setBlock(POS, AllBlocks.DEPOT.get().defaultBlockState(), 3);
        primerDepot = (DepotBlockEntity) level.getBlockEntity(POS);
        primerDepot.setHeldItem(new ItemStack(com.simibubi.create.AllItems.COPPER_NUGGET.get()));
        level.setBlock(POS.above(2), AllBlocks.MECHANICAL_PRESS.get().defaultBlockState(), 3);
        level.setBlock(ASSEMBLY, AllBlocks.DEPOT.get().defaultBlockState(), 3);
        assemblyDepot = (DepotBlockEntity) level.getBlockEntity(ASSEMBLY);
        primerFluid=FluidSmokeSupport.spout(ASSEMBLY,30);
    }

    static void tick() {
        if (level.getBlockEntity(POS.above(2)) instanceof MechanicalPressBlockEntity press) press.setSpeed(256);
        if (!loaded && primerDepot.getHeldItem().is(SentryRegistry.PRIMER_CUP.get()) && primerDepot.getHeldItem().getCount()==8){
            assemblyDepot.setHeldItem(primerDepot.getHeldItem().split(1));primerDepot.setChanged();loaded=true;
        }
    }

    static boolean ready() {
        return assemblyDepot != null && assemblyDepot.getHeldItem().is(SentryRegistry.PRIMER.get()) && assemblyDepot.getHeldItem().getCount() == 1;
    }

    static ItemStack take() {
        test("real cup pressing and spout charging preserve cup and fluid stock", () -> {
            check(ready(), "primer factory did not output one primer");
            check(primerDepot.getHeldItem().is(SentryRegistry.PRIMER_CUP.get()) && primerDepot.getHeldItem().getCount() == 7, "cup yield or stock changed");
            check(primerFluid.getFluidInTank(0).getAmount()==20,"primer did not consume exactly 10 mB");
        });
        var result = assemblyDepot.getHeldItem().copy();
        assemblyDepot.setHeldItem(ItemStack.EMPTY);
        return result;
    }
}
