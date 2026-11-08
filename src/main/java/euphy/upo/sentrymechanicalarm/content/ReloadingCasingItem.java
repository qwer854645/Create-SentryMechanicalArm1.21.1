package euphy.upo.sentrymechanicalarm.content;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class ReloadingCasingItem extends EmptyCasingItem {
    public ReloadingCasingItem(Properties properties){super(properties);}
    @Override public Component getName(ItemStack stack){return ammoId(stack)==null?Component.translatable("item.sentrymechanicalarm.reloading_casing"):Component.translatable("item.sentrymechanicalarm.reloading_casing.named",super.getName(stack));}
}
