package euphy.upo.sentrymechanicalarm.mixin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tacz.guns.resource.DelegatingPackResources;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** TaCZ wraps several gunpacks in one PackResources. Language files must compose
 * by key, rather than losing all but the first pack's file for each namespace. */
@Mixin(DelegatingPackResources.class)
public abstract class TaczGunPackLanguageMixin {
    @Inject(method = "getResource", at = @At("HEAD"), cancellable = true, remap = false)
    private void sentry$mergeLanguageFiles(PackType type, ResourceLocation location,
            CallbackInfoReturnable<IoSupplier<InputStream>> cir) {
        if (type != PackType.CLIENT_RESOURCES || !location.getPath().startsWith("lang/")
                || !location.getPath().endsWith(".json")) return;
        var children = ((DelegatingPackResources)(Object)this).getChildren();
        if (children == null || children.size() < 2) return;
        var sources = new ArrayList<IoSupplier<InputStream>>();
        for (var child : children) {
            var source = child.getResource(type, location);
            if (source != null) sources.add(source);
        }
        if (sources.size() < 2) return;
        var orderedSources = List.copyOf(sources);
        cir.setReturnValue(() -> sentry$readMergedLanguage(location, orderedSources));
    }
    @Unique private static InputStream sentry$readMergedLanguage(ResourceLocation location,
            List<IoSupplier<InputStream>> sources) {
        var merged = new JsonObject();
        // DelegatingPackResources#getResource returns the first matching child.
        // Keep that same precedence for duplicate keys, while filling missing keys
        // from lower-priority children. External resource packs still override normally.
        for (var source : sources) {
            try (var stream = source.get(); var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                var language = JsonParser.parseReader(reader).getAsJsonObject();
                for (var entry : language.entrySet())
                    if (!merged.has(entry.getKey()) && entry.getValue().isJsonPrimitive())
                        merged.add(entry.getKey(), entry.getValue().deepCopy());
            } catch (IOException | RuntimeException error) {
                SentryMechanicalArm.LOGGER.warn("Cannot read gunpack language {} while merging translations", location, error);
            }
        }
        return new ByteArrayInputStream(merged.toString().getBytes(StandardCharsets.UTF_8));
    }
}
