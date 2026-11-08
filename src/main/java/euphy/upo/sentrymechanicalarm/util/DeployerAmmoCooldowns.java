package euphy.upo.sentrymechanicalarm.util;

import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.Map;
import java.util.WeakHashMap;

/** Transient processing state belongs to the transported object, never its item components. */
public final class DeployerAmmoCooldowns {
    private record Cooldown(BlockPos pos, long until) {}
    private static final Map<Level, Map<TransportedItemStack, Cooldown>> LEVELS = new WeakHashMap<>();

    public static boolean cooling(Level level, TransportedItemStack stack, BlockPos pos) {
        var entries = LEVELS.get(level);
        if (entries == null) return false;
        var cooldown = entries.get(stack);
        if (cooldown == null) return false;
        if (level.getGameTime() >= cooldown.until() || !pos.equals(cooldown.pos())) {
            entries.remove(stack);
            return false;
        }
        return true;
    }

    public static void filled(Level level, TransportedItemStack stack, BlockPos pos) {
        LEVELS.computeIfAbsent(level, ignored -> new WeakHashMap<>())
                .put(stack, new Cooldown(pos.immutable(), level.getGameTime() + 60));
    }

    public static void removeLegacyData(ItemStack stack) {
        var tag = ItemNBTHelper.getTag(stack);
        if (!tag.contains("DeployerCooldownPos") && !tag.contains("DeployerCooldownTime")) return;
        tag.remove("DeployerCooldownPos");
        tag.remove("DeployerCooldownTime");
        if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else ItemNBTHelper.setTag(stack, tag);
    }
}
