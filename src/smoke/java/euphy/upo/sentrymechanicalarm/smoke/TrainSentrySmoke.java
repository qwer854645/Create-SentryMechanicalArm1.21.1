package euphy.upo.sentrymechanicalarm.smoke;

import com.simibubi.create.*;
import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.EntityKineticBullet;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.content.*;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.SentryFakePlayer;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Native Train -> Carriage -> CarriageContraptionEntity on a dedicated test track graph. */
@EventBusSubscriber(modid="sentrymechanicalarm")
public final class TrainSentrySmoke {
    static ServerLevel level;static int ticks,passed,failed;static boolean running;
    static final List<Lane> lanes=new ArrayList<>();static final Set<Long> forced=new HashSet<>();
    static class Lane {String name;TrackGraph graph;Train train;Carriage carriage;CarriageContraptionEntity entity;MovementContext ctx;VirtualSentryArmBlockEntity arm;Husk target;Vec3 targetPos,start;int initial,shots,hits,at100,at200,at230;float startYaw;double originError,moved,yawChange;}
    interface Check{void run()throws Exception;}
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static void test(String name,Check c){try{c.run();passed++;SentryMechanicalArm.LOGGER.info("TRAIN PASS {}",name);}catch(Throwable t){failed++;SentryMechanicalArm.LOGGER.error("TRAIN FAIL "+name,t);}}
    static void force(BlockPos p){var c=new net.minecraft.world.level.ChunkPos(p);if(!level.getForcedChunks().contains(c.toLong())){level.setChunkForced(c.x,c.z,true);forced.add(c.toLong());}level.getChunkAt(p);}
    @SubscribeEvent public static void start(ServerStartedEvent event){
        if(!"train".equals(System.getProperty("sentry.smoke")))return;
        running=true;level=event.getServer().overworld();
        test("dedicated server has no connected players",()->check(event.getServer().getPlayerCount()==0,"Players connected"));
        test("assemble native straight train",()->lane("straight",0,new Vec3(0,0,128),false));
        test("assemble native diagonal ceiling train",()->lane("diagonal-ceiling",1,new Vec3(90,0,90),true));
        test("assemble native uphill train",()->lane("uphill",2,new Vec3(0,32,128),false));
        test("assemble native curved train",()->lane("curve",3,new Vec3(16,0,16),false));
    }
    static void lane(String name,int index,Vec3 delta,boolean ceiling)throws Exception{
        var base=new BlockPos(3000+index*192,200,3000);
        for(int d=-16;d<=144;d+=16)for(int x=-16;x<=112;x+=16)force(base.offset(x,0,d));
        level.setBlock(base,AllBlocks.SMALL_BOGEY.getDefaultState(),3);
        level.setBlock(base.above(),Blocks.SLIME_BLOCK.defaultBlockState(),3);
        var armPos=base.above(2);
        level.setBlock(armPos,SentryRegistry.SENTRY_ARM_BLOCK.get().defaultBlockState().setValue(SentryArmBlock.CEILING,ceiling),3);
        var placed=(SentryArmBlockEntity)level.getBlockEntity(armPos);
        placed.setHeldItem(GunItemBuilder.create().setId(ResourceLocation.parse(ceiling?"tacz:m16a4":"tacz:ak47")).setFireMode(ceiling?FireMode.BURST:FireMode.AUTO).setAmmoCount(30).setAmmoInBarrel(true).build());placed.setSpeed(256);
        var ammoBox=new ItemStack(com.tacz.guns.init.ModItems.AMMO_BOX.get());
        var box=(com.tacz.guns.api.item.IAmmoBox)ammoBox.getItem();
        box.setAmmoId(ammoBox,com.tacz.guns.api.TimelessAPI.getCommonGunIndex(ResourceLocation.parse(ceiling?"tacz:m16a4":"tacz:ak47")).orElseThrow().getGunData().getAmmoId());
        box.setAmmoCount(ammoBox,Boolean.getBoolean("sentry.trainDepot")?0:200);placed.addAmmoBox(ammoBox);
        if(Boolean.getBoolean("sentry.trainDepot")){
            var depotPos=base.above().east();
            level.setBlock(depotPos,com.tacz.guns.init.ModBlocks.AMMO_STORAGE_DIAMOND.get().defaultBlockState(),3);
            var depot=(com.tacz.guns.block.entity.AmmoStorageBlockEntity)level.getBlockEntity(depotPos);
            var standard=com.tacz.guns.api.item.builder.AmmoItemBuilder.create().setId(box.getAmmoId(ammoBox)).setCount(2000).build();
            depot.insertAmmo(standard,false);var special=standard.copyWithCount(37);
            special.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Preserved train variant"));depot.insertAmmo(special,false);
        }
        var contraption=new CarriageContraption(Direction.SOUTH);
        check(contraption.assemble(level,base),"Carriage assembly rejected");
        check(contraption.getActors().stream().anyMatch(p->p.getKey().state().getBlock() instanceof SentryArmBlock),"Sentry not captured");
        contraption.removeBlocksFromWorld(level,BlockPos.ZERO);
        var graph=new TrackGraph();
        var a=new TrackNodeLocation.DiscoveredLocation(level,base.getCenter()).withNormal(new Vec3(0,1,0)).materials(TrackMaterial.ANDESITE,TrackMaterial.ANDESITE);
        var b=new TrackNodeLocation.DiscoveredLocation(level,base.getCenter().add(delta)).withNormal(new Vec3(0,1,0)).materials(TrackMaterial.ANDESITE,TrackMaterial.ANDESITE);
        com.simibubi.create.content.trains.track.BezierConnection curve=null;
        if(name.equals("curve"))curve=new com.simibubi.create.content.trains.track.BezierConnection(
                net.createmod.catnip.data.Couple.create(base,base.offset(16,0,16)),
                net.createmod.catnip.data.Couple.create(a.getLocation(),b.getLocation()),
                net.createmod.catnip.data.Couple.create(new Vec3(0,0,1),new Vec3(-1,0,0)),
                net.createmod.catnip.data.Couple.create(new Vec3(0,1,0),new Vec3(0,1,0)),true,false,TrackMaterial.ANDESITE);
        graph.createNodeIfAbsent(a);graph.createNodeIfAbsent(b);graph.connectNodes(level,a,b,curve);Create.RAILWAYS.putGraphWithDefaultGroup(graph);
        var nodeA=graph.locateNode(a);var nodeB=graph.locateNode(b);var edge=graph.getConnectionsFrom(nodeA).get(nodeB);
        check(edge!=null,"No native edge");
        double center=edge.getLength()/2;
        var bogey=new CarriageBogey(AllBlocks.SMALL_BOGEY.get(),false,new CompoundTag(),new TravellingPoint(nodeA,nodeB,edge,center,false),new TravellingPoint(nodeA,nodeB,edge,center-2,false));
        var carriage=new Carriage(bogey,null,0);
        var train=new Train(UUID.randomUUID(),UUID.randomUUID(),graph,List.of(carriage),List.of(),false,0);Create.RAILWAYS.addTrain(train);
        carriage.setContraption(level,contraption);carriage.manageEntities(level);
        var entity=carriage.anyAvailableEntity();check(entity!=null,"Carriage entity not instantiated");
        var lane=new Lane();lane.name=name;lane.graph=graph;lane.train=train;lane.carriage=carriage;lane.entity=entity;lane.start=entity.position();lane.startYaw=entity.yaw;
        lane.ctx=entity.getContraption().getActors().stream().filter(p->p.getValue().state.getBlock() instanceof SentryArmBlock).findFirst().orElseThrow().getValue();
        lane.arm=SentryMovementBehaviour.getOrCreate(lane.ctx);lane.initial=laneAmmo(lane);
        if(Boolean.getBoolean("sentry.trainDepot"))check(lane.initial==2068,"Mounted train depot missing");
        lane.targetPos=lane.arm.getProjectedWorldPos().add(10,ceiling?-2:1,0);
        lane.target=new Husk(EntityType.HUSK,level);lane.target.setPos(lane.targetPos);lane.target.setNoAi(true);lane.target.setNoGravity(true);lane.target.setPersistenceRequired();
        lane.target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1024);lane.target.setHealth(1024);
        lane.target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);level.addFreshEntity(lane.target);lanes.add(lane);
    }
    static int ammo(SentryArmBlockEntity arm){
        var stack=arm.getHeldItem();var gun=IGun.getIGunOrNull(stack);int count=gun.getCurrentAmmoCount(stack)+(gun.hasBulletInBarrel(stack)?1:0);
        for(var stackBox:arm.attachedAmmoBoxes)if(stackBox.getItem() instanceof com.tacz.guns.api.item.IAmmoBox box)count+=box.getAmmoCount(stackBox);
        return count;
    }
    static int laneAmmo(Lane lane){return ammo(lane.arm)+(Boolean.getBoolean("sentry.trainDepot")?(int)AmmoDepotSmoke.total(lane.entity.getContraption().getStorage().getAllItems()):0);}
    @SubscribeEvent public static void pre(ServerTickEvent.Pre event){
        if(!running)return;
        for(var lane:lanes){
            lane.target.setPos(lane.targetPos);lane.target.setDeltaMovement(Vec3.ZERO);lane.target.setHealth(lane.target.getMaxHealth());
            lane.train.manualTick=true;lane.train.speed=ticks<100?.045:ticks<200?-.045:ticks<340?0:.045;lane.train.targetSpeed=lane.train.speed;
        }
    }
    @SubscribeEvent public static void bullet(EntityJoinLevelEvent event){
        if(!running || !(event.getEntity() instanceof EntityKineticBullet b) || !(b.getOwner() instanceof FakePlayer fp))return;
        for(var lane:lanes)if(SentryFakePlayer.getArmFromPlayer(fp)==lane.arm){lane.shots++;lane.originError=Math.max(lane.originError,b.position().distanceTo(lane.arm.getActualMuzzlePos()));}
    }
    @SubscribeEvent public static void damage(LivingIncomingDamageEvent event){if(!running || !(event.getSource().getEntity() instanceof FakePlayer))return;for(var lane:lanes)if(event.getEntity()==lane.target)lane.hits++;}
    @SubscribeEvent public static void post(ServerTickEvent.Post event){
        if(!running)return;
        ticks++;
        for(var lane:lanes){
            lane.moved=Math.max(lane.moved,lane.entity.position().distanceTo(lane.start));
            lane.yawChange=Math.max(lane.yawChange,Math.abs(net.minecraft.util.Mth.wrapDegrees(lane.entity.yaw-lane.startYaw)));
            if(ticks==100)lane.at100=lane.shots;if(ticks==200)lane.at200=lane.shots;if(ticks==340)lane.at230=lane.shots;
        }
        if(ticks<400)return;
        for(var lane:lanes){
            SentryMechanicalArm.LOGGER.info("TRAIN METRICS {} moved={} shots={} hits={} ammo={} initial={} originError={} alive={} rpm={} yaw={} pitch={} target={} result={}",lane.name,lane.moved,lane.shots,lane.hits,laneAmmo(lane),lane.initial,lane.originError,lane.entity.isAlive(),lane.arm.getSpeed(),lane.entity.yaw,lane.entity.pitch,lane.arm.getSentryStatus(),lane.arm.getLastShootResult());
            test(lane.name+" native train travels",()->check(lane.moved>2 && lane.entity.isAlive(),"Train did not travel"));
            test(lane.name+" server autonomous fire conserves ammo",()->check(lane.shots>0 && lane.initial-laneAmmo(lane)==lane.shots,"No shots or ammo mismatch"));
            test(lane.name+" moving muzzle matches real projectile",()->check(lane.shots>0 && lane.originError<1e-5,"Muzzle mismatch"));
            test(lane.name+" real damage",()->check(lane.hits>0,"No actual damage"));
            test(lane.name+" reversing still fires",()->check(lane.at200>lane.at100,"No fire in reverse phase"));
            test(lane.name+" parked captured-power sentry still fires",()->check(lane.at230>lane.at200,"No fire while parked"));
            if(lane.name.equals("curve"))test("curve changes carriage heading",()->check(lane.yawChange>5,"No curved traversal"));
            test(lane.name+" carriage NBT retains gun state",()->{
                new SentryMovementBehaviour().writeExtraData(lane.ctx);
                var restored=new CarriageContraption();restored.readNBT(level,lane.entity.getContraption().writeNBT(level.registryAccess(),false),false);
                var ctx=restored.getActors().stream().filter(p->p.getValue().state.getBlock() instanceof SentryArmBlock).findFirst().orElseThrow().getValue();
                check(ammo(VirtualSentryArmBlockEntity.fromData(ctx.localPos,ctx.state,ctx.blockEntityData,level))==ammo(lane.arm),"Stale carriage ammo");
            });
            if(Boolean.getBoolean("sentry.trainDepot")){
                test(lane.name+" depot supplies beyond initial magazine",()->check(lane.shots>60,"Depot never refilled the empty box"));
                test(lane.name+" depot variants survive carriage serialization",()->{
                    var palette=new DimensionPalette();var saved=lane.train.write(palette,level.registryAccess());
                    var copy=Train.read(saved,level.registryAccess(),java.util.Map.of(lane.graph.id,lane.graph),palette);
                    var inv=copy.carriages.getFirst().storage.getAllItems();long special=0;
                    for(int slot=0;slot<inv.getSlots();slot++)if(inv.getStackInSlot(slot).has(net.minecraft.core.component.DataComponents.CUSTOM_NAME))special+=inv.getStackInSlot(slot).getCount();
                    check(special==37 && AmmoDepotSmoke.total(inv)==AmmoDepotSmoke.total(lane.entity.getContraption().getStorage().getAllItems()),"Depot NBT or variant lost");
                });
            }
            test(lane.name+" entity removal clears shooter",()->{var fp=SentryFakePlayer.get(lane.arm);lane.entity.discard();check(SentryFakePlayer.getArmFromPlayer(fp)==null && fp.getMainHandItem().isEmpty(),"Shooter retained");});
            lane.target.discard();Create.RAILWAYS.removeTrain(lane.train.id);Create.RAILWAYS.trackNetworks.remove(lane.graph.id);
        }
        running=false;for(long k:forced){var c=new net.minecraft.world.level.ChunkPos(k);level.setChunkForced(c.x,c.z,false);}
        SentryMechanicalArm.LOGGER.info("TRAIN RESULT passed={} failed={}",passed,failed);CasingIntegrationSmoke.finish();level.getServer().halt(false);
    }
}
