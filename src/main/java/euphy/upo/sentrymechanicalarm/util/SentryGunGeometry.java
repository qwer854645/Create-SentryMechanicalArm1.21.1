package euphy.upo.sentrymechanicalarm.util;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Vector3d;

/** Shared arm-to-gun transform. No client model classes or float world coordinates. */
public final class SentryGunGeometry {
    private SentryGunGeometry() {}
    public record Pose(Vec3 position, Vec3 forward) {
        public float yaw() { return (float)Math.toDegrees(Math.atan2(-forward.x, forward.z)); }
        public float pitch() { return (float)-Math.toDegrees(Math.atan2(forward.y, forward.horizontalDistance())); }
        public boolean aligned(Vec3 target, double degrees) {
            Vec3 delta = target.subtract(position);
            return finite(target) && delta.lengthSqr() > 1e-10
                    && forward.dot(delta.normalize()) >= Math.cos(Math.toRadians(degrees));
        }
    }
    public static boolean finite(Vec3 v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    public static Matrix4d mount(boolean ceiling, float base, float lower, float upper, float head, boolean minigun) {
        Matrix4d m = new Matrix4d().translate(.5,.5,.5);
        if (ceiling) m.rotateX(Math.PI);
        m.translate(0,.25,0).rotateY(Math.toRadians(base))
                .translate(0,.125,0).rotateX(Math.toRadians(lower))
                .translate(0,0,-.875).rotateX(Math.toRadians(upper-180))
                .translate(0,0,-.9375).rotateX(Math.toRadians(head-45))
                .rotateX(Math.PI/2).translate(0,-.625,0).rotateX(-Math.PI/2).translate(0,.18,0);
        if (minigun) m.rotateX(-Math.PI/2).translate(0,-.7,.2);
        return m.scale(1.5);
    }
    public static Pose pose(BlockPos block, boolean ceiling, float base, float lower, float upper, float head,
                            SentryGunProfiles.Profile profile) {
        Matrix4d m = mount(ceiling, base, lower, upper, head, profile.minigunMount());
        var p = m.transformPosition(new Vector3d(profile.muzzle().x, profile.muzzle().y, profile.muzzle().z));
        var d = m.transformDirection(new Vector3d(profile.forward().x, profile.forward().y, profile.forward().z)).normalize();
        return new Pose(new Vec3(block.getX()+p.x,block.getY()+p.y,block.getZ()+p.z), new Vec3(d.x,d.y,d.z));
    }
}
