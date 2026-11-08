package euphy.upo.sentrymechanicalarm.ponder.scene;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.tacz.guns.api.item.IGun;
import euphy.upo.sentrymechanicalarm.content.BlazeFireControlBlockEntity;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.ParticleEmitter;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class FireControlScene {

    public static void control(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("fire_control", "火控台");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        BlockPos sentryPos = util.grid().at(1, 1, 2);
        BlockPos firePose = util.grid().at(3, 1, 2);
        BlockPos villager = util.grid().at(3, 1, 2);
        BlockPos villagerPos = util.grid().at(4, 0, 2);
        Vec3 villagertext = util.vector().of(3.5, 2, 0.5);

        Selection sentrySelect = util.select().position(sentryPos);
        Selection fireControlSelect = util.select().position(firePose);

        ItemStack wrench = new ItemStack(AllItems.WRENCH.get());
        ItemStack list =  new ItemStack(SentryRegistry.FIRE_CONTROL_CLIPBOARD.get());

        scene.idle(20);

        scene.world().showSection(sentrySelect, Direction.DOWN);
        scene.idle(10);
        setGun(scene, sentryPos, "tacz:minigun");
        scene.idle(10);
        scene.overlay().showText(60)
                .text("哨戒动力臂默认只会攻击敌对生物")
                .pointAt(util.vector().topOf(sentryPos))
                .placeNearTarget();
        scene.idle(70);

        scene.world().showSection(fireControlSelect, Direction.DOWN);

        scene.overlay().showText(60)
                .text("使用火控台，可以指定攻击特定的生物")
                .pointAt(util.vector().topOf(firePose))
                .placeNearTarget().attachKeyFrame();
        scene.idle(70);

        scene.overlay().showControls(firePose.getCenter(), Pointing.RIGHT, 50)
                .rightClick()
                .withItem(wrench);
        scene.idle(7);
        scene.overlay().showOutline(PonderPalette.GREEN, "frame_1", fireControlSelect, 50);
        scene.overlay().showText(50)
                .text("扳手右键二者其一……")
                .pointAt(util.vector().topOf(firePose))
                .placeNearTarget();
        scene.idle(60);

        scene.overlay().showControls(sentryPos.getCenter(), Pointing.RIGHT, 30)
                .rightClick()
                .withItem(wrench);
        scene.idle(7);
        scene.overlay().showOutline(PonderPalette.GREEN, "frame_1", sentrySelect, 30);
        scene.overlay().showText(30)
                .text("再右键另一个")
                .pointAt(util.vector().topOf(sentryPos))
                .placeNearTarget();
        scene.idle(40);

        scene.overlay().showText(60)
                .text("即可在二者间建立连接")
                .pointAt(util.vector().topOf(firePose))
                .placeNearTarget();
        for (int i = 0; i < 18; i++) {
            drawGreenLine(scene, sentryPos, firePose, util, 8);
            scene.idle(4);
        }
        scene.idle(20);

        scene.world().setKineticSpeed(util.select().everywhere(), 128);

        ElementLink<EntityElement> villagerLink = scene.world().createEntity(w -> {
            Villager entity = EntityType.VILLAGER.create(w);
            Vec3 p = util.vector().of(3.5, 1, 0.5);
            entity.setPos(p.x, p.y, p.z);
            entity.xo = p.x;
            entity.yo = p.y;
            entity.zo = p.z;
            float rotation = 180.0f;
            entity.yRotO = rotation;
            entity.setYRot(rotation);
            entity.yHeadRotO = rotation;
            entity.yHeadRot = rotation;

            return entity;
        });

        scene.idle(20);

        scene.overlay().showControls(villagerPos.east().getCenter(), Pointing.RIGHT, 60)
                .withItem(new ItemStack(Items.SPYGLASS));
        scene.idle(7);
        scene.overlay().showText(60)
                .text("主手使用望远镜、副手拿许可，按中键记录目标；也可右键许可编辑名单。")
                .pointAt(villagertext)
                .placeNearTarget().attachKeyFrame();
        scene.idle(70);

        scene.overlay().showControls(firePose.east().getCenter(), Pointing.RIGHT, 60)
                .rightClick()
                .withItem(list);
        scene.overlay().showText(60)
                .text("将名单交付火控台……")
                .pointAt(util.vector().topOf(firePose))
                .placeNearTarget();
        scene.idle(30);
        scene.world().modifyBlockEntity(firePose, BlazeFireControlBlockEntity.class, be -> {
            be.inventory.setStackInSlot(0, list);
        });
        scene.effects().indicateSuccess(firePose);
        scene.idle(40);

        scene.overlay().showText(80)
                .text("黑名单模式只攻击名单内目标；白名单是安全列表，名单外目标可能被攻击。")
                .pointAt(sentryPos.getCenter())
                .placeNearTarget();
        scene.idle(40);

        Vec3 villagerVec = util.vector().of(3.5, 1, 0.5);
        Vec3 targetVec = villagerVec.add(0, 1.5, 0);
        Vec3 sentryCenter = util.vector().centerOf(sentryPos);
        Vec3 diff = targetVec.subtract(sentryCenter);
        double yawRad = Math.atan2(diff.z, diff.x);
        double yawDeg = Math.toDegrees(yawRad);
        float targetBaseAngle = (float) (yawDeg);

        double horizontalDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        double pitchRad = Math.atan2(diff.y, horizontalDist);
        double pitchDeg = Math.toDegrees(pitchRad);

        float targetHeadAngle = (float) (-pitchDeg);
        scene.world().modifyBlockEntity(sentryPos, SentryArmBlockEntity.class, be -> {
            be.idleTargetYaw = targetBaseAngle;
            be.idleTargetPitch = targetHeadAngle;
        });

        scene.idle(30);

        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.world().modifyEntity(villagerLink, entity -> {
            if (entity instanceof LivingEntity living) {
                living.hurtTime = 60;
                living.animateHurt(0);
            }
        });
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.world().modifyEntity(villagerLink, entity -> {
            if (entity instanceof LivingEntity living) {
                living.hurtTime = 60;
                living.animateHurt(0);
            }
        });
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);

        scene.world().modifyEntity(villagerLink, entity -> {
            if (entity instanceof LivingEntity living) {
                living.setHealth(0.0f);
                living.deathTime = 1;
            }
        });
        scene.idle(30);

        scene.markAsFinished();
    }

    public static void targeting(SceneBuilder builder,SceneBuildingUtil util) {
        var scene=new CreateSceneBuilder(builder);
        scene.title("sentry_targeting","名单模式与瞄准镜指挥");
        scene.configureBasePlate(0,0,9);
        scene.scaleSceneView(.75f);
        scene.showBasePlate();
        BlockPos arm=new BlockPos(2,1,3),control=new BlockPos(5,1,3);
        scene.world().setBlock(arm,SentryRegistry.SENTRY_ARM_BLOCK.get().defaultBlockState(),false);
        scene.world().setBlock(control,SentryRegistry.BLAZE_FIRE_CONTROL.get().defaultBlockState(),false);
        scene.world().showSection(util.select().position(arm),Direction.DOWN);
        scene.world().showSection(util.select().position(control),Direction.DOWN);
        SentryMechanicalArmScene.setGun(scene,arm,"tacz:glock_17");
        var permit=new ItemStack(SentryRegistry.FIRE_CONTROL_CLIPBOARD.get());
        var scope=new ItemStack(SentryRegistry.SENTRY_SCOPE.get());
        targetingNote(scene,control,"先用扳手连接火控台与哨戒动力臂，再右键许可编辑名单。");
        scene.overlay().showControls(control.getCenter(),Pointing.RIGHT,70).rightClick().withItem(permit);
        targetingNote(scene,control,"黑名单是攻击列表，只攻击名单内目标；白名单是安全列表，会保护名单内目标。");
        targetingNote(scene,control,"白名单模式请先把自己加入名单：手持许可潜行右键。主手望远镜加副手许可，中键可远程记录目标。");
        scene.world().modifyBlockEntity(control,BlazeFireControlBlockEntity.class,be->be.inventory.setStackInSlot(0,permit.copy()));
        scene.overlay().showControls(control.getCenter(),Pointing.RIGHT,70).rightClick().withItem(scope);
        targetingNote(scene,control,"手持哨戒瞄准镜，潜行右键火控台绑定；再次操作同一火控台可解绑。");
        var target=scene.world().createEntity(world->{
            var entity=EntityType.ZOMBIE.create(world);
            entity.setPos(5.5,1,6.5);
            return entity;
        });
        scene.idle(15);
        scene.overlay().showControls(new Vec3(5.5,2.5,6.5),Pointing.RIGHT,70).withItem(scope);
        drawGreenLine(scene,control,new BlockPos(5,2,6),util,8);
        targetingNote(scene,new BlockPos(5,2,6),"右键使用瞄准镜放大，使用期间左键标记实体，引导已连接的炮台集火。");
        scene.world().setBlock(new BlockPos(6,1,6),Blocks.TARGET.defaultBlockState(),false);
        scene.world().showSection(util.select().position(6,1,6),Direction.DOWN);
        targetingNote(scene,new BlockPos(6,1,6),"使用期间中键标记坐标。坐标攻击只供符合条件的爆炸武器使用，普通枪械不会因此对地面开火。");
        targetingNote(scene,arm,"标记不能绕过射程、动力与弹药条件；移动结构上的火控也遵守这些限制。");
        scene.markAsFinished();
    }

    private static void targetingNote(CreateSceneBuilder scene,BlockPos pos,String text) {
        scene.overlay().showText(100).text(text).pointAt(pos.getCenter()).placeNearTarget().attachKeyFrame();
        scene.idle(110);
    }

    private static void setGun(CreateSceneBuilder scene, BlockPos sentryPos, String gunId) {
        Item gunItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse("tacz:modern_kinetic_gun"));
        if (gunItem == Items.AIR) return;
        ItemStack stack = new ItemStack(gunItem);
        IGun iGun = IGun.getIGunOrNull(stack);
        if (iGun != null) {
            iGun.setGunId(stack, ResourceLocation.parse(gunId));
        }
        scene.world().modifyBlockEntity(sentryPos, SentryArmBlockEntity.class, be -> be.setHeldItem(stack));
    }

    private static void drawGreenLine(CreateSceneBuilder scene, BlockPos startPos, BlockPos endPos, SceneBuildingUtil util, int density) {
        Vec3 startVec = util.vector().centerOf(startPos);
        Vec3 endVec = util.vector().centerOf(endPos);
        Vec3 difference = endVec.subtract(startVec);
        double distance = difference.length();

        int particleCount = (int) (distance * density);
        if (particleCount < 1) particleCount = 1;

        DustParticleOptions greenDust = new DustParticleOptions(new Vector3f(0.0f, 1.0f, 0.0f), 1.0f);
        ParticleEmitter greenEmitter = (level, x, y, z) -> level.addParticle(greenDust, x, y, z, 0, 0, 0);

        for (int i = 0; i <= particleCount; i++) {
            double t = (double) i / particleCount;
            Vec3 currentPos = startVec.add(difference.scale(t));
            scene.effects().emitParticles(currentPos, greenEmitter, 1.0f, 1);
        }
    }

    private static void fire(CreateSceneBuilder scene, BlockPos sentryPos, BlockPos targetPos, SceneBuildingUtil util) {
        scene.world().modifyBlockEntity(sentryPos, SentryArmBlockEntity.class, be -> {
            be.setLastShootTime(System.currentTimeMillis());
            be.setShellEjected();
        });
    }
}
