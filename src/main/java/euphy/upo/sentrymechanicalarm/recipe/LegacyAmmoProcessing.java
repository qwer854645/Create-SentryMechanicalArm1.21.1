package euphy.upo.sentrymechanicalarm.recipe;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import euphy.upo.sentrymechanicalarm.content.UnfinishedAmmoItem;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.ItemNBTHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Legacy copper/powder belt processing: one material pays for one unfinished item. */
public final class LegacyAmmoProcessing {
    public record Result(ItemStack processed, ItemStack remainder) {}

    public static Result apply(ItemStack input, ItemStack material, long time) {
        if (!euphy.upo.sentrymechanicalarm.SMAServerConfig.ENABLE_AMMO_CRAFTING.get()) return null;
        if (input.isEmpty() || material.isEmpty() || input.getItem() != SentryRegistry.UNFINISHED_AMMO.get()
                || input.has(AllDataComponents.SEQUENCED_ASSEMBLY) || UnfinishedAmmoItem.getAmmoId(input) == null) return null;
        boolean copper = material.is(AllItems.COPPER_SHEET.get());
        if (!copper && !material.is(Items.GUNPOWDER)) return null;
        if (copper ? UnfinishedAmmoItem.getCopperSheets(input) >= UnfinishedAmmoItem.MAX_COPPER_SHEETS
                : UnfinishedAmmoItem.hasGunpowder(input)) return null;
        var tag = ItemNBTHelper.getOrCreateTag(input);
        long cooldown = copper ? 30 : 120;
        if (tag.contains("SMAPCooldown") && time - tag.getLong("SMAPCooldown") < cooldown) return null;
        int count = Math.min(input.getCount(), material.getCount());
        ItemStack output = input.copyWithCount(count);
        if (copper) UnfinishedAmmoItem.setCopperSheets(output, UnfinishedAmmoItem.getCopperSheets(output) + 1);
        else UnfinishedAmmoItem.setGunpowderAdded(output);
        var updated = ItemNBTHelper.getOrCreateTag(output);
        updated.putLong("SMAPCooldown", time);
        ItemNBTHelper.setTag(output, updated);
        material.shrink(count);
        return new Result(output, input.copyWithCount(input.getCount() - count));
    }
}
