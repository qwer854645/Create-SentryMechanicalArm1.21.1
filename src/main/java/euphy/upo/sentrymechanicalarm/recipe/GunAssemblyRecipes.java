package euphy.upo.sentrymechanicalarm.recipe;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import euphy.upo.sentrymechanicalarm.SMAServerConfig;
import euphy.upo.sentrymechanicalarm.content.ReceiverComponentItem;
import euphy.upo.sentrymechanicalarm.registry.GunParts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.*;
import static euphy.upo.sentrymechanicalarm.recipe.GunManufacturingRecipes.*;

public final class GunAssemblyRecipes {
    public record Choice(ResourceLocation id, ResourceLocation gun, GunAssemblyIngredient input,
                         ItemStack output, SequencedAssemblyRecipe recipe) {}
    public static List<Choice> choices(Level level) {
        var result = new ArrayList<Choice>();
        for (var holder : level.getRecipeManager().getRecipes())
            if (holder.value() instanceof SequencedAssemblyRecipe sequence
                    && sequence.getIngredient().getCustomIngredient() instanceof GunAssemblyIngredient input
                    && input.recipe().equals(holder.id()) && validReceiver(input.receiver())) {
                var output = sequence.getResultItem(level.registryAccess()); var api = IGun.getIGunOrNull(output);
                var attachment=com.tacz.guns.api.item.IAttachment.getIAttachmentOrNull(output);
                boolean base=Arrays.stream(input.receiver().getItems()).allMatch(s->s.getItem() instanceof euphy.upo.sentrymechanicalarm.content.AttachmentBaseItem);
                if (api != null && !base && Arrays.stream(input.receiver().getItems()).noneMatch(s->s.getItem() instanceof euphy.upo.sentrymechanicalarm.content.AttachmentBaseItem))
                    result.add(new Choice(holder.id(), api.getGunId(output), input, output.copy(), sequence));
                else if (attachment!=null && base) result.add(new Choice(holder.id(),attachment.getAttachmentId(output),input,output.copy(),sequence));
            }
        result.sort(Comparator.comparing(Choice::gun)); return List.copyOf(result);
    }
    /** One target selection consumes exactly one receiver, including native data-pack entrances. */
    static boolean validReceiver(Ingredient receiver) {
        var samples=receiver.getItems();
        return samples.length>0 && !receiver.test(ItemStack.EMPTY) && Arrays.stream(samples).allMatch(stack->
                stack.getItem() instanceof ReceiverComponentItem && stack.getCount()==1 && receiver.test(stack.copyWithCount(1)));
    }
    public static List<RecipeHolder<?>> build(RecipeManager manager, MinecraftServer server) {
        var sources = new LinkedHashMap<ResourceLocation, GunSmithTableRecipe>();
        for (var holder : manager.getRecipes().stream().sorted(Comparator.comparing(RecipeHolder::id)).toList())
            if (holder.value() instanceof GunSmithTableRecipe recipe) {
                var api = IGun.getIGunOrNull(recipe.getOutput());
                if (api != null && recipe.getOutput().getCount() == 1) sources.putIfAbsent(api.getGunId(recipe.getOutput()), recipe);
            }
        var result = new ArrayList<RecipeHolder<?>>();
        for (var entry : TimelessAPI.getAllCommonGunIndex().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            var gun = entry.getKey();
            var canonical = ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", PREFIX + "guns/" + gun.getNamespace() + "/" + gun.getPath());
            if (ManufacturingRules.nativeRecipePresent(manager, server, canonical)) continue;
            var resource = ResourceLocation.fromNamespaceAndPath(gun.getNamespace(), "sentry_guns/" + gun.getPath() + ".json");
            try {
                var rule = read(server, resource);
                if (rule == null && (!SMAServerConfig.AUTO_GUN_MANUFACTURING.get() || !sources.containsKey(gun))) continue;
                if (rule != null) {
                    ManufacturingRules.requireKeys(rule, Set.of("enabled", "tier", "component_tiers", "component_counts", "input", "materials", "extra_materials", "steps", "output"));
                    if (!ManufacturingRules.enabled(rule)) continue;
                    if (rule.has("steps") && (rule.has("materials") || rule.has("extra_materials")))
                        throw new IllegalArgumentException("steps replace all operations; cannot combine with materials/extra_materials");
                    if (rule.has("component_counts") && (rule.has("materials") || rule.has("steps")))
                        throw new IllegalArgumentException("component_counts cannot combine with complete materials/steps replacements");
                }
                var original = sources.get(gun);
                var output = original == null ? GunItemBuilder.create().setId(gun).build() : original.getOutput().copy();
                if (rule != null && rule.has("output")) output = ItemStack.CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), rule.get("output")).getOrThrow();
                var api = IGun.getIGunOrNull(output);
                if (api == null || !gun.equals(api.getGunId(output)) || output.getCount() != 1) throw new IllegalArgumentException("Output must be one matching gun");
                String type = entry.getValue().getType().toLowerCase(Locale.ROOT);
                if (!Set.of("pistol", "smg", "shotgun", "rifle", "sniper", "mg").contains(type) && rule == null) continue;
                var requirements = GunComponentRequirements.derive(type, entry.getValue().getGunData());
                var grades = new HashMap<>(requirements.tiers());
                var counts = new HashMap<>(requirements.counts());
                // Explicit settings are applied after every heuristic; no later automatic upgrades.
                if (rule != null && rule.has("tier")) {
                    var grade = tier(rule.get("tier"));
                    for (String family : GunParts.FAMILIES) grades.put(family, grade);
                }
                if (rule != null && rule.has("component_tiers")) {
                    var custom = rule.getAsJsonObject("component_tiers");
                    ManufacturingRules.requireKeys(custom, Set.copyOf(GunParts.FAMILIES));
                    for (var value : custom.entrySet()) grades.put(value.getKey(), tier(value.getValue()));
                }
                if (rule != null && rule.has("component_counts")) {
                    var custom = rule.getAsJsonObject("component_counts");
                    ManufacturingRules.requireKeys(custom, Set.copyOf(GunParts.FAMILIES));
                    for (var value : custom.entrySet()) counts.put(value.getKey(), value.getKey().equals("receiver")
                            ? integer(value.getValue(), 1, 1) : integer(value.getValue(), 0, 64));
                }
                var costs = new ArrayList<Ingredient>();
                for (String family : List.of("barrel", "action", "recoil", "trigger", "feed", "grip", "control"))
                    if (counts.get(family) > 0)
                        repeat(costs, Ingredient.of(GunParts.item(grades.get(family).component(family))), counts.get(family));
                if (original != null && (rule == null || (!rule.has("materials") && !rule.has("steps"))))
                    for (var input : original.getInputs()) {
                        var candidates = input.getIngredient().getItems();
                        if (candidates.length == 0) throw new IllegalArgumentException("Unresolved native material");
                        boolean ordinary = Arrays.stream(candidates).allMatch(s -> !s.isEmpty()
                                && (AmmoMetalCosts.isMetal(s.getItem()) || s.is(Items.LAPIS_LAZULI) || s.is(ItemTags.LOGS) || s.is(ItemTags.PLANKS))
                                && s.getComponentsPatch().isEmpty());
                        if (!ordinary) repeat(costs, input.getIngredient(), input.getCount());
                    }
                Ingredient receiver = Ingredient.of(GunParts.item(grades.get("receiver").component("receiver")));
                if (rule != null) {
                    if (rule.has("input")) receiver = ingredient(server, rule.get("input"));
                    if (rule.has("materials")) costs = new ArrayList<>(materials(server, rule.getAsJsonArray("materials")));
                    if (rule.has("extra_materials")) costs.addAll(materials(server, rule.getAsJsonArray("extra_materials")));
                }
                if (!validReceiver(receiver) || Arrays.stream(receiver.getItems()).anyMatch(s->s.getItem() instanceof euphy.upo.sentrymechanicalarm.content.AttachmentBaseItem)) throw new IllegalArgumentException("input must match single receiver components");
                var holder = gunSequence(server, canonical, receiver, costs, output, rule != null && rule.has("steps") ? rule.getAsJsonArray("steps") : null);
                var sequence = (SequencedAssemblyRecipe)holder.value();
                var start = (GunAssemblyIngredient)sequence.getIngredient().getCustomIngredient();
                var copying = GunAssemblyCopying.recipe(new Choice(holder.id(), gun, start, output, sequence), ItemStack.EMPTY);
                result.add(holder); result.add(copying);
            } catch (Exception e) { reject(resource, e); }
        }
        return List.copyOf(result);
    }
}
