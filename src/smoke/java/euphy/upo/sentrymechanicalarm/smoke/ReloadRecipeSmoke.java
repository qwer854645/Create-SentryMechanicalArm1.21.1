package euphy.upo.sentrymechanicalarm.smoke;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import euphy.upo.sentrymechanicalarm.*;
import euphy.upo.sentrymechanicalarm.content.*;
import euphy.upo.sentrymechanicalarm.recipe.*;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Real resource reloads in an isolated server world, including pack removal. */
@EventBusSubscriber(modid=SentryMechanicalArm.MODID)
public final class ReloadRecipeSmoke {
    static int passed,failed,stage,ticks;static MinecraftServer server;static Path pack;
    static CompletableFuture<Void> pending;static SentryArmBlockEntity arm;
    static net.neoforged.neoforge.common.util.FakePlayer oldPlayer;
    static ResourceLocation cutId=ResourceLocation.parse("sentrymechanicalarm:ammo_cutting/tacz_9mm");
    static ResourceLocation assemblyId=ResourceLocation.parse("sentrymechanicalarm:ammo_assembly/tacz_9mm");
    static ResourceLocation smithId;static int baseCount,packCount;
    static final ResourceLocation GUN=ResourceLocation.parse("tacz:glock_17");
    static final ResourceLocation AMMO=ResourceLocation.parse("tacz:9mm");
    interface Check {void run()throws Exception;}
    static void test(String name,Check c){try{c.run();passed++;SentryMechanicalArm.LOGGER.info("RELOAD PASS {}",name);}catch(Throwable e){failed++;SentryMechanicalArm.LOGGER.error("RELOAD FAIL "+name,e);}}
    static void check(boolean c,String m){if(!c)throw new AssertionError(m);}
    static ItemStack template(int n){var s=new ItemStack(SentryRegistry.UNFINISHED_AMMO.get(),n);var t=new CompoundTag();t.putString("AmmoId",AMMO.toString());t.putInt("CopperSheets",0);t.putBoolean("GunpowderAdded",false);s.set(DataComponents.CUSTOM_DATA,CustomData.of(t));return s;}
    static long generated(){return server.getRecipeManager().getRecipes().stream().filter(h->h.id().getNamespace().equals("sentrymechanicalarm")&&h.id().getPath().startsWith("ammo_")&&h.id().getPath().contains("/")).count();}
    static void write(String file,String value)throws Exception{var p=pack.resolve(file);Files.createDirectories(p.getParent());Files.writeString(p,value);}
    static void reload(boolean include){server.getPackRepository().reload();var ids=new ArrayList<>(server.getPackRepository().getSelectedIds());ids.remove("file/sentry-reload-smoke");if(include)ids.add("file/sentry-reload-smoke");pending=server.reloadResources(ids);ticks=0;}
    static void assemble(RecipeHolder<?> holder){
        var assembly=(SequencedAssemblyRecipe)holder.value();
        ItemStack item=assembly.getIngredient().getItems()[0].copy();
        var expected=assembly.getResultItem(server.registryAccess());
        for(var step:assembly.getSequence()){
            com.simibubi.create.content.processing.recipe.ProcessingRecipe<?,?> selected;
            if(step.getRecipe() instanceof com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe){
                var inventory=new net.neoforged.neoforge.items.ItemStackHandler(2);inventory.setStackInSlot(0,item);
                inventory.setStackInSlot(1,step.getRecipe().getIngredients().get(1).getItems()[0].copy());
                selected=SequencedAssemblyRecipe.getRecipe(server.overworld(),new net.neoforged.neoforge.items.wrapper.RecipeWrapper(inventory),
                        com.simibubi.create.AllRecipeTypes.DEPLOYING.getType(),com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe.class).orElseThrow().value();
            }else{
                selected=SequencedAssemblyRecipe.getRecipe(server.overworld(),new SingleRecipeInput(item),
                        com.simibubi.create.AllRecipeTypes.PRESSING.getType(),com.simibubi.create.content.kinetics.press.PressingRecipe.class).orElseThrow().value();
            }
            var outputs=selected.rollResults(server.overworld().random);
            check(outputs.size()==1,"unexpected step outputs");item=outputs.getFirst();
        }
        check(ItemStack.matches(item,expected),"native sequence output differs: "+holder.id());
    }
    @SubscribeEvent public static void start(ServerStartedEvent event){
        if(!"reload".equals(System.getProperty("sentry.smoke")))return;
        server=event.getServer();
        test("startup recipes exist",()->{check(server.getRecipeManager().byKey(cutId).isPresent(),"cutting missing");check(server.getRecipeManager().byKey(assemblyId).isPresent(),"assembly missing");baseCount=(int)generated();check(baseCount>0,"no dynamic recipes");});
        test("Create 1.21 singular block and item tags",()->{
            check(SentryRegistry.SENTRY_ARM_BLOCK.get().defaultBlockState().is(TagKey.create(Registries.BLOCK,ResourceLocation.parse("create:wrench_pickup"))),"wrench tag missing");
            check(new ItemStack(SentryRegistry.APPLE_PIE.get()).is(TagKey.create(Registries.ITEM,ResourceLocation.parse("create:upright_on_belt"))),"belt tag missing");
        });
        test("crafting pays one item per slot",()->{
            var recipe=new AmmoTemplateRecipe(CraftingBookCategory.MISC);
            for(int n:new int[]{1,2,64}){var input=CraftingInput.of(2,1,List.of(AmmoItemBuilder.create().setId(AMMO).setCount(n).build(),new ItemStack(AllItems.COPPER_SHEET.get(),n)));check(recipe.matches(input,server.overworld()),"template mismatch");check(recipe.assemble(input,server.registryAccess()).getCount()==1,"stack count multiplied output");}
            check(!recipe.matches(CraftingInput.of(2,1,List.of(new ItemStack(Items.DIAMOND),new ItemStack(AllItems.COPPER_SHEET.get()))),server.overworld()),"invalid ingredient accepted");
        });
        test("component ingredient and recipe network round trip",()->{
            var recipe=(SequencedAssemblyRecipe)server.getRecipeManager().byKey(assemblyId).orElseThrow().value();
            check(recipe.getIngredient().test(template(1)),"legitimate template rejected");
            var wrong=template(1);var t=wrong.get(DataComponents.CUSTOM_DATA).copyTag();t.putString("AmmoId","tacz:45acp");wrong.set(DataComponents.CUSTOM_DATA,CustomData.of(t));check(!recipe.getIngredient().test(wrong),"wrong caliber accepted");
            var ops=server.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
            var json=Recipe.CODEC.encodeStart(ops,recipe).getOrThrow();var decoded=(SequencedAssemblyRecipe)Recipe.CODEC.parse(ops,json).getOrThrow();check(decoded.getIngredient().test(template(1))&&!decoded.getIngredient().test(wrong),"ingredient lost through JSON codec");
            var buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),server.registryAccess());
            try {RecipeHolder.STREAM_CODEC.encode(buf,server.getRecipeManager().byKey(assemblyId).orElseThrow());var netRecipe=(SequencedAssemblyRecipe)RecipeHolder.STREAM_CODEC.decode(buf).value();check(netRecipe.getIngredient().test(template(1))&&!netRecipe.getIngredient().test(wrong),"network ingredient mismatch");} finally {buf.release();}
        });
        test("legacy belt debits the full processed count and preserves remainder",()->{
            var input=template(64);var held=new ItemStack(AllItems.COPPER_SHEET.get(),3);var r=LegacyAmmoProcessing.apply(input,held,100);
            check(r!=null&&r.processed().getCount()==3&&r.remainder().getCount()==61&&held.isEmpty(),"incorrect material debit/split");check(UnfinishedAmmoItem.getCopperSheets(r.processed())==1&&UnfinishedAmmoItem.getCopperSheets(r.remainder())==0,"unpaid progress");
            var powder=new ItemStack(Items.GUNPOWDER,2);var r2=LegacyAmmoProcessing.apply(r.processed(),powder,300);check(r2!=null&&r2.processed().getCount()==2&&r2.remainder().getCount()==1&&powder.isEmpty(),"powder debit/split");
            check(LegacyAmmoProcessing.apply(r2.processed(),new ItemStack(Items.GUNPOWDER),500)==null,"duplicate powder accepted");
            check(LegacyAmmoProcessing.apply(r.processed(),new ItemStack(AllItems.COPPER_SHEET.get()),110)==null,"cooldown ignored");
        });
        test("unsupported materials cannot use legacy pressing",()->{
            check(!DynamicRecipeManager.supportsLegacyPressing(new AmmoRecipeConfig.Config(AmmoRecipeConfig.AmmoCategory.DEFAULT,List.of(Ingredient.of(Items.TNT),Ingredient.of(Items.GUNPOWDER)),1)),"TNT bypass");
            check(DynamicRecipeManager.supportsLegacyPressing(new AmmoRecipeConfig.Config(AmmoRecipeConfig.AmmoCategory.DEFAULT,List.of(Ingredient.of(AllItems.COPPER_SHEET.get()),Ingredient.of(Items.GUNPOWDER)),1)),"simple legacy route lost");
        });
        test("malformed unfinished id safely rejected",()->{var s=template(1);var t=new CompoundTag();t.putString("AmmoId","invalid ID!!");s.set(DataComponents.CUSTOM_DATA,CustomData.of(t));check(UnfinishedAmmoItem.getAmmoId(s)==null,"invalid id accepted");});
        test("native pressing enforces caliber and fully paid progress",()->{
            int checked=0;
            for(var holder:server.getRecipeManager().getRecipes()) {
                if(!holder.id().getNamespace().equals("sentrymechanicalarm")||!holder.id().getPath().startsWith("ammo_pressing/"))continue;
                var recipe=(com.simibubi.create.content.kinetics.press.PressingRecipe)holder.value();
                var complete=recipe.getIngredients().getFirst().getItems()[0].copy();
                check(recipe.matches(new SingleRecipeInput(complete),server.overworld()),"paid item rejected: "+holder.id());
                var tag=complete.get(DataComponents.CUSTOM_DATA).copyTag();tag.putBoolean("GunpowderAdded",false);complete.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
                check(!recipe.matches(new SingleRecipeInput(complete),server.overworld()),"unpaid powder accepted");
                tag.putBoolean("GunpowderAdded",true);tag.putString("AmmoId","smoke:wrong");complete.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
                check(!recipe.matches(new SingleRecipeInput(complete),server.overworld()),"wrong caliber accepted");checked++;
            }
            check(checked>0,"legacy fixtures absent");
        });
        test("idempotent recipe regeneration",()->{DynamicRecipeManager.rebuild(server.getRecipeManager());check(generated()==baseCount,"rebuild duplicated recipes");});
        test("all generated sequences complete through native Create processing",()->{
            int count=0;for(var holder:server.getRecipeManager().getRecipes())if(holder.id().getNamespace().equals("sentrymechanicalarm")&&holder.id().getPath().startsWith("ammo_assembly/")){assemble(holder);count++;}
            check(count==TimelessAPI.getAllCommonAmmoIndex().size(),"not all ammo sequences processed");
        });
        test("master crafting switch gates templates, materials, cached recipes and in-progress assembly",()->{
            var holder=server.getRecipeManager().byKey(assemblyId).orElseThrow();
            var sequence=(SequencedAssemblyRecipe)holder.value();
            var inventory=new net.neoforged.neoforge.items.ItemStackHandler(2);
            inventory.setStackInSlot(0,template(1));inventory.setStackInSlot(1,sequence.getSequence().getFirst().getRecipe().getIngredients().get(1).getItems()[0].copy());
            var first=SequencedAssemblyRecipe.getRecipe(server.overworld(),new net.neoforged.neoforge.items.wrapper.RecipeWrapper(inventory),com.simibubi.create.AllRecipeTypes.DEPLOYING.getType(),com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe.class).orElseThrow().value();
            var progress=first.rollResults(server.overworld().random).getFirst();
            var input=CraftingInput.of(2,1,List.of(AmmoItemBuilder.create().setId(AMMO).build(),new ItemStack(AllItems.COPPER_SHEET.get())));
            var recipe=new AmmoTemplateRecipe(CraftingBookCategory.MISC);
            var material=new ItemStack(AllItems.COPPER_SHEET.get(),4);
            try {
                SMAServerConfig.ENABLE_AMMO_CRAFTING.set(false);
                SMAServerConfig.ENABLE_AMMO_CRAFTING.clearCache(); // Test the next-world value without restarting the fixture.
                check(!recipe.matches(input,server.overworld())&&recipe.assemble(input,server.registryAccess()).isEmpty(),"template still craftable");
                check(LegacyAmmoProcessing.apply(template(3),material,200)==null&&material.getCount()==4,"legacy processing consumed materials");
                check(!sequence.getIngredient().test(template(1)),"cached ingredient still enabled");
                check(SequencedAssemblyRecipe.getRecipe(server.overworld(),progress,com.simibubi.create.AllRecipeTypes.DEPLOYING.getType(),com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe.class).isEmpty(),"in-progress assembly still enabled");
                for(var h:server.getRecipeManager().getRecipes())if(h.id().getPath().startsWith("ammo_pressing/")&&h.id().getNamespace().equals("sentrymechanicalarm")){
                    var press=(com.simibubi.create.content.kinetics.press.PressingRecipe)h.value();check(!press.matches(new SingleRecipeInput(press.getIngredients().getFirst().getItems()[0]),server.overworld()),"cached pressing still enabled");
                }
                DynamicRecipeManager.rebuild(server.getRecipeManager());check(generated()==0,"master off retained auto recipes");
                check(server.getRecipeManager().getRecipes().stream().anyMatch(h->h.value() instanceof GunSmithTableRecipe),"TaCZ recipes removed");
            } finally {SMAServerConfig.ENABLE_AMMO_CRAFTING.set(true);SMAServerConfig.ENABLE_AMMO_CRAFTING.clearCache();DynamicRecipeManager.rebuild(server.getRecipeManager());}
            check(generated()==baseCount&&recipe.matches(input,server.overworld()),"master re-enable failed");
        });
        test("prepare real resource pack and cached shooter",()->{
            smithId=server.getRecipeManager().getRecipes().stream().filter(h->h.value() instanceof GunSmithTableRecipe r && IAmmo.getIAmmoOrNull(r.getOutput())!=null&&AMMO.equals(IAmmo.getIAmmoOrNull(r.getOutput()).getAmmoId(r.getOutput()))).map(RecipeHolder::id).sorted().findFirst().orElseThrow();
            pack=server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("sentry-reload-smoke");
            write("pack.mcmeta","{\"pack\":{\"pack_format\":48,\"description\":\"isolated reload validation\"}}");
            write("data/sentrymechanicalarm/recipe/"+cutId.getPath()+".json","{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:dirt\"}],\"result\":{\"id\":\"minecraft:diamond\"}}");
            write("data/"+smithId.getNamespace()+"/recipe/"+smithId.getPath()+".json","{\"type\":\"tacz:gun_smith_table_crafting\",\"materials\":[{\"item\":{\"item\":\"minecraft:iron_ingot\"},\"count\":2},{\"item\":{\"item\":\"minecraft:gunpowder\"},\"count\":1}],\"result\":{\"type\":\"ammo\",\"group\":\"pd_cartridges\",\"id\":\"tacz:9mm\",\"count\":7}}");
            write("data/tacz/sentry_muzzles/glock_17.json","{\"muzzle\":[0.1,0.2,-1.234],\"forward\":[0,0,-1]}");
            arm=new SentryArmBlockEntity(new BlockPos(20,200,20),SentryRegistry.SENTRY_ARM_BLOCK.get().defaultBlockState());arm.setLevel(server.overworld());arm.setHeldItem(GunItemBuilder.create().setId(GUN).setAmmoCount(11).build());oldPlayer=SentryFakePlayer.get(arm);SentryFakePlayer.sync(oldPlayer,arm,0,0,arm.getHeldItem());check(!oldPlayer.getMainHandItem().isEmpty(),"shooter uninitialized");
        });
        test("player login does not invalidate running shooter or rebuild recipes",()->{
            var recipe=server.getRecipeManager().byKey(assemblyId).orElseThrow();
            DynamicRecipeManager.onDatapackSync(new net.neoforged.neoforge.event.OnDatapackSyncEvent(server.getPlayerList(),oldPlayer));
            check(server.getRecipeManager().byKey(assemblyId).orElseThrow()==recipe&&!oldPlayer.getMainHandItem().isEmpty()&&SentryFakePlayer.getArmFromPlayer(oldPlayer)==arm,"login invalidated shared state");
        });
        reload(true);stage=1;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(server==null||stage==0)return;
        if(++ticks>2400){test("reload deadline",()->{throw new AssertionError("timeout");});finish();return;}
        if(pending==null||!pending.isDone())return;
        test("actual resource reload stage "+stage,()->pending.join());
        if(pending.isCompletedExceptionally()){finish();return;}
        if(stage==1){
            test("data-pack recipe wins generated id",()->check(server.getRecipeManager().byKey(cutId).orElseThrow().value() instanceof ShapelessRecipe,"override overwritten"));
            test("TaCZ initialized materials drive regenerated assembly",()->{var r=(SequencedAssemblyRecipe)server.getRecipeManager().byKey(assemblyId).orElseThrow().value();check(r.getResultItem(server.registryAccess()).getCount()==7,"old output count");check(r.getSequence().size()==4,"old material steps");check(server.getRecipeManager().byKey(ResourceLocation.parse("sentrymechanicalarm:ammo_pressing/tacz_9mm")).isEmpty(),"iron bypass pressing retained");});
            test("reloaded sequence completes with new materials and count",()->assemble(server.getRecipeManager().byKey(assemblyId).orElseThrow()));
            test("server muzzle reload replaces old profile",()->check(Math.abs(SentryGunProfiles.get(GUN).muzzle().z+1.234)<1e-6,"old muzzle cache"));
            test("old shooter invalidated with ammo intact",()->{check(oldPlayer.getMainHandItem().isEmpty()&&SentryFakePlayer.getArmFromPlayer(oldPlayer)==null,"old shooter retained");check(IGun.getIGunOrNull(arm.getHeldItem()).getCurrentAmmoCount(arm.getHeldItem())==11,"ammo changed on reload");var fp=SentryFakePlayer.get(arm);check(fp!=oldPlayer,"old shooter reused");SentryFakePlayer.remove(arm);});
            packCount=(int)generated();reload(true);stage=2;
        }else if(stage==2){
            test("repeated reload retains override and no duplicates",()->{check(generated()==packCount,"recipe count drift");check(server.getRecipeManager().byKey(cutId).orElseThrow().value() instanceof ShapelessRecipe,"override drift");});
            SMAServerConfig.ENABLE_DYNAMIC_RECIPES.set(false);reload(true);stage=3;
        }else if(stage==3){
            test("disable removes generated recipes but keeps data-pack recipe",()->{check(generated()==1,"generated recipes left after disable: "+generated());check(server.getRecipeManager().byKey(cutId).isPresent(),"data-pack override removed");});
            SMAServerConfig.ENABLE_DYNAMIC_RECIPES.set(true);reload(false);stage=4;
        }else if(stage==4){
            test("pack removal restores original recipes and muzzle",()->{check(generated()==baseCount,"base recipes not restored");check(!(server.getRecipeManager().byKey(cutId).orElseThrow().value() instanceof ShapelessRecipe),"removed override retained");check(Math.abs(SentryGunProfiles.get(GUN).muzzle().z+1.234)>1e-4,"removed muzzle retained");});
            finish();
        }
    }
    static void finish(){if(arm!=null)SentryFakePlayer.remove(arm);SMAServerConfig.ENABLE_DYNAMIC_RECIPES.set(true);stage=0;SentryMechanicalArm.LOGGER.info("RELOAD RESULT passed={} failed={}",passed,failed);server.halt(false);}
}
