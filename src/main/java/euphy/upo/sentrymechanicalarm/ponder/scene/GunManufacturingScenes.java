package euphy.upo.sentrymechanicalarm.ponder.scene;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import euphy.upo.sentrymechanicalarm.registry.*;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import java.util.List;
import static euphy.upo.sentrymechanicalarm.ponder.scene.CasingScenes.*;

/** Illustrations of the manufacturing workflow; material costs remain recipe-driven. */
public final class GunManufacturingScenes {
    private static ItemStack part(String name){return new ItemStack(GunParts.item(name));}
    private static ItemStack workpiece(String path){
        var stack=new ItemStack(GunParts.WORKPIECE.get());
        stack.set(ModDataComponents.GUN_WORK.get(),ResourceLocation.parse("sentrymechanicalarm:gun_manufacturing/"+path));
        return stack;
    }
    private static ItemStack selectedReceiver(){
        var stack=workpiece("guns/tacz/glock_17");
        stack.set(ModDataComponents.GUN_RECEIVER.get(),ItemContainerContents.fromItems(List.of(part("receiver_component"))));
        return stack;
    }
    private static void replace(CreateSceneBuilder scene,BlockPos depot,ItemStack stack){
        scene.world().removeItemsFromBelt(depot);
        scene.world().createItemOnBelt(depot,Direction.UP,stack.copy());
    }
    private static void press(CreateSceneBuilder scene,BlockPos pos){
        scene.world().modifyBlockEntity(pos,MechanicalPressBlockEntity.class,
                be->be.getPressingBehaviour().start(PressingBehaviour.Mode.BELT));
        scene.idle(40);
    }

    public static void components(SceneBuilder builder,SceneBuildingUtil util){
        var scene=start(builder,"gun_components","加工不同等级的零件");
        BlockPos basic=new BlockPos(2,1,3),precision=new BlockPos(4,1,3),reinforced=new BlockPos(6,1,3);
        for(var depot:List.of(basic,precision,reinforced)){
            show(scene,util,depot);show(scene,util,depot.above(2));
            replace(scene,depot,part("barrel_blank"));
        }
        scene.world().setKineticSpeed(util.select().everywhere(),64);
        note(scene,precision,"先按配方生产毛坯和基础件，再交给机器继续加工。");
        var firstMaterials=List.of(part("gun_fastener"),new ItemStack(AllItems.BRASS_SHEET.get()),new ItemStack(AllItems.STURDY_SHEET.get()));
        var depots=List.of(basic,precision,reinforced);
        for(int n=0;n<depots.size();n++)hold(scene,util,depots.get(n).above(2),firstMaterials.get(n));
        note(scene,basic.above(2),"同一种毛坯，安装连接件、黄铜板或坚固板，可以进入不同加工路线。");
        for(int n=0;n<depots.size();n++){
            deploy(scene,depots.get(n).above(2));
            replace(scene,depots.get(n),workpiece("parts/"+GunParts.Tier.values()[n].component("barrel")));
        }
        note(scene,reinforced,"基础、精密和强化组件从毛坯分别加工，无需逐级升级。");
        BlockPos saw=new BlockPos(4,1,6),pressDepot=new BlockPos(6,1,6),press=pressDepot.above(2);
        show(scene,util,saw);show(scene,util,pressDepot);show(scene,util,press);
        scene.world().setKineticSpeed(util.select().position(saw),64);
        scene.world().setKineticSpeed(util.select().position(press),64);
        scene.overlay().showControls(saw.getCenter().add(0,.8,0),Pointing.RIGHT,70).withItem(workpiece("parts/barrel_component"));
        note(scene,saw,"沿配方完成后续切削、安装和冲压，便可得到对应组件。");
        // Complete the basic barrel example: fastener -> cutting -> iron sheet -> pressing.
        hold(scene,util,basic.above(2),new ItemStack(AllItems.IRON_SHEET.get()));
        deploy(scene,basic.above(2));
        scene.world().removeItemsFromBelt(basic);
        replace(scene,pressDepot,workpiece("parts/barrel_component"));
        press(scene,press);
        replace(scene,pressDepot,part("barrel_component"));
        scene.effects().indicateSuccess(pressDepot);
        note(scene,pressDepot,"组件等级决定制造用料，不会改变成品枪械的属性。");
        note(scene,precision,"不同组件的后续工序各不相同，可在配方查看器中查看。");
        scene.overlay().showControls(basic.above(2).getCenter(),Pointing.RIGHT,90).withItem(new ItemStack(GunParts.FORMING_JIG.get()));
        note(scene,basic.above(2),"制作枪管毛坯、弹簧、连接件、握持件和光学毛坯时，先让机械手持零件加工夹具处理原材料；夹具不会消耗。");
        scene.markAsFinished();
    }

