package euphy.upo.sentrymechanicalarm.recipe;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.deployer.*;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import euphy.upo.sentrymechanicalarm.SMAServerConfig;
import euphy.upo.sentrymechanicalarm.content.EmptyCasingItem;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.SentryCasingProfiles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import java.math.*;
import java.util.*;

/** Shell metal is independent of payload analysis; generic copper fallback is an explicit policy. */
public final class CasingFormingRecipes {
    public static final String PREFIX = "casing_forming/";
    public static boolean enabled() { return SentryRegistry.CASING_MOLD.isBound() && SMAServerConfig.ENABLE_CASING_FORMING.get(); }
    public static ResourceLocation id(ResourceLocation ammo) { return ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", PREFIX + ammo.getNamespace() + "/" + ammo.getPath()); }
    public static Optional<RecipeHolder<?>> find(RecipeManager manager, ResourceLocation ammo) { return manager.byKey(id(ammo)); }
    public static List<ResourceLocation> choices(RecipeManager manager) {
        return manager.getRecipes().stream().filter(h -> h.id().getNamespace().equals("sentrymechanicalarm") && h.id().getPath().startsWith(PREFIX))
                .map(h -> EmptyCasingItem.ammoId(h.value().getResultItem(net.minecraft.core.RegistryAccess.EMPTY)))
                .filter(Objects::nonNull).distinct().sorted().toList();
    }
    public record Plan(Item metal, int yield) {}
    public static Optional<Plan> plan(GunSmithTableRecipe original) {
        var steps = ReloadingCosts.derive(original, SMAServerConfig.RELOADING_METAL_FACTOR.get());
        if (original.getOutput().getCount() <= 0) return Optional.empty();
        var nativeMetals = new LinkedHashMap<Item, BigDecimal>();
        for (var material : original.getInputs()) {
            var candidates = material.getIngredient().getItems();
            if (material.getCount() <= 0 || candidates.length == 0 || !material.getIngredient().isSimple()) continue;
            Item input = candidates[0].getItem();
            if (Arrays.stream(candidates).anyMatch(s -> s.getItem() != input || !s.getComponentsPatch().isEmpty() || s.hasCraftingRemainingItem())) continue;
            Item nugget = input == Items.COPPER_INGOT ? AllItems.COPPER_NUGGET.get()
                    : input == Items.IRON_INGOT ? Items.IRON_NUGGET : input == Items.GOLD_INGOT ? Items.GOLD_NUGGET : input;
            if (nugget != AllItems.COPPER_NUGGET.get() && nugget != Items.IRON_NUGGET && nugget != Items.GOLD_NUGGET) continue;
            var units = BigDecimal.valueOf(material.getCount()).multiply(BigDecimal.valueOf(input == nugget ? 1 : 9));
            nativeMetals.merge(nugget, units, BigDecimal::add);
        }
        var rounds = BigDecimal.valueOf(original.getOutput().getCount());
        for (Item metal : List.of(AllItems.COPPER_NUGGET.get(), Items.IRON_NUGGET, Items.GOLD_NUGGET)) {
            var nativeUnits = nativeMetals.get(metal); if (nativeUnits == null) continue;
            var shellShare = nativeUnits.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(SMAServerConfig.RELOADING_METAL_FACTOR.get())));
            var budget = shellShare;
            if (steps.isPresent()) {
                long direct = steps.get().stream().filter(i -> i.test(new ItemStack(metal))).count();
                var reloading = BigDecimal.valueOf(direct);
                if (metal == AllItems.COPPER_NUGGET.get()) reloading = reloading.add(BigDecimal.ONE.divide(BigDecimal.valueOf(PrimerAllocation.CUPS_PER_NUGGET)));
                budget = budget.min(nativeUnits.subtract(reloading.multiply(rounds)));
            }
            if (budget.signum() <= 0) continue;
            var count = rounds.divide(budget, 0, RoundingMode.CEILING).max(BigDecimal.ONE);
            if (count.compareTo(BigDecimal.valueOf(64)) <= 0) return Optional.of(new Plan(metal, count.intValueExact()));
        }
        return Optional.empty();
    }
    public static List<RecipeHolder<?>> build(RecipeManager manager) {
        if (!enabled()) return List.of();
        var result = new ArrayList<RecipeHolder<?>>();
        var inputs = net.minecraft.core.NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.STICK));
        result.add(new RecipeHolder<>(ResourceLocation.parse("sentrymechanicalarm:casing_mold"), new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(SentryRegistry.CASING_MOLD.get()), inputs)));
        var sources = new LinkedHashMap<ResourceLocation, List<GunSmithTableRecipe>>();
        for (var holder : AmmoMaterialSources.collect(manager,net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer())) {
            var original = holder.value();
            var api = IAmmo.getIAmmoOrNull(original.getOutput()); if (api == null) continue;
            var ammo = api.getAmmoId(original.getOutput());
            sources.computeIfAbsent(ammo, key -> new ArrayList<>()).add(original);
        }
        int nativeCount = 0, fallbackCount = 0;
        for (var ammo : SentryCasingProfiles.eligible().stream().sorted().toList()) {
            var originals = sources.getOrDefault(ammo, List.of());
            // Full automatic reloading still selects its first understood recipe. Other
            // casing types may use a metal-only budget without understanding their payload.
            var understood = originals.stream().filter(r -> ReloadingCosts.derive(r, SMAServerConfig.RELOADING_METAL_FACTOR.get()).isPresent()).findFirst();
            Optional<Plan> plan = understood.isPresent() ? plan(understood.get()) : originals.stream().map(CasingFormingRecipes::plan).flatMap(Optional::stream).findFirst();
            if (plan.isPresent()) nativeCount++;
            else if (SMAServerConfig.GENERIC_CASING_FORMING.get()) {
                plan = Optional.of(new Plan(AllItems.COPPER_NUGGET.get(), SMAServerConfig.DEFAULT_CASING_YIELD.get())); fallbackCount++;
            } else continue;
            var p = plan.get();
            var recipe = new ItemApplicationRecipe.Builder<>(DeployerApplicationRecipe::new, id(ammo))
                    .require(p.metal()).require(new MoldIngredient(ammo)).output(EmptyCasingItem.create(ammo, p.yield())).toolNotConsumed().build();
            result.add(new RecipeHolder<>(id(ammo), recipe));
        }
        euphy.upo.sentrymechanicalarm.SentryMechanicalArm.LOGGER.info("Casing manufacture: {} native metal budgets, {} generic copper rules", nativeCount, fallbackCount);
        return List.copyOf(result);
    }
}
