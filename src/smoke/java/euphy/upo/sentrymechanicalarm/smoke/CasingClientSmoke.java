package euphy.upo.sentrymechanicalarm.smoke;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.client.EmptyCasingRenderer;
import euphy.upo.sentrymechanicalarm.content.EmptyCasingItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class CasingClientSmoke {
    static int frames;static boolean captured,started;static int failures;
    static final List<ItemStack> samples=new ArrayList<>();
    static boolean preview(){
        var mc=Minecraft.getInstance();
        if(!started){
            started=true;int nativeModels=0,missingTextures=0;
            var renderer=new EmptyCasingRenderer();
            for(var entry:TimelessAPI.getAllClientAmmoIndex().stream().sorted(Map.Entry.comparingByKey()).toList()){
                var casing=EmptyCasingItem.create(entry.getKey(),1);samples.add(casing);
                try {
                    var texture=entry.getValue().getShellTextureLocation();
                    if(texture!=null&&mc.getResourceManager().getResource(texture).isEmpty()){
                        missingTextures++;
                        if(EmptyCasingRenderer.hasPackShell(entry.getKey()))throw new AssertionError("missing texture accepted as native shell");
                        SentryMechanicalArm.LOGGER.info("CASING_CLIENT FALLBACK missing texture ammo={} texture={}",entry.getKey(),texture);
                    }
                    if(EmptyCasingRenderer.hasPackShell(entry.getKey()))nativeModels++;
                    for(var context:List.of(ItemDisplayContext.GUI,ItemDisplayContext.GROUND,ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)){
                        var pose=new PoseStack();pose.translate(.1,.2,.3);var before=new org.joml.Matrix4f(pose.last().pose());
                        renderer.renderByItem(casing,context,pose,mc.renderBuffers().bufferSource(),15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                        if(!pose.last().pose().equals(before))throw new AssertionError("caller matrix changed");
                    }
                }catch(Throwable e){failures++;SentryMechanicalArm.LOGGER.error("CASING_CLIENT FAIL "+entry.getKey(),e);}
            }
            samples.add(EmptyCasingItem.create(ResourceLocation.parse("removed_pack:ammo"),1));
            if(nativeModels<15){failures++;SentryMechanicalArm.LOGGER.error("CASING_CLIENT FAIL insufficient real pack shell models {}",nativeModels);}
            SentryMechanicalArm.LOGGER.info("CASING_CLIENT models={} samples={} missingTextures={} failed={}",nativeModels,samples.size(),missingTextures,failures);
            mc.setScreen(new Preview());return false;
        }
        if(!captured&&frames>=8&&mc.getOverlay()==null){
            try(var screenshot=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){
                var file=java.nio.file.Path.of("../../evidence/"+(Boolean.getBoolean("sentry.gunpackClientSmoke")?"gunpacks-casing-preview.png":"alpha11-casing-preview.png"));java.nio.file.Files.createDirectories(file.getParent());screenshot.writeToFile(file);
                SentryMechanicalArm.LOGGER.info("CASING_CLIENT RESULT failed={} screenshot={}",failures,file.toAbsolutePath());
            }catch(Exception e){failures++;SentryMechanicalArm.LOGGER.error("CASING_CLIENT FAIL screenshot",e);}
            captured=true;mc.setScreen(null);
        }
        return captured;
    }
    static final class Preview extends Screen {
        Preview(){super(Component.literal("Automatic gun-pack casing preview"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics gui,int mouseX,int mouseY,float partialTick){
            gui.fill(0,0,width,height,0xff18212c);
            gui.drawString(font,"Automatic casings - native TaCZ shell models / textures",12,10,0xffffff);
            int cols=7,cellW=Math.min(100,(width-20)/cols),cellH=Math.min(62,(height-35)/((samples.size()+cols-1)/cols));
            float scale=Math.min(2.5f,(cellH-15)/16f);
            for(int i=0;i<samples.size();i++){
                int x=10+(i%cols)*cellW,y=30+(i/cols)*cellH;
                gui.fill(x,y,x+cellW-3,y+cellH-3,0xff293746);
                gui.pose().pushPose();gui.pose().translate(x+cellW/2f-8*scale,y+3,0);gui.pose().scale(scale,scale,1);gui.renderItem(samples.get(i),0,0);gui.pose().popPose();
                String id=EmptyCasingItem.ammoId(samples.get(i)).getPath();if(id.length()>14)id=id.substring(0,13)+"~";
                gui.pose().pushPose();gui.pose().translate(x+3,y+cellH-11,0);gui.pose().scale(.65f,.65f,1);gui.drawString(font,id,0,0,0xd5dce5,false);gui.pose().popPose();
            }
            gui.flush();frames++;
        }
    }
}
