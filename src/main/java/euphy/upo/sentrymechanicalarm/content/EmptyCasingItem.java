package euphy.upo.sentrymechanicalarm.content;

import com.tacz.guns.api.TimelessAPI;
import euphy.upo.sentrymechanicalarm.registry.ModDataComponents;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;

public final class EmptyCasingItem extends Item {
    public EmptyCasingItem(Properties properties) { super(properties); }
    public static ResourceLocation ammoId(ItemStack stack) { return stack.get(ModDataComponents.CASING_AMMO.get()); }
    public static ItemStack create(ResourceLocation ammoId, int count) {
        var stack = new ItemStack(SentryRegistry.EMPTY_CASING.get(), count);
        stack.set(ModDataComponents.CASING_AMMO.get(), ammoId);
        return stack;
    }
    @Override public Component getName(ItemStack stack) {
        var id = ammoId(stack);
        if (id == null) return super.getName(stack);
        Component ammoName = TimelessAPI.getCommonAmmoIndex(id)
                .<Component>map(index -> {
                    String name=index.getPojo().getName();
                    return name==null||name.isBlank()?Component.literal(id.toString()):Component.translatable(name);
                })
                .orElseGet(() -> Component.literal(id.toString()));
        return Component.translatable("item.sentrymechanicalarm.empty_casing.named", ammoName);
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        var id = ammoId(stack);
        if (id != null) tooltip.add(Component.literal(id.toString()).withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.sentrymechanicalarm.empty_casing.description").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
    @SuppressWarnings("removal")
    @Override public void initializeClient(java.util.function.Consumer<net.neoforged.neoforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            private final euphy.upo.sentrymechanicalarm.client.EmptyCasingRenderer renderer = new euphy.upo.sentrymechanicalarm.client.EmptyCasingRenderer();
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() { return renderer; }
        });
    }
}
