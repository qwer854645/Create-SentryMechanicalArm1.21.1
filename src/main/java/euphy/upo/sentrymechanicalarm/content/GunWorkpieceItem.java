package euphy.upo.sentrymechanicalarm.content;

import com.simibubi.create.AllDataComponents;
import net.minecraft.world.item.*;
import net.minecraft.network.chat.Component;
import java.util.List;

public final class GunWorkpieceItem extends Item {
    public GunWorkpieceItem(Properties properties) { super(properties); }
    @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        var stored = stack.get(euphy.upo.sentrymechanicalarm.registry.ModDataComponents.GUN_RECEIVER.get());
        var receiver = stored == null ? ItemStack.EMPTY : stored.copyOne();
        if (player.isShiftKeyDown() && !stack.has(AllDataComponents.SEQUENCED_ASSEMBLY)
                && receiver.getItem() instanceof ReceiverComponentItem) {
            if (!level.isClientSide) {
                player.setItemInHand(hand, receiver.copyWithCount(1));
                player.getInventory().setChanged(); player.containerMenu.broadcastChanges();
            }
            return net.minecraft.world.InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }
        return super.use(level, player, hand);
    }
    @Override public Component getName(ItemStack stack) {
        var id = stack.get(euphy.upo.sentrymechanicalarm.registry.ModDataComponents.GUN_WORK.get());
        if (id == null) return super.getName(stack);
        String path = id.getPath().replaceFirst("/v_[^/]+$", "");
        Component target = null;
        if (path.startsWith("gun_manufacturing/parts/")) {
            String name = path.substring("gun_manufacturing/parts/".length());
            if (euphy.upo.sentrymechanicalarm.registry.GunParts.names().contains(name))
                target = Component.translatable(euphy.upo.sentrymechanicalarm.registry.GunParts.item(name).getDescriptionId());
        } else if (path.startsWith("gun_manufacturing/guns/")) {
            var gun = net.minecraft.resources.ResourceLocation.tryParse(path.substring("gun_manufacturing/guns/".length()).replaceFirst("/", ":"));
            if (gun != null) target = euphy.upo.sentrymechanicalarm.util.GunNames.name(gun);
        }
        if (path.startsWith("gun_manufacturing/attachments/")) {
            var attachment=net.minecraft.resources.ResourceLocation.tryParse(path.substring("gun_manufacturing/attachments/".length()).replaceFirst("/", ":"));
            if(attachment!=null) target=euphy.upo.sentrymechanicalarm.util.AssemblyTargets.attachmentName(attachment);
        }
        return target == null ? super.getName(stack) : Component.translatable("item.sentrymechanicalarm.gun_workpiece.named", target);
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        var identity = stack.get(euphy.upo.sentrymechanicalarm.registry.ModDataComponents.GUN_WORK.get());
        if (flag.isAdvanced() && identity != null) lines.add(Component.literal(identity.toString()));
        var progress = stack.get(AllDataComponents.SEQUENCED_ASSEMBLY);
        boolean attachment=identity!=null&&identity.getPath().startsWith("gun_manufacturing/attachments/");
        if (progress == null && stack.has(euphy.upo.sentrymechanicalarm.registry.ModDataComponents.GUN_RECEIVER.get()))
            lines.add(Component.translatable("item.sentrymechanicalarm."+(attachment?"attachment_workpiece":"gun_workpiece")+".refund").withStyle(net.minecraft.ChatFormatting.GRAY));
        if (progress == null && identity != null && (identity.getPath().startsWith("gun_manufacturing/guns/") || identity.getPath().startsWith("gun_manufacturing/attachments/")))
            lines.add(Component.translatable("item.sentrymechanicalarm."+(attachment?"attachment_workpiece":"gun_workpiece")+".copy").withStyle(net.minecraft.ChatFormatting.GRAY));
        if (progress != null) {
            lines.add(Component.translatable("item.sentrymechanicalarm.gun_workpiece.step", progress.step()));
            if (flag.isAdvanced()) lines.add(Component.literal(progress.id().toString()));
        }
    }
}
