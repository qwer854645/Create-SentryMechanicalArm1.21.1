package euphy.upo.sentrymechanicalarm.client;

/** Keeps JEI optional: none of its API types occur in callers or this bridge's signatures. */
public final class CasingJeiBridge {
    public static void refresh(){
        if(!net.neoforged.fml.ModList.get().isLoaded("jei"))return;
        try{Class.forName("euphy.upo.sentrymechanicalarm.compat.jei.CasingJeiPlugin").getMethod("refresh").invoke(null);}
        catch(ReflectiveOperationException e){euphy.upo.sentrymechanicalarm.SentryMechanicalArm.LOGGER.error("Cannot refresh JEI casing variants",e);}
    }
}
