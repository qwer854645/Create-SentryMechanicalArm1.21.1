package euphy.upo.sentrymechanicalarm.smoke;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.renderer.item.GunItemTransforms;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.util.SentryGunProfiles;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector4f;

@EventBusSubscriber(modid=SentryMechanicalArm.MODID,value=Dist.CLIENT)
public final class ClientGeometrySmoke {
    private static int ticks;
    private static boolean done;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("sentry.clientSmoke") || done) return;
        var mc=Minecraft.getInstance();
        if (mc.level==null || SentryGunProfiles.clientSnapshot().isEmpty()) return;
        if (++ticks<60) return;
        done=true; int passed=0,failed=0,skipped=0; double maxError=0;
        for (var entry:TimelessAPI.getAllClientGunIndex()) {
            var profile=SentryGunProfiles.get(entry.getKey());
            if (!profile.fromModel()) { skipped++; continue; }
            try {
                var display=entry.getValue().getDefaultDisplay();
                var model=display.getGunModel(); model.cleanAnimationTransform();
                PoseStack pose=new PoseStack();
                // Vanilla ItemRenderer shifts into custom item-renderer coordinates.
                pose.translate(-.5,-.5,-.5);
                GunItemTransforms.apply(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,display.getTransform().getScale(),model,pose);
                for (var part:model.getMuzzleFlashPosPath()) part.translateAndRotateAndScale(pose);
                Vector4f point=new Vector4f(0,0,0,1); pose.last().pose().transform(point);
                double error=new Vec3(point.x,point.y,point.z).distanceTo(profile.muzzle());
                maxError=Math.max(maxError,error);
                if (error>1e-4) throw new AssertionError("Model/profile mismatch="+error);
                passed++;
            } catch(Throwable e) { failed++; SentryMechanicalArm.LOGGER.error("PHASE3_CLIENT FAIL "+entry.getKey(),e); }
        }
        if(passed==0) failed++;
        try {
            verifyMovingMount();
            SentryMechanicalArm.LOGGER.info("COMPAT_CLIENT PASS Create render matrices agree with moving muzzle (floor, ceiling, minigun)");
        } catch(Throwable e){failed++;SentryMechanicalArm.LOGGER.error("COMPAT_CLIENT FAIL moving mount",e);}
        SentryMechanicalArm.LOGGER.info("PHASE3_CLIENT RESULT passed={} failed={} skipped={} maxModelError={} receivedProfiles={}",
                passed,failed,skipped,maxError,SentryGunProfiles.clientSnapshot().size());
        mc.stop();
    }
    private static void verifyMovingMount() throws Exception {
        var mc=Minecraft.getInstance();
        var entity=new com.simibubi.create.content.contraptions.ControlledContraptionEntity(com.simibubi.create.AllEntityTypes.CONTROLLED_CONTRAPTION.get(),mc.level);
        entity.setPos(12,160,24);entity.xo=entity.xOld=12;entity.yo=entity.yOld=160;entity.zo=entity.zOld=24;
        entity.setRotationAxis(net.minecraft.core.Direction.Axis.Y);entity.setAngle(38);
        var previous=entity.getClass().getDeclaredField("prevAngle");previous.setAccessible(true);previous.setFloat(entity,38);
        var matrices=new com.simibubi.create.content.contraptions.render.ContraptionMatrices();
        var setup=matrices.getClass().getDeclaredMethod("setup",PoseStack.class,com.simibubi.create.content.contraptions.AbstractContraptionEntity.class);setup.setAccessible(true);setup.invoke(matrices,new PoseStack(),entity);
        var local=new net.minecraft.core.BlockPos(3,1,-2);
        matrices.getModel().translate(local.getX(),local.getY(),local.getZ());
        var actor=new org.joml.Matrix4f(matrices.getWorld()).mul(matrices.getModel().last().pose());
        // Create deliberately nudges render geometry to avoid Z-fighting; physics has no nudge.
        var nudgePose=new PoseStack();dev.engine_room.flywheel.lib.transform.TransformStack.of(nudgePose).nudge(entity.getId());
        var nudge=nudgePose.last().pose().getTranslation(new org.joml.Vector3f());
        float pt=net.createmod.catnip.animation.AnimationTickHolder.getPartialTicks();
        for(boolean ceiling:new boolean[]{false,true})for(String gun:new String[]{"tacz:ak47","tacz:minigun"}){
            var profile=SentryGunProfiles.get(net.minecraft.resources.ResourceLocation.parse(gun));
            var mount=new org.joml.Matrix4f(euphy.upo.sentrymechanicalarm.util.SentryGunGeometry.mount(ceiling,123,135,90,12,profile.minigunMount()));
            var rendered=new org.joml.Vector3f((float)profile.muzzle().x,(float)profile.muzzle().y,(float)profile.muzzle().z);
            new org.joml.Matrix4f(actor).mul(mount).transformPosition(rendered);
            var pose=euphy.upo.sentrymechanicalarm.util.SentryGunGeometry.pose(local,ceiling,123,135,90,12,profile);
            var expected=entity.toGlobalVector(pose.position(),pt).add(nudge.x,nudge.y,nudge.z);
            double error=new Vec3(rendered).distanceTo(expected);
            SentryMechanicalArm.LOGGER.info("COMPAT_CLIENT METRIC {} ceiling={} error={}",gun,ceiling,error);
            // Create's CPU VecHelper uses Minecraft trig tables; rendering uses float quaternions.
            if(error>1e-3)throw new AssertionError("Create render position mismatch "+gun+" error="+error+" expected="+expected+" actual="+rendered.x+","+rendered.y+","+rendered.z);
        }
    }
}
