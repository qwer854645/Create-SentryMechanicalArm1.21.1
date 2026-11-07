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
        SentryMechanicalArm.LOGGER.info("PHASE3_CLIENT RESULT passed={} failed={} skipped={} maxModelError={} receivedProfiles={}",
                passed,failed,skipped,maxError,SentryGunProfiles.clientSnapshot().size());
        mc.stop();
    }
}
