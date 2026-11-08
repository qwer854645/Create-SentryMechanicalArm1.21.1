package euphy.upo.sentrymechanicalarm.content;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;

public final class PrimerItem extends Item {
    public PrimerItem(Properties properties) { super(properties); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(stack.is(euphy.upo.sentrymechanicalarm.registry.SentryRegistry.PRIMER_CUP.get()) ? "item.sentrymechanicalarm.primer_cup.description" : "item.sentrymechanicalarm.primer.description").withStyle(ChatFormatting.GRAY));
    }
}
