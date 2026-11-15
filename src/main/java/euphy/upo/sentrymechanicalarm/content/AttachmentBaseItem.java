package euphy.upo.sentrymechanicalarm.content;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import java.util.List;

/** Shares selection/refund infrastructure, but never matches a firearm entrance. */
public final class AttachmentBaseItem extends ReceiverComponentItem {
    public AttachmentBaseItem(Properties properties){super(properties);}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        lines.add(Component.translatable("item.sentrymechanicalarm.attachment_base.use").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
