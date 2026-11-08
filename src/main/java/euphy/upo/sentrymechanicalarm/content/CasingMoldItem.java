package euphy.upo.sentrymechanicalarm.content;

import euphy.upo.sentrymechanicalarm.registry.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.List;

public final class CasingMoldItem extends Item {
    public CasingMoldItem(Properties properties) { super(properties); }
    public static ItemStack create(ResourceLocation ammo) {
        if (!SentryRegistry.CASING_MOLD.isBound()) return ItemStack.EMPTY;
        var stack = new ItemStack(SentryRegistry.CASING_MOLD.get());
        stack.set(ModDataComponents.CASING_AMMO.get(), ammo);
        return stack;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) euphy.upo.sentrymechanicalarm.client.CasingMoldScreen.open(hand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        var ammo = EmptyCasingItem.ammoId(stack);
        if (ammo != null) lines.add(Component.translatable("item.sentrymechanicalarm.casing_mold.selected",
                SentryRegistry.EMPTY_CASING.get().getName(EmptyCasingItem.create(ammo, 1))).withStyle(ChatFormatting.GOLD));
        else lines.add(Component.translatable("item.sentrymechanicalarm.casing_mold.unconfigured").withStyle(ChatFormatting.GRAY));
        if (flag.isAdvanced() && ammo != null) lines.add(Component.literal(ammo.toString()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
