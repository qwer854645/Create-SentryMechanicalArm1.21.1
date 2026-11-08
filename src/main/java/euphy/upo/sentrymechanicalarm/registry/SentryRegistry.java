package euphy.upo.sentrymechanicalarm.registry;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.stress.BlockStressValues;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.content.*;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.IContainerFactory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;


import static com.simibubi.create.foundation.data.TagGen.pickaxeOnly;
import static euphy.upo.sentrymechanicalarm.SentryMechanicalArm.MODID;

public class SentryRegistry {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredHolder<Block, SentryArmBlock> SENTRY_ARM_BLOCK = BLOCKS.register("sentry_mechanical_arm",
            id -> new SentryArmBlock(id, BlockBehaviour.Properties.of()));

    public static final DeferredHolder<Block,CasingCollectorBlock> CASING_COLLECTOR_BLOCK=BLOCKS.register("casing_collector",
            ()->new CasingCollectorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f).sound(net.minecraft.world.level.block.SoundType.METAL)));
    public static final DeferredHolder<Item,BlockItem> CASING_COLLECTOR_ITEM=ITEMS.register("casing_collector",()->new BlockItem(CASING_COLLECTOR_BLOCK.get(),new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<CasingCollectorBlockEntity>> CASING_COLLECTOR_BE=BLOCK_ENTITIES.register("casing_collector",
            ()->BlockEntityType.Builder.of(CasingCollectorBlockEntity::new,CASING_COLLECTOR_BLOCK.get()).build(null));

    public static final DeferredHolder<Item, ApplePieItem> APPLE_PIE = ITEMS.register("apple_pie",
            () -> new ApplePieItem(new Item.Properties().food(new FoodProperties.Builder().nutrition(8).saturationModifier(0.3f).build())));

    public static final DeferredHolder<Item, CasingMoldItem> CASING_MOLD = euphy.upo.sentrymechanicalarm.SMAStartupConfig.ENABLE_CASINGS.get() ? ITEMS.register("casing_mold",
            () -> new CasingMoldItem(new Item.Properties().stacksTo(1))) : DeferredHolder.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(MODID,"casing_mold"));

    public static final DeferredHolder<Item, EmptyCasingItem> EMPTY_CASING = euphy.upo.sentrymechanicalarm.SMAStartupConfig.ENABLE_CASINGS.get() ? ITEMS.register("empty_casing",
            () -> new EmptyCasingItem(new Item.Properties().stacksTo(64))) : DeferredHolder.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(MODID,"empty_casing"));

    public static final DeferredHolder<Item, EmptyCasingItem> RELOADING_CASING = euphy.upo.sentrymechanicalarm.SMAStartupConfig.ENABLE_CASINGS.get() ? ITEMS.register("reloading_casing",
            () -> new ReloadingCasingItem(new Item.Properties().stacksTo(1))) : DeferredHolder.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(MODID,"reloading_casing"));

    public static final DeferredHolder<Block, BlazeFireControlBlock> BLAZE_FIRE_CONTROL = BLOCKS.register("blaze_fire_control",
            id -> new BlazeFireControlBlock(BlockBehaviour.Properties.of()));

    public static final DeferredHolder<Item, BlazeFireControlBlockItem> BLAZE_FIRE_CONTROL_ITEM = ITEMS.register("blaze_fire_control",
            id -> new BlazeFireControlBlockItem(BLAZE_FIRE_CONTROL.get(), new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, BlockItem> SENTRY_ARM_ITEM = ITEMS.register("sentry_mechanical_arm",
            id -> new BlockItem(SENTRY_ARM_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, FireControlClipboardItem> FIRE_CONTROL_CLIPBOARD = ITEMS.register("fire_control_clipboard",
            id -> new FireControlClipboardItem(new Item.Properties()));

    public static final DeferredHolder<Item, SentryScopeItem> SENTRY_SCOPE = ITEMS.register("sentry_scope",
            id -> new SentryScopeItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SentryArmBlockEntity>> SENTRY_ARM_BE = BLOCK_ENTITIES.register("sentry_mechanical_arm",
            () -> BlockEntityType.Builder.of(SentryArmBlockEntity::new, SENTRY_ARM_BLOCK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlazeFireControlBlockEntity>> BLAZE_FIRE_CONTROL_BE = BLOCK_ENTITIES.register("blaze_fire_control",
            () -> BlockEntityType.Builder.of(BlazeFireControlBlockEntity::new, BLAZE_FIRE_CONTROL.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<FireControlMenu>> FIRE_CONTROL_MENU = MENUS.register("fire_control_menu",
            () -> new MenuType<>(
                    (IContainerFactory<FireControlMenu>) (id, inventory, buf) -> new FireControlMenu(id, inventory, buf),
                    FeatureFlags.DEFAULT_FLAGS
            ));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SENTRY_TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MODID))
                    .icon(() -> new net.minecraft.world.item.ItemStack(BLAZE_FIRE_CONTROL_ITEM.get(), 1))
                    .displayItems((parameters, output) -> {
                        output.accept(new net.minecraft.world.item.ItemStack(SENTRY_ARM_ITEM.get(), 1));
                        output.accept(new net.minecraft.world.item.ItemStack(BLAZE_FIRE_CONTROL_ITEM.get(), 1));
                        output.accept(new net.minecraft.world.item.ItemStack(FIRE_CONTROL_CLIPBOARD.get(), 1));
                        output.accept(new net.minecraft.world.item.ItemStack(APPLE_PIE.get(), 1));
                        output.accept(new net.minecraft.world.item.ItemStack(SENTRY_SCOPE.get(), 1));
                        output.accept(CASING_COLLECTOR_ITEM.get());
                        if(CASING_MOLD.isBound())output.accept(new net.minecraft.world.item.ItemStack(CASING_MOLD.get()));
                    })
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CASINGS_TAB =
            euphy.upo.sentrymechanicalarm.SMAStartupConfig.ENABLE_CASINGS.get()
            ? CREATIVE_TABS.register("casings", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MODID + ".casings"))
                    .icon(() -> EmptyCasingItem.create(ResourceLocation.parse("tacz:9mm"),1))
                    .withTabsAfter(ResourceLocation.parse("tacz:ammo"))
                    .withTabsBefore(ResourceLocation.parse("tacz:other"))
                    .displayItems((parameters, output) -> {
                        com.tacz.guns.api.TimelessAPI.getAllCommonAmmoIndex().stream()
                                .filter(entry -> euphy.upo.sentrymechanicalarm.util.SentryCasingProfiles.creativeEligible(entry.getKey()))
                                .sorted(java.util.Map.Entry.comparingByKey())
                                .forEach(entry -> output.accept(EmptyCasingItem.create(entry.getKey(), 1)));
                    }).build())
            : DeferredHolder.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath(MODID,"casings"));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
        BLOCK_ENTITIES.register(eventBus);
        MENUS.register(eventBus);
        CREATIVE_TABS.register(eventBus);
    }

    public static void registerAllStressValues() {
        double stressImpact = 3.0;
        BlockStressValues.IMPACTS.register(SENTRY_ARM_BLOCK.get(), () -> stressImpact);
    }

    public static void registerMovementBehaviours() {
        MovementBehaviour.REGISTRY.register(SENTRY_ARM_BLOCK.get(), new SentryMovementBehaviour());
        MovementBehaviour.REGISTRY.register(BLAZE_FIRE_CONTROL.get(), new FireControlMovementBehaviour());
    }

}
