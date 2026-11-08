package euphy.upo.sentrymechanicalarm.recipe;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipeBuilder;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import euphy.upo.sentrymechanicalarm.SMAServerConfig;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.*;

@EventBusSubscriber(modid = SentryMechanicalArm.MODID)
public class DynamicRecipeManager {

    private static Item taczAmmoItem;
    // Track actual holders, so a data-pack replacement with the same id remains authoritative.
    private static final Map<RecipeManager, Map<ResourceLocation, RecipeHolder<?>>> GENERATED = new WeakHashMap<>();

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        euphy.upo.sentrymechanicalarm.util.SentryCasingProfiles.rebuild(event.getServer());
        rebuild(event.getServer().getRecipeManager());
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void onDatapackSync(net.neoforged.neoforge.event.OnDatapackSyncEvent event) {
        // Fired after TaCZ has initialized its recipes/tags and before vanilla recipe sync.
        // A single-player login must not invalidate active shooters or regenerate recipes.
        if (event.getPlayer() != null) return;
        euphy.upo.sentrymechanicalarm.util.SentryCasingProfiles.rebuild(event.getPlayerList().getServer());
        rebuild(event.getPlayerList().getServer().getRecipeManager());
        euphy.upo.sentrymechanicalarm.util.SentryFakePlayer.invalidateGunData();
    }

    @SubscribeEvent
    public static void onStop(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        GENERATED.clear();
    }

    public static void rebuild(RecipeManager recipeManager) {
        Map<ResourceLocation, RecipeHolder<?>> previous = GENERATED.getOrDefault(recipeManager, Map.of());
        List<RecipeHolder<?>> base = recipeManager.getRecipes().stream()
                .filter(holder -> previous.get(holder.id()) != holder).toList();

        taczAmmoItem = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("tacz", "ammo"));
        if (taczAmmoItem == Items.AIR) {
            SentryMechanicalArm.LOGGER.error("Failed to find TacZ ammo item, aborting recipe injection");
            return;
        }

        if (!SMAServerConfig.ENABLE_AMMO_CRAFTING.get() || !SMAServerConfig.ENABLE_DYNAMIC_RECIPES.get()) {
            recipeManager.replaceRecipes(base);
            GENERATED.remove(recipeManager);
            SentryMechanicalArm.LOGGER.info("Dynamic recipes disabled by config");
            return;
        }

        List<RecipeHolder<?>> allNewRecipes = new ArrayList<>();

        allNewRecipes.addAll(injectCuttingRecipes(recipeManager));
        allNewRecipes.addAll(injectSequencedAssemblyRecipes(recipeManager));
        allNewRecipes.addAll(injectPressingRecipes(recipeManager));
        allNewRecipes.addAll(ReloadingRecipes.build(recipeManager));

