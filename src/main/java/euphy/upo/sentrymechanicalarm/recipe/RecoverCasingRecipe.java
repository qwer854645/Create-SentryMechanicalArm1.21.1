package euphy.upo.sentrymechanicalarm.recipe;

import euphy.upo.sentrymechanicalarm.content.*;
import euphy.upo.sentrymechanicalarm.registry.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Cancel one unfinished round, recovering its casing but none of the spent materials. */
public final class RecoverCasingRecipe extends CustomRecipe {
    public RecoverCasingRecipe(CraftingBookCategory category){super(category);}
    private static ItemStack source(CraftingInput input){
        ItemStack result=ItemStack.EMPTY;
        for(int i=0;i<input.size();i++){var stack=input.getItem(i);if(stack.isEmpty())continue;if(!result.isEmpty()||!(stack.getItem() instanceof ReloadingCasingItem)||EmptyCasingItem.ammoId(stack)==null)return ItemStack.EMPTY;result=stack;}
        return result;
    }
    public boolean matches(CraftingInput input,Level level){return SentryRegistry.EMPTY_CASING.isBound()&&!source(input).isEmpty();}
    public ItemStack assemble(CraftingInput input,HolderLookup.Provider registries){var stack=source(input);return stack.isEmpty()?ItemStack.EMPTY:EmptyCasingItem.create(EmptyCasingItem.ammoId(stack),1);}
    public boolean canCraftInDimensions(int width,int height){return width*height>=1;}
    public RecipeSerializer<?> getSerializer(){return SentryRecipeSerializers.RECOVER_CASING.get();}
}
