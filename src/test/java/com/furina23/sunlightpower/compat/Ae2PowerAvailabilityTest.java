package com.furina23.sunlightpower.compat;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Ae2PowerAvailabilityTest {
    @Test
    void emptyProviderAnnouncesPowerWhenGenerationResumes() {
        var availability = new Ae2PowerAvailability();
        AtomicInteger events = new AtomicInteger();
        availability.onExtraction(10, 0, false);
        availability.notifyIfAvailable(0, events::incrementAndGet);
        assertEquals(0, events.get());
        availability.notifyIfAvailable(10, events::incrementAndGet);
        assertEquals(1, events.get());
        availability.notifyIfAvailable(10, events::incrementAndGet);
        assertEquals(1, events.get());
    }

    @Test
    void exhaustedOutputBudgetCanResumeOnTheNextTick() {
        var availability = new Ae2PowerAvailability();
        AtomicInteger events = new AtomicInteger();
        availability.onExtraction(20, 10, false);
        availability.notifyIfAvailable(0, events::incrementAndGet);
        assertEquals(0, events.get());
        availability.notifyIfAvailable(10, events::incrementAndGet);
        assertEquals(1, events.get());
        availability.onExtraction(20, 10, false);
        availability.notifyIfAvailable(10, events::incrementAndGet);
        assertEquals(2, events.get());
    }

    @Test
    void simulationAndCompleteExtractionDoNotRequestGridChanges() {
        var availability = new Ae2PowerAvailability();
        AtomicInteger events = new AtomicInteger();
        availability.onExtraction(10, 0, true);
        availability.notifyIfAvailable(10, events::incrementAndGet);
        availability.onExtraction(10, 10, false);
        availability.notifyIfAvailable(10, events::incrementAndGet);
        assertEquals(0, events.get());
    }
}
