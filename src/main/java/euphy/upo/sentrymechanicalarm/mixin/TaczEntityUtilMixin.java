package euphy.upo.sentrymechanicalarm.mixin;

import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.EntityUtil;
import euphy.upo.sentrymechanicalarm.util.SentryBulletOwnership;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityUtil.class, remap = false)
public abstract class TaczEntityUtilMixin {
    // Shared by TaCZ's single-hit and piercing/multi-hit entity queries.
    @Inject(method = "getHitResult", at = @At("HEAD"), cancellable = true, remap = false)
    private static void sentry$ignoreOwnStructure(Projectile bullet, Entity candidate, Vec3 start, Vec3 end,
            CallbackInfoReturnable<EntityKineticBullet.EntityResult> cir) {
        if (SentryBulletOwnership.ignores(bullet, candidate)) cir.setReturnValue(null);
    }
}
