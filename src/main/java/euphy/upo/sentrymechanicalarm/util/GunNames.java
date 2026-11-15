package euphy.upo.sentrymechanicalarm.util;

import com.tacz.guns.api.TimelessAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import java.util.Arrays;
import java.util.Locale;

/** Keep translations lazy, so names also follow language and resource-pack reloads. */
public final class GunNames {
    private GunNames() {}
    public static String readable(ResourceLocation gun) {
        return gun.getPath().replace('_', ' ').replace('/', ' ').toUpperCase(Locale.ROOT);
    }
    public static Component name(ResourceLocation gun) {
        return TimelessAPI.getCommonGunIndex(gun).<Component>map(index -> {
            String key = index.getPojo().getName();
            return key == null || key.isBlank() ? Component.literal(readable(gun))
                    : Component.translatableWithFallback(key, readable(gun));
        }).orElseGet(() -> Component.literal(readable(gun)));
    }
    public static Component withFallback(Component name, ResourceLocation gun) {
        MutableComponent result;
        if (name.getContents() instanceof TranslatableContents text) {
            String fallback = text.getFallback();
            if (fallback == null || fallback.isBlank() || fallback.equals(text.getKey())) fallback = readable(gun);
            Object[] args = Arrays.stream(text.getArgs()).map(arg -> arg instanceof Component component
                    ? withFallback(component, gun) : arg).toArray();
            result = Component.translatableWithFallback(text.getKey(), fallback, args);
        } else result = name.plainCopy();
        result.setStyle(name.getStyle());
        for (var sibling : name.getSiblings()) result.append(withFallback(sibling, gun));
        return result;
    }
}
