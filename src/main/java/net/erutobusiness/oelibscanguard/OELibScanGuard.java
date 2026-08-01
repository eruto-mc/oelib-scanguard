package net.erutobusiness.oelibscanguard;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * OELib Scan Guard — a one-fix compatibility mod.
 *
 * <p>It exists only to stop an intermittent startup crash inside OELib's packet auto-registration
 * (a {@code ConcurrentModificationException} raised when Forge constructs mods in parallel).
 * All of the work happens in {@code NetworkAutoRegistrationMixin}; this class just declares the mod.
 *
 * <p>If upstream OELib fixes the race, this mod can simply be removed.
 */
@Mod(OELibScanGuard.MOD_ID)
public class OELibScanGuard {

    public static final String MOD_ID = "oelibscanguard";
    public static final Logger LOGGER = LogUtils.getLogger();

    public OELibScanGuard() {
        LOGGER.info("OELib Scan Guard active: OELib's packet scan will be snapshotted under its own lock.");
    }
}
