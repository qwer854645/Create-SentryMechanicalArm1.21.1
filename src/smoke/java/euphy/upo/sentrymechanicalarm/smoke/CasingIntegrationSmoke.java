package euphy.upo.sentrymechanicalarm.smoke;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.GunFirePostEvent;
import com.tacz.guns.api.item.*;
import euphy.upo.sentrymechanicalarm.*;
import euphy.upo.sentrymechanicalarm.content.EmptyCasingItem;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import java.util.*;

@EventBusSubscriber(modid=SentryMechanicalArm.MODID)
public final class CasingIntegrationSmoke {
    static final Map<ResourceLocation,Integer> shots=new HashMap<>(),drops=new HashMap<>();
    static final List<ItemEntity> owned=new ArrayList<>();static boolean active;
    @SubscribeEvent(priority=EventPriority.HIGH)public static void start(ServerStartedEvent e){
        if(!Boolean.getBoolean("sentry.casingIntegrationSmoke"))return;
        SMAServerConfig.CASING_ENTITY_LIMIT.set(1024);SMAServerConfig.CASING_CHANCE.set(1.0);active=true;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)public static void shot(GunFirePostEvent e){
        if(!active||e.getShooter().level().isClientSide)return;
        var api=IGun.getIGunOrNull(e.getGunItemStack());if(api==null)return;
        var id=TimelessAPI.getCommonGunIndex(api.getGunId(e.getGunItemStack())).orElseThrow().getGunData().getAmmoId();
        if(SentryCasingProfiles.enabled(id))shots.merge(id,1,Integer::sum);
    }
    @SubscribeEvent public static void item(EntityJoinLevelEvent e){
        if(active&&!e.loadedFromDisk()&&!e.getLevel().isClientSide&&e.getEntity() instanceof ItemEntity item&&item.getItem().is(SentryRegistry.EMPTY_CASING.get())){
            owned.add(item);drops.merge(EmptyCasingItem.ammoId(item.getItem()),item.getItem().getCount(),Integer::sum);
        }
    }
    static void finish(){
        if(!active)return;active=false;owned.forEach(ItemEntity::discard);owned.clear();
        if(shots.isEmpty()||!shots.equals(drops))SentryMechanicalArm.LOGGER.error("CASING_INTEGRATION FAIL shots={} drops={}",shots,drops);
        else SentryMechanicalArm.LOGGER.info("CASING_INTEGRATION PASS shots={} drops={}",shots,drops);
    }
}
