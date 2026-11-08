package euphy.upo.sentrymechanicalarm.smoke;

import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Real Ponder UI, block entity restoration and every instruction in all nine scenes. */
@net.neoforged.fml.common.EventBusSubscriber(modid="sentrymechanicalarm",value=net.neoforged.api.distmarker.Dist.CLIENT)
public final class PonderRenderSmoke {
    private static List<PonderScene> scenes;
    private static int index,passed,failed;
    private static boolean complete;
    private static Preview preview;
    private static int loggedRenderErrors;
    private static org.apache.logging.log4j.core.appender.AbstractAppender listener;
    static boolean run() {
        if(complete)return true;
        var mc=Minecraft.getInstance();
        if(scenes==null) {
            listener=new org.apache.logging.log4j.core.appender.AbstractAppender("PonderRenderProbe",null,
                    org.apache.logging.log4j.core.layout.PatternLayout.createDefaultLayout(),false,
                    org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY) {
                @Override public void append(org.apache.logging.log4j.core.LogEvent event) {
                    var message=event.getMessage().getFormattedMessage();
                    if(message.startsWith("Failed to render SentryArmBlockEntity")
                            ||message.startsWith("Tried to load invalid item")&&message.contains("minecraft:air"))loggedRenderErrors++;
                }
            };
            listener.start();((org.apache.logging.log4j.core.Logger)org.apache.logging.log4j.LogManager.getRootLogger()).addAppender(listener);
            var unique=new LinkedHashMap<ResourceLocation,PonderScene>();
            for(var component:List.of(SentryRegistry.SENTRY_ARM_ITEM.getId(),SentryRegistry.BLAZE_FIRE_CONTROL.getId(),
                    SentryRegistry.CASING_MOLD.getId(),SentryRegistry.SENTRY_SCOPE.getId()))
                for(var scene:PonderIndex.getSceneAccess().compile(component))unique.putIfAbsent(scene.getId(),scene);
            scenes=new ArrayList<>(unique.values());
            if(scenes.size()!=9)throw new AssertionError("Expected nine Ponder scenes, got "+unique.keySet());
        }
        if(preview==null) {
            if(index==scenes.size()) {
                complete=true;mc.setScreen(null);
                ((org.apache.logging.log4j.core.Logger)org.apache.logging.log4j.LogManager.getRootLogger()).removeAppender(listener);
                listener.stop();
                if(loggedRenderErrors>0){failed++;SentryMechanicalArm.LOGGER.error("PONDER FAIL {} internally caught render errors",loggedRenderErrors);}
                SentryMechanicalArm.LOGGER.info("PONDER RESULT passed={} failed={}",passed,failed);
                ClientGeometrySmoke.extraFailures+=failed;
                return true;
            }
            try {
                var scene=scenes.get(index);
                scene.begin();
                var rendered=new HashSet<net.minecraft.world.level.block.entity.BlockEntity>();
                scene.getWorld().getRenderedBlockEntities().forEach(rendered::add);
                var b=scene.getBounds();int count=0;
                for(var pos:BlockPos.betweenClosed(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ())) {
                    var state=scene.getWorld().getBlockState(pos);
                    if(!state.hasBlockEntity())continue;
                    var entity=scene.getWorld().getBlockEntity(pos);
                    // Straight Create tracks intentionally have no BE (turn=false).
                    if(entity==null&&state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock block
                            &&block.newBlockEntity(pos,state)==null)continue;
                    if(entity==null||!rendered.contains(entity))throw new AssertionError("Unrendered BE "+state+" at "+pos);
                    if(entity instanceof euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity arm
                            &&arm.getHeldItem().getItem() instanceof com.tacz.guns.api.item.IGun gun
                            &&com.tacz.guns.api.TimelessAPI.getClientGunIndex(gun.getGunId(arm.getHeldItem())).isEmpty())
                        throw new AssertionError("Missing Ponder gun model "+gun.getGunId(arm.getHeldItem())+" at "+pos);
                    count++;
                }
                SentryMechanicalArm.LOGGER.info("PONDER READY {} rendered block entities={}",scene.getId(),count);
                preview=new Preview(scene);
                mc.setScreen(preview);
            } catch(Throwable error){failed++;SentryMechanicalArm.LOGGER.error("PONDER FAIL scene "+scenes.get(index).getId(),error);index++;preview=null;}
        } else if(preview.finished) {index++;preview=null;}
        return false;
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void frame(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) {
        if(preview!=null&&!preview.finished&&Minecraft.getInstance().screen==preview)preview.captureFrame();
    }
    private static final class Preview extends PonderUI {
        final PonderScene scene;
        boolean finished,captured;
        int ticks;
        Preview(PonderScene scene)throws Exception {
            super(List.of(scene));this.scene=scene;
            var subject=PonderUI.class.getDeclaredField("stack");subject.setAccessible(true);
            subject.set(this,new ItemStack(SentryRegistry.SENTRY_ARM_ITEM.get()));
        }
        @Override public void tick() {
            if(finished)return;
            try {
                super.tick();
                for(int n=0;n<19&&scene.getCurrentTime()<scene.getTotalTime();n++)scene.tick();
                if(++ticks>200)throw new AssertionError("Playback timed out "+scene.getCurrentTime()+"/"+scene.getTotalTime());
            }catch(Throwable error){fail(error);}
        }
        @Override protected void renderWindow(GuiGraphics graphics,int x,int y,float partial) {
            if(finished)return;
            try {
                super.renderWindow(graphics,x,y,partial);
            }catch(Throwable error){fail(error);}
        }
        void captureFrame() {
            try {
                // Ponder submits its world after renderWindow. Capture after the full frame.
                if(!captured&&scene.getCurrentTime()>scene.getTotalTime()*.6) {
                    try(var shot=net.minecraft.client.Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                        var file=java.nio.file.Path.of("ponder-screens",scene.getId().getPath().replace('/','_')+".png");
                        java.nio.file.Files.createDirectories(file.getParent());shot.writeToFile(file);
                    }
                    captured=true;
                }
                if(scene.getCurrentTime()>=scene.getTotalTime()-1) {
                    if(!captured)throw new AssertionError("No rendered frame captured");
                    passed++;finished=true;
                    SentryMechanicalArm.LOGGER.info("PONDER PASS playback and rendering {} ticks={}",scene.getId(),scene.getCurrentTime());
                }
            }catch(Throwable error){fail(error);}
        }
        private void fail(Throwable error){if(finished)return;failed++;finished=true;SentryMechanicalArm.LOGGER.error("PONDER FAIL playback/render "+scene.getId(),error);}
    }
}
