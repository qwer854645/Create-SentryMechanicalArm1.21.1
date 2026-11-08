package euphy.upo.sentrymechanicalarm.smoke;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.*;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import euphy.upo.sentrymechanicalarm.*;
import euphy.upo.sentrymechanicalarm.content.*;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.*;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import java.util.*;

@EventBusSubscriber(modid=SentryMechanicalArm.MODID)
public final class CasingSwitchSmoke {
    static int passed,failed,drops,shots,ticks,baseline;static Husk mob;static net.minecraft.server.level.ServerLevel level;
    static final List<ItemEntity> items=new ArrayList<>();static boolean running;
    interface Check{void run()throws Exception;}
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    static void test(String name,Check c){try{c.run();passed++;SentryMechanicalArm.LOGGER.info("CASING_SWITCH PASS {}",name);}catch(Throwable e){failed++;SentryMechanicalArm.LOGGER.error("CASING_SWITCH FAIL "+name,e);}}
    static ItemStack gun(){return GunItemBuilder.create().setId(ResourceLocation.parse("tacz:glock_17")).setFireMode(FireMode.SEMI).setAmmoCount(40).setAmmoInBarrel(true).build();}
    static void pair(LivingEntity entity,boolean consume){var gun=entity.getMainHandItem();SentryCasingDrops.before(new GunFireEvent(entity,gun,LogicalSide.SERVER));if(consume){var api=IGun.getIGunOrNull(gun);api.setCurrentAmmoCount(gun,api.getCurrentAmmoCount(gun)-1);}SentryCasingDrops.after(new GunFirePostEvent(entity,gun,LogicalSide.SERVER));}
    @SubscribeEvent public static void start(ServerStartedEvent e){
        if(!"casingconfig".equals(System.getProperty("sentry.smoke")))return;
        running=true;level=e.getServer().overworld();
        var id=ResourceLocation.parse("sentrymechanicalarm:empty_casing");boolean enabled=SMAStartupConfig.ENABLE_CASINGS.get();
        test("casing names follow localized ammo nouns and preserve formatting",()->{
            check("§a9mm 手枪弹壳".equals(SentryCasingNames.fromAmmoName("§a9mm 手枪弹")),"Chinese pistol name");
            check("§e5.56x45mm 步枪弹壳§r".equals(SentryCasingNames.fromAmmoName("§e5.56x45mm 步枪弹§r")),"Chinese rifle colors");
            check("§a9mm Casing".equals(SentryCasingNames.fromAmmoName("§a9mm Bullet")),"English name");
            check("12 Gauge Casings".equals(SentryCasingNames.fromAmmoName("12 Gauge Shells")),"English plural");
            check(SentryCasingNames.fromAmmoName("Custom Magic Charge")==null,"unknown names should use translated fallback");
        });
        test("legacy casing item and recipes are removed",()->{
            check(!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("sentrymechanicalarm:unfinished_ammo")),"legacy item registered");
            check(!net.minecraft.core.registries.BuiltInRegistries.RECIPE_SERIALIZER.containsKey(ResourceLocation.parse("sentrymechanicalarm:ammo_template")),"legacy serializer registered");
            check(level.getRecipeManager().getRecipes().stream().noneMatch(h->h.id().getNamespace().equals("sentrymechanicalarm")&&h.id().getPath().startsWith("ammo_")),"legacy recipes present");
        });
        test("mold registration and manufacture recipes follow startup master",()->{
            check(SentryRegistry.CASING_MOLD.isBound()==enabled,"mold registration mismatch");
            if(!enabled)check(euphy.upo.sentrymechanicalarm.recipe.CasingFormingRecipes.choices(level.getRecipeManager()).isEmpty()&&level.getRecipeManager().byKey(ResourceLocation.parse("sentrymechanicalarm:casing_mold")).isEmpty(),"disabled mold recipes survived");
        });
        test("startup option controls real item registry",()->check(net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)==enabled&&SentryRegistry.RELOADING_CASING.isBound()==enabled,"wrong registry membership"));
        test("creative tab builds safely with current registration",()->{var tab=SentryRegistry.SENTRY_TAB.get();tab.buildContents(new CreativeModeTab.ItemDisplayParameters(net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS,true,level.registryAccess()));check(tab.getDisplayItems().stream().anyMatch(x->x.getItem() instanceof EmptyCasingItem)==false,"creative content mismatch");});
        test("dedicated casing tab follows startup registration and contains only casings",()->{
            var tabId=ResourceLocation.parse("sentrymechanicalarm:casings");
            check(net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(tabId)==enabled,"tab registration mismatch");
            if(enabled){var tab=SentryRegistry.CASINGS_TAB.get();tab.buildContents(new CreativeModeTab.ItemDisplayParameters(net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS,true,level.registryAccess()));
                check(tab.getDisplayItems().size()==SentryCasingProfiles.eligible().size(),"missing casing variants");
                check(tab.getDisplayItems().stream().allMatch(x->x.getItem() instanceof EmptyCasingItem),"non-casing entry");}
        });
        test("casing eligibility packet round-trips and excludes unsupported ammo",()->{
            var original=new euphy.upo.sentrymechanicalarm.network.SentryCasingProfilesPacket(SentryCasingProfiles.eligible());
            var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try{euphy.upo.sentrymechanicalarm.network.SentryCasingProfilesPacket.CODEC.encode(buffer,original);check(original.equals(euphy.upo.sentrymechanicalarm.network.SentryCasingProfilesPacket.CODEC.decode(buffer)),"packet mismatch");}finally{buffer.release();}
            for(String ammoId:java.util.List.of("tacz:rpg_rocket","ars_armorer:mana","applied_armorer:fluix_battery"))check(!original.ammo().contains(ResourceLocation.parse(ammoId)),"unsupported casing "+ammoId);
        });
        test("factory safely handles missing item",()->check(EmptyCasingItem.create(ResourceLocation.parse("tacz:9mm"),1).isEmpty()!=enabled,"unsafe factory"));
        if(!enabled){test("disabled shot hooks remain safe",()->{var entity=new Husk(EntityType.HUSK,level);entity.setItemSlot(EquipmentSlot.MAINHAND,gun());pair(entity,true);check(drops==0,"disabled casing drop");});test("disabled discovery skips casing profiles",()->check(SentryCasingProfiles.snapshot().isEmpty(),"profiles loaded while disabled"));finish();return;}
        SMAServerConfig.CASING_CHANCE.set(1.0);SMAServerConfig.CASING_ENTITY_LIMIT.set(1024);
        level.setChunkForced(31,31,true);level.getChunkAt(new net.minecraft.core.BlockPos(505,240,505));
        mob=new Husk(EntityType.HUSK,level);mob.setPos(505,240,505);mob.setNoAi(true);mob.setNoGravity(true);mob.setItemSlot(EquipmentSlot.MAINHAND,gun());
        test("other mobs default off",()->{int n=drops;SMAServerConfig.OTHER_CASINGS.set(false);pair(mob,true);check(drops==n,"disabled mob drops");});
        test("mob flag independent from player and sentry flags",()->{int n=drops;SMAServerConfig.OTHER_CASINGS.set(true);SMAServerConfig.PLAYER_CASINGS.set(false);SMAServerConfig.SENTRY_CASINGS.set(false);pair(mob,true);check(drops==n+1,"mob switch not independent");});
        test("mob success event without ammo cost gives nothing",()->{int n=drops;pair(mob,false);check(drops==n,"free mob casing");});
        test("infinite offhand box suppresses mob recovery",()->{int n=drops;var box=new ItemStack(com.tacz.guns.init.ModItems.AMMO_BOX.get());var api=(com.tacz.guns.api.item.IAmmoBox)box.getItem();api.setCreative(box,false);api.setAmmoId(box,ResourceLocation.parse("tacz:9mm"));mob.setItemSlot(EquipmentSlot.OFFHAND,box);pair(mob,true);mob.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);check(drops==n,"infinite mob casing farm");});
        test("sentry switch independent from player and mob switches",()->{
            var arm=new SentryArmBlockEntity(new net.minecraft.core.BlockPos(507,240,505),SentryRegistry.SENTRY_ARM_BLOCK.get().defaultBlockState()){@Override public boolean isShooterActive(){return true;}};
            arm.setLevel(level);arm.initialize();arm.setHeldItem(gun());var fp=SentryFakePlayer.get(arm);fp.setPos(507,240,505);fp.setItemSlot(EquipmentSlot.MAINHAND,gun());
            try{int n=drops;SMAServerConfig.OTHER_CASINGS.set(true);SMAServerConfig.SENTRY_CASINGS.set(false);pair(fp,true);check(drops==n,"disabled sentry drops");SMAServerConfig.SENTRY_CASINGS.set(true);SMAServerConfig.OTHER_CASINGS.set(false);pair(fp,true);check(drops==n+1,"sentry blocked by player/mob switches");}finally{SentryFakePlayer.remove(arm);}
        });
        SMAServerConfig.OTHER_CASINGS.set(true);SMAServerConfig.PLAYER_CASINGS.set(false);SMAServerConfig.SENTRY_CASINGS.set(false);
        mob.setItemSlot(EquipmentSlot.MAINHAND,gun());level.addFreshEntity(mob);var op=IGunOperator.fromLivingEntity(mob);op.initialData();op.draw(mob::getMainHandItem);op.getDataHolder().drawTimestamp=com.tacz.guns.util.time.GunTime.nowMillis()-10000;baseline=drops;
    }
    @SubscribeEvent public static void post(GunFirePostEvent e){if(running&&e.getShooter()==mob&&e.getLogicalSide()==LogicalSide.SERVER)shots++;}
    @SubscribeEvent public static void join(EntityJoinLevelEvent e){if(running&&e.getEntity() instanceof ItemEntity item&&item.getItem().getItem() instanceof EmptyCasingItem){drops+=item.getItem().getCount();items.add(item);}}
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){if(!running||mob==null)return;ticks++;
        if(ticks==20||ticks==40||ticks==60){SMAServerConfig.OTHER_CASINGS.set(ticks!=40);IGunOperator.fromLivingEntity(mob).shoot(()->0f,()->0f);}
        if(ticks==80){test("native TaCZ mob fires three real shots",()->check(shots==3,"shots="+shots));test("native mob drops respect flag and finite debit",()->check(drops-baseline==2,"native drops="+(drops-baseline)));finish();}
    }
    static void finish(){running=false;if(mob!=null)mob.discard();items.forEach(ItemEntity::discard);level.setChunkForced(31,31,false);SentryMechanicalArm.LOGGER.info("CASING_SWITCH RESULT enabled={} passed={} failed={}",SMAStartupConfig.ENABLE_CASINGS.get(),passed,failed);level.getServer().halt(false);}
}
