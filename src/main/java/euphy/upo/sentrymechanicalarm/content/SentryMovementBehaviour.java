package euphy.upo.sentrymechanicalarm.content;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import euphy.upo.sentrymechanicalarm.network.SentryContraptionStatePacket;
import euphy.upo.sentrymechanicalarm.util.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.api.distmarker.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid="sentrymechanicalarm")
public class SentryMovementBehaviour implements MovementBehaviour {
    public static VirtualSentryArmBlockEntity getOrCreate(MovementContext ctx){
        if(!(ctx.temporaryData instanceof VirtualSentryArmBlockEntity))
            ctx.temporaryData=VirtualSentryArmBlockEntity.fromData(ctx.localPos,ctx.state,ctx.blockEntityData,ctx.world);
        var arm=(VirtualSentryArmBlockEntity)ctx.temporaryData;arm.bind(ctx);return arm;
    }
    @Override public boolean isActive(MovementContext context){return true;}
    @Override public void startMoving(MovementContext context){getOrCreate(context);}
    @Override public void tick(MovementContext context){
        if(context.world==null || context.contraption==null || context.contraption.entity==null) return;
        var arm=getOrCreate(context);
        if(context.world.isClientSide){
            arm.baseAngle.tickChaser();arm.lowerArmAngle.tickChaser();arm.upperArmAngle.tickChaser();arm.headAngle.tickChaser();return;
        }
        if(!context.data.contains("SentryCapturedSpeed"))context.data.putFloat("SentryCapturedSpeed",arm.getContraptionSpeed());
        float stored=context.data.getFloat("SentryCapturedSpeed");
        arm.setContraptionSpeed(stored!=0?stored:(float)Math.min(256,context.motion.length()*512));
        if(context.world.getGameTime()%20==0 && context.contraption.getStorage()!=null)
            SentryAmmoSupply.refill(arm,context.contraption.getStorage().getAllItems());
        arm.tickSentryController();persist(context,arm);
        if(context.world.getGameTime()%5==0)
            PacketDistributor.sendToPlayersTrackingEntity(context.contraption.entity,new SentryContraptionStatePacket(
                    context.contraption.entity.getId(),context.localPos,context.blockEntityData.copy()));
    }
    private static void persist(MovementContext ctx,VirtualSentryArmBlockEntity arm){
        var state=arm.snapshot();
        if(ctx.blockEntityData==null)ctx.blockEntityData=new net.minecraft.nbt.CompoundTag();
        // Create's block map and actor point to this same tag. Keep that identity for
        // both structure serialization and addBlocksToWorld during disassembly.
        for(String key:new java.util.HashSet<>(ctx.blockEntityData.getAllKeys()))ctx.blockEntityData.remove(key);
        ctx.blockEntityData.merge(state);
        var info=ctx.contraption.getBlocks().get(ctx.localPos);
        if(info!=null && info.nbt()!=ctx.blockEntityData){
            var updated=new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo(ctx.localPos,ctx.state,ctx.blockEntityData);
            ctx.contraption.getBlocks().put(ctx.localPos,updated);
            for(var pair:ctx.contraption.getActors())if(pair.getValue()==ctx)pair.setLeft(updated);
        }
    }
    @Override public void writeExtraData(MovementContext context){
        if(!context.world.isClientSide && context.temporaryData instanceof VirtualSentryArmBlockEntity arm)persist(context,arm);
    }
    @Override public void stopMoving(MovementContext context){
        if(context.temporaryData instanceof VirtualSentryArmBlockEntity arm){
            if(!context.world.isClientSide)persist(context,arm);SentryFakePlayer.remove(arm);
        }
        context.temporaryData=null;
    }
    @SubscribeEvent public static void onContraptionRemoved(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event){
        if(event.getEntity() instanceof com.simibubi.create.content.contraptions.AbstractContraptionEntity ace && ace.getContraption()!=null)
            for(var pair:ace.getContraption().getActors())
                if(pair.getValue()!=null && pair.getValue().temporaryData instanceof VirtualSentryArmBlockEntity arm)SentryFakePlayer.remove(arm);
    }
    @Override public boolean disableBlockEntityRendering(){return true;}
    @Override @OnlyIn(Dist.CLIENT) public void renderInContraption(MovementContext context,VirtualRenderWorld world,
            ContraptionMatrices matrices,MultiBufferSource buffer){SentryArmRenderer.renderInContraption(context,world,matrices,buffer);}
}
