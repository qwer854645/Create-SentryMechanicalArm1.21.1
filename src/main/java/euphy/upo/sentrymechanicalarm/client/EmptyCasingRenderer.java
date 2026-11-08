package euphy.upo.sentrymechanicalarm.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.index.ClientAmmoIndex;
import euphy.upo.sentrymechanicalarm.content.EmptyCasingItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import java.util.*;

/** Reuse the pack's actual empty-shell mesh/UV; never substitute its complete bullet icon. */
public final class EmptyCasingRenderer extends BlockEntityWithoutLevelRenderer {
    private static final com.tacz.guns.client.model.SlotModel ICON_MODEL=new com.tacz.guns.client.model.SlotModel();
    private record Fit(double x,double y,double z,double scale) {}
    private static final Map<ClientAmmoIndex,Fit> fits=new WeakHashMap<>();
    private static final Set<ClientAmmoIndex> failed=Collections.newSetFromMap(new WeakHashMap<>());
    public EmptyCasingRenderer(){super(null,null);}
    public static void clear(){fits.clear();failed.clear();CasingAppearance.clear();}
    public static boolean hasPackShell(ResourceLocation id){
        var index=TimelessAPI.getClientAmmoIndex(id).orElse(null);
        return index!=null&&index.getShellModel()!=null&&index.getShellTextureLocation()!=null
                &&Minecraft.getInstance().getResourceManager().getResource(index.getShellTextureLocation()).isPresent();
    }
    private static Fit fit(ResourceLocation ammo,ClientAmmoIndex index){
        return fits.computeIfAbsent(index,key->{
            try {
                var common=TimelessAPI.getCommonAmmoIndex(ammo).orElseThrow();
                var display=ClientAssetsManager.INSTANCE.getAmmoDisplay(common.getPojo().getDisplay());
                var modelId=display.getShellDisplay().getModelLocation();
                var file=ResourceLocation.fromNamespaceAndPath(modelId.getNamespace(),"geo_models/"+modelId.getPath()+".json");
                try(var reader=Minecraft.getInstance().getResourceManager().getResource(file).orElseThrow().openAsReader()){
                    var json=JsonParser.parseReader(reader).getAsJsonObject();
                    if(!json.has("minecraft:geometry"))return new Fit(0,0,0,6);
                    double[] min={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY};
                    double[] max={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
                    for(var geometry:json.getAsJsonArray("minecraft:geometry"))for(var bone:geometry.getAsJsonObject().getAsJsonArray("bones")){
                        var b=bone.getAsJsonObject();if(!b.has("cubes"))continue;
                        for(var element:b.getAsJsonArray("cubes")){
                            var cube=element.getAsJsonObject();var origin=cube.getAsJsonArray("origin");var size=cube.getAsJsonArray("size");
                            for(int axis=0;axis<3;axis++){double o=origin.get(axis).getAsDouble(),s=size.get(axis).getAsDouble();min[axis]=Math.min(min[axis],Math.min(o,o+s));max[axis]=Math.max(max[axis],Math.max(o,o+s));}
                        }
                    }
                    double dimension=Math.max(max[0]-min[0],Math.max(max[1]-min[1],max[2]-min[2]));
                    if(!Double.isFinite(dimension)||dimension<=0||dimension>256)return new Fit(0,0,0,6);
                    return new Fit((min[0]+max[0])/32,(min[1]+max[1])/32,(min[2]+max[2])/32,Math.clamp(10/dimension,.1,24));
                }
            }catch(Exception e){return new Fit(0,0,0,6);}
        });
    }
    @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        var id=EmptyCasingItem.ammoId(stack);var index=id==null?null:TimelessAPI.getClientAmmoIndex(id).orElse(null);
        var icon=CasingAppearance.get(id);
        if(icon!=null&&(context==ItemDisplayContext.GUI||!hasPackShell(id))){
            pose.pushPose();
            try{
                pose.translate(.5,1.5,.5);pose.mulPose(Axis.ZN.rotationDegrees(180));
                ICON_MODEL.renderToBuffer(pose,buffers.getBuffer(RenderType.entityTranslucent(icon.texture())),light,overlay,net.minecraft.util.FastColor.ARGB32.color((int)(255*com.tacz.guns.client.renderer.item.ItemPreviewRenderContext.alpha()),255,255,255));
            }finally{pose.popPose();}
            return;
        }
        if(index!=null&&!failed.contains(index)){
            pose.pushPose();
            try {
                var model=index.getShellModel();var texture=index.getShellTextureLocation();
                if(model!=null&&texture!=null&&hasPackShell(id)){
                    var fit=fit(id,index);pose.translate(.5,.5,.5);
                    if(context==ItemDisplayContext.GUI){pose.mulPose(Axis.YP.rotationDegrees(-35));pose.mulPose(Axis.XP.rotationDegrees(25));}
                    pose.scale((float)-fit.scale(),(float)-fit.scale(),(float)fit.scale());
                    pose.translate(-fit.x(),-1.5+fit.y(),-fit.z());
                    model.render(pose,context,RenderType.entityCutout(texture),light,overlay,buffers);
                    return;
                }
            }catch(Exception e){failed.add(index);euphy.upo.sentrymechanicalarm.SentryMechanicalArm.LOGGER.warn("Using generic casing for {}: {}",id,e.toString());}
            finally{pose.popPose();}
        }
        renderFallback(pose,buffers,light,overlay);
    }
    private static void renderFallback(PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        var out=buffers.getBuffer(RenderType.entityCutout(ResourceLocation.withDefaultNamespace("textures/block/gold_block.png")));
        // Hollow, open-ended brass shell. No copied or generated third-party bitmap assets.
        float a=.34f,b=.66f,c=.39f,d=.61f,lo=.22f,hi=.78f;
        float[][][] quads={{{a,lo,a},{b,lo,a},{b,hi,a},{a,hi,a}},{{b,lo,b},{a,lo,b},{a,hi,b},{b,hi,b}},{{a,lo,b},{a,lo,a},{a,hi,a},{a,hi,b}},{{b,lo,a},{b,lo,b},{b,hi,b},{b,hi,a}},{{a,lo,b},{b,lo,b},{b,lo,a},{a,lo,a}},{{c,lo+.04f,c},{c,hi,c},{d,hi,c},{d,lo+.04f,c}},{{d,lo+.04f,d},{d,hi,d},{c,hi,d},{c,lo+.04f,d}},{{c,lo+.04f,d},{c,hi,d},{c,hi,c},{c,lo+.04f,c}},{{d,lo+.04f,c},{d,hi,c},{d,hi,d},{d,lo+.04f,d}},{{a,hi,a},{b,hi,a},{d,hi,c},{c,hi,c}},{{b,hi,a},{b,hi,b},{d,hi,d},{d,hi,c}},{{b,hi,b},{a,hi,b},{c,hi,d},{d,hi,d}},{{a,hi,b},{a,hi,a},{c,hi,c},{c,hi,d}}};
        for(var quad:quads){
            org.joml.Vector3f p0=new org.joml.Vector3f(quad[0]),p1=new org.joml.Vector3f(quad[1]),p2=new org.joml.Vector3f(quad[2]);
            var normal=p1.sub(p0,new org.joml.Vector3f()).cross(p2.sub(p0,new org.joml.Vector3f())).normalize();
            for(int v=0;v<4;v++)out.addVertex(pose.last(),quad[v][0],quad[v][1],quad[v][2]).setColor(-1).setUv(v==1||v==2?1:0,v>=2?1:0).setOverlay(overlay).setLight(light).setNormal(pose.last(),normal.x,normal.y,normal.z);
        }
    }
}
