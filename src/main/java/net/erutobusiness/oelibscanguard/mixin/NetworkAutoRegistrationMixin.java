package net.erutobusiness.oelibscanguard.mixin;

import net.erutobusiness.oelibscanguard.OELibScanGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collection;
import java.util.Set;

/**
 * Makes OELib's packet scan tolerate concurrent registration during parallel mod loading.
 *
 * <p><b>Symptom.</b> The game fails to start, intermittently:
 *
 * <pre>
 * Failed to create mod instance. ModID: oelib, class cc.sighs.oelib.forge.OELibForge
 * java.util.ConcurrentModificationException: null
 *   at java.util.LinkedHashMap$LinkedHashIterator.nextNode
 *   at java.util.AbstractCollection.addAll
 *   at java.util.HashSet.&lt;init&gt;
 *   at java.util.Set.copyOf
 *   at cc.sighs.oelib.network.api.NetworkAutoRegistration.findAllAnnotatedPackets
 *   at cc.sighs.oelib.forge.OELibForge.&lt;init&gt;
 *   ... ForkJoinPool (modloading-worker)
 * </pre>
 *
 * <p><b>Cause.</b> {@code NetworkAutoRegistration} keeps its base packages in
 * {@code Collections.synchronizedSet(new LinkedHashSet&lt;&gt;())} and snapshots them with
 * {@code Set.copyOf(BASE_PACKAGES)}. A synchronized wrapper only guards single operations —
 * its javadoc requires the <i>caller</i> to hold the wrapper's monitor while iterating, and
 * {@code Set.copyOf} iterates. Forge constructs mods in parallel, so another mod calling
 * {@code registerBasePackage} at that moment mutates the set mid-iteration. It is a race,
 * hence intermittent.
 *
 * <p><b>Fix.</b> Take the wrapper's monitor around the copy. {@code Collections.synchronizedSet}
 * uses the returned wrapper itself as the mutex, so synchronizing on the collection being copied
 * is exactly the lock the mutating side already holds.
 *
 * <p>Both {@code Set.copyOf} calls in the method are redirected on purpose (no {@code ordinal}),
 * so this keeps working if the call order changes. The second one copies a
 * {@code ConcurrentHashMap} key set, where the extra lock is uncontended and harmless.
 *
 * <p><b>remap = false</b>: the target is OELib's own class and method, not a Minecraft one.
 *
 * @see <a href="https://github.com/Tower-of-Sighs/OELib">OELib</a>
 */
@Mixin(targets = "cc.sighs.oelib.network.api.NetworkAutoRegistration", remap = false)
public class NetworkAutoRegistrationMixin {

    @Redirect(
            method = "findAllAnnotatedPackets",
            at = @At(value = "INVOKE",
                     target = "Ljava/util/Set;copyOf(Ljava/util/Collection;)Ljava/util/Set;"),
            remap = false)
    private static Set<?> oelibscanguard$copyUnderLock(Collection<?> source) {
        synchronized (source) {
            Set<?> copy = Set.copyOf(source);
            OELibScanGuard.LOGGER.debug("guarded snapshot of {} entr{}",
                    copy.size(), copy.size() == 1 ? "y" : "ies");
            return copy;
        }
    }
}
