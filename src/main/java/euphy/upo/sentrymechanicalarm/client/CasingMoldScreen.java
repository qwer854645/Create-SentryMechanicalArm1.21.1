package euphy.upo.sentrymechanicalarm.client;

import euphy.upo.sentrymechanicalarm.content.EmptyCasingItem;
import euphy.upo.sentrymechanicalarm.network.CasingMoldSelectPacket;
import euphy.upo.sentrymechanicalarm.recipe.CasingFormingRecipes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

public final class CasingMoldScreen extends Screen {
    private final InteractionHand hand;
    private List<ResourceLocation> all = List.of(), filtered = List.of();
    private final List<Button> rows = new ArrayList<>();
    private int page;
    private Button previous, next;
    public CasingMoldScreen(InteractionHand hand) { super(Component.translatable("screen.sentrymechanicalarm.casing_mold")); this.hand = hand; }
    public static void open(InteractionHand hand) { Minecraft.getInstance().setScreen(new CasingMoldScreen(hand)); }
    @Override protected void init() {
        rows.clear();
        all = minecraft.level == null ? List.of() : CasingFormingRecipes.choices(minecraft.level.getRecipeManager());
        int left = width / 2 - 140, top = Math.max(24, height / 2 - 115);
        var search = addRenderableWidget(new EditBox(font, left, top + 20, 280, 20, Component.translatable("screen.sentrymechanicalarm.casing_mold.search")));
        search.setHint(Component.translatable("screen.sentrymechanicalarm.casing_mold.search"));
        search.setResponder(text -> { page = 0; filter(text); });
        for (int i = 0; i < 6; i++) {
            int row = i;
            rows.add(addRenderableWidget(Button.builder(Component.empty(), button -> {
                int index = page * 6 + row;
                if (index >= filtered.size()) return;
                PacketDistributor.sendToServer(new CasingMoldSelectPacket(hand == InteractionHand.OFF_HAND, filtered.get(index)));
                onClose();
            }).bounds(left, top + 46 + i * 23, 280, 20).build()));
        }
        previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; refreshRows(); }).bounds(left, top + 188, 32, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; refreshRows(); }).bounds(left + 248, top + 188, 32, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(left + 90, top + 188, 100, 20).build());
        filter(""); setInitialFocus(search);
    }
    private void filter(String text) {
        String q = text.toLowerCase(Locale.ROOT);
        filtered = all.stream().filter(id -> id.toString().toLowerCase(Locale.ROOT).contains(q)
                || EmptyCasingItem.create(id, 1).getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)).toList();
        refreshRows();
    }
    private void refreshRows() {
        page = Math.max(0, Math.min(page, Math.max(0, (filtered.size() - 1) / 6)));
        for (int i = 0; i < rows.size(); i++) {
            int index = page * 6 + i; var row = rows.get(i); row.visible = index < filtered.size();
            if (row.visible) row.setMessage(EmptyCasingItem.create(filtered.get(index), 1).getHoverName());
        }
        previous.active = page > 0; next.active = (page + 1) * 6 < filtered.size();
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        int top = Math.max(24, height / 2 - 115);
        graphics.drawCenteredString(font, title, width / 2, top + 5, 0xffffff);
        if (filtered.isEmpty()) graphics.drawCenteredString(font, Component.translatable("screen.sentrymechanicalarm.casing_mold.empty"), width / 2, top + 90, 0xaaaaaa);
    }
    @Override public boolean isPauseScreen() { return false; }
}
