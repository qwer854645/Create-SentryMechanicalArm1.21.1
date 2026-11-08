package euphy.upo.sentrymechanicalarm.client;

import com.tacz.guns.client.resource.ClientAssetsManager;
import euphy.upo.sentrymechanicalarm.content.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import java.util.List;

public final class CasingTooltips {
    public static void append(ItemStack stack,List<Component> lines,TooltipFlag flag){
        var id=EmptyCasingItem.ammoId(stack);
        lines.add(Component.translatable(stack.getItem() instanceof ReloadingCasingItem?"item.sentrymechanicalarm.reloading_casing.description":"item.sentrymechanicalarm.empty_casing.description").withStyle(ChatFormatting.GRAY));
        if(id==null)return;
        var pack=ClientAssetsManager.INSTANCE.getPackInfo(id);
        if(pack!=null)lines.add(Component.translatable(pack.getName()).withStyle(ChatFormatting.BLUE,ChatFormatting.ITALIC));
        if(flag.isAdvanced())lines.add(Component.translatable("item.sentrymechanicalarm.casing.ammo_id",id.toString()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