        LinkedHashMap<ResourceLocation, RecipeHolder<?>> combined = new LinkedHashMap<>();
        base.forEach(holder -> combined.put(holder.id(), holder));
        Map<ResourceLocation, RecipeHolder<?>> owned = new HashMap<>();
        for (var holder : allNewRecipes) {
            if (combined.putIfAbsent(holder.id(), holder) == null) owned.put(holder.id(), holder);
        }
        recipeManager.replaceRecipes(combined.values());
        GENERATED.put(recipeManager, owned);
        SentryMechanicalArm.LOGGER.info("Rebuilt {} dynamic ammo recipes ({} data-pack overrides)",
                owned.size(), allNewRecipes.size() - owned.size());
    }

    /** Preserve existing ids; resolve legacy escaping collisions in sorted ammo-id order. */
    private static ResourceLocation uniqueRecipeId(String category, String safePath,
                                                   Set<ResourceLocation> used, int index) {
        String base = category + "/" + safePath;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(SentryMechanicalArm.MODID, base);
        int attempt = 0;
        while (!used.add(id)) {
            attempt++;
            String suffix = attempt == 1 ? Integer.toString(index) : index + "_" + attempt;
            id = ResourceLocation.fromNamespaceAndPath(SentryMechanicalArm.MODID, base + "/" + suffix);
            SentryMechanicalArm.LOGGER.warn(
                    "DynamicRecipeManager: ammo recipe path collision on {}, keeping entry as {}",
                    base, id);
        }
        return id;
    }

    private static String encodeIdPart(String part) {
        return part.replace("_", "__").replace("/", "_");
    }

    private static String uniquePath(ResourceLocation ammoId) {
        // Retain legacy ids for in-progress assemblies; uniqueRecipeId handles collisions.
        return encodeIdPart(ammoId.getNamespace()) + "_" + encodeIdPart(ammoId.getPath());
    }

    public static List<RecipeHolder<?>> injectCuttingRecipes(RecipeManager recipeManager) {
        var ammoEntries = TimelessAPI.getAllCommonAmmoIndex().stream()
                .sorted(Map.Entry.comparingByKey()).toList();
        if (ammoEntries.isEmpty()) return List.of();

        List<RecipeHolder<?>> newRecipes = new ArrayList<>();
        Set<ResourceLocation> usedRecipeIds = new LinkedHashSet<>();
        int recipeIndex = 0;
        for (var entry : ammoEntries) {
            ResourceLocation ammoId = entry.getKey();
            String safePath = uniquePath(ammoId);
            ResourceLocation recipeId = uniqueRecipeId(
                    "ammo_cutting", safePath, usedRecipeIds, recipeIndex++);

            ItemStack output = new ItemStack(SentryRegistry.UNFINISHED_AMMO.get());
            CompoundTag tag = new CompoundTag();
            tag.putString("AmmoId", ammoId.toString());
            tag.putInt("CopperSheets", 0);
            tag.putBoolean("GunpowderAdded", false);
            output.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

            CuttingRecipe recipe = new StandardProcessingRecipe.Builder<>(CuttingRecipe::new, recipeId)
                    .withItemIngredients(Ingredient.of(AllItems.COPPER_SHEET.get()))
                    .withSingleItemOutput(output)
                    .duration(50)
                    .build();

            newRecipes.add(new RecipeHolder<>(recipeId, recipe));
        }

        return newRecipes;
    }

    public static List<RecipeHolder<?>> injectSequencedAssemblyRecipes(RecipeManager recipeManager) {
        var ammoEntries = TimelessAPI.getAllCommonAmmoIndex().stream()
                .sorted(Map.Entry.comparingByKey()).toList();
        if (ammoEntries.isEmpty()) {
            SentryMechanicalArm.LOGGER.warn("No ammo entries found from TimelessAPI");
            return List.of();
        }

        SentryMechanicalArm.LOGGER.info("Building {} sequenced assembly recipes", ammoEntries.size());

        List<RecipeHolder<?>> newRecipes = new ArrayList<>();
        Set<ResourceLocation> usedRecipeIds = new LinkedHashSet<>();
        int recipeIndex = 0;
        for (var entry : ammoEntries) {
            ResourceLocation ammoId = entry.getKey();
            AmmoRecipeConfig.Config config = getOrCreateConfig(ammoId, recipeManager);
            String safePath = uniquePath(ammoId);
            ResourceLocation recipeId = uniqueRecipeId(
                    "ammo_assembly", safePath, usedRecipeIds, recipeIndex++);

            ItemStack outputAmmo = new ItemStack(taczAmmoItem, config.outputCount());
            CompoundTag outputTag = new CompoundTag();
            outputTag.putString("AmmoId", ammoId.toString());
            outputAmmo.set(DataComponents.CUSTOM_DATA, CustomData.of(outputTag));

            SequencedAssemblyRecipeBuilder builder =
                    new SequencedAssemblyRecipeBuilder(recipeId);
            builder.require(new UnfinishedAmmoIngredient(ammoId, 0, false).toVanilla())
                    .transitionTo(SentryRegistry.UNFINISHED_AMMO.get())
                    .addOutput(outputAmmo, 1)
                    .loops(1);

            for (Ingredient stepItem : config.assemblySteps()) {
                builder.addStep(DeployerApplicationRecipe::new, rb -> rb.require(stepItem));
            }

            builder.addStep(PressingRecipe::new, rb -> rb);

            newRecipes.add(builder.build());
        }

        return newRecipes;
    }

    public static List<RecipeHolder<?>> injectPressingRecipes(RecipeManager recipeManager) {
        var ammoEntries = TimelessAPI.getAllCommonAmmoIndex().stream()
                .sorted(Map.Entry.comparingByKey()).toList();
        if (ammoEntries.isEmpty()) return List.of();

        List<RecipeHolder<?>> newRecipes = new ArrayList<>();
        Set<ResourceLocation> usedRecipeIds = new LinkedHashSet<>();
        int recipeIndex = 0;
        for (var entry : ammoEntries) {
            ResourceLocation ammoId = entry.getKey();
            AmmoRecipeConfig.Config config = getOrCreateConfig(ammoId, recipeManager);
            // The legacy belt path records copper sheets and one gunpowder only.
            // It cannot account for iron, TNT or arbitrary pack materials.
            if (!supportsLegacyPressing(config)) continue;
            String safePath = uniquePath(ammoId);
            ResourceLocation pressRecipeId = uniqueRecipeId(
                    "ammo_pressing", safePath, usedRecipeIds, recipeIndex++);

            long materialSteps = config.assemblySteps().stream()
                    .map(Ingredient::getItems)
                    .filter(items -> items.length > 0 && items[0].getItem() != Items.GUNPOWDER)
                    .count();

            ItemStack outputAmmo = new ItemStack(taczAmmoItem, config.outputCount());
            CompoundTag outputTag = new CompoundTag();
            outputTag.putString("AmmoId", ammoId.toString());
            outputAmmo.set(DataComponents.CUSTOM_DATA, CustomData.of(outputTag));

            PressingRecipe recipe = new StandardProcessingRecipe.Builder<>(PressingRecipe::new, pressRecipeId)
                    .withItemIngredients(new UnfinishedAmmoIngredient(ammoId, (int) materialSteps, true).toVanilla())
                    .withSingleItemOutput(outputAmmo)
                    .duration(100)
                    .build();

            newRecipes.add(new RecipeHolder<>(pressRecipeId, recipe));
        }

        return newRecipes;
    }

    private static AmmoRecipeConfig.Config getOrCreateConfig(ResourceLocation ammoId, RecipeManager recipeManager) {
        if (SMAServerConfig.AUTO_MATCH_FROM_TACZ.get()) {
            GunSmithTableRecipe recipe = findTaCZRecipe(ammoId, recipeManager);
            if (recipe != null) {
                AmmoRecipeConfig.Config fromRecipe = buildConfigFromRecipe(ammoId, recipe);
                if (fromRecipe != null) return fromRecipe;
            }
        }

        AmmoRecipeConfig.Config fromOverride = AmmoRecipeConfig.getOverride(ammoId);
        if (fromOverride != null) return fromOverride;

        return AmmoRecipeConfig.getCategoryDefault(ammoId);
    }

    private static GunSmithTableRecipe findTaCZRecipe(ResourceLocation ammoId, RecipeManager recipeManager) {
        return recipeManager.getRecipes().stream()
                .sorted(Comparator.comparing(holder -> holder.id().toString()))
                .map(RecipeHolder::value).filter(GunSmithTableRecipe.class::isInstance)
                .map(GunSmithTableRecipe.class::cast)
                .filter(recipe -> {
                    ItemStack output = recipe.getOutput();
                    IAmmo ammo = IAmmo.getIAmmoOrNull(output);
                    return ammo != null && ammoId.equals(ammo.getAmmoId(output));
                }).findFirst().orElse(null);
    }

    public static boolean supportsLegacyPressing(AmmoRecipeConfig.Config config) {
        int sheets = 0, powder = 0;
        for (Ingredient ingredient : config.assemblySteps()) {
            ItemStack[] items = ingredient.getItems();
            if (items.length != 1) return false;
            ItemStack item = items[0];
            if (ItemStack.isSameItemSameComponents(item, new ItemStack(AllItems.COPPER_SHEET.get()))) sheets++;
            else if (ItemStack.isSameItemSameComponents(item, new ItemStack(Items.GUNPOWDER))) powder++;
            else return false;
        }
        return sheets > 0 && sheets <= euphy.upo.sentrymechanicalarm.content.UnfinishedAmmoItem.MAX_COPPER_SHEETS && powder == 1;
    }

    private record RawStep(ItemStack item, int count) {
        public RawStep(Item item, int count){
            this(new ItemStack(item), count);
        }
    }

    private static AmmoRecipeConfig.Config buildConfigFromRecipe(ResourceLocation ammoId, GunSmithTableRecipe recipe) {
        try {
            List<RawStep> rawSteps = new ArrayList<>();
            for (GunSmithTableIngredient input : recipe.getInputs()) {
                Ingredient ingredient = input.getIngredient();
                int count = input.getCount();
                ItemStack[] items = ingredient.getItems();
                if (items.length == 0) return null;

                ItemStack rawItem = items[0];
                if (rawItem.getItem() == Items.AIR) continue;

                Item mappedItem = mapToAssemblyItem(rawItem.getItem());
                if(mappedItem != rawItem.getItem()){
                    // block data components to avoid being unable to craft
                    rawSteps.add(new RawStep(mappedItem, count));
                }else{
                    rawSteps.add(new RawStep(rawItem, count));
                }
            }

            int outputCount = recipe.getOutput().getCount();
            if (outputCount <= 0) {
                outputCount = AmmoRecipeConfig.getCategoryDefault(ammoId).outputCount();
            }

            SMAServerConfig.StepScaling mode = SMAServerConfig.STEP_SCALING.get();
            if (mode == SMAServerConfig.StepScaling.GCD) {
                int gcd = outputCount;
                for (RawStep s : rawSteps) gcd = gcd(gcd, s.count());
                if (gcd > 1) {
                    for (int i = 0; i < rawSteps.size(); i++) {
                        RawStep s = rawSteps.get(i);
                        rawSteps.set(i, new RawStep(s.item(), s.count() / gcd));
                    }
                    outputCount /= gcd;
                }
            } else if (mode == SMAServerConfig.StepScaling.FIXED) {
                int factor = SMAServerConfig.FIXED_SCALE_FACTOR.get();
                if (factor > 1) {
                    for (int i = 0; i < rawSteps.size(); i++) {
                        RawStep s = rawSteps.get(i);
                        rawSteps.set(i, new RawStep(s.item(), Math.max(1, s.count() / factor)));
                    }
                    outputCount = Math.max(1, outputCount / factor);
                }
            }

            rawSteps = optimizeToBlocks(rawSteps);
            var scaled = scaleToFitSteps(rawSteps, 7, outputCount);
            rawSteps = scaled.steps();
            outputCount = scaled.outputCount();

            List<ItemStack> assemblySteps = new ArrayList<>();
            for (RawStep s : rawSteps) {
                for (int i = 0; i < s.count(); i++) {
                    assemblySteps.add(s.item());
                }
            }

            List<ItemStack> gunpowderSteps = new ArrayList<>();
            assemblySteps.removeIf(item -> {
                if (item.getItem() == Items.GUNPOWDER || item.getItem() == Items.TNT) {
                    gunpowderSteps.add(item);
                    return true;
                }
                return false;
            });
            assemblySteps.addAll(gunpowderSteps);

            return new AmmoRecipeConfig.Config(
                    AmmoRecipeConfig.AmmoCategory.DEFAULT,
                    assemblySteps.stream().map(
                            item -> item.getComponents().isEmpty()
                                    ? Ingredient.of(item) // Optimize Matching
                                    : DataComponentIngredient.of(false, item)
                    ).toList(),
                    outputCount
            );
        } catch (Exception e) {
            SentryMechanicalArm.LOGGER.debug("Failed to build config from recipe", e);
            return null;
        }
    }

    private static int gcd(int a, int b) {
        while (b != 0) { int t = b; b = a % b; a = t; }
        return Math.abs(a);
    }

    private record TargetBlock(Item blockitem, int consumption) {}

    private static class ReplaceRulesHolder { // lazy load to avoid NullPointerException happened in Initialize Phase
        private static final Map<Item, TargetBlock> optimizeRules = Map.ofEntries(
                Map.entry(Items.GUNPOWDER, new TargetBlock(Items.TNT, 4)),
                Map.entry(Items.GLOWSTONE_DUST, new TargetBlock(Items.GLOWSTONE, 4)),
                Map.entry(Items.SNOWBALL, new TargetBlock(Items.SNOW_BLOCK, 4)),
                Map.entry(Items.CLAY_BALL, new TargetBlock(Items.CLAY, 4)),
                Map.entry(Items.BRICK, new TargetBlock(Items.BRICKS, 4)),
                Map.entry(Items.NETHER_BRICK, new TargetBlock(Items.NETHER_BRICKS, 4)),
                Map.entry(Items.AMETHYST_SHARD, new TargetBlock(Items.AMETHYST_BLOCK, 4)),
                Map.entry(Items.QUARTZ, new TargetBlock(Items.QUARTZ_BLOCK, 4)),
                Map.entry(Items.HONEYCOMB, new TargetBlock(Items.HONEY_BLOCK, 4)),
                Map.entry(Items.BONE_MEAL, new TargetBlock(Items.BONE_BLOCK, 9)),
                Map.entry(Items.LAPIS_LAZULI, new TargetBlock(Items.LAPIS_BLOCK, 9)),
                Map.entry(Items.REDSTONE, new TargetBlock(Items.REDSTONE_BLOCK, 9)),
                Map.entry(Items.DIAMOND, new TargetBlock(Items.DIAMOND_BLOCK, 9)),
                Map.entry(Items.EMERALD, new TargetBlock(Items.EMERALD_BLOCK, 9)),
                Map.entry(Items.COPPER_INGOT, new TargetBlock(Items.COPPER_BLOCK, 9)),
                Map.entry(Items.IRON_INGOT, new TargetBlock(Items.IRON_BLOCK, 9)),
                Map.entry(Items.GOLD_INGOT, new TargetBlock(Items.GOLD_BLOCK, 9)),
                Map.entry(AllItems.COPPER_SHEET.get(), new TargetBlock(Items.COPPER_BLOCK, 9)),
                Map.entry(AllItems.IRON_SHEET.get(), new TargetBlock(Items.IRON_BLOCK, 9)),
                Map.entry(AllItems.GOLDEN_SHEET.get(), new TargetBlock(Items.GOLD_BLOCK, 9))
        );
        public static Map<Item, TargetBlock> getOptimizeRulesLazy(){
            return optimizeRules;
        }
        private static final Map<Item, Item> asmMappingRules = Map.of(
                Items.COPPER_INGOT,AllItems.COPPER_SHEET.get(),
                Items.IRON_INGOT,AllItems.IRON_SHEET.get(),
                Items.GOLD_INGOT,AllItems.GOLDEN_SHEET.get(),
                AllItems.COPPER_SHEET.get(),AllItems.COPPER_SHEET.get(),
                AllItems.IRON_SHEET.get(),AllItems.IRON_SHEET.get(),
                AllItems.GOLDEN_SHEET.get(),AllItems.GOLDEN_SHEET.get()
        );
        public static Map<Item, Item> getAsmMappingRulesLazy(){
            return asmMappingRules;
        }
    }

    private static Item mapToAssemblyItem(Item rawItem) {
        Item result = ReplaceRulesHolder.getAsmMappingRulesLazy().get(rawItem);
        return result!=null?result:rawItem;
    }

    private record ScaledResult(List<RawStep> steps, int outputCount) {}

    private static ScaledResult scaleToFitSteps(List<RawStep> rawSteps, int maxSteps, int outputCount) {
        int totalSteps = 0;
        for (RawStep s : rawSteps) totalSteps += s.count();
        boolean hasGunpowder = rawSteps.stream().anyMatch(s ->
            s.item().getItem() == Items.GUNPOWDER || s.item().getItem() == Items.TNT);
        if (!hasGunpowder) totalSteps++;

        if (totalSteps <= maxSteps) return new ScaledResult(rawSteps, outputCount);

        int factor = (int) Math.ceil((double) totalSteps / maxSteps);
        List<RawStep> scaled = new ArrayList<>();
        for (RawStep s : rawSteps) {
            int newCount = Math.max(1, s.count() / factor);
            scaled.add(new RawStep(s.item(), newCount));
        }
        int newOutput = Math.max(1, outputCount / factor);
        return new ScaledResult(scaled, newOutput);
    }

    private static List<RawStep> optimizeToBlocks(List<RawStep> rawSteps) {
        List<RawStep> result = new ArrayList<>();
        for (RawStep step : rawSteps) {
            TargetBlock target = ReplaceRulesHolder.getOptimizeRulesLazy().get(step.item().getItem());
            int count = step.count();
            int remainder = count;
            if(target != null && count>=target.consumption){
                remainder = count % target.consumption;
                // block data components to avoid being unable to craft
                result.add(new RawStep(target.blockitem, count / target.consumption));
            }
            if (remainder > 0) result.add(remainder==count?step:new RawStep(step.item(), remainder));
        }
        return result;
    }

}
