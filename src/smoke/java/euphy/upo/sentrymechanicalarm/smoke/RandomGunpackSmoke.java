package euphy.upo.sentrymechanicalarm.smoke;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.*;
import com.tacz.guns.api.item.*;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.EntityKineticBullet;
import euphy.upo.sentrymechanicalarm.*;
import euphy.upo.sentrymechanicalarm.content.*;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

@EventBusSubscriber(modid=SentryMechanicalArm.MODID)
public final class RandomGunpackSmoke {
    static final List<Lane> lanes=new ArrayList<>();static final Set<Long> chunks=new HashSet<>();
    static final List<ItemEntity> items=new ArrayList<>();static final List<BlockPos> blocks=new ArrayList<>();
    static final Map<ResourceLocation,Integer> expected=new HashMap<>(),dropped=new HashMap<>();
    static ServerLevel level;static boolean running;static int ticks,passed,failed;
    static class Arm extends SentryArmBlockEntity{
        float power=256;Arm(BlockPos p){super(p,SentryRegistry.SENTRY_ARM_BLOCK.get().defaultBlockState());}
        @Override public boolean isShooterActive(){return !isRemoved();}
        @Override public float getSpeed(){return power;}
    }
    static class Lane{final List<Integer> shotTicks=new ArrayList<>();ResourceLocation id,ammo;FireMode mode;Arm arm;Husk target;Vec3 targetPos;int bullets,shots,hits,initial,stopShots;double originError;}
    interface Check{void run()throws Exception;}
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    static void test(String name,Check c){try{c.run();passed++;SentryMechanicalArm.LOGGER.info("RANDOMPACK PASS {}",name);}catch(Throwable e){failed++;SentryMechanicalArm.LOGGER.error("RANDOMPACK FAIL "+name,e);}}
    static boolean targetPack(ResourceLocation id){return id.getNamespace().equals(System.getProperty("sentry.randomNamespace"));}
    static void force(BlockPos p){var c=new net.minecraft.world.level.ChunkPos(p);if(chunks.add(c.toLong()))level.setChunkForced(c.x,c.z,true);level.getChunkAt(p);}
    static int total(Lane l){var g=l.arm.getHeldItem();var api=IGun.getIGunOrNull(g);int n=api.getCurrentAmmoCount(g)+(api.hasBulletInBarrel(g)?1:0);for(var b:l.arm.attachedAmmoBoxes)if(b.getItem() instanceof IAmmoBox box)n+=box.getAmmoCount(b);return n;}
    @SubscribeEvent public static void start(ServerStartedEvent e){
        if(!"randompacks".equals(System.getProperty("sentry.smoke")))return;
        running=true;level=e.getServer().overworld();SMAServerConfig.CASING_ENTITY_LIMIT.set(1024);SMAServerConfig.CASING_CHANCE.set(1.0);
        var entries=new ArrayList<>(TimelessAPI.getAllCommonGunIndex().stream().filter(x->targetPack(x.getKey())).sorted(Map.Entry.comparingByKey()).toList());
        test("gun indexes loaded",()->check(!entries.isEmpty(),"no sample guns loaded"));
        SentryMechanicalArm.LOGGER.info("RANDOMPACK CATALOG namespace={} guns={} ammo={}",System.getProperty("sentry.randomNamespace"),entries.size(),TimelessAPI.getAllCommonAmmoIndex().stream().filter(x->targetPack(x.getKey())).count());
        // Seeded gun sample, with one representative for every gun type before filling to 12.
        Collections.shuffle(entries,new Random(512317632L));
        var selected=new LinkedHashSet<ResourceLocation>();var types=new HashSet<String>();
        for(var entry:entries)if(types.add(entry.getValue().getType().toString()))selected.add(entry.getKey());
        for(var entry:entries){if(selected.size()>=12)break;selected.add(entry.getKey());}
        for(var id:selected)for(var mode:TimelessAPI.getCommonGunIndex(id).orElseThrow().getGunData().getFireModeSet())test("assemble "+id+" "+mode,()->lane(id,mode));
    }
    static void lane(ResourceLocation id,FireMode mode){
        var index=TimelessAPI.getCommonGunIndex(id).orElseThrow();var data=index.getGunData();
        Lane l=new Lane();l.id=id;l.ammo=data.getAmmoId();l.mode=mode;
        BlockPos p=new BlockPos(8000+lanes.size()*80,235,8000);for(int z=-16;z<=96;z+=16)force(p.south(z));
        l.arm=new Arm(p);l.arm.setLevel(level);l.arm.initialize();
        var builder=GunItemBuilder.create().setId(id).setFireMode(mode).setAmmoCount(data.getAmmoAmount()).setAmmoInBarrel(true);
        var g=builder.build(level.registryAccess());check(!g.isEmpty(),"missing gun");
        l.arm.setHeldItem(g);l.arm.rangeScroll.setValue(80);
        {var box=new ItemStack(com.tacz.guns.init.ModItems.AMMO_BOX.get());var api=(IAmmoBox)box.getItem();api.setAmmoId(box,l.ammo);api.setAmmoCount(box,200);l.arm.addAmmoBox(box);}
        l.initial=total(l);
        l.target=new Husk(EntityType.HUSK,level);l.targetPos=p.getCenter().add(0,-.5,Math.min(24,l.arm.rangeScroll.getValue()*.6));l.target.setPos(l.targetPos);l.target.setNoAi(true);l.target.setNoGravity(true);l.target.setPersistenceRequired();
        l.target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100000);l.target.setHealth(100000);l.target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        String name="PackTarget_"+lanes.size();l.target.setCustomName(net.minecraft.network.chat.Component.literal(name));level.addFreshEntity(l.target);
        var fcPos=p.east(2);blocks.add(fcPos);level.setBlock(fcPos,SentryRegistry.BLAZE_FIRE_CONTROL.get().defaultBlockState(),3);var fc=(BlazeFireControlBlockEntity)level.getBlockEntity(fcPos);
        var clipboard=new ItemStack(SentryRegistry.FIRE_CONTROL_CLIPBOARD.get());var tag=new net.minecraft.nbt.CompoundTag();var names=new net.minecraft.nbt.ListTag();names.add(net.minecraft.nbt.StringTag.valueOf(name));tag.put("TargetList",names);tag.putBoolean("WhitelistMode",false);ItemNBTHelper.setTag(clipboard,tag);fc.inventory.setStackInSlot(0,clipboard);l.arm.setConnectedFireControl(fcPos);lanes.add(l);
    }
    static Lane lane(LivingEntity shooter){if(!(shooter instanceof FakePlayer fp))return null;var arm=SentryFakePlayer.getArmFromPlayer(fp);return lanes.stream().filter(x->x.arm==arm).findFirst().orElse(null);}
    @SubscribeEvent public static void post(GunFirePostEvent e){if(!running||e.getShooter().level().isClientSide)return;var l=lane(e.getShooter());if(l==null)return;l.shots++;l.shotTicks.add(ticks);if(SentryCasingProfiles.enabled(l.ammo))expected.merge(l.ammo,1,Integer::sum);}
    @SubscribeEvent public static void entity(EntityJoinLevelEvent e){
        if(!running||e.getLevel().isClientSide)return;
        if(e.getEntity() instanceof EntityKineticBullet bullet&&bullet.getOwner() instanceof LivingEntity owner){var l=lane(owner);if(l!=null){l.bullets++;l.originError=Math.max(l.originError,bullet.position().distanceTo(l.arm.getGunPose().position()));}}
        if(!e.loadedFromDisk()&&e.getEntity() instanceof ItemEntity item&&item.getItem().is(SentryRegistry.EMPTY_CASING.get())){items.add(item);dropped.merge(EmptyCasingItem.ammoId(item.getItem()),item.getItem().getCount(),Integer::sum);}
    }
    @SubscribeEvent public static void damage(LivingIncomingDamageEvent e){if(running)for(var l:lanes)if(e.getEntity()==l.target){if(e.getSource().getEntity() instanceof FakePlayer)l.hits++;e.setCanceled(true);}}
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){if(!running)return;
        try{
            for(var l:lanes){l.target.setHealth(l.target.getMaxHealth());l.target.setPos(l.targetPos);l.target.setDeltaMovement(Vec3.ZERO);l.arm.tick();}
            ticks++;
            if(ticks==500){for(var l:lanes){l.stopShots=l.shots;l.arm.power=0;}summarize();}
            if(ticks==515){test("power loss stops all delayed native shots",()->{for(var l:lanes)check(l.shots==l.stopShots,"post-power shot "+l.id);});finish();}
        }catch(Throwable error){failed++;SentryMechanicalArm.LOGGER.error("RANDOMPACK FAIL tick",error);finish();}
    }
    static void summarize(){
        for(var l:lanes){
            SentryMechanicalArm.LOGGER.info("RANDOMPACK METRICS {} mode={} shots={} bullets={} hits={} initial={} remaining={} origin={} status={}",l.id,l.mode,l.shots,l.bullets,l.hits,l.initial,total(l),l.originError,l.arm.getSentryStatus());
            SentryMechanicalArm.LOGGER.info("RANDOMPACK AIM {} profile={} pose={} angles={}/{}/{}/{} aligned={}",l.id,SentryGunProfiles.get(l.id),l.arm.getGunPose(),l.arm.baseAngle.getValue(),l.arm.lowerArmAngle.getValue(),l.arm.upperArmAngle.getValue(),l.arm.headAngle.getValue(),l.arm.getGunPose().aligned(l.target.getEyePosition(),1));
            test(l.id+" "+l.mode+" native script fires",()->check(l.shots>0&&l.bullets>0,"no shot"));
            test(l.id+" "+l.mode+" authoritative origin",()->check(l.originError<1e-5,"origin drift"));
            test(l.id+" "+l.mode+" finite ammo conservation",()->check(l.initial-total(l)==l.shots,"consumed="+(l.initial-total(l))+" shots="+l.shots));
            test(l.id+" script remains usable",()->check(!IGunOperator.fromLivingEntity(SentryFakePlayer.get(l.arm)).getDataHolder().gunScriptFailed,"script disabled"));
        }
        test("case totals equal successful finite shots",()->check(expected.equals(dropped),"expected="+expected+" actual="+dropped));
    }
    static void finish(){running=false;for(var l:lanes){l.target.discard();SentryFakePlayer.remove(l.arm);}items.forEach(ItemEntity::discard);for(var p:blocks){if(level.getBlockEntity(p) instanceof BlazeFireControlBlockEntity fc)fc.inventory.setStackInSlot(0,ItemStack.EMPTY);level.removeBlock(p,false);}for(long k:chunks){var c=new net.minecraft.world.level.ChunkPos(k);level.setChunkForced(c.x,c.z,false);}SentryMechanicalArm.LOGGER.info("RANDOMPACK RESULT passed={} failed={} lanes={} casingDrops={}",passed,failed,lanes.size(),dropped.values().stream().mapToInt(Integer::intValue).sum());level.getServer().halt(false);}
}
