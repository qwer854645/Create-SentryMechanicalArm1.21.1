package euphy.upo.sentrymechanicalarm.util;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunFirePostEvent;
import com.tacz.guns.api.item.*;
import euphy.upo.sentrymechanicalarm.SMAServerConfig;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.content.EmptyCasingItem;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Pre/post pairs prove real ammunition was consumed, including native delayed bursts. */
@EventBusSubscriber(modid=SentryMechanicalArm.MODID)
public final class SentryCasingDrops {
    private record Before(ItemStack gun,ResourceLocation ammo,long count) {}
    private static final Map<LivingEntity,Before> pending=new WeakHashMap<>();
    private record ChunkKey(ResourceLocation dimension,long chunk) {}
    private static final Map<ChunkKey,Integer> budgets=new HashMap<>();
    public static void clear(){pending.clear();budgets.clear();}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(ServerTickEvent.Post event){clear();}
    private static boolean shooterAllowed(LivingEntity shooter, ResourceLocation ammo) {
        if(!SentryRegistry.EMPTY_CASING.isBound()||!SMAServerConfig.ENABLE_CASINGS.get()||!SentryCasingProfiles.drop(ammo).enabled())return false;
        if(shooter instanceof FakePlayer fp){
            var arm=SentryFakePlayer.getArmFromPlayer(fp);
            if(arm==null||!arm.isShooterActive()||!SMAServerConfig.SENTRY_CASINGS.get())return false;
            // Infinite boxes can feed finite magazines; do not mint recoverable material from them.
            for(var box:arm.attachedAmmoBoxes)if(box.getItem() instanceof IAmmoBox b && b.isCreative(box)
                    &&(b.isAllTypeCreative(box)||ammo.equals(b.getAmmoId(box))))return false;
        }else if(shooter instanceof Player player){
            if(player.isCreative()||player.isSpectator()||!SMAServerConfig.PLAYER_CASINGS.get())return false;
        }else{
            if(!SMAServerConfig.OTHER_CASINGS.get())return false;
            for(var slot:net.minecraft.world.entity.EquipmentSlot.values()){
                var stack=shooter.getItemBySlot(slot);
                if(stack.getItem() instanceof IAmmoBox box&&box.isCreative(stack)
                        &&(box.isAllTypeCreative(stack)||ammo.equals(box.getAmmoId(stack))))return false;
            }
        }
        if(shooter instanceof Player player)for(var stack:player.getInventory().items)
            if(stack.getItem() instanceof IAmmoBox box&&box.isCreative(stack)&&(box.isAllTypeCreative(stack)||ammo.equals(box.getAmmoId(stack))))return false;
        return true;
    }
    private static long ammunition(LivingEntity shooter,ItemStack gun,ResourceLocation ammo) {
        IGun api=IGun.getIGunOrNull(gun);if(api==null)return 0;
        long total=Math.max(0,api.getCurrentAmmoCount(gun))+(api.hasBulletInBarrel(gun)?1:0);
        if(shooter instanceof Player player)for(var stack:player.getInventory().items){
            if(stack==gun)continue;
            if(stack.getItem() instanceof IAmmo a&&ammo.equals(a.getAmmoId(stack)))total+=stack.getCount();
            else if(stack.getItem() instanceof IAmmoBox box&&!box.isCreative(stack)&&ammo.equals(box.getAmmoId(stack)))total+=Math.max(0,box.getAmmoCount(stack));
        }
        return total;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void before(GunFireEvent event) {
        if(event.getLogicalSide()!=LogicalSide.SERVER)return;
        var shooter=event.getShooter();pending.remove(shooter);
        if(!SentryRegistry.EMPTY_CASING.isBound())return;
        var gun=event.getGunItemStack();var api=IGun.getIGunOrNull(gun);if(api==null)return;
        var index=TimelessAPI.getCommonGunIndex(api.getGunId(gun));if(index.isEmpty())return;
        var ammo=index.get().getGunData().getAmmoId();if(!shooterAllowed(shooter,ammo))return;
        pending.put(shooter,new Before(gun,ammo,ammunition(shooter,gun,ammo)));
    }
    @SubscribeEvent public static void after(GunFirePostEvent event) {
        if(event.getLogicalSide()!=LogicalSide.SERVER)return;
        var shooter=event.getShooter();var before=pending.remove(shooter);
        if(before==null||before.gun()!=event.getGunItemStack()||!(shooter.level() instanceof ServerLevel level)
                ||!shooterAllowed(shooter,before.ammo())||before.count()<=ammunition(shooter,before.gun(),before.ammo()))return;
        var rule=SentryCasingProfiles.drop(before.ammo());
        if(level.random.nextDouble()>=(rule.chance()==null?SMAServerConfig.CASING_CHANCE.get():rule.chance()))return;
        var template=rule.template();int remaining=rule.count();
        if(shooter instanceof FakePlayer fake){
            var arm=SentryFakePlayer.getArmFromPlayer(fake);
            var rest=arm==null?template.copyWithCount(remaining):CasingCollectors.insert(arm,template.copyWithCount(remaining));
            if(rest.isEmpty())return;
            remaining=rest.getCount();template=rest.copyWithCount(1);
        }else if(shooter instanceof net.minecraft.server.level.ServerPlayer player&&SMAServerConfig.PLAYER_CASINGS_TO_INVENTORY.get()){
            giveToInventory(player,template,remaining);return;
        }
        Vec3 forward=shooter.getLookAngle().normalize();Vec3 origin=shooter.getEyePosition();
        if(shooter instanceof FakePlayer fp){var pose=SentryFakePlayer.getArmFromPlayer(fp).getGunPose();origin=pose.position();forward=pose.forward();}
        Vec3 right=forward.cross(new Vec3(0,1,0));if(right.lengthSqr()<1e-8)right=new Vec3(1,0,0);else right=right.normalize();
        Vec3 position=origin.add(right.scale(.15)).subtract(forward.scale(.1));
        if(!SentryGunGeometry.finite(position)||!level.hasChunkAt(net.minecraft.core.BlockPos.containing(position)))return;
        int cx=net.minecraft.util.Mth.floor(position.x)>>4,cz=net.minecraft.util.Mth.floor(position.z)>>4;
        var chunkBox=new AABB(cx*16,level.getMinBuildHeight(),cz*16,cx*16+16,level.getMaxBuildHeight(),cz*16+16);
        int cap=SMAServerConfig.CASING_ENTITY_LIMIT.get();
        var key=new ChunkKey(level.dimension().location(),net.minecraft.world.level.ChunkPos.asLong(cx,cz));
        int occupied=budgets.computeIfAbsent(key,k->level.getEntitiesOfClass(ItemEntity.class,chunkBox,
                e->e.getItem().is(SentryRegistry.EMPTY_CASING.get())||e.getPersistentData().getBoolean("sentrymechanicalarm:casing_drop")).size());
        if(occupied>=cap)return;
        while(remaining>0&&occupied<cap){
            int amount=Math.min(remaining,template.getMaxStackSize());
            var item=new ItemEntity(level,position.x,position.y,position.z,template.copyWithCount(amount));
            item.getPersistentData().putBoolean("sentrymechanicalarm:casing_drop",true);
            item.setDeltaMovement(right.scale(.18+level.random.nextDouble()*.08).add(0,.1+level.random.nextDouble()*.04,0));
            item.setPickUpDelay(10);item.lifespan=SMAServerConfig.CASING_LIFETIME.get();
            if(!level.addFreshEntity(item))break;
            remaining-=amount;occupied++;budgets.put(key,occupied);
        }
    }
    /** Vanilla /give insertion and overflow semantics, without creating a fake pickup entity on success. */
    private static void giveToInventory(net.minecraft.server.level.ServerPlayer player,ItemStack template,int count){
        while(count>0){
            int amount=Math.min(count,template.getMaxStackSize());count-=amount;
            var stack=template.copyWithCount(amount);player.getInventory().add(stack);
            if(stack.getCount()<amount)player.level().playSound(null,player.getX(),player.getY(),player.getZ(),
                    net.minecraft.sounds.SoundEvents.ITEM_PICKUP,net.minecraft.sounds.SoundSource.PLAYERS,.2f,
                    ((player.getRandom().nextFloat()-player.getRandom().nextFloat())*.7f+1)*2);
            if(!stack.isEmpty()){
                var overflow=player.drop(stack,false);
                if(overflow!=null){overflow.setNoPickUpDelay();overflow.setTarget(player.getUUID());overflow.getPersistentData().putBoolean("sentrymechanicalarm:casing_drop",true);}
            }
        }
        player.getInventory().setChanged();player.containerMenu.broadcastChanges();
    }

}
