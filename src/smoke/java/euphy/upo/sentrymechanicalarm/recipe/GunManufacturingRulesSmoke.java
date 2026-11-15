package euphy.upo.sentrymechanicalarm.recipe;

import com.google.gson.Gson;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import euphy.upo.sentrymechanicalarm.registry.GunParts;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import static euphy.upo.sentrymechanicalarm.registry.GunParts.Tier.*;

/** Focused boundary checks, only compiled with the opt-in smoke sources. */
public final class GunManufacturingRulesSmoke {
    private static int checks;
    private static void expect(boolean condition,String message){
        if(!condition)throw new AssertionError(message);checks++;
    }
    private static GunComponentRequirements.Requirements rule(String type,String fields){
        return GunComponentRequirements.derive(type,new Gson().fromJson("{"+fields+"}",GunData.class));
    }
    public static int verify(){
        checks=0;
        var basic=rule("rifle","\"fire_mode\":[\"semi\"]");
        expect(basic.tiers().get("barrel")==PRECISION&&basic.tiers().get("grip")==BASIC,"mixed component grades lost");
        expect(basic.counts().get("control")==0,"ordinary gun charged for control");
        var manual=rule("sniper","\"bolt\":\"manual_action\",\"rpm\":1200,\"fire_mode\":[\"auto\"]");
        expect(manual.counts().get("recoil")==0&&manual.tiers().get("action")==PRECISION,"manual action charged for automatic recoil");
        expect(rule("rifle","\"rpm\":899,\"fire_mode\":[\"auto\"]").counts().get("recoil")==1,"899 RPM threshold");
        var rapid=rule("rifle","\"rpm\":900,\"fire_mode\":[\"auto\"]");
        expect(rapid.counts().get("recoil")==2&&rapid.tiers().get("recoil")==REINFORCED,"900 RPM threshold");
        expect(rule("rifle","\"rpm\":1200,\"fire_mode\":[\"semi\"]").counts().get("recoil")==1,"semi-auto theoretical RPM used");
        expect(rule("pistol","\"ammo_amount\":59").counts().get("feed")==1,"59 capacity threshold");
        expect(rule("pistol","\"ammo_amount\":60").counts().get("feed")==2,"60 capacity threshold");
        expect(rule("pistol","\"ammo_amount\":120").counts().get("feed")==3,"120 capacity threshold");
        for(String feed:new String[]{"fuel","inventory","manual"})
            expect(rule("pistol","\"ammo_amount\":120,\"reload\":{\"type\":\""+feed+"\"}").counts().get("feed")==1,"special feed capacity used: "+feed);
        expect(rule("pistol","\"ammo_amount\":120,\"reload\":{\"infinite\":true}").counts().get("feed")==1,"infinite capacity used");
        var charged=rule("pistol","\"fire_mode\":[\"semi\"],\"charging\":{\"semi\":{}}");
        expect(charged.counts().get("control")==1&&charged.tiers().get("control")==PRECISION,"declared charging ignored");
        var receiver=Ingredient.of(GunParts.item("receiver_component"));
        expect(GunAssemblyRecipes.validReceiver(receiver),"valid receiver rejected");
        expect(!GunAssemblyRecipes.validReceiver(Ingredient.EMPTY),"empty receiver accepted");
        expect(!GunAssemblyRecipes.validReceiver(Ingredient.of(Items.IRON_INGOT)),"non-receiver accepted");
        expect(!GunAssemblyRecipes.validReceiver(new CountedDeployerIngredient(receiver,2).toVanilla()),"multi-receiver cost undercharged");
        expect(GunAssemblyRecipes.validReceiver(Ingredient.of(GunParts.item("receiver_component"),GunParts.item("precision_receiver_component"))),"alternative receivers rejected");
        return checks;
    }
}
