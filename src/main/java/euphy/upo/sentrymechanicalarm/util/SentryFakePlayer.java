package euphy.upo.sentrymechanicalarm.util;

import com.mojang.authlib.GameProfile;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.util.time.GunTime;
import euphy.upo.sentrymechanicalarm.compat.AeronauticsHelper;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlock;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import euphy.upo.sentrymechanicalarm.content.VirtualSentryArmBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@net.neoforged.fml.common.EventBusSubscriber(modid="sentrymechanicalarm")
public class SentryFakePlayer {

    private static final WeakHashMap<SentryArmBlockEntity, FakePlayer> FAKE_PLAYERS = new WeakHashMap<>();
    private static final WeakHashMap<FakePlayer, java.lang.ref.WeakReference<SentryArmBlockEntity>> REVERSE_MAP = new WeakHashMap<>();
    private static final Map<String, RobustFakePlayer> CONTRAPTION_FAKE_PLAYERS = new HashMap<>();
    public record Shot(Vec3 origin, Vec3 direction) {}
    private static final Map<FakePlayer,Shot> LAST_SHOT = new WeakHashMap<>();
    public static void beginShot(FakePlayer player) { LAST_SHOT.remove(player); }
    public static boolean rememberShot(FakePlayer player,Vec3 origin,Vec3 velocity) {
        if (velocity.lengthSqr()>1e-10 && !LAST_SHOT.containsKey(player)) {
            LAST_SHOT.put(player,new Shot(origin,velocity.normalize())); return true;
        }
        return false;
    }
    public static Shot lastShot(FakePlayer player) { return LAST_SHOT.get(player); }

    private static final java.util.Map<FakePlayer, Boolean> FIRED_TRACKER = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final java.util.concurrent.atomic.AtomicInteger COUNTER = new java.util.concurrent.atomic.AtomicInteger(0);

    public static void markFired(FakePlayer fp) {
        FIRED_TRACKER.put(fp, true);
    }

    public static boolean checkAndClearFired(FakePlayer fp) {
        return FIRED_TRACKER.remove(fp) != null;
    }

    private static class RobustFakePlayer extends FakePlayer {
        private boolean fakeCreativeMode = false;

        public RobustFakePlayer(ServerLevel level, GameProfile name) {
            super(level, name);
        }

        public void setLevelPublic(ServerLevel level) {
            super.setLevel(level);
        }

        public void setFakeCreative(boolean active) {
            this.fakeCreativeMode = active;
        }

        @Override
        public boolean isCreative() {
            return this.fakeCreativeMode || super.isCreative();
        }

        @Override
        public boolean isInvulnerableTo(DamageSource source) {
            return true;
        }

        // justlevelingfork Compat
    }

    public static void setTempCreative(FakePlayer fp, boolean active) {
        if (fp instanceof RobustFakePlayer robust) {
            robust.setFakeCreative(active);
        } else {
            if (active) fp.setGameMode(GameType.CREATIVE);
            else fp.setGameMode(GameType.SURVIVAL);
        }
    }

    public static FakePlayer get(SentryArmBlockEntity arm) {
        if (!(arm.getLevel() instanceof ServerLevel serverLevel)) return null;
        FakePlayer fp = FAKE_PLAYERS.computeIfAbsent(arm, k -> {
            String name = "Sentry_" + arm.getBlockPos().getX() + "_" + arm.getBlockPos().getY() + "_" + arm.getBlockPos().getZ();
            return createRobustFakePlayer(serverLevel, name);
        });
        if(getArmFromPlayer(fp)!=arm)REVERSE_MAP.put(fp,new java.lang.ref.WeakReference<>(arm));
        return fp;
    }

