package euphy.upo.sentrymechanicalarm.mixin;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import euphy.upo.sentrymechanicalarm.util.GunNames;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Missing gunpack translations must not become raw keys in item or JEI tooltips. */
@Mixin(AbstractGunItem.class)
public abstract class TaczGunNameMixin {
    @Inject(method = "getName", at = @At("RETURN"), cancellable = true, remap = false)
    private void sentry$gunNameFallback(ItemStack stack, CallbackInfoReturnable<Component> cir) {
        var gun = IGun.getIGunOrNull(stack);
        if (gun != null) cir.setReturnValue(GunNames.withFallback(cir.getReturnValue(), gun.getGunId(stack)));
    }
}
