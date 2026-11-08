package euphy.upo.sentrymechanicalarm.util;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.tacz.guns.api.TimelessAPI;
import euphy.upo.sentrymechanicalarm.content.EmptyCasingItem;
import euphy.upo.sentrymechanicalarm.recipe.ManufacturingRules;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Server-only templates; normal item-entity synchronization carries arbitrary item components. */
public record CasingDropRule(ItemStack template,int count,Double chance,ResourceLocation casing) {
    public CasingDropRule { template=template.copyWithCount(1); }
    @Override public ItemStack template(){return template.copy();}
    public boolean enabled(){return count>0&&!template.isEmpty();}
    public static CasingDropRule disabled(){return new CasingDropRule(ItemStack.EMPTY,0,null,null);}
    public static CasingDropRule automatic(ResourceLocation ammo,boolean enabled){return enabled?new CasingDropRule(EmptyCasingItem.create(ammo,1),1,null,ammo):disabled();}
    public static CasingDropRule read(MinecraftServer server,ResourceLocation ammo,JsonObject json){
        ManufacturingRules.requireKeys(json,Set.of("enabled","casing","item","count","chance"));
        if(!ManufacturingRules.enabled(json))return disabled();
        int count=json.has("count")?json.get("count").getAsBigDecimal().intValueExact():-1;
        if(count<-1||count>1024||json.has("count")&&count<0)throw new IllegalArgumentException("Drop count must be 0..1024");
        if(count==0)return disabled();
        Double chance=json.has("chance")?json.get("chance").getAsDouble():null;
        if(chance!=null&&(!Double.isFinite(chance)||chance<0||chance>1))throw new IllegalArgumentException("Drop chance must be 0..1");
        if(json.has("casing")&&json.has("item"))throw new IllegalArgumentException("Use casing or item, not both");
        ResourceLocation casing=null;ItemStack item;
        if(json.has("item")){
            item=ItemStack.CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE),json.get("item")).getOrThrow();
            if(item.isEmpty()||item.getMaxStackSize()<1)throw new IllegalArgumentException("Empty drop item");
            if(item.getItem() instanceof EmptyCasingItem)throw new IllegalArgumentException("Use casing for typed casing drops");
        }else{
            casing=json.has("casing")?ResourceLocation.parse(json.get("casing").getAsString()):ammo;
            if(TimelessAPI.getCommonAmmoIndex(casing).isEmpty())throw new IllegalArgumentException("Unknown casing ammo: "+casing);
            if(ManufacturingRules.casingOverride(casing).equals(Optional.of(false)))throw new IllegalArgumentException("Target casing explicitly disabled: "+casing);
            item=EmptyCasingItem.create(casing,1);
        }
        int amount=count<0?item.getCount():count;
        if(amount<1||amount>1024)throw new IllegalArgumentException("Drop count must be 1..1024, or explicit zero to disable");
        return new CasingDropRule(item,amount,chance,casing);
    }
}
