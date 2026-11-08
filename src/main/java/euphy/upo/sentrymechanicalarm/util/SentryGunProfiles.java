package euphy.upo.sentrymechanicalarm.util;

import com.google.gson.*;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.network.SentryGunProfilesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4d;
import org.joml.Vector3d;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Canonical rest-pose muzzle data, derived on the server from gun-pack JSON, then synchronized. */
@EventBusSubscriber(modid = SentryMechanicalArm.MODID)
public final class SentryGunProfiles {
    public record Profile(Vec3 muzzle, Vec3 forward, boolean visibleProjectile, boolean minigunMount, boolean fromModel) {
        public Profile {
            if (!SentryGunGeometry.finite(muzzle) || muzzle.length() > 8 || !SentryGunGeometry.finite(forward)
                    || !Double.isFinite(forward.lengthSqr()) || forward.lengthSqr() < 1e-8) throw new IllegalArgumentException("Invalid muzzle profile");
            forward = forward.normalize();
        }
    }
    public static final Profile FALLBACK = new Profile(new Vec3(0,0,-.75), new Vec3(0,0,-1), true,false,false);
    private static volatile Map<ResourceLocation,Profile> server = Map.of(), client = Map.of();
    public static Profile get(ItemStack gun) {
        var api = IGun.getIGunOrNull(gun);
        return api == null ? FALLBACK : get(api.getGunId(gun));
    }
    public static Profile get(ResourceLocation id) { return server.getOrDefault(id,client.getOrDefault(id,FALLBACK)); }
    public static Map<ResourceLocation,Profile> snapshot() { return server; }
    public static Map<ResourceLocation,Profile> clientSnapshot() { return client; }
    public static boolean needsSyntheticTracer(ItemStack gun) {
        if (get(gun).visibleProjectile()) return false;
        var api=IGun.getIGunOrNull(gun);
        return api != null && TimelessAPI.getCommonGunIndex(api.getGunId(gun))
                .map(index -> !index.getGunData().getBulletData().hasTracerAmmo()).orElse(false);
    }
    public static void receive(Map<ResourceLocation,Profile> profiles) { client = Map.copyOf(profiles); }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void start(ServerStartedEvent event) { rebuild(event.getServer()); }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { server = Map.of(); }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void sync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null || server.isEmpty()) rebuild(event.getPlayerList().getServer());
        var packet = new SentryGunProfilesPacket(server);
        if (event.getPlayer() != null) PacketDistributor.sendToPlayer(event.getPlayer(),packet);
        else for (var p : event.getPlayerList().getPlayers()) PacketDistributor.sendToPlayer(p,packet);
    }
    public static void rebuild(MinecraftServer minecraft) {
        Map<ResourceLocation,Profile> loaded = new LinkedHashMap<>();
        ResourceManager resources = minecraft.getResourceManager();
        List<PackResources> packs = resources.listPacks().toList();
        for (var entry : TimelessAPI.getAllCommonGunIndex()) {
            var id = entry.getKey(); var index = entry.getValue();
            try {
                JsonObject display = asset(packs,index.getPojo().getDisplay(),"display/guns/");
                boolean visible = true;
                var ammo = TimelessAPI.getCommonAmmoIndex(index.getGunData().getAmmoId());
                if (ammo.isPresent()) {
                    JsonObject ammoDisplay = asset(packs,ammo.get().getPojo().getDisplay(),"display/ammo/");
                    if (ammoDisplay != null) visible = ammoDisplay.has("entity") && !ammoDisplay.get("entity").isJsonNull();
                }
                // Existing special mounting is an explicit preset, never a name substring classifier.
                boolean special = id.equals(ResourceLocation.parse("tacz:minigun"));
                Profile profile = new Profile(FALLBACK.muzzle(),FALLBACK.forward(),visible,special,false);
                if (display != null && display.has("model") && (!display.has("model_type") || display.get("model_type").getAsString().equals("default"))) {
                    try {
                        JsonObject model = asset(packs,ResourceLocation.parse(display.get("model").getAsString()),"geo_models/");
                        if (model != null) profile = parseModel(display,model,visible,special);
                    } catch (Exception e) {
                        SentryMechanicalArm.LOGGER.warn("Model muzzle unavailable for {}; data-pack override or fallback will be used: {}",id,e.toString());
                    }
                }
                var overrideId = ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"sentry_muzzles/"+id.getPath()+".json");
                var override = resources.getResource(overrideId);
                if (override.isPresent()) {
                    try (var reader = override.get().openAsReader()) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        profile = new Profile(json.has("muzzle") ? vector(json,"muzzle",profile.muzzle()) : profile.muzzle(),
                                vector(json,"forward",profile.forward()),
                                json.has("visible_projectile") ? json.get("visible_projectile").getAsBoolean() : profile.visibleProjectile(),
                                json.has("minigun_mount") ? json.get("minigun_mount").getAsBoolean() : profile.minigunMount(), !json.has("muzzle") && profile.fromModel());
                    }
                }
                loaded.put(id,profile);
            } catch (Exception e) {
                loaded.put(id,FALLBACK);
                SentryMechanicalArm.LOGGER.warn("Using fallback muzzle for {}: {}",id,e.toString());
            }
        }
        server = Map.copyOf(loaded);
        SentryMechanicalArm.LOGGER.info("Sentry muzzle profiles: {} guns, {} from model JSON",loaded.size(),loaded.values().stream().filter(Profile::fromModel).count());
    }
    public static JsonObject asset(List<PackResources> packs, ResourceLocation id, String prefix) throws Exception {
        if (id == null) return null;
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(id.getNamespace(),prefix+id.getPath()+".json");
        for (int i=packs.size()-1;i>=0;i--) {
            var stream = packs.get(i).getResource(PackType.CLIENT_RESOURCES,file);
            if (stream != null) try (var reader = new InputStreamReader(stream.get(),StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
        return null;
    }
    private record Bone(String parent, Vec3 pivot, Vec3 rotation) {}
    public static Profile parseModel(JsonObject display, JsonObject model, boolean visible, boolean special) {
        if (!model.has("minecraft:geometry")) throw new IllegalArgumentException("Unsupported legacy/custom model");
        Map<String,Bone> bones = new LinkedHashMap<>();
        for (var element : model.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")) {
            var b = element.getAsJsonObject();
            bones.put(b.get("name").getAsString(),new Bone(b.has("parent") ? b.get("parent").getAsString() : null,
                    vector(b,"pivot",Vec3.ZERO),vector(b,"rotation",Vec3.ZERO)));
        }
        var hand = path(bones,"thirdperson_hand"); var muzzle = path(bones,"muzzle_flash");
        if (hand.isEmpty() || muzzle.isEmpty()) throw new IllegalArgumentException("Missing hand or muzzle node");
        Vec3 scale = new Vec3(1,1,1);
        if (display.has("transform") && display.getAsJsonObject("transform").has("scale"))
            scale = vector(display.getAsJsonObject("transform").getAsJsonObject("scale"),"thirdperson",scale);
        Matrix4d m = new Matrix4d().translate(0,1.5,0).scale(-1,-1,1).translate(0,1.5,0);
        for (int i=hand.size()-1;i>=0;i--) {
            Bone b=hand.get(i); Vec3 p=partPosition(b,bones); Vec3 r=b.rotation();
            m.rotateX(-Math.toRadians(r.x)).rotateY(-Math.toRadians(r.y)).rotateZ(-Math.toRadians(r.z));
            m.translate(-p.x*scale.x/16, b.parent()==null ? (1.5-p.y/16)*scale.y : -p.y*scale.y/16, -p.z*scale.z/16);
        }
        m.translate(0,-1.5,0).translate(0,1.5,0).scale(scale.x,scale.y,scale.z).translate(0,-1.5,0);
        for (Bone b : muzzle) {
            Vec3 p=partPosition(b,bones), r=b.rotation();
            m.translate(p.x/16,p.y/16,p.z/16).rotateZ(Math.toRadians(r.z)).rotateY(Math.toRadians(r.y)).rotateX(Math.toRadians(r.x));
        }
        var pos=m.transformPosition(new Vector3d()); var dir=m.transformDirection(new Vector3d(0,0,-1)).normalize();
        return new Profile(new Vec3(pos.x,pos.y,pos.z),new Vec3(dir.x,dir.y,dir.z),visible,special,true);
    }
    private static Vec3 partPosition(Bone b, Map<String,Bone> bones) {
        if (b.parent()==null) return new Vec3(b.pivot.x,24-b.pivot.y,b.pivot.z);
        Bone parent=Objects.requireNonNull(bones.get(b.parent()),"Missing parent");
        return new Vec3(b.pivot.x-parent.pivot.x,parent.pivot.y-b.pivot.y,b.pivot.z-parent.pivot.z);
    }
    private static List<Bone> path(Map<String,Bone> bones,String name) {
        List<Bone> result=new ArrayList<>(); Set<String> visited=new HashSet<>();
        while (name!=null && bones.containsKey(name)) {
            if (!visited.add(name) || visited.size()>256) throw new IllegalArgumentException("Cyclic bone hierarchy");
            Bone b=bones.get(name); result.add(b); name=b.parent();
        }
        Collections.reverse(result); return result;
    }
    private static Vec3 vector(JsonObject o,String key,Vec3 fallback) {
        if (!o.has(key)) return fallback;
        var a=o.getAsJsonArray(key);
        if (a.size()!=3) throw new IllegalArgumentException("Expected xyz vector");
        return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());
    }
}
