package com.furina23.sunlightpower.compat;

import java.util.function.DoubleUnaryOperator;

/** Converts fractional AE power without creating energy in an integer-valued source. */
final class Ae2EnergyConversion {
    private Ae2EnergyConversion() {
    }

    static Extraction extraction(double requestedAe, long available,
                                 DoubleUnaryOperator storedToAe, DoubleUnaryOperator aeToStored) {
        if (!(requestedAe > 0) || available <= 0) return new Extraction(0, 0);
        double deliveredAe = Math.min(requestedAe, storedToAe.applyAsDouble(available));
        long consumed = Math.min(available, (long) Math.ceil(aeToStored.applyAsDouble(deliveredAe)));
        return new Extraction(consumed, deliveredAe);
    }

    private static long wholeStoredUnits(double requestedAe, long available, DoubleUnaryOperator aeToStored) {
        long requested = (long) Math.floor(aeToStored.applyAsDouble(Math.max(0, requestedAe)));
        return Math.max(0, Math.min(available, requested));
    }

    static long charge(long offered, DoubleUnaryOperator storedToAe, DoubleUnaryOperator aeToStored,
                       Receiver receiver) {
        if (offered <= 0) return 0;
        double offeredAe = storedToAe.applyAsDouble(offered);
        double simulatedAccepted = accepted(offeredAe, receiver.inject(offeredAe, true));
        long wholeUnits = wholeStoredUnits(simulatedAccepted, offered, aeToStored);
        if (wholeUnits == 0) return 0;

        double wholeAe = storedToAe.applyAsDouble(wholeUnits);
        double actualAccepted = accepted(wholeAe, receiver.inject(wholeAe, false));
        // A receiver can accept less than its simulation promised. Charge every accepted fraction.
        long consumed = (long) Math.ceil(aeToStored.applyAsDouble(actualAccepted));
        return Math.max(0, Math.min(wholeUnits, consumed));
    }

    private static double accepted(double offered, double remainder) {
        return Math.max(0, Math.min(offered, offered - remainder));
    }

    @FunctionalInterface
    interface Receiver {
        double inject(double offeredAe, boolean simulate);
    }

    record Extraction(long storedUnits, double aePower) {
    }
}
