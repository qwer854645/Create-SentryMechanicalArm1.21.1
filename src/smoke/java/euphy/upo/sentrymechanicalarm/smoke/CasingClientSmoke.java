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
            try(var original=new com.mojang.blaze3d.platform.NativeImage(16,24,true)){
                for(int y=1;y<15;y++)for(int x=5;x<11;x++)original.setPixelRGBA(x,y,y<5?0xff0000ff:0xff3399cc);
                for(int y=19;y<21;y++)for(int x=1;x<15;x++)original.setPixelRGBA(x,y,0xffff0000);
                try(var cropped=euphy.upo.sentrymechanicalarm.client.CasingAppearance.crop(original)){
                    int kept=0;
                    for(int y=0;y<cropped.getHeight();y++)for(int x=0;x<cropped.getWidth();x++){
                        int pixel=cropped.getPixelRGBA(x,y);if((pixel>>>24)==0)continue;
                        if(pixel!=0xff3399cc)throw new AssertionError("crop retained projectile/label or recolored source");kept++;
                    }
                    if(kept!=54)throw new AssertionError("case body pixels lost: "+kept);
                }
                SentryMechanicalArm.LOGGER.info("CASING_CLIENT PASS crop removes projectile and label, retains exact body pixels");
            }catch(Throwable e){failures++;SentryMechanicalArm.LOGGER.error("CASING_CLIENT FAIL crop",e);}
            try{
                var tab=euphy.upo.sentrymechanicalarm.registry.SentryRegistry.CASINGS_TAB.get();
                if(!ResourceLocation.parse("tacz:9mm").equals(EmptyCasingItem.ammoId(tab.getIconItem())))throw new AssertionError("tab icon is not 9mm casing");
                if(euphy.upo.sentrymechanicalarm.util.SentryCasingProfiles.clientSnapshot().isEmpty())throw new AssertionError("no casing rules received from server");
                tab.buildContents(new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(mc.level.enabledFeatures(),true,mc.level.registryAccess()));
                if(tab.getDisplayItems().size()!=euphy.upo.sentrymechanicalarm.util.SentryCasingProfiles.clientSnapshot().size())throw new AssertionError("creative tab does not match synchronized rules");
                for(var stack:tab.getDisplayItems())if(!euphy.upo.sentrymechanicalarm.util.SentryCasingProfiles.clientSnapshot().contains(EmptyCasingItem.ammoId(stack)))throw new AssertionError("ineligible creative casing");
                var tabs=net.neoforged.neoforge.common.CreativeModeTabRegistry.getSortedCreativeModeTabs();
                int ammo=tabs.indexOf(com.tacz.guns.init.ModCreativeTabs.AMMO_TAB.get()),casings=tabs.indexOf(tab);
                if(ammo<0||Math.abs(casings-ammo)!=1)throw new AssertionError("casing tab not adjacent to ammo: "+ammo+" / "+casings);
                SentryMechanicalArm.LOGGER.info("CASING_CLIENT PASS 9mm tab icon and immediate placement next to TaCZ ammo");
            }catch(Throwable e){failures++;SentryMechanicalArm.LOGGER.error("CASING_CLIENT FAIL creative tab",e);}
            var renderer=new EmptyCasingRenderer();
            try{
                var holder=euphy.upo.sentrymechanicalarm.recipe.ReloadingRecipes.find(mc.level.getRecipeManager(),ResourceLocation.parse("tacz:9mm"));
                if(holder.isPresent()){
                    var recipe=(com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe)holder.get().value();var progress=recipe.getTransitionalItem().copy();
                    progress.set(com.simibubi.create.AllDataComponents.SEQUENCED_ASSEMBLY,new com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe.SequencedAssembly(holder.get().id(),1,1f/recipe.getSequence().size()));
                    if(!ResourceLocation.parse("tacz:9mm").equals(EmptyCasingItem.ammoId(progress)))throw new AssertionError("progress item lost ammo identity");
                    for(var context:List.of(ItemDisplayContext.GUI,ItemDisplayContext.GROUND,ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,ItemDisplayContext.THIRD_PERSON_RIGHT_HAND))renderer.renderByItem(progress,context,new PoseStack(),mc.renderBuffers().bufferSource(),15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                    SentryMechanicalArm.LOGGER.info("RELOADING_CLIENT PASS intermediate item caliber, name={}, four render contexts and recipe sync",progress.getHoverName().getString());
                }
            }catch(Throwable e){failures++;SentryMechanicalArm.LOGGER.error("RELOADING_CLIENT FAIL",e);}
            for(var entry:TimelessAPI.getAllClientAmmoIndex().stream().sorted(Map.Entry.comparingByKey()).toList()){
                if(!euphy.upo.sentrymechanicalarm.util.SentryCasingProfiles.creativeEligible(entry.getKey()))continue;
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
            gui.drawString(font,"Automatic casings - TaCZ ammo (left) / cropped casing (right)",12,10,0xffffff);
            int cols=7,cellW=Math.min(100,(width-20)/cols),cellH=Math.min(62,(height-35)/((samples.size()+cols-1)/cols));
            float scale=Math.min(1.8f,(cellH-15)/16f);
            for(int i=0;i<samples.size();i++){
                int x=10+(i%cols)*cellW,y=30+(i/cols)*cellH;
                gui.fill(x,y,x+cellW-3,y+cellH-3,0xff293746);
                var ammoId=EmptyCasingItem.ammoId(samples.get(i));
                var ammo=com.tacz.guns.api.item.builder.AmmoItemBuilder.create().setId(ammoId).build();
                gui.pose().pushPose();gui.pose().translate(x+cellW*.25f-8*scale,y+3,0);gui.pose().scale(scale,scale,1);gui.renderItem(ammo,0,0);gui.pose().popPose();
                gui.pose().pushPose();gui.pose().translate(x+cellW*.73f-8*scale,y+3,0);gui.pose().scale(scale,scale,1);gui.renderItem(samples.get(i),0,0);gui.pose().popPose();
                String id=EmptyCasingItem.ammoId(samples.get(i)).getPath();if(id.length()>14)id=id.substring(0,13)+"~";
                gui.pose().pushPose();gui.pose().translate(x+3,y+cellH-11,0);gui.pose().scale(.65f,.65f,1);gui.drawString(font,id,0,0,0xd5dce5,false);gui.pose().popPose();
            }
            gui.flush();frames++;
        }
    }
}
