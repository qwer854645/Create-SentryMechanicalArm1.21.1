package euphy.upo.sentrymechanicalarm.content;

import com.simibubi.create.AllDataComponents;
import net.minecraft.world.item.*;
import net.minecraft.network.chat.Component;
import java.util.List;

public final class GunWorkpieceItem extends Item {
    public GunWorkpieceItem(Properties properties) { super(properties); }
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
            if (gun != null) target = com.tacz.guns.api.TimelessAPI.getCommonGunIndex(gun)
                    .<Component>map(index -> Component.translatable(index.getPojo().getName())).orElse(Component.literal(gun.toString()));
        }
        return target == null ? super.getName(stack) : Component.translatable("item.sentrymechanicalarm.gun_workpiece.named", target);
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        var identity = stack.get(euphy.upo.sentrymechanicalarm.registry.ModDataComponents.GUN_WORK.get());
        if (flag.isAdvanced() && identity != null) lines.add(Component.literal(identity.toString()));
        var progress = stack.get(AllDataComponents.SEQUENCED_ASSEMBLY);
        if (progress != null) {
            lines.add(Component.translatable("item.sentrymechanicalarm.gun_workpiece.step", progress.step()));
            if (flag.isAdvanced()) lines.add(Component.literal(progress.id().toString()));
        }
    }
}
