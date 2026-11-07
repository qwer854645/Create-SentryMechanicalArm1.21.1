package euphy.upo.sentrymechanicalarm.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import euphy.upo.sentrymechanicalarm.content.UnfinishedAmmoItem;
import euphy.upo.sentrymechanicalarm.registry.SentryRecipeSerializers;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import java.util.stream.Stream;

/** Match semantic progress, independently of unrelated components and numeric NBT encodings. */
public record UnfinishedAmmoIngredient(ResourceLocation ammoId, int sheets, boolean powder) implements ICustomIngredient {
    public static final MapCodec<UnfinishedAmmoIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("ammo_id").forGetter(UnfinishedAmmoIngredient::ammoId),
            Codec.intRange(0, UnfinishedAmmoItem.MAX_COPPER_SHEETS).optionalFieldOf("sheets", 0).forGetter(UnfinishedAmmoIngredient::sheets),
            Codec.BOOL.optionalFieldOf("powder", false).forGetter(UnfinishedAmmoIngredient::powder)
    ).apply(instance, UnfinishedAmmoIngredient::new));

    @Override public boolean test(ItemStack stack) {
        return !stack.isEmpty() && stack.is(SentryRegistry.UNFINISHED_AMMO.get())
                && ammoId.equals(UnfinishedAmmoItem.getAmmoId(stack))
                && UnfinishedAmmoItem.getCopperSheets(stack) >= sheets
                && (!powder || UnfinishedAmmoItem.hasGunpowder(stack));
    }
    @Override public Stream<ItemStack> getItems() {
        var stack = new ItemStack(SentryRegistry.UNFINISHED_AMMO.get());
        var tag = new CompoundTag();
        tag.putString("AmmoId", ammoId.toString());
        tag.putInt("CopperSheets", sheets);
        tag.putBoolean("GunpowderAdded", powder);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return Stream.of(stack);
    }
    @Override public boolean isSimple() { return false; }
    @Override public IngredientType<?> getType() { return SentryRecipeSerializers.UNFINISHED_AMMO.get(); }
}
