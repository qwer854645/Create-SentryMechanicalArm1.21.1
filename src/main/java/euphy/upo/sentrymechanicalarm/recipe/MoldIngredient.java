package euphy.upo.sentrymechanicalarm.recipe;

import com.mojang.serialization.MapCodec;
import euphy.upo.sentrymechanicalarm.content.*;
import euphy.upo.sentrymechanicalarm.registry.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.*;
import java.util.stream.Stream;

public record MoldIngredient(ResourceLocation ammo) implements ICustomIngredient {
    public static final MapCodec<MoldIngredient> CODEC = ResourceLocation.CODEC.fieldOf("ammo_id").xmap(MoldIngredient::new, MoldIngredient::ammo);
    public boolean test(ItemStack stack) { return CasingFormingRecipes.enabled() && stack.is(SentryRegistry.CASING_MOLD.get()) && ammo.equals(EmptyCasingItem.ammoId(stack)); }
    public Stream<ItemStack> getItems() { return Stream.of(CasingMoldItem.create(ammo)); }
    public boolean isSimple() { return false; }
    public IngredientType<?> getType() { return SentryRecipeSerializers.MOLD.get(); }
}
