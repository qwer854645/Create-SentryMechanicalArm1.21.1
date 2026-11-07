package euphy.upo.sentrymechanicalarm.smoke;

import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.api.contraption.storage.item.*;
import com.tacz.guns.api.item.*;
import com.tacz.guns.api.item.builder.*;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.block.entity.AmmoStorageBlockEntity;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.entity.EntityKineticBullet;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.compat.*;
import euphy.upo.sentrymechanicalarm.content.*;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import java.util.*;

@EventBusSubscriber(modid="sentrymechanicalarm")
public final class AmmoDepotSmoke {
    static ServerLevel level;static boolean running;static int ticks,passed,failed;
    static final List<Lane> lanes=new ArrayList<>();static final Set<Long> forced=new HashSet<>();
    static final List<BlockPos> blocks=new ArrayList<>();
    static final ResourceLocation ID=ResourceLocation.parse("tacz:762x39");
    static ResourceLocation ammoId;
    static class Lane {String name;List<SentryArmBlockEntity> arms=new ArrayList<>();AmmoStorageBlockEntity depot;MovingSentrySmoke.TestEntity entity;dev.ryanhcode.sable.companion.SubLevelAccess ship;Husk target;Vec3 targetPos;long initial;int shots,hits;double error;}
    interface Check{void run()throws Exception;}
    static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    static void test(String name,Check c){try{c.run();passed++;SentryMechanicalArm.LOGGER.info("DEPOT PASS {}",name);}catch(Throwable e){failed++;SentryMechanicalArm.LOGGER.error("DEPOT FAIL "+name,e);}}
    static void force(BlockPos p){var c=new net.minecraft.world.level.ChunkPos(p);if(!level.getForcedChunks().contains(c.toLong())){level.setChunkForced(c.x,c.z,true);forced.add(c.toLong());}level.getChunkAt(p);}
    static ItemStack ammo(int count){return AmmoItemBuilder.create().setId(ammoId).setCount(count).build();}
    static ItemStack special(int count){var s=ammo(count);s.set(DataComponents.CUSTOM_NAME,Component.literal("Component variant"));return s;}
    static long total(IItemHandler inv){long n=0;for(int s=0;s<inv.getSlots();s++)n+=inv.getStackInSlot(s).getCount();return n;}
    static long armAmmo(SentryArmBlockEntity arm){var s=arm.getHeldItem();var g=(IGun)s.getItem();long n=g.getCurrentAmmoCount(s)+(g.hasBulletInBarrel(s)?1:0);for(var b:arm.attachedAmmoBoxes)if(b.getItem() instanceof IAmmoBox box)n+=box.getAmmoCount(b);return n;}
    static IItemHandler inventory(Lane lane){return lane.entity==null?lane.depot.getItemHandler():lane.entity.getContraption().getStorage().getAllItems();}
    static long all(Lane lane){return total(inventory(lane))+lane.arms.stream().mapToLong(AmmoDepotSmoke::armAmmo).sum();}
    @SubscribeEvent public static void start(ServerStartedEvent event){
        if(!"depot".equals(System.getProperty("sentry.smoke")))return;
        running=true;level=event.getServer().overworld();
        ammoId=com.tacz.guns.api.TimelessAPI.getCommonGunIndex(ResourceLocation.parse("tacz:ak47")).orElseThrow().getGunData().getAmmoId();
        Block[] tiers={ModBlocks.AMMO_STORAGE.get(),ModBlocks.AMMO_STORAGE_GOLD.get(),ModBlocks.AMMO_STORAGE_DIAMOND.get()};
        for(int t=0;t<3;t++){
            int tier=t;test("tier "+t+" capability, codec and exact variant ledger",()->ledger(tiers[tier],tier));
            test("tier "+t+" static supply scene",()->scene(tiers[tier],tier,false));
            test("tier "+t+" moving shared supply scene",()->scene(tiers[tier],tier,true));
        }
        test("long totals survive codec and unmount without clamping",AmmoDepotSmoke::bulk);
        test("special-only stock is preserved and reported",()->{
            var lane=lanes.getFirst();var arm=lane.arms.getFirst();var box=arm.attachedAmmoBoxes.getFirst();var b=(IAmmoBox)box.getItem();
            int old=b.getAmmoCount(box);b.setAmmoCount(box,0);
            var inv=new net.neoforged.neoforge.items.ItemStackHandler(1);inv.setStackInSlot(0,special(37));
            SentryAmmoSupply.refill(arm,inv);check(b.getAmmoCount(box)==0 && inv.getStackInSlot(0).getCount()==37 && arm.getSupplyState()==SentryAmmoSupply.State.SPECIAL_ONLY,"Special ammo flattened");b.setAmmoCount(box,old);
        });
        test("missing box, full box and wrong creative caliber report correctly",()->{
            var lane=lanes.getFirst();var arm=lane.arms.getFirst();var box=arm.attachedAmmoBoxes.getFirst();var b=(IAmmoBox)box.getItem();
            arm.attachedAmmoBoxes.set(0,ItemStack.EMPTY);SentryAmmoSupply.refill(arm,lane.depot.getItemHandler());check(arm.getSupplyState()==SentryAmmoSupply.State.NO_BOX,"Missing box state");
            arm.attachedAmmoBoxes.set(0,box);int old=b.getAmmoCount(box);b.setAmmoId(box,ammoId);
            int cap=com.tacz.guns.api.TimelessAPI.getCommonAmmoIndex(ammoId).orElseThrow().getStackSize()*com.tacz.guns.config.sync.SyncConfig.AMMO_BOX_STACK_SIZE.get();
            b.setAmmoCount(box,cap);long stock=total(lane.depot.getItemHandler());SentryAmmoSupply.refill(arm,lane.depot.getItemHandler());
            check(arm.getSupplyState()==SentryAmmoSupply.State.FULL && total(lane.depot.getItemHandler())==stock,"Full box drained stock");b.setAmmoCount(box,old);
            var creative=box.copy();b.setCreative(creative,false);b.setAmmoId(creative,ResourceLocation.parse("tacz:9mm"));arm.attachedAmmoBoxes.set(0,creative);
            SentryAmmoSupply.refill(arm,lane.depot.getItemHandler());check(arm.getSupplyState()==SentryAmmoSupply.State.NO_BOX && total(lane.depot.getItemHandler())==stock,"Creative caliber mismatch consumed stock");arm.attachedAmmoBoxes.set(0,box);
        });
        test("denied extraction never credits ammunition",()->{
            var arm=lanes.getFirst().arms.getFirst();var box=arm.attachedAmmoBoxes.getFirst();var b=(IAmmoBox)box.getItem();int old=b.getAmmoCount(box);b.setAmmoCount(box,0);
            IItemHandler denied=new IItemHandler(){public int getSlots(){return 1;}public ItemStack getStackInSlot(int slot){return ammo(100);}public ItemStack insertItem(int slot,ItemStack s,boolean sim){return s;}public ItemStack extractItem(int slot,int n,boolean sim){return ItemStack.EMPTY;}public int getSlotLimit(int slot){return 64;}public boolean isItemValid(int slot,ItemStack s){return false;}};
            SentryAmmoSupply.refill(arm,denied);check(b.getAmmoCount(box)==0 && arm.getSupplyState()==SentryAmmoSupply.State.NO_STOCK,"Denied transfer created ammo");b.setAmmoCount(box,old);
        });
        if(net.neoforged.fml.ModList.get().isLoaded("sable"))test("real Sable ship with adjacent depot",AmmoDepotSmoke::shipScene);
    }
    static void ledger(Block tier,int i)throws Exception{
        var p=new BlockPos(4700+i*4,220,4500);force(p);blocks.add(p);level.setBlock(p,tier.defaultBlockState(),3);
        var depot=(AmmoStorageBlockEntity)level.getBlockEntity(p);
        check(MountedItemStorageType.REGISTRY.get(tier)==SentryAmmoDepotCompat.TYPE.get(),"Wrong mounted adapter");
        depot.insertAmmo(ammo(1500),false);depot.insertAmmo(special(37),false);
        var mounted=AmmoDepotMountedStorage.capture(depot);
        int size=com.tacz.guns.api.TimelessAPI.getCommonAmmoIndex(ammoId).orElseThrow().getStackSize();
        var empty=new AmmoDepotMountedStorage(depot.getCapacityStacks(),List.of());
        check(empty.insertItem(0,ammo(depot.getCapacityStacks()*size+1),true).getCount()==1 && empty.stock().isEmpty(),"Capacity simulation mismatch");
        var before=mounted.stock();mounted.extractItem(0,10,true);mounted.insertItem(0,ammo(5),true);
        check(mounted.count(0)==1500 && mounted.count(1)==37,"Simulation mutated stock");
        var ops=RegistryOps.create(NbtOps.INSTANCE,level.registryAccess());
        var encoded=MountedItemStorage.CODEC.encodeStart(ops,mounted).getOrThrow();
        var copy=(AmmoDepotMountedStorage)MountedItemStorage.CODEC.parse(ops,encoded).getOrThrow();
        check(copy.count(0)==1500 && copy.count(1)==37 && copy.getStackInSlot(1).getHoverName().getString().equals("Component variant"),"Variant codec lost data");
        copy.extractItem(0,23,false);copy.unmount(level,tier.defaultBlockState(),p,depot);
        check(total(depot.getItemHandler())==1514,"Unmount count mismatch");
        check(depot.getContents().stream().anyMatch(c->c.count()==37 && c.sample().has(DataComponents.CUSTOM_NAME)),"Special component lost");
        var packed=depot.saveWithoutMetadata(level.registryAccess());depot.loadWithComponents(packed,level.registryAccess());check(total(depot.getItemHandler())==1514,"Depot NBT mismatch");
    }
    static void bulk(){
        var depot=lanes.getFirst().depot;long count=(long)Integer.MAX_VALUE+4000;
        var mounted=new AmmoDepotMountedStorage(216,List.of(new AmmoDepotMountedStorage.Stock(0,ammo(1),count),new AmmoDepotMountedStorage.Stock(1,special(1),7)));
        check(!mounted.insertItem(0,ammo(1),true).isEmpty(),"Overcapacity accepts insertion");
        var ops=RegistryOps.create(NbtOps.INSTANCE,level.registryAccess());
        var copy=(AmmoDepotMountedStorage)MountedItemStorage.CODEC.parse(ops,MountedItemStorage.CODEC.encodeStart(ops,mounted).getOrThrow()).getOrThrow();
        copy.extractItem(0,9,false);check(copy.count(0)==count-9,"Long count clamped");
        var original=depot.saveWithoutMetadata(level.registryAccess());copy.unmount(level,depot.getBlockState(),depot.getBlockPos(),depot);
        check(depot.getContents().stream().anyMatch(c->c.count()==count-9),"Native depot long count clamped");depot.loadWithComponents(original,level.registryAccess());
    }
    static void scene(Block tier,int index,boolean moving)throws Exception{
        var lane=new Lane();lane.name=(moving?"moving":"static")+index;
        var p=new BlockPos(4500+index*100,220,moving?4700:4500);force(p);force(p.south(16));
        var depotPos=p.east().below();blocks.add(depotPos);blocks.add(p.below());blocks.add(p);
        level.setBlock(depotPos,tier.defaultBlockState(),3);lane.depot=(AmmoStorageBlockEntity)level.getBlockEntity(depotPos);
        lane.depot.insertAmmo(ammo(2000),false);lane.depot.insertAmmo(special(37),false);
        level.setBlock(p.below(),Blocks.SLIME_BLOCK.defaultBlockState(),3);
        // Static depots are one block directly adjacent, not diagonally adjacent.
        if(!moving){blocks.add(p.east());level.setBlock(p.east(),tier.defaultBlockState(),3);lane.depot=(AmmoStorageBlockEntity)level.getBlockEntity(p.east());lane.depot.insertAmmo(ammo(2000),false);lane.depot.insertAmmo(special(37),false);}
        level.setBlock(p,SentryRegistry.SENTRY_ARM_BLOCK.get().defaultBlockState(),3);
        var placed=(SentryArmBlockEntity)level.getBlockEntity(p);placed.initialize();
        placed.setHeldItem(GunItemBuilder.create().setId(ResourceLocation.parse("tacz:ak47")).setFireMode(FireMode.AUTO).setAmmoCount(30).setAmmoInBarrel(true).build());if(moving)placed.setSpeed(256);
        var box=new ItemStack(com.tacz.guns.init.ModItems.AMMO_BOX.get());placed.addAmmoBox(box);
        if(moving){
            // A second real actor shares the same mounted depot.
            var p2=p.west();blocks.add(p2);blocks.add(p2.below());level.setBlock(p2.below(),Blocks.SLIME_BLOCK.defaultBlockState(),3);
            level.setBlock(p2,SentryRegistry.SENTRY_ARM_BLOCK.get().defaultBlockState(),3);
            var second=(SentryArmBlockEntity)level.getBlockEntity(p2);second.setHeldItem(placed.getHeldItem().copy());second.setSpeed(256);second.addAmmoBox(box.copy());
            var contraption=new BearingContraption(false,Direction.UP);check(contraption.assemble(level,p.below(2)),"Assembly failed");contraption.removeBlocksFromWorld(level,BlockPos.ZERO);
            lane.entity=new MovingSentrySmoke.TestEntity(level,contraption);lane.entity.setPos(p.getX(),p.getY()-1,p.getZ());level.addFreshEntity(lane.entity);
            for(var actor:contraption.getActors())if(actor.getValue().state.getBlock() instanceof SentryArmBlock)lane.arms.add(SentryMovementBehaviour.getOrCreate(actor.getValue()));
            check(lane.arms.size()==2 && total(inventory(lane))==2037,"Shared mounted depot missing");
        }else{
            lane.arms.add(placed);
            // Real motor + cog drives the placed arm throughout the test.
            var motorPos=p.west().below();blocks.add(motorPos);blocks.add(p.west());
            level.setBlock(motorPos,com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalBlock.FACING,Direction.UP),3);
            level.setBlock(p.west(),com.simibubi.create.AllBlocks.COGWHEEL.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,Direction.Axis.Y),3);
            var motor=(com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)level.getBlockEntity(motorPos);motor.initialize();motor.generatedSpeed.setValue(256);
        }
        lane.initial=all(lane);lane.targetPos=p.south(12).getCenter().add(0,1,0);
        lane.target=new Husk(EntityType.HUSK,level);lane.target.setPos(lane.targetPos);lane.target.setNoAi(true);lane.target.setNoGravity(true);lane.target.setPersistenceRequired();
        lane.target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1024);lane.target.setHealth(1024);level.addFreshEntity(lane.target);lanes.add(lane);
    }
    static void shipScene()throws Exception{
        scene(ModBlocks.AMMO_STORAGE_DIAMOND.get(),3,false);var lane=lanes.getLast();lane.name="sable";
        var p=lane.arms.getFirst().getBlockPos();
        var positions=List.of(p,p.below(),p.east(),p.east().below(),p.west(),p.west().below());
        var helper=Class.forName("dev.ryanhcode.sable.api.SubLevelAssemblyHelper");
        lane.ship=(dev.ryanhcode.sable.companion.SubLevelAccess)helper.getMethod("assembleBlocks",ServerLevel.class,BlockPos.class,Iterable.class,dev.ryanhcode.sable.companion.math.BoundingBox3ic.class)
                .invoke(null,level,p,positions,dev.ryanhcode.sable.companion.math.BoundingBox3i.from(positions));
        var local=BlockPos.containing(lane.ship.logicalPose().transformPositionInverse(p.getCenter()));
        lane.arms.clear();lane.arms.add((SentryArmBlockEntity)level.getBlockEntity(local));lane.depot=(AmmoStorageBlockEntity)level.getBlockEntity(local.east());
        check(lane.depot!=null && lane.arms.getFirst()!=null && all(lane)==lane.initial,"Ship moved inventory incorrectly");
        var motor=(com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)level.getBlockEntity(local.west().below());motor.initialize();motor.generatedSpeed.setValue(256);
        Object physics=Class.forName("dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem").getMethod("require",net.minecraft.world.level.Level.class).invoke(null,level);
        physics.getClass().getMethod("setPaused",boolean.class).invoke(physics,true);
    }
    static void removeShip(dev.ryanhcode.sable.companion.SubLevelAccess ship)throws Exception{
        try{
            var lookup=java.lang.invoke.MethodHandles.publicLookup();var base=Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
            var container=lookup.findStatic(base,"getContainer",java.lang.invoke.MethodType.methodType(Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer"),ServerLevel.class)).invoke(level);
            var reason=Class.forName("dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason");
            lookup.findVirtual(base,"removeSubLevel",java.lang.invoke.MethodType.methodType(void.class,Class.forName("dev.ryanhcode.sable.sublevel.SubLevel"),reason)).invoke(container,ship,reason.getField("REMOVED").get(null));
            lookup.findVirtual(base,"processSubLevelRemovals",java.lang.invoke.MethodType.methodType(void.class)).invoke(container);
        }catch(Throwable t){throw new Exception(t);}
    }
    @SubscribeEvent public static void pre(ServerTickEvent.Pre event){if(!running)return;for(var lane:lanes){lane.target.setPos(lane.targetPos);lane.target.setDeltaMovement(Vec3.ZERO);lane.target.setHealth(lane.target.getMaxHealth());if(lane.entity!=null)lane.entity.setAngle((float)(Math.sin(ticks*.01)*10));
        if(lane.ship!=null){((dev.ryanhcode.sable.companion.math.Pose3d)lane.ship.logicalPose()).orientation().identity().rotateXYZ(.17,.5+Math.sin(ticks*.01)*.1,-.23);try{lane.ship.getClass().getMethod("forceUpdateGlobalBounds").invoke(lane.ship);}catch(Exception e){throw new RuntimeException(e);}}
    }}
    @SubscribeEvent public static void bullet(EntityJoinLevelEvent event){if(!running || !(event.getEntity() instanceof EntityKineticBullet b) || !(b.getOwner() instanceof FakePlayer fp))return;for(var lane:lanes){var arm=SentryFakePlayer.getArmFromPlayer(fp);if(lane.arms.contains(arm)){lane.shots++;lane.error=Math.max(lane.error,b.position().distanceTo(arm.getActualMuzzlePos()));}}}
    @SubscribeEvent public static void hit(LivingIncomingDamageEvent event){if(!running || !(event.getSource().getEntity() instanceof FakePlayer))return;for(var lane:lanes)if(event.getEntity()==lane.target)lane.hits++;}
    @SubscribeEvent public static void post(ServerTickEvent.Post event){
        if(!running || ++ticks<400)return;
        for(var lane:lanes){
            SentryMechanicalArm.LOGGER.info("DEPOT METRICS {} initial={} now={} shots={} hits={} stock={} error={} rpm={} status={} result={}",lane.name,lane.initial,all(lane),lane.shots,lane.hits,total(inventory(lane)),lane.error,lane.arms.getFirst().getSpeed(),lane.arms.getFirst().getSentryStatus(),lane.arms.getFirst().getLastShootResult());
            test(lane.name+" sustained fire conserves total ammunition",()->check(lane.shots>60 && lane.initial-all(lane)==lane.shots,"Wrong total ammunition"));
            test(lane.name+" real hits and muzzle",()->check(lane.hits>0 && lane.error<1e-5,"No hits or muzzle mismatch"));
            test(lane.name+" component variants remain untouched",()->{
                long count=0;var inv=inventory(lane);for(int s=0;s<inv.getSlots();s++)if(inv.getStackInSlot(s).has(DataComponents.CUSTOM_NAME))count+=inv.getStackInSlot(s).getCount();check(count==37,"Special variant consumed");
            });
            if(lane.entity!=null){
                long remaining=total(inventory(lane));
                test(lane.name+" mounted inventory survives structure NBT",()->{
                    var copy=new BearingContraption();copy.readNBT(level,lane.entity.getContraption().writeNBT(level.registryAccess(),false),false);
                    check(total(copy.getStorage().getAllItems())==remaining,"Mounted storage codec mismatch");
                });
                test(lane.name+" disassembly restores current depot contents",()->{
                    lane.entity.setAngle(0);lane.entity.disassemble();var pos=BlockPos.containing(lane.entity.position()).east();
                    var depot=(AmmoStorageBlockEntity)level.getBlockEntity(pos);check(depot!=null && total(depot.getItemHandler())==remaining,"Disassembly restored stale stock");
                });
                lane.entity.discard();
            }
            lane.target.discard();for(var arm:lane.arms)SentryFakePlayer.remove(arm);
            if(lane.ship!=null)test("Sable depot fixture cleanup",()->removeShip(lane.ship));
        }
        for(var p:blocks)level.removeBlock(p,false);
        running=false;for(long k:forced){var c=new net.minecraft.world.level.ChunkPos(k);level.setChunkForced(c.x,c.z,false);}
        SentryMechanicalArm.LOGGER.info("DEPOT RESULT passed={} failed={}",passed,failed);CasingIntegrationSmoke.finish();level.getServer().halt(false);
    }
}
