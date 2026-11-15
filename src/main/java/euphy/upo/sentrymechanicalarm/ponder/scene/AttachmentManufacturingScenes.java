package euphy.upo.sentrymechanicalarm.ponder.scene;

import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import euphy.upo.sentrymechanicalarm.recipe.*;
import euphy.upo.sentrymechanicalarm.registry.*;
import euphy.upo.sentrymechanicalarm.util.AssemblyTargets;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import java.util.*;
import static euphy.upo.sentrymechanicalarm.ponder.scene.CasingScenes.*;

/** Prefer a current recipe example; generic illustrations also work before entering a world. */
public final class AttachmentManufacturingScenes {
    private static ItemStack part(String name){return new ItemStack(GunParts.item(name));}
    public static void attachmentAssembly(SceneBuilder builder,SceneBuildingUtil util){
        var scene=start(builder,"attachment_assembly","制造枪械配件");
        BlockPos input=new BlockPos(2,1,3),sample=new BlockPos(4,1,3),finish=new BlockPos(6,1,3);
        var level=Minecraft.getInstance().level;
        var choice=level==null?null:GunAssemblyRecipes.choices(level).stream().filter(AssemblyTargets::attachment)
                .sorted(Comparator.comparingInt(c->AssemblyTargets.type(c).equals("scope")?0:1)).findFirst().orElse(null);
        var base=choice==null?part("attachment_base_component"):choice.input().receiver().getItems()[0].copyWithCount(1);
        var prepared=choice==null?new ItemStack(GunParts.WORKPIECE.get()):choice.input().prepared();
        var output=choice==null?TimelessAPI.getAllCommonAttachmentIndex().stream()
                .filter(e->e.getValue().getType()==AttachmentType.SCOPE&&!e.getValue().getPojo().isBuiltinAttachment()&&!e.getValue().getPojo().isHidden())
                .sorted(Map.Entry.comparingByKey()).findFirst().map(e->AttachmentItemBuilder.create().setId(e.getKey()).build()).orElse(ItemStack.EMPTY):choice.output().copy();
        if(choice==null&&!output.isEmpty()){
            var api=com.tacz.guns.api.item.IAttachment.getIAttachmentOrNull(output);var id=api.getAttachmentId(output);
            prepared.set(ModDataComponents.GUN_WORK.get(),ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm","gun_manufacturing/attachments/"+id.getNamespace()+"/"+id.getPath()));
        }
        prepared.set(ModDataComponents.GUN_RECEIVER.get(),ItemContainerContents.fromItems(List.of(base.copy())));
        for(int x:List.of(2,4,6))show(scene,util,new BlockPos(x,1,6));
        scene.world().createItemOnBelt(new BlockPos(2,1,6),Direction.UP,part("attachment_base_component"));
        scene.world().createItemOnBelt(new BlockPos(4,1,6),Direction.UP,part("precision_optics_component"));
        scene.world().createItemOnBelt(new BlockPos(6,1,6),Direction.UP,part("reinforced_muzzle_component"));
        note(scene,new BlockPos(4,1,6),"基座、光学和枪口组件都有基础、精密与强化规格，可从各自毛坯直接加工。");
        show(scene,util,input);show(scene,util,sample);
        scene.world().createItemOnBelt(input,Direction.UP,base.copy());
        scene.overlay().showControls(input.getCenter().add(0,1,0),Pointing.RIGHT,70).rightClick().withItem(base);
        note(scene,input,"手持配件基座右击，按类别、枪包或名称选择目标配件。");
        scene.world().removeItemsFromBelt(input);scene.world().createItemOnBelt(input,Direction.UP,prepared.copy());
        note(scene,input,"确认后得到选定目标的待装配件；尚未加工时，可潜行右击取回原基座。");
        show(scene,util,input.above(2));hold(scene,util,input.above(2),prepared.copy());
        scene.world().setKineticSpeed(util.select().position(input.above(2)),64);
        scene.world().removeItemsFromBelt(input);scene.world().createItemOnBelt(input,Direction.UP,base.copy());
        deploy(scene,input.above(2));
        scene.world().removeItemsFromBelt(input);scene.world().createItemOnBelt(sample,Direction.UP,prepared.copy());
        note(scene,input.above(2),"机械手可重复使用未加工的待装配件，为下方的合格基座选择同一目标。");
        note(scene,sample,"按目标配方安装组件。瞄具倍率、扩容等级与消音数据会影响默认用料。");
        show(scene,util,finish);show(scene,util,finish.above(2));
        scene.world().setKineticSpeed(util.select().position(finish.above(2)),64);
        scene.world().removeItemsFromBelt(sample);scene.world().createItemOnBelt(finish,Direction.UP,prepared.copy());
        note(scene,finish,"完成配方中的所有工序和特殊材料投入，再进行最后冲压。");
        scene.world().modifyBlockEntity(finish.above(2),MechanicalPressBlockEntity.class,be->be.getPressingBehaviour().start(PressingBehaviour.Mode.BELT));
        scene.idle(40);scene.world().removeItemsFromBelt(finish);
        if(!output.isEmpty())scene.world().createItemOnBelt(finish,Direction.UP,output);
        scene.effects().indicateSuccess(finish);
        note(scene,finish,"批量配方会保留产量，并按整批计入基座和组件；具体用料以当前配方为准。");
        scene.markAsFinished();
    }
}
