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
        int w=source.getWidth(),h=source.getHeight(),left=w,right=-1,top=h,bottom=-1;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)if((source.getPixelRGBA(x,y)>>>24)>=32){left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);}
        if(right<left)throw new IllegalArgumentException("Empty ammo icon");
        // Slot art commonly has a separate wide caliber caption below the cartridges.
        // Cut at the first pronounced widening of the silhouette, if there is one.
        int previous=0,caption=-1;
        for(int y=top;y<=bottom;y++){
            int rowLeft=w,rowRight=-1,ink=0;
            for(int x=0;x<w;x++)if((source.getPixelRGBA(x,y)>>>24)>=32){rowLeft=Math.min(rowLeft,x);rowRight=Math.max(rowRight,x);ink++;}
            int span=Math.max(0,rowRight-rowLeft+1);
            if(y>=top+4&&previous>0&&ink>=previous*1.4&&span>=(right-left+1)*.65){caption=y;break;}
            if(ink>0)previous=ink;
        }
        if(caption>top+3)bottom=caption-1;
        while(bottom>top){boolean ink=false;for(int x=0;x<w;x++)if((source.getPixelRGBA(x,bottom)>>>24)>=32){ink=true;break;}if(ink)break;bottom--;}
        top+=Math.min(bottom-top,(int)Math.round((bottom-top+1)*.30));
        left=w;right=-1;
        for(int y=top;y<=bottom;y++)for(int x=0;x<w;x++)if((source.getPixelRGBA(x,y)>>>24)>=32){left=Math.min(left,x);right=Math.max(right,x);}
        if(right<left)throw new IllegalArgumentException("Empty cropped icon");
        int width=right-left+1,height=bottom-top+1;
        // Transparent square canvas; nearest-neighbor copying preserves every source color.
        int size=Math.max(width,height)+4;
        var result=new NativeImage(size,size,true);
        int dx=(size-width)/2,dy=(size-height)/2;
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)result.setPixelRGBA(dx+x,dy+y,source.getPixelRGBA(left+x,top+y));
        return result;
    }
}
