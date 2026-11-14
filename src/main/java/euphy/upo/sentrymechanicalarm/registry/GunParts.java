package euphy.upo.sentrymechanicalarm.registry;

import euphy.upo.sentrymechanicalarm.content.GunWorkpieceItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import java.util.*;

public final class GunParts {
    private static final Map<String, DeferredItem<Item>> PARTS = new LinkedHashMap<>();
    static {
        for (String name : List.of("barrel_blank", "receiver_blank", "bolt_blank", "gun_spring", "gun_fastener", "grip_blank", "feed_part", "conductive_part",
                "barrel_component", "receiver_component", "action_component", "grip_component", "feed_component", "control_component"))
            PARTS.put(name, SentryRegistry.ITEMS.register(name, () -> new Item(new Item.Properties())));
    }
    public static final DeferredItem<GunWorkpieceItem> WORKPIECE = SentryRegistry.ITEMS.register("gun_workpiece",
            () -> new GunWorkpieceItem(new Item.Properties().stacksTo(1)));
    public static void register() {} // Trigger registration before the item register is attached to the bus.
    public static Item item(String name) { var entry = PARTS.get(name); if (entry == null) throw new IllegalArgumentException("Unknown gun part: " + name); return entry.get(); }
    public static Set<String> names() { return Collections.unmodifiableSet(PARTS.keySet()); }
}
