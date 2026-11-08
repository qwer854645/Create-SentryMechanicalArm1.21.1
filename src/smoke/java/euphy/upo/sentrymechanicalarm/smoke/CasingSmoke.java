package euphy.upo.sentrymechanicalarm.smoke;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.event.common.*;
import com.tacz.guns.api.item.gun.FireMode;
import euphy.upo.sentrymechanicalarm.*;
import euphy.upo.sentrymechanicalarm.content.EmptyCasingItem;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import java.util.*;

@EventBusSubscriber(modid=SentryMechanicalArm.MODID)
public final class CasingSmoke {
    static final ResourceLocation AMMO=ResourceLocation.parse("tacz:9mm");
    static final List<ItemEntity> owned=new ArrayList<>();
    static final Map<ResourceLocation,Integer> expected=new HashMap<>(),actual=new HashMap<>();
    static ServerLevel level;static ServerPlayer player;static int passed,failed,ticks,playerShots,spawned;static boolean observing;static int attempts;static long lastAttempt;
    interface Case{void run()throws Exception;}
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void test(String name,Case c){try{c.run();passed++;SentryMechanicalArm.LOGGER.info("CASING PASS {}",name);}catch(Throwable e){failed++;SentryMechanicalArm.LOGGER.error("CASING FAIL "+name,e);}}
    static int count(){return spawned;}
    static ItemStack gun(){return GunItemBuilder.create().setId(ResourceLocation.parse("tacz:glock_17")).setFireMode(FireMode.SEMI).setAmmoCount(40).setAmmoInBarrel(true).build();}
    static void pair(boolean consume,boolean post){var g=player.getMainHandItem();SentryCasingDrops.before(new GunFireEvent(player,g,LogicalSide.SERVER));if(consume){var api=IGun.getIGunOrNull(g);api.setCurrentAmmoCount(g,api.getCurrentAmmoCount(g)-1);}if(post)SentryCasingDrops.after(new GunFirePostEvent(player,g,LogicalSide.SERVER));}
    @SubscribeEvent(priority=EventPriority.HIGH)public static void start(ServerStartedEvent e){
        if(!Boolean.getBoolean("sentry.casingSmoke"))return;level=e.getServer().overworld();
        SMAServerConfig.CASING_ENTITY_LIMIT.set(1024);SMAServerConfig.CASING_CHANCE.set(1.0);
        player=new ServerPlayer(e.getServer(),level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"CasingPlayer"),net.minecraft.server.level.ClientInformation.createDefault());
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(e.getServer(),new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player,net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(),false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
        };
        player.setPos(505,240,505);level.setChunkForced(31,31,true);level.getChunkAt(player.blockPosition());player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,gun());
        test("discover real gunpack shell resources",()->check(SentryCasingProfiles.snapshot().size()==TimelessAPI.getAllCommonAmmoIndex().size()&&SentryCasingProfiles.enabled(AMMO),"ammo discovery incomplete"));
        test("rocket and unknown ammo rejected",()->check(!SentryCasingProfiles.enabled(ResourceLocation.parse("tacz:rpg_rocket"))&&!SentryCasingProfiles.enabled(ResourceLocation.parse("unknown:ammo")),"unsafe ammo enabled"));
        test("casing components preserve identity through disk and network",()->{
            var casing=EmptyCasingItem.create(AMMO,7);var restored=ItemStack.parse(level.registryAccess(),casing.save(level.registryAccess())).orElseThrow();check(ItemStack.matches(casing,restored),"NBT changed casing");
            var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());try{ItemStack.STREAM_CODEC.encode(buffer,casing);check(ItemStack.matches(casing,ItemStack.STREAM_CODEC.decode(buffer)),"network changed casing");}finally{buffer.release();}
            check(!ItemStack.isSameItemSameComponents(casing,EmptyCasingItem.create(ResourceLocation.parse("tacz:12g"),7)),"different calibers merge");
        });
        test("success notification without ammo debit produces nothing",()->{int n=count();pair(false,true);check(count()==n,"free casing");});
        test("finite consumed round produces exactly one casing",()->{int n=count();pair(true,true);check(count()==n+1,"missing casing");});
        test("duplicate post notification produces nothing",()->{int n=count();SentryCasingDrops.after(new GunFirePostEvent(player,player.getMainHandItem(),LogicalSide.SERVER));check(count()==n,"duplicate casing");});
        test("cancelled or unfinished shot produces nothing",()->{int n=count();pair(false,false);SentryCasingDrops.clear();SentryCasingDrops.after(new GunFirePostEvent(player,player.getMainHandItem(),LogicalSide.SERVER));check(count()==n,"unfinished shot dropped");});
        test("master and player switches suppress drops",()->{int n=count();try{SMAServerConfig.ENABLE_CASINGS.set(false);pair(true,true);SMAServerConfig.ENABLE_CASINGS.set(true);SMAServerConfig.PLAYER_CASINGS.set(false);pair(true,true);}finally{SMAServerConfig.ENABLE_CASINGS.set(true);SMAServerConfig.PLAYER_CASINGS.set(true);}check(count()==n,"disabled drop");});
        test("zero chance and entity cap suppress drops",()->{int n=count();try{SMAServerConfig.CASING_CHANCE.set(0.0);pair(true,true);SMAServerConfig.CASING_CHANCE.set(1.0);SMAServerConfig.CASING_ENTITY_LIMIT.set(0);pair(true,true);}finally{SMAServerConfig.CASING_CHANCE.set(1.0);SMAServerConfig.CASING_ENTITY_LIMIT.set(1024);}check(count()==n,"chance/cap ignored");});
        test("same-tick repeated shots respect entity limit",()->{int n=count();SentryCasingDrops.clear();try{SMAServerConfig.CASING_ENTITY_LIMIT.set(1);pair(true,true);pair(true,true);pair(true,true);}finally{SMAServerConfig.CASING_ENTITY_LIMIT.set(1024);SentryCasingDrops.clear();}check(count()==n+1,"same-tick cap bypass");});
        test("infinite ammo box cannot mint recoverable casings",()->{int n=count();var box=new ItemStack(com.tacz.guns.init.ModItems.AMMO_BOX.get());var api=(com.tacz.guns.api.item.IAmmoBox)box.getItem();api.setCreative(box,false);api.setAmmoId(box,AMMO);player.getInventory().setItem(9,box);try{pair(true,true);}finally{player.getInventory().setItem(9,ItemStack.EMPTY);}check(count()==n,"infinite box casing farm");});
        test("creative shooter produces no recoverable casing",()->{int n=count();try{player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);pair(true,true);}finally{player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);}check(count()==n,"creative casing farm");});
        test("casing drops independent of reloading switch",()->{int n=count();try{SMAServerConfig.ENABLE_RELOADING.set(false);SMAServerConfig.ENABLE_RELOADING.clearCache();pair(true,true);}finally{SMAServerConfig.ENABLE_RELOADING.set(true);SMAServerConfig.ENABLE_RELOADING.clearCache();}check(count()==n+1,"coupled switches");});
        level.getEntitiesOfClass(ItemEntity.class,new net.minecraft.world.phys.AABB(490,230,490,530,260,530),x->x.getItem().is(SentryRegistry.EMPTY_CASING.get())).forEach(ItemEntity::discard);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,gun());var op=IGunOperator.fromLivingEntity(player);op.initialData();op.draw(player::getMainHandItem);op.getDataHolder().drawTimestamp=com.tacz.guns.util.time.GunTime.nowMillis()-10000;
        observing=true;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){
        if(!observing)return;ticks++;long now=com.tacz.guns.util.time.GunTime.nowMillis();
        // Server catch-up ticks can run faster than TaCZ's real-time firing cooldown.
        if(ticks>=20&&attempts<3&&now-lastAttempt>=1000){lastAttempt=now;attempts++;var outcome=IGunOperator.fromLivingEntity(player).shoot(()->0f,()->0f);SentryMechanicalArm.LOGGER.info("CASING_PLAYER_ATTEMPT tick={} result={}",ticks,outcome);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)public static void post(GunFirePostEvent e){if(!observing||e.getLogicalSide()!=LogicalSide.SERVER)return;var api=IGun.getIGunOrNull(e.getGunItemStack());if(api==null)return;var id=TimelessAPI.getCommonGunIndex(api.getGunId(e.getGunItemStack())).orElseThrow().getGunData().getAmmoId();if(SentryCasingProfiles.enabled(id))expected.merge(id,1,Integer::sum);if(e.getShooter()==player)playerShots++;}
    @SubscribeEvent public static void item(EntityJoinLevelEvent e){if(Boolean.getBoolean("sentry.casingSmoke")&&SentryRegistry.EMPTY_CASING.isBound()&&!e.loadedFromDisk()&&!e.getLevel().isClientSide&&e.getEntity() instanceof ItemEntity item&&item.getItem().is(SentryRegistry.EMPTY_CASING.get())){owned.add(item);spawned+=item.getItem().getCount();if(observing)actual.merge(EmptyCasingItem.ammoId(item.getItem()),item.getItem().getCount(),Integer::sum);}}
    static void finish(){
        test("actual player native shooting produces casings",()->check(playerShots==3,"player shots="+playerShots));
        test("native semi auto burst shotgun and sentry shots each drop once",()->check(!expected.isEmpty()&&expected.equals(actual),"expected="+expected+" actual="+actual));
        observing=false;owned.forEach(ItemEntity::discard);owned.clear();player.discard();level.setChunkForced(31,31,false);SentryMechanicalArm.LOGGER.info("CASING RESULT passed={} failed={} actualDrops={}",passed,failed,actual.values().stream().mapToInt(Integer::intValue).sum());
    }
}
