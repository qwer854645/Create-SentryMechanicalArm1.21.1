package euphy.upo.sentrymechanicalarm.smoke;

import com.simibubi.create.*;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.kinetics.deployer.*;
import com.simibubi.create.content.kinetics.press.*;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.tacz.guns.api.item.*;
import euphy.upo.sentrymechanicalarm.*;
import euphy.upo.sentrymechanicalarm.content.*;
import euphy.upo.sentrymechanicalarm.recipe.*;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

@EventBusSubscriber(modid=SentryMechanicalArm.MODID)
public final class ReloadingSmoke {
    static final ResourceLocation AMMO=ResourceLocation.parse("tacz:9mm");
    static final BlockPos COLLECT=new BlockPos(544,240,544),DEPOT=new BlockPos(550,240,544);
    static ServerLevel level;static boolean running;static int ticks,passed,failed,stage,stageTick,started=-1;static SequencedAssemblyRecipe recipe;
    static net.minecraft.server.level.ServerPlayer shooter;
    static int shots,returnedCases,reloadStage;static boolean shotWindow;
    static java.util.concurrent.CompletableFuture<Void> reload;
    static java.nio.file.Path pack;static ItemStack savedProgress;static ResourceLocation previousRecipe;
    static DeployerBlockEntity hand;static DepotBlockEntity depot;
    interface Check{void run()throws Exception;}
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    static void test(String n,Check c){try{c.run();passed++;SentryMechanicalArm.LOGGER.info("RELOADING PASS {}",n);}catch(Throwable e){failed++;SentryMechanicalArm.LOGGER.error("RELOADING FAIL "+n,e);}}
    static ItemStack casing(ResourceLocation id,int count){return EmptyCasingItem.create(id,count);}
    static void nativeSequence(SequencedAssemblyRecipe assembly){
        var item=assembly.getIngredient().getItems()[0].copy();
        for(var step:assembly.getSequence()){
            SentryMechanicalArm.LOGGER.info("RELOADING STEP {} input={} material={}",ReloadingRecipes.id(IAmmo.getIAmmoOrNull(assembly.getResultItem(level.registryAccess())).getAmmoId(assembly.getResultItem(level.registryAccess()))),item,step.getRecipe().getIngredients());
            com.simibubi.create.content.processing.recipe.ProcessingRecipe<?,?> selected;
            if(step.getRecipe() instanceof DeployerApplicationRecipe){
                var inventory=new net.neoforged.neoforge.items.ItemStackHandler(2);inventory.setStackInSlot(0,item);inventory.setStackInSlot(1,step.getRecipe().getIngredients().get(1).getItems()[0].copy());
                selected=SequencedAssemblyRecipe.getRecipe(level,new net.neoforged.neoforge.items.wrapper.RecipeWrapper(inventory),AllRecipeTypes.DEPLOYING.getType(),DeployerApplicationRecipe.class).orElseThrow().value();
            }else selected=SequencedAssemblyRecipe.getRecipe(level,new SingleRecipeInput(item),AllRecipeTypes.PRESSING.getType(),PressingRecipe.class).orElseThrow().value();
            item=selected.rollResults(level.random).getFirst();
        }
        check(ItemStack.matches(item,assembly.getResultItem(level.registryAccess())),"wrong native output");check(item.getCount()==1,"one case generated several rounds");
    }
    @SubscribeEvent public static void start(ServerStartedEvent event){
        if(!"reloading".equals(System.getProperty("sentry.smoke")))return;
        running=true;level=event.getServer().overworld();
        recipe=(SequencedAssemblyRecipe)ReloadingRecipes.find(event.getServer().getRecipeManager(),AMMO).orElseThrow().value();
        test("auto recipe is one case to one native round",()->{check(recipe.getIngredient().test(casing(AMMO,1)),"right case rejected");check(!recipe.getIngredient().test(casing(ResourceLocation.parse("tacz:12g"),1)),"wrong caliber accepted");check(recipe.getResultItem(level.registryAccess()).getCount()==1,"batch amplification");check(recipe.getSequence().size()==3,"unexpected 9mm cost/steps");});
        test("all generated reloading sequences preserve ammo identity",()->{for(var h:level.getRecipeManager().getRecipes())if(h.id().getPath().startsWith("casing_reloading/")&&h.id().getNamespace().equals(SentryMechanicalArm.MODID))nativeSequence((SequencedAssemblyRecipe)h.value());});
        test("reloading ingredient survives recipe JSON and network codecs",()->{var ops=level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);var json=Recipe.CODEC.encodeStart(ops,recipe).getOrThrow();var copy=(SequencedAssemblyRecipe)Recipe.CODEC.parse(ops,json).getOrThrow();check(copy.getIngredient().test(casing(AMMO,1))&&!copy.getIngredient().test(casing(ResourceLocation.parse("tacz:12g"),1)),"JSON lost caliber");var b=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());try{RecipeHolder.STREAM_CODEC.encode(b,new RecipeHolder<>(ReloadingRecipes.find(level.getRecipeManager(),AMMO).orElseThrow().id(),recipe));var decoded=(SequencedAssemblyRecipe)RecipeHolder.STREAM_CODEC.decode(b).value();check(decoded.getIngredient().test(casing(AMMO,1)),"network ingredient rejected");}finally{b.release();}});
        test("unrelated names do not change caliber",()->{var named=casing(AMMO,1);named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Recycled"));check(recipe.getIngredient().test(named),"name affects caliber");});
        test("Create filters distinguish case components",()->{var filter=com.simibubi.create.content.logistics.filter.FilterItemStack.of(casing(AMMO,1));check(filter.test(level,casing(AMMO,2),true),"filter rejects same caliber");check(!filter.test(level,casing(ResourceLocation.parse("tacz:12g"),1),true),"filter ignores caliber");});
        test("belt transport copies and saves preserve caliber and count",()->{
            for(var ammo:List.of(AMMO,ResourceLocation.parse("tacz:12g"))){var item=new com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack(casing(ammo,17));item.beltPosition=2.5f;
                var saved=com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack.read(item.serializeNBT(level.registryAccess()),level.registryAccess());check(ItemStack.matches(item.stack,saved.stack)&&saved.beltPosition==2.5f,"belt save loses components");check(ItemStack.matches(item.stack,item.copy().stack),"belt copy changes stack");}
        });
        test("special ammo has no automatic reloading recipe",()->{for(String id:List.of("ars_armorer:mana","applied_armorer:fluix_battery","tacz:rpg_rocket"))check(ReloadingRecipes.find(level.getRecipeManager(),ResourceLocation.parse(id)).isEmpty(),"special ammo recipe");});
        test("reloading switch removes only new recipes",()->{SMAServerConfig.ENABLE_RELOADING.set(false);try{DynamicRecipeManager.rebuild(level.getRecipeManager());check(ReloadingRecipes.find(level.getRecipeManager(),AMMO).isEmpty(),"disabled recipe retained");check(!recipe.getIngredient().test(casing(AMMO,1)),"disabled ingredient active");check(level.getRecipeManager().getRecipes().stream().noneMatch(h->h.id().getNamespace().equals(SentryMechanicalArm.MODID)&&h.id().getPath().startsWith("ammo_")),"legacy production still exists");}finally{SMAServerConfig.ENABLE_RELOADING.set(true);DynamicRecipeManager.rebuild(level.getRecipeManager());}});
        level.setChunkForced(34,34,true);level.getChunkAt(COLLECT);
        level.setBlock(COLLECT,Blocks.CHEST.defaultBlockState(),3);level.setBlock(COLLECT.above(),Blocks.HOPPER.defaultBlockState(),3);level.setBlock(COLLECT.above(2),AllBlocks.CHUTE.get().defaultBlockState(),3);
        for(var stack:List.of(casing(AMMO,17),casing(ResourceLocation.parse("tacz:12g"),11))){var item=new ItemEntity(level,COLLECT.getX()+.5,COLLECT.getY()+3.2,COLLECT.getZ()+.5,stack);item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);level.addFreshEntity(item);}
        level.setBlock(DEPOT,AllBlocks.DEPOT.get().defaultBlockState(),3);depot=(DepotBlockEntity)level.getBlockEntity(DEPOT);depot.setHeldItem(ItemStack.EMPTY);
        level.setBlock(DEPOT.above(2),AllBlocks.DEPLOYER.get().defaultBlockState().setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING,Direction.DOWN),3);hand=(DeployerBlockEntity)level.getBlockEntity(DEPOT.above(2));
    }
    static void changePack(boolean include)throws Exception{
        var server=level.getServer();server.getPackRepository().reload();var selected=new ArrayList<>(server.getPackRepository().getSelectedIds());selected.remove("file/reloading-smoke");if(include)selected.add("file/reloading-smoke");reload=server.reloadResources(selected);
    }
    static void write(String path,String text)throws Exception{var file=pack.resolve(path);java.nio.file.Files.createDirectories(file.getParent());java.nio.file.Files.writeString(file,text);}
    @SubscribeEvent public static void fire(com.tacz.guns.api.event.common.GunFirePostEvent e){if(shotWindow&&e.getShooter()==shooter)shots++;}
    @SubscribeEvent public static void entity(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e){if(shotWindow&&e.getEntity() instanceof ItemEntity item&&item.getItem().is(SentryRegistry.EMPTY_CASING.get())&&AMMO.equals(EmptyCasingItem.ammoId(item.getItem())))returnedCases+=item.getItem().getCount();}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){if(!running)return;ticks++;
        if(reload!=null){if(!reload.isDone())return;try{reload.join();if(reloadStage==1){
            test("data-pack cost overrides rebuild and invalidate old progress",()->{
                var updated=(SequencedAssemblyRecipe)ReloadingRecipes.find(level.getRecipeManager(),AMMO).orElseThrow().value();
                check(updated.getSequence().get(0).getRecipe().getIngredients().get(1).test(new ItemStack(Items.GUNPOWDER)),"old materials cached");
                check(level.getRecipeManager().byKey(previousRecipe).isEmpty(),"cost change retained old progress recipe");
                check(SequencedAssemblyRecipe.getRecipe(level,new SingleRecipeInput(savedProgress),AllRecipeTypes.PRESSING.getType(),PressingRecipe.class).isEmpty(),"old progress bypasses changed cost");
                nativeSequence(updated);
                check(ReloadingRecipes.find(level.getRecipeManager(),ResourceLocation.parse("tacz:308")).isEmpty(),"air cost accepted");
                var autoAmmo=com.tacz.guns.api.TimelessAPI.getAllCommonAmmoIndex().stream().map(java.util.Map.Entry::getKey).filter(id->!id.equals(AMMO)&&ReloadingRecipes.find(level.getRecipeManager(),id).isPresent()).findFirst().orElseThrow();
                SMAServerConfig.AUTO_RELOADING.set(false);
                try{DynamicRecipeManager.rebuild(level.getRecipeManager());check(ReloadingRecipes.find(level.getRecipeManager(),AMMO).isPresent(),"explicit costs disabled with auto generation");check(ReloadingRecipes.find(level.getRecipeManager(),autoAmmo).isEmpty(),"automatic cost generation still active");}
                finally{SMAServerConfig.AUTO_RELOADING.set(true);DynamicRecipeManager.rebuild(level.getRecipeManager());}
                check(ReloadingRecipes.find(level.getRecipeManager(),ResourceLocation.parse("tacz:12g")).isEmpty(),"disabled override ignored");
                check(ReloadingRecipes.find(level.getRecipeManager(),ResourceLocation.parse("tacz:556x45")).isEmpty(),"free override accepted");
                var recover=new RecoverCasingRecipe(CraftingBookCategory.MISC);var input=CraftingInput.of(1,1,List.of(savedProgress));check(recover.matches(input,level),"cannot cancel old progress");check(ItemStack.matches(recover.assemble(input,level.registryAccess()),casing(AMMO,1)),"cancelling refunds more than the casing");
            });reloadStage=2;changePack(false);return;
        }else{
            test("removing data pack restores auto costs without duplicate recipes",()->{var current=ReloadingRecipes.find(level.getRecipeManager(),AMMO).orElseThrow();check(current.id().equals(previousRecipe),"stable costs got a different ID");nativeSequence((SequencedAssemblyRecipe)current.value());int count=level.getRecipeManager().getRecipes().size();DynamicRecipeManager.rebuild(level.getRecipeManager());check(level.getRecipeManager().getRecipes().size()==count,"duplicate generated holders");});finish();return;
        }}catch(Throwable error){failed++;SentryMechanicalArm.LOGGER.error("RELOADING FAIL data reload",error);finish();return;}}

        if(hand!=null){hand.setSpeed(256);if(hand.getPlayer()==null){if(ticks>100){test("machine fixture initializes",()->{throw new AssertionError("deployer not ticking in forced chunk");});finish();}return;}if(started<0){
            var stock=(net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(COLLECT);ItemStack collected=ItemStack.EMPTY;
            for(int i=0;i<stock.getContainerSize();i++)if(AMMO.equals(EmptyCasingItem.ammoId(stock.getItem(i)))){collected=stock.getItem(i).split(1);stock.setChanged();break;}
            if(collected.isEmpty())return;depot.setHeldItem(collected);started=ticks;hand.getPlayer().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,recipe.getSequence().get(0).getRecipe().getIngredients().get(1).getItems()[0].copyWithCount(3));}}
        if(ticks-started==120){test("real mechanical hand consumes material and advances depot casing",()->{check(depot.getHeldItem().is(SentryRegistry.RELOADING_CASING.get()),"deployer did not process depot: "+depot.getHeldItem());check(hand.getPlayer().getMainHandItem().getCount()==2,"deployer material not consumed once");savedProgress=depot.getHeldItem().copy();previousRecipe=savedProgress.get(AllDataComponents.SEQUENCED_ASSEMBLY).id();check(AMMO.equals(EmptyCasingItem.ammoId(savedProgress)),"progress lost caliber");var restored=ItemStack.parse(level.registryAccess(),savedProgress.save(level.registryAccess())).orElseThrow();check(ItemStack.matches(savedProgress,restored),"progress disk round-trip");depot.setHeldItem(restored);});hand.getPlayer().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,recipe.getSequence().get(1).getRecipe().getIngredients().get(1).getItems()[0].copyWithCount(3));}
        if(ticks-started==240){test("second native material is consumed exactly once",()->check(hand.getPlayer().getMainHandItem().getCount()==2,"powder not consumed"));hand=null;level.setBlock(DEPOT.above(2),AllBlocks.MECHANICAL_PRESS.get().defaultBlockState(),3);}
        if(started>=0&&ticks-started>=241&&ticks-started<421){var press=(MechanicalPressBlockEntity)level.getBlockEntity(DEPOT.above(2));press.setSpeed(256);}
        if(started>=0&&ticks-started==420){test("real chute hopper chest preserve both calibers and total",()->{var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(COLLECT);int a=0,b=0;for(int i=0;i<chest.getContainerSize();i++){var stack=chest.getItem(i);if(AMMO.equals(EmptyCasingItem.ammoId(stack)))a+=stack.getCount();else if(ResourceLocation.parse("tacz:12g").equals(EmptyCasingItem.ammoId(stack)))b+=stack.getCount();}check(a==16&&b==11,"collected "+a+" / "+b);});test("real pressing machine outputs one original gunpack round",()->{var item=depot.getHeldItem();var api=IAmmo.getIAmmoOrNull(item);check(api!=null&&AMMO.equals(api.getAmmoId(item))&&item.getCount()==1,"output="+item);});
            // Transfer the actual single factory output into the test gun's sole chambered round.
            depot.getHeldItem().shrink(1);depot.setHeldItem(ItemStack.EMPTY);
            shooter=new net.minecraft.server.level.ServerPlayer(level.getServer(),level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ReloadingPlayer"),net.minecraft.server.level.ClientInformation.createDefault());
            shooter.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),shooter,net.minecraft.server.network.CommonListenerCookie.createInitial(shooter.getGameProfile(),false)){@Override public void send(net.minecraft.network.protocol.Packet<?> packet){}};
            shooter.setPos(DEPOT.getX(),DEPOT.getY()+3,DEPOT.getZ());shooter.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,com.tacz.guns.api.item.builder.GunItemBuilder.create().setId(ResourceLocation.parse("tacz:glock_17")).setFireMode(com.tacz.guns.api.item.gun.FireMode.SEMI).setAmmoCount(0).setAmmoInBarrel(true).build());
            var op=com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(shooter);op.initialData();op.draw(shooter::getMainHandItem);op.getDataHolder().drawTimestamp=com.tacz.guns.util.time.GunTime.nowMillis()-10000;SMAServerConfig.CASING_CHANCE.set(1.0);SMAServerConfig.CASING_ENTITY_LIMIT.set(1024);
        }
        if(started>=0&&ticks-started==440){shotWindow=true;com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(shooter).shoot(()->0f,()->0f);}
        if(started>=0&&ticks-started==460){shotWindow=false;test("one reloaded round fires and returns exactly one case",()->{var gun=shooter.getMainHandItem();var api=IGun.getIGunOrNull(gun);check(shots==1&&returnedCases==1,"shots/cases="+shots+"/"+returnedCases);check(api.getCurrentAmmoCount(gun)==0&&!api.hasBulletInBarrel(gun),"shot did not consume sole round");});
            try{pack=level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR).resolve("reloading-smoke");
                write("pack.mcmeta","{\"pack\":{\"pack_format\":48,\"description\":\"Disposable reloading verification\"}}");
                write("data/tacz/sentry_reloading/9mm.json","{\"materials\":[{\"ingredient\":{\"item\":\"minecraft:gunpowder\"},\"count\":2}]}");
                write("data/tacz/sentry_reloading/12g.json","{\"enabled\":false}");
                write("data/tacz/sentry_reloading/308.json","{\"materials\":[{\"ingredient\":{\"item\":\"minecraft:air\"}}]}");
                write("data/tacz/sentry_reloading/556x45.json","{\"materials\":[]}");reloadStage=1;changePack(true);
            }catch(Exception error){failed++;SentryMechanicalArm.LOGGER.error("RELOADING FAIL setup data pack",error);finish();}
        }
    }
    static void finish(){running=false;if(shooter!=null)shooter.discard();level.setChunkForced(34,34,false);SentryMechanicalArm.LOGGER.info("RELOADING RESULT passed={} failed={}",passed,failed);level.getServer().halt(false);}
}
