package euphy.upo.sentrymechanicalarm.recipe;

import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.FeedType;
import euphy.upo.sentrymechanicalarm.registry.GunParts;
import java.util.*;
import static euphy.upo.sentrymechanicalarm.registry.GunParts.Tier.*;

/** Gameplay costs derived from declared gun data, never names or damage values. */
final class GunComponentRequirements {
    record Requirements(Map<String, GunParts.Tier> tiers, Map<String, Integer> counts) {}

    static Requirements derive(String type, GunData data) {
        var tiers = new LinkedHashMap<String, GunParts.Tier>();
        var counts = new LinkedHashMap<String, Integer>();
        for (String family : GunParts.FAMILIES) { tiers.put(family, BASIC); counts.put(family, 1); }
        counts.put("control", 0);
        switch (type) {
            case "smg" -> grade(tiers, PRECISION, "action", "recoil");
            case "shotgun" -> grade(tiers, REINFORCED, "barrel");
            case "rifle" -> grade(tiers, PRECISION, "barrel", "receiver", "action", "trigger");
            case "sniper" -> {
                grade(tiers, PRECISION, "barrel", "action", "trigger");
                grade(tiers, REINFORCED, "receiver", "recoil");
            }
            case "mg" -> {
                grade(tiers, REINFORCED, "barrel", "receiver", "recoil", "feed");
                grade(tiers, PRECISION, "action", "trigger"); counts.put("feed", 2);
            }
            default -> { /* Basic profile also supports explicitly enabled custom categories. */ }
        }
        if (data.getBolt() == Bolt.MANUAL_ACTION) {
            counts.put("recoil", 0);
            grade(tiers, PRECISION, "action");
        } else {
            var modes = data.getFireModeSet();
            boolean rapidMode = modes.contains(FireMode.AUTO) || modes.contains(FireMode.BURST);
            if (rapidMode) {
                grade(tiers, PRECISION, "trigger");
                int rpm = modes.stream().filter(m -> m == FireMode.AUTO || m == FireMode.BURST)
                        .mapToInt(data::getRoundsPerMinute).max().orElse(0);
                if (rpm >= 900) {
                    grade(tiers, PRECISION, "action");
                    grade(tiers, REINFORCED, "recoil"); counts.put("recoil", 2);
                }
            }
        }
        // Magazine capacity describes the unmodified firearm, not optional extended magazines.
        int capacity = data.getAmmoAmount();
        if (!data.getReloadData().isInfinite() && data.getReloadData().getType() == FeedType.MAGAZINE && capacity >= 60)
            counts.put("feed", Math.max(counts.get("feed"), capacity >= 120 ? 3 : 2));
        // Charging is a declared gameplay feature, not an inference that a gun is electronic.
        if (data.getFireModeSet().stream().anyMatch(mode -> data.getChargeData(mode) != null)) {
            grade(tiers, PRECISION, "control"); counts.put("control", 1);
        }
        return new Requirements(Map.copyOf(tiers), Map.copyOf(counts));
    }

    private static void grade(Map<String, GunParts.Tier> tiers, GunParts.Tier tier, String... families) {
        for (String family : families) tiers.put(family, tier);
    }
}
