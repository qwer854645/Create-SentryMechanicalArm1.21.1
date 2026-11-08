package euphy.upo.sentrymechanicalarm.ponder.scene;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.spout.SpoutBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.tacz.guns.api.item.IAmmo;
import euphy.upo.sentrymechanicalarm.content.*;
import euphy.upo.sentrymechanicalarm.registry.*;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

/** Separate additions: the four original storyboards and their schematics stay intact. */
public final class CasingScenes {
    private static final ResourceLocation NINE_MM=ResourceLocation.parse("tacz:9mm");

    private static CreateSceneBuilder start(SceneBuilder builder,String id,String title) {
        var scene=new CreateSceneBuilder(builder);
        scene.title(id,title);
        scene.configureBasePlate(0,0,9);
        scene.scaleSceneView(.75f);
        scene.showBasePlate();
        scene.idle(15);
        return scene;
    }

    private static void note(CreateSceneBuilder scene,BlockPos pos,String text) {
        scene.overlay().showText(90).text(text).pointAt(Vec3.atCenterOf(pos)).placeNearTarget().attachKeyFrame();
        scene.idle(100);
    }

    private static void show(CreateSceneBuilder scene,SceneBuildingUtil util,BlockPos pos) {
        scene.world().showSection(util.select().position(pos),Direction.DOWN);
        scene.idle(10);
    }

    private static void hold(CreateSceneBuilder scene,SceneBuildingUtil util,BlockPos deployer,ItemStack stack) {
        scene.world().modifyBlockEntityNBT(util.select().position(deployer),DeployerBlockEntity.class,
            tag->tag.put("HeldItem",stack.saveOptional(scene.world().getHolderLookupProvider())));
    }

    private static void deploy(CreateSceneBuilder scene,BlockPos pos) {
        scene.world().moveDeployer(pos,1,20);
        scene.idle(21);
        scene.world().moveDeployer(pos,-1,20);
        scene.idle(21);
    }

    public static void forming(SceneBuilder builder,SceneBuildingUtil util) {
        var scene=start(builder,"casing_forming","用模具制造弹壳");
        BlockPos depot=new BlockPos(4,1,4),deployer=depot.above(2);
        scene.world().setBlock(depot,AllBlocks.DEPOT.getDefaultState(),false);
        scene.world().setBlock(deployer,AllBlocks.DEPLOYER.getDefaultState(),false);
        show(scene,util,depot);
        var blank=new ItemStack(SentryRegistry.CASING_MOLD.get());
        scene.overlay().showControls(Vec3.atCenterOf(depot.above()),Pointing.RIGHT,70).rightClick().withItem(blank);
        note(scene,depot,"手持空白模具右键，搜索并选择可制造的口径；不需要先获得对应子弹。");
        var mold=CasingMoldItem.create(NINE_MM);
        show(scene,util,deployer);
        hold(scene,util,deployer,mold);
        scene.overlay().showControls(Vec3.atCenterOf(deployer),Pointing.RIGHT,70).withItem(mold);
        note(scene,deployer,"把已选口径的模具交给机械手。模具可重复使用，不消耗，也不磨损。");
        scene.world().createItemOnBelt(depot,Direction.UP,new ItemStack(Items.COPPER_INGOT));
        note(scene,depot,"默认 9mm 示例：一锭铜制成五枚弹壳。不同口径的金属规格和数量以 JEI 为准。");
        deploy(scene,deployer);
        scene.world().removeItemsFromBelt(depot);
        scene.world().createItemOnBelt(depot,Direction.UP,EmptyCasingItem.create(NINE_MM,5));
        scene.effects().indicateSuccess(depot);
        note(scene,depot,"新制弹壳与同口径回收弹壳相同，可堆叠并进入同一条再装填生产线。");
        scene.overlay().showControls(Vec3.atCenterOf(deployer),Pointing.RIGHT,70).withItem(mold);
        note(scene,deployer,"空白模具或错误口径不会加工。想换一种弹壳，先取回模具重新选择口径。");
        scene.markAsFinished();
    }

