package euphy.upo.sentrymechanicalarm.util;

import dev.ryanhcode.sable.companion.SableCompanion;
import euphy.upo.sentrymechanicalarm.compat.*;
import euphy.upo.sentrymechanicalarm.content.*;
import net.minecraft.core.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.lang.ref.WeakReference;
import java.util.*;

/** Same-owner insertion only. No ground-item vacuuming, remote chest insertion or forced chunk loading. */
@EventBusSubscriber(modid="sentrymechanicalarm")
public final class CasingCollectors {
    private record Owner(String api,Object id) {}
    private static final Map<Level,Map<BlockPos,WeakReference<CasingCollectorBlockEntity>>> loaded=new WeakHashMap<>();
    public static void register(CasingCollectorBlockEntity collector){if(collector.getLevel()!=null)loaded.computeIfAbsent(collector.getLevel(),l->new HashMap<>()).put(collector.getBlockPos().immutable(),new WeakReference<>(collector));}
    public static void unregister(CasingCollectorBlockEntity collector){var entries=loaded.get(collector.getLevel());if(entries!=null){var ref=entries.get(collector.getBlockPos());if(ref!=null&&ref.get()==collector)entries.remove(collector.getBlockPos());}}
    @SubscribeEvent public static void stop(net.neoforged.neoforge.event.server.ServerStoppedEvent event){loaded.clear();}
    private static Object vsShipAt(Level level,BlockPos pos) throws ReflectiveOperationException {
        var api=Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
        try{return api.getMethod("getShipObjectManagingPos",Level.class,BlockPos.class).invoke(null,level,pos);}
        catch(NoSuchMethodException missing){return api.getMethod("getShipObjectManagingPos",Level.class,net.minecraft.world.phys.Vec3.class).invoke(null,level,pos.getCenter());}
    }
    /** Container GUI distance must use the projected position, not a shipyard/plot coordinate. */
    public static net.minecraft.world.phys.Vec3 worldPosition(Level level,BlockPos pos){
        var center=pos.getCenter();var sublevel=SableCompanion.INSTANCE.getContaining(level,pos);
        if(sublevel!=null)return sublevel.logicalPose().transformPosition(center);
        if(!AeronauticsHelper.isAeronauticsLoaded())return center;
        try{var ship=vsShipAt(level,pos);if(ship==null)return center;
            var shipApi=Class.forName("org.valkyrienskies.core.api.ships.Ship");var transform=shipApi.getMethod("getTransform").invoke(ship);
            var transformApi=Class.forName("org.valkyrienskies.core.api.ships.ShipTransform");
            if(transformApi.getMethod("getShipToWorld").invoke(transform) instanceof org.joml.Matrix4dc matrix){var projected=matrix.transformPosition(new org.joml.Vector3d(center.x,center.y,center.z));return new net.minecraft.world.phys.Vec3(projected.x,projected.y,projected.z);}
        }catch(ReflectiveOperationException ignored){}
        return null;
    }
    private static Owner owner(Level level,BlockPos pos){
        var sublevel=SableCompanion.INSTANCE.getContaining(level,pos);
        if(sublevel!=null)return new Owner("sable",sublevel.getUniqueId());
        if(!AeronauticsHelper.isAeronauticsLoaded())return null;
        try{
            var ship=vsShipAt(level,pos);
            if(ship==null)return null;
            var shipApi=Class.forName("org.valkyrienskies.core.api.ships.Ship");
            return new Owner("vs",((Number)shipApi.getMethod("getId").invoke(ship)).longValue());
        }catch(ReflectiveOperationException error){return new Owner("unresolved",pos.asLong());}
    }
    public static ItemStack insert(SentryArmBlockEntity arm,ItemStack stack){
        if(stack.isEmpty()||arm.getLevel()==null||!arm.isShooterActive())return stack;
        if(arm instanceof VirtualSentryArmBlockEntity virtual){
            var contraption=virtual.getOwningContraption();if(contraption==null||contraption.getStorage()==null)return stack;
            var stores=contraption.getStorage().getAllItemStorages().entrySet().stream()
                    .filter(e->e.getValue() instanceof CasingCollectorMountedStorage)
                    .sorted(Comparator.<Map.Entry<BlockPos,com.simibubi.create.api.contraption.storage.item.MountedItemStorage>>comparingDouble(e->e.getKey().distSqr(arm.getBlockPos())).thenComparingLong(e->e.getKey().asLong())).toList();
            for(var entry:stores){stack=ItemHandlerHelper.insertItemStacked(entry.getValue(),stack,false);if(stack.isEmpty())break;}
            return stack;
        }
        var level=arm.getLevel();var source=owner(level,arm.getBlockPos());
        if(source==null){
            for(var direction:Direction.values()){
                var pos=arm.getBlockPos().relative(direction);
                if(!level.isLoaded(pos)||owner(level,pos)!=null)continue;
                if(level.getBlockEntity(pos) instanceof CasingCollectorBlockEntity collector){stack=ItemHandlerHelper.insertItemStacked(collector.getItemHandler(),stack,false);if(stack.isEmpty())break;}
            }
            return stack;
        }
        var entries=loaded.get(level);if(entries==null)return stack;
        var candidates=new ArrayList<CasingCollectorBlockEntity>();
        for(var iterator=entries.entrySet().iterator();iterator.hasNext();){var entry=iterator.next();var collector=entry.getValue().get();
            if(collector==null||collector.isRemoved()||!level.isLoaded(entry.getKey())||level.getBlockEntity(entry.getKey())!=collector){iterator.remove();continue;}
            if(source.equals(owner(level,entry.getKey())))candidates.add(collector);
        }
        candidates.sort(Comparator.comparingDouble((CasingCollectorBlockEntity c)->c.getBlockPos().distSqr(arm.getBlockPos())).thenComparingLong(c->c.getBlockPos().asLong()));
        for(var collector:candidates){stack=ItemHandlerHelper.insertItemStacked(collector.getItemHandler(),stack,false);if(stack.isEmpty())break;}
        return stack;
    }
}
