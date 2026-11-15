package euphy.upo.sentrymechanicalarm.client;

import com.tacz.guns.api.TimelessAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.*;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import java.util.*;

/** Wrap and scroll the gunpack's own description without changing the crafting layout. */
final class GunDescriptionPanel extends AbstractWidget {
    private ResourceLocation gun;
    private final boolean attachment;
    private int offset;
    private List<FormattedCharSequence> lines = List.of();
    GunDescriptionPanel(int x, int y, int width, int height, boolean attachment) {
        super(x, y, width, height, Component.translatable("screen.sentrymechanicalarm.gun_assembly.description"));
        this.attachment=attachment;
    }
    void setTarget(ResourceLocation id) { if (!Objects.equals(gun, id)) offset = 0; gun = id; }
    private Component description() {
        String key = gun == null ? null : euphy.upo.sentrymechanicalarm.util.AssemblyTargets.description(gun,attachment);
        if (key == null || key.isBlank()) return missing();
        if (I18n.exists(key)) return Component.translatable(key);
        // TaCZ accepts both translation keys and literal sentences in this field.
        // Missing keys get a neutral message rather than a fabricated description.
        if (key.matches("[A-Za-z0-9_:/-]+(?:\\.[A-Za-z0-9_:/-]+)+")) return missing();
        return Component.literal(key);
    }
    private Component missing() { return Component.translatable("screen.sentrymechanicalarm.gun_assembly.no_description"); }
    private int visibleLines() { return Math.max(1, height / 10); }
    private int maximum() { return Math.max(0, lines.size() - visibleLines()); }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!isMouseOver(x, y) || vertical == 0) return false;
        offset = Math.max(0, Math.min(maximum(), offset - (int)Math.signum(vertical))); return true;
    }
    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (gun == null) return;
        var font = Minecraft.getInstance().font;
        var text = Component.translatable("screen.sentrymechanicalarm.gun_assembly.pack", gun.getNamespace())
                .append("\n").append(description());
        lines = font.split(text, Math.max(1, width - 12));
        offset = Math.max(0, Math.min(maximum(), offset));
        graphics.enableScissor(getX(), getY(), getX() + width, getY() + height);
        for (int n = 0; n < visibleLines() && offset + n < lines.size(); n++)
            graphics.drawString(font, lines.get(offset + n), getX(), getY() + n * 10, 0xffd1d0c3, false);
        if (maximum() > 0) {
            int thumb = Math.min(height, Math.max(6, height * visibleLines() / lines.size()));
            int y = getY() + (height - thumb) * offset / maximum();
            graphics.fill(getX() + width - 5, getY(), getX() + width - 2, getY() + height, 0xff202222);
            graphics.fill(getX() + width - 5, y, getX() + width - 2, y + thumb, 0xffb4b2a2);
        }
        graphics.disableScissor();
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage().copy().append(": ").append(description()));
    }
}
