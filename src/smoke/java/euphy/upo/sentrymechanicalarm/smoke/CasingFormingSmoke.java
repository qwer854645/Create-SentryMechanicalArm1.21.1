package euphy.upo.sentrymechanicalarm.smoke;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.deployer.*;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import euphy.upo.sentrymechanicalarm.*;
import euphy.upo.sentrymechanicalarm.content.*;
import euphy.upo.sentrymechanicalarm.network.CasingMoldSelectPacket;
import euphy.upo.sentrymechanicalarm.recipe.*;
import euphy.upo.sentrymechanicalarm.registry.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import java.util.*;

import static euphy.upo.sentrymechanicalarm.smoke.ReloadingSmoke.*;

final class CasingFormingSmoke {
    static final BlockPos POS = DEPOT.offset(8, 0, 0);
    static DepotBlockEntity formed;
    static DeployerBlockEntity moldHand;
    static int suppliedAt = -1;
    static boolean configured;

    static void verify() {
        test("forming budgets include casing metal and direct projectile metal", () -> {
            var nativeRecipe = ReloadingCostSmoke.original("tacz:9mm");
            var plan = CasingFormingRecipes.plan(nativeRecipe).orElseThrow();
            check(plan.metal() == Items.COPPER_INGOT && plan.yield() == 5, "9mm must use one copper ingot for five shells");
            // Independent fixed references: native 9mm 1.8 nuggets/round, 5.56 3 nuggets/round.
            check(9.0 / plan.yield() == 1.8, "fresh 9mm underprices native main metal");
            var rifle = CasingFormingRecipes.plan(ReloadingCostSmoke.original("tacz:556x45")).orElseThrow();
            check(rifle.metal() == AllItems.COPPER_NUGGET.get() && rifle.yield() == 1 && 1.0 / rifle.yield() + 2 == 3, "fresh 5.56 exceeds native copper budget");
            check(new java.util.HashSet<>(CasingFormingRecipes.choices(level.getRecipeManager())).equals(euphy.upo.sentrymechanicalarm.util.SentryCasingProfiles.eligible()), "eligible cases missing mold recipes");
            var complex=ReloadingCostSmoke.synthetic(10, ReloadingCostSmoke.material(Items.COPPER_INGOT,2),ReloadingCostSmoke.material(Items.DIAMOND,1));
            check(CasingFormingRecipes.plan(complex).isPresent(),"payload material blocked shell-metal extraction");
            for (String id : List.of("tacz:rpg_rocket", "ars_armorer:mana", "applied_armorer:fluix_battery"))
                check(CasingFormingRecipes.find(level.getRecipeManager(), ResourceLocation.parse(id)).isEmpty(), "special case manufactured");
        });
        test("generic fallback can be disabled and only changes unknown-cost yields", () -> {
            var all=new java.util.HashSet<>(CasingFormingRecipes.choices(level.getRecipeManager()));
            boolean previous=SMAServerConfig.GENERIC_CASING_FORMING.get();int yield=SMAServerConfig.DEFAULT_CASING_YIELD.get();
            boolean legacy=SMAServerConfig.LEGACY_RELOADING_SOURCES.get(),restore=SMAServerConfig.RESTORE_LEGACY_AMMO_RECIPES.get();
            try {
                SMAServerConfig.RESTORE_LEGACY_AMMO_RECIPES.set(false);SMAServerConfig.LEGACY_RELOADING_SOURCES.set(false);
                SMAServerConfig.GENERIC_CASING_FORMING.set(false);DynamicRecipeManager.rebuild(level.getRecipeManager());
                var nativeOnly=new java.util.HashSet<>(CasingFormingRecipes.choices(level.getRecipeManager()));
                var fallback=new java.util.HashSet<>(all);fallback.removeAll(nativeOnly);
                check(!fallback.isEmpty()&&nativeOnly.contains(AMMO),"fallback and native rules not separated");
                SMAServerConfig.GENERIC_CASING_FORMING.set(true);SMAServerConfig.DEFAULT_CASING_YIELD.set(3);DynamicRecipeManager.rebuild(level.getRecipeManager());
                var changed=CasingFormingRecipes.find(level.getRecipeManager(),fallback.iterator().next()).orElseThrow().value().getResultItem(level.registryAccess());
                check(changed.getCount()==3,"fallback yield config ignored");
                check(CasingFormingRecipes.find(level.getRecipeManager(),AMMO).orElseThrow().value().getResultItem(level.registryAccess()).getCount()==5,"fallback config changed a native budget");
            }finally{SMAServerConfig.RESTORE_LEGACY_AMMO_RECIPES.set(restore);SMAServerConfig.LEGACY_RELOADING_SOURCES.set(legacy);SMAServerConfig.GENERIC_CASING_FORMING.set(previous);SMAServerConfig.DEFAULT_CASING_YIELD.set(yield);DynamicRecipeManager.rebuild(level.getRecipeManager());}
        });
        test("mold identity recipe codecs and reusable tool flag", () -> {
            var holder = CasingFormingRecipes.find(level.getRecipeManager(), AMMO).orElseThrow();
            var recipe = (DeployerApplicationRecipe) holder.value();
            check(recipe.shouldKeepHeldItem(), "mold is consumed");
            var inv = new net.neoforged.neoforge.items.ItemStackHandler(2);
            inv.setStackInSlot(0, new ItemStack(Items.COPPER_INGOT));
            inv.setStackInSlot(1, CasingMoldItem.create(AMMO));
            var wrapper = new net.neoforged.neoforge.items.wrapper.RecipeWrapper(inv);
            check(recipe.matches(wrapper, level), "correct mold rejected");
            inv.setStackInSlot(1, CasingMoldItem.create(ResourceLocation.parse("tacz:12g")));
            check(!recipe.matches(wrapper, level), "wrong mold caliber accepted");
            inv.setStackInSlot(1, new ItemStack(SentryRegistry.CASING_MOLD.get()));
            check(!recipe.matches(wrapper, level), "blank mold accepted");
            var ops = level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
            var copy = (DeployerApplicationRecipe) Recipe.CODEC.parse(ops, Recipe.CODEC.encodeStart(ops, recipe).getOrThrow()).getOrThrow();
            check(copy.shouldKeepHeldItem() && copy.getRequiredHeldItem().test(CasingMoldItem.create(AMMO)), "JSON lost mold identity or tool reuse");
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
            try { RecipeHolder.STREAM_CODEC.encode(buffer, holder); var restored = (DeployerApplicationRecipe) RecipeHolder.STREAM_CODEC.decode(buffer).value();
                check(restored.shouldKeepHeldItem() && restored.getRequiredHeldItem().test(CasingMoldItem.create(AMMO)), "network lost reusable mold");
            } finally { buffer.release(); }
            var mold = CasingMoldItem.create(AMMO);
            check(ItemStack.matches(mold, ItemStack.parse(level.registryAccess(), mold.save(level.registryAccess())).orElseThrow()), "mold disk identity changed");
        });
        test("mold selection packet validates held item caliber and hand", () -> {
            var player = new net.minecraft.server.level.ServerPlayer(level.getServer(), level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "MoldPlayer"), net.minecraft.server.level.ClientInformation.createDefault());
            player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(), new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player, net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
            };
            try {
                var mold = new ItemStack(SentryRegistry.CASING_MOLD.get()); player.setItemInHand(InteractionHand.OFF_HAND, mold);
                CasingMoldSelectPacket.handle(new CasingMoldSelectPacket(false, AMMO), player);
                check(EmptyCasingItem.ammoId(mold) == null, "wrong hand mutated mold");
                CasingMoldSelectPacket.handle(new CasingMoldSelectPacket(true, ResourceLocation.parse("unknown:ammo")), player);
                check(EmptyCasingItem.ammoId(mold) == null, "unknown caliber accepted");
                CasingMoldSelectPacket.handle(new CasingMoldSelectPacket(true, AMMO), player);
                check(AMMO.equals(EmptyCasingItem.ammoId(mold)), "valid offhand selection rejected");
                var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
                try { var packet = new CasingMoldSelectPacket(true, AMMO); CasingMoldSelectPacket.CODEC.encode(buffer, packet); check(packet.equals(CasingMoldSelectPacket.CODEC.decode(buffer)), "selection packet codec mismatch"); }
                finally { buffer.release(); }
            } finally { player.discard(); }
        });
        test("forming switch removes generated recipes without removing reloading", () -> {
            try { SMAServerConfig.ENABLE_CASING_FORMING.set(false); DynamicRecipeManager.rebuild(level.getRecipeManager());
                check(CasingFormingRecipes.find(level.getRecipeManager(), AMMO).isEmpty(), "disabled forming remains");
                check(ReloadingRecipes.find(level.getRecipeManager(), AMMO).isPresent(), "forming switch disabled reloading");
            } finally { SMAServerConfig.ENABLE_CASING_FORMING.set(true); DynamicRecipeManager.rebuild(level.getRecipeManager()); }
        });
    }
    static void setup() {
        level.setBlock(POS, AllBlocks.DEPOT.get().defaultBlockState(), 3);
        formed = (DepotBlockEntity) level.getBlockEntity(POS); formed.setHeldItem(new ItemStack(AllItems.COPPER_NUGGET.get()));
        level.setBlock(POS.above(2), AllBlocks.DEPLOYER.get().defaultBlockState().setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING, Direction.DOWN), 3);
        moldHand = (DeployerBlockEntity) level.getBlockEntity(POS.above(2));
    }
    static void tick() {
        if (moldHand == null) return; moldHand.setSpeed(256);
        if (moldHand.getPlayer() == null) return;
        if (suppliedAt < 0) { suppliedAt = ticks; moldHand.getPlayer().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SentryRegistry.CASING_MOLD.get())); }
        if (!configured && ticks - suppliedAt >= 40) {
            test("blank mold leaves metal untouched on a real depot", () -> check(formed.getHeldItem().is(AllItems.COPPER_NUGGET.get()) && formed.getHeldItem().getCount() == 1, "unconfigured tool processed metal"));
            formed.setHeldItem(new ItemStack(Items.COPPER_INGOT));
            moldHand.getPlayer().setItemInHand(InteractionHand.MAIN_HAND, CasingMoldItem.create(AMMO)); configured = true;
        }
    }
    static boolean ready() { return formed != null && formed.getHeldItem().is(SentryRegistry.EMPTY_CASING.get()) && AMMO.equals(EmptyCasingItem.ammoId(formed.getHeldItem())) && formed.getHeldItem().getCount() == 5; }
    static ItemStack take() {
        test("real mold forms five stack-compatible cases without tool wear", () -> {
            check(ready(), "wrong fresh case yield");
            check(ItemStack.isSameItemSameComponents(formed.getHeldItem(), casing(AMMO, 1)), "fresh and recovered cases do not stack");
            check(ItemStack.matches(moldHand.getPlayer().getMainHandItem(), CasingMoldItem.create(AMMO)), "mold consumed or changed");
        });
        var result = formed.getHeldItem().split(1); formed.setChanged(); return result;
    }
}