    public static void assembly(SceneBuilder builder,SceneBuildingUtil util){
        var scene=start(builder,"gun_assembly","选择并装配枪械");
        BlockPos first=new BlockPos(2,1,3),next=new BlockPos(4,1,3),last=new BlockPos(6,1,3);
        show(scene,util,first);
        replace(scene,first,part("receiver_component"));
        scene.overlay().showControls(first.getCenter().add(0,1,0),Pointing.RIGHT,70).rightClick().withItem(part("receiver_component"));
        note(scene,first,"手持机匣组件右击，可以选择要装配的枪械。");
        note(scene,first,"机匣规格必须符合配方；开启“仅适配”可筛选当前机匣能制作的枪械。");
        replace(scene,first,selectedReceiver());
        scene.effects().indicateSuccess(first);
        note(scene,first,"确认后消耗一个机匣，得到选定枪型的待装配件。");
        show(scene,util,first.above(2));show(scene,util,next);show(scene,util,next.above(2));
        scene.world().setKineticSpeed(util.select().everywhere(),64);
        hold(scene,util,first.above(2),part("barrel_component"));
        deploy(scene,first.above(2));
        scene.world().removeItemsFromBelt(first);
        replace(scene,next,workpiece("guns/tacz/glock_17"));
        hold(scene,util,next.above(2),part("action_component"));
        note(scene,next.above(2),"按照目标配方，依次安装所需组件。");
        deploy(scene,next.above(2));
        note(scene,next,"零件的等级和数量随枪械而变，不必为所有枪械准备同一套组件。");
        show(scene,util,last);show(scene,util,last.above(2));
        scene.world().setKineticSpeed(util.select().position(last.above(2)),64);
        scene.world().removeItemsFromBelt(next);
        replace(scene,last,workpiece("guns/tacz/glock_17"));
        note(scene,last,"完成配方中的全部工序，再经最后冲压，即可得到枪械。");
        press(scene,last.above(2));
        replace(scene,last,GunItemBuilder.create().setId(ResourceLocation.parse("tacz:glock_17")).build());
        scene.effects().indicateSuccess(last);
        scene.overlay().showControls(first.getCenter().add(0,1,0),Pointing.RIGHT,70).rightClick().whileSneaking().withItem(selectedReceiver());
        note(scene,first,"尚未加工的待装配件可潜行右击，退回原机匣。");
        scene.markAsFinished();
    }

    public static void copying(SceneBuilder builder,SceneBuildingUtil util){
        var scene=start(builder,"gun_target_copying","批量选择装配目标");
        BlockPos depot=new BlockPos(3,1,4),deployer=depot.above(2),sample=new BlockPos(6,1,4);
        show(scene,util,depot);show(scene,util,deployer);show(scene,util,sample);
        var tool=selectedReceiver();replace(scene,sample,tool);
        scene.overlay().showOutline(PonderPalette.GREEN,"copy_tool",util.select().position(sample),90);
        note(scene,sample,"先选择一种枪械，保留一个尚未加工的待装配件。");
        scene.world().removeItemsFromBelt(sample);
        hold(scene,util,deployer,tool);
        scene.world().setKineticSpeed(util.select().position(deployer),64);
        scene.overlay().showControls(deployer.getCenter(),Pointing.RIGHT,70).withItem(tool);
        note(scene,deployer,"将它交给机械手，作为选择同一装配目标的工具。");
        replace(scene,depot,part("receiver_component"));
        note(scene,depot,"在下方送入符合目标配方、尚未选择枪型的机匣。");
        deploy(scene,deployer);replace(scene,depot,selectedReceiver());
        scene.effects().indicateSuccess(depot);
        note(scene,depot,"机械手将机匣设为相同目标，手中的工具不会消耗。");
        replace(scene,sample,selectedReceiver());
        replace(scene,depot,part("receiver_component"));deploy(scene,deployer);replace(scene,depot,selectedReceiver());
        note(scene,depot,"将得到的待装配件送入后续生产线，即可连续制造同一种枪械。");
        note(scene,deployer,"已开始加工，或配方已经变化的待装配件，不能作为选择工具。");
        scene.markAsFinished();
    }
}
