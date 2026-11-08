package euphy.upo.sentrymechanicalarm.smoke;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.event.common.*;
import euphy.upo.sentrymechanicalarm.*;
import euphy.upo.sentrymechanicalarm.content.*;
import euphy.upo.sentrymechanicalarm.compat.*;
import euphy.upo.sentrymechanicalarm.recipe.*;
import euphy.upo.sentrymechanicalarm.registry.*;
import euphy.upo.sentrymechanicalarm.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Actual server inventory, mounted storage codec and resource-reload integration. */
@EventBusSubscriber(modid="sentrymechanicalarm")
public final class ReleaseCasingSmoke {
    static ServerLevel level;static int passed,failed,stage,ticks;
    static final BlockPos POS=new BlockPos(552,246,552);
    static final ResourceLocation AMMO=ResourceLocation.parse("tacz:9mm"),RIFLE=ResourceLocation.parse("tacz:338");
    static CompletableFuture<Void> reload;static Path pack;static ResourceLocation original;
    interface Check{void run()throws Exception;}
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void test(String name,Check action){try{action.run();passed++;SentryMechanicalArm.LOGGER.info("RELEASE PASS {}",name);}catch(Throwable e){failed++;SentryMechanicalArm.LOGGER.error("RELEASE FAIL "+name,e);}}
    static ItemStack casing(int count){return EmptyCasingItem.create(AMMO,count);}
    static List<ItemEntity> drops(){return level.getEntitiesOfClass(ItemEntity.class,new AABB(POS).inflate(12),e->e.getPersistentData().getBoolean("sentrymechanicalarm:casing_drop"));}
    static ServerPlayer player(){
        var p=new ServerPlayer(level.getServer(),level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ReleasePlayer"),ClientInformation.createDefault());
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),p,net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
        };
        p.setPos(POS.getX()+.5,POS.getY()+1,POS.getZ()+.5);
        p.setItemInHand(InteractionHand.MAIN_HAND,GunItemBuilder.create().setId(ResourceLocation.parse("tacz:glock_17")).setAmmoCount(10).setAmmoInBarrel(true).build());return p;
    }
    static void shot(ServerPlayer p,boolean consume){
        SentryCasingDrops.clear();var gun=p.getMainHandItem();
        SentryCasingDrops.before(new GunFireEvent(p,gun,LogicalSide.SERVER));
        if(consume){var api=IGun.getIGunOrNull(gun);api.setCurrentAmmoCount(gun,api.getCurrentAmmoCount(gun)-1);}
        SentryCasingDrops.after(new GunFirePostEvent(p,gun,LogicalSide.SERVER));
    }
    @SubscribeEvent public static void start(ServerStartedEvent event)throws Exception{
        if(!"release".equals(System.getProperty("sentry.smoke")))return;
        level=event.getServer().overworld();level.setChunkForced(34,34,true);level.getChunkAt(POS);
    }
    static void runChecks()throws Exception {
        test("player inventory collection defaults to enabled",()->check(SMAServerConfig.PLAYER_CASINGS_TO_INVENTORY.get(),"default disabled"));
        SMAServerConfig.CASING_CHANCE.set(1.0);
        test("finite shot inserts one case without a ground entity",()->{var p=player();try{shot(p,true);check(p.getInventory().items.stream().filter(s->AMMO.equals(EmptyCasingItem.ammoId(s))).mapToInt(ItemStack::getCount).sum()==1,"case missing from inventory");check(drops().isEmpty(),"unexpected ground drop");}finally{p.discard();}});
        test("unpaid shot produces no case",()->{var p=player();try{shot(p,false);check(p.getInventory().items.stream().noneMatch(s->AMMO.equals(EmptyCasingItem.ammoId(s))),"unpaid inventory output");}finally{p.discard();}});
        test("full inventory overflow survives a zero ground casing budget",()->{
            int cap=SMAServerConfig.CASING_ENTITY_LIMIT.get();SMAServerConfig.CASING_ENTITY_LIMIT.set(0);var p=player();
            try{for(int n=1;n<p.getInventory().items.size();n++)p.getInventory().setItem(n,new ItemStack(Items.STONE,64));shot(p,true);
                check(drops().stream().mapToInt(e->e.getItem().getCount()).sum()==1,"overflow lost");
                check(drops().stream().allMatch(e->p.getUUID().equals(e.getTarget())&&!e.hasPickUpDelay()),"not /give overflow semantics");
            }finally{drops().forEach(ItemEntity::discard);p.discard();SMAServerConfig.CASING_ENTITY_LIMIT.set(cap);}
        });
        test("creative and disabled player sources produce no recovery",()->{var p=player();try{p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);shot(p,true);check(p.getInventory().items.stream().noneMatch(s->AMMO.equals(EmptyCasingItem.ammoId(s))),"creative casing");p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);SMAServerConfig.PLAYER_CASINGS.set(false);shot(p,true);check(p.getInventory().items.stream().noneMatch(s->AMMO.equals(EmptyCasingItem.ammoId(s))),"source gate bypassed");}finally{SMAServerConfig.PLAYER_CASINGS.set(true);p.discard();}});
        level.setBlock(POS,SentryRegistry.SENTRY_ARM_BLOCK.get().defaultBlockState(),3);
        level.setBlock(POS.east(),SentryRegistry.CASING_COLLECTOR_BLOCK.get().defaultBlockState(),3);
        var arm=(SentryArmBlockEntity)level.getBlockEntity(POS);
        var collector=(CasingCollectorBlockEntity)level.getBlockEntity(POS.east());
        test("adjacent collector stores typed cases",()->{check(CasingCollectors.insert(arm,casing(17)).isEmpty(),"collection refused");check(ItemStack.matches(collector.getItem(0),casing(17)),"count or caliber changed");});
        var custom=new ItemStack(Items.GOLD_NUGGET,70);custom.set(DataComponents.CUSTOM_NAME,Component.literal("Custom shot byproduct"));
        test("custom component items split across collector slots",()->{collector.clearContent();check(CasingCollectors.insert(arm,custom.copy()).isEmpty(),"custom remainder");int total=0;for(int n=0;n<27;n++){var s=collector.getItem(n);if(s.isEmpty())continue;check(ItemStack.isSameItemSameComponents(s,custom),"components lost");total+=s.getCount();}check(total==70,"custom count changed");});
        test("partial insert returns only the unaccepted remainder",()->{for(int n=0;n<27;n++)collector.setItem(n,new ItemStack(Items.STONE,64));collector.setItem(26,custom.copyWithCount(63));var remainder=CasingCollectors.insert(arm,custom.copy());check(remainder.getCount()==69&&collector.getItem(26).getCount()==64,"partial insert duplicated or lost stock");});
        test("collector persistence retains exact components",()->{var saved=collector.saveWithFullMetadata(level.registryAccess());var restored=(CasingCollectorBlockEntity)BlockEntity.loadStatic(POS.east(),collector.getBlockState(),saved,level.registryAccess());check(restored!=null&&ItemStack.matches(restored.getItem(26),custom.copyWithCount(64)),"save round trip changed stack");});
        test("Create mounts collector as dedicated storage and writes it back",()->{
            collector.clearContent();collector.setItem(0,casing(17));
            var manager=new com.simibubi.create.content.contraptions.MountedStorageManager();
            manager.addBlock(level,collector.getBlockState(),POS.east(),BlockPos.ZERO,collector);manager.initialize();
            var mounted=manager.getAllItemStorages().get(BlockPos.ZERO);check(mounted instanceof CasingCollectorMountedStorage,"wrong mounted storage type");
            var ops=level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            var codec=com.simibubi.create.api.contraption.storage.item.MountedItemStorage.CODEC;
            var restored=codec.parse(ops,codec.encodeStart(ops,mounted).getOrThrow()).getOrThrow();
            check(ItemStack.matches(restored.getStackInSlot(0),casing(17)),"mounted save changed stock");
            collector.clearContent();restored.unmount(level,collector.getBlockState(),POS.east(),collector);
            check(ItemStack.matches(collector.getItem(0),casing(17)),"unmount did not write back");
        });
        test("ordinary chests are not casing collectors",()->{collector.clearContent();level.removeBlock(POS.east(),false);level.setBlock(POS.east(),Blocks.CHEST.defaultBlockState(),3);check(ItemStack.matches(CasingCollectors.insert(arm,casing(4)),casing(4)),"ordinary chest accepted cases");});
        original=ReloadingRecipes.find(level.getRecipeManager(),RIFLE).orElseThrow().id();
        pack=level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR).resolve("release-rules");
        write("pack.mcmeta","{\"pack\":{\"pack_format\":48,\"description\":\"Release checks only\"}}");
        write("data/tacz/sentry_manufacturing/338.json","{\"enabled\":true,\"reloading\":{\"materials\":[{\"ingredient\":{\"item\":\"minecraft:iron_nugget\"},\"count\":2}],\"propellant\":15},\"forming\":{\"input\":{\"item\":\"minecraft:iron_ingot\"},\"output_count\":7},\"drops\":{\"item\":{\"id\":\"minecraft:gold_nugget\"},\"count\":70,\"chance\":1}}");
        write("data/tacz/sentry_reloading/338.json","{\"materials\":[{\"ingredient\":{\"item\":\"minecraft:diamond\"},\"count\":4}]}");
        write("data/tacz/sentry_manufacturing/50ae.json","{\"enabled\":false}");
        write("data/tacz/sentry_manufacturing/762x39.json","{\"reloading\":{\"propellant\":-1}}");
        write("data/tacz/sentry_manufacturing/45acp.json","{\"forming\":{\"input\":{\"item\":\"minecraft:iron_ingot\"},\"output_count\":7},\"drops\":{\"count\":0}}");
        write("data/sentrymechanicalarm/recipe/casing_forming/tacz/45acp.json","{\"neoforge:conditions\":[{\"type\":\"neoforge:false\"}],\"type\":\"create:deploying\"}");
        write("data/tacz/sentry_manufacturing/556x45.json","{\"drops\":{\"casing\":\"tacz:9mm\",\"count\":2,\"chance\":0.5}}");
        var recipe=CasingFormingRecipes.find(level.getRecipeManager(),RIFLE).orElseThrow().value();
        var json=Recipe.CODEC.encodeStart(level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),recipe).getOrThrow().getAsJsonObject();
        json.getAsJsonArray("results").get(0).getAsJsonObject().addProperty("count",3);
        write("data/sentrymechanicalarm/recipe/casing_forming/tacz/338.json",json.toString());
        stage=1;changePack(true);
    }
    static void write(String path,String text)throws Exception{var file=pack.resolve(path);Files.createDirectories(file.getParent());Files.writeString(file,text);}
    static void changePack(boolean include){var server=level.getServer();server.getPackRepository().reload();var selected=new ArrayList<>(server.getPackRepository().getSelectedIds());selected.remove("file/release-rules");if(include)selected.add("file/release-rules");reload=server.reloadResources(selected);}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(level==null)return;if(++ticks>1200){failed++;finish();return;}
        // Entity queries need the forced chunk's visibility transition to have ticked.
        if(stage==0){if(ticks<20)return;try{runChecks();}catch(Throwable error){failed++;SentryMechanicalArm.LOGGER.error("RELEASE FAIL setup",error);finish();}return;}
        if(reload==null||!reload.isDone())return;
        try{reload.join();if(stage==1){
            test("unified reloading overrides legacy fees exactly",()->{var r=(com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe)ReloadingRecipes.find(level.getRecipeManager(),RIFLE).orElseThrow().value();check(r.getSequence().size()==3,"unexpected stages");check(((com.simibubi.create.content.fluids.transfer.FillingRecipe)r.getSequence().getFirst().getRecipe()).getRequiredFluid().amount()==15,"fluid rescaled");var metal=r.getSequence().get(1).getRecipe().getIngredients().get(1);check(metal.test(new ItemStack(Items.IRON_NUGGET))&&CountedDeployerIngredient.amount(metal)==2,"legacy fees or auto discount won");});
            test("complete native recipe overrides unified forming",()->check(CasingFormingRecipes.find(level.getRecipeManager(),RIFLE).orElseThrow().value().getResultItem(level.registryAccess()).getCount()==3,"native override lost"));
            test("conditional native resource suppresses automatic replacement",()->check(CasingFormingRecipes.find(level.getRecipeManager(),ResourceLocation.parse("tacz:45acp")).isEmpty(),"disabled native recipe replaced"));
            test("explicit disable and invalid section do not fall back",()->{var disabled=ResourceLocation.parse("tacz:50ae");check(!SentryCasingProfiles.enabled(disabled)&&ReloadingRecipes.find(level.getRecipeManager(),disabled).isEmpty()&&CasingFormingRecipes.find(level.getRecipeManager(),disabled).isEmpty()&&!SentryCasingProfiles.drop(disabled).enabled(),"root disable ignored");check(ReloadingRecipes.find(level.getRecipeManager(),ResourceLocation.parse("tacz:762x39")).isEmpty(),"invalid reloading fell back");check(CasingFormingRecipes.find(level.getRecipeManager(),ResourceLocation.parse("tacz:762x39")).isPresent(),"unrelated forming disabled");});
            test("custom drop type count and probability are retained",()->{var rule=SentryCasingProfiles.drop(RIFLE);check(rule.template().is(Items.GOLD_NUGGET)&&rule.count()==70&&rule.chance()==1,"custom drop changed");check(!SentryCasingProfiles.drop(ResourceLocation.parse("tacz:45acp")).enabled(),"zero count ignored");var mapped=SentryCasingProfiles.drop(ResourceLocation.parse("tacz:556x45"));check(AMMO.equals(mapped.casing())&&mapped.count()==2&&mapped.chance()==.5,"mapping changed");});
            stage=2;changePack(false);
        }else{test("removing rule pack restores original recipes and drops",()->{check(ReloadingRecipes.find(level.getRecipeManager(),RIFLE).orElseThrow().id().equals(original),"recipe did not restore");check(SentryCasingProfiles.drop(RIFLE).template().is(SentryRegistry.EMPTY_CASING.get()),"custom drop cached");check(ReloadingRecipes.find(level.getRecipeManager(),ResourceLocation.parse("tacz:762x39")).isPresent(),"invalid-rule suppression cached");});finish();}
        }catch(Throwable error){failed++;SentryMechanicalArm.LOGGER.error("RELEASE FAIL resource reload",error);finish();}
    }
    static void finish(){var server=level.getServer();level.setChunkForced(34,34,false);level=null;SentryMechanicalArm.LOGGER.info("RELEASE RESULT passed={} failed={}",passed,failed);server.halt(false);}
}
