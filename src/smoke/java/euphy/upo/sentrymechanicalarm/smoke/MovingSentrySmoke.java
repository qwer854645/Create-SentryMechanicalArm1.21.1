package euphy.upo.sentrymechanicalarm.smoke;

import com.simibubi.create.AllEntityTypes;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.tacz.guns.api.item.*;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.EntityKineticBullet;
import dev.ryanhcode.sable.companion.*;
import dev.ryanhcode.sable.companion.math.*;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.content.*;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.*;
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
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

@EventBusSubscriber(modid="sentrymechanicalarm")
public final class MovingSentrySmoke {
    static ServerLevel level;static boolean running,initializing;static int ticks,passed,failed;
    static final List<Lane> lanes=new ArrayList<>();static final Set<Long> forced=new HashSet<>();
    static final class TestEntity extends ControlledContraptionEntity {
        TestEntity(ServerLevel l,BearingContraption c){super(AllEntityTypes.CONTROLLED_CONTRAPTION.get(),l);setContraption(c);setRotationAxis(Direction.Axis.Y);}
    }
    static class Lane {String name;TestEntity entity;MovementContext ctx;VirtualSentryArmBlockEntity arm;Husk target;Vec3 pos;int shots,hits;double error;int initial;}
    static SubLevelAccess ship;static SentryArmBlockEntity shipArm;static Husk shipTarget;static Vec3 shipTargetPos;static int shipShots,shipHits,shipInitial;static double shipError;
    interface Check {void run()throws Exception;}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void test(String name,Check check){try{check.run();passed++;SentryMechanicalArm.LOGGER.info("COMPAT PASS {}",name);}catch(Throwable e){failed++;SentryMechanicalArm.LOGGER.error("COMPAT FAIL "+name,e);}}
    static void force(BlockPos p){var c=new net.minecraft.world.level.ChunkPos(p);if(!level.getForcedChunks().contains(c.toLong())){level.setChunkForced(c.x,c.z,true);forced.add(c.toLong());}level.getChunkAt(p);}
    @SubscribeEvent public static void start(ServerStartedEvent event){
        if(!"compat".equals(System.getProperty("sentry.smoke")))return;
        running=true;level=event.getServer().overworld();
        for(int n=0;n<3;n++){var p=new BlockPos(1800+n*80,220,1800);force(p);force(p.west());force(p.south(16));force(p.south(32));}
        var p=new BlockPos(2100,220,1800);force(p);force(p.south(16));force(p.south(32));
        initializing=true;
    }
    static void setup(){
        test("assemble floor actor",()->lane("floor","ak47",false,0));
        test("assemble ceiling burst actor",()->lane("ceiling-burst","m16a4",true,1));
        test("assemble manual-action actor",()->lane("manual","m700",false,2));
        test("Sable arbitrary rotation and scale round trip",()->{
            var p=new Pose3d();p.position().set(120,210,450);p.rotationPoint().set(29000000,210,-29000000);
            p.orientation().rotateXYZ(.32,.81,-.27);p.scale().set(1.2,.9,1.1);
            Vec3 local=new Vec3(29000000.3,211.4,-28999998.6),dir=new Vec3(.2,.4,-.8).normalize();
            check(p.transformPositionInverse(p.transformPosition(local)).distanceTo(local)<1e-7,"position drift");
            check(p.transformNormalInverse(p.transformNormal(dir)).normalize().distanceTo(dir)<1e-8,"direction drift");
        });
        test("empty attached box resupplies through extraction once",()->{
            var arm=lanes.getFirst().arm;var box=new ItemStack(com.tacz.guns.init.ModItems.AMMO_BOX.get());
            arm.attachedAmmoBoxes.set(0,box);
            var id=com.tacz.guns.api.TimelessAPI.getCommonGunIndex(ResourceLocation.parse("tacz:ak47")).orElseThrow().getGunData().getAmmoId();
            var ammo=com.tacz.guns.api.item.builder.AmmoItemBuilder.create().setId(id).setCount(37).build();
            var inv=new net.neoforged.neoforge.items.ItemStackHandler(1);inv.setStackInSlot(0,ammo);
            SentryAmmoSupply.refill(arm,inv);SentryAmmoSupply.refill(arm,inv);
            check(inv.getStackInSlot(0).isEmpty() && ((IAmmoBox)box.getItem()).getAmmoCount(box)==37,"ammo duplicated/lost");
            arm.attachedAmmoBoxes.set(0,ItemStack.EMPTY);
        });
        if(net.neoforged.fml.ModList.get().isLoaded("sable"))test("real Sable assembly uses anchor and full pose",MovingSentrySmoke::sableAssembly);
    }
    static void lane(String name,String gun,boolean ceiling,int n)throws Exception{
        var p=new BlockPos(1800+n*80,220,1800);force(p);force(p.south(16));force(p.south(32));
        level.setBlock(p,SentryRegistry.SENTRY_ARM_BLOCK.get().defaultBlockState().setValue(SentryArmBlock.CEILING,ceiling),3);
        var placed=(SentryArmBlockEntity)level.getBlockEntity(p);
        placed.setHeldItem(GunItemBuilder.create().setId(ResourceLocation.parse("tacz:"+gun)).setFireMode(gun.equals("m16a4")?FireMode.BURST:FireMode.AUTO).setAmmoCount(100).setAmmoInBarrel(true).build());placed.setSpeed(256);
        level.setBlock(p.west(),SentryRegistry.CASING_COLLECTOR_BLOCK.get().defaultBlockState(),3);
        level.addFreshEntity(new com.simibubi.create.content.contraptions.glue.SuperGlueEntity(level,
                com.simibubi.create.content.contraptions.glue.SuperGlueEntity.span(p,p.west())));
        var c=new BearingContraption(false,Direction.UP);check(c.assemble(level,p.below()),"Create assembly rejected");
        c.removeBlocksFromWorld(level,BlockPos.ZERO);
        var entity=new TestEntity(level,c);entity.setPos(p.getX(),p.getY(),p.getZ());level.addFreshEntity(entity);
        var ctx=c.getActors().stream().filter(a->a.getValue().state.getBlock() instanceof SentryArmBlock).findFirst().orElseThrow().getValue();
        var lane=new Lane();lane.name=name;lane.entity=entity;lane.ctx=ctx;lane.arm=SentryMovementBehaviour.getOrCreate(ctx);lane.initial=ammo(lane.arm);
        lane.target=new Husk(EntityType.HUSK,level);lane.pos=p.south(12).getCenter().add(0,ceiling?-2:1,0);
        lane.target.setPos(lane.pos);lane.target.setNoAi(true);lane.target.setNoGravity(true);lane.target.setPersistenceRequired();
        lane.target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(10000);lane.target.setHealth(10000);
        lane.target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);level.addFreshEntity(lane.target);lanes.add(lane);
    }
    static int ammo(SentryArmBlockEntity arm){var s=arm.getHeldItem();var g=IGun.getIGunOrNull(s);return g.getCurrentAmmoCount(s)+(g.hasBulletInBarrel(s)?1:0);}
    static void sableAssembly()throws Exception{
        var p=new BlockPos(2100,220,1800);force(p);force(p.south(16));force(p.south(32));
        level.setBlock(p,SentryRegistry.SENTRY_ARM_BLOCK.get().defaultBlockState(),3);
        ((SentryArmBlockEntity)level.getBlockEntity(p)).setHeldItem(GunItemBuilder.create().setId(ResourceLocation.parse("tacz:ak47")).setFireMode(FireMode.AUTO).setAmmoCount(100).setAmmoInBarrel(true).build());
        level.setBlock(p.east(),com.simibubi.create.AllBlocks.COGWHEEL.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,Direction.Axis.Y),3);
        level.setBlock(p.east().below(),com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(net.minecraft.world.level.block.DirectionalBlock.FACING,Direction.UP),3);
        level.setBlock(p.west(),SentryRegistry.CASING_COLLECTOR_BLOCK.get().defaultBlockState(),3);
        var positions=List.of(p,p.east(),p.east().below(),p.west());
        var cls=Class.forName("dev.ryanhcode.sable.api.SubLevelAssemblyHelper");
        var sub=(SubLevelAccess)cls.getMethod("assembleBlocks",ServerLevel.class,BlockPos.class,Iterable.class,BoundingBox3ic.class)
                .invoke(null,level,p,positions,BoundingBox3i.from(positions));
        check(sub!=null,"no ship assembled");
        var pose=(Pose3d)sub.logicalPose();
        var localArm=BlockPos.containing(pose.transformPositionInverse(p.getCenter()));
        shipArm=(SentryArmBlockEntity)level.getBlockEntity(localArm);
        check(shipArm!=null,"arm not carried to ship plot");
        var motor=(com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)level.getBlockEntity(localArm.east().below());motor.initialize();motor.generatedSpeed.setValue(256);
        shipInitial=ammo(shipArm);ship=sub;
        Object system=Class.forName("dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem").getMethod("require",net.minecraft.world.level.Level.class).invoke(null,level);
        system.getClass().getMethod("setPaused",boolean.class).invoke(system,true);
        pose.orientation().rotateXYZ(.2,.7,-.3);
        var local=new Vec3(pose.rotationPoint().x,pose.rotationPoint().y,pose.rotationPoint().z).add(.3,1.7,-.9);
        var anchor=BlockPos.containing(new Vec3(pose.rotationPoint().x,pose.rotationPoint().y,pose.rotationPoint().z));
        check(SableCompanion.INSTANCE.getContaining(level,anchor)!=null,"plot not registered");
        var in=new SentryGunGeometry.Pose(local,new Vec3(.2,.3,-.9).normalize());
        var out=euphy.upo.sentrymechanicalarm.compat.AeronauticsHelper.sablePoseToWorld(level,anchor,in);
        check(out.position().distanceTo(pose.transformPosition(local))<1e-7 && out.forward().distanceTo(pose.transformNormal(in.forward()).normalize())<1e-8,"wrong world pose");
        shipTarget=new Husk(EntityType.HUSK,level);shipTargetPos=p.south(12).getCenter().add(0,1,0);
        shipTarget.setPos(shipTargetPos);shipTarget.setNoAi(true);shipTarget.setNoGravity(true);shipTarget.setPersistenceRequired();
        shipTarget.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1024);shipTarget.setHealth(1024);level.addFreshEntity(shipTarget);
    }
    @SubscribeEvent public static void spawn(EntityJoinLevelEvent e){
        if(!running || !(e.getEntity() instanceof EntityKineticBullet b) || !(b.getOwner() instanceof FakePlayer fp))return;
        for(var lane:lanes)if(SentryFakePlayer.getArmFromPlayer(fp)==lane.arm){lane.shots++;lane.error=Math.max(lane.error,b.position().distanceTo(lane.arm.getActualMuzzlePos()));}
        if(SentryFakePlayer.getArmFromPlayer(fp)==shipArm){shipShots++;shipError=Math.max(shipError,b.position().distanceTo(shipArm.getActualMuzzlePos()));}
    }
    @SubscribeEvent public static void hit(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent e){
        if(!running || !(e.getSource().getEntity() instanceof FakePlayer))return;
        for(var lane:lanes)if(e.getEntity()==lane.target)lane.hits++;
        if(e.getEntity()==shipTarget)shipHits++;
    }
    @SubscribeEvent public static void pre(ServerTickEvent.Pre e){if(!running)return;for(var lane:lanes){lane.target.setPos(lane.pos);lane.target.setDeltaMovement(Vec3.ZERO);lane.entity.setAngle((float)(Math.sin(ticks*.01)*15));}
        if(ship!=null){((Pose3d)ship.logicalPose()).orientation().identity().rotateXYZ(.2,.7+Math.sin(ticks*.01)*.15,-.3);try{ship.getClass().getMethod("forceUpdateGlobalBounds").invoke(ship);}catch(Exception ex){throw new RuntimeException(ex);}}
        if(shipTarget!=null){shipTarget.setPos(shipTargetPos);shipTarget.setDeltaMovement(Vec3.ZERO);}
    }
    @SubscribeEvent public static void post(ServerTickEvent.Post e){
        if(!running)return;
        if(initializing){if(++ticks<20)return;initializing=false;ticks=0;setup();return;}
        if(++ticks<260)return;
        for(var lane:lanes){
            SentryMechanicalArm.LOGGER.info("COMPAT METRICS {} shots={} ammo={} initial={} error={} health={} result={} angles={},{},{}",lane.name,lane.shots,ammo(lane.arm),lane.initial,lane.error,lane.target.getHealth(),lane.arm.getLastShootResult(),lane.arm.baseAngle.getValue(),lane.arm.headAngle.getValue(),lane.arm.upperArmAngle.getValue());
            test(lane.name+" autonomous server fire and conservation",()->check(lane.shots>0 && lane.initial-ammo(lane.arm)==lane.shots,"no shots or wrong ammo"));
            test(lane.name+" canonical moving muzzle",()->check(lane.shots>0 && lane.error<1e-5,"wrong muzzle"));
            test(lane.name+" real projectile damage",()->check(lane.hits>0,"no damage events"));
            test(lane.name+" moving collector receives every paid shot",()->{
                int count=0;for(var storage:lane.entity.getContraption().getStorage().getAllItemStorages().values())
                    if(storage instanceof euphy.upo.sentrymechanicalarm.compat.CasingCollectorMountedStorage)
                        for(int slot=0;slot<storage.getSlots();slot++)count+=storage.getStackInSlot(slot).getCount();
                check(count==lane.shots&&count>0,"collector/shot mismatch "+count+"/"+lane.shots);
            });
            test(lane.name+" saved actor state survives NBT",()->{
                new SentryMovementBehaviour().writeExtraData(lane.ctx);
                var copy=new BearingContraption();copy.readNBT(level,lane.entity.getContraption().writeNBT(level.registryAccess(),false),false);
                var restored=copy.getActors().getFirst().getValue();
                check(ammo(VirtualSentryArmBlockEntity.fromData(restored.localPos,restored.state,restored.blockEntityData,level))==ammo(lane.arm),"stale saved ammo");
            });
            test(lane.name+" stop releases fake player",()->{var fp=SentryFakePlayer.get(lane.arm);new SentryMovementBehaviour().stopMoving(lane.ctx);check(SentryFakePlayer.getArmFromPlayer(fp)==null && fp.getMainHandItem().isEmpty(),"shooter leaked");});
            test(lane.name+" disassembly restores consumed state",()->{
                int expected=ammo(lane.arm);lane.entity.setAngle(0);lane.entity.disassemble();
                var restored=level.getBlockEntity(BlockPos.containing(lane.entity.position()));
                check(restored instanceof SentryArmBlockEntity && ammo((SentryArmBlockEntity)restored)==expected,"world placement restored stale state");
                var collectorPos=restored.getBlockPos().west();
                check(level.getBlockEntity(collectorPos) instanceof CasingCollectorBlockEntity,"collector not restored");
                var collector=(CasingCollectorBlockEntity)level.getBlockEntity(collectorPos);int cases=0;
                for(int slot=0;slot<27;slot++)cases+=collector.getItem(slot).getCount();
                check(cases==lane.shots,"unmount changed collector count");collector.clearContent();level.removeBlock(collectorPos,false);
                ((SentryArmBlockEntity)restored).setHeldItem(ItemStack.EMPTY);level.removeBlock(restored.getBlockPos(),false);
            });
            lane.target.discard();lane.entity.discard();
        }
        if(shipArm!=null){
            SentryMechanicalArm.LOGGER.info("COMPAT SHIP shots={} hits={} initial={} ammo={} error={} rpm={} pose={}",shipShots,shipHits,shipInitial,ammo(shipArm),shipError,shipArm.getSpeed(),shipArm.getGunPose());
            test("tilted rotating Sable ship fires conservatively",()->check(shipShots>0 && shipInitial-ammo(shipArm)==shipShots,"no shots or wrong ammo"));
            test("Sable world-space muzzle",()->check(shipShots>0 && shipError<1e-5,"wrong ship muzzle"));
            test("Sable real projectile damage",()->check(shipHits>0,"no ship damage events"));
            test("Sable same-structure collector receives every paid shot",()->{
                var collector=(CasingCollectorBlockEntity)level.getBlockEntity(shipArm.getBlockPos().west());int count=0;
                for(int slot=0;slot<27;slot++)count+=collector.getItem(slot).getCount();
                check(count==shipShots&&count>0,"ship collector/shot mismatch "+count+"/"+shipShots);
            });
            SentryFakePlayer.remove(shipArm);shipTarget.discard();
            test("Sable fixture removes its physics body cleanly",()->{
                var containerType=Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
                Object container;
                var reasonType=Class.forName("dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason");
                Object reason=reasonType.getField("REMOVED").get(null);
                try {
                    var lookup=java.lang.invoke.MethodHandles.publicLookup();
                    container=lookup.findStatic(containerType,"getContainer",java.lang.invoke.MethodType.methodType(
                            Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer"),ServerLevel.class)).invoke(level);
                    lookup.findVirtual(containerType,"removeSubLevel",java.lang.invoke.MethodType.methodType(void.class,
                            Class.forName("dev.ryanhcode.sable.sublevel.SubLevel"),reasonType)).invoke(container,ship,reason);
                    lookup.findVirtual(containerType,"processSubLevelRemovals",java.lang.invoke.MethodType.methodType(void.class)).invoke(container);
                } catch(Throwable t){throw new Exception(t);}
            });
        }
        running=false;for(long k:forced){var c=new net.minecraft.world.level.ChunkPos(k);level.setChunkForced(c.x,c.z,false);}
        SentryMechanicalArm.LOGGER.info("COMPAT RESULT passed={} failed={}",passed,failed);level.getServer().halt(false);
    }
}
