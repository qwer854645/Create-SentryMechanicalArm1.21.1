package euphy.upo.sentrymechanicalarm.recipe;

import com.simibubi.create.AllItems;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import net.minecraft.world.item.*;
import java.math.BigDecimal;
import java.util.*;

/** Shared metal identities and denominations; no cross-metal value conversion. */
public final class AmmoMetalCosts {
    private AmmoMetalCosts() {}
    public record Denomination(Item item,int units) {}
    public static List<Item> priority(){return List.of(AllItems.COPPER_NUGGET.get(),Items.IRON_NUGGET,Items.GOLD_NUGGET);}
    public static Item canonical(Item item){
        if(item==AllItems.COPPER_NUGGET.get()||item==Items.COPPER_INGOT||item==Items.COPPER_BLOCK)return AllItems.COPPER_NUGGET.get();
        if(item==Items.IRON_NUGGET||item==Items.IRON_INGOT||item==Items.IRON_BLOCK)return Items.IRON_NUGGET;
        if(item==Items.GOLD_NUGGET||item==Items.GOLD_INGOT||item==Items.GOLD_BLOCK)return Items.GOLD_NUGGET;
        return item;
    }
    public static boolean isMetal(Item item){return priority().contains(canonical(item));}
    public static int units(Item item){
        if(item==Items.COPPER_BLOCK||item==Items.IRON_BLOCK||item==Items.GOLD_BLOCK)return 81;
        return canonical(item)!=item?9:1;
    }
    public static Optional<Item> primary(Map<Item,?> totals){return priority().stream().filter(totals::containsKey).findFirst();}
    public static List<Denomination> denominations(Item metal){
        if(metal==AllItems.COPPER_NUGGET.get())return List.of(new Denomination(metal,1),new Denomination(Items.COPPER_INGOT,9),new Denomination(Items.COPPER_BLOCK,81));
        if(metal==Items.IRON_NUGGET)return List.of(new Denomination(metal,1),new Denomination(Items.IRON_INGOT,9),new Denomination(Items.IRON_BLOCK,81));
        if(metal==Items.GOLD_NUGGET)return List.of(new Denomination(metal,1),new Denomination(Items.GOLD_INGOT,9),new Denomination(Items.GOLD_BLOCK,81));
        return List.of();
    }
    public static Map<Item,BigDecimal> nativeTotals(GunSmithTableRecipe recipe){
        var totals=new LinkedHashMap<Item,BigDecimal>();
        for(var material:recipe.getInputs()){
            var ingredient=material.getIngredient();var candidates=ingredient.getItems();
            if(material.getCount()<1||!ingredient.isSimple()||candidates.length==0)continue;
            Item input=candidates[0].getItem();
            if(!isMetal(input)||Arrays.stream(candidates).anyMatch(s->s.isEmpty()||s.getItem()!=input||!s.getComponentsPatch().isEmpty()||s.hasCraftingRemainingItem()))continue;
            totals.merge(canonical(input),BigDecimal.valueOf(material.getCount()).multiply(BigDecimal.valueOf(units(input))),BigDecimal::add);
        }
        return Map.copyOf(totals);
    }
}
