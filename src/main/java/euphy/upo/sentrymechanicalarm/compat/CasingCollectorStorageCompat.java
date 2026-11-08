package euphy.upo.sentrymechanicalarm.compat;

import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorageType;
import euphy.upo.sentrymechanicalarm.content.CasingCollectorBlockEntity;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

public final class CasingCollectorStorageCompat {
    private static final DeferredRegister<MountedItemStorageType<?>> TYPES=DeferredRegister.create(CreateRegistries.MOUNTED_ITEM_STORAGE_TYPE,"sentrymechanicalarm");
    public static final DeferredHolder<MountedItemStorageType<?>,Type> TYPE=TYPES.register("casing_collector",Type::new);
    public static void register(IEventBus bus){TYPES.register(bus);}
    public static void bind(){MountedItemStorageType.REGISTRY.register(SentryRegistry.CASING_COLLECTOR_BLOCK.get(),TYPE.get());}
    public static final class Type extends MountedItemStorageType<CasingCollectorMountedStorage>{
        Type(){super(CasingCollectorMountedStorage.CODEC);}
        @Override public CasingCollectorMountedStorage mount(Level level,BlockState state,BlockPos pos,BlockEntity entity){
            return entity instanceof CasingCollectorBlockEntity collector?new CasingCollectorMountedStorage(collector.getItemHandler()):null;
        }
    }
}
