package euphy.upo.sentrymechanicalarm.recipe;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipeBuilder;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import euphy.upo.sentrymechanicalarm.*;
import euphy.upo.sentrymechanicalarm.registry.GunParts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Shared industrial components and per-gun assembly; native gun outputs retain all components. */
public final class GunManufacturingRecipes {
    public static final String PREFIX = "gun_manufacturing/";
    private record Plan(Ingredient input, List<Ingredient> materials, ItemStack output) {}
    public static boolean enabled() { return SMAServerConfig.GUN_MANUFACTURING.get(); }
    private static Ingredient of(Item item) { return Ingredient.of(item); }
    private static Ingredient part(String name) { return of(GunParts.item(name)); }
    private static Plan plan(Ingredient input, Item output, int count, Ingredient... additions) {
        return new Plan(input, List.of(additions), new ItemStack(output, count));
    }
    private static Map<String, Plan> defaults() {
        var p = new LinkedHashMap<String, Plan>();
        // Every input consumes one item; material lists add to, rather than repeat, that cost.
        p.put("barrel_blank", plan(of(Items.IRON_INGOT), GunParts.item("barrel_blank"), 1, of(Items.IRON_INGOT)));
        p.put("receiver_blank", plan(of(AllItems.IRON_SHEET.get()), GunParts.item("receiver_blank"), 1, of(AllItems.ANDESITE_ALLOY.get()), of(AllItems.IRON_SHEET.get())));
        p.put("bolt_blank", plan(of(AllItems.ANDESITE_ALLOY.get()), GunParts.item("bolt_blank"), 1, of(Items.IRON_INGOT), of(AllItems.BRASS_NUGGET.get())));
        p.put("gun_spring", plan(of(AllItems.IRON_SHEET.get()), GunParts.item("gun_spring"), 4));
        p.put("gun_fastener", plan(of(Items.IRON_NUGGET), GunParts.item("gun_fastener"), 4));
        p.put("grip_blank", plan(Ingredient.of(ItemTags.PLANKS), GunParts.item("grip_blank"), 2));
        p.put("feed_part", plan(of(AllItems.IRON_SHEET.get()), GunParts.item("feed_part"), 2, part("gun_fastener")));
        p.put("conductive_part", plan(of(AllItems.COPPER_SHEET.get()), GunParts.item("conductive_part"), 2, of(Items.REDSTONE)));
        p.put("barrel_component", plan(part("barrel_blank"), GunParts.item("barrel_component"), 1, part("gun_fastener"), of(AllItems.IRON_SHEET.get())));
        p.put("receiver_component", plan(part("receiver_blank"), GunParts.item("receiver_component"), 1, part("gun_fastener"), part("gun_fastener"), of(Items.IRON_INGOT)));
        p.put("action_component", plan(part("bolt_blank"), GunParts.item("action_component"), 1, part("gun_spring"), part("gun_fastener"), of(AllItems.BRASS_NUGGET.get())));
        p.put("grip_component", plan(part("grip_blank"), GunParts.item("grip_component"), 1, part("gun_fastener")));
        p.put("feed_component", plan(part("feed_part"), GunParts.item("feed_component"), 1, part("gun_spring"), part("gun_fastener")));
        p.put("control_component", plan(part("conductive_part"), GunParts.item("control_component"), 1, of(AllItems.BRASS_INGOT.get()), of(Items.REDSTONE)));
        return p;
    }
    public static List<RecipeHolder<?>> build(RecipeManager manager) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || !enabled()) return List.of();
        var result = new ArrayList<RecipeHolder<?>>();
        for (var entry : defaults().entrySet()) {
            var canonical = ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", PREFIX + "parts/" + entry.getKey());
            if (ManufacturingRules.nativeRecipePresent(manager, server, canonical)) continue;
            var resource = ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", "gun_parts/" + entry.getKey() + ".json");
            try {
                Plan plan = entry.getValue();
                var override = read(server, resource);
                if (override != null) {
                    ManufacturingRules.requireKeys(override, Set.of("enabled", "input", "materials", "output_count"));
                    if (!ManufacturingRules.enabled(override)) continue;
                    int count = override.has("output_count") ? integer(override.get("output_count"), 1, 64) : plan.output().getCount();
                    plan = new Plan(override.has("input") ? ingredient(server, override.get("input")) : plan.input(),
                            override.has("materials") ? materials(server, override.getAsJsonArray("materials")) : plan.materials(),
                            new ItemStack(GunParts.item(entry.getKey()), count));
                }
                result.add(sequence(server, canonical, plan));
            } catch (Exception e) { reject(resource, e); }
        }
        var sources = new LinkedHashMap<ResourceLocation, GunSmithTableRecipe>();
        for (var holder : manager.getRecipes().stream().sorted(Comparator.comparing(RecipeHolder::id)).toList())
            if (holder.value() instanceof GunSmithTableRecipe recipe) {
                var api = IGun.getIGunOrNull(recipe.getOutput());
                if (api != null && recipe.getOutput().getCount() == 1) sources.putIfAbsent(api.getGunId(recipe.getOutput()), recipe);
            }
        for (var entry : TimelessAPI.getAllCommonGunIndex().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            var gun = entry.getKey();
            var canonical = ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", PREFIX + "guns/" + gun.getNamespace() + "/" + gun.getPath());
            if (ManufacturingRules.nativeRecipePresent(manager, server, canonical)) continue;
            var resource = ResourceLocation.fromNamespaceAndPath(gun.getNamespace(), "sentry_guns/" + gun.getPath() + ".json");
            try {
                var override = read(server, resource);
                if (override == null && (!SMAServerConfig.AUTO_GUN_MANUFACTURING.get() || !sources.containsKey(gun))) continue;
                if (override != null) {
                    ManufacturingRules.requireKeys(override, Set.of("enabled", "input", "materials", "extra_materials", "output"));
                    if (!ManufacturingRules.enabled(override)) continue;
                }
                var original = sources.get(gun);
                ItemStack output = original == null ? GunItemBuilder.create().setId(gun).build() : original.getOutput().copy();
                if (override != null && override.has("output")) output = ItemStack.CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), override.get("output")).getOrThrow();
                var api = IGun.getIGunOrNull(output);
                if (api == null || !gun.equals(api.getGunId(output)) || output.getCount() != 1)
                    throw new IllegalArgumentException("Output must be one gun matching the rule ID");
                var costs = new ArrayList<Ingredient>();
                costs.add(part("barrel_component")); costs.add(part("action_component"));
                costs.add(part("grip_component")); costs.add(part("feed_component"));
                String type = entry.getValue().getType().toLowerCase(Locale.ROOT);
                switch (type) {
                    case "pistol", "smg", "shotgun" -> {}
                    case "rifle" -> costs.add(of(AllItems.BRASS_INGOT.get()));
                    case "sniper" -> { costs.add(of(AllItems.PRECISION_MECHANISM.get())); costs.add(part("barrel_component")); }
                    case "mg" -> { costs.add(part("feed_component")); costs.add(of(AllItems.PRECISION_MECHANISM.get())); }
                    default -> { if (override == null) continue; }
                }
                // Keep special gunpack ingredients as authored, with their component-aware Ingredient.
                if (original != null) for (var input : original.getInputs()) {
                    var candidates = input.getIngredient().getItems();
                    if (candidates.length == 0) throw new IllegalArgumentException("Unresolved native material");
                    boolean ordinary = Arrays.stream(candidates).allMatch(stack -> !stack.isEmpty()
                            && (AmmoMetalCosts.isMetal(stack.getItem()) || stack.is(Items.LAPIS_LAZULI)
                            || stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS))
                            && stack.getComponentsPatch().isEmpty());
                    if (!ordinary) repeat(costs, input.getIngredient(), input.getCount());
                }
                Ingredient start = part("receiver_component");
                if (override != null) {
                    if (override.has("input")) start = ingredient(server, override.get("input"));
                    if (override.has("materials")) costs = new ArrayList<>(materials(server, override.getAsJsonArray("materials")));
                    if (override.has("extra_materials")) costs.addAll(materials(server, override.getAsJsonArray("extra_materials")));
                }
                result.add(sequence(server, canonical, new Plan(start, costs, output)));
            } catch (Exception e) { reject(resource, e); }
        }
        SentryMechanicalArm.LOGGER.info("Gun manufacturing: {} part and firearm sequences", result.size());
        return List.copyOf(result);
    }
    private static void reject(ResourceLocation resource, Exception error) {
        SentryMechanicalArm.LOGGER.warn("Invalid gun manufacturing rule {}: {}; no automatic fallback", resource, error.toString());
    }
    private static JsonObject read(MinecraftServer server, ResourceLocation id) throws Exception {
        var resource = server.getResourceManager().getResource(id);
        if (resource.isEmpty()) return null;
        try (var reader = resource.get().openAsReader()) { return JsonParser.parseReader(reader).getAsJsonObject(); }
    }
    private static Ingredient ingredient(MinecraftServer server, JsonElement json) {
        var value = Ingredient.CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), json).getOrThrow();
        if (value.test(ItemStack.EMPTY) || value.getItems().length == 0) throw new IllegalArgumentException("Ingredient is empty or unresolved");
        return value;
    }
    private static int integer(JsonElement value, int min, int max) {
        int count = value.getAsBigDecimal().intValueExact();
        if (count < min || count > max) throw new IllegalArgumentException("Count out of range: " + count);
        return count;
    }
    private static List<Ingredient> materials(MinecraftServer server, JsonArray values) {
        var result = new ArrayList<Ingredient>();
        for (var value : values) {
            var row = value.getAsJsonObject(); ManufacturingRules.requireKeys(row, Set.of("ingredient", "count"));
            repeat(result, ingredient(server, row.get("ingredient")), row.has("count") ? integer(row.get("count"), 1, 1024) : 1);
        }
        return result;
    }
    private static void repeat(List<Ingredient> list, Ingredient ingredient, int count) {
        if (count < 1 || count > 1024 || list.size() + count > 1024) throw new IllegalArgumentException("Too many material units");
        for (int n = 0; n < count; n++) list.add(ingredient);
    }
    private static JsonElement canonical(JsonElement value) {
        if (value.isJsonObject()) { var result = new JsonObject(); value.getAsJsonObject().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).forEach(entry -> result.add(entry.getKey(), canonical(entry.getValue()))); return result; }
        if (value.isJsonArray()) { var result = new JsonArray(); for (var item : value.getAsJsonArray()) result.add(canonical(item)); return result; }
        return value;
    }
    private static RecipeHolder<?> sequence(MinecraftServer server, ResourceLocation canonical, Plan plan) throws Exception {
        var steps = CountedDeployerIngredient.compact(plan.materials());
        if (steps.size() > 15) throw new IllegalArgumentException("Sequence exceeds 16 steps");
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var data = new JsonObject(); data.addProperty("format", "gun-sequence-v1"); data.add("input", Ingredient.CODEC.encodeStart(ops, plan.input()).getOrThrow());
        var costs = new JsonArray(); for (var step : steps) costs.add(Ingredient.CODEC.encodeStart(ops, step).getOrThrow());
        data.add("materials", costs); data.add("output", ItemStack.CODEC.encodeStart(ops, plan.output()).getOrThrow());
        var hash = java.security.MessageDigest.getInstance("SHA-256").digest(canonical(data).toString().getBytes(StandardCharsets.UTF_8));
        var id = ResourceLocation.fromNamespaceAndPath(canonical.getNamespace(), canonical.getPath() + "/v_" + HexFormat.of().formatHex(hash).substring(0, 24));
        var builder = new SequencedAssemblyRecipeBuilder(id).require(plan.input()).transitionTo(GunParts.WORKPIECE.get())
                .addOutput(plan.output(), 1).loops(1);
        // Start on a filtered Deployer when materials are installed. Starting every
        // recipe on a press would compete with Create's ordinary sheet recipes.
        if (steps.isEmpty()) builder.addStep(PressingRecipe::new, step -> step);
        for (var cost : steps) builder.addStep(DeployerApplicationRecipe::new, step -> step.require(cost));
        builder.addStep(PressingRecipe::new, step -> step);
        var holder = builder.build();
        ((euphy.upo.sentrymechanicalarm.mixin.SequencedAssemblyRecipeAccessor)(Object)holder.value())
                .sentry$setTransition(new com.simibubi.create.content.processing.recipe.ProcessingOutput(GunParts.WORKPIECE.get(), 1,
                        net.minecraft.core.component.DataComponentPatch.builder().set(euphy.upo.sentrymechanicalarm.registry.ModDataComponents.GUN_WORK.get(), id).build(), 1));
        // The first subrecipe must also accept raw input, not just a pre-existing transition item.
        holder.value().getSequence().getFirst().getRecipe().getIngredients().set(0, Ingredient.of(
                java.util.stream.Stream.concat(Arrays.stream(plan.input().getItems()), java.util.stream.Stream.of(new ItemStack(GunParts.WORKPIECE.get())))));
        return holder;
    }
}
