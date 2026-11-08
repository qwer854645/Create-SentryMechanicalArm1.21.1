package euphy.upo.sentrymechanicalarm.smoke;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.init.ModItems;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.content.*;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.SentryFakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Optional real-server regression suite. Does not load any player save. */
@EventBusSubscriber(modid = SentryMechanicalArm.MODID)
public final class StaticSentrySmoke {
    private static final BlockPos POS = new BlockPos(128, 240, 128);
    private static int passed, failed, ticks, fired;
    private static ProbeArm active;
    private static Zombie first, second;
    private static SentryArmBlockEntity placedArm;
    private static com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity motor;
    private static Zombie placedTarget;
    private static int placedCheckpoint;
    private static boolean forcedByTest;
    private static final BlockPos PLACED = POS.east(48);
    private static int checkpoint;
    private static final List<SentryArmBlockEntity> arms = new ArrayList<>();
    private static final Map<BlockPos, net.minecraft.world.level.block.state.BlockState> wall = new LinkedHashMap<>();
    private static final Map<BlockPos, net.minecraft.world.level.block.state.BlockState> fixtures = new LinkedHashMap<>();

    static final class ProbeArm extends SentryArmBlockEntity {
        @Override public boolean isShooterActive(){return !isRemoved();} // Isolated test controller, not a placed block.
        float suppliedSpeed;
        ProbeArm(BlockPos pos) { super(pos, SentryRegistry.SENTRY_ARM_BLOCK.get().defaultBlockState()); }
        @Override public float getSpeed() { return suppliedSpeed; }
    }
    static final class RecordingPlayer extends net.neoforged.neoforge.common.util.FakePlayer {
        int sentryPackets;
        RecordingPlayer(ServerLevel level) {
            super(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "Phase2Probe"));
            connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),
                    new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), this,
                    net.minecraft.server.network.CommonListenerCookie.createInitial(getGameProfile(), false)) {
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {
                    if (packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom
                            && custom.payload() instanceof euphy.upo.sentrymechanicalarm.network.SentryShootPacket) sentryPackets++;
                }
            };
        }
    }
    private static ProbeArm arm(ServerLevel level) {
        var arm = new ProbeArm(POS);
        arm.setLevel(level);
        arms.add(arm);
        return arm;
    }
    private static ItemStack gun(int magazine, boolean barrel) {
        return GunItemBuilder.create().setId(ResourceLocation.parse("tacz:glock_17"))
                .setFireMode(FireMode.SEMI).setAmmoCount(magazine).setAmmoInBarrel(barrel).build();
    }
    private static ResourceLocation ammo() {
        return TimelessAPI.getCommonGunIndex(ResourceLocation.parse("tacz:glock_17")).orElseThrow().getGunData().getAmmoId();
    }
    private static ItemStack box(ResourceLocation id, int count) {
        var box = new ItemStack(ModItems.AMMO_BOX.get());
        var api = (IAmmoBox) box.getItem();
        api.setAmmoId(box, id);
        api.setAmmoCount(box, count);
        return box;
    }
    private static int total(SentryArmBlockEntity arm) {
        var stack = arm.getHeldItem();
        var api = IGun.getIGunOrNull(stack);
        int count = api == null ? 0 : api.getCurrentAmmoCount(stack) + (api.hasBulletInBarrel(stack) ? 1 : 0);
        for (var box : arm.attachedAmmoBoxes) if (box.getItem() instanceof IAmmoBox b) count += b.getAmmoCount(box);
        return count;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    interface Case { void run() throws Exception; }
    private static void test(String name, Case body) {
        try { body.run(); passed++; SentryMechanicalArm.LOGGER.info("PHASE2 PASS {}", name); }
        catch (Throwable e) { failed++; SentryMechanicalArm.LOGGER.error("PHASE2 FAIL " + name, e); }
    }
    private static Object call(Object target, String method, Class<?>[] types, Object... args) throws Exception {
        Method m = SentryArmBlockEntity.class.getDeclaredMethod(method, types);
        m.setAccessible(true);
        return m.invoke(target, args);
    }
    @SubscribeEvent
    public static void fired(com.tacz.guns.api.event.common.GunFireEvent event) {
        if (active != null && event.getShooter() instanceof net.neoforged.neoforge.common.util.FakePlayer fp
                && SentryFakePlayer.getArmFromPlayer(fp) == active) fired++;
    }
    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!"phase2".equals(System.getProperty("sentry.smoke"))) return;
        passed = failed = ticks = fired = 0;
        var level = event.getServer().overworld();
        level.getChunkAt(POS);
        UpstreamRepairSmoke.start(level);
        test("barrel-only ammo remains usable", () -> {
            var a = arm(level); a.setHeldItem(gun(0, true)); check(a.hasAnyAmmo(), "Loaded chamber ignored");
        });
        test("wrong-caliber creative box is not usable", () -> {
            var a = arm(level); a.setHeldItem(gun(0, false));
            var b = box(ResourceLocation.parse("tacz:762x39"), 1);
            ((IAmmoBox)b.getItem()).setCreative(b, false); a.attachedAmmoBoxes.set(0, b);
            check(!a.hasAnyAmmo(), "Wrong creative caliber accepted");
        });
        test("empty and invalid direct insert rejected", () -> {
            var a = arm(level); a.setHeldItem(gun(0, false));
            check(!a.addAmmoBox(ItemStack.EMPTY), "Accepted empty item");
            check(!a.addAmmoBox(new ItemStack(Items.STONE)), "Accepted stone");
        });
        test("automation simulation and empty-box insertion", () -> {
            var a = arm(level); a.setHeldItem(gun(0, false));
            var b = box(DefaultAssets.EMPTY_AMMO_ID, 0);
            var h = a.getItemHandler();
            check(h.insertItem(0, b, true).isEmpty() && a.getAmmoBox().isEmpty(), "Simulation mutated or refused empty box");
            check(h.insertItem(0, b, false).isEmpty() && !a.getAmmoBox().isEmpty(), "Insert failed");
            check(!h.extractItem(0, 1, true).isEmpty() && !a.getAmmoBox().isEmpty(), "Extraction simulation mutated");
            check(!h.extractItem(0, 1, false).isEmpty() && a.getAmmoBox().isEmpty(), "Extraction failed");
        });
        test("automation invalid slot is harmless", () -> {
            var a = arm(level); a.setHeldItem(gun(0, false)); var b = box(ammo(), 3);
            check(a.getItemHandler().insertItem(-1, b, false).getCount() == 1, "Invalid slot consumed box");
            check(a.getItemHandler().insertItem(2, b, true).getCount() == 1, "Invalid slot simulation");
        });
        test("reload conserves ammo and clears depleted box caliber", () -> {
            var a = arm(level); a.setHeldItem(gun(0, false)); a.addAmmoBox(box(ammo(), 3));
            var fp = SentryFakePlayer.get(a); SentryFakePlayer.sync(fp, a, 0, 0, a.getHeldItem());
            var held = fp.getMainHandItem(); var api = IGun.getIGunOrNull(held);
            boolean result = (boolean)call(a, "performInstantReload",
                    new Class<?>[]{net.neoforged.neoforge.common.util.FakePlayer.class, IGun.class, ItemStack.class}, fp, api, held);
            check(result, "Reload failed");
            check(api.getCurrentAmmoCount(held) + (api.hasBulletInBarrel(held) ? 1 : 0) == 3, "Ammo created/lost");
            var empty = fp.getInventory().getItem(9); var b = (IAmmoBox)empty.getItem();
            check(b.getAmmoCount(empty) == 0 && b.getAmmoId(empty).equals(DefaultAssets.EMPTY_AMMO_ID), "Empty box locked to old caliber");
        });
        test("full ammo boxes cannot be extracted by automation", () -> {
            var a = arm(level); a.setHeldItem(gun(0, false)); a.addAmmoBox(box(ammo(), 3));
            check(a.getItemHandler().extractItem(0, 1, false).isEmpty() && total(a) == 3, "Automation lost loaded box");
        });
        test("same-ID gun replacement resets fake shooter", () -> {
            var a = arm(level); a.setHeldItem(gun(2, true)); var old = SentryFakePlayer.get(a);
            SentryFakePlayer.sync(old, a, 0, 0, a.getHeldItem());
            a.setHeldItem(gun(7, false)); var replacement = SentryFakePlayer.get(a);
            check(old != replacement, "Reused previous shooter state");
            SentryFakePlayer.sync(replacement, a, 0, 0, a.getHeldItem());
            check(IGun.getIGunOrNull(replacement.getMainHandItem()).getCurrentAmmoCount(replacement.getMainHandItem()) == 7, "Wrong replacement magazine");
        });
        test("removed block releases shooter", () -> {
            var a = arm(level); a.setHeldItem(gun(1, false)); var old = SentryFakePlayer.get(a);
            a.setRemoved(); check(SentryFakePlayer.getArmFromPlayer(old) == null, "Removed block retained shooter");
        });
        test("disk round-trip preserves gun components boxes color range link", () -> {
            fixtures.put(POS.east(2), level.getBlockState(POS.east(2)));
            level.setBlock(POS.east(2), SentryRegistry.BLAZE_FIRE_CONTROL.get().defaultBlockState(), 3);
            var a = arm(level); a.initialize(); var g = gun(4, true);
            g.set(DataComponents.CUSTOM_NAME, Component.literal("Phase2 custom gun")); a.setHeldItem(g);
            a.addAmmoBox(box(ammo(), 7)); a.applyColor(DyeColor.BLUE);
            a.rangeScroll.setValue(24); a.setConnectedFireControl(POS.east(2));
            Path file = Path.of("phase2-smoke.nbt");
            try {
                NbtIo.writeCompressed(a.saveWithFullMetadata(level.registryAccess()), file);
                var restored = arm(level); restored.initialize();
                restored.loadWithComponents(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()), level.registryAccess());
                check(ItemStack.matches(g, restored.getHeldItem()), "Gun components lost");
                check(total(restored) == 12 && restored.color.orElseThrow() == DyeColor.BLUE, "Ammo/color lost");
                check(POS.east(2).equals(restored.getConnectedFireControl()) && restored.rangeScroll.getValue() == 24, "Link/range lost");
            } finally { Files.deleteIfExists(file); }
        });
        test("block-entity packet preserves gun and ammo components", () -> {
            var a = arm(level); a.initialize(); var g = gun(4, true);
            g.set(DataComponents.CUSTOM_NAME, Component.literal("Network gun")); a.setHeldItem(g);
            a.addAmmoBox(box(ammo(), 6)); a.applyColor(DyeColor.RED); a.rangeScroll.setValue(20);
            var replica = arm(level); replica.initialize();
            replica.onDataPacket(null, a.getUpdatePacket(), level.registryAccess());
            check(ItemStack.matches(a.getHeldItem(), replica.getHeldItem()) && total(replica) == total(a), "Replicated inventory differs");
            check(replica.color.orElseThrow() == DyeColor.RED && replica.rangeScroll.getValue() == 20, "Replicated settings differ");
        });
        test("invalid fire-control link rejected", () -> {
            var a = arm(level); a.setConnectedFireControl(POS.east(3));
            check(a.getConnectedFireControl() == null, "Linked to air");
        });
        test("chunk unload releases shooter without losing inventory", () -> {
            var a = arm(level); a.setHeldItem(gun(2, true)); a.addAmmoBox(box(ammo(), 4));
            var fp = SentryFakePlayer.get(a); a.onChunkUnloaded();
            check(SentryFakePlayer.getArmFromPlayer(fp) == null && total(a) == 7, "Unload lost ammo or leaked shooter");
        });
        test("destroy drops gun and box exactly once", () -> {
            BlockPos p = POS.east(4); fixtures.put(p, level.getBlockState(p));
            level.setBlock(p, SentryRegistry.SENTRY_ARM_BLOCK.get().defaultBlockState(), 3);
            var placed = (SentryArmBlockEntity)level.getBlockEntity(p);
            var g = gun(3, true); placed.setHeldItem(g); placed.addAmmoBox(box(ammo(), 9));
            var fp = SentryFakePlayer.get(placed);
            level.destroyBlock(p, false);
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(p).inflate(2));
            try {
                int guns = 0, boxes = 0;
                for (var drop : drops) {
                    if (drop.getItem().getItem() instanceof IGun) { guns += drop.getItem().getCount(); check(ItemStack.matches(g, drop.getItem()), "Gun components changed"); }
                    if (drop.getItem().getItem() instanceof IAmmoBox b) { boxes += drop.getItem().getCount(); check(b.getAmmoCount(drop.getItem()) == 9, "Dropped box lost ammo"); }
                }
                check(guns == 1 && boxes == 1, "Wrong drop counts: " + guns + "/" + boxes);
                check(SentryFakePlayer.getArmFromPlayer(fp) == null, "Dropped block leaked shooter");
            } finally { drops.forEach(ItemEntity::discard); }
        });
        test("fire-control list and focus select correct target", () -> {
            var a = arm(level); a.initialize(); a.setHeldItem(gun(5, true)); a.rangeScroll.setValue(32);
            var fc = (BlazeFireControlBlockEntity)level.getBlockEntity(POS.east(2));
            var listed = target(level, POS.south(11)); listed.setCustomName(Component.literal("Phase2Listed"));
            var other = target(level, POS.south(9)); other.setCustomName(Component.literal("Phase2Other"));
            try {
                var clip = new ItemStack(SentryRegistry.FIRE_CONTROL_CLIPBOARD.get());
                CompoundTag tag = new CompoundTag(); ListTag names = new ListTag(); names.add(StringTag.valueOf("Phase2Listed"));
                tag.put("TargetList", names); tag.putBoolean("WhitelistMode", false);
                euphy.upo.sentrymechanicalarm.util.ItemNBTHelper.setTag(clip, tag);
                fc.inventory.setStackInSlot(0, clip); a.setConnectedFireControl(fc.getBlockPos());
                call(a, "scanForTarget", new Class<?>[0]);
                check(a.saveWithFullMetadata(level.registryAccess()).getInt("TargetId") == listed.getId(), "Attack list ignored");
                tag.putBoolean("WhitelistMode", true);
                euphy.upo.sentrymechanicalarm.util.ItemNBTHelper.setTag(clip, tag);
                fc.inventory.setStackInSlot(0, clip); a.updateFromFireControl(); call(a, "scanForTarget", new Class<?>[0]);
                check(a.saveWithFullMetadata(level.registryAccess()).getInt("TargetId") == other.getId(), "Protected name attacked");
                fc.setFocusedEntity(listed.getId()); a.updateFromFireControl(); call(a, "scanForTarget", new Class<?>[0]);
                check(a.saveWithFullMetadata(level.registryAccess()).getInt("TargetId") == listed.getId(), "Explicit focus ignored");
            } finally { listed.discard(); other.discard(); fc.inventory.setStackInSlot(0, ItemStack.EMPTY); fc.setFocusedEntity(-1); }
        });
        test("shoot packet round-trip preserves ammo and action", () -> {
            var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                var packet = new euphy.upo.sentrymechanicalarm.network.SentryShootPacket(POS, -1,
                        euphy.upo.sentrymechanicalarm.util.ItemNBTHelper.getTag(gun(5, true)),
                        POS.getCenter(), POS.south(10).getCenter(),
                        euphy.upo.sentrymechanicalarm.network.SentryShootPacket.ActionType.SHOOT);
                euphy.upo.sentrymechanicalarm.network.SentryShootPacket.CODEC.encode(buf, packet);
                check(packet.equals(euphy.upo.sentrymechanicalarm.network.SentryShootPacket.CODEC.decode(buf)), "Packet state changed");
            } finally { buf.release(); }
        });
        test("nearby network dispatch excludes other dimensions and far players", () -> {
            var nearby = new RecordingPlayer(level); nearby.setPos(POS.getCenter());
            var distant = new RecordingPlayer(level); distant.setPos(POS.getCenter().add(100,0,0));
            var nether = event.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            var otherDimension = new RecordingPlayer(nether); otherDimension.setPos(POS.getCenter());
            level.players().add(nearby); level.players().add(distant); nether.players().add(otherDimension);
            try {
                euphy.upo.sentrymechanicalarm.network.NetworkHandler.sendToNearby(
                        new euphy.upo.sentrymechanicalarm.network.SentryShootPacket(POS, -1, new CompoundTag(),
                                POS.getCenter(), POS.getCenter(), euphy.upo.sentrymechanicalarm.network.SentryShootPacket.ActionType.SHOOT), level, POS);
                check(nearby.sentryPackets == 1 && distant.sentryPackets == 0 && otherDimension.sentryPackets == 0,
                        "Incorrect recipients: " + nearby.sentryPackets + "/" + distant.sentryPackets + "/" + otherDimension.sentryPackets);
            } finally { level.players().remove(nearby); level.players().remove(distant); nether.players().remove(otherDimension); }
        });
        test("gun interaction honors hand reach and spectator state", () -> {
            BlockPos p = POS.west(4); fixtures.put(p, level.getBlockState(p));
            level.setBlock(p, SentryRegistry.SENTRY_ARM_BLOCK.get().defaultBlockState(), 3);
            var placed = (SentryArmBlockEntity)level.getBlockEntity(p);
            var player = new RecordingPlayer(level); player.setPos(p.getCenter().add(0,0,2));
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, gun(2, true));
            euphy.upo.sentrymechanicalarm.network.SentryInteractPacket.handle(
                    new euphy.upo.sentrymechanicalarm.network.SentryInteractPacket(p, net.minecraft.world.InteractionHand.OFF_HAND, false), player);
            check(!placed.getHeldItem().isEmpty() && player.getOffhandItem().isEmpty(), "Valid off-hand transfer failed");
            placed.setHeldItem(ItemStack.EMPTY); player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, gun(2, true));
            player.setPos(p.getCenter().add(100,0,0));
            euphy.upo.sentrymechanicalarm.network.SentryInteractPacket.handle(
                    new euphy.upo.sentrymechanicalarm.network.SentryInteractPacket(p, net.minecraft.world.InteractionHand.MAIN_HAND, false), player);
            check(placed.getHeldItem().isEmpty() && !player.getMainHandItem().isEmpty(), "Remote packet changed inventory");
            player.setPos(p.getCenter().add(0,0,2)); player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            euphy.upo.sentrymechanicalarm.network.SentryInteractPacket.handle(
                    new euphy.upo.sentrymechanicalarm.network.SentryInteractPacket(p, net.minecraft.world.InteractionHand.MAIN_HAND, false), player);
            check(placed.getHeldItem().isEmpty(), "Spectator modified gun");
            player.discard();
        });
        try {
            active = arm(level); active.initialize(); active.setHeldItem(gun(4, true)); active.addAmmoBox(box(ammo(), 100));
            active.rangeScroll.setValue(32); active.suppliedSpeed = 256;
            first = target(level, POS.south(14));
            checkpoint = total(active);
            var chunk = new net.minecraft.world.level.ChunkPos(PLACED);
            forcedByTest = !level.getForcedChunks().contains(chunk.toLong());
            if (forcedByTest) level.setChunkForced(chunk.x, chunk.z, true);
            level.getChunkAt(PLACED);
            fixtures.put(PLACED, level.getBlockState(PLACED));
            fixtures.put(PLACED.east(), level.getBlockState(PLACED.east()));
            fixtures.put(PLACED.east().below(), level.getBlockState(PLACED.east().below()));
            // Mechanical arms mesh as small cogwheels; they have no axial shaft port.
            level.setBlock(PLACED.east().below(), com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState()
                    .setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, net.minecraft.core.Direction.UP), 3);
            level.setBlock(PLACED.east(), com.simibubi.create.AllBlocks.COGWHEEL.getDefaultState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.Y), 3);
            level.setBlock(PLACED, SentryRegistry.SENTRY_ARM_BLOCK.get().defaultBlockState(), 3);
            motor = (com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)level.getBlockEntity(PLACED.east().below());
            motor.initialize(); motor.generatedSpeed.setValue(256);
            placedArm = (SentryArmBlockEntity)level.getBlockEntity(PLACED); placedArm.initialize();
            placedArm.setHeldItem(gun(20, true)); placedArm.addAmmoBox(box(ammo(), 100)); placedArm.rangeScroll.setValue(32);
            placedTarget = target(level, PLACED.south(14)); placedCheckpoint = total(placedArm);
        } catch (Throwable e) { failed++; SentryMechanicalArm.LOGGER.error("PHASE2 setup failed", e); finish(event.getServer()); }
    }
    private static Zombie target(ServerLevel level, BlockPos p) {
        var z = new Zombie(EntityType.ZOMBIE, level); z.setPos(p.getX()+.5, p.getY(), p.getZ()+.5);
        z.setNoAi(true); z.setNoGravity(true); z.setInvulnerable(true); z.setPersistenceRequired();
        level.addFreshEntity(z); return z;
    }
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (active == null) return;
        try {
            // Projectile knockback must not move our stationary target out of range.
            if (first != null && !first.isRemoved()) {
                first.setPos(POS.getX()+.5, POS.getY(), POS.getZ()+14.5);
                first.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            }
            if (second != null && !second.isRemoved()) {
                second.setPos(POS.getX()+3.5, POS.getY(), POS.getZ()+13.5);
                second.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            }
            if (placedTarget != null && !placedTarget.isRemoved()) {
                placedTarget.setPos(PLACED.getX()+.5, PLACED.getY(), PLACED.getZ()+14.5);
                placedTarget.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            }
            UpstreamRepairSmoke.tick();
            active.tick(); ticks++;
            if (ticks == 160) {
                test("placed sentry receives Create motor power and fires", () ->
                        check(Math.abs(placedArm.getSpeed()) > 0 && total(placedArm) < placedCheckpoint,
                                "World sentry failed: speed="+placedArm.getSpeed()+" ammo="+total(placedArm)));
                motor.generatedSpeed.setValue(0);
                test("powered sentry acquires hostile and fires", () -> check(total(active) < checkpoint, "No ammo consumed while powered"));
                test("live shooting and auto-reload conserve finite ammunition", () ->
                        check(fired > 5 && checkpoint - total(active) == fired,
                                "Ammo loss=" + (checkpoint-total(active)) + " actual shots=" + fired));
                active.suppliedSpeed = 0; checkpoint = total(active);
            }
            if (ticks == 180) placedCheckpoint = total(placedArm);
            if (ticks == 200) {
                test("Create motor shutdown stops placed sentry", () ->
                        check(placedArm.getSpeed() == 0 && total(placedArm) == placedCheckpoint, "World sentry fired without power"));
                motor.generatedSpeed.setValue(256);
                test("power loss stops firing", () -> check(total(active) == checkpoint, "Fired without power"));
                active.suppliedSpeed = 256;
            }
            if (ticks == 280) {
                test("Create motor restoration resumes placed sentry", () ->
                        check(Math.abs(placedArm.getSpeed()) > 0 && total(placedArm) < placedCheckpoint, "World sentry did not resume"));
                test("power restoration resumes firing", () -> check(total(active) < checkpoint,
                        "Did not resume: ammo=" + checkpoint + "->" + total(active) + " status=" + active.getSentryStatus()
                        + " angles=" + active.baseAngle.getValue()+","+active.headAngle.getValue()+","+active.upperArmAngle.getValue()
                        + " target=" + active.saveWithFullMetadata(active.getLevel().registryAccess()).getInt("TargetId")));
                checkpoint = total(active);
                // Fresh ammunition permits obstruction testing even if earlier checks used all rounds.
                active.setHeldItem(gun(20, true)); active.attachedAmmoBoxes.set(0, box(ammo(), 20));
                for (BlockPos p : BlockPos.betweenClosed(POS.offset(-4,-2,7), POS.offset(4,6,7))) {
                    var key = p.immutable(); wall.put(key, active.getLevel().getBlockState(key));
                    active.getLevel().setBlock(key, Blocks.OBSIDIAN.defaultBlockState(), 3);
                }
            }
            if (ticks == 310) checkpoint = total(active); // Allow cached visibility to refresh.
            if (ticks == 350) {
                test("solid obstruction prevents continued firing", () -> check(total(active) == checkpoint, "Shot through obstruction"));
                restoreWall(); checkpoint = total(active);
            }
            if (ticks == 450) {
                test("removing obstruction resumes acquisition", () -> check(total(active) < checkpoint, "Did not reacquire"));
                first.discard(); second = target((ServerLevel)active.getLevel(), POS.south(13).east(3)); checkpoint = total(active);
            }
            if (ticks == 550) {
                test("removed target is replaced", () -> check(total(active) < checkpoint, "Did not rescan target"));
                active.setHeldItem(gun(0, false)); active.attachedAmmoBoxes.set(0, box(DefaultAssets.EMPTY_AMMO_ID, 0));
            }
            if (ticks == 600) {
                test("empty gun and box enter no-ammo state", () ->
                        check(active.getSentryStatus() == SentryArmBlockEntity.SentryStatus.NO_AMMO, "Did not report empty"));
                active.attachedAmmoBoxes.set(0, box(ammo(), 5)); checkpoint = fired;
            }
            if (ticks == 700) {
                test("resupply wakes an empty sentry", () -> check(fired > checkpoint, "Resupply did not resume fire"));
                test("resupply conserves ammunition", () -> check(5 - total(active) == fired - checkpoint, "Resupply duplicated/lost ammunition"));
                finish(event.getServer());
            }
        } catch (Throwable e) { failed++; SentryMechanicalArm.LOGGER.error("PHASE2 tick failure", e); finish(event.getServer()); }
    }
    private static void restoreWall() {
        if (active != null) wall.forEach((p,s) -> active.getLevel().setBlock(p,s,3)); wall.clear();
    }
    private static void finish(MinecraftServer server) {
        restoreWall(); if (first != null) first.discard(); if (second != null) second.discard();
        if (placedTarget != null) placedTarget.discard();
        for (var a : arms) SentryFakePlayer.remove(a);
        fixtures.forEach((p,s) -> server.overworld().setBlock(p,s,3)); fixtures.clear();
        for (BlockPos p : List.of(POS, PLACED)) server.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(p).inflate(8)).forEach(ItemEntity::discard);
        if (forcedByTest) { var chunk = new net.minecraft.world.level.ChunkPos(PLACED); server.overworld().setChunkForced(chunk.x,chunk.z,false); }
        SentryMechanicalArm.LOGGER.info("PHASE2 RESULT passed={} failed={}", passed, failed);
        active = null; arms.clear(); server.halt(false);
    }
}
