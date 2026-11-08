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

public class EmptyCasingItem extends Item {
    public EmptyCasingItem(Properties properties) { super(properties); }
    public static ResourceLocation ammoId(ItemStack stack) {
        var ammo=stack.get(ModDataComponents.CASING_AMMO.get());if(ammo!=null)return ammo;
        if(stack.getItem() instanceof ReloadingCasingItem){
            var progress=stack.get(com.simibubi.create.AllDataComponents.SEQUENCED_ASSEMBLY);
            if(progress!=null&&progress.id().getNamespace().equals("sentrymechanicalarm")&&progress.id().getPath().startsWith("casing_reloading/")){
                String path=progress.id().getPath().substring("casing_reloading/".length());int version=path.lastIndexOf("/v_");if(version>=0)path=path.substring(0,version);
                return ResourceLocation.tryParse(path.replaceFirst("/",":"));
            }
        }
        return null;
    }
    public static ItemStack create(ResourceLocation ammoId, int count) {
        if(!SentryRegistry.EMPTY_CASING.isBound())return ItemStack.EMPTY;
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
        // Resolve with the current language, so gunpack translations and inline colors carry over.
        String casing=euphy.upo.sentrymechanicalarm.util.SentryCasingNames.fromAmmoName(ammoName.getString());
        return casing==null?Component.translatable("item.sentrymechanicalarm.empty_casing.named", ammoName):Component.literal(casing);
    }
    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        euphy.upo.sentrymechanicalarm.client.CasingTooltips.append(stack,tooltip,flag);
    }
    @SuppressWarnings("removal")
    @Override public void initializeClient(java.util.function.Consumer<net.neoforged.neoforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            private final euphy.upo.sentrymechanicalarm.client.EmptyCasingRenderer renderer = new euphy.upo.sentrymechanicalarm.client.EmptyCasingRenderer();
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() { return renderer; }
        });
    }
}
