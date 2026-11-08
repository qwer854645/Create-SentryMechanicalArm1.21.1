package euphy.upo.sentrymechanicalarm.util;

import euphy.upo.sentrymechanicalarm.content.VirtualSentryArmBlockEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.common.util.FakePlayer;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

public final class SentryBulletOwnership {
    private static final Map<Projectile, WeakReference<Entity>> ROOTS = new WeakHashMap<>();

    public static void remember(Projectile bullet, FakePlayer shooter) {
        if (SentryFakePlayer.getArmFromPlayer(shooter) instanceof VirtualSentryArmBlockEntity arm) {
            var contraption = arm.getOwningContraption();
            if (contraption != null && contraption.entity != null)
                ROOTS.put(bullet, new WeakReference<>(contraption.entity.getRootVehicle()));
        }
    }

    public static boolean ignores(Projectile bullet, Entity candidate) {
        var reference = ROOTS.get(bullet);
        var root = reference == null ? null : reference.get();
        return root != null && (candidate == root || candidate.getRootVehicle() == root);
    }
}
