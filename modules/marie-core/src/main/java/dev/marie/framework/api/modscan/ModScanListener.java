package dev.marie.framework.api.modscan;

import dev.marie.framework.api.ApiStatus;

/**
 * Plug-in point: observes a mod scan. Both callbacks run on the scan thread, not the game thread;
 * an exception thrown from one is logged and ignored. A scan that is cancelled fires no finish callback.
 */
@ApiStatus.Experimental
public interface ModScanListener {

    void onScanStarted(ModScanStart start);

    void onScanFinished(ModScanFinish finish);
}
