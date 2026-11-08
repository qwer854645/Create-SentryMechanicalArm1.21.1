package euphy.upo.sentrymechanicalarm.smoke;

import com.google.gson.*;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.EntityKineticBullet;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlock;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import euphy.upo.sentrymechanicalarm.network.SentryGunProfilesPacket;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import java.util.*;

@EventBusSubscriber(modid=SentryMechanicalArm.MODID)
public final class GunGeometrySmoke {
    private static final List<Scenario> cases=new ArrayList<>();
    private static final Set<Long> forced=new HashSet<>();
    private static final Map<BlockPos,net.minecraft.world.level.block.state.BlockState> controls=new LinkedHashMap<>();
    private static ServerLevel level;
    private static int ticks, passed, failed;
    private static boolean running;
    static final class Arm extends SentryArmBlockEntity {
        @Override public boolean isShooterActive(){return !isRemoved();} // Isolated test controller, not a placed block.
        float suppliedSpeed=256;
        Arm(BlockPos p,boolean ceiling) { super(p,SentryRegistry.SENTRY_ARM_BLOCK.get().defaultBlockState().setValue(SentryArmBlock.CEILING,ceiling)); }
        @Override public float getSpeed() { return suppliedSpeed; }
    }
    static final class Scenario {
        String name; Arm arm; Husk target; Vec3 targetPos; int shots, bullets, hits, initialAmmo;
        int firstCharge=-1,firstShot=-1,stopBullets;
        boolean awaitingProjectile;
        double maxOriginError, maxDirectionError;
    }
    interface Check { void run() throws Exception; }
    static void check(boolean success,String message) { if (!success) throw new AssertionError(message); }
    static void test(String name,Check check) {
        try { check.run(); passed++; SentryMechanicalArm.LOGGER.info("PHASE3 PASS {}",name); }
        catch (Throwable e) { failed++; SentryMechanicalArm.LOGGER.error("PHASE3 FAIL "+name,e); }
    }
    @SubscribeEvent
    public static void start(ServerStartedEvent event) {
        if (!"phase3".equals(System.getProperty("sentry.smoke"))) return;
        running=true; level=event.getServer().overworld(); ticks=passed=failed=0;
        test("default profiles derive from model assets",()->check(SentryGunProfiles.snapshot().values().stream().filter(SentryGunProfiles.Profile::fromModel).count()>=60,"Model resources not found"));
        test("AK47 classification uses ammo display",()->check(!SentryGunProfiles.get(ResourceLocation.parse("tacz:ak47")).visibleProjectile(),"AK47 misclassified"));
        test("RPG native projectile suppresses extra trail",()->check(SentryGunProfiles.get(ResourceLocation.parse("tacz:rpg7")).visibleProjectile(),"RPG misclassified"));
        test("native tracer suppresses synthetic line",()-> {
            var g=gun("glock_17",FireMode.SEMI); check(!SentryGunProfiles.needsSyntheticTracer(g),"Duplicate native tracer");
        });
        test("missing gun has bounded deterministic fallback",()-> {
            var p=SentryGunProfiles.get(ResourceLocation.parse("example:unavailable"));
            check(!p.fromModel() && SentryGunGeometry.finite(p.muzzle()) && p.muzzle().length()<8,"Unsafe fallback");
        });
        test("data-pack muzzle override is applied",()->check(SentryGunProfiles.get(ResourceLocation.parse("sentrymechanicalarm:phase3_hold"))
                .muzzle().distanceTo(new Vec3(.1,.2,-.9))<1e-10,"Override ignored"));
        test("non-finite profile is rejected",()-> {
            boolean rejected=false;
            try { new SentryGunProfiles.Profile(new Vec3(Double.NaN,0,0),new Vec3(0,0,-1),false,false,false); }
            catch(IllegalArgumentException expected) { rejected=true; }
            check(rejected,"Non-finite vector accepted");
        });
        test("Bedrock rest bone conversion matches known geometry",()-> {
            JsonObject model=JsonParser.parseString("{\"minecraft:geometry\":[{\"bones\":[{\"name\":\"root\",\"pivot\":[0,0,0]},{\"name\":\"thirdperson_hand\",\"parent\":\"root\",\"pivot\":[0,0,0]},{\"name\":\"muzzle_flash\",\"parent\":\"root\",\"pivot\":[0,0,-16]}]}]}").getAsJsonObject();
            var p=SentryGunProfiles.parseModel(new JsonObject(),model,false,false);
            check(p.muzzle().distanceTo(new Vec3(0,0,-1))<1e-8,"Incorrect bone transforms: "+p.muzzle());
        });
        test("ceiling mirrors rest pose correctly",()-> {
            var p=SentryGunProfiles.get(ResourceLocation.parse("tacz:glock_17"));
            var floor=SentryGunGeometry.pose(BlockPos.ZERO,false,180,135,90,0,p);
            var ceiling=SentryGunGeometry.pose(BlockPos.ZERO,true,180,135,90,0,p);
            check(Math.abs(floor.position().y+ceiling.position().y-1)<1e-8
                    && Math.abs(floor.forward().z+ceiling.forward().z)<1e-8,"Missing ceiling inversion");
        });
        test("pitch alignment rejects vertical error",()-> {
            var p=new SentryGunGeometry.Pose(Vec3.ZERO,new Vec3(0,0,1));
            check(!p.aligned(new Vec3(0,10,0),1) && p.aligned(new Vec3(0,0,10),1),"Yaw-only firing gate");
        });
        test("world coordinates preserve muzzle precision",()-> {
            var profile=SentryGunProfiles.get(ResourceLocation.parse("tacz:glock_17"));
            var zero=SentryGunGeometry.pose(BlockPos.ZERO,false,180,135,90,15,profile);
            var far=SentryGunGeometry.pose(new BlockPos(29000000,240,-29000000),false,180,135,90,15,profile);
            check(far.position().subtract(new Vec3(29000000,240,-29000000)).distanceTo(zero.position())<1e-7,"Float world truncation");
        });
        test("canonical profile packet round-trip",()-> {
            var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                var packet=new SentryGunProfilesPacket(SentryGunProfiles.snapshot()); SentryGunProfilesPacket.CODEC.encode(buf,packet);
                var decoded=SentryGunProfilesPacket.CODEC.decode(buf);
                check(decoded.profiles().size()==packet.profiles().size(),"Profile count lost");
                for (var e:packet.profiles().entrySet()) {
                    var other=decoded.profiles().get(e.getKey()); check(other.muzzle().distanceTo(e.getValue().muzzle())<1e-10
                            && other.forward().distanceTo(e.getValue().forward())<1e-10,"Profile corrupted");
                }
            } finally { buf.release(); }
        });
        try {
            scenario("pistol","glock_17",FireMode.SEMI,false,0);
            scenario("automatic","ak47",FireMode.AUTO,false,0);
            scenario("burst","m16a4",FireMode.BURST,false,0);
            scenario("bolt action","ai_awp",FireMode.SEMI,false,0);
            scenario("shotgun","m870",FireMode.SEMI,false,0);
            scenario("launcher","rpg7",FireMode.SEMI,false,0);
            scenario("charge","rhino357",FireMode.SEMI,false,0);
            scenario("minigun","minigun",FireMode.AUTO,false,0);
            scenario("elevated target","glock_17",FireMode.SEMI,false,8);
            scenario("ceiling mount","glock_17",FireMode.SEMI,true,-4);
            scenario("long range 112 blocks","ai_awp",FireMode.SEMI,false,0,112);
            scenario("hold charge","sentrymechanicalarm:phase3_hold",FireMode.SEMI,false,0);
            scenario("delay charge","sentrymechanicalarm:phase3_delay",FireMode.SEMI,false,0);
        } catch(Throwable e) { failed++; SentryMechanicalArm.LOGGER.error("PHASE3 setup failure",e); finish(); }
    }
    private static ItemStack gun(String name,FireMode mode) {
        var id=gunId(name);
        return GunItemBuilder.create().setId(id).setFireMode(mode).setAmmoCount(100).setAmmoInBarrel(true).build();
    }
    private static ResourceLocation gunId(String name) { return ResourceLocation.parse(name.contains(":") ? name : "tacz:"+name); }
    private static void force(BlockPos pos) {
        var chunk=new net.minecraft.world.level.ChunkPos(pos);
        if (!level.getForcedChunks().contains(chunk.toLong())) { level.setChunkForced(chunk.x,chunk.z,true); forced.add(chunk.toLong()); }
    }
    private static void scenario(String label,String gunName,FireMode mode,boolean ceiling,int dy) {
        scenario(label,gunName,mode,ceiling,dy,14);
    }
    private static void scenario(String label,String gunName,FireMode mode,boolean ceiling,int dy,int distance) {
        Scenario s=new Scenario(); s.name=label;
        BlockPos pos=new BlockPos(512+cases.size()*64,240,512);
        for (int z=0;z<=distance+16;z+=16) force(pos.south(z)); level.getChunkAt(pos);
        s.arm=new Arm(pos,ceiling); s.arm.setLevel(level); s.arm.initialize();
        var g=gun(gunName,mode); if(g.isEmpty()) throw new IllegalStateException("Gun missing: "+gunName);
        var gunApi=IGun.getIGunOrNull(g);
        if (gunApi.useInventoryAmmo(g)) { gunApi.setCurrentAmmoCount(g,0); gunApi.setBulletInBarrel(g,false); }
        s.arm.setHeldItem(g);
        if (gunApi.useInventoryAmmo(g)) {
            var b=new ItemStack(com.tacz.guns.init.ModItems.AMMO_BOX.get());
            var box=(com.tacz.guns.api.item.IAmmoBox)b.getItem();
            box.setAmmoId(b,TimelessAPI.getCommonGunIndex(gunId(gunName)).orElseThrow().getGunData().getAmmoId());
            box.setAmmoCount(b,100); s.arm.addAmmoBox(b);
        }
        s.arm.rangeScroll.setValue(Math.max(32,distance+8)); s.initialAmmo=ammo(s.arm);
        s.target=new Husk(EntityType.HUSK,level); s.targetPos=pos.south(distance).getCenter().add(0,dy-.5,0);
        s.target.setPos(s.targetPos); s.target.setNoAi(true); s.target.setNoGravity(true); s.target.setPersistenceRequired();
        s.target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(10000);
        s.target.setHealth(10000); s.target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        String targetName="Phase3Target_"+cases.size(); s.target.setCustomName(net.minecraft.network.chat.Component.literal(targetName));
        level.addFreshEntity(s.target);
        // Isolate the long-range lane from neighboring test targets using the real fire-control list.
        BlockPos fcPos=pos.east(2); controls.put(fcPos,level.getBlockState(fcPos));
        level.setBlock(fcPos,SentryRegistry.BLAZE_FIRE_CONTROL.get().defaultBlockState(),3);
        var fc=(euphy.upo.sentrymechanicalarm.content.BlazeFireControlBlockEntity)level.getBlockEntity(fcPos);
        var clip=new ItemStack(SentryRegistry.FIRE_CONTROL_CLIPBOARD.get()); var tag=new net.minecraft.nbt.CompoundTag();
        var list=new net.minecraft.nbt.ListTag(); list.add(net.minecraft.nbt.StringTag.valueOf(targetName));
        tag.put("TargetList",list); tag.putBoolean("WhitelistMode",false); ItemNBTHelper.setTag(clip,tag);
        fc.inventory.setStackInSlot(0,clip); s.arm.setConnectedFireControl(fcPos);
        cases.add(s);
    }
    private static int ammo(Arm arm) {
        var stack=arm.getHeldItem(); var gun=IGun.getIGunOrNull(stack);
        int count=gun.getCurrentAmmoCount(stack)+(gun.hasBulletInBarrel(stack)?1:0);
        for (var b:arm.attachedAmmoBoxes) if(b.getItem() instanceof com.tacz.guns.api.item.IAmmoBox box) count+=box.getAmmoCount(b);
        return count;
    }
    @SubscribeEvent public static void fire(com.tacz.guns.api.event.common.GunFireEvent event) {
        if(!running || !(event.getShooter() instanceof FakePlayer fp)) return;
        var arm=SentryFakePlayer.getArmFromPlayer(fp);
        for(var s:cases) if(s.arm==arm) s.awaitingProjectile=true;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void spawn(EntityJoinLevelEvent event) {
        if(!running || !(event.getEntity() instanceof EntityKineticBullet b) || !(b.getOwner() instanceof FakePlayer fp)) return;
        var arm=SentryFakePlayer.getArmFromPlayer(fp);
        for(var s:cases) if(s.arm==arm) {
            if(s.awaitingProjectile) { s.shots++; s.awaitingProjectile=false; }
            s.bullets++; if (s.firstShot<0) s.firstShot=ticks;
            var pose=s.arm.getGunPose();
            s.maxOriginError=Math.max(s.maxOriginError,b.position().distanceTo(pose.position()));
            double dot=net.minecraft.util.Mth.clamp(b.getDeltaMovement().normalize().dot(pose.forward()),-1,1);
            s.maxDirectionError=Math.max(s.maxDirectionError,Math.toDegrees(Math.acos(dot)));
        }
    }
    @SubscribeEvent public static void hit(LivingIncomingDamageEvent event) {
        if(!running) return;
        for(var s:cases) if(event.getEntity()==s.target && event.getSource().getEntity() instanceof FakePlayer) s.hits++;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(!running) return;
        try {
            for(var s:cases) {
                s.target.setPos(s.targetPos); s.target.setDeltaMovement(Vec3.ZERO); s.arm.tick();
                if (s.firstCharge<0 && s.arm.getSentryStatus()==SentryArmBlockEntity.SentryStatus.CHARGING) s.firstCharge=ticks;
            }
            ticks++;
            if(ticks==250) {
                test("power loss cancels pending burst projectiles",()-> {
                    for(var s:cases) check(s.bullets==s.stopBullets,"Delayed projectile after power loss: "+s.name);
                });
                finish(); return;
            }
            if(ticks!=240) return;
            for(var s:cases) {
                SentryMechanicalArm.LOGGER.info("PHASE3 METRICS {} shots={} bullets={} hits={} originError={} directionError={} ammo={} status={} angles={},{},{}",
                        s.name,s.shots,s.bullets,s.hits,s.maxOriginError,s.maxDirectionError,ammo(s.arm),s.arm.getSentryStatus(),
                        s.arm.baseAngle.getValue(),s.arm.headAngle.getValue(),s.arm.upperArmAngle.getValue());
                var data=com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(SentryFakePlayer.get(s.arm)).getDataHolder();
                SentryMechanicalArm.LOGGER.info("PHASE3 STATE {} result={} draw={} now={} reload={} scriptFailed={}",s.name,
                        s.arm.getLastShootResult(),data.drawTimestamp,com.tacz.guns.util.time.GunTime.nowMillis(),data.reloadStateType,data.gunScriptFailed);
                test(s.name+" fires and conserves ammunition",()->check(s.shots>0 && s.initialAmmo-ammo(s.arm)==s.shots,"No fire or wrong consumption"));
                test(s.name+" projectile starts at canonical muzzle",()->check(s.bullets>0 && s.maxOriginError<1e-5,"Spawn error="+s.maxOriginError));
                test(s.name+" projectile direction follows barrel",()->check(s.bullets>0 && s.maxDirectionError<15,"Direction error="+s.maxDirectionError));
                test(s.name+" damages target",()->check(s.hits>0 && s.target.getHealth()<10000,"No actual target hit"));
                var stack=s.arm.getHeldItem(); var api=IGun.getIGunOrNull(stack);
                var charge=TimelessAPI.getCommonGunIndex(api.getGunId(stack)).orElseThrow().getGunData().getChargeData(api.getFireMode(stack));
                if(charge!=null) test(s.name+" respects charge threshold",()-> {
                    float threshold=charge.getChargeType()==com.tacz.guns.resource.pojo.data.gun.ChargeType.HOLD
                            ? Math.min(charge.getFireThreshold(),charge.getMaxCharge()) : charge.getMaxCharge();
                    int required=(int)Math.ceil(threshold/charge.getIncreasePerTick())-1;
                    check(s.firstCharge>=0 && s.firstShot-s.firstCharge>=required,"Fired before charge completed");
                });
                s.stopBullets=s.bullets; s.arm.suppliedSpeed=0;
            }
        } catch(Throwable e) { failed++; SentryMechanicalArm.LOGGER.error("PHASE3 tick failure",e); finish(); }
    }
    private static void finish() {
        running=false;
        for(var s:cases) { s.target.discard(); SentryFakePlayer.remove(s.arm); }
        controls.forEach((p,state)-> {
            if(level.getBlockEntity(p) instanceof euphy.upo.sentrymechanicalarm.content.BlazeFireControlBlockEntity fc)
                fc.inventory.setStackInSlot(0,ItemStack.EMPTY);
            level.setBlock(p,state,3);
        }); controls.clear();
        for(long key:forced) { var chunk=new net.minecraft.world.level.ChunkPos(key); level.setChunkForced(chunk.x,chunk.z,false); }
        forced.clear(); cases.clear();
        SentryMechanicalArm.LOGGER.info("PHASE3 RESULT passed={} failed={}",passed,failed);
        if(Boolean.getBoolean("sentry.casingSmoke")) CasingSmoke.finish();
        level.getServer().halt(false);
    }
}