    public static void reloading(SceneBuilder builder,SceneBuildingUtil util) {
        var scene=start(builder,"casing_reloading","流态装药与单发再装填");
        BlockPos basin=new BlockPos(1,1,2),mixer=basin.above(2),tank=new BlockPos(2,1,5);
        BlockPos filling=new BlockPos(4,1,3),spout=filling.above(2);
        BlockPos pressing=new BlockPos(7,1,3),press=pressing.above(2);
        scene.world().setBlock(basin,AllBlocks.BASIN.getDefaultState(),false);
        scene.world().setBlock(mixer,AllBlocks.MECHANICAL_MIXER.getDefaultState(),false);
        scene.world().setBlock(tank,AllBlocks.FLUID_TANK.getDefaultState(),false);
        scene.world().setBlock(filling,AllBlocks.DEPOT.getDefaultState(),false);
        scene.world().setBlock(spout,AllBlocks.SPOUT.getDefaultState(),false);
        scene.world().setBlock(pressing,AllBlocks.DEPOT.getDefaultState(),false);
        scene.world().setBlock(press,AllBlocks.MECHANICAL_PRESS.getDefaultState(),false);
        show(scene,util,basin);show(scene,util,mixer);
        scene.overlay().showControls(Vec3.atCenterOf(basin.above()),Pointing.RIGHT,70).withItem(new ItemStack(Items.GUNPOWDER));
        note(scene,basin,"一份火药搅拌得到 1000 mB 流态火药，不加水，也不需要加热。");
        show(scene,util,tank);
        scene.world().modifyBlockEntity(tank,FluidTankBlockEntity.class,
            be->be.getTankInventory().setFluid(new FluidStack(SentryFluids.PROPELLANT.get(),1000)));
        note(scene,tank,"使用储液罐和管道运输流态火药，送往注液器；这里分开展示各加工工位。");
        show(scene,util,filling);show(scene,util,spout);
        var casing=EmptyCasingItem.create(NINE_MM,1);
        scene.world().createItemOnBelt(filling,Direction.UP,casing);
        scene.world().modifyBlockEntity(spout,SpoutBlockEntity.class,be->{
            var fluid=be.getBehaviour(SmartFluidTankBehaviour.TYPE);
            if(fluid!=null)fluid.getPrimaryHandler().setFluid(new FluidStack(SentryFluids.PROPELLANT.get(),1000));
        });
        note(scene,spout,"一枚弹壳装配一发子弹。默认 9mm 每发注入 40 mB，其他口径按原配方比例取整。");
        scene.world().modifyBlockEntity(spout,SpoutBlockEntity.class,be->be.processingTicks=20);
        scene.idle(25);
        scene.world().removeItemsFromBelt(filling);
        var partial=new ItemStack(SentryRegistry.RELOADING_CASING.get());
        partial.set(ModDataComponents.CASING_AMMO.get(),NINE_MM);
        scene.overlay().showControls(Vec3.atCenterOf(filling.above()),Pointing.RIGHT,70).withItem(partial);
        note(scene,filling,"需要金属或特殊配料的口径，再用机械手投入 JEI 所示材料；默认 9mm 可直接进入冲压。");
        show(scene,util,pressing);show(scene,util,press);
        scene.world().createItemOnBelt(pressing,Direction.UP,partial);
        scene.world().modifyBlockEntity(press,MechanicalPressBlockEntity.class,
            be->be.getPressingBehaviour().start(PressingBehaviour.Mode.BELT));
        scene.idle(40);
        scene.world().removeItemsFromBelt(pressing);
        scene.world().createItemOnBelt(pressing,Direction.UP,ammo());
        scene.effects().indicateSuccess(pressing);
        note(scene,pressing,"最后冲压，得到原枪包的同口径子弹。流程不需要底火、小火药或弹药模板。");
        note(scene,spout,"每次注液最多 1000 mB，较大装药会拆成多步；材料数量与完整顺序以当前 JEI 配方为准。");
        scene.overlay().showControls(Vec3.atCenterOf(pressing.above()),Pointing.RIGHT,70).withItem(partial);
        note(scene,pressing,"旧在制品无法继续时，可单独放入合成栏取回弹壳；已经投入的材料不会退回。");
        scene.markAsFinished();
    }

