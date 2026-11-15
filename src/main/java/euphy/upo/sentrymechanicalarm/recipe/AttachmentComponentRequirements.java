package euphy.upo.sentrymechanicalarm.recipe;

import com.google.gson.JsonObject;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.config.common.GunConfig;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.modifier.custom.SilenceModifier;
import com.tacz.guns.resource.pojo.data.attachment.*;
import euphy.upo.sentrymechanicalarm.registry.GunParts;
import java.util.*;
import static euphy.upo.sentrymechanicalarm.registry.GunParts.Tier.*;

/** Manufacturing heuristics use declared features; names and arbitrary scripts are never classifiers. */
final class AttachmentComponentRequirements {
    record Requirements(Map<String,GunParts.Tier> grades,Map<String,Integer> counts,int springs) {}
    static Requirements derive(AttachmentType type,AttachmentData data,JsonObject display){
        var grades=new LinkedHashMap<String,GunParts.Tier>();var counts=new LinkedHashMap<String,Integer>();
        for(String family:GunParts.allFamilies()){grades.put(family,BASIC);counts.put(family,0);}
        counts.put("attachment_base",1);int springs=0;
        switch(type){
            case SCOPE -> {
                double zoom=maximumZoom(display);counts.put("optics",zoom>=12?3:zoom>=8?2:1);
                if(zoom>=4)grades.put("optics",PRECISION);
                if(zoom>=12)grades.put("attachment_base",PRECISION);
            }
            case MUZZLE -> {
                counts.put("muzzle",1);
                if(silenced(data)){counts.put("muzzle",2);grades.put("muzzle",PRECISION);}
            }
            case STOCK,GRIP -> counts.put("grip",1);
            case LASER -> {counts.put("optics",1);counts.put("control",1);grades.put("control",PRECISION);}
            case EXTENDED_MAG -> {
                int level=Math.max(1,Math.min(3,data.getExtendedMagLevel()));
                counts.put("feed",level);springs=level;
                if(level>=2)grades.put("feed",level==2?PRECISION:REINFORCED);
                if(level>=3)grades.put("attachment_base",REINFORCED);
            }
            default -> throw new IllegalArgumentException("Unsupported attachment category");
        }
        return new Requirements(Map.copyOf(grades),Map.copyOf(counts),springs);
    }
    private static double maximumZoom(JsonObject display){
        if(display==null||!display.has("zoom")||!display.get("zoom").isJsonArray())return 1;
        double max=1;
        for(var value:display.getAsJsonArray("zoom")){
            if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())continue;
            double zoom=value.getAsDouble();if(Double.isFinite(zoom)&&zoom>0)max=Math.max(max,zoom);
        }
        return max;
    }
    private static boolean silenced(AttachmentData data){
        var property=data.getModifier().get(SilenceModifier.ID);
        if(property==null||!(property.getValue() instanceof it.unimi.dsi.fastutil.Pair<?,?> pair))return false;
        if(Boolean.TRUE.equals(pair.right()))return true;
        if(!(pair.left() instanceof Modifier modifier))return false;
        // A script can calculate gun-dependent results. Leave those to explicit manufacturing rules.
        if(modifier.getFunction()!=null&&!modifier.getFunction().isBlank())return false;
        int original=GunConfig.DEFAULT_GUN_FIRE_SOUND_DISTANCE.get();
        double adjusted=AttachmentPropertyManager.eval(modifier,original);
        return original>0&&Double.isFinite(adjusted)&&adjusted<original;
    }
}
