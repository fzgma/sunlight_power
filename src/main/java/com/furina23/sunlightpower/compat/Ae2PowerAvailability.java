package com.furina23.sunlightpower.compat;

/** A short actual extraction removes a provider from AE2 until it announces available power again. */
final class Ae2PowerAvailability {
    private boolean notificationNeeded;

    void onExtraction(double requested, double supplied, boolean simulate) {
        if (!simulate && supplied < requested) notificationNeeded = true;
    }

    void notifyIfAvailable(long available, Runnable notifyGrid) {
        if (notificationNeeded && available > 0) {
            notifyGrid.run();
            notificationNeeded = false;
        }
    }
}
