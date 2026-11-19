package euphy.upo.sentrymechanicalarm.smoke;

import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.input.InteractKey;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import java.util.concurrent.CompletableFuture;

/** Exercises the transformed TaCZ key handler and real client/server block interactions. */
final class TaczHotfixClientSmoke {
    private static int phase, waitTicks, scenario;
    private static BlockPos pos;
    private static CompletableFuture<Void> pending;
    private static InputConstants.Key originalKey;
    private static boolean done;

    static boolean run() {
        if (done) return true;
        var mc = Minecraft.getInstance();
        try {
            var server = mc.getSingleplayerServer();
            if (server == null) throw new AssertionError("Integrated test server missing");
            if (phase == 0) {
                originalKey = InteractKey.INTERACT_KEY.getKey();
                pos = mc.player.blockPosition().east(2);
                pending = server.submit(() -> {
                    var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                    player.setGameMode(GameType.SURVIVAL);
                    player.getInventory().clearContent();
                    player.serverLevel().setBlock(pos, SentryRegistry.SENTRY_ARM_BLOCK.get().defaultBlockState(), 3);
                    player.setItemInHand(InteractionHand.MAIN_HAND,
                            GunItemBuilder.create().setId(ResourceLocation.parse("tacz:glock_17")).build());
                    player.inventoryMenu.broadcastChanges();
                });
                phase = 1; waitTicks = 0;
                return false;
            }
            if (phase == 1) {
                if (!pending.isDone()) return false;
                pending.join();
                if (++waitTicks < 20) return false;
                if (!(mc.player.getMainHandItem().getItem() instanceof IGun)
                        || !(mc.level.getBlockEntity(pos) instanceof SentryArmBlockEntity))
                    throw new AssertionError("Test inventory or block did not synchronize");
                mc.setScreen(null);
                InteractKey.INTERACT_KEY.setKey(scenario == 1
                        ? InputConstants.Type.KEYSYM.getOrCreate(79) : mc.options.keyUse.getKey());
                boolean shared = InteractKey.sharesUseBinding(mc.options.keyUse);
                if (shared != (scenario != 1)) throw new AssertionError("Unexpected key binding state");
                var hit = new BlockHitResult(pos.getCenter(), Direction.UP, pos, false);
                mc.hitResult = hit;
                boolean handled = scenario == 2
                        ? InteractKey.onInteractControllerPress(true) : InteractKey.onInteractPress();
                if (!handled) throw new AssertionError("TaCZ rejected the interaction");
                if (scenario == 0) mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                phase = 2; waitTicks = 0;
                return false;
            }
            if (phase == 2) {
                if (++waitTicks < 20) return false;
                pending = server.submit(() -> {
                    var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                    var arm = (SentryArmBlockEntity) player.serverLevel().getBlockEntity(pos);
                    if (!(arm.getHeldItem().getItem() instanceof IGun) || !player.getMainHandItem().isEmpty())
                        throw new AssertionError("Gun not installed exactly once, scenario=" + scenario);
                    SentryMechanicalArm.LOGGER.info("TACZ_HOTFIX_CLIENT PASS {} installs exactly one gun",
                            new String[] {"shared use binding", "independent interact binding", "controller with shared binding"}[scenario]);
                    arm.setHeldItem(ItemStack.EMPTY); arm.sendData();
                    player.setItemInHand(InteractionHand.MAIN_HAND,
                            GunItemBuilder.create().setId(ResourceLocation.parse("tacz:glock_17")).build());
                    player.inventoryMenu.broadcastChanges();
                });
                phase = 3;
                return false;
            }
            if (!pending.isDone()) return false;
            pending.join();
            if (++scenario < 3) { phase = 1; waitTicks = 0; return false; }
            SentryMechanicalArm.LOGGER.info("TACZ_HOTFIX_CLIENT RESULT passed=3 failed=0");
        } catch (Throwable error) {
            ClientGeometrySmoke.extraFailures++;
            SentryMechanicalArm.LOGGER.error("TACZ_HOTFIX_CLIENT FAIL", error);
        }
        if (originalKey != null) InteractKey.INTERACT_KEY.setKey(originalKey);
        done = true;
        return true;
    }
}
