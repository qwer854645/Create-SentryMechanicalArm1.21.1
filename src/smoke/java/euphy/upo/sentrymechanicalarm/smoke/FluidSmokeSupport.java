package euphy.upo.sentrymechanicalarm.smoke;

import com.simibubi.create.AllBlocks;
import euphy.upo.sentrymechanicalarm.registry.SentryFluids;
import net.minecraft.core.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import static euphy.upo.sentrymechanicalarm.smoke.ReloadingSmoke.*;

final class FluidSmokeSupport {
    static IFluidHandler spout(BlockPos depot,int amount){
        var position=depot.above(2);level.setBlock(position,AllBlocks.SPOUT.get().defaultBlockState(),3);
        var handler=level.getCapability(Capabilities.FluidHandler.BLOCK,position,Direction.UP);
        check(handler!=null,"spout fluid capability missing");add(handler,amount);return handler;
    }
    static void add(IFluidHandler handler,int amount){check(handler.fill(new FluidStack(SentryFluids.PROPELLANT.get(),amount),IFluidHandler.FluidAction.EXECUTE)==amount,"spout rejected fluid");}
}