    public static SentryArmBlockEntity getArmFromPlayer(FakePlayer fp) {
        var reference=REVERSE_MAP.get(fp);return reference==null?null:reference.get();
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void cleanInactive(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){
        if(event.getServer().getTickCount()%20!=0)return;
        for(var arm:new java.util.ArrayList<>(FAKE_PLAYERS.keySet()))if(arm!=null && !arm.isShooterActive())remove(arm);
        for(var player:new java.util.ArrayList<>(REVERSE_MAP.keySet()))if(player!=null && getArmFromPlayer(player)==null){
            REVERSE_MAP.remove(player);player.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);player.discard();FIRED_TRACKER.remove(player);LAST_SHOT.remove(player);
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void clearServer(net.neoforged.neoforge.event.server.ServerStoppedEvent event){
        for(var arm:new java.util.ArrayList<>(FAKE_PLAYERS.keySet()))if(arm!=null)remove(arm);
        for(var player:CONTRAPTION_FAKE_PLAYERS.values()){player.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);player.discard();}
        CONTRAPTION_FAKE_PLAYERS.clear();REVERSE_MAP.clear();FAKE_PLAYERS.clear();FIRED_TRACKER.clear();LAST_SHOT.clear();
    }

    public static FakePlayer getForContraption(ServerLevel level, UUID contraptionUUID, BlockPos localPos) {
        String key = contraptionUUID.toString() + "_" + localPos.asLong();

        RobustFakePlayer existing = CONTRAPTION_FAKE_PLAYERS.get(key);

        if (existing != null) {
            if (existing.level() != level) {
                existing.setLevelPublic(level);
            }
            return existing;
        }

        String name = "SentryC_" + Math.abs(key.hashCode());
        RobustFakePlayer newPlayer = createRobustFakePlayer(level, name);
        CONTRAPTION_FAKE_PLAYERS.put(key, newPlayer);
        return newPlayer;
    }

    public static void removeForContraption(UUID contraptionUUID, BlockPos localPos) {
        String key = contraptionUUID.toString() + "_" + localPos.asLong();
        FakePlayer removed = CONTRAPTION_FAKE_PLAYERS.remove(key);
        if (removed != null) {
            removed.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
            removed.discard();
        }
    }

    private static RobustFakePlayer createRobustFakePlayer(ServerLevel level, String name) {
        UUID uuid = new UUID(COUNTER.incrementAndGet(), UUID.nameUUIDFromBytes(name.getBytes()).getLeastSignificantBits());
        GameProfile profile = new GameProfile(uuid, name);

        RobustFakePlayer fp = new RobustFakePlayer(level, profile);
        fp.setGameMode(GameType.SURVIVAL);
        fp.setNoGravity(true);
        fp.setInvisible(true);
        fp.setInvulnerable(true);

        if (fp.connection == null) {
            try {
                net.minecraft.network.Connection fakeConnection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.CLIENTBOUND);
                fp.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(), fakeConnection, fp, net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false)) {
                    @Override
                    public void send(net.minecraft.network.protocol.Packet<?> packet) {
                    }
                };
            } catch (Exception e) {
                euphy.upo.sentrymechanicalarm.SentryMechanicalArm.LOGGER.error("Failed to mock connection for {}", name, e);
            }
        }

        // Not adding the fake player to the world prevents it from interfering with
        // vanilla mechanics such as the sleep/night-skip check. The Tacz gun mod
        // operations (shoot, inventory, capabilities) work via the object reference
        // and do not require the fake player to be a world-registered entity.

        return fp;
    }

    public static Vec3 getMuzzlePosition(Vec3 basePos, float yaw, float pitch, double armLength) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        return basePos.add(
            -Math.sin(yawRad) * Math.cos(pitchRad) * armLength,
            -Math.sin(pitchRad) * armLength,
            Math.cos(yawRad) * Math.cos(pitchRad) * armLength
        );
    }

    /** Visual policy only: every weapon still uses the same real projectile spawn path. */
    public static boolean hasEntityBullet(ItemStack gunStack) {
        return SentryGunProfiles.get(gunStack).visibleProjectile();
    }

    public static double getGunArmLength(ItemStack gunStack) {
        return SentryGunProfiles.get(gunStack).muzzle().length() * 1.5;
    }
    public static Vec3 getMuzzlePosition(SentryArmBlockEntity arm, float yaw, float pitch, double armLength) {
        boolean isCeiling = arm.getBlockState().hasProperty(SentryArmBlock.CEILING) && arm.getBlockState().getValue(SentryArmBlock.CEILING);
        double yBase = isCeiling ? -2.8 : 1.0;
        Vec3 basePos = new Vec3(arm.getBlockPos().getX() + 0.5, arm.getBlockPos().getY() + yBase, arm.getBlockPos().getZ() + 0.5);
        return getMuzzlePosition(basePos, yaw, pitch, armLength);
    }

    public static Vec3 getContraptionLocalMuzzle(Vec3 localPosCenter, VirtualSentryArmBlockEntity virtualBE, ItemStack gunStack) {
        boolean isCeiling = virtualBE.getBlockState().hasProperty(SentryArmBlock.CEILING) && virtualBE.getBlockState().getValue(SentryArmBlock.CEILING);
        return SentryGunGeometry.pose(BlockPos.containing(localPosCenter),isCeiling,
                virtualBE.baseAngle.getValue(),virtualBE.lowerArmAngle.getValue(),
                virtualBE.upperArmAngle.getValue(),virtualBE.headAngle.getValue(),SentryGunProfiles.get(gunStack)).position();
    }
    public static void remove(SentryArmBlockEntity arm) {
        FakePlayer fp = FAKE_PLAYERS.remove(arm);
        if (fp != null) {
            REVERSE_MAP.remove(fp);
            FIRED_TRACKER.remove(fp);
            LAST_SHOT.remove(fp);
            fp.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
            if (fp.level() instanceof ServerLevel serverLevel) {
                fp.discard();
            }
        }
    }

    /** TaCZ attachment properties and burst tasks belong to the old resource generation. */
    public static void invalidateGunData() {
        for (var arm : new java.util.ArrayList<>(FAKE_PLAYERS.keySet())) {
            if (arm != null) remove(arm);
        }
        for (var player : CONTRAPTION_FAKE_PLAYERS.values()) {
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            player.discard();
        }
        CONTRAPTION_FAKE_PLAYERS.clear();
    }

    public static void sync(FakePlayer fp, SentryArmBlockEntity arm, float yaw, float pitch, ItemStack gunStack) {
        updatePose(fp,arm);
        yaw=fp.getYRot();pitch=fp.getXRot();

        fp.setYRot(yaw);
        fp.setXRot(pitch);
        fp.yHeadRot = yaw;
        fp.yBodyRot = yaw;

        syncEquipment(fp,arm,gunStack);
    }

    /** Refresh only geometry during delayed bursts, never overwrite already-consumed ammo. */
    public static void updatePose(FakePlayer fp,SentryArmBlockEntity arm) {
        var pose = arm.getGunPose();
        Vec3 feet = pose.position().subtract(0,fp.getEyeHeight(),0);
        fp.setPos(feet.x,feet.y,feet.z);
        fp.xo=fp.xOld=feet.x; fp.yo=fp.yOld=feet.y; fp.zo=fp.zOld=feet.z;
        fp.setYRot(pose.yaw());fp.setXRot(pose.pitch());
        fp.yHeadRot=pose.yaw();fp.yBodyRot=pose.yaw();
    }

    private static void syncEquipment(FakePlayer fp,SentryArmBlockEntity arm,ItemStack gunStack) {

        fp.setHealth(fp.getMaxHealth());
        fp.deathTime = 0;
        fp.removeAllEffects();
        fp.clearFire();

        IGunOperator operator = IGunOperator.fromLivingEntity(fp);

        operator.aim(true);

        if (operator.getDataHolder().currentGunItem == null) {
            operator.initialData();
        }

        ItemStack currentFakeItem = fp.getMainHandItem();
        boolean needsSwitch = true;

        IGun iGunTarget = IGun.getIGunOrNull(gunStack);

        if (iGunTarget != null && !currentFakeItem.isEmpty()) {
            IGun iGunCurrent = IGun.getIGunOrNull(currentFakeItem);
            if (iGunCurrent != null) {
                ResourceLocation idTarget = iGunTarget.getGunId(gunStack);
                ResourceLocation idCurrent = iGunCurrent.getGunId(currentFakeItem);
                if (idTarget.equals(idCurrent)) {
                    needsSwitch = false;
                }
            }
        } else if (iGunTarget == null && currentFakeItem.isEmpty()) {
            needsSwitch = false;
        }

        if (needsSwitch) {
            fp.getInventory().clearContent();
            ItemStack newStack = gunStack.copy();
            fp.setItemSlot(EquipmentSlot.MAINHAND, newStack);

            operator.draw(() -> newStack);
            // TaCZ 1.2 uses its pause-aware clock, not wall-clock epoch time.
            operator.getDataHolder().drawTimestamp = GunTime.nowMillis() - 10000;

        } else {
            if (ItemNBTHelper.hasTag(gunStack)) {
                if (!java.util.Objects.equals(ItemNBTHelper.getTag(currentFakeItem), ItemNBTHelper.getTag(gunStack))) {
                    ItemNBTHelper.setTag(currentFakeItem, ItemNBTHelper.getTag(gunStack));
                }
            } else {
                if (ItemNBTHelper.hasTag(currentFakeItem)) currentFakeItem.remove(DataComponents.CUSTOM_DATA);
            }
            if (currentFakeItem.getCount() != gunStack.getCount()) {
                currentFakeItem.setCount(gunStack.getCount());
            }
        }

        if (iGunTarget != null) {
            for (int i = 0; i < arm.attachedAmmoBoxes.size(); i++) {
                ItemStack box = arm.attachedAmmoBoxes.get(i);
                fp.getInventory().setItem(9 + i, box.copy());
            }
        }
    }
}
