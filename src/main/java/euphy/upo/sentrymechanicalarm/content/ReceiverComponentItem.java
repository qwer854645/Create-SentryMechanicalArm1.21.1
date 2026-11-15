package euphy.upo.sentrymechanicalarm.content;

import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import java.util.List;

public class ReceiverComponentItem extends Item {
    public ReceiverComponentItem(Properties properties) { super(properties); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) euphy.upo.sentrymechanicalarm.client.GunAssemblyScreen.open(hand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.sentrymechanicalarm.receiver_component.use").withStyle(ChatFormatting.GRAY));
    }
}
