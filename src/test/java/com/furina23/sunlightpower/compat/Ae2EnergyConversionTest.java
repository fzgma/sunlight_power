package com.furina23.sunlightpower.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ae2EnergyConversionTest {
    @Test
    void extractionNeverRoundsPastTheRequestedPower() {
        var extracted = extraction(0.75, 100);
        assertEquals(2, extracted.storedUnits());
        assertEquals(0.75, extracted.aePower());
        assertTrue(extracted.storedUnits() / 2.0 >= extracted.aePower());
    }

    @Test
    void fractionalPowerRequestsUsedByAe2ToDetectPoweredGridsRemainSupported() {
        // AE2 probes an otherwise idle grid with a 0.1 AE simulation.
        var extracted = extraction(0.1, 100);
        assertEquals(0.1, extracted.aePower());
        assertEquals(1, extracted.storedUnits());
    }

    @Test
    void extractionHonorsAvailableEnergyAndRejectsNegativeRequests() {
        assertEquals(3, extraction(10, 3).storedUnits());
        assertEquals(1.5, extraction(10, 3).aePower());
        assertEquals(0, extraction(10, 0).aePower());
        assertEquals(0, extraction(-10, 100).aePower());
    }

    @Test
    void itemChargingUsesOnlyTheOfferedEnergy() {
        Battery item = new Battery(100);
        assertEquals(6, charge(6, item));
        assertEquals(3, item.storedAe);
    }

    @Test
    void aFullItemDoesNotConsumeEnergy() {
        Battery item = new Battery(2);
        item.storedAe = 2;
        assertEquals(0, charge(100, item));
        assertEquals(2, item.storedAe);
        assertEquals(0, item.actualInjections);
    }

    @Test
    void aFractionalUnitOfFreeSpaceDoesNotCreateFreeEnergy() {
        Battery item = new Battery(0.25);
        assertEquals(0, charge(10, item));
        assertEquals(0, item.storedAe);
        assertEquals(0, item.actualInjections);
    }

    @Test
    void nearlyFullItemAcceptsOnlyWholeStoredUnits() {
        Battery item = new Battery(0.75);
        assertEquals(1, charge(10, item));
        assertEquals(0.5, item.storedAe);
        assertEquals(1, item.actualInjections);
    }

    @Test
    void reducedAcceptanceAfterSimulationStillConsumesTheInjectedFraction() {
        double[] received = {0};
        long consumed = Ae2EnergyConversion.charge(2, stored -> stored / 2, ae -> ae * 2,
                (offered, simulate) -> {
                    if (simulate) return 0;
                    received[0] += 0.25;
                    return offered - 0.25;
                });

        assertEquals(1, consumed);
        assertEquals(0.25, received[0]);
        assertTrue(consumed / 2.0 >= received[0]);
    }

    @Test
    void rejectedModulationDoesNotConsumeEnergy() {
        assertEquals(0, Ae2EnergyConversion.charge(2, stored -> stored / 2, ae -> ae * 2,
                (offered, simulate) -> simulate ? 0 : offered));
    }

    @Test
    void emptyOrNegativeOffersNeverTouchTheItem() {
        Battery item = new Battery(100);
        assertEquals(0, charge(0, item));
        assertEquals(0, charge(-1, item));
        assertEquals(0, item.storedAe);
        assertEquals(0, item.actualInjections);
    }

    private static long charge(long offered, Battery item) {
        return Ae2EnergyConversion.charge(offered, stored -> stored / 2, ae -> ae * 2, item::inject);
    }

    private static Ae2EnergyConversion.Extraction extraction(double requestedAe, long available) {
        return Ae2EnergyConversion.extraction(requestedAe, available, stored -> stored / 2, ae -> ae * 2);
    }

    private static final class Battery {
        private final double capacityAe;
        private double storedAe;
        private int actualInjections;

        private Battery(double capacityAe) {
            this.capacityAe = capacityAe;
        }

        private double inject(double offered, boolean simulate) {
            double accepted = Math.min(offered, capacityAe - storedAe);
            if (!simulate) {
                storedAe += accepted;
                actualInjections++;
            }
            return offered - accepted;
        }
    }
}
