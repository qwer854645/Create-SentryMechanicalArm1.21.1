package euphy.upo.sentrymechanicalarm.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import euphy.upo.sentrymechanicalarm.content.EmptyCasingItem;
import euphy.upo.sentrymechanicalarm.registry.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.*;
import java.util.stream.Stream;

/** Ammo identity is semantic: unrelated names/components do not change the caliber. */
public record CasingIngredient(ResourceLocation ammoId) implements ICustomIngredient {
    public static final MapCodec<CasingIngredient> CODEC=ResourceLocation.CODEC.fieldOf("ammo_id").xmap(CasingIngredient::new,CasingIngredient::ammoId);
    public boolean test(ItemStack stack){return ReloadingRecipes.enabled()&&!stack.isEmpty()&&stack.is(SentryRegistry.EMPTY_CASING.get())&&ammoId.equals(EmptyCasingItem.ammoId(stack));}
    public Stream<ItemStack> getItems(){return Stream.of(EmptyCasingItem.create(ammoId,1));}
    public boolean isSimple(){return false;}
    public IngredientType<?> getType(){return SentryRecipeSerializers.CASING.get();}
}
