package euphy.upo.sentrymechanicalarm.smoke;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.SentryFakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Real TaCZ API regression check, on a dedicated development server only. */
@EventBusSubscriber(modid = SentryMechanicalArm.MODID)
public final class ShootingClockSmoke {
    @SubscribeEvent
    public static void run(ServerStartedEvent event) {
        if (!Boolean.getBoolean("sentry.smoke")) return;
        var level = event.getServer().overworld();
        // No placed block or player save is modified.
        var arm = new SentryArmBlockEntity(new BlockPos(0, 200, 0),
                SentryRegistry.SENTRY_ARM_BLOCK.get().defaultBlockState());
        arm.setLevel(level);
        var fp = SentryFakePlayer.get(arm);
        try {
            ItemStack gun = GunItemBuilder.create()
                    .setId(ResourceLocation.fromNamespaceAndPath("tacz", "glock_17"))
                    .setFireMode(FireMode.SEMI).setAmmoCount(10).setAmmoInBarrel(true).build();
            if (gun.isEmpty()) throw new IllegalStateException("Default test gun not loaded");
            SentryFakePlayer.sync(fp, arm, 0, -90, gun);
            var operator = IGunOperator.fromLivingEntity(fp);
            var data = operator.getDataHolder();
            // Make the ordinary first-shot cooldown elapsed without sleeping.
            data.baseTimestamp -= 1000;
            long fixedDrawTimestamp = data.drawTimestamp;
            data.drawTimestamp = System.currentTimeMillis() - 10000;
            ShootResult before = operator.shoot(() -> -90f, () -> 0f);
            if (before != ShootResult.IS_DRAWING) {
                throw new IllegalStateException("Expected old clock to block draw, got " + before);
            }
            data.drawTimestamp = fixedDrawTimestamp;
            var iGun = IGun.getIGunOrNull(fp.getMainHandItem());
            int ammoBefore = iGun.getCurrentAmmoCount(fp.getMainHandItem())
                    + (iGun.hasBulletInBarrel(fp.getMainHandItem()) ? 1 : 0);
            ShootResult after = operator.shoot(() -> -90f, () -> 0f);
            boolean fired = SentryFakePlayer.checkAndClearFired(fp);
            int ammoAfter = iGun.getCurrentAmmoCount(fp.getMainHandItem())
                    + (iGun.hasBulletInBarrel(fp.getMainHandItem()) ? 1 : 0);
            if (after != ShootResult.SUCCESS || !fired || ammoAfter != ammoBefore - 1) {
                throw new IllegalStateException("Shooting failed: " + after + ", fired=" + fired
                        + ", ammo=" + ammoBefore + "->" + ammoAfter);
            }
            SentryMechanicalArm.LOGGER.info("SHOOT_CLOCK_SMOKE PASS before={} after={} fired={} ammo={}->{}",
                    before, after, fired, ammoBefore, ammoAfter);
        } catch (Throwable failure) {
            SentryMechanicalArm.LOGGER.error("SHOOT_CLOCK_SMOKE FAIL", failure);
        } finally {
            SentryFakePlayer.remove(arm);
            event.getServer().halt(false);
        }
    }
}
