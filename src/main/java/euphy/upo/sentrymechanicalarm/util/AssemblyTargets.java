package euphy.upo.sentrymechanicalarm.util;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import euphy.upo.sentrymechanicalarm.recipe.GunAssemblyRecipes.Choice;
import euphy.upo.sentrymechanicalarm.SMAServerConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Common-side target metadata; rendering and display assets remain client-side. */
public final class AssemblyTargets {
    public static boolean attachment(Choice choice){return IAttachment.getIAttachmentOrNull(choice.output())!=null;}
    public static boolean enabled(Choice choice){return attachment(choice)?SMAServerConfig.ATTACHMENT_MANUFACTURING.get():SMAServerConfig.GUN_MANUFACTURING.get();}
    public static Component attachmentName(ResourceLocation id){
        String key=TimelessAPI.getCommonAttachmentIndex(id).map(index->index.getPojo().getName()).orElse(null);
        return key==null||key.isBlank()?Component.literal(GunNames.readable(id)):Component.translatableWithFallback(key,GunNames.readable(id));
    }
    public static String type(Choice choice){
        return attachment(choice)?TimelessAPI.getCommonAttachmentIndex(choice.gun()).map(index->index.getType().getSerializedName()).orElse("none")
                :TimelessAPI.getCommonGunIndex(choice.gun()).map(index->index.getType()).orElse("other");
    }
    public static String description(ResourceLocation id,boolean attachment){
        return attachment?TimelessAPI.getCommonAttachmentIndex(id).map(index->index.getPojo().getTooltip()).orElse(null)
                :TimelessAPI.getCommonGunIndex(id).map(index->index.getPojo().getTooltip()).orElse(null);
    }
}
