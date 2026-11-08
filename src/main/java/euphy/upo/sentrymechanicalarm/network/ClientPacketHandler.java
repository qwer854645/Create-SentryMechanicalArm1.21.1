package euphy.upo.sentrymechanicalarm.network;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlock;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import euphy.upo.sentrymechanicalarm.content.VirtualSentryArmBlockEntity;
import euphy.upo.sentrymechanicalarm.mixin.GunDisplayInstanceAccessor;
import euphy.upo.sentrymechanicalarm.util.ArmSoundHelper;
import euphy.upo.sentrymechanicalarm.util.SentryFakePlayer;
import euphy.upo.sentrymechanicalarm.util.SentryTrailManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ClientPacketHandler {
    public static void handleContraptionState(SentryContraptionStatePacket packet) {
        var level=net.minecraft.client.Minecraft.getInstance().level;
        if (level==null || !(level.getEntity(packet.entityId()) instanceof com.simibubi.create.content.contraptions.AbstractContraptionEntity ace)
                || ace.getContraption()==null) return;
        for (var pair:ace.getContraption().getActors()) {
            var ctx=pair.getValue();
            if (ctx==null || !ctx.localPos.equals(packet.localPos()) || !(ctx.state.getBlock() instanceof euphy.upo.sentrymechanicalarm.content.SentryArmBlock)) continue;
            var arm=euphy.upo.sentrymechanicalarm.content.SentryMovementBehaviour.getOrCreate(ctx);
            arm.acceptNetworkState(packet.state());
            ctx.blockEntityData=packet.state().copy();
            return;
        }
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<ResourceLocation, GunDisplayInstance> SENTRY_DISPLAYS = new HashMap<>();
    public static void clearCaches() { SENTRY_DISPLAYS.clear(); }
    public static void handleCasingProfiles(SentryCasingProfilesPacket packet){
        euphy.upo.sentrymechanicalarm.util.SentryCasingProfiles.receive(packet.ammo());
        euphy.upo.sentrymechanicalarm.client.CasingJeiBridge.refresh();
        var mc=net.minecraft.client.Minecraft.getInstance();
        if(mc.level!=null&&euphy.upo.sentrymechanicalarm.registry.SentryRegistry.CASINGS_TAB.isBound()){
            var parameters=new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(mc.level.enabledFeatures(),mc.player!=null&&mc.player.canUseGameMasterBlocks(),mc.level.registryAccess());
            euphy.upo.sentrymechanicalarm.registry.SentryRegistry.CASINGS_TAB.get().buildContents(parameters);
            net.minecraft.world.item.CreativeModeTabs.searchTab().buildContents(parameters);
            if(mc.screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen)mc.screen.init(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
        }
    }
    public static void handleGunProfiles(SentryGunProfilesPacket packet) {
        euphy.upo.sentrymechanicalarm.util.SentryGunProfiles.receive(packet.profiles());
        clearCaches(); SentryTrailManager.clear();
    }

    private static void triggerSentryAnimation(ItemStack gunStack, String input) {
        IGun iGun = IGun.getIGunOrNull(gunStack);
        if (iGun == null) return;
        ResourceLocation gunId = iGun.getGunId(gunStack);
        if (gunId == null) return;

        GunDisplayInstance sentryDisplay = SENTRY_DISPLAYS.computeIfAbsent(gunId, id -> {
            Optional<GunDisplayInstance> original = TimelessAPI.getGunDisplay(gunStack);
            if (original.isEmpty()) return null;
            GunDisplay display = ((GunDisplayInstanceAccessor) (Object) original.get()).sentrymechanicalarm$getDisplay();
            if (display == null) return null;
            return GunDisplayInstance.create(id, display);
        });

        if (sentryDisplay != null) {
            LuaAnimationStateMachine<GunAnimationStateContext> sm = sentryDisplay.getAnimationStateMachine();
            if (sm != null) sm.trigger(input);
        }
    }

    private static void addCanonicalTrail(ItemStack gun,Vec3 start,Vec3 end) {
        if (!euphy.upo.sentrymechanicalarm.util.SentryGunProfiles.needsSyntheticTracer(gun)
                || !euphy.upo.sentrymechanicalarm.util.SentryGunGeometry.finite(start)
                || !euphy.upo.sentrymechanicalarm.util.SentryGunGeometry.finite(end)) return;
        Vec3 delta=end.subtract(start);
        if (delta.lengthSqr()>1e-8) SentryTrailManager.addTracer(start,delta.normalize(),8.0,2.0,delta.length());
    }
    public static void handleSentryShoot(SentryShootPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        BlockEntity be = mc.level.getBlockEntity(msg.pos());
        if (!(be instanceof SentryArmBlockEntity sentry)) return;

        sentry.updateAmmoFromPacket(msg.slotIndex(), msg.itemTag());
        ItemStack gunStack = sentry.getHeldItem();

        if (gunStack.isEmpty() || !(gunStack.getItem() instanceof IGun iGun)) return;

        Level level = mc.level;
        Vec3 center = msg.realStart();

        Optional<GunDisplayInstance> displayOpt = TimelessAPI.getGunDisplay(gunStack);
        if (displayOpt.isEmpty()) return;
        GunDisplayInstance display = displayOpt.get();

        switch (msg.actionType()) {
            case CHARGE -> {
                triggerSentryAnimation(gunStack, "bolt");
                ArmSoundHelper.playChargeSound(level, center, gunStack, display);
            }
            case BOLT -> ArmSoundHelper.playBoltSound(level, center, display);
            case RELOAD_EMPTY -> ArmSoundHelper.playReloadSound(level, center, display, true);
            case RELOAD_TACTICAL -> ArmSoundHelper.playReloadSound(level, center, display, false);
            case SHOOT -> {
                sentry.triggerShootEffects();
                triggerSentryAnimation(gunStack, "shoot");
                TimelessAPI.getCommonGunIndex(iGun.getGunId(gunStack)).ifPresent(index ->
                    ArmSoundHelper.playFireEffects(level, center, gunStack, index.getGunData())
                );

                addCanonicalTrail(gunStack,msg.realStart(),msg.realEnd());
            }
        }
    }

    public static void handleSentryContraptionShoot(SentryContraptionShootPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;
        if(msg.actionType()!=SentryShootPacket.ActionType.SHOOT){
            TimelessAPI.getGunDisplay(msg.gunStack()).ifPresent(display->{
                switch(msg.actionType()){
                    case CHARGE -> {triggerSentryAnimation(msg.gunStack(),"bolt");ArmSoundHelper.playChargeSound(level,msg.realStart(),msg.gunStack(),display);}
                    case BOLT -> ArmSoundHelper.playBoltSound(level,msg.realStart(),display);
                    case RELOAD_EMPTY -> ArmSoundHelper.playReloadSound(level,msg.realStart(),display,true);
                    case RELOAD_TACTICAL -> ArmSoundHelper.playReloadSound(level,msg.realStart(),display,false);
                    default -> {}
                }
            });return;
        }

        Entity entity = level.getEntity(msg.contraptionId());
        if (entity instanceof AbstractContraptionEntity ace) {
            Contraption contraption = ace.getContraption();
            if (contraption != null) {
                for (var actor : contraption.getActors()) {
                    if (actor.getKey().pos().equals(msg.localPos())) {
                        MovementContext context = actor.getValue();
                        SentryArmBlockEntity be = null;
                        if (context.temporaryData instanceof SentryArmBlockEntity s) {
                            be = s;
                        } else if (context.blockEntityData != null) {
                            VirtualSentryArmBlockEntity vbe = VirtualSentryArmBlockEntity.fromData(
                                    context.localPos, context.state, context.blockEntityData, context.world);
                            context.temporaryData = vbe;
                            be = vbe;
                        }
                        if (be != null) {
                            be.setLastShootTime(System.currentTimeMillis());
                            be.triggerShootEffects();
                        }
                        break;
                    }
                }
            }
        }

        addCanonicalTrail(msg.gunStack(),msg.realStart(),msg.realEnd());

        if (msg.gunStack().getItem() instanceof IGun iGun) {
            triggerSentryAnimation(msg.gunStack(), "shoot");
            TimelessAPI.getCommonGunIndex(iGun.getGunId(msg.gunStack())).ifPresent(index ->
                ArmSoundHelper.playFireEffects(level, msg.realStart(), msg.gunStack(), index.getGunData())
            );
        }
    }
}
