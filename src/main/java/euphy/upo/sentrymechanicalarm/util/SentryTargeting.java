package euphy.upo.sentrymechanicalarm.util;

import euphy.upo.sentrymechanicalarm.SMAServerConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

public final class SentryTargeting {
    public static boolean defaultTarget(LivingEntity entity) {
        if (entity instanceof Enemy) return true;
        var entries = SMAServerConfig.EXTRA_TARGETS.get();
        return entries.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())
                || entries.contains(entity.getName().getString());
    }
}
