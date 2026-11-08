package euphy.upo.sentrymechanicalarm.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.tacz.guns.api.TimelessAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Crop the current pack's ammo slot artwork; retain its original pixels and colors. */
public final class CasingAppearance {
    public record Appearance(ResourceLocation texture) {}
    private static final Map<ResourceLocation,Appearance> cache=new HashMap<>();
    private static final Set<ResourceLocation> missing=new HashSet<>();
    public static void clear(){
        for(var a:cache.values())Minecraft.getInstance().getTextureManager().release(a.texture());
        cache.clear();missing.clear();
    }
    public static Appearance get(ResourceLocation ammo){
        if(ammo==null||missing.contains(ammo))return null;
        var cached=cache.get(ammo);if(cached!=null)return cached;
        var index=TimelessAPI.getClientAmmoIndex(ammo).orElse(null);
        if(index==null||index.getSlotTextureLocation()==null){missing.add(ammo);return null;}
        try(var stream=Minecraft.getInstance().getResourceManager().getResource(index.getSlotTextureLocation()).orElseThrow().open();var source=NativeImage.read(stream)){
            var image=crop(source);
            var texture=Minecraft.getInstance().getTextureManager().register("sentry_casing_crop",new DynamicTexture(image));
            var appearance=new Appearance(texture);cache.put(ammo,appearance);return appearance;
        }catch(Exception e){missing.add(ammo);return null;}
    }
    public static NativeImage crop(NativeImage source){
        int w=source.getWidth(),h=source.getHeight(),top=-1,bottom=-1;
        // Original simple crop: retain the first continuous artwork band intact.
        for(int y=0;y<h;y++){
            boolean opaque=false;for(int x=0;x<w;x++)if((source.getPixelRGBA(x,y)>>>24)>=32){opaque=true;break;}
            if(top<0){if(opaque){top=y;bottom=y;}}
            else if(opaque)bottom=y;else break;
        }
        if(top<0)throw new IllegalArgumentException("Empty ammo icon");
        top+=Math.min(bottom-top,(int)Math.round((bottom-top+1)*.38));
        int left=w,right=-1;
        for(int y=top;y<=bottom;y++)for(int x=0;x<w;x++)if((source.getPixelRGBA(x,y)>>>24)>=32){left=Math.min(left,x);right=Math.max(right,x);}
        if(right<left)throw new IllegalArgumentException("Empty cropped ammo icon");
        int width=right-left+1,height=bottom-top+1;
        // Transparent square canvas; nearest-neighbor copying preserves every source color.
        int size=Math.max(width,height)+4;
        var result=new NativeImage(size,size,true);
        int dx=(size-width)/2,dy=(size-height)/2;
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)result.setPixelRGBA(dx+x,dy+y,source.getPixelRGBA(left+x,top+y));
        return result;
    }
}
