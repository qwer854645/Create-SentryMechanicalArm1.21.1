package euphy.upo.sentrymechanicalarm.recipe;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import euphy.upo.sentrymechanicalarm.SMAServerConfig;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.*;
import java.util.*;

/** Cost sources only: never installs or modifies a gun pack's native crafting recipes. */
public final class AmmoMaterialSources {
    private AmmoMaterialSources() {}
    private static boolean keys(JsonObject object, Set<String> allowed) { return allowed.containsAll(object.keySet()); }
    public static List<RecipeHolder<GunSmithTableRecipe>> collect(RecipeManager manager, MinecraftServer server) {
        var result = new ArrayList<RecipeHolder<GunSmithTableRecipe>>();
        var modernAmmo = new HashSet<ResourceLocation>();
        for (var holder : manager.getRecipes().stream().sorted(Comparator.comparing(RecipeHolder::id)).toList()) {
            if (!(holder.value() instanceof GunSmithTableRecipe recipe)) continue;
            result.add(new RecipeHolder<>(holder.id(), recipe));
            var api = IAmmo.getIAmmoOrNull(recipe.getOutput());
            if (api != null) modernAmmo.add(api.getAmmoId(recipe.getOutput()));
        }
        if (server == null || !SMAServerConfig.LEGACY_RELOADING_SOURCES.get()) return List.copyOf(result);
        var resources = server.getResourceManager();
        for (var entry : resources.listResources("recipes", id -> id.getPath().endsWith(".json")).entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            var location = entry.getKey();
            String path = location.getPath().substring("recipes/".length());
            // A modern resource, including a conditional/disabled one, has priority over its old counterpart.
            if (resources.getResource(ResourceLocation.fromNamespaceAndPath(location.getNamespace(), "recipe/" + path)).isPresent()) continue;
            try (var reader = entry.getValue().openAsReader()) {
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                if (!json.has("type") || !json.get("type").getAsString().equals("tacz:gun_smith_table_crafting")
                        || !keys(json, Set.of("type", "materials", "result"))) continue;
                var output = json.getAsJsonObject("result");
                if (!output.has("type") || !output.get("type").getAsString().equals("ammo")
                        || !keys(output, Set.of("type", "id", "count", "group"))) continue;
                var ammo = ResourceLocation.parse(output.get("id").getAsString());
                if (modernAmmo.contains(ammo) || !euphy.upo.sentrymechanicalarm.util.SentryCasingProfiles.enabled(ammo)) continue;
                boolean supported = true;
                for (var raw : json.getAsJsonArray("materials")) {
                    var material = raw.getAsJsonObject();
                    if (!keys(material, Set.of("item", "count"))) { supported = false; break; }
                    var ingredient = material.getAsJsonObject("item");
                    if (ingredient.size() != 1 || (!ingredient.has("item") && !ingredient.has("tag"))) { supported = false; break; }
                    if (ingredient.has("tag")) {
                        String tag = ingredient.get("tag").getAsString();
                        if (tag.startsWith("forge:")) tag = "c:" + tag.substring(6);
                        if (tag.equals("c:gunpowder")) tag = "c:gunpowders";
                        ingredient.addProperty("tag", tag);
                    }
                }
                if (!supported) continue;
                var recipe = GunSmithTableRecipe.CODEC.codec().parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), json).getOrThrow();
                recipe.init(server.registryAccess());
                result.add(new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath(location.getNamespace(), "legacy_materials/" + path.substring(0, path.length() - 5)), recipe));
            } catch (RuntimeException | java.io.IOException error) {
                SentryMechanicalArm.LOGGER.debug("Unsupported legacy material source {}: {}", location, error.toString());
            }
        }
        return List.copyOf(result);
    }
}
