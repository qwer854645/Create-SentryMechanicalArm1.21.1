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

/** Machines are preloaded from each storyboard schematic. Ponder's replacement
 * instructions cannot create blocks outside the loaded schematic's bounds. */
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
        var scene=start(builder,"casing_forming","使用模具制造弹壳");
        BlockPos depot=new BlockPos(4,1,4),deployer=depot.above(2);
        show(scene,util,depot);
        var blank=new ItemStack(SentryRegistry.CASING_MOLD.get());
        scene.overlay().showControls(Vec3.atCenterOf(depot.above()),Pointing.RIGHT,70).rightClick().withItem(blank);
        note(scene,depot,"右击模具，可以选择要制造的弹壳口径。");
        var mold=CasingMoldItem.create(NINE_MM);
        show(scene,util,deployer);
        scene.world().setKineticSpeed(util.select().position(deployer),64);
        hold(scene,util,deployer,mold);
        scene.overlay().showControls(Vec3.atCenterOf(deployer),Pointing.RIGHT,70).withItem(mold);
        note(scene,deployer,"将设好口径的模具交给机械手。");
        scene.world().createItemOnBelt(depot,Direction.UP,new ItemStack(Items.COPPER_INGOT));
        note(scene,depot,"机械手使用模具，将金属加工成对应的弹壳。");
        deploy(scene,deployer);
        scene.world().removeItemsFromBelt(depot);
        scene.world().createItemOnBelt(depot,Direction.UP,EmptyCasingItem.create(NINE_MM,5));
        scene.effects().indicateSuccess(depot);
        note(scene,depot,"新制弹壳和回收弹壳都可用于再装填。");
        scene.overlay().showControls(Vec3.atCenterOf(deployer),Pointing.RIGHT,70).withItem(mold);
        note(scene,deployer,"加工不会消耗模具，可以取回并重新选择口径。");
        scene.markAsFinished();
    }

    public static void reloading(SceneBuilder builder,SceneBuildingUtil util) {
        var scene=start(builder,"casing_reloading","使用弹壳再装填");
        BlockPos basin=new BlockPos(1,1,2),mixer=basin.above(2),tank=new BlockPos(2,1,5);
        BlockPos filling=new BlockPos(4,1,3),spout=filling.above(2);
        BlockPos pressing=new BlockPos(7,1,3),press=pressing.above(2);
        BlockPos materialDepot=new BlockPos(5,1,6),materialDeployer=materialDepot.above(2);
        show(scene,util,basin);show(scene,util,mixer);
        scene.overlay().showControls(Vec3.atCenterOf(basin.above()),Pointing.RIGHT,70).withItem(new ItemStack(Items.GUNPOWDER));
        note(scene,basin,"使用动力搅拌器，将火药加工成流态火药。");
        show(scene,util,tank);
        scene.world().modifyBlockEntity(tank,FluidTankBlockEntity.class,
            be->be.getTankInventory().setFluid(new FluidStack(SentryFluids.PROPELLANT.get(),1000)));
        note(scene,tank,"使用管道，将流态火药送入注液器。");
        show(scene,util,filling);show(scene,util,spout);
        var casing=EmptyCasingItem.create(NINE_MM,1);
        scene.world().createItemOnBelt(filling,Direction.UP,casing);
        scene.world().modifyBlockEntity(spout,SpoutBlockEntity.class,be->{
            var fluid=be.getBehaviour(SmartFluidTankBehaviour.TYPE);
            if(fluid!=null)fluid.getPrimaryHandler().setFluid(new FluidStack(SentryFluids.PROPELLANT.get(),1000));
        });
        note(scene,spout,"注液器会为下方的弹壳装入流态火药。");
        scene.world().modifyBlockEntity(spout,SpoutBlockEntity.class,be->be.processingTicks=20);
        scene.idle(25);
        scene.world().removeItemsFromBelt(filling);
        var partial=new ItemStack(SentryRegistry.RELOADING_CASING.get());
        partial.set(ModDataComponents.CASING_AMMO.get(),NINE_MM);
        scene.overlay().showControls(Vec3.atCenterOf(filling.above()),Pointing.RIGHT,70).withItem(partial);
        show(scene,util,materialDepot);show(scene,util,materialDeployer);
        hold(scene,util,materialDeployer,new ItemStack(AllItems.COPPER_NUGGET.get()));
        note(scene,materialDeployer,"部分弹药还需要机械手加入金属或其他材料。");
        show(scene,util,pressing);show(scene,util,press);
        scene.world().setKineticSpeed(util.select().position(press),64);
        scene.world().createItemOnBelt(pressing,Direction.UP,partial);
        scene.world().modifyBlockEntity(press,MechanicalPressBlockEntity.class,
            be->be.getPressingBehaviour().start(PressingBehaviour.Mode.BELT));
        scene.idle(40);
        scene.world().removeItemsFromBelt(pressing);
        scene.world().createItemOnBelt(pressing,Direction.UP,ammo());
        scene.effects().indicateSuccess(pressing);
        note(scene,pressing,"最后经过冲压，弹壳便被装成一发子弹。");
        note(scene,spout,"装药量较大时，需要经过多次注液。");
        scene.overlay().showControls(Vec3.atCenterOf(pressing.above()),Pointing.RIGHT,70).withItem(partial);
        note(scene,pressing,"将未完成的弹壳单独放入合成栏，可以取回空壳。");
        scene.markAsFinished();
    }

    public static void collecting(SceneBuilder builder,SceneBuildingUtil util) {
        var scene=start(builder,"casing_collection","收集弹壳");
        BlockPos arm=new BlockPos(3,1,3),collector=arm.east(),hopper=collector.south(),chest=hopper.south();
        show(scene,util,arm);
        SentryMechanicalArmScene.setGun(scene,arm,"tacz:glock_17");
        show(scene,util,collector);
        scene.overlay().showOutline(PonderPalette.GREEN,"collector",util.select().position(collector),90);
        note(scene,collector,"将弹壳收集器放在哨戒动力臂旁。");
        var casing=EmptyCasingItem.create(NINE_MM,1);
        for(int i=1;i<=3;i++) {
            int count=i;
            scene.world().modifyBlockEntity(arm,SentryArmBlockEntity.class,be->{be.setLastShootTime(System.currentTimeMillis());be.setShellEjected();});
            scene.world().modifyBlockEntity(collector,CasingCollectorBlockEntity.class,be->be.setItem(0,EmptyCasingItem.create(NINE_MM,count)));
            scene.idle(12);
        }
        scene.overlay().showControls(Vec3.atCenterOf(collector.above()),Pointing.RIGHT,70).withItem(casing);
        note(scene,collector,"射击产生的弹壳会直接存入收集器。");
        show(scene,util,hopper);show(scene,util,chest);
        note(scene,hopper,"使用漏斗等物流组件，可以取出收集器内的物品。");
        note(scene,collector,"收集器装满后，无法存入的部分会落在地上。");
        // Replace the visible static section before animating the same blocks as one group.
        scene.world().hideSection(util.select().fromTo(3,1,3,4,1,3),Direction.DOWN);
        scene.idle(15);
        var group=scene.world().showIndependentSection(util.select().fromTo(3,1,3,4,1,3),Direction.DOWN);
        scene.world().moveSection(group,new Vec3(0,1,0),25);
        scene.idle(30);
        note(scene,collector.above(),"在动态结构上，动力臂会使用同一结构内的收集器。");
        scene.overlay().showControls(new Vec3(2,2,5),Pointing.RIGHT,70).withItem(casing);
        note(scene,chest,"玩家射击产生的弹壳默认存入背包。");
        note(scene,arm,"背包放不下的弹壳会在玩家身旁掉落。");
        scene.markAsFinished();
    }

    public static void depotSupply(SceneBuilder builder,SceneBuildingUtil util) {
        var scene=start(builder,"sentry_depot_supply","使用弹药库补给");
        BlockPos arm=new BlockPos(3,1,3),depot=arm.east();
        show(scene,util,arm);show(scene,util,depot);
        SentryMechanicalArmScene.setGun(scene,arm,"tacz:glock_17");
        var box=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("tacz:ammo_box")));
        if(box.getItem() instanceof com.tacz.guns.api.item.IAmmoBox ammoBox) {
            ammoBox.setAmmoId(box,NINE_MM);
            ammoBox.setAmmoCount(box,0);
        }
        scene.world().modifyBlockEntity(arm,SentryArmBlockEntity.class,be->be.attachedAmmoBoxes.set(0,box.copy()));
        scene.overlay().showControls(Vec3.atCenterOf(arm.above()),Pointing.RIGHT,70).withItem(box);
        note(scene,arm,"为哨戒动力臂装上匹配的弹药盒。");
        scene.overlay().showControls(Vec3.atCenterOf(depot.above()),Pointing.RIGHT,70).withItem(ammo());
        note(scene,depot,"相邻弹药库中的弹药会补充到弹药盒内。");
        scene.effects().indicateSuccess(arm);
        note(scene,depot,"在动态结构上，动力臂也能使用结构内的弹药库。");
        note(scene,arm,"弹药不足时，可查看动力臂的补给提示。");
        note(scene,depot,"通过物流持续补充弹药库，就能维持供弹。");
        scene.markAsFinished();
    }

    private static ItemStack ammo() {
        var stack=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("tacz:ammo")));
        if(stack.getItem() instanceof IAmmo item)item.setAmmoId(stack,NINE_MM);
        return stack;
    }
}
