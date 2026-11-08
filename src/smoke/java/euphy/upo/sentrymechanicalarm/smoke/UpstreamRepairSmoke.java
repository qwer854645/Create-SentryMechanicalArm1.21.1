package euphy.upo.sentrymechanicalarm.smoke;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.EntityKineticBullet;
import euphy.upo.sentrymechanicalarm.*;
import euphy.upo.sentrymechanicalarm.content.*;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Small smoke checks for the upstream repairs, alongside the static suite. */
final class UpstreamRepairSmoke {
    private static int passed, failed, age;
    private static ServerLevel level;
    private static DeployerBlockEntity deployer;
    private static DepotBlockEntity depot;
    private static boolean stocked;
    private static final BlockPos POS = new BlockPos(240, 240, 144);
    private static final ResourceLocation AMMO = ResourceLocation.parse("tacz:9mm");
    interface Check { void run() throws Exception; }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void test(String name, Check body) {
        try { body.run(); passed++; SentryMechanicalArm.LOGGER.info("ALPHA42 PASS {}", name); }
        catch (Throwable e) { failed++; SentryMechanicalArm.LOGGER.error("ALPHA42 FAIL " + name, e); }
    }
    static ItemStack box(int count) {
        var stack = new ItemStack(com.tacz.guns.init.ModItems.AMMO_BOX.get());
        var api = (IAmmoBox) stack.getItem(); api.setAmmoId(stack, AMMO); api.setAmmoCount(stack, count); return stack;
    }
    static void start(ServerLevel server) {
        level = server;
        test("legacy cooldown removal preserves native ammunition components", () -> {
            var expected = box(13); var old = expected.copy(); var tag = ItemNBTHelper.getOrCreateTag(old);
            tag.putLong("DeployerCooldownPos", 10); tag.putLong("DeployerCooldownTime", 900);
            ItemNBTHelper.setTag(old, tag); DeployerAmmoCooldowns.removeLegacyData(old);
            check(ItemStack.isSameItemSameComponents(expected, old), "native box identity differs");
        });
        test("default targets include hostile mobs and configured neutral types", () -> {
            var old = SMAServerConfig.EXTRA_TARGETS.get();
            try {
                SMAServerConfig.EXTRA_TARGETS.set(List.of("minecraft:wandering_trader"));
                check(SentryTargeting.defaultTarget(EntityType.ZOMBIE.create(level)), "hostile lost");
                check(SentryTargeting.defaultTarget(EntityType.WANDERING_TRADER.create(level)), "additional target lost");
                check(!SentryTargeting.defaultTarget(EntityType.COW.create(level)), "unlisted neutral targeted");
            } finally { SMAServerConfig.EXTRA_TARGETS.set(old); }
        });
        for (int delay : new int[] {-1, 0, 12}) test("reload interval setting " + delay, () -> reloadDelay(delay));
        test("own structure excluded before native hit selection", UpstreamRepairSmoke::collision);
        test("native Deployer and depot setup", () -> {
            level.setChunkForced(POS.getX() >> 4, POS.getZ() >> 4, true);
            level.getChunkAt(POS);
            level.setBlock(POS, AllBlocks.DEPOT.getDefaultState(), 3);
            depot = (DepotBlockEntity) level.getBlockEntity(POS);
            var oldBox = box(0); var tag = ItemNBTHelper.getOrCreateTag(oldBox);
            tag.putLong("DeployerCooldownPos", POS.asLong()); tag.putLong("DeployerCooldownTime", Long.MAX_VALUE);
            ItemNBTHelper.setTag(oldBox, tag); depot.setHeldItem(oldBox);
            level.setBlock(POS.above(2), AllBlocks.DEPLOYER.getDefaultState()
                    .setValue(DirectionalKineticBlock.FACING, Direction.DOWN), 3);
            deployer = (DeployerBlockEntity) level.getBlockEntity(POS.above(2));
        });
        // The older static fixture deliberately exercises the fixed short-delay option.
        SMAServerConfig.SENTRY_RELOAD_DELAY.set(2);
    }
    static void reloadDelay(int configured) throws Exception {
        var arm = new StaticSentrySmoke.ProbeArm(POS); arm.setLevel(level);
        arm.setHeldItem(GunItemBuilder.create().setId(ResourceLocation.parse("tacz:glock_17"))
                .setAmmoCount(0).setAmmoInBarrel(false).setFireMode(FireMode.SEMI).build());
        arm.addAmmoBox(box(3)); var player = SentryFakePlayer.get(arm);
        try {
            SMAServerConfig.SENTRY_RELOAD_DELAY.set(configured);
            SentryFakePlayer.sync(player, arm, 0, 0, arm.getHeldItem());
            var gun = player.getMainHandItem(); var operator = IGunOperator.fromLivingEntity(player);
            var index = TimelessAPI.getCommonGunIndex(ResourceLocation.parse("tacz:glock_17"));
            var context = Arrays.stream(SentryArmBlockEntity.class.getDeclaredClasses())
                    .filter(c -> c.getSimpleName().equals("FireContext")).findFirst().orElseThrow();
            var constructor = context.getDeclaredConstructors()[0]; constructor.setAccessible(true);
            var value = constructor.newInstance(player, arm.getHeldItem(), gun, IGun.getIGunOrNull(gun),
                    operator, operator.getDataHolder(), index, false, FireMode.SEMI);
            var handler = SentryArmBlockEntity.class.getDeclaredMethod("handleNoAmmo", context); handler.setAccessible(true); handler.invoke(arm, value);
            var field = SentryArmBlockEntity.class.getDeclaredField("shootDelayAccumulator"); field.setAccessible(true);
            var timing = index.orElseThrow().getGunData().getReloadData();
            double expected = configured < 0 ? Math.ceil((timing.getFeed().getEmptyTime() + timing.getCooldown().getEmptyTime()) * 20) : configured;
            check(field.getFloat(arm) == expected, "wrong delay " + field.getFloat(arm) + " expected=" + expected);
            var api = IGun.getIGunOrNull(gun);
            check(api.getCurrentAmmoCount(gun) + (api.hasBulletInBarrel(gun) ? 1 : 0) == 3, "reload changed ammunition total");
        } finally { SentryFakePlayer.remove(arm); }
    }
    static void collision() throws Exception {
        var contraption = new BearingContraption(false, Direction.UP);
        contraption.bounds = new net.minecraft.world.phys.AABB(BlockPos.ZERO);
        var root = new MovingSentrySmoke.TestEntity(level, contraption);
        var info = new StructureTemplate.StructureBlockInfo(BlockPos.ZERO,
                SentryRegistry.SENTRY_ARM_BLOCK.get().defaultBlockState(), new net.minecraft.nbt.CompoundTag());
        var ctx = new MovementContext(level, info, contraption);
        var arm = SentryMovementBehaviour.getOrCreate(ctx); var shooter = SentryFakePlayer.get(arm);
        var bullet = new EntityKineticBullet(EntityKineticBullet.TYPE, level); bullet.setOwner(shooter);
        var other = EntityType.COW.create(level);
        try {
            SentryBulletOwnership.remember(bullet, shooter);
            check(SentryBulletOwnership.ignores(bullet, root), "owner missing");
            check(!SentryBulletOwnership.ignores(bullet, other), "unrelated entity ignored");
            var nativeHit = com.tacz.guns.util.EntityUtil.class.getDeclaredMethod("getHitResult", Projectile.class,
                    net.minecraft.world.entity.Entity.class, Vec3.class, Vec3.class);
            nativeHit.setAccessible(true);
            check(nativeHit.invoke(null, bullet, root, Vec3.ZERO, new Vec3(1, 0, 0)) == null,
                    "native query did not apply exclusion");
            SentryFakePlayer.remove(arm);
            check(SentryBulletOwnership.ignores(bullet, root), "in-flight bullet lost ownership on disassembly");
        } finally { SentryFakePlayer.remove(arm); bullet.discard(); root.discard(); other.discard(); }
    }
    static void tick() {
        if (level == null) return;
        age++;
        if (deployer != null) {
            deployer.setSpeed(256);
            if (!stocked && deployer.getPlayer() != null) {
                deployer.getPlayer().setItemInHand(InteractionHand.MAIN_HAND, AmmoItemBuilder.create().setId(AMMO).setCount(13).build());
                stocked = true;
            }
        }
        if (age == 140) {
            test("real Deployer output matches manually filled ammo box", () -> {
                check(stocked && ItemStack.isSameItemSameComponents(depot.getHeldItem(), box(13)),
                        "filled output has wrong count or components: " + depot.getHeldItem());
                check(deployer.getPlayer().getMainHandItem().isEmpty(), "ammunition not consumed");
            });
            level.removeBlock(POS.above(2), false); level.removeBlock(POS, false);
            level.setChunkForced(POS.getX() >> 4, POS.getZ() >> 4, false);
            SentryMechanicalArm.LOGGER.info("ALPHA42 RESULT passed={} failed={}", passed, failed);
            level = null;
        }
    }
}