    public static void collecting(SceneBuilder builder,SceneBuildingUtil util) {
        var scene=start(builder,"casing_collection","收集射击后的弹壳");
        BlockPos arm=new BlockPos(3,1,3),collector=arm.east(),hopper=collector.south(),chest=hopper.south();
        scene.world().setBlock(arm,SentryRegistry.SENTRY_ARM_BLOCK.get().defaultBlockState(),false);
        scene.world().setBlock(collector,SentryRegistry.CASING_COLLECTOR_BLOCK.get().defaultBlockState(),false);
        scene.world().setBlock(hopper,Blocks.HOPPER.defaultBlockState().setValue(net.minecraft.world.level.block.HopperBlock.FACING,Direction.SOUTH),false);
        scene.world().setBlock(chest,Blocks.CHEST.defaultBlockState(),false);
        show(scene,util,arm);
        SentryMechanicalArmScene.setGun(scene,arm,"tacz:glock_17");
        show(scene,util,collector);
        scene.overlay().showOutline(PonderPalette.GREEN,"collector",util.select().position(collector),90);
        note(scene,collector,"把弹壳收集器放在静态哨戒动力臂六个面相邻的位置，不需要额外切换收集模式。");
        var casing=EmptyCasingItem.create(NINE_MM,1);
        for(int i=1;i<=3;i++) {
            int count=i;
            scene.world().modifyBlockEntity(arm,SentryArmBlockEntity.class,be->{be.setLastShootTime(System.currentTimeMillis());be.setShellEjected();});
            scene.world().modifyBlockEntity(collector,CasingCollectorBlockEntity.class,be->be.setItem(0,EmptyCasingItem.create(NINE_MM,count)));
            scene.idle(12);
        }
        scene.overlay().showControls(Vec3.atCenterOf(collector.above()),Pointing.RIGHT,70).withItem(casing);
        note(scene,collector,"射后产物直接进入 27 格库存，不先掉到地上；也不会吸取附近已有的掉落物。");
        show(scene,util,hopper);show(scene,util,chest);
        note(scene,hopper,"漏斗或 Create 物流可以取出库存。自定义的其他射后物品也可用同样方式收集。");
        note(scene,collector,"没有收集器，或多个收集器都已装满时，只把放不下的部分留作地面掉落。");
        // Replace the visible static section before animating the same blocks as one group.
        scene.world().hideSection(util.select().fromTo(3,1,3,4,1,3),Direction.DOWN);
        scene.idle(15);
        var group=scene.world().showIndependentSection(util.select().fromTo(3,1,3,4,1,3),Direction.DOWN);
        scene.world().moveSection(group,new Vec3(0,1,0),25);
        scene.idle(30);
        note(scene,collector.above(),"移动结构查找同一结构内的收集器，优先使用较近的；Sable 船体之间不会跨结构收集。");
        scene.overlay().showControls(new Vec3(2,2,5),Pointing.RIGHT,70).withItem(casing);
        note(scene,chest,"玩家射击默认把回收物放入背包；背包满时，剩余部分会在玩家位置掉出。");
        note(scene,arm,"只有实际消耗有限弹药的射击才产生回收物；创造模式和无限弹药不会免费产壳。");
        scene.markAsFinished();
    }

    public static void depotSupply(SceneBuilder builder,SceneBuildingUtil util) {
        var scene=start(builder,"sentry_depot_supply","弹药库自动补给");
        BlockPos arm=new BlockPos(3,1,3),depot=arm.east();
        scene.world().setBlock(arm,SentryRegistry.SENTRY_ARM_BLOCK.get().defaultBlockState(),false);
        scene.world().setBlock(depot,com.tacz.guns.init.ModBlocks.AMMO_STORAGE.get().defaultBlockState(),false);
        show(scene,util,arm);show(scene,util,depot);
        SentryMechanicalArmScene.setGun(scene,arm,"tacz:glock_17");
        var box=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("tacz:ammo_box")));
        if(box.getItem() instanceof com.tacz.guns.api.item.IAmmoBox ammoBox) {
            ammoBox.setAmmoId(box,NINE_MM);
            ammoBox.setAmmoCount(box,0);
        }
        scene.world().modifyBlockEntity(arm,SentryArmBlockEntity.class,be->be.attachedAmmoBoxes.set(0,box.copy()));
        scene.overlay().showControls(Vec3.atCenterOf(arm.above()),Pointing.RIGHT,70).withItem(box);
        note(scene,arm,"先为哨戒动力臂安装兼容的弹药盒，最多两个；弹药库用于补充盒内弹药。");
        scene.overlay().showControls(Vec3.atCenterOf(depot.above()),Pointing.RIGHT,70).withItem(ammo());
        note(scene,depot,"静态炮台从相邻弹药库获取匹配弹药。确保库内有库存，并让炮台保持有效动力。");
        scene.effects().indicateSuccess(arm);
        note(scene,depot,"Create 移动结构可使用同结构的挂载库存和弹药库；移动时通过结构存储保留弹药数量。");
        note(scene,arm,"缺少弹药盒、口径不匹配或库存不足时无法补给，可通过动力臂的状态提示定位原因。");
        note(scene,depot,"弹药库存放未发射弹药；射后的弹壳交给弹壳收集器，两种容器用途不同。");
        scene.markAsFinished();
    }

    private static ItemStack ammo() {
        var stack=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("tacz:ammo")));
        if(stack.getItem() instanceof IAmmo item)item.setAmmoId(stack,NINE_MM);
        return stack;
    }
}
