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
    static int extraFailures;
    private static int ticks;
    private static boolean done;
    private static java.util.concurrent.CompletableFuture<Void> reload;
    private static Object oldDisplay,oldCasing;
    private static boolean reloadVerified;
    private static int reloadFailures;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("sentry.clientSmoke") || done) return;
        var mc=Minecraft.getInstance();
        if (mc.level==null || SentryGunProfiles.clientSnapshot().isEmpty()) return;
        if (++ticks<60) return;
        if (Boolean.getBoolean("sentry.clientReloadSmoke") && !reloadVerified) {
            try {
                var id=net.minecraft.resources.ResourceLocation.parse("tacz:glock_17");
                var field=euphy.upo.sentrymechanicalarm.network.ClientPacketHandler.class.getDeclaredField("SENTRY_DISPLAYS");field.setAccessible(true);
                var cache=(java.util.Map)field.get(null);
                if (reload==null) {
                    oldCasing=euphy.upo.sentrymechanicalarm.client.CasingAppearance.get(net.minecraft.resources.ResourceLocation.parse("tacz:9mm"));
                    oldDisplay=TimelessAPI.getClientGunIndex(id).orElseThrow().getDefaultDisplay();
                    cache.put(id,oldDisplay);
                    reload=mc.reloadResourcePacks();
                    return;
                }
                if (!reload.isDone()) return;
                reload.join();
                if (euphy.upo.sentrymechanicalarm.client.CasingAppearance.get(net.minecraft.resources.ResourceLocation.parse("tacz:9mm"))==oldCasing) throw new AssertionError("Generated casing icon survived resource reload");
                if (!cache.isEmpty()) throw new AssertionError("Sentry display cache survived resource reload");
                if (TimelessAPI.getClientGunIndex(id).orElseThrow().getDefaultDisplay()==oldDisplay) throw new AssertionError("TaCZ display was not reloaded");
                if (SentryGunProfiles.clientSnapshot().isEmpty()) throw new AssertionError("Server profiles cleared by client-only reload");
                SentryMechanicalArm.LOGGER.info("RELOAD_CLIENT PASS actual resource reload clears sentry displays, refreshes TaCZ models and retains synchronized profiles");
            } catch(Throwable e) {reloadFailures++;SentryMechanicalArm.LOGGER.error("RELOAD_CLIENT FAIL",e);}
            reloadVerified=true;
        }
        if(Boolean.getBoolean("sentry.casingClientSmoke")){try{if(!CasingJeiSmoke.verify())return;}catch(Throwable e){reloadFailures++;SentryMechanicalArm.LOGGER.error("JEI_CASING FAIL",e);}}
        if(Boolean.getBoolean("sentry.casingClientSmoke") && !CasingClientSmoke.preview()) return;
        if(Boolean.getBoolean("sentry.ponderSmoke")&&!PonderRenderSmoke.run())return;
        done=true; int passed=0,failed=reloadFailures+CasingClientSmoke.failures+extraFailures,skipped=0; double maxError=0;
        if(Boolean.getBoolean("sentry.gunpackClientSmoke")) for(String id:java.util.List.of("ars_armorer:mortar_source_thrower","applied_armorer:niklas_pistol_double_win_win")) SentryMechanicalArm.LOGGER.info("GUNPACK_CLIENT PROFILE {} {}",id,SentryGunProfiles.get(net.minecraft.resources.ResourceLocation.parse(id)));
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
        try{verifyCog();SentryMechanicalArm.LOGGER.info("COG_CLIENT PASS center, ceiling, rotation and caller frame (24 poses)");}
        catch(Throwable e){failed++;SentryMechanicalArm.LOGGER.error("COG_CLIENT FAIL",e);}
        try{verifyActorRendererFrame();SentryMechanicalArm.LOGGER.info("COG_CLIENT PASS complete actor renderer preserves caller frame (4 renders)");}
        catch(Throwable e){failed++;SentryMechanicalArm.LOGGER.error("COG_CLIENT FAIL renderer frame",e);}
        try{verifyDepotClient();SentryMechanicalArm.LOGGER.info("DEPOT_CLIENT PASS mounted ledger codec, supply state and translations");}
        catch(Throwable e){failed++;SentryMechanicalArm.LOGGER.error("DEPOT_CLIENT FAIL",e);}
        try{verifyDyeSprites();SentryMechanicalArm.LOGGER.info("DYE_CLIENT PASS 16 colors: baked source sprites and shifted UV bounds");}
        catch(Throwable e){failed++;SentryMechanicalArm.LOGGER.error("DYE_CLIENT FAIL",e);}
        SentryMechanicalArm.LOGGER.info("PHASE3_CLIENT RESULT passed={} failed={} skipped={} maxModelError={} receivedProfiles={}",
                passed,failed,skipped,maxError,SentryGunProfiles.clientSnapshot().size());
        mc.stop();
    }
    private static void verifyDyeSprites() {
        var parts=euphy.upo.sentrymechanicalarm.registry.SentryPartialModels.class;
        for(var color:net.minecraft.world.item.DyeColor.values()) {
            var shifts=euphy.upo.sentrymechanicalarm.util.SentrySpriteShifts.ARM_TEXTURES;
            verifyDyeShift(shifts.get(color),java.util.List.of(
                    euphy.upo.sentrymechanicalarm.registry.SentryPartialModels.ARM_LOWER_BODY,
                    euphy.upo.sentrymechanicalarm.registry.SentryPartialModels.ARM_UPPER_BODY,
                    euphy.upo.sentrymechanicalarm.registry.SentryPartialModels.ARM_CLAW_BASE,
                    euphy.upo.sentrymechanicalarm.registry.SentryPartialModels.ARM_CLAW_GRIP_UPPER,
                    euphy.upo.sentrymechanicalarm.registry.SentryPartialModels.ARM_CLAW_GRIP_LOWER));
            verifyDyeShift(euphy.upo.sentrymechanicalarm.util.SentrySpriteShifts.BASE_TEXTURES.get(color),
                    java.util.List.of(euphy.upo.sentrymechanicalarm.registry.SentryPartialModels.SENTRU_BASE));
            verifyDyeShift(euphy.upo.sentrymechanicalarm.util.SentrySpriteShifts.COG_TEXTURES.get(color),
                    java.util.List.of(euphy.upo.sentrymechanicalarm.registry.SentryPartialModels.SENTRU_COG));
        }
    }
    private static void verifyDyeShift(net.createmod.catnip.render.SpriteShiftEntry shift,
            java.util.List<dev.engine_room.flywheel.lib.model.baked.PartialModel> parts) {
        var target=shift.getTarget();
        if(target==null||target.contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()))
            throw new AssertionError("Missing colored sprite");
        var sides=new java.util.ArrayList<net.minecraft.core.Direction>();sides.add(null);sides.addAll(java.util.List.of(net.minecraft.core.Direction.values()));
        for(var part:parts)for(var side:sides)for(var quad:part.get().getQuads(null,side,net.minecraft.util.RandomSource.create(0))) {
            if(!quad.getSprite().contents().name().equals(shift.getOriginal().contents().name()))
                throw new AssertionError("UV source mismatch "+part.modelLocation()+" uses "+quad.getSprite().contents().name()+" shift assumes "+shift.getOriginal().contents().name());
            var vertices=quad.getVertices();int stride=vertices.length/4;
            for(int vertex=0;vertex<4;vertex++) {
                float u=shift.getTargetU(Float.intBitsToFloat(vertices[vertex*stride+4]));
                float v=shift.getTargetV(Float.intBitsToFloat(vertices[vertex*stride+5]));
                if(u<target.getU0()-1e-5||u>target.getU1()+1e-5||v<target.getV0()-1e-5||v>target.getV1()+1e-5)
                    throw new AssertionError("Shift samples outside colored sprite "+part.modelLocation());
            }
        }
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
    private static void verifyCog(){
        for(boolean ceiling:new boolean[]{false,true})for(int angle:new int[]{0,30,90,150,270,359})for(int moving:new int[]{0,1}){
            var pose=new PoseStack();
            if(moving==1){pose.translate(12,20,-9);pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(17));pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(41));}
            var caller=new org.joml.Matrix4f(pose.last().pose());
            var expected=caller.transformPosition(new org.joml.Vector3f(.5f,.5f,.5f));
            pose.pushPose();
            euphy.upo.sentrymechanicalarm.content.SentryArmRenderer.transformContraptionCog(pose,ceiling,angle);
            var actual=pose.last().pose().transformPosition(new org.joml.Vector3f(.5f,.5f,.5f));
            if(actual.distance(expected)>1e-5)throw new AssertionError("Cog orbits its block center");
            var axis=pose.last().pose().transformDirection(new org.joml.Vector3f(0,1,0));
            var expectedAxis=caller.transformDirection(new org.joml.Vector3f(0,ceiling?-1:1,0));
            if(axis.distance(expectedAxis)>1e-5)throw new AssertionError("Cog axis differs from mount");
            pose.popPose();if(!pose.last().pose().equals(caller))throw new AssertionError("Caller frame changed");
        }
    }
    private static void verifyActorRendererFrame() throws Exception {
        var mc=Minecraft.getInstance();
        if(euphy.upo.sentrymechanicalarm.registry.SentryPartialModels.SENTRU_COG.get()==null)throw new AssertionError("Partial models not ready");
        var entity=new com.simibubi.create.content.contraptions.ControlledContraptionEntity(com.simibubi.create.AllEntityTypes.CONTROLLED_CONTRAPTION.get(),mc.level);
        entity.setRotationAxis(net.minecraft.core.Direction.Axis.Y);entity.setAngle(21);
        var matrices=new com.simibubi.create.content.contraptions.render.ContraptionMatrices();
        var setup=matrices.getClass().getDeclaredMethod("setup",PoseStack.class,com.simibubi.create.content.contraptions.AbstractContraptionEntity.class);setup.setAccessible(true);setup.invoke(matrices,new PoseStack(),entity);
        var world=new com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld(mc.level,-64,384,net.minecraft.core.BlockPos.ZERO,()->{});
        for(boolean ceiling:new boolean[]{false,true})for(boolean gun:new boolean[]{false,true}){
            var state=euphy.upo.sentrymechanicalarm.registry.SentryRegistry.SENTRY_ARM_BLOCK.get().defaultBlockState().setValue(euphy.upo.sentrymechanicalarm.content.SentryArmBlock.CEILING,ceiling);
            var contraption=new com.simibubi.create.content.contraptions.bearing.BearingContraption(false,net.minecraft.core.Direction.UP);
            var info=new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo(net.minecraft.core.BlockPos.ZERO,state,new net.minecraft.nbt.CompoundTag());
            var ctx=new com.simibubi.create.content.contraptions.behaviour.MovementContext(mc.level,info,contraption);
            var arm=euphy.upo.sentrymechanicalarm.content.SentryMovementBehaviour.getOrCreate(ctx);arm.setContraptionSpeed(128);arm.upperArmAngle.setValue(90);
            if(gun)arm.setHeldItem(com.tacz.guns.api.item.builder.GunItemBuilder.create().setId(net.minecraft.resources.ResourceLocation.parse("tacz:ak47")).build());
            var pose=matrices.getModel();pose.pushPose();pose.translate(3,4,5);
            var before=new org.joml.Matrix4f(pose.last().pose());
            euphy.upo.sentrymechanicalarm.content.SentryArmRenderer.renderInContraption(ctx,world,matrices,mc.renderBuffers().bufferSource());
            if(!pose.last().pose().equals(before))throw new AssertionError("Actor renderer popped or changed caller pose");
            pose.popPose();
        }
        mc.renderBuffers().bufferSource().endBatch();
    }
    private static void verifyDepotClient(){
        var level=Minecraft.getInstance().level;
        var id=TimelessAPI.getCommonGunIndex(net.minecraft.resources.ResourceLocation.parse("tacz:ak47")).orElseThrow().getGunData().getAmmoId();
        var ammo=com.tacz.guns.api.item.builder.AmmoItemBuilder.create().setId(id).setCount(1).build();
        var special=ammo.copy();special.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Client variant"));
        var mounted=new euphy.upo.sentrymechanicalarm.compat.AmmoDepotMountedStorage(216,java.util.List.of(
                new euphy.upo.sentrymechanicalarm.compat.AmmoDepotMountedStorage.Stock(0,ammo,(long)Integer.MAX_VALUE+71),
                new euphy.upo.sentrymechanicalarm.compat.AmmoDepotMountedStorage.Stock(1,special,19)));
        var ops=net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE,level.registryAccess());
        var codec=com.simibubi.create.api.contraption.storage.item.MountedItemStorage.CODEC;
        var decoded=(euphy.upo.sentrymechanicalarm.compat.AmmoDepotMountedStorage)codec.parse(ops,codec.encodeStart(ops,mounted).getOrThrow()).getOrThrow();
        if(decoded.count(0)!=(long)Integer.MAX_VALUE+71 || decoded.count(1)!=19 || !decoded.getStackInSlot(1).has(net.minecraft.core.component.DataComponents.CUSTOM_NAME))throw new AssertionError("Client depot codec lost stock");
        for(var state:euphy.upo.sentrymechanicalarm.util.SentryAmmoSupply.State.values()){
            if(!net.minecraft.client.resources.language.I18n.exists("sentry.supply."+state.name().toLowerCase(java.util.Locale.ROOT)))throw new AssertionError("Missing supply translation "+state);
        }
        var block=euphy.upo.sentrymechanicalarm.registry.SentryRegistry.SENTRY_ARM_BLOCK.get().defaultBlockState();
        var arm=euphy.upo.sentrymechanicalarm.content.VirtualSentryArmBlockEntity.fromData(net.minecraft.core.BlockPos.ZERO,block,null,level);
        arm.setSupplyState(euphy.upo.sentrymechanicalarm.util.SentryAmmoSupply.State.SPECIAL_ONLY);
        var copy=euphy.upo.sentrymechanicalarm.content.VirtualSentryArmBlockEntity.fromData(net.minecraft.core.BlockPos.ZERO,block,arm.snapshot(),level);
        if(copy.getSupplyState()!=arm.getSupplyState())throw new AssertionError("Supply state not synchronized");
    }
}
