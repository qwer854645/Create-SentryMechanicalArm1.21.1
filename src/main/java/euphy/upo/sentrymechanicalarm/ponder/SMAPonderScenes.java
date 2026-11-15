package euphy.upo.sentrymechanicalarm.ponder;

import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.ponder.scene.FireControlScene;
import euphy.upo.sentrymechanicalarm.ponder.scene.SentryMechanicalArmScene;
import euphy.upo.sentrymechanicalarm.ponder.scene.CasingScenes;
import euphy.upo.sentrymechanicalarm.ponder.scene.GunManufacturingScenes;
import euphy.upo.sentrymechanicalarm.registry.GunParts;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class SMAPonderScenes {

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(GunParts.names().stream().filter(GunParts::attachmentPart).map(name->ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm",name)).toArray(ResourceLocation[]::new))
                .addStoryBoard("attachment_assembly",euphy.upo.sentrymechanicalarm.ponder.scene.AttachmentManufacturingScenes::attachmentAssembly,AllCreatePonderTags.KINETIC_APPLIANCES);
        helper.forComponents(GunParts.WORKPIECE.getId())
                .addStoryBoard("attachment_assembly",euphy.upo.sentrymechanicalarm.ponder.scene.AttachmentManufacturingScenes::attachmentAssembly,AllCreatePonderTags.KINETIC_APPLIANCES);
        helper.forComponents(GunParts.names().stream().map(name->ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm",name)).toArray(ResourceLocation[]::new))
                .addStoryBoard("gun_components",GunManufacturingScenes::components,AllCreatePonderTags.KINETIC_APPLIANCES);
        helper.forComponents(GunParts.WORKPIECE.getId())
                .addStoryBoard("gun_components",GunManufacturingScenes::components,AllCreatePonderTags.KINETIC_APPLIANCES);
        helper.forComponents(GunParts.WORKPIECE.getId(),ResourceLocation.parse("sentrymechanicalarm:receiver_component"),
                ResourceLocation.parse("sentrymechanicalarm:precision_receiver_component"),ResourceLocation.parse("sentrymechanicalarm:reinforced_receiver_component"))
                .addStoryBoard("gun_assembly",GunManufacturingScenes::assembly,AllCreatePonderTags.KINETIC_APPLIANCES)
                .addStoryBoard("gun_target_copying",GunManufacturingScenes::copying,AllCreatePonderTags.KINETIC_APPLIANCES);
        helper.forComponents(SentryRegistry.SENTRY_ARM_BLOCK.getId())
                .addStoryBoard("introduce", SentryMechanicalArmScene::introducing, AllCreatePonderTags.KINETIC_APPLIANCES)
                .addStoryBoard("supply", SentryMechanicalArmScene::supplying, AllCreatePonderTags.KINETIC_APPLIANCES)
                .addStoryBoard("movement", SentryMechanicalArmScene::movement, AllCreatePonderTags.KINETIC_APPLIANCES);

        helper.forComponents(SentryRegistry.BLAZE_FIRE_CONTROL.getId())
                .addStoryBoard("fire_control", FireControlScene::control, AllCreatePonderTags.DECORATION);

        helper.forComponents(SentryRegistry.BLAZE_FIRE_CONTROL.getId(),SentryRegistry.SENTRY_SCOPE.getId(),SentryRegistry.FIRE_CONTROL_CLIPBOARD.getId())
                .addStoryBoard("sentry_targeting",FireControlScene::targeting,AllCreatePonderTags.KINETIC_APPLIANCES);

        helper.forComponents(SentryRegistry.SENTRY_ARM_BLOCK.getId(),com.tacz.guns.init.ModBlocks.AMMO_STORAGE.getId(),
                com.tacz.guns.init.ModBlocks.AMMO_STORAGE_GOLD.getId(),com.tacz.guns.init.ModBlocks.AMMO_STORAGE_DIAMOND.getId())
                .addStoryBoard("sentry_depot_supply",CasingScenes::depotSupply,AllCreatePonderTags.KINETIC_APPLIANCES);

        if(SentryRegistry.EMPTY_CASING.isBound()) {
            helper.forComponents(SentryRegistry.CASING_MOLD.getId(),SentryRegistry.EMPTY_CASING.getId())
                    .addStoryBoard("casing_forming",CasingScenes::forming,AllCreatePonderTags.KINETIC_APPLIANCES);
            helper.forComponents(SentryRegistry.EMPTY_CASING.getId(),SentryRegistry.RELOADING_CASING.getId(),SentryRegistry.CASING_MOLD.getId())
                    .addStoryBoard("casing_reloading",CasingScenes::reloading,AllCreatePonderTags.KINETIC_APPLIANCES);
            helper.forComponents(SentryRegistry.SENTRY_ARM_BLOCK.getId(),SentryRegistry.CASING_COLLECTOR_BLOCK.getId(),SentryRegistry.EMPTY_CASING.getId())
                    .addStoryBoard("casing_collection",CasingScenes::collecting,AllCreatePonderTags.KINETIC_APPLIANCES);
        }
    }
}
