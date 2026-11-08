package euphy.upo.sentrymechanicalarm.compat;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.api.contraption.storage.item.simple.SimpleMountedStorage;
import net.neoforged.neoforge.items.IItemHandler;

/** Create owns capture, serialization, interaction, PSI access and unmount; no second inventory copy. */
public final class CasingCollectorMountedStorage extends SimpleMountedStorage {
    public static final MapCodec<CasingCollectorMountedStorage> CODEC=SimpleMountedStorage.codec(CasingCollectorMountedStorage::new);
    public CasingCollectorMountedStorage(IItemHandler handler){super(CasingCollectorStorageCompat.TYPE.get(),handler);}
}
