package euphy.upo.sentrymechanicalarm.registry;

import com.simibubi.create.content.fluids.VirtualFluid;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.*;

/** Process-only fluid: transported by pipes and tanks, without a placeable world block. */
public final class SentryFluids {
    private SentryFluids() {}
    public static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, "sentrymechanicalarm");
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, "sentrymechanicalarm");
    public static final DeferredHolder<FluidType, FluidType> PROPELLANT_TYPE = TYPES.register("propellant",
            () -> new FluidType(FluidType.Properties.create().descriptionId("fluid_type.sentrymechanicalarm.propellant").density(1200).viscosity(1800)));
    public static final DeferredHolder<Fluid, VirtualFluid> PROPELLANT = FLUIDS.register("propellant", () -> VirtualFluid.createSource(properties()));
    public static final DeferredHolder<Fluid, VirtualFluid> FLOWING_PROPELLANT = FLUIDS.register("flowing_propellant", () -> VirtualFluid.createFlowing(properties()));
    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(PROPELLANT_TYPE, PROPELLANT, FLOWING_PROPELLANT);
    }
    public static void register(IEventBus bus) { TYPES.register(bus); FLUIDS.register(bus); }
}
