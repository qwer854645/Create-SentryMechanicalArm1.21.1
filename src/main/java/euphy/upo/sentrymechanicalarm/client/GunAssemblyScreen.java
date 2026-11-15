package euphy.upo.sentrymechanicalarm.client;

import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import euphy.upo.sentrymechanicalarm.content.ReceiverComponentItem;
import euphy.upo.sentrymechanicalarm.network.GunAssemblySelectPacket;
import euphy.upo.sentrymechanicalarm.recipe.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Dynamic game UI; Create icons are referenced from Create, not copied into this mod. */
@net.neoforged.fml.common.EventBusSubscriber(modid="sentrymechanicalarm",value=net.neoforged.api.distmarker.Dist.CLIENT)
public final class GunAssemblyScreen extends Screen {
    private final InteractionHand hand;
    private final boolean attachments;
    private List<GunAssemblyRecipes.Choice> all = List.of(), filtered = List.of();
    private List<String> packs = List.of("");
    private final List<Row> rows = new ArrayList<>();
    private GunAssemblyRecipes.Choice selected;
    private EditBox search;
    private StyledButton confirm, pack, matchOnly, modelTab, descriptionTab, category;
    private static final List<String> CATEGORIES=List.of("all","scope","muzzle","stock","grip","laser","extended_mag");
    private int categoryIndex;
    private Scrollbar scrollbar;
    private GunModelPreview modelPreview;
    private GunDescriptionPanel descriptionPanel;
    private boolean onlyCompatible, showDescription;
    private ItemStack heldReceiver = ItemStack.EMPTY;
    private int left, top, panelWidth, panelHeight, listWidth, visibleRows, offset, packIndex;
    private int listTop, listBottom;
    private static final int ROW_HEIGHT = 24, ROW_STRIDE = 26;
    private String query = "";
    private Component tr(String key, Object... args) { return Component.translatable("screen.sentrymechanicalarm."+(attachments?"attachment_assembly.":"gun_assembly.") + key, args); }
    public GunAssemblyScreen(InteractionHand hand) {
        super(Component.translatable("screen.sentrymechanicalarm."+(attachmentHand(hand)?"attachment_assembly.":"gun_assembly.")+"title"));
        this.hand=hand; this.attachments=attachmentHand(hand);
    }
    private static boolean attachmentHand(InteractionHand hand) { var player=Minecraft.getInstance().player; return player!=null && player.getItemInHand(hand).getItem() instanceof euphy.upo.sentrymechanicalarm.content.AttachmentBaseItem; }
    public static void open(InteractionHand hand) { Minecraft.getInstance().setScreen(new GunAssemblyScreen(hand)); }
    @net.neoforged.bus.api.SubscribeEvent
    public static void recipesUpdated(net.neoforged.neoforge.client.event.RecipesUpdatedEvent event) {
        if (Minecraft.getInstance().screen instanceof GunAssemblyScreen screen) {
            screen.loadChoices(); screen.offset=0; screen.filter();
        }
    }
    private void loadChoices() {
        String namespace=packs.get(Math.min(packIndex,packs.size()-1));
        all = minecraft.level == null ? List.of() : GunAssemblyRecipes.choices(minecraft.level).stream().filter(c->attachments==euphy.upo.sentrymechanicalarm.util.AssemblyTargets.attachment(c)).toList();
        var namespaces = new TreeSet<String>(); for (var c : all) namespaces.add(c.gun().getNamespace());
        var p = new ArrayList<String>(); p.add(""); p.addAll(namespaces); packs = List.copyOf(p);
        packIndex=Math.max(0,packs.indexOf(namespace));
        if (selected!=null) {
            var previous=selected.id();
            selected=all.stream().filter(c->c.id().equals(previous)).findFirst().orElse(null);
        }
    }
    @Override protected void init() {
        heldReceiver = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getItemInHand(hand).copy();
        panelWidth = Math.min(410, width - 12); panelHeight = Math.min(254, height - 12);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        listWidth = Math.max(115, panelWidth * 39 / 100);
        listTop = top + (attachments?100:78); listBottom = top + panelHeight - 57;
        // Derive complete rows from the actual viewport, including the last row's
        // height. No separate pagination area can overlap rows at intermediate sizes.
        visibleRows = Math.max(1, (listBottom - listTop + ROW_STRIDE - ROW_HEIGHT) / ROW_STRIDE);
        loadChoices();
        search = addRenderableWidget(new EditBox(font, left + 12, top + 34, listWidth - 12, 16, tr("search")));
        search.setMaxLength(100); search.setHint(tr("search")); search.setValue(query);
        search.setResponder(s -> { query = s; offset = 0; filter(); });
        pack = addRenderableWidget(new StyledButton(left + 10, top + 55, listWidth - 56, 18, Component.empty(), () -> {
            packIndex = (packIndex + 1) % packs.size(); offset = 0; filter();
        }));
        matchOnly = addRenderableWidget(new StyledButton(left + listWidth - 42, top + 55, 44, 18, Component.empty(), () -> {
            onlyCompatible = !onlyCompatible; offset = 0; filter();
        }));
        matchOnly.setTooltip(Tooltip.create(tr("matching_only")));
        if(attachments) category=addRenderableWidget(new StyledButton(left+10,top+77,listWidth-8,18,Component.empty(),()->{
            categoryIndex=(categoryIndex+1)%CATEGORIES.size();offset=0;filter();
        }));
        rows.clear();
        for (int n = 0; n < visibleRows; n++) rows.add(addRenderableWidget(new Row(left + 10, listTop + n * ROW_STRIDE, listWidth - 20)));
        scrollbar = addRenderableWidget(new Scrollbar(left + listWidth - 6, listTop, 8, Math.max(1, listBottom - listTop)));
        int modelX = left + listWidth + 20, modelWidth = panelWidth - listWidth - 35;
        int modelTop = top + 71, modelBottom = top + Math.min(141, panelHeight - 100) - 19;
        modelPreview = addRenderableWidget(new GunModelPreview(modelX, modelTop, modelWidth, Math.max(20, modelBottom - modelTop),attachments));
        descriptionPanel = addRenderableWidget(new GunDescriptionPanel(modelX, modelTop, modelWidth, Math.max(20, modelBottom - modelTop), attachments));
        int tabX = left + panelWidth - 73;
        modelTab = addRenderableWidget(new StyledButton(tabX, top + 35, 30, 17, tr("model_tab"), () -> { showDescription = false; refresh(); }));
        descriptionTab = addRenderableWidget(new StyledButton(tabX + 32, top + 35, 30, 17, tr("description"), () -> { showDescription = true; refresh(); }));
        addRenderableWidget(new StyledButton(left + 10, top + panelHeight - 35, 52, 22, Component.translatable("gui.cancel"), this::onClose));
        confirm = addRenderableWidget(new StyledButton(left + panelWidth - 103, top + panelHeight - 35, 93, 22, tr("confirm"), () -> {
            if (selected == null || !compatible(selected)) return;
            PacketDistributor.sendToServer(new GunAssemblySelectPacket(hand == InteractionHand.OFF_HAND, selected.id())); onClose();
        }));
        confirm.checkmark = true;
        if (selected == null || all.stream().noneMatch(c -> c.id().equals(selected.id())))
            selected = all.stream().filter(this::compatible).findFirst().orElse(all.isEmpty() ? null : all.getFirst());
        filter(); setInitialFocus(search);
    }
    private boolean compatible(GunAssemblyRecipes.Choice c) {
        return minecraft.player != null && c.input().receiver().test(minecraft.player.getItemInHand(hand)) && euphy.upo.sentrymechanicalarm.util.AssemblyTargets.enabled(c);
    }
    private Component gunName(GunAssemblyRecipes.Choice c) {
        return euphy.upo.sentrymechanicalarm.util.GunNames.withFallback(c.output().getHoverName(), c.gun());
    }
    private ItemStack receiverSample() {
        if (selected==null) return ItemStack.EMPTY;
        if (minecraft.player!=null) {
            var held=minecraft.player.getItemInHand(hand);
            if (selected.input().receiver().test(held)) return held.copyWithCount(1);
        }
        var samples=selected.input().receiver().getItems();
        return samples.length==0 ? ItemStack.EMPTY : samples[0];
    }
    private void filter() {
        String q = query.toLowerCase(Locale.ROOT), namespace = packs.get(packIndex);
        filtered = all.stream().filter(c -> (namespace.isEmpty() || namespace.equals(c.gun().getNamespace()))
                && (!onlyCompatible || compatible(c))
                && (!attachments || categoryIndex==0 || CATEGORIES.get(categoryIndex).equals(euphy.upo.sentrymechanicalarm.util.AssemblyTargets.type(c)))
                && (c.gun().toString().toLowerCase(Locale.ROOT).contains(q) || gunName(c).getString().toLowerCase(Locale.ROOT).contains(q))).toList();
        if (selected == null || filtered.stream().noneMatch(c -> c.id().equals(selected.id())))
            selected = filtered.stream().filter(this::compatible).findFirst().orElse(filtered.isEmpty() ? null : filtered.getFirst());
        int selectedIndex = filtered.indexOf(selected);
        if (selectedIndex >= 0) {
            if (selectedIndex < offset) offset = selectedIndex;
            else if (selectedIndex >= offset + visibleRows) offset = selectedIndex - visibleRows + 1;
        }
        if (pack != null) pack.setMessage(namespace.isEmpty() ? tr("all_packs") : Component.literal(namespace));
        if (matchOnly != null) { matchOnly.setMessage(tr(onlyCompatible ? "matching" : "unrestricted")); matchOnly.selectedTab = onlyCompatible; }
        if(category!=null)category.setMessage(tr("category."+CATEGORIES.get(categoryIndex)));
        refresh();
    }
    private void refresh() {
        offset = Math.max(0, Math.min(offset, Math.max(0, filtered.size() - visibleRows)));
        for (int n = 0; n < rows.size(); n++) {
            var row = rows.get(n); row.choice = offset + n < filtered.size() ? filtered.get(offset + n) : null;
            row.visible = row.choice != null; if (row.choice != null) row.setMessage(gunName(row.choice));
        }
        if (scrollbar != null) scrollbar.active = filtered.size() > visibleRows;
        if (modelPreview != null) {
            modelPreview.visible = selected != null && !showDescription;
            modelPreview.setTarget(selected == null ? null : selected.gun(), selected == null ? ItemStack.EMPTY : selected.output());
        }
        if (descriptionPanel != null) {
            descriptionPanel.visible = selected != null && showDescription;
            descriptionPanel.setTarget(selected == null ? null : selected.gun());
        }
        if (modelTab != null) { modelTab.active = selected != null; modelTab.selectedTab = !showDescription; }
        if (descriptionTab != null) { descriptionTab.active = selected != null; descriptionTab.selectedTab = showDescription; }
        if (confirm != null) confirm.active = selected != null && compatible(selected);
    }
    @Override public void tick() {
        super.tick();
        if (minecraft.player == null || !(minecraft.player.getItemInHand(hand).getItem() instanceof ReceiverComponentItem) || attachments!=attachmentHand(hand)) onClose();
        else if (!ItemStack.isSameItemSameComponents(heldReceiver, minecraft.player.getItemInHand(hand))) {
            heldReceiver = minecraft.player.getItemInHand(hand).copy(); filter();
        }
        else refresh();
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical != 0 && mouseX >= left + 10 && mouseX < left + listWidth + 2 && mouseY >= listTop && mouseY < listBottom) {
            offset -= (int)Math.signum(vertical); refresh(); return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }
    private void panel(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, 0xff171819);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xffa7a7a2);
        g.fill(x + 2, y + 2, x + w - 1, y + h - 1, 0xff555652);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, fill);
    }
    private void label(GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
        g.drawString(font, font.plainSubstrByWidth(text.getString(), Math.max(0, maxWidth)), x, y, color, false);
    }
    private void slot(GuiGraphics g, ItemStack stack, int x, int y) {
        panel(g, x, y, 20, 20, 0xff252728); g.renderItem(stack, x + 2, y + 2); g.renderItemDecorations(font, stack, x + 2, y + 2);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        panel(g, left, top, panelWidth, panelHeight, 0xff414343);
        panel(g, left + 4, top + 4, panelWidth - 8, 23, 0xffe1cba5);
        label(g, title, left + 13, top + 11, panelWidth - 28, 0xff30281e);
        panel(g, left + 6, top + 29, listWidth + 2, panelHeight - 82, 0xff292b2c);
        int rx = left + listWidth + 12, rw = panelWidth - listWidth - 19;
        panel(g, rx, top + 29, rw, panelHeight - 82, 0xff292b2c);
        if (filtered.isEmpty()) label(g, tr("empty"), left + 15, listTop + 16, listWidth - 15, 0xffaaaaaa);
        if (selected != null) {
            panel(g, rx + 5, top + 34, rw - 10, 19, 0xff6b4b32);
            label(g, gunName(selected), rx + 11, top + 40, rw - 82, 0xfffff9c7);
            label(g, gunSummary(selected), rx + 10, top + 59, rw - 20, 0xffb4b5b1);
            int materialsTop = top + Math.min(141, panelHeight - 100);
            label(g, tr("materials"), rx + 10, materialsTop - 14, rw - 20, 0xffe8dfc5);
            var materials = materialStacks(selected);
            int cols = Math.max(1, Math.min(8, (rw - 20) / 21));
            for (int n = 0; n < materials.size(); n++) {
                int sx = rx + 10 + (n % cols) * 21, sy = materialsTop + (n / cols) * 21;
                if (sy + 20 > top + panelHeight - 56) break;
                slot(g, materials.get(n), sx, sy);
            }
            var core = receiverSample();
            slot(g, core, left + 71, top + panelHeight - 35);
            label(g, tr("consume", core.getHoverName()), left + 96, top + panelHeight - 30, panelWidth - 205, 0xffe0e0d8);
        }
        label(g, selected != null && !compatible(selected) ? tr("wrong_receiver") : tr("hint"), left + 10, top + panelHeight - 48, panelWidth - 20,
                selected != null && !compatible(selected) ? 0xffe9b878 : 0xffb7b9b2);
        // Screen.render() renders the blurred background again in 1.21.1. Render
        // widgets directly, so our panel and labels remain sharp like the widgets.
        for (var widget : renderables) {
            if (widget instanceof Row) g.enableScissor(left + 10, listTop, left + listWidth - 10, listBottom);
            widget.render(g, mouseX, mouseY, partialTick);
            if (widget instanceof Row) g.disableScissor();
        }
        if (selected != null) {
            int cols = Math.max(1, Math.min(8, (rw - 20) / 21)); var materials = materialStacks(selected);
            for (int n = 0; n < materials.size(); n++) {
                int x = rx + 10 + (n % cols) * 21, y = top + Math.min(141, panelHeight - 100) + (n / cols) * 21;
                if (y + 20 > top + panelHeight - 56) break;
                if (mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20) g.renderTooltip(font, materials.get(n), mouseX, mouseY);
            }
            if (mouseX >= left + 71 && mouseX < left + 91 && mouseY >= top + panelHeight - 35 && mouseY < top + panelHeight - 15)
                g.renderTooltip(font, receiverSample(), mouseX, mouseY);
        }
    }
    private List<ItemStack> materialStacks(GunAssemblyRecipes.Choice choice) {
        var result = new ArrayList<ItemStack>();
        for (var step : choice.recipe().getSequence()) if (step.getRecipe() instanceof DeployerApplicationRecipe deployer) {
            var ingredient = deployer.getRequiredHeldItem(); var samples = ingredient.getItems();
            if (samples.length > 0) result.add(samples[0].copyWithCount(CountedDeployerIngredient.amount(ingredient)));
        }
        return result;
    }
    private Component gunSummary(GunAssemblyRecipes.Choice choice) {
        String type = euphy.upo.sentrymechanicalarm.util.AssemblyTargets.type(choice);
        var category = attachments?tr("category."+type):Component.translatableWithFallback("tacz.type." + type.toLowerCase(Locale.ROOT) + ".name", type.toUpperCase(Locale.ROOT));
        var grades = Component.empty();
        for (var tier : euphy.upo.sentrymechanicalarm.registry.GunParts.Tier.values()) {
            var receiver = euphy.upo.sentrymechanicalarm.registry.GunParts.item(tier.component(attachments?"attachment_base":"receiver"));
            if (Arrays.stream(choice.input().receiver().getItems()).noneMatch(stack -> stack.is(receiver))) continue;
            if (!grades.getSiblings().isEmpty()) grades.append("/");
            grades.append(tr("grade." + tier.name().toLowerCase(Locale.ROOT)));
        }
        return attachments?tr("summary",category,grades,choice.output().getCount()):tr("summary", category, grades);
    }
    @Override public boolean isPauseScreen() { return false; }
    private final class Scrollbar extends AbstractWidget {
        private double grabOffset;
        private boolean dragging;
        Scrollbar(int x, int y, int w, int h) { super(x, y, w, h, tr("scrollbar")); }
        private int maximum() { return Math.max(0, filtered.size() - visibleRows); }
        private int trackHeight() { return Math.max(1, height - 4); }
        private int thumbHeight() {
            return Math.min(trackHeight(), Math.max(12, (int)((long)trackHeight() * visibleRows / Math.max(visibleRows, filtered.size()))));
        }
        private int thumbTop() {
            return getY() + 2 + (maximum() == 0 ? 0 : (int)Math.round((double)offset * (trackHeight() - thumbHeight()) / maximum()));
        }
        private void moveTo(double mouseY) {
            int travel = trackHeight() - thumbHeight();
            if (travel <= 0 || maximum() == 0) return;
            double position = (mouseY - getY() - 2 - grabOffset) / travel;
            offset = (int)Math.round(Math.max(0, Math.min(1, position)) * maximum()); refresh();
        }
        @Override public void onClick(double mouseX, double mouseY) {
            dragging = true;
            grabOffset = mouseY >= thumbTop() && mouseY < thumbTop() + thumbHeight() ? mouseY - thumbTop() : thumbHeight() / 2.0;
            moveTo(mouseY);
        }
        @Override protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            if (dragging) moveTo(mouseY);
        }
        @Override public void onRelease(double mouseX, double mouseY) { dragging = false; }
        @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!active || !isFocused()) return false;
            switch (keyCode) {
                case org.lwjgl.glfw.GLFW.GLFW_KEY_UP -> offset--;
                case org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN -> offset++;
                case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP -> offset -= visibleRows;
                case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN -> offset += visibleRows;
                case org.lwjgl.glfw.GLFW.GLFW_KEY_HOME -> offset = 0;
                case org.lwjgl.glfw.GLFW.GLFW_KEY_END -> offset = maximum();
                default -> { return false; }
            }
            refresh(); return true;
        }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            panel(g, getX(), getY(), width, height, 0xff202222);
            panel(g, getX() + 1, thumbTop(), width - 2, thumbHeight(), !active ? 0xff646760
                    : dragging || isHoveredOrFocused() ? 0xffd4cfb9 : 0xffa5a59c);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, getMessage());
        }
    }
    private class StyledButton extends AbstractButton {
        private final Runnable action; boolean checkmark, selectedTab;
        StyledButton(int x, int y, int w, int h, Component text, Runnable action) { super(x, y, w, h, text); this.action = action; }
        @Override public void onPress() { action.run(); }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partialTick) {
            panel(g, getX(), getY(), width, height, !active ? 0xff555650 : selectedTab ? 0xffddc58d : isHoveredOrFocused() ? 0xffc9c3ac : 0xffa5a59c);
            if (checkmark) AllIcons.I_CONFIRM.render(g, getX() + 3, getY() + 3);
            label(g, getMessage(), getX() + (checkmark ? 22 : 6), getY() + (height - 8) / 2, width - (checkmark ? 25 : 12), active ? 0xff242822 : 0xff777870);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }
    private final class Row extends StyledButton {
        GunAssemblyRecipes.Choice choice;
        Row(int x, int y, int w) { super(x, y, w, ROW_HEIGHT, Component.empty(), () -> {}); }
        @Override public void onPress() { selected = choice; refresh(); }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partialTick) {
            boolean chosen = selected != null && choice.id().equals(selected.id());
            panel(g, getX(), getY(), width, height, chosen ? 0xff62543a : isHoveredOrFocused() ? 0xff555853 : 0xff373a38);
            if (chosen) { g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + 2, 0xffedc778); g.fill(getX() + 1, getY() + 1, getX() + 2, getY() + height - 1, 0xffedc778); }
            g.renderItem(choice.output(), getX() + 4, getY() + 4);
            label(g, gunName(choice), getX() + 25, getY() + 4, width - 30, compatible(choice) ? 0xffeee8d4 : 0xff999e98);
            label(g, Component.literal(choice.gun().getNamespace()), getX() + 25, getY() + 14, width - 30, 0xffa5aaa1);
        }
    }
}
