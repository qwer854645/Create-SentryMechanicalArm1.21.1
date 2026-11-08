package euphy.upo.sentrymechanicalarm.ponder.scene;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.simibubi.create.foundation.ponder.element.BeltItemElement;
import com.tacz.guns.api.item.IGun;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import net.minecraft.resources.ResourceLocation;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class SentryMechanicalArmScene {

    public static void introducing(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("sentry_arm_intro", "哨戒动力臂简介");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        BlockPos sentryPos = util.grid().at(2, 1, 2);
        BlockPos cog_1 = util.grid().at(1, 1, 2);
        BlockPos cog_2 = util.grid().at(0, 1, 3);
        Selection sentrySelect = util.select().position(sentryPos);
        Selection cogS1 = util.select().position(cog_1);
        Selection cogS2 = util.select().position(cog_2);

        BlockPos targetPos = util.grid().at(2, 2, 2);

        scene.idle(20);

        ElementLink<WorldSectionElement> sentryLink = scene.world().showIndependentSection(sentrySelect, Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(50)
                .text("哨戒动力臂是一种强大的火力平台")
                .pointAt(util.vector().topOf(sentryPos))
                .placeNearTarget();
        scene.idle(60);

        scene.overlay().showText(60)
                .text("手持 TaCZ 枪械，按交互键（默认 O）装入动力臂。")
                .pointAt(util.vector().topOf(sentryPos))
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(50);

        setGun(scene, sentryPos, "tacz:minigun");
        scene.effects().indicateSuccess(sentryPos.above());
        scene.idle(30);

        scene.overlay().showText(60)
                .text("右击装入弹药盒，潜行右击取下。")
                .pointAt(util.vector().topOf(sentryPos))
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(40);

        ElementLink<WorldSectionElement> cog1Link = scene.world().showIndependentSection(cogS1, Direction.DOWN);
        ElementLink<WorldSectionElement> cog2Link = scene.world().showIndependentSection(cogS2, Direction.DOWN);
        scene.idle(20);

        scene.world().setKineticSpeed(util.select().everywhere(), -64);
        scene.world().setKineticSpeed(cogS1, 64);
        scene.idle(12);

        scene.overlay().showText(60)
                .text("接入应力，它就会开始工作，自动索敌所有的敌对生物")
                .pointAt(util.vector().topOf(sentryPos))
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(70);

        for (int i = 0; i < 10; i++) {
            fire(scene, sentryPos, targetPos, util);
            scene.idle(3);
        }
        scene.idle(25);

        scene.world().moveSection(sentryLink, util.vector().of(0, 1, 0), 20);
        scene.world().moveSection(cog1Link, util.vector().of(0, 1, 0), 20);
        scene.world().moveSection(cog2Link, util.vector().of(0, 1, 0), 20);
        scene.idle(25);

        BlockPos tempBarrelSpawnPos = util.grid().at(4, 1, 2);
        scene.world().setBlock(tempBarrelSpawnPos, Blocks.BARREL.defaultBlockState(), false);

        ElementLink<WorldSectionElement> barrelLink = scene.world().showIndependentSection(util.select().position(tempBarrelSpawnPos), Direction.NORTH);

        scene.world().moveSection(barrelLink, util.vector().of(-2, 0, 0), 0);
        scene.idle(15);

        scene.overlay().showText(120)
                .text("静态动力臂可将击杀战利品和转化的经验存入下方容器。")
                .pointAt(util.vector().blockSurface(sentryPos, Direction.WEST))
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(40);

        Vec3 barrelTop = util.vector().topOf(sentryPos);

        scene.overlay().showControls(barrelTop.add(0, -1.5, 0), Pointing.UP, 40)
                .withItem(new ItemStack(Items.ROTTEN_FLESH));
        scene.idle(15);

        scene.overlay().showControls(barrelTop.add(0.5, -1, 0), Pointing.RIGHT, 40)
                .withItem(new ItemStack(Items.BONE));
        scene.idle(40);

        scene.markAsFinished();
    }

    public static void supplying(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("sentry_arm_supply", "后勤供应");
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.scaleSceneView(0.8f);

        BlockPos sentryPos = util.grid().at(1, 1, 2);
        BlockPos supply_1 = util.grid().at(4, 1, 3);
        BlockPos supply_2 = util.grid().at(2, 1, 5);
        BlockPos cog_1 = util.grid().at(2, 1, 2);
        BlockPos cog_2 = util.grid().at(2, 1, 3);
        BlockPos cog_3 = util.grid().at(3, 1, 4);
        BlockPos depotPos = util.grid().at(1, 1, 4);
        BlockPos beltStart = util.grid().at(4, 1, 7);
        BlockPos beltEnd = util.grid().at(3, 1, 2);
        BlockPos deployerPos = util.grid().at(7, 3, 4);
        BlockPos deployerPos_2 = util.grid().at(7, 3, 5);
        BlockPos beltPos = util.grid().at(7, 1, 4);
        Selection sentrySelect = util.select().position(sentryPos);
        Selection supplySelect_1 = util.select().position(supply_1);
        Selection supplySelect_2 = util.select().position(supply_2);
        Selection cogS1 = util.select().position(cog_1);
        Selection cogS2 = util.select().position(cog_2);
        Selection cogS3 = util.select().position(cog_3);
        Selection supplyLine_1 = util.select().fromTo(2, 1, 2, 8, 3, 8);
        Selection supplyLine_2 = util.select().fromTo(1, 1, 4, 1, 1, 7);
        Selection S1Selection = util.select().fromTo(4, 1, 3, 4, 2, 3);
        Selection S2Selection = util.select().fromTo(2, 1, 5, 2, 2, 5);
        Selection deployerSelection = util.select().position(deployerPos);
        Selection deployerSelection_2 = util.select().position(deployerPos_2);

        ItemStack ammoStack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("tacz:ammo_box")));
        if(ammoStack.getItem() instanceof com.tacz.guns.api.item.IAmmoBox box) {
            box.setAmmoId(ammoStack, ResourceLocation.parse("tacz:9mm"));
            box.setAmmoCount(ammoStack,30);
        }
        ItemStack ammo_Stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("tacz:ammo")), 40);
        if(ammo_Stack.getItem() instanceof com.tacz.guns.api.item.IAmmo ammo)
            ammo.setAmmoId(ammo_Stack,ResourceLocation.parse("tacz:9mm"));
        ItemStack emptyBox=ammoStack.copy();
        if(emptyBox.getItem() instanceof com.tacz.guns.api.item.IAmmoBox box)box.setAmmoCount(emptyBox,0);

        scene.idle(20);

        scene.world().showSection(sentrySelect, Direction.DOWN);
        scene.idle(15);
        setGun(scene, sentryPos, "tacz:glock_17");
        scene.idle(10);

        scene.overlay().showText(50)
                .text("如果想要哨戒动力臂拥有持久的火力……")
                .pointAt(util.vector().topOf(sentryPos))
                .placeNearTarget();
        scene.idle(60);

        scene.idle(15);

        scene.world().showSection(supplyLine_1, Direction.DOWN);
        scene.world().showSection(supplyLine_2, Direction.DOWN);
        scene.overlay().showText(60)
                .text("你需要为它打造一套后勤供应")
                .pointAt(util.vector().topOf(sentryPos))
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(80);

        scene.world().setKineticSpeed(util.select().everywhere(), -64);
        scene.world().setKineticSpeed(cogS1, 64);
        scene.world().setKineticSpeed(cogS3, 64);
        scene.world().setKineticSpeed(util.select().fromTo(7, 1, 2, 7, 1, 6), 64);
        scene.world().setKineticSpeed(util.select().fromTo(7, 1, 7, 2, 1, 7), 64);
        scene.idle(10);

        scene.world().createItemOnBelt(beltStart, Direction.UP, ammoStack);

        scene.idle(40);

        scene.overlay().showOutline(PonderPalette.GREEN, "frame_1", S2Selection, 80);
        scene.idle(20);

        scene.overlay().showText(80)
                .text("动力臂和漏斗可以给哨戒动力臂供弹")
                .pointAt(util.vector().blockSurface(supply_2, Direction.WEST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(20);

        scene.world().setKineticSpeed(supplySelect_2, -64);
        scene.idle(5);
        scene.world().instructArm(supply_2, ArmBlockEntity.Phase.MOVE_TO_INPUT, ItemStack.EMPTY, 0);
        scene.idle(24);
        scene.world().removeItemsFromBelt(depotPos);
        scene.world().instructArm(supply_2, ArmBlockEntity.Phase.SEARCH_OUTPUTS, ammoStack, 0);
        scene.idle(24);
        scene.world().instructArm(supply_2, ArmBlockEntity.Phase.MOVE_TO_OUTPUT, ammoStack, 0);
        scene.idle(24);
        scene.world().modifyBlockEntity(sentryPos, SentryArmBlockEntity.class, be -> {
            be.attachedAmmoBoxes.set(1, ammoStack);
        });
        scene.world().instructArm(supply_2, ArmBlockEntity.Phase.SEARCH_INPUTS, ItemStack.EMPTY, 0);
        scene.idle(25);

        for (int i = 0; i < 16; i++) {
            fire(scene, sentryPos, util.grid().at(4, 2, 1), util);
            scene.idle(2);
        }
        scene.idle(20);

        scene.overlay().showOutline(PonderPalette.GREEN, "frame_1", S1Selection, 80);
        scene.overlay().showText(45)
                .text("动力臂会自动取出打空的弹药盒")
                .pointAt(util.vector().blockSurface(supply_1, Direction.EAST))
                .placeNearTarget();
        scene.idle(55);

        scene.world().setKineticSpeed(supplySelect_1, -64);
        scene.idle(5);
        scene.world().instructArm(supply_1, ArmBlockEntity.Phase.MOVE_TO_INPUT, ItemStack.EMPTY, 0);
        scene.idle(24);
        scene.world().modifyBlockEntity(sentryPos, SentryArmBlockEntity.class, be -> {
            be.attachedAmmoBoxes.set(1, ItemStack.EMPTY);
        });
        scene.world().instructArm(supply_1, ArmBlockEntity.Phase.SEARCH_OUTPUTS, emptyBox, 0);
        scene.idle(24);
        scene.world().instructArm(supply_1, ArmBlockEntity.Phase.MOVE_TO_OUTPUT, emptyBox, 0);
        scene.idle(24);
        scene.world().createItemOnBelt(beltEnd, Direction.UP, emptyBox);
        scene.world().instructArm(supply_1, ArmBlockEntity.Phase.SEARCH_INPUTS, ItemStack.EMPTY, 0);
        scene.idle(15);
        scene.overlay().showOutline(PonderPalette.GREEN, "frame_1", deployerSelection, 60);
        scene.overlay().showText(80)
                .text("持有弹药的机械手会为下方的弹药盒装弹")
                .pointAt(util.vector().blockSurface(deployerPos, Direction.EAST))
                .placeNearTarget().attachKeyFrame();
        scene.idle(20);

        scene.world().modifyBlockEntityNBT(deployerSelection, DeployerBlockEntity.class, nbt -> {
            nbt.put("HeldItem", ammo_Stack.saveOptional(scene.world().getHolderLookupProvider()));
        });
        scene.world().modifyBlockEntityNBT(deployerSelection_2, DeployerBlockEntity.class, nbt -> {
            nbt.put("HeldItem", ammo_Stack.saveOptional(scene.world().getHolderLookupProvider()));
        });

        scene.world().setKineticSpeed(deployerSelection, -64);

        scene.world().moveDeployer(deployerPos, 1.0f, 25);
        scene.idle(26);
        scene.world().moveDeployer(deployerPos, -1.0f, 25);
        scene.effects().indicateSuccess(beltPos);
        scene.world().removeItemsFromBelt(deployerPos.below(2));
        ElementLink<BeltItemElement> itemLink =scene.world().createItemOnBelt(deployerPos.below(2), Direction.UP, ammoStack);
        scene.world().stallBeltItem(itemLink, true);
        scene.idle(15);
        scene.world().stallBeltItem(itemLink, false);
        scene.idle(10);

        scene.world().moveDeployer(deployerPos_2, 1.0f, 25);
        scene.idle(26);
        scene.world().moveDeployer(deployerPos_2, -1.0f, 25);

        scene.world().removeItemsFromBelt(deployerPos_2.below(2));
        ElementLink<BeltItemElement> itemLink_2 =scene.world().createItemOnBelt(deployerPos_2.below(2), Direction.UP, ammoStack);
        scene.world().stallBeltItem(itemLink_2, true);
        scene.idle(15);
        scene.world().stallBeltItem(itemLink_2, false);
        scene.idle(55);

        scene.overlay().showText(80)
                .text("如此，哨戒动力臂便有了源源不断的火力")
                .pointAt(util.vector().topOf(sentryPos))
                .placeNearTarget();
        scene.idle(80);

        scene.markAsFinished();
    }



    public static void movement(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("sentry_arm_move", "动态结构");
        scene.configureBasePlate(0, 0, 10);
        scene.scaleSceneView(0.7f);
        scene.showBasePlate();
        scene.idle(30);

        Selection train = util.select().fromTo(0, 1, 4, 9, 4, 6);
        ElementLink<WorldSectionElement> movement = scene.world().showIndependentSection(util.select().fromTo(6, 1, 2, 2, 2, 2), Direction.DOWN);
        ElementLink<WorldSectionElement> piston_pos = scene.world().showIndependentSection(util.select().fromTo(4, 1, 3, 4, 1, 3), Direction.DOWN);
        ItemStack ammoStack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("tacz:ammo_box")));

        setGun(scene, util.grid().at(2, 1, 2), "tacz:minigun");
        setGun(scene, util.grid().at(2, 2, 2), "tacz:minigun");
        scene.world().moveSection(piston_pos, util.vector().of(0, 0, -1), 0);

        scene.idle(20);

        scene.world().moveSection(movement, util.vector().of(-2, 0, 0), 20);
        scene.world().setKineticSpeed(util.select().everywhere(), 32f);
        scene.idle(25);
        scene.world().setKineticSpeed(util.select().everywhere(), 0);

        scene.idle(10);

        scene.overlay().showText(60)
                .text("哨戒动力臂可以在动态结构上使用")
                .pointAt(util.vector().topOf(util.grid().at(0, 2, 2)))
                .placeNearTarget();
        scene.idle(70);

        scene.world().moveSection(movement, util.vector().of(2, 0, 0), 40);
        scene.world().setKineticSpeed(util.select().everywhere(), -16f);
        scene.idle(25);
        scene.world().setKineticSpeed(util.select().everywhere(), 0);

        scene.idle(10);

        scene.overlay().showText(60)
                .text("移动结构上的动力臂会保留装配时的转速。")
                .pointAt(util.vector().topOf(util.grid().at(2, 2, 2)))
                .placeNearTarget();
        scene.idle(70);

        scene.world().hideIndependentSection(movement, Direction.UP);
        scene.world().hideIndependentSection(piston_pos, Direction.UP);
        scene.idle(20);

        scene.world().showIndependentSection(train, Direction.DOWN);
        scene.idle(20);
        scene.world().setKineticSpeed(util.select().everywhere(), -64f);
        scene.idle(20);

        scene.overlay().showText(60)
                .text("哨戒动力臂会使用结构中容器内的弹药")
                .pointAt(util.vector().topOf(util.grid().at(2, 2, 2)))
                .placeNearTarget().attachKeyFrame();
        scene.overlay().showControls(util.grid().at(3, 4, 5).getCenter(), Pointing.UP, 60).withItem(ammoStack);
        scene.idle(70);

        scene.overlay().showText(60)
                .text("移动结构上的火控台依然有效")
                .pointAt(util.vector().topOf(util.grid().at(1, 4, 5)))
                .placeNearTarget();
        scene.idle(80);

        scene.markAsFinished();
    }

    static void setGun(CreateSceneBuilder scene, BlockPos sentryPos, String gunId) {
        ItemStack gun = createGun(gunId);
        if (!gun.isEmpty()) {
            scene.world().modifyBlockEntity(sentryPos, SentryArmBlockEntity.class, be -> be.setHeldItem(gun));
        }
    }

    private static ItemStack createGun(String gunId) {
        Item gunItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("tacz:modern_kinetic_gun"));
        if (gunItem == net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(gunItem);
        IGun iGun = IGun.getIGunOrNull(stack);
        if (iGun != null) {
            iGun.setGunId(stack, ResourceLocation.parse(gunId));
        }
        return stack;
    }

    private static void fire(CreateSceneBuilder scene, BlockPos sentryPos, BlockPos targetPos, SceneBuildingUtil util) {
        scene.world().modifyBlockEntity(sentryPos, SentryArmBlockEntity.class, be -> {
            be.setLastShootTime(System.currentTimeMillis());
            be.setShellEjected();
        });
    }
}
