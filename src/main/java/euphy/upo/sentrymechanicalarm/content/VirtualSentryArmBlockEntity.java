package euphy.upo.sentrymechanicalarm.content;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import euphy.upo.sentrymechanicalarm.compat.AeronauticsHelper;
import euphy.upo.sentrymechanicalarm.network.*;
import euphy.upo.sentrymechanicalarm.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.Comparator;

/** Keeps the actor's block coordinates local throughout its lifetime. */
public class VirtualSentryArmBlockEntity extends SentryArmBlockEntity {
    private MovementContext context;
    private float contraptionSpeed;
    private double range=16;
    public VirtualSentryArmBlockEntity(BlockPos pos,BlockState state){super(pos,state);}
    public com.simibubi.create.content.contraptions.Contraption getOwningContraption(){return context==null?null:context.contraption;}
    public void bind(MovementContext context){this.context=context;this.level=context.world;}
    public void setVirtualLevel(Level level){this.level=level;}
    public void setContraptionSpeed(float speed){contraptionSpeed=speed;}
    public float getContraptionSpeed(){return contraptionSpeed;}
    @Override public float getSpeed(){
        return context!=null && (context.disabled || context.contraption.entity!=null && context.contraption.entity.isRemoved())?0:contraptionSpeed;
    }
    @Override public void tick(){} // Never tick Create's world kinetic network for a virtual actor.
    @Override public boolean isShooterActive(){
        if(context==null || context.disabled || context.contraption==null || context.contraption.entity==null || level==null)return false;
        var entity=context.contraption.entity;
        var pos=BlockPos.containing(entity.position());
        if(level instanceof net.minecraft.server.level.ServerLevel server && !AeronauticsHelper.isInSableSubLevel(level,pos)
                && server.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null)return false;
        return !entity.isRemoved() && level.getEntity(entity.getId())==entity;
    }
    @Override public void setChanged(){}
    @Override public void sendData(){} // MovementBehaviour batches authoritative snapshots.
    @Override protected double getSentryRange(){return range;}
    @Override public Vec3 getProjectedWorldPos(){return toWorld(getBlockPos().getCenter());}
    public Vec3 toWorld(Vec3 local){
        if(context==null || context.contraption==null || context.contraption.entity==null) return local;
        return AeronauticsHelper.sableSubLevelToWorld(level,context.contraption.entity.toGlobalVector(local,1));
    }
    @Override public Vec3 worldToSubLevel(Vec3 world){
        if(context==null || context.contraption==null || context.contraption.entity==null) return world;
        var ace=context.contraption.entity;
        return ace.toLocalVector(AeronauticsHelper.sableWorldToSubLevel(level,world,BlockPos.containing(ace.position())),1);
    }
    @Override public SentryGunGeometry.Pose getGunPose(){
        var local=getLocalGunPose();
        if(context==null || context.contraption==null || context.contraption.entity==null) return local;
        var ace=context.contraption.entity;
        var inLevel=new SentryGunGeometry.Pose(ace.toGlobalVector(local.position(),1),ace.applyRotation(local.forward(),1).normalize());
        return AeronauticsHelper.sablePoseToWorld(level,BlockPos.containing(ace.position()),inLevel);
    }
    @Override protected void scanForTarget(){
        Vec3 center=getProjectedWorldPos();
        var fc=FireControlMovementBehaviour.findFireControl(context.contraption);
        LivingEntity target=null;
        if(fc!=null && level.getEntity(fc.focusedEntityId) instanceof LivingEntity focus
                && eligible(focus,center,false) && getBestTargetPos(focus)!=null) target=focus;
        if(target==null) target=level.getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(range),e->{
            if(!eligible(e,center,true)) return false;
            if(fc==null || fc.displayItem.isEmpty()) return e instanceof Enemy;
            boolean listed=fc.targetList.contains(e.getName().getString())
                    || fc.targetList.contains(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
            return fc.isWhitelist ? !listed : listed;
        }).stream().filter(e->getBestTargetPos(e)!=null).min(Comparator.comparingDouble(e->e.distanceToSqr(center))).orElse(null);
        cachedTarget=target;setTargetId(target==null?-1:target.getId());
    }
    private boolean eligible(LivingEntity e,Vec3 center,boolean excludeCreative){
        return e.isAlive() && !e.isSpectator() && !(e instanceof FakePlayer) && e.distanceToSqr(center)<=range*range
                && (!excludeCreative || !(e instanceof net.minecraft.world.entity.player.Player p) || !p.isCreative());
    }
    @Override protected void sendShootPacket(FakePlayer player){
        var shot=SentryFakePlayer.lastShot(player);var pose=getGunPose();
        Vec3 start=shot==null?pose.position():shot.origin(),dir=shot==null?pose.forward():shot.direction();
        var hit=level.clip(new net.minecraft.world.level.ClipContext(start,start.add(dir.scale(100)),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
        PacketDistributor.sendToPlayersTrackingEntity(context.contraption.entity,new SentryContraptionShootPacket(
                context.contraption.entity.getId(),getBlockPos(),start,hit.getLocation(),getHeldItem().copy()));
    }
    @Override protected void sendActionPacket(SentryShootPacket.ActionType type){
        if(context==null || context.contraption.entity==null)return;
        Vec3 pos=getActualMuzzlePos();
        PacketDistributor.sendToPlayersTrackingEntity(context.contraption.entity,new SentryContraptionShootPacket(
                context.contraption.entity.getId(),getBlockPos(),pos,pos,getHeldItem().copy(),type));
    }
    public CompoundTag snapshot(){
        CompoundTag tag=new CompoundTag();write(tag,level.registryAccess(),false);
        tag.putFloat("Speed",contraptionSpeed);tag.putDouble("SentryActorRange",range);return tag;
    }
    public void acceptState(CompoundTag tag){
        read(tag,level.registryAccess(),false);contraptionSpeed=tag.getFloat("Speed");
        range=tag.contains("SentryActorRange")?Math.clamp(tag.getDouble("SentryActorRange"),1,256):16;
    }
    public static VirtualSentryArmBlockEntity fromData(BlockPos pos,BlockState state,CompoundTag tag,Level level){
        var arm=new VirtualSentryArmBlockEntity(pos,state);arm.setVirtualLevel(level);if(tag!=null)arm.acceptState(tag);return arm;
    }
    public void acceptNetworkState(CompoundTag tag){
        float base=baseAngle.getValue(),lower=lowerArmAngle.getValue(),upper=upperArmAngle.getValue(),head=headAngle.getValue();
        acceptState(tag);
        float targetBase=baseAngle.getValue(),targetLower=lowerArmAngle.getValue(),targetUpper=upperArmAngle.getValue(),targetHead=headAngle.getValue();
        baseAngle.setValue(base);lowerArmAngle.setValue(lower);upperArmAngle.setValue(upper);headAngle.setValue(head);
        baseAngle.chase(base+net.minecraft.util.Mth.wrapDegrees(targetBase-base),.5,net.createmod.catnip.animation.LerpedFloat.Chaser.EXP);
        lowerArmAngle.chase(targetLower,.5,net.createmod.catnip.animation.LerpedFloat.Chaser.EXP);
        upperArmAngle.chase(targetUpper,.5,net.createmod.catnip.animation.LerpedFloat.Chaser.EXP);
        headAngle.chase(targetHead,.5,net.createmod.catnip.animation.LerpedFloat.Chaser.EXP);
    }
}
