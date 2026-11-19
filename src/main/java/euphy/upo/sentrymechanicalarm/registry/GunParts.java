package euphy.upo.sentrymechanicalarm.registry;

import euphy.upo.sentrymechanicalarm.content.GunWorkpieceItem;
import euphy.upo.sentrymechanicalarm.content.ReceiverComponentItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import java.util.*;

public final class GunParts {
    public static final List<String> FAMILIES = List.of("barrel", "receiver", "action", "recoil", "grip", "feed", "trigger", "control");
    public static final List<String> ATTACHMENT_FAMILIES = List.of("attachment_base", "optics", "muzzle");
    public static boolean attachmentPart(String name) {
        if(List.of("attachment_blank","optical_blank","muzzle_blank").contains(name))return true;
        for(var family:ATTACHMENT_FAMILIES)for(var tier:Tier.values())if(tier.component(family).equals(name))return true;
        return false;
    }
    public static List<String> allFamilies() { var result=new ArrayList<>(FAMILIES); result.addAll(ATTACHMENT_FAMILIES); return List.copyOf(result); }
    public enum Tier {
        BASIC(""), PRECISION("precision_"), REINFORCED("reinforced_");
        private final String prefix;
        Tier(String prefix) { this.prefix = prefix; }
        public String component(String family) {
            if (!allFamilies().contains(family)) throw new IllegalArgumentException("Unknown component family: " + family);
            return prefix + family + "_component";
        }
    }
    private static final Map<String, DeferredItem<Item>> PARTS = new LinkedHashMap<>();
    static {
        for (String name : List.of("barrel_blank", "receiver_blank", "bolt_blank", "gun_spring", "gun_fastener", "grip_blank", "feed_part", "trigger_blank", "conductive_part", "attachment_blank", "optical_blank", "muzzle_blank"))
            PARTS.put(name, SentryRegistry.ITEMS.register(name, () -> new Item(new Item.Properties())));
        for (Tier tier : Tier.values()) for (String family : allFamilies()) {
            String name = tier.component(family);
            PARTS.put(name, SentryRegistry.ITEMS.register(name, () -> family.equals("receiver")
                    ? new ReceiverComponentItem(new Item.Properties()) : family.equals("attachment_base")
                    ? new euphy.upo.sentrymechanicalarm.content.AttachmentBaseItem(new Item.Properties()) : new Item(new Item.Properties())));
        }
    }
    public static final DeferredItem<Item> FORMING_JIG = SentryRegistry.ITEMS.register("part_forming_jig",
            () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<GunWorkpieceItem> WORKPIECE = SentryRegistry.ITEMS.register("gun_workpiece",
            () -> new GunWorkpieceItem(new Item.Properties().stacksTo(1)));
    public static void register() {} // Trigger registration before the item register is attached to the bus.
    public static Item item(String name) { var entry = PARTS.get(name); if (entry == null) throw new IllegalArgumentException("Unknown gun part: " + name); return entry.get(); }
    public static Set<String> names() { return Collections.unmodifiableSet(PARTS.keySet()); }
}
