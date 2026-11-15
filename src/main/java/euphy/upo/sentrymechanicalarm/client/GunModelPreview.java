package euphy.upo.sentrymechanicalarm.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.*;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.*;

/** A real FIXED-context gun preview; GUI-context item rendering only draws its icon. */
final class GunModelPreview extends AbstractWidget {
    private ItemStack stack = ItemStack.EMPTY;
    private ResourceLocation gun;
    private float yaw = -45, pitch = 15, zoom = 1;
    private boolean dragging, failed;
    GunModelPreview(int x, int y, int width, int height,boolean attachment) {
        super(x, y, width, height, Component.translatable("screen.sentrymechanicalarm."+(attachment?"attachment_assembly":"gun_assembly")+".model"));
        setTooltip(Tooltip.create(Component.translatable("screen.sentrymechanicalarm.gun_assembly.model_hint")));
    }
    void setTarget(ResourceLocation id, ItemStack output) {
        if (!java.util.Objects.equals(gun, id)) { reset(); failed = false; }
        gun = id; stack = output;
    }
    private void reset() { yaw = -45; pitch = 15; zoom = 1; dragging = false; }
    @Override public void onClick(double mouseX, double mouseY) { dragging = true; }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (active && visible && button == 1 && isMouseOver(mouseX, mouseY)) { reset(); return true; }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        if (!dragging) return;
        yaw = Mth.wrapDegrees(yaw + (float)dragX * 1.2f);
        pitch = Mth.clamp(pitch + (float)dragY, -85, 85);
    }
    @Override public void onRelease(double mouseX, double mouseY) { dragging = false; }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (!active || !isMouseOver(mouseX, mouseY) || vertical == 0) return false;
        zoom = Mth.clamp(zoom * (float)Math.pow(1.12, vertical), 0.4f, 2.5f); return true;
    }
    private void unavailable(GuiGraphics graphics) {
        var mc = Minecraft.getInstance();
        graphics.drawCenteredString(mc.font, Component.translatable("screen.sentrymechanicalarm.gun_assembly.no_model"),
                getX() + width / 2, getY() + height / 2 - 4, 0xffaaaaaa);
    }
    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (stack.isEmpty()) return;
        var attachment=com.tacz.guns.api.item.IAttachment.getIAttachmentOrNull(stack);
        boolean available=attachment!=null ? TimelessAPI.getClientAttachmentIndex(attachment.getAttachmentId(stack)).isPresent()
                : TimelessAPI.getGunDisplay(stack).map(display->display.getGunModel()!=null).orElse(false);
        if (failed || !available) {
            unavailable(graphics); return;
        }
        var mc = Minecraft.getInstance();
        var buffers = mc.renderBuffers().bufferSource();
        graphics.flush();
        graphics.enableScissor(getX(), getY(), getX() + width, getY() + height);
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.enableDepthTest();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            modelView.mul(graphics.pose().last().pose());
            modelView.translate(getX() + width / 2f, getY() + height / 2f, 200);
            float scale = Math.min(width * 0.45f, height * 1.35f) * zoom;
            modelView.scale(scale, -scale, scale);
            modelView.rotate(Axis.XP.rotationDegrees(pitch)); modelView.rotate(Axis.YP.rotationDegrees(yaw));
            RenderSystem.applyModelViewMatrix();
            Lighting.setupForFlatItems();
            mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, new PoseStack(), buffers, mc.level, 0);
        } catch (RuntimeException error) {
            failed = true;
            SentryMechanicalArm.LOGGER.warn("Cannot render assembly preview for {}", gun, error);
        } finally {
            try { buffers.endBatch(); }
            finally {
                modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
                Lighting.setupFor3DItems(); RenderSystem.enableDepthTest(); RenderSystem.disableBlend();
                RenderSystem.setShaderColor(1, 1, 1, 1); graphics.disableScissor();
            }
        }
        if (failed) unavailable(graphics);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        output.add(NarratedElementType.USAGE, Component.translatable("screen.sentrymechanicalarm.gun_assembly.model_hint"));
    }
}
