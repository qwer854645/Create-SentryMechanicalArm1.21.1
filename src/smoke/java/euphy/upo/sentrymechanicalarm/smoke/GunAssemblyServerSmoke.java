package euphy.upo.sentrymechanicalarm.smoke;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.deployer.*;
import com.simibubi.create.content.kinetics.press.*;
import com.simibubi.create.content.kinetics.saw.*;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import euphy.upo.sentrymechanicalarm.*;
import euphy.upo.sentrymechanicalarm.recipe.*;
import euphy.upo.sentrymechanicalarm.registry.*;
import euphy.upo.sentrymechanicalarm.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Opt-in dedicated-server tests. Actual machines execute each physical fixture step;
 * the harness transfers workpieces between machines instead of constructing belt logistics. */
@EventBusSubscriber(modid = "sentrymechanicalarm")
public final class GunAssemblyServerSmoke {
    private static ServerLevel level;
    private static int passed, failed, ticks, phase;
    private static boolean running;
    private static int nativeCount, choiceCount;
    private static GunAssemblyRecipes.Choice batchFixture;
    private static Set<ResourceLocation> nativeAmmo;
    private static final List<Job> jobs = new ArrayList<>();
    private static final Set<ChunkPos> forced = new HashSet<>();
    private static CompletableFuture<Void> reload;
    private interface Check { void run() throws Exception; }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void test(String name, Check action) {
        try { action.run(); passed++; SentryMechanicalArm.LOGGER.info("GUN_ASSEMBLY PASS {}", name); }
        catch (Throwable error) { failed++; SentryMechanicalArm.LOGGER.error("GUN_ASSEMBLY FAIL " + name, error); }
    }
    private static Set<ResourceLocation> nativeIds(boolean guns) {
        var ids = new HashSet<ResourceLocation>();
        for (var h : level.getRecipeManager().getRecipes()) if (h.value() instanceof GunSmithTableRecipe r
                && (IGun.getIGunOrNull(r.getOutput()) != null) == guns) ids.add(h.id());
        return ids;
    }
    private static List<RecipeHolder<?>> sequences() {
        return level.getRecipeManager().getRecipes().stream().filter(h -> h.id().getNamespace().equals("sentrymechanicalarm")
                && h.id().getPath().startsWith("gun_manufacturing/") && !h.id().getPath().startsWith("gun_manufacturing/attachments/") && h.value() instanceof SequencedAssemblyRecipe)
                .sorted(Comparator.comparing(RecipeHolder::id)).toList();
    }
    private static ItemStack runNative(SequencedAssemblyRecipe recipe) {
        var item = recipe.getIngredient().getItems()[0].copy();
        for (var step : recipe.getSequence()) {
            ProcessingRecipe<?, ?> selected;
            if (step.getRecipe() instanceof DeployerApplicationRecipe deploying) {
                var inv = new ItemStackHandler(2); inv.setStackInSlot(0, item);
                inv.setStackInSlot(1, deploying.getRequiredHeldItem().getItems()[0].copy());
                selected = SequencedAssemblyRecipe.getRecipe(level, new RecipeWrapper(inv), AllRecipeTypes.DEPLOYING.getType(), DeployerApplicationRecipe.class).orElseThrow().value();
            } else if (step.getRecipe() instanceof CuttingRecipe)
                selected = SequencedAssemblyRecipe.getRecipe(level, item, AllRecipeTypes.CUTTING.getType(), CuttingRecipe.class).orElseThrow().value();
            else selected = SequencedAssemblyRecipe.getRecipe(level, new SingleRecipeInput(item), AllRecipeTypes.PRESSING.getType(), PressingRecipe.class).orElseThrow().value();
            item = selected.rollResults(level.random).getFirst();
        }
        check(ItemStack.matches(item, recipe.getResultItem(level.registryAccess())), "native execution changed output/components/count");
        return item;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void start(ServerStartedEvent event) {
        if (!"gunassembly".equals(System.getProperty("sentry.smoke"))) return;
        running = true; level = event.getServer().overworld();
        test("component data boundaries and receiver entrance validation", () -> {
            int checks=GunManufacturingRulesSmoke.verify();
            SentryMechanicalArm.LOGGER.info("GUN_ASSEMBLY component rule boundary checks={}",checks);
        });
        test("native gun recipes are initially available", () -> {
            nativeCount = nativeIds(true).size(); nativeAmmo = nativeIds(false); choiceCount = GunAssemblyRecipes.choices(level).stream().filter(c -> !AssemblyTargets.attachment(c)).toList().size();
            check(nativeCount > 0 && choiceCount > 0, "missing native sources or generated choices");
        });
        test("all generated part and gun sequences execute through native Create matching", () -> {
            int parts = 0, guns = 0;
            for (var holder : sequences()) { runNative((SequencedAssemblyRecipe)holder.value());
                if (holder.id().getPath().contains("/parts/")) parts++; else guns++;
            }
            check(parts == GunParts.names().size() && guns == choiceCount, "unexpected route counts: " + parts + "/" + guns);
            SentryMechanicalArm.LOGGER.info("GUN_ASSEMBLY native routes parts={} guns={}", parts, guns);
        });
        test("gun input and target data survive recipe network serialization", () -> {
            var choice = GunAssemblyRecipes.choices(level).stream().filter(c -> !AssemblyTargets.attachment(c)).toList().getFirst();
            var buffer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
            try {
                RecipeHolder.STREAM_CODEC.encode(buffer, new RecipeHolder<>(choice.id(), choice.recipe()));
                var decoded = (SequencedAssemblyRecipe)RecipeHolder.STREAM_CODEC.decode(buffer).value();
                check(decoded.getIngredient().test(choice.input().prepared()), "network lost target identity");
                check(!decoded.getIngredient().test(choice.input().receiver().getItems()[0]), "network accepts unselected receiver");
            } finally { buffer.release(); }
        });
        test("all attachment sequences execute with native matching", () -> {
            var choices=GunAssemblyRecipes.choices(level).stream().filter(AssemblyTargets::attachment).toList();
            check(!choices.isEmpty(),"missing attachment choices");
            for(var c:choices)runNative(c.recipe());
            SentryMechanicalArm.LOGGER.info("ATTACHMENT_AUDIT native sequences={}",choices.size());
        });
        test("attachment takeover restores recipes and keeps unrelated native outputs", () -> {
            var original=List.copyOf(level.getRecipeManager().getRecipes());
            var before=GunAssemblyRecipes.choices(level).stream().filter(AssemblyTargets::attachment).map(GunAssemblyRecipes.Choice::id).collect(java.util.stream.Collectors.toSet());
            try {
                SMAServerConfig.REMOVE_NATIVE_ATTACHMENT_RECIPES.set(true);DynamicRecipeManager.rebuild(level.getRecipeManager());
                check(GunAssemblyRecipes.choices(level).stream().filter(AssemblyTargets::attachment).map(GunAssemblyRecipes.Choice::id).collect(java.util.stream.Collectors.toSet()).equals(before),"takeover lost attachment sources");
                long nativeBefore=original.stream().filter(h->h.value() instanceof GunSmithTableRecipe r&&com.tacz.guns.api.item.IAttachment.getIAttachmentOrNull(r.getOutput())!=null).count();
                long nativeAfter=level.getRecipeManager().getRecipes().stream().filter(h->h.value() instanceof GunSmithTableRecipe r&&com.tacz.guns.api.item.IAttachment.getIAttachmentOrNull(r.getOutput())!=null).count();
                check(nativeAfter<nativeBefore,"takeover removed no recipes");
                DynamicRecipeManager.rebuild(level.getRecipeManager());
                check(GunAssemblyRecipes.choices(level).stream().filter(AssemblyTargets::attachment).count()==before.size(),"repeated rebuild lost attachment sources");
            } finally {SMAServerConfig.REMOVE_NATIVE_ATTACHMENT_RECIPES.set(false);DynamicRecipeManager.rebuild(level.getRecipeManager());}
            check(nativeAmmo.equals(nativeIds(false)),"restoring attachments changed native ammo/attachment recipes");
        });
        test("attachment batch retains all bases and output components", () -> {
            var manager=level.getRecipeManager();var saved=List.copyOf(manager.getRecipes());
            var c=GunAssemblyRecipes.choices(level).stream().filter(AssemblyTargets::attachment).findFirst().orElseThrow();
            var output=c.output().copyWithCount(4);output.set(DataComponents.CUSTOM_NAME,Component.literal("Batch data preserved"));
            var sourceId=ResourceLocation.parse("audit:attachment_batch");
            var source=new GunSmithTableRecipe(new com.tacz.guns.crafting.result.GunSmithTableResult(output,null),List.of(new com.tacz.guns.crafting.GunSmithTableIngredient(Ingredient.of(Items.IRON_INGOT),4)));
            try {
                var inputs=new ArrayList<RecipeHolder<?>>();
                for(var h:saved)if(!(h.value() instanceof GunSmithTableRecipe r&&com.tacz.guns.api.item.IAttachment.getIAttachmentOrNull(r.getOutput())!=null&&com.tacz.guns.api.item.IAttachment.getIAttachmentOrNull(r.getOutput()).getAttachmentId(r.getOutput()).equals(c.gun())))inputs.add(h);
                inputs.add(new RecipeHolder<>(sourceId,source));manager.replaceRecipes(inputs);
                var built=AttachmentAssemblyRecipes.build(manager,level.getServer());
                var route=built.recipes().stream().filter(h->h.value() instanceof SequencedAssemblyRecipe r&&ItemStack.matches(r.getResultItem(level.registryAccess()),output)).findFirst().orElseThrow();
                var recipe=(SequencedAssemblyRecipe)route.value();int bases=1;
                for(var step:recipe.getSequence())if(step.getRecipe() instanceof DeployerApplicationRecipe r){var material=r.getRequiredHeldItem();if(java.util.Arrays.stream(material.getItems()).allMatch(i->i.getItem() instanceof euphy.upo.sentrymechanicalarm.content.AttachmentBaseItem))bases+=CountedDeployerIngredient.amount(material);}
                check(bases==4,"batch undercharged bases: "+bases);
                check(built.replacedNative().contains(sourceId),"matching batch source not marked replaceable");
                inputs.add(route);manager.replaceRecipes(inputs);runNative(recipe);
                batchFixture=new GunAssemblyRecipes.Choice(route.id(),c.gun(),(GunAssemblyIngredient)recipe.getIngredient().getCustomIngredient(),output,recipe);
            } finally {manager.replaceRecipes(saved);}
        });
        test("forced ammo casing list and global override extend eligibility", () -> {
            var excluded = TimelessAPI.getAllCommonAmmoIndex().stream().map(Map.Entry::getKey).filter(id -> !SentryCasingProfiles.enabled(id)).findFirst().orElseThrow();
            SMAServerConfig.FORCED_CASING_AMMO.set(List.of(excluded.toString())); SentryCasingProfiles.rebuild(event.getServer());
            check(SentryCasingProfiles.enabled(excluded) && SentryCasingProfiles.drop(excluded).enabled(), "single-ammo override failed");
            check(SentryCasingProfiles.allowsGun(ResourceLocation.parse("test:without_ejection"),excluded), "forced ammo did not bypass per-gun check");
            SMAServerConfig.FORCED_CASING_AMMO.set(List.of()); SMAServerConfig.FORCE_ALL_CASING_AMMO.set(true); SentryCasingProfiles.rebuild(event.getServer());
            check(SentryCasingProfiles.enabled(excluded), "global override failed");
            SMAServerConfig.FORCE_ALL_CASING_AMMO.set(false); SentryCasingProfiles.rebuild(event.getServer());
            check(!SentryCasingProfiles.enabled(excluded), "forced eligibility persisted after disabling");
            check(!SentryCasingProfiles.allowsGun(ResourceLocation.parse("test:without_ejection"),excluded), "forced per-gun bypass persisted after disabling");
        });
        test("native firearm removal preserves ammo recipes and assembly sources", () -> {
            SMAServerConfig.REMOVE_NATIVE_GUN_RECIPES.set(true); DynamicRecipeManager.rebuild(level.getRecipeManager());
            check(nativeIds(true).isEmpty(), "native guns still craftable"); check(nativeAmmo.equals(nativeIds(false)), "ammo/attachment recipes changed");
            check(GunAssemblyRecipes.choices(level).stream().filter(c -> !AssemblyTargets.attachment(c)).toList().size() == choiceCount, "assemblies lost native source data");
            DynamicRecipeManager.rebuild(level.getRecipeManager());
            check(GunAssemblyRecipes.choices(level).stream().filter(c -> !AssemblyTargets.attachment(c)).toList().size() == choiceCount, "repeated rebuild lost hidden sources");
            SMAServerConfig.REMOVE_NATIVE_GUN_RECIPES.set(false); DynamicRecipeManager.rebuild(level.getRecipeManager());
            check(nativeIds(true).size() == nativeCount, "native recipes did not restore");
            SMAServerConfig.REMOVE_NATIVE_GUN_RECIPES.set(true); DynamicRecipeManager.rebuild(level.getRecipeManager());
        });
        test("physical fixtures can be prepared", () -> {
            for (var holder : sequences()) if (holder.id().getPath().contains("/parts/"))
                add(new Job(holder.id().toString(), (SequencedAssemblyRecipe)holder.value(), null));
            var samples = new LinkedHashMap<String, GunAssemblyRecipes.Choice>();
            for (var choice : GunAssemblyRecipes.choices(level).stream().filter(c -> !AssemblyTargets.attachment(c)).toList()) {
                String type = TimelessAPI.getCommonGunIndex(choice.gun()).orElseThrow().getType();
                samples.putIfAbsent("type:" + type, choice); samples.putIfAbsent("pack:" + choice.gun().getNamespace(), choice);
            }
            for (var choice : new LinkedHashSet<>(samples.values())) add(new Job(choice.gun().toString(), choice.recipe(), choice));
            var attachmentSamples=new LinkedHashMap<String,GunAssemblyRecipes.Choice>();
            for(var c:GunAssemblyRecipes.choices(level).stream().filter(AssemblyTargets::attachment).toList())attachmentSamples.putIfAbsent(AssemblyTargets.type(c),c);
            for(var c:attachmentSamples.values())add(new Job("attachment:"+c.gun(),c.recipe(),c));
            if(batchFixture!=null){
                var active=new ArrayList<>(level.getRecipeManager().getRecipes());active.add(new RecipeHolder<>(batchFixture.id(),batchFixture.recipe()));
                active.add(GunAssemblyCopying.recipe(batchFixture,ItemStack.EMPTY));level.getRecipeManager().replaceRecipes(active);
                add(new Job("attachment:four_item_batch",batchFixture.recipe(),batchFixture));
            }
            check(jobs.size() > GunParts.names().size(), "missing physical gun samples");
        });
    }
    private static void add(Job job) {
        int n = jobs.size(); job.pos = new BlockPos(640 + (n % 7) * 8, 240, 640 + (n / 7) * 8);
        var chunk = new ChunkPos(job.pos); if (forced.add(chunk)) level.setChunkForced(chunk.x, chunk.z, true);
        var sawChunk = new ChunkPos(job.pos.east(2)); if (forced.add(sawChunk)) level.setChunkForced(sawChunk.x, sawChunk.z, true);
        level.getChunkAt(job.pos);
        level.setBlock(job.pos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(job.pos.above(2), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(job.pos.east(2), Blocks.AIR.defaultBlockState(), 3);
        for (var item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(job.pos).inflate(3))) item.discard();
        level.setBlock(job.pos, AllBlocks.DEPOT.getDefaultState(), 3);
        job.depot = (DepotBlockEntity)level.getBlockEntity(job.pos); jobs.add(job); job.begin();
    }
    private static final class Job {
        final String label; final SequencedAssemblyRecipe assembly; final GunAssemblyRecipes.Choice gun;
        BlockPos pos; DepotBlockEntity depot; DeployerBlockEntity hand; MechanicalPressBlockEntity press; SawBlockEntity saw;
        ItemStack current, material = ItemStack.EMPTY, tool = ItemStack.EMPTY; int step, since; boolean copy, loaded, done;
        Job(String label, SequencedAssemblyRecipe assembly, GunAssemblyRecipes.Choice gun) {
            this.label = label; this.assembly = assembly; this.gun = gun; copy = gun != null;
            current = gun == null ? assembly.getIngredient().getItems()[0].copy() : gun.input().receiver().getItems()[0].copy();
            if (gun != null) current.set(DataComponents.CUSTOM_NAME, Component.literal("Original " + label));
        }
        void begin() {
            hand = null; press = null; saw = null; loaded = false; since = ticks;
            depot.setHeldItem(current.copy());
            if (copy || assembly.getSequence().get(step).getRecipe() instanceof DeployerApplicationRecipe) {
                level.setBlock(pos.above(2), AllBlocks.DEPLOYER.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.DOWN), 3);
                hand = (DeployerBlockEntity)level.getBlockEntity(pos.above(2));
                material = copy ? gun.input().prepared() : ((DeployerApplicationRecipe)assembly.getSequence().get(step).getRecipe()).getRequiredHeldItem().getItems()[0].copy();
                if (copy) material.set(ModDataComponents.GUN_RECEIVER.get(), net.minecraft.world.item.component.ItemContainerContents.fromItems(List.of(current.copyWithCount(1))));
                tool = material.copy();
            } else if (assembly.getSequence().get(step).getRecipe() instanceof CuttingRecipe) {
                depot.setHeldItem(ItemStack.EMPTY);
                level.setBlock(pos.east(2), AllBlocks.MECHANICAL_SAW.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.UP), 3);
                saw = (SawBlockEntity)level.getBlockEntity(pos.east(2));
                check(saw.inventory.insertItem(0, current.copy(), false).isEmpty(), "saw input rejected " + label);
                loaded = true;
            } else {
                level.setBlock(pos.above(2), AllBlocks.MECHANICAL_PRESS.getDefaultState(), 3);
                press = (MechanicalPressBlockEntity)level.getBlockEntity(pos.above(2)); loaded = true;
            }
        }
        void tick() {
            if (done) return;
            if (ticks - since > 300) throw new AssertionError("machine timeout " + label + " step=" + step + " copy=" + copy + " held=" + depot.getHeldItem());
            if (hand != null) {
                hand.setSpeed(256); if (hand.getPlayer() == null) return;
                if (!loaded) { hand.getPlayer().setItemInHand(InteractionHand.MAIN_HAND, material.copy()); loaded = true; }
            }
            if (press != null) press.setSpeed(256);
            if (saw != null) saw.setSpeed(256);
            // Cutting clears slot 0 and writes the first result to output slot 1.
            var output = saw == null ? depot.getHeldItem() : saw.inventory.appliedRecipe ? saw.inventory.getStackInSlot(1) : ItemStack.EMPTY;
            if (output.isEmpty() || ItemStack.matches(output, current)) return;
            if (copy) {
                check(gun.input().test(output), "copy produced wrong target " + label);
                check(ItemStack.matches(tool, hand.getPlayer().getMainHandItem()), "copy consumed template " + label);
                check(ItemStack.isSameItemSameComponents(current, output.get(ModDataComponents.GUN_RECEIVER.get()).copyOne()), "copy lost receiver data " + label);
                current = output.copy(); copy = false;
            } else {
                if (hand != null) check(hand.getPlayer().getMainHandItem().isEmpty(), "wrong material debit " + label + " step=" + step);
                step++; current = output.copy();
                if (step == assembly.getSequence().size()) {
                    check(ItemStack.matches(current, assembly.getResultItem(level.registryAccess())), "wrong physical result " + label);
                    done = true; passed++; SentryMechanicalArm.LOGGER.info("GUN_ASSEMBLY PASS physical {} -> {}", label, current);
                } else {
                    var progress = current.get(AllDataComponents.SEQUENCED_ASSEMBLY);
                    check(progress != null && progress.step() == step, "wrong physical progress " + label);
                }
            }
            if (hand != null && hand.getPlayer() != null) hand.getPlayer().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            if (saw != null) saw.inventory.clear();
            depot.setHeldItem(ItemStack.EMPTY); level.setBlock(pos.above(2), Blocks.AIR.defaultBlockState(), 3); level.setBlock(pos.east(2), Blocks.AIR.defaultBlockState(), 3);
            if (!done) begin();
        }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!running) return; ticks++;
        if (ticks > 2600) { failed++; SentryMechanicalArm.LOGGER.error("GUN_ASSEMBLY FAIL overall timeout"); finish(); return; }
        if (phase == 0) {
            for (var job : jobs) if (!job.done) try { job.tick(); }
            catch (Throwable error) { job.done = true; failed++; SentryMechanicalArm.LOGGER.error("GUN_ASSEMBLY FAIL physical " + job.label, error); }
            if (!jobs.isEmpty() && jobs.stream().allMatch(j -> j.done)) {
                phase = 1; reload = level.getServer().reloadResources(level.getServer().getPackRepository().getSelectedIds());
            }
        } else if (phase == 1 && reload.isDone()) {
            test("real data reload retains assembly while removing native guns", () -> {
                reload.join(); check(nativeIds(true).isEmpty(), "native guns returned after reload");
                check(GunAssemblyRecipes.choices(level).stream().filter(c -> !AssemblyTargets.attachment(c)).toList().size() == choiceCount, "reload lost assembly sources");
                check(nativeAmmo.equals(nativeIds(false)), "reload lost other gunsmith recipes");
            });
            test("disabling removal restores current native recipes after reload", () -> {
                SMAServerConfig.REMOVE_NATIVE_GUN_RECIPES.set(false); DynamicRecipeManager.rebuild(level.getRecipeManager());
                check(nativeIds(true).size() == nativeCount, "native recipes not restored");
            });
            finish();
        }
    }
    private static void finish() {
        running = false; for (var chunk : forced) level.setChunkForced(chunk.x, chunk.z, false);
        SentryMechanicalArm.LOGGER.info("GUN_ASSEMBLY RESULT passed={} failed={} fixtures={} ticks={}", passed, failed, jobs.size(), ticks);
        level.getServer().halt(false);
    }
}
