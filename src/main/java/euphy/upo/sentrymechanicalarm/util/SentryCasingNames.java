package euphy.upo.sentrymechanicalarm.util;

import java.util.regex.Pattern;

/** Change the localized ammo noun without losing the pack's caliber, spacing or color codes. */
public final class SentryCasingNames {
    private static final Pattern TRAILING_FORMAT=Pattern.compile("((?:§[0-9a-fk-or])*)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern ENGLISH=Pattern.compile("\\b(bullets?|rounds?|cartridges?|shells?|ammunition|ammo)$",Pattern.CASE_INSENSITIVE);
    public static String fromAmmoName(String name){
        var formatting=TRAILING_FORMAT.matcher(name);formatting.find();
        String end=formatting.group(1),body=name.substring(0,name.length()-end.length());
        if(body.endsWith("弹壳"))return name;
        if(body.endsWith("子弹")||body.endsWith("弹药"))return body.substring(0,body.length()-2)+"弹壳"+end;
        if(body.endsWith("弹"))return body+"壳"+end;
        var english=ENGLISH.matcher(body);
        if(english.find()){
            String original=english.group(1),casing=original.toLowerCase(java.util.Locale.ROOT).endsWith("s")&&!original.equalsIgnoreCase("ammunition")?"Casings":"Casing";
            if(original.equals(original.toLowerCase(java.util.Locale.ROOT)))casing=casing.toLowerCase(java.util.Locale.ROOT);
            else if(original.equals(original.toUpperCase(java.util.Locale.ROOT)))casing=casing.toUpperCase(java.util.Locale.ROOT);
            return body.substring(0,english.start())+casing+end;
        }
        return null;
    }
}
