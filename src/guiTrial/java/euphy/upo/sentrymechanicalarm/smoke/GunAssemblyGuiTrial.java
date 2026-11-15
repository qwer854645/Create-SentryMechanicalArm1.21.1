package euphy.upo.sentrymechanicalarm.smoke;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.client.GunAssemblyScreen;
import euphy.upo.sentrymechanicalarm.content.ReceiverComponentItem;
import euphy.upo.sentrymechanicalarm.network.GunAssemblySelectPacket;
import euphy.upo.sentrymechanicalarm.recipe.*;
import euphy.upo.sentrymechanicalarm.registry.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

/** Opt-in integrated-client trial; excluded from normal builds. */
@EventBusSubscriber(modid = "sentrymechanicalarm", value = Dist.CLIENT)
public final class GunAssemblyGuiTrial {
    private static boolean queued, opened, shot;
    private static volatile boolean ready;
    private static int frames;
    private static void expect(boolean condition, String description) {
        if (!condition) throw new IllegalStateException(description);
        SentryMechanicalArm.LOGGER.info("GUN_GUI_TRIAL PASS {}", description);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("sentry.guiTrial")) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        if (!queued) {
            queued = true; var uuid = mc.player.getUUID(); var server = mc.getSingleplayerServer();
            server.execute(() -> {
                try {
                    var player = server.getPlayerList().getPlayer(uuid); if (player == null) throw new IllegalStateException("No trial player");
                    var choices = GunAssemblyRecipes.choices(player.serverLevel());
                    var choice = choices.stream().filter(c -> c.input().receiver().test(new ItemStack(GunParts.item("precision_receiver_component"))))
                            .findFirst().orElseThrow();
                    var core = new ItemStack(GunParts.item("precision_receiver_component"));
                    core.set(DataComponents.CUSTOM_NAME, Component.literal("Trial Receiver"));
                    var old = player.getMainHandItem().copy();
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GunParts.item("receiver_component")));
                    GunAssemblySelectPacket.handle(new GunAssemblySelectPacket(false, choice.id()), player);
                    expect(player.getMainHandItem().is(GunParts.item("receiver_component")), "wrong receiver cannot prepare precision target");
                    player.setItemInHand(InteractionHand.MAIN_HAND, core.copy());
                    GunAssemblySelectPacket.handle(new GunAssemblySelectPacket(false, choice.id()), player);
                    var prepared = player.getMainHandItem().copy();
                    expect(choice.input().test(prepared), "selection produces the exact pending target");
                    expect(!choice.input().test(core), "unselected receiver cannot start gun sequence");
                    expect(choices.stream().filter(c -> !c.id().equals(choice.id())).noneMatch(c -> c.input().test(prepared)), "pending target cannot enter another gun sequence");
                    expect(ItemStack.isSameItemSameComponents(core, prepared.get(ModDataComponents.GUN_RECEIVER.get()).copyOne()), "selection preserves original receiver components");
                    var copy = GunAssemblyCopying.recipe(choice, core).value();
                    var inv = new ItemStackHandler(2); inv.setStackInSlot(0, core.copy()); inv.setStackInSlot(1, prepared.copy());
                    expect(copy.matches(new RecipeWrapper(inv), player.serverLevel()) && copy.shouldKeepHeldItem(), "deployer copies target with an unconsumed tool");
                    var deployer = (com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity)com.simibubi.create.AllBlocks.DEPLOYER.get()
                            .newBlockEntity(player.blockPosition(), com.simibubi.create.AllBlocks.DEPLOYER.getDefaultState());
                    deployer.setLevel(player.serverLevel());
                    var search = new com.simibubi.create.content.kinetics.deployer.DeployerRecipeSearchEvent(deployer, new RecipeWrapper(inv));
                    GunAssemblyCopying.search(search);
                    expect(search.getRecipe() != null && ((DeployerApplicationRecipe)search.getRecipe().value()).shouldKeepHeldItem(), "deployer search selects the reusable copying recipe");
                    var copied = copy.getRollableResults().getFirst().getStack();
                    expect(choice.input().test(copied) && ItemStack.isSameItemSameComponents(core, copied.get(ModDataComponents.GUN_RECEIVER.get()).copyOne()), "copy output preserves target and source receiver");
                    var first = (DeployerApplicationRecipe)choice.recipe().getSequence().getFirst().getRecipe();
                    inv.setStackInSlot(0, prepared.copy()); inv.setStackInSlot(1, first.getRequiredHeldItem().getItems()[0].copy());
                    var next = SequencedAssemblyRecipe.getRecipe(player.serverLevel(), new RecipeWrapper(inv), AllRecipeTypes.DEPLOYING.getType(), DeployerApplicationRecipe.class).orElseThrow();
                    var advanced = next.value().rollResults(player.serverLevel().random).getFirst();
                    expect(advanced.has(AllDataComponents.SEQUENCED_ASSEMBLY) && choice.id().equals(advanced.get(ModDataComponents.GUN_WORK.get())), "first production operation retains exact target and advances progress");
                    inv.setStackInSlot(0, core.copy()); inv.setStackInSlot(1, advanced.copy());
                    var invalid = new com.simibubi.create.content.kinetics.deployer.DeployerRecipeSearchEvent(deployer, new RecipeWrapper(inv));
                    GunAssemblyCopying.search(invalid);
                    expect(invalid.getRecipe() == null, "processed workpieces cannot be copying tools");
                    player.setItemInHand(InteractionHand.MAIN_HAND, prepared.copy()); player.setShiftKeyDown(true);
                    GunParts.WORKPIECE.get().use(player.serverLevel(), player, InteractionHand.MAIN_HAND); player.setShiftKeyDown(false);
                    expect(ItemStack.isSameItemSameComponents(player.getMainHandItem(), core), "unprocessed target refunds original receiver");
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GunParts.item("precision_receiver_component")));
                    if (!old.isEmpty()) player.getInventory().placeItemBackInInventory(old);
                    player.getInventory().setChanged(); player.containerMenu.broadcastChanges();
                    SentryMechanicalArm.LOGGER.info("GUN_GUI_TRIAL RESULT PASS choices={}", choices.size()); ready = true;
                } catch (Throwable failure) { SentryMechanicalArm.LOGGER.error("GUN_GUI_TRIAL RESULT FAIL", failure); }
            });
        }
        if (ready && !opened && mc.player.getMainHandItem().getItem() instanceof ReceiverComponentItem) {
            GunAssemblyScreen.open(InteractionHand.MAIN_HAND); opened = true;
        }
        if (opened && !shot && mc.screen instanceof GunAssemblyScreen && ++frames > 25) {
            Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(), message -> SentryMechanicalArm.LOGGER.info("GUN_GUI_TRIAL screenshot {}", message.getString()));
            shot = true;
        }
    }
}
