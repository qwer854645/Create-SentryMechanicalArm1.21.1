package euphy.upo.sentrymechanicalarm.smoke;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.tacz.guns.api.item.*;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.*;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.block.entity.AmmoStorageBlockEntity;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.init.ModBlocks;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.compat.AmmoDepotMountedStorage;
import euphy.upo.sentrymechanicalarm.content.*;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.SentryFakePlayer;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.*;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
import java.util.*;

/** Three separate JVMs: prepare/save, reload+boundary tests/save, reload+resume. */
@EventBusSubscriber(modid="sentrymechanicalarm")
public final class RestartSentrySmoke {
    static ServerLevel level;static Path manifest;static String phase;static boolean running,suppress;
    static int ticks,passed,failed,step,stepTick,ghostShots;static FakePlayer unloadedShooter;
    static long unloadExpected;static final List<Fixture> fixtures=new ArrayList<>();
    static class Fixture {
        String name,mode;BlockPos armPos,depotPos,controllerPos;UUID entityId,trainId,targetId,shipId;
        List<BlockPos> staticArms=new ArrayList<>();List<CompoundTag> signatures=new ArrayList<>();
        long expected,baseline;int shots,checkpoint;
        AbstractContraptionEntity entity(){
            if(mode.equals("train")){
                var train=Create.RAILWAYS.trains.get(trainId);return train==null?null:train.carriages.getFirst().anyAvailableEntity();
            }
            return entityId==null?null:level.getEntity(entityId) instanceof AbstractContraptionEntity ace?ace:null;
        }
        List<SentryArmBlockEntity> arms(){
            if(mode.equals("bulk"))return List.of();
            if(mode.equals("bearing") || mode.equals("train")){
                var entity=entity();if(entity==null || entity.getContraption()==null)return List.of();
                List<SentryArmBlockEntity> result=new ArrayList<>();
                for(var pair:entity.getContraption().getActors())if(pair.getValue()!=null && pair.getKey().state().getBlock() instanceof SentryArmBlock)
                    result.add(SentryMovementBehaviour.getOrCreate(pair.getValue()));
                return result;
            }
            List<SentryArmBlockEntity> result=new ArrayList<>();
            for(var pos:staticArms)if(level.getBlockEntity(pos) instanceof SentryArmBlockEntity arm)result.add(arm);
            return result;
        }
        AmmoStorageBlockEntity depot(){return level.getBlockEntity(depotPos) instanceof AmmoStorageBlockEntity depot?depot:null;}
        long stock(){
            if(mode.equals("bearing") || mode.equals("train")){
                var entity=entity();if(entity==null)throw new IllegalStateException("Entity not loaded: "+name);
                return AmmoDepotSmoke.total(entity.getContraption().getStorage().getAllItems());
            }
            var depot=depot();if(depot==null)throw new IllegalStateException("Depot not loaded: "+name);
            return depot.getContents().stream().mapToLong(c->c.count()).sum();
        }
        long total(){return stock()+arms().stream().mapToLong(AmmoDepotSmoke::armAmmo).sum();}
        long special(){
            if(mode.equals("bearing") || mode.equals("train")){
                var inv=entity().getContraption().getStorage().getAllItems();long count=0;
                for(int s=0;s<inv.getSlots();s++)if(inv.getStackInSlot(s).has(DataComponents.CUSTOM_NAME))count+=inv.getStackInSlot(s).getCount();return count;
            }
            return depot().getContents().stream().filter(c->c.sample().has(DataComponents.CUSTOM_NAME)).mapToLong(c->c.count()).sum();
        }
        CompoundTag save(){
            CompoundTag tag=new CompoundTag();tag.putString("Name",name);tag.putString("Mode",mode);
            tag.put("Arm",NbtUtils.writeBlockPos(armPos));tag.put("Depot",NbtUtils.writeBlockPos(depotPos));
            if(controllerPos!=null)tag.put("Controller",NbtUtils.writeBlockPos(controllerPos));
            if(entityId!=null)tag.putUUID("Entity",entityId);if(trainId!=null)tag.putUUID("Train",trainId);if(targetId!=null)tag.putUUID("Target",targetId);if(shipId!=null)tag.putUUID("Ship",shipId);
            ListTag arms=new ListTag();for(var pos:staticArms)arms.add(NbtUtils.writeBlockPos(pos));tag.put("StaticArms",arms);
            ListTag guns=new ListTag();for(var arm:arms())guns.add(signature(arm));tag.put("Guns",guns);tag.putLong("Expected",total());return tag;
        }
        static Fixture load(CompoundTag tag){
            var f=new Fixture();f.name=tag.getString("Name");f.mode=tag.getString("Mode");f.armPos=NbtUtils.readBlockPos(tag,"Arm").orElseThrow();f.depotPos=NbtUtils.readBlockPos(tag,"Depot").orElseThrow();
            f.controllerPos=NbtUtils.readBlockPos(tag,"Controller").orElse(null);
            if(tag.hasUUID("Entity"))f.entityId=tag.getUUID("Entity");if(tag.hasUUID("Train"))f.trainId=tag.getUUID("Train");if(tag.hasUUID("Target"))f.targetId=tag.getUUID("Target");if(tag.hasUUID("Ship"))f.shipId=tag.getUUID("Ship");
            var positions=tag.getList("StaticArms",Tag.TAG_INT_ARRAY);for(var p:positions){var a=((IntArrayTag)p).getAsIntArray();f.staticArms.add(new BlockPos(a[0],a[1],a[2]));}
            var guns=tag.getList("Guns",Tag.TAG_COMPOUND);for(int i=0;i<guns.size();i++)f.signatures.add(guns.getCompound(i));f.expected=tag.getLong("Expected");return f;
        }
    }
    interface Check{void run()throws Exception;}
    static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    static void test(String name,Check c){try{c.run();passed++;SentryMechanicalArm.LOGGER.info("RESTART PASS {} {}",phase,name);}catch(Throwable t){failed++;SentryMechanicalArm.LOGGER.error("RESTART FAIL "+phase+" "+name,t);}}
    static CompoundTag signature(SentryArmBlockEntity arm){
        var stack=arm.getHeldItem();var gun=IGun.getIGunOrNull(stack);if(gun==null)throw new IllegalStateException("Missing gun");
        var tag=new CompoundTag();tag.putString("Gun",gun.getGunId(stack).toString());tag.putString("Mode",gun.getFireMode(stack).name());tag.putString("Name",stack.getHoverName().getString());
        for(var type:AttachmentType.values()){var attachment=gun.getAttachmentTag(stack,type);if(attachment!=null && !attachment.isEmpty())tag.put(type.name(),attachment.copy());}return tag;
    }
    static void equip(Fixture f){for(var arm:f.arms()){
        var stack=arm.getHeldItem().copy();var gun=IGun.getIGunOrNull(stack);
        var scope=AttachmentItemBuilder.create().setId(ResourceLocation.parse("tacz:scope_acog_ta31")).build();gun.installAttachment(level.registryAccess(),stack,scope);
        stack.set(DataComponents.CUSTOM_NAME,Component.literal("Restart validation gun"));arm.setHeldItem(stack);
        check(ResourceLocation.parse("tacz:scope_acog_ta31").equals(gun.getAttachmentId(stack,AttachmentType.SCOPE)),"Scope installation failed");
    }}
    static void pausePhysics()throws Exception{
        var type=Class.forName("dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem");var system=type.getMethod("require",net.minecraft.world.level.Level.class).invoke(null,level);type.getMethod("setPaused",boolean.class).invoke(system,true);
    }
    static Fixture physical(AmmoDepotSmoke.Lane lane,String mode){
        var f=new Fixture();f.name=mode;f.mode=mode;f.armPos=lane.arms.getFirst().getBlockPos();f.depotPos=lane.depot.getBlockPos();f.staticArms.add(f.armPos);f.targetId=lane.target.getUUID();return f;
    }
    static void prepare()throws Exception{
        check(!Files.exists(manifest),"Refusing to overwrite a previous restart manifest");
        AmmoDepotSmoke.level=level;AmmoDepotSmoke.ammoId=com.tacz.guns.api.TimelessAPI.getCommonGunIndex(ResourceLocation.parse("tacz:ak47")).orElseThrow().getGunData().getAmmoId();
        AmmoDepotSmoke.scene(ModBlocks.AMMO_STORAGE.get(),0,false);fixtures.add(physical(AmmoDepotSmoke.lanes.getLast(),"static"));
        AmmoDepotSmoke.scene(ModBlocks.AMMO_STORAGE_GOLD.get(),0,true);var moving=AmmoDepotSmoke.lanes.getLast();
        var f=new Fixture();f.name=f.mode="bearing";f.armPos=new BlockPos(4500,220,4700);f.depotPos=f.armPos.east().below();f.staticArms.add(f.armPos);f.staticArms.add(f.armPos.west());f.targetId=moving.target.getUUID();f.controllerPos=f.armPos.below(2);
        level.setBlock(f.controllerPos,AllBlocks.MECHANICAL_BEARING.getDefaultState().setValue(DirectionalBlock.FACING,Direction.UP),3);
        var bearing=(MechanicalBearingBlockEntity)level.getBlockEntity(f.controllerPos);bearing.initialize();
        var contraption=moving.entity.getContraption();var pos=moving.entity.position();moving.entity.discard();
        var nativeEntity=ControlledContraptionEntity.create(level,bearing,contraption);nativeEntity.setPos(pos);bearing.attach(nativeEntity);level.addFreshEntity(nativeEntity);f.entityId=nativeEntity.getUUID();fixtures.add(f);
        level.setBlock(f.controllerPos.below(),AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalBlock.FACING,Direction.UP),3);
        var motor=(com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)level.getBlockEntity(f.controllerPos.below());motor.initialize();motor.generatedSpeed.setValue(1);
        TrainSentrySmoke.level=level;TrainSentrySmoke.lane("restart-train",0,new Vec3(0,0,128),false);var train=TrainSentrySmoke.lanes.getLast();
        var tf=new Fixture();tf.name=tf.mode="train";tf.armPos=BlockPos.containing(train.entity.position());tf.depotPos=tf.armPos;tf.entityId=train.entity.getUUID();tf.trainId=train.train.id;tf.targetId=train.target.getUUID();fixtures.add(tf);
        AmmoDepotSmoke.shipScene();var ship=AmmoDepotSmoke.lanes.getLast();var sf=physical(ship,"sable");sf.shipId=ship.ship.getUniqueId();fixtures.add(sf);pausePhysics();
        var bulk=new Fixture();bulk.name=bulk.mode="bulk";bulk.armPos=bulk.depotPos=new BlockPos(4532,220,4500);AmmoDepotSmoke.force(bulk.depotPos);
        level.setBlock(bulk.depotPos,ModBlocks.AMMO_STORAGE_DIAMOND.get().defaultBlockState(),3);
        new AmmoDepotMountedStorage(864,List.of(new AmmoDepotMountedStorage.Stock(0,AmmoDepotSmoke.ammo(1),(long)Integer.MAX_VALUE+7000),new AmmoDepotMountedStorage.Stock(1,AmmoDepotSmoke.special(1),37))).unmount(level,level.getBlockState(bulk.depotPos),bulk.depotPos,bulk.depot());fixtures.add(bulk);
        for(var fixture:fixtures){if(!fixture.mode.equals("bulk"))equip(fixture);fixture.baseline=fixture.total();}
    }
    @SubscribeEvent public static void start(ServerStartedEvent event){
        if(!"restart".equals(System.getProperty("sentry.smoke")))return;
        running=true;level=event.getServer().overworld();phase=System.getProperty("sentry.restartPhase","prepare");suppress=!phase.equals("prepare");
        manifest=event.getServer().getWorldPath(LevelResource.ROOT).resolve("sentry-restart-expected.nbt");
        test("isolated world selected",()->check(manifest.toAbsolutePath().toString().contains("restart-worlds"),"Wrong world directory"));
        test("no players connected",()->check(event.getServer().getPlayerCount()==0,"Players connected"));
        try{
            if(phase.equals("prepare"))prepare();else{
                var tag=NbtIo.readCompressed(manifest,NbtAccounter.unlimitedHeap());check(tag!=null,"Missing manifest");
                check(tag.getInt("Failures")==0,"Earlier phase failed");
                var list=tag.getList("Fixtures",Tag.TAG_COMPOUND);for(int i=0;i<list.size();i++)fixtures.add(Fixture.load(list.getCompound(i)));
                pausePhysics();
            }
        }catch(Throwable t){failed++;SentryMechanicalArm.LOGGER.error("RESTART FAIL setup",t);finish(false);}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void cancel(GunFireEvent event){if(running && suppress && event.getShooter() instanceof FakePlayer)event.setCanceled(true);}
    @SubscribeEvent public static void bullet(EntityJoinLevelEvent event){
        if(!running || !(event.getEntity() instanceof EntityKineticBullet b) || !(b.getOwner() instanceof FakePlayer fp))return;
        if(fp==unloadedShooter)ghostShots++;
        var arm=SentryFakePlayer.getArmFromPlayer(fp);for(var f:fixtures){
            if(step>=3 && step<=4 && f.mode.equals("static"))continue;
            if(!f.mode.equals("bulk") && f.arms().contains(arm))f.shots++;
        }
    }
    @SubscribeEvent public static void pre(ServerTickEvent.Pre event){
        if(!running)return;
        for(var f:fixtures){
            if(step>=3 && step<=4 && f.mode.equals("static"))continue;
            if(f.targetId!=null && level.getEntity(f.targetId) instanceof Husk target){target.setHealth(target.getMaxHealth());target.setDeltaMovement(Vec3.ZERO);}
            if(f.mode.equals("train")){var train=Create.RAILWAYS.trains.get(f.trainId);if(train!=null){train.manualTick=true;train.speed=suppress?0:.03;train.targetSpeed=train.speed;}}
        }
    }
    static boolean ready(){
        for(var f:fixtures){if(f.mode.equals("train") || f.mode.equals("bearing")){if(f.entity()==null || f.arms().size()!=f.signatures.size())return false;}else if(f.depot()==null || !f.mode.equals("bulk") && f.arms().size()!=f.signatures.size())return false;}
        return true;
    }
    static void audit(){
        for(var f:fixtures){
            SentryMechanicalArm.LOGGER.info("RESTART AUDIT {} {} expected={} current={} special={}",phase,f.name,f.expected,f.total(),f.special());
            test(f.name+" persisted ammo total",()->check(f.total()==f.expected,"Expected "+f.expected+", got "+f.total()));
            test(f.name+" component variant preserved",()->check(f.special()==37,"Special components/count changed"));
            test(f.name+" gun, mode, name and scope preserved",()->{var arms=f.arms();check(arms.size()==f.signatures.size(),"Arm count mismatch");for(int i=0;i<arms.size();i++)check(signature(arms.get(i)).equals(f.signatures.get(i)),"Gun signature changed");});
            f.baseline=f.total();f.checkpoint=f.shots;
        }
    }
    static void saveManifest()throws Exception{
        var root=new CompoundTag();root.putString("Phase",phase);root.putInt("Failures",failed);root.putInt("Passed",passed);var list=new ListTag();for(var f:fixtures)list.add(f.save());root.put("Fixtures",list);NbtIo.writeCompressed(root,manifest);
    }
    static void finish(boolean save){
        suppress=true;
        if(save)test("write independent expectation manifest",RestartSentrySmoke::saveManifest);
        SentryMechanicalArm.LOGGER.info("RESTART RESULT phase={} passed={} failed={} world={}",phase,passed,failed,manifest);
        running=false;level.getServer().halt(false);
    }
    static void liveCheck(){for(var f:fixtures){
        test(f.name+" live conservation",()->check(f.baseline-f.total()==f.shots-f.checkpoint,"Live ammo mismatch"));
        if(!f.mode.equals("bulk"))test(f.name+" resumes real fire",()->check(f.shots-f.checkpoint>5,"No firing after restart"));
    }}
    static void beginUnload(){
        var f=fixtures.getFirst();unloadExpected=f.total();unloadedShooter=SentryFakePlayer.get(f.arms().getFirst());ghostShots=0;
        int cx=f.armPos.getX()>>4,cz=f.armPos.getZ()>>4;
        for(int x=cx-2;x<=cx+2;x++)for(int z=cz-2;z<=cz+2;z++)level.setChunkForced(x,z,false);
        step=3;stepTick=ticks;
    }
    static void breakAndPlace()throws Exception{
        var f=fixtures.getFirst();var depot=f.depot();long stock=f.stock();var pos=f.depotPos;var block=depot.getBlockState().getBlock();
        check(level.destroyBlock(pos,true),"Could not break depot");
        var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(3),e->e.getItem().getItem()==block.asItem());check(drops.size()==1,"Expected exactly one depot drop");
        ItemStack item=drops.getFirst().getItem().copy();var data=item.getOrDefault(DataComponents.BLOCK_ENTITY_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        long count=0;var entries=data.getList("Entries",Tag.TAG_COMPOUND);for(int i=0;i<entries.size();i++)count+=entries.getCompound(i).getLong("Count");check(count==stock,"Dropped depot has wrong stock");
        var placer=FakePlayerFactory.getMinecraft(level);placer.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);placer.getAbilities().instabuild=true;placer.setItemInHand(InteractionHand.MAIN_HAND,item);
        var hit=new BlockHitResult(Vec3.atCenterOf(pos.below()),Direction.UP,pos.below(),false);
        var context=new BlockPlaceContext(new UseOnContext(placer,InteractionHand.MAIN_HAND,hit));
        check(((BlockItem)item.getItem()).place(context).consumesAction(),"Native placement failed");placer.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);drops.getFirst().discard();
        check(f.stock()==stock && f.special()==37,"Placed depot lost contents or components");
    }
    static void disassembleAndReassemble()throws Exception{
        var f=fixtures.stream().filter(x->x.mode.equals("bearing")).findFirst().orElseThrow();long expected=f.total();var old=new ArrayList<FakePlayer>();for(var arm:f.arms())old.add(SentryFakePlayer.get(arm));
        var bearing=(MechanicalBearingBlockEntity)level.getBlockEntity(f.controllerPos);bearing.setAngle(0);bearing.getMovedContraption().setAngle(0);bearing.disassemble();
        f.mode="static-group";check(f.total()==expected && f.special()==37,"Disassembly stock changed");for(var fp:old)check(SentryFakePlayer.getArmFromPlayer(fp)==null && fp.getMainHandItem().isEmpty(),"Old shooter survived disassembly");
        for(var arm:f.arms())arm.setSpeed(256);bearing.assemble();check(bearing.getMovedContraption()!=null,"Native reassembly failed");f.mode="bearing";f.entityId=bearing.getMovedContraption().getUUID();check(f.total()==expected,"Reassembly stock changed");
    }
    @SubscribeEvent public static void post(ServerTickEvent.Post event){
        if(!running)return;ticks++;
        try{
            if(phase.equals("prepare")){
                if(ticks<240)return;suppress=true;for(var f:fixtures){f.signatures.clear();for(var arm:f.arms())f.signatures.add(signature(arm));}
                liveCheck();finish(true);return;
            }
            if(step==0){
                if(!ready()){if(ticks>400)throw new AssertionError("Timed out loading fixtures");return;}
                audit();step=1;stepTick=ticks;suppress=false;return;
            }
            if(step==1 && ticks-stepTick>=160){
                suppress=true;liveCheck();
                if(phase.equals("verify")){finish(false);return;}
                step=2;stepTick=ticks;return;
            }
            if(step==2 && ticks-stepTick>=10){beginUnload();return;}
            if(step==3){
                var f=fixtures.getFirst();int cx=f.armPos.getX()>>4,cz=f.armPos.getZ()>>4;
                if(ticks-stepTick>=40 && level.getChunkSource().getChunkNow(cx,cz)==null){
                    test("real chunk unload clears old shooter",()->check(SentryFakePlayer.getArmFromPlayer(unloadedShooter)==null && unloadedShooter.getMainHandItem().isEmpty(),"Shooter not released"));
                    step=4;stepTick=ticks;suppress=false;return;
                }
                if(ticks-stepTick>1000)throw new AssertionError("Timed out waiting for actual chunk unload");return;
            }
            if(step==4 && ticks-stepTick>=40){
                suppress=true;test("no ghost projectile after chunk unload",()->check(ghostShots==0,"Old shooter fired after unload"));
                var f=fixtures.getFirst();var chunk=new net.minecraft.world.level.ChunkPos(f.armPos);level.setChunkForced(chunk.x,chunk.z,true);level.getChunk(chunk.x,chunk.z);
                step=5;stepTick=ticks;return;
            }
            if(step==5 && ticks-stepTick>=20){
                var f=fixtures.getFirst();
                test("chunk reload preserves ammo and components",()->check(f.total()==unloadExpected && f.special()==37,"Chunk reload changed inventory"));
                test("destroy and native-place depot preserve contents",RestartSentrySmoke::breakAndPlace);
                test("active bearing disassembly and reassembly preserve contents",RestartSentrySmoke::disassembleAndReassemble);
                finish(true);
            }
        }catch(Throwable t){failed++;SentryMechanicalArm.LOGGER.error("RESTART FAIL tick",t);finish(false);}
    }
}
