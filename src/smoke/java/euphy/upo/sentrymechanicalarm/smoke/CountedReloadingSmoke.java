package euphy.upo.sentrymechanicalarm.smoke;

import com.simibubi.create.*;
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
    static final ResourceLocation SHOT = ResourceLocation.parse("tacz:12g");
    static final BlockPos POS = DEPOT.offset(0, 0, 4);
    static DepotBlockEntity depot;
    static DeployerBlockEntity machine;
    static ItemStack initial;
    static int start = -1;
    static boolean finished;

    static void verify() {
        test("12g assembly is five stages with unchanged full material counts", () -> {
            var seq = (SequencedAssemblyRecipe) ReloadingRecipes.find(level.getRecipeManager(), SHOT).orElseThrow().value();
            check(seq.getSequence().size() == 5, "shotgun recipe still expands each material unit");
            var materials = seq.getSequence().stream().filter(s -> s.getRecipe() instanceof DeployerApplicationRecipe)
                    .map(s -> s.getRecipe().getIngredients().get(1)).toList();
            check(CountedDeployerIngredient.amount(materials.get(1)) == 10, "powder charge count lost");
            check(CountedDeployerIngredient.amount(materials.get(2)) == 4, "copper count lost");
            check(materials.get(1).getItems()[0].getCount() == 10, "recipe viewer sample lost quantity");
            var ops = level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
            var copy = (SequencedAssemblyRecipe) Recipe.CODEC.parse(ops, Recipe.CODEC.encodeStart(ops, seq).getOrThrow()).getOrThrow();
            check(CountedDeployerIngredient.amount(copy.getSequence().get(1).getRecipe().getIngredients().get(1)) == 10, "JSON lost charge count");
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
            try { RecipeHolder.STREAM_CODEC.encode(buffer, ReloadingRecipes.find(level.getRecipeManager(), SHOT).orElseThrow());
                var restored = (SequencedAssemblyRecipe) RecipeHolder.STREAM_CODEC.decode(buffer).value();
                check(CountedDeployerIngredient.amount(restored.getSequence().get(2).getRecipe().getIngredients().get(1)) == 4, "network lost copper count");
            } finally { buffer.release(); }
        });
        test("compact recipe audit records actual synchronized quantities", () -> {
            var report=new com.google.gson.JsonArray();
            for(var h:level.getRecipeManager().getRecipes())if(h.id().getNamespace().equals("sentrymechanicalarm")&&h.id().getPath().startsWith("casing_reloading/")){
                var seq=(SequencedAssemblyRecipe)h.value();var row=new com.google.gson.JsonObject();
                row.addProperty("ammo",IAmmo.getIAmmoOrNull(seq.getResultItem(level.registryAccess())).getAmmoId(seq.getResultItem(level.registryAccess())).toString());
                row.addProperty("stages",seq.getSequence().size());var materials=new com.google.gson.JsonArray();
                for(var step:seq.getSequence())if(step.getRecipe() instanceof DeployerApplicationRecipe){var ingredient=step.getRecipe().getIngredients().get(1);var material=new com.google.gson.JsonObject();material.addProperty("item",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ingredient.getItems()[0].getItem()).toString());material.addProperty("count",CountedDeployerIngredient.amount(ingredient));materials.add(material);}
                row.add("materials",materials);report.add(row);
            }
            check(!report.isEmpty(),"no compact recipes audited");java.nio.file.Files.writeString(java.nio.file.Path.of("../../evidence/alpha27-recipes.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        });
        test("ordinary recipes and reusable molds do not acquire counted consumption", () -> {
            var ordinary = (DeployerApplicationRecipe) level.getRecipeManager().byKey(ResourceLocation.parse("sentrymechanicalarm:deploying_primer")).orElseThrow().value();
            check(CountedDeployerIngredient.from(ordinary.getRequiredHeldItem()) == null, "primer recipe changed");
            var mold = (DeployerApplicationRecipe) CasingFormingRecipes.find(level.getRecipeManager(), AMMO).orElseThrow().value();
            check(mold.shouldKeepHeldItem() && CountedDeployerIngredient.from(mold.getRequiredHeldItem()) == null, "mold lost reuse");
            var tools = CountedDeployerIngredient.compact(List.of(Ingredient.of(Items.IRON_PICKAXE), Ingredient.of(Items.IRON_PICKAXE)));
            check(tools.size() == 2, "tool damage compressed into stack consumption");
            var containers = CountedDeployerIngredient.compact(List.of(Ingredient.of(Items.MILK_BUCKET), Ingredient.of(Items.MILK_BUCKET)));
            check(containers.size() == 2, "container returns lost");
        });
    }

    static void setup() {
        var inv = new net.neoforged.neoforge.items.ItemStackHandler(2);
        inv.setStackInSlot(0, casing(SHOT, 1)); inv.setStackInSlot(1, new ItemStack(SentryRegistry.PRIMER.get()));
        var first = SequencedAssemblyRecipe.getRecipe(level, new net.neoforged.neoforge.items.wrapper.RecipeWrapper(inv),
                AllRecipeTypes.DEPLOYING.getType(), DeployerApplicationRecipe.class).orElseThrow().value();
        initial = first.rollResults(level.random).getFirst();
        level.setBlock(POS, AllBlocks.DEPOT.get().defaultBlockState(), 3);
        depot = (DepotBlockEntity) level.getBlockEntity(POS); depot.setHeldItem(initial.copy());
        level.setBlock(POS.above(2), AllBlocks.DEPLOYER.get().defaultBlockState().setValue(
                com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING, Direction.DOWN), 3);
        machine = (DeployerBlockEntity) level.getBlockEntity(POS.above(2));
    }

    static void tick() {
        if (finished || depot == null) return;
        if (machine != null) {
            machine.setSpeed(256);
            if (machine.getPlayer() == null) return;
            if (start < 0) { start = ticks; machine.getPlayer().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SentryRegistry.SMALL_GUNPOWDER.get(), 3)); }
        }
        if (start < 0) return;
        int elapsed = ticks - start;
        if (elapsed == 120) {
            test("insufficient charge waits without consuming powder or advancing the case", () -> {
                check(ItemStack.matches(depot.getHeldItem(), initial), "underpaid assembly advanced");
                check(machine.getPlayer().getMainHandItem().getCount() == 3, "partial stock consumed");
            });
            machine.getPlayer().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SentryRegistry.SMALL_GUNPOWDER.get(), 13));
        }
        if (elapsed == 240) {
            test("one real mechanical charge consumes ten portions exactly", () -> {
                check(machine.getPlayer().getMainHandItem().getCount() == 3, "charge was not ten portions");
                check(depot.getHeldItem().get(AllDataComponents.SEQUENCED_ASSEMBLY).step() == 2, "charge did not advance once");
            });
            machine.getPlayer().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllItems.COPPER_NUGGET.get(), 7));
        }
        if (elapsed == 360) {
            test("one real metal step consumes four nuggets exactly", () -> {
                check(machine.getPlayer().getMainHandItem().getCount() == 3, "metal step was not four nuggets");
                check(depot.getHeldItem().get(AllDataComponents.SEQUENCED_ASSEMBLY).step() == 3, "metal did not advance once");
            });
            machine.getPlayer().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_NUGGET, 3));
        }
        if (elapsed == 480) {
            test("ordinary one-nugget step retains native consumption", () -> check(machine.getPlayer().getMainHandItem().getCount() == 2, "one-item step changed"));
            machine = null; level.setBlock(POS.above(2), AllBlocks.MECHANICAL_PRESS.get().defaultBlockState(), 3);
        }
        if (elapsed >= 481 && elapsed <= 660 && level.getBlockEntity(POS.above(2)) instanceof MechanicalPressBlockEntity press) press.setSpeed(256);
        if (elapsed == 660) {
            test("short shotgun sequence outputs one correct round", () -> {
                var output = depot.getHeldItem(); var api = IAmmo.getIAmmoOrNull(output);
                check(api != null && SHOT.equals(api.getAmmoId(output)) && output.getCount() == 1, "wrong final output");
            });
            finished = true;
        }
    }
}
