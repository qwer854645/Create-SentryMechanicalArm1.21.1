package euphy.upo.sentrymechanicalarm.compat;

import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorageType;
import com.tacz.guns.block.entity.AmmoStorageBlockEntity;
import com.tacz.guns.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.*;
import net.neoforged.bus.api.IEventBus;

public final class SentryAmmoDepotCompat {
    private static final DeferredRegister<MountedItemStorageType<?>> TYPES=DeferredRegister.create(CreateRegistries.MOUNTED_ITEM_STORAGE_TYPE,"sentrymechanicalarm");
    public static final DeferredHolder<MountedItemStorageType<?>,Type> TYPE=TYPES.register("tacz_ammo_depot",Type::new);
    public static void register(IEventBus bus){TYPES.register(bus);}
    public static void bind(){
        for(var block:java.util.List.of(ModBlocks.AMMO_STORAGE.get(),ModBlocks.AMMO_STORAGE_GOLD.get(),ModBlocks.AMMO_STORAGE_DIAMOND.get()))
            MountedItemStorageType.REGISTRY.register(block,TYPE.get());
    }
    public static final class Type extends MountedItemStorageType<AmmoDepotMountedStorage>{
        Type(){super(AmmoDepotMountedStorage.CODEC);}
        @Override public AmmoDepotMountedStorage mount(Level level,BlockState state,BlockPos pos,BlockEntity entity){
            return entity instanceof AmmoStorageBlockEntity depot?AmmoDepotMountedStorage.capture(depot):null;
        }
    }
}
