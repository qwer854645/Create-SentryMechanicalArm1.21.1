package euphy.upo.sentrymechanicalarm.recipe;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipeBuilder;
import euphy.upo.sentrymechanicalarm.*;
import euphy.upo.sentrymechanicalarm.registry.GunParts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Shared component sequences and entry point for native mechanical gun assembly. */
public final class GunManufacturingRecipes {
    public static final String PREFIX = "gun_manufacturing/";
    private enum Operation { DEPLOYING, PRESSING, CUTTING }
    private record Step(Operation operation, Ingredient material) {}
    private record Plan(Ingredient input, List<Step> steps, ItemStack output) {}
    public static boolean enabled() { return SMAServerConfig.GUN_MANUFACTURING.get(); }
    private static Ingredient of(Item item) { return Ingredient.of(item); }
    private static Ingredient part(String name) { return of(GunParts.item(name)); }
    private static Plan plan(Ingredient input, Item output, int count, Ingredient... additions) {
        return new Plan(input, assemblySteps(List.of(additions)), new ItemStack(output, count));
    }
    private static Step deploy(Ingredient material) { return new Step(Operation.DEPLOYING, material); }
    private static Step press() { return new Step(Operation.PRESSING, null); }
    private static Step cut() { return new Step(Operation.CUTTING, null); }
    private static List<Step> assemblySteps(List<Ingredient> materials) {
        var steps = new ArrayList<Step>();
        for (var cost : CountedDeployerIngredient.compact(materials)) steps.add(deploy(cost));
        // One forming step is enough for routes without installed materials.
        steps.add(press());
        return List.copyOf(steps);
    }
    private static Map<String, Plan> defaults() {
        var p = new LinkedHashMap<String, Plan>();
        // Every input consumes one item; material lists add to, rather than repeat, that cost.
        p.put("barrel_blank", plan(of(Items.IRON_INGOT), GunParts.item("barrel_blank"), 1, of(Items.IRON_INGOT)));
        p.put("receiver_blank", plan(of(AllItems.IRON_SHEET.get()), GunParts.item("receiver_blank"), 1, of(AllItems.ANDESITE_ALLOY.get()), of(AllItems.IRON_SHEET.get())));
        p.put("bolt_blank", plan(of(AllItems.ANDESITE_ALLOY.get()), GunParts.item("bolt_blank"), 1, of(Items.IRON_INGOT), of(AllItems.BRASS_NUGGET.get())));
        p.put("gun_spring", new Plan(of(AllItems.IRON_SHEET.get()), List.of(cut(), press()), new ItemStack(GunParts.item("gun_spring"), 4)));
        p.put("gun_fastener", plan(of(Items.IRON_NUGGET), GunParts.item("gun_fastener"), 4));
        p.put("grip_blank", plan(Ingredient.of(ItemTags.PLANKS), GunParts.item("grip_blank"), 2));
        p.put("feed_part", plan(of(AllItems.IRON_SHEET.get()), GunParts.item("feed_part"), 2, part("gun_fastener")));
        p.put("conductive_part", plan(of(AllItems.COPPER_SHEET.get()), GunParts.item("conductive_part"), 2, of(Items.REDSTONE)));
        p.put("trigger_blank", plan(of(AllItems.COPPER_SHEET.get()), GunParts.item("trigger_blank"), 2, of(Items.IRON_NUGGET)));
        p.put("attachment_blank", plan(of(AllItems.BRASS_NUGGET.get()), GunParts.item("attachment_blank"), 1, of(Items.IRON_NUGGET)));
        p.put("optical_blank", new Plan(of(Items.GLASS_PANE), List.of(cut(),press()), new ItemStack(GunParts.item("optical_blank"))));
        p.put("muzzle_blank", plan(of(Items.COPPER_INGOT), GunParts.item("muzzle_blank"), 1, of(AllItems.IRON_SHEET.get())));
        for (String family : GunParts.allFamilies()) for (var tier : GunParts.Tier.values()) {
            String blank = switch (family) {
                case "attachment_base" -> "attachment_blank";
                case "optics" -> "optical_blank";
                case "muzzle" -> "muzzle_blank";
                case "barrel" -> "barrel_blank";
                case "receiver" -> "receiver_blank";
                case "action" -> "bolt_blank";
                case "recoil" -> "gun_spring";
                case "grip" -> "grip_blank";
                case "feed" -> "feed_part";
                case "trigger" -> "trigger_blank";
                default -> "conductive_part";
            };
            var steps = new ArrayList<Step>();
            // Different held items select the tier before any shared operation.
            // Counts alone cannot distinguish Deployer recipe entrances.
            steps.add(deploy(switch (tier) {
                case BASIC -> part("gun_fastener");
                case PRECISION -> of(AllItems.BRASS_SHEET.get());
                case REINFORCED -> of(AllItems.STURDY_SHEET.get());
            }));
            if (tier != GunParts.Tier.BASIC || family.equals("barrel") || family.equals("trigger")) steps.add(cut());
            switch (family) {
                case "attachment_base", "muzzle", "barrel" -> steps.add(deploy(of(AllItems.IRON_SHEET.get())));
                case "optics" -> steps.add(deploy(of(Items.AMETHYST_SHARD)));
                case "receiver" -> steps.add(deploy(of(AllItems.ANDESITE_ALLOY.get())));
                case "action", "feed", "trigger" -> steps.add(deploy(part("gun_spring")));
                case "recoil" -> steps.add(deploy(of(AllItems.IRON_SHEET.get())));
                case "control" -> steps.add(deploy(of(Items.REDSTONE)));
                default -> { /* The wooden grip already supplies its main material. */ }
            }
            if (tier != GunParts.Tier.BASIC) {
                steps.add(deploy(part("gun_fastener")));
                steps.add(press());
                if (family.equals("control")) steps.add(deploy(of(AllItems.ELECTRON_TUBE.get())));
                else if (tier == GunParts.Tier.REINFORCED) steps.add(deploy(of(AllItems.BRASS_INGOT.get())));
                if (tier == GunParts.Tier.REINFORCED) steps.add(cut());
            }
            steps.add(press());
            String name = tier.component(family);
            p.put(name, new Plan(part(blank), List.copyOf(steps), new ItemStack(GunParts.item(name))));
        }
        return p;
    }
    public static List<RecipeHolder<?>> build(RecipeManager manager) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || (!enabled() && !SMAServerConfig.ATTACHMENT_MANUFACTURING.get())) return List.of();
        var result = new ArrayList<RecipeHolder<?>>();
        var partPlans = new LinkedHashMap<ResourceLocation, Plan>();
        var explicitParts = new HashSet<ResourceLocation>();
        for (var entry : defaults().entrySet()) {
            if(GunParts.attachmentPart(entry.getKey())&&!SMAServerConfig.ATTACHMENT_MANUFACTURING.get())continue;
            var canonical = ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", PREFIX + "parts/" + entry.getKey());
            if (ManufacturingRules.nativeRecipePresent(manager, server, canonical)) continue;
            var resource = ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", "gun_parts/" + entry.getKey() + ".json");
            try {
                Plan plan = entry.getValue();
                var override = read(server, resource);
                if (override != null) {
                    ManufacturingRules.requireKeys(override, Set.of("enabled", "input", "materials", "steps", "output_count"));
                    if (!ManufacturingRules.enabled(override)) continue;
                    if (override.has("steps") && override.has("materials")) throw new IllegalArgumentException("Use either steps or materials");
                    int count = override.has("output_count") ? integer(override.get("output_count"), 1, 64) : plan.output().getCount();
                    plan = new Plan(override.has("input") ? ingredient(server, override.get("input")) : plan.input(),
                            override.has("steps") ? steps(server, override.getAsJsonArray("steps")) : override.has("materials") ? assemblySteps(materials(server, override.getAsJsonArray("materials"))) : plan.steps(),
                            new ItemStack(GunParts.item(entry.getKey()), count));
                }
                partPlans.put(canonical, plan);
                if (override != null) explicitParts.add(canonical);
            } catch (Exception e) { reject(resource, e); }
        }
        // Evaluate the full set before adding anything: never let iteration order pick a branch.
        var conflicts = new HashSet<ResourceLocation>();
        var candidates = new ArrayList<>(partPlans.entrySet());
        for (int a = 0; a < candidates.size(); a++) for (int b = a + 1; b < candidates.size(); b++) {
            var left = candidates.get(a); var right = candidates.get(b);
            if (sameEntrance(left.getValue(), right.getValue())) {
                boolean leftExplicit = explicitParts.contains(left.getKey()), rightExplicit = explicitParts.contains(right.getKey());
                if (leftExplicit == rightExplicit) {
                    conflicts.add(left.getKey()); conflicts.add(right.getKey());
                } else conflicts.add(leftExplicit ? right.getKey() : left.getKey());
                SentryMechanicalArm.LOGGER.warn("Conflicting gun part sequence entrances: {} and {}; explicit rules take precedence, equally ranked conflicts are disabled. Change the first operation or its held material.", left.getKey(), right.getKey());
            }
        }
        for (var entry : partPlans.entrySet()) if (!conflicts.contains(entry.getKey())) {
            try { result.add(sequence(server, entry.getKey(), entry.getValue())); }
            catch (Exception e) { reject(entry.getKey(), e); }
        }
        if (enabled()) result.addAll(GunAssemblyRecipes.build(manager, server));
        SentryMechanicalArm.LOGGER.info("Gun manufacturing: {} part and selected-firearm sequences", result.size());
        return List.copyOf(result);
    }
    static void reject(ResourceLocation resource, Exception error) {
        SentryMechanicalArm.LOGGER.warn("Invalid gun manufacturing rule {}: {}; no automatic fallback", resource, error.toString());
    }
    static JsonObject read(MinecraftServer server, ResourceLocation id) throws Exception {
        var resource = server.getResourceManager().getResource(id);
        if (resource.isEmpty()) return null;
        try (var reader = resource.get().openAsReader()) { return JsonParser.parseReader(reader).getAsJsonObject(); }
    }
    static Ingredient ingredient(MinecraftServer server, JsonElement json) {
        var value = Ingredient.CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), json).getOrThrow();
        if (value.test(ItemStack.EMPTY) || value.getItems().length == 0) throw new IllegalArgumentException("Ingredient is empty or unresolved");
        return value;
    }
    static int integer(JsonElement value, int min, int max) {
        int count = value.getAsBigDecimal().intValueExact();
        if (count < min || count > max) throw new IllegalArgumentException("Count out of range: " + count);
        return count;
    }
    static GunParts.Tier tier(JsonElement value) {
        return GunParts.Tier.valueOf(value.getAsString().toUpperCase(Locale.ROOT));
    }
    static List<Ingredient> materials(MinecraftServer server, JsonArray values) {
        var result = new ArrayList<Ingredient>();
        for (var value : values) {
            var row = value.getAsJsonObject(); ManufacturingRules.requireKeys(row, Set.of("ingredient", "count"));
            repeat(result, ingredient(server, row.get("ingredient")), row.has("count") ? integer(row.get("count"), 1, 1024) : 1);
        }
        return result;
    }
    private static List<Step> steps(MinecraftServer server, JsonArray values) {
        if (values.isEmpty() || values.size() > 16) throw new IllegalArgumentException("Expected 1..16 steps");
        var result = new ArrayList<Step>();
        for (var value : values) {
            var row = value.getAsJsonObject();
            ManufacturingRules.requireKeys(row, Set.of("type", "ingredient", "count"));
            var operation = Operation.valueOf(row.get("type").getAsString().toUpperCase(Locale.ROOT));
            if (operation == Operation.DEPLOYING) {
                var material = ingredient(server, row.get("ingredient"));
                int count = row.has("count") ? integer(row.get("count"), 1, 64) : 1;
                if (count > 1) {
                    var counted = new CountedDeployerIngredient(material, count);
                    if (counted.getItems().findAny().isEmpty()) throw new IllegalArgumentException("Material cannot be deployed in this quantity");
                    material = counted.toVanilla();
                }
                result.add(deploy(material));
            } else {
                if (row.has("ingredient") || row.has("count")) throw new IllegalArgumentException("Only deploying accepts ingredient/count");
                result.add(new Step(operation, null));
            }
        }
        return List.copyOf(result);
    }
    private static Ingredient uncounted(Ingredient ingredient) {
        var counted = CountedDeployerIngredient.from(ingredient);
        return counted == null ? ingredient : uncounted(counted.ingredient());
    }
    static boolean overlaps(Ingredient left, Ingredient right) {
        var a = uncounted(left); var b = uncounted(right);
        return Arrays.stream(a.getItems()).anyMatch(b::test) || Arrays.stream(b.getItems()).anyMatch(a::test);
    }
    private static boolean sameEntrance(Plan left, Plan right) {
        if (!overlaps(left.input(), right.input())) return false;
        var a = left.steps().getFirst(); var b = right.steps().getFirst();
        return a.operation() == b.operation() && (a.operation() != Operation.DEPLOYING || overlaps(a.material(), b.material()));
    }
    static void repeat(List<Ingredient> list, Ingredient ingredient, int count) {
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
        if (plan.steps().isEmpty() || plan.steps().size() > 16) throw new IllegalArgumentException("Expected 1..16 steps");
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var data = new JsonObject(); data.addProperty("format", "gun-sequence-v2"); data.add("input", Ingredient.CODEC.encodeStart(ops, plan.input()).getOrThrow());
        var operations = new JsonArray();
        for (var step : plan.steps()) {
            var row = new JsonObject(); row.addProperty("type", step.operation().name());
            if (step.material() != null) row.add("ingredient", Ingredient.CODEC.encodeStart(ops, step.material()).getOrThrow());
            operations.add(row);
        }
        data.add("steps", operations); data.add("output", ItemStack.CODEC.encodeStart(ops, plan.output()).getOrThrow());
        var hash = java.security.MessageDigest.getInstance("SHA-256").digest(canonical(data).toString().getBytes(StandardCharsets.UTF_8));
        var id = ResourceLocation.fromNamespaceAndPath(canonical.getNamespace(), canonical.getPath() + "/v_" + HexFormat.of().formatHex(hash).substring(0, 24));
        var builder = new SequencedAssemblyRecipeBuilder(id).require(plan.input()).transitionTo(GunParts.WORKPIECE.get())
                .addOutput(plan.output(), 1).loops(1);
        for (var operation : plan.steps()) switch (operation.operation()) {
            case DEPLOYING -> builder.addStep(DeployerApplicationRecipe::new, step -> step.require(operation.material()));
            case PRESSING -> builder.addStep(PressingRecipe::new, step -> step);
            case CUTTING -> builder.addStep(CuttingRecipe::new, step -> step.duration(60));
        }
        var holder = builder.build();
        ((euphy.upo.sentrymechanicalarm.mixin.SequencedAssemblyRecipeAccessor)(Object)holder.value())
                .sentry$setTransition(new com.simibubi.create.content.processing.recipe.ProcessingOutput(GunParts.WORKPIECE.get(), 1,
                        net.minecraft.core.component.DataComponentPatch.builder().set(euphy.upo.sentrymechanicalarm.registry.ModDataComponents.GUN_WORK.get(), id).build(), 1));
        // The first subrecipe must also accept raw input, not just a pre-existing transition item.
        holder.value().getSequence().getFirst().getRecipe().getIngredients().set(0, Ingredient.of(
                java.util.stream.Stream.concat(Arrays.stream(plan.input().getItems()), java.util.stream.Stream.of(new ItemStack(GunParts.WORKPIECE.get())))));
        return holder;
    }
    static RecipeHolder<?> gunSequence(MinecraftServer server, ResourceLocation canonical, Ingredient receiver,
            List<Ingredient> costs, ItemStack output, JsonArray explicitSteps) throws Exception {
        List<Step> operations;
        if (explicitSteps != null) operations = steps(server, explicitSteps);
        else {
            var list = new ArrayList<Step>(); int installed = 0;
            for (var material : CountedDeployerIngredient.compact(costs)) {
                list.add(deploy(material));
                if (++installed == 3) list.add(press());
            }
            list.add(press()); operations = List.copyOf(list);
        }
        var holder = sequence(server, canonical, new Plan(receiver, operations, output));
        // The fingerprint includes receiver, output components and the complete operations.
        // Only a receiver explicitly prepared for this exact recipe can start it.
        ((euphy.upo.sentrymechanicalarm.mixin.SequencedAssemblyRecipeAccessor)(Object)holder.value())
                .sentry$setIngredient(new GunAssemblyIngredient(holder.id(), receiver).toVanilla());
        return holder;
    }
}
