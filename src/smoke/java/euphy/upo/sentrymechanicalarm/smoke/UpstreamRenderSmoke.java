package euphy.upo.sentrymechanicalarm.smoke;

import com.mojang.blaze3d.vertex.*;
import com.tacz.guns.api.TimelessAPI;
import euphy.upo.sentrymechanicalarm.*;
import euphy.upo.sentrymechanicalarm.content.*;
import euphy.upo.sentrymechanicalarm.network.*;
import euphy.upo.sentrymechanicalarm.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Small real-render-context checks for the reported shared-buffer crashes. */
final class UpstreamRenderSmoke {
    static int passed, failed;
    interface Check { void run() throws Exception; }
    static void test(String name, Check body) {
        try { body.run(); passed++; SentryMechanicalArm.LOGGER.info("RENDER42 PASS {}", name); }
        catch (Throwable e) { failed++; SentryMechanicalArm.LOGGER.error("RENDER42 FAIL " + name, e); }
    }
    static int verify() {
        var mc = Minecraft.getInstance();
        test("shell animation switch prevents cosmetic shell creation", () -> {
            var field = SentryShellManager.class.getDeclaredField("shells"); field.setAccessible(true);
            var shells = (java.util.List<?>) field.get(null); shells.clear();
            try {
                SMAClientConfig.SHELL_ANIMATIONS.set(false);
                SentryShellManager.addShell(ResourceLocation.parse("tacz:9mm"), Vec3.ZERO, new Vector3f(), new Vector3f(), new Vector3f(), 1);
                if (!shells.isEmpty()) throw new AssertionError("disabled cosmetic shell created");
                SMAClientConfig.SHELL_ANIMATIONS.set(true);
                SentryShellManager.addShell(ResourceLocation.parse("tacz:9mm"), Vec3.ZERO, new Vector3f(), new Vector3f(), new Vector3f(), 1);
                if (shells.size() != 1) throw new AssertionError("enabled shell missing");
            } finally { shells.clear(); SMAClientConfig.SHELL_ANIMATIONS.set(true); }
        });
        test("supplemental pellets create individual tracers without shot effects", () -> {
            var id = TimelessAPI.getAllCommonGunIndex().stream()
                    .map(java.util.Map.Entry::getKey)
                    .filter(key -> {
                        var stack = com.tacz.guns.api.item.builder.GunItemBuilder.create().setId(key).build();
                        return SentryGunProfiles.needsSyntheticTracer(stack);
                    }).findFirst().orElseThrow();
            var gun = com.tacz.guns.api.item.builder.GunItemBuilder.create().setId(id).build();
            var field = SentryTrailManager.class.getDeclaredField("tracers"); field.setAccessible(true);
            var traces = (java.util.List<?>) field.get(null); SentryTrailManager.clear();
            try {
                for (int n = 0; n < 4; n++) ClientPacketHandler.handleSentryContraptionShoot(new SentryContraptionShootPacket(
                        -1, BlockPos.ZERO, new Vec3(0, 100, 0), new Vec3(n, 100, 20), gun, SentryShootPacket.ActionType.TRACER));
                if (traces.size() != 4) throw new AssertionError("pellets merged or lost: " + traces.size());
            } finally { SentryTrailManager.clear(); }
        });
        test("legacy muzzle quads preserve caller buffer in both render modes", () -> {
            var source = mc.renderBuffers().bufferSource();
            var held = source.getBuffer(RenderType.solid());
            quad(held);
            var method = SentryArmRenderer.class.getDeclaredMethod("renderFlashQuads", PoseStack.class, ResourceLocation.class, boolean.class);
            method.setAccessible(true);
            var texture = ResourceLocation.parse("minecraft:textures/block/white_wool.png");
            try {
                for (boolean glow : new boolean[] {false, true}) {
                    method.invoke(null, new PoseStack(), texture, glow);
                    quad(held); // BufferBuilder not started is thrown here if an effect flushes its caller.
                }
            } finally { source.endBatch(); }
        });
        SentryMechanicalArm.LOGGER.info("RENDER42 RESULT passed={} failed={}", passed, failed);
        return failed;
    }
    static void quad(VertexConsumer consumer) {
        for (int n = 0; n < 4; n++) consumer.addVertex((n == 1 || n == 2) ? 1 : 0, n >= 2 ? 1 : 0, 0)
                .setColor(255, 255, 255, 255).setUv(0, 0).setLight(15728880).setNormal(0, 0, 1);
    }
}
