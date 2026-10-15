package com.furina23.sunlightpower.logic;

import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SolarEnergyBufferTest {
    private static final int NORTH = 2;
    private static final int SOUTH = 3;
    private static final long TICK = 100;
    private static final long OUTPUT_LIMIT = 16;

    private final AtomicInteger changes = new AtomicInteger();
    private final SolarEnergyBuffer buffer = new SolarEnergyBuffer(changes::incrementAndGet);

    @Test
    void generationStoresEnergyWhileCapacityIsAvailable() {
        buffer.setAmount(100, 8000);
        assertEquals(8, buffer.addGeneration(8, 8000));
        assertEquals(108, buffer.getAmount());
    }

    @Test
    void aFullBatteryRejectsGenerationWithoutMarkingItDirty() {
        buffer.setAmount(8000, 8000);
        changes.set(0);
        assertEquals(0, buffer.addGeneration(8, 8000));
        assertEquals(8000, buffer.getAmount());
        assertEquals(0, changes.get());
    }

    @Test
    void generationOnlyFillsTheRemainingCapacity() {
        buffer.setAmount(7997, 8000);
        assertEquals(3, buffer.addGeneration(8, 8000));
        assertEquals(8000, buffer.getAmount());
    }

    @Test
    void validLongSizedGenerationCannotOverflowTheBattery() {
        buffer.setAmount(Long.MAX_VALUE - 2, Long.MAX_VALUE);
        assertEquals(2, buffer.addGeneration(Long.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, buffer.getAmount());
    }

    @Test
    void loadedEnergyIsBoundedByCapacityAndCannotBeNegative() {
        buffer.setAmount(Long.MAX_VALUE, 8000);
        assertEquals(8000, buffer.getAmount());
        buffer.setAmount(-1, 8000);
        assertEquals(0, buffer.getAmount());
    }

    @Test
    void itemChargingCommitsTheSameAmountToBothBatteries() {
        buffer.setAmount(100, 8000);
        TestBattery item = new TestBattery(1000, 0);
        changes.set(0);
        assertEquals(16, buffer.transferToItem(16, item::insert));
        assertEquals(84, buffer.getAmount());
        assertEquals(16, item.amount);
        assertEquals(1, changes.get());
    }

    @Test
    void chargingStopsAtTheItemsRemainingCapacity() {
        buffer.setAmount(100, 8000);
        TestBattery item = new TestBattery(1000, 997);
        assertEquals(3, buffer.transferToItem(16, item::insert));
        assertEquals(97, buffer.getAmount());
        assertEquals(1000, item.amount);
    }

    @Test
    void aFullOrRejectingItemDoesNotConsumeSourceEnergy() {
        buffer.setAmount(100, 8000);
        TestBattery fullItem = new TestBattery(1000, 1000);
        assertEquals(0, buffer.transferToItem(16, fullItem::insert));
        assertEquals(0, buffer.transferToItem(16, (offered, transaction) -> 0));
        assertEquals(100, buffer.getAmount());
        assertEquals(1000, fullItem.amount);
    }

    @Test
    void chargingCannotCreateEnergyWhenThePanelHasLessThanTheRate() {
        buffer.setAmount(3, 8000);
        TestBattery item = new TestBattery(1000, 0);
        assertEquals(3, buffer.transferToItem(16, item::insert));
        assertEquals(0, buffer.getAmount());
        assertEquals(3, item.amount);
        assertEquals(0, buffer.transferToItem(16, item::insert));
    }

    @Test
    void receiverFailureRollsBackItsInsertion() {
        buffer.setAmount(100, 8000);
        TestBattery target = new TestBattery(1000, 0);
        assertThrows(IllegalStateException.class, () -> buffer.transferToItem(16, (offered, transaction) -> {
            target.insert(offered, transaction);
            throw new IllegalStateException("receiver failed");
        }));
        assertEquals(100, buffer.getAmount());
        assertEquals(0, target.amount);
    }

    @Test
    void anInvalidReceiverResultAbortsInsteadOfCreatingEnergy() {
        buffer.setAmount(100, 8000);
        TestBattery target = new TestBattery(1000, 0);
        assertThrows(IllegalStateException.class, () -> buffer.transferToItem(16, (offered, transaction) -> {
            target.insert(offered, transaction);
            return offered + 1;
        }));
        assertEquals(100, buffer.getAmount());
        assertEquals(0, target.amount);
    }

    @Test
    void committedExtractionIsLimitedByRequestedAmountAndSideBudget() {
        buffer.setAmount(100, 8000);
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(5, buffer.extract(NORTH, TICK, 5, OUTPUT_LIMIT, transaction));
            assertEquals(11, buffer.extract(NORTH, TICK, Long.MAX_VALUE, OUTPUT_LIMIT, transaction));
            assertEquals(0, buffer.extract(NORTH, TICK, 1, OUTPUT_LIMIT, transaction));
            transaction.commit();
        }
        assertEquals(84, buffer.getAmount());
    }

    @Test
    void pushAndPullShareTheSamePerTickSideBudget() {
        buffer.setAmount(100, 8000);
        TestBattery neighbor = new TestBattery(1000, 0);
        assertEquals(10, buffer.transferTo(NORTH, TICK, 10, OUTPUT_LIMIT, neighbor::insert));
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(6, buffer.extract(NORTH, TICK, 16, OUTPUT_LIMIT, transaction));
            transaction.commit();
        }
        assertEquals(0, buffer.transferTo(NORTH, TICK, 16, OUTPUT_LIMIT, neighbor::insert));
        assertEquals(84, buffer.getAmount());
        assertEquals(10, neighbor.amount);
    }

    @Test
    void outputBudgetsAreIndependentAndRenewOnTheNextTick() {
        buffer.setAmount(100, 8000);
        assertEquals(16, buffer.extract(NORTH, TICK, 100, OUTPUT_LIMIT, null));
        assertEquals(16, buffer.extract(SOUTH, TICK, 100, OUTPUT_LIMIT, null));
        assertEquals(16, buffer.extract(NORTH, TICK + 1, 100, OUTPUT_LIMIT, null));
        assertEquals(52, buffer.getAmount());
    }

    @Test
    void ae2HasAnIndependentBudgetThatAlsoLimitsRepeatedRequests() {
        buffer.setAmount(100, 8000);
        assertEquals(16, buffer.extract(NORTH, TICK, 100, OUTPUT_LIMIT, null));
        assertEquals(16, buffer.extract(SolarEnergyBuffer.AE2_CHANNEL, TICK, 100, OUTPUT_LIMIT, null));
        assertEquals(0, buffer.extract(SolarEnergyBuffer.AE2_CHANNEL, TICK, 100, OUTPUT_LIMIT, null));
        assertEquals(68, buffer.getAmount());
    }

    @Test
    void abortedExtractionRestoresEnergyAndTheOutputBudget() {
        buffer.setAmount(100, 8000);
        changes.set(0);
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(16, buffer.extract(NORTH, TICK, 100, OUTPUT_LIMIT, transaction));
        }
        assertEquals(100, buffer.getAmount());
        assertEquals(16, buffer.getAvailableOutput(NORTH, TICK, OUTPUT_LIMIT));
        assertEquals(0, changes.get());
    }

    @Test
    void aCommittedNestedExtractionStillRollsBackWithItsOuterTransaction() {
        buffer.setAmount(100, 8000);
        try (Transaction outer = Transaction.openOuter()) {
            assertEquals(10, buffer.extract(NORTH, TICK, 10, OUTPUT_LIMIT, outer));
            try (Transaction inner = outer.openNested()) {
                assertEquals(6, buffer.extract(NORTH, TICK, 16, OUTPUT_LIMIT, inner));
                assertEquals(16, buffer.extract(SOUTH, TICK, 16, OUTPUT_LIMIT, inner));
                inner.commit();
            }
        }
        assertEquals(100, buffer.getAmount());
        assertEquals(16, buffer.getAvailableOutput(NORTH, TICK, OUTPUT_LIMIT));
        assertEquals(16, buffer.getAvailableOutput(SOUTH, TICK, OUTPUT_LIMIT));
    }

    @Test
    void abortingAnInnerTransactionPreservesCommittedOuterChanges() {
        buffer.setAmount(100, 8000);
        try (Transaction outer = Transaction.openOuter()) {
            assertEquals(10, buffer.extract(NORTH, TICK, 10, OUTPUT_LIMIT, outer));
            try (Transaction inner = outer.openNested()) {
                assertEquals(6, buffer.extract(NORTH, TICK, 16, OUTPUT_LIMIT, inner));
            }
            outer.commit();
        }
        assertEquals(90, buffer.getAmount());
        assertEquals(6, buffer.getAvailableOutput(NORTH, TICK, OUTPUT_LIMIT));
    }

    @Test
    void disabledOutputAndEmptyStorageCannotTransferEnergy() {
        buffer.setAmount(100, 8000);
        assertEquals(0, buffer.extract(NORTH, TICK, 100, 0, null));
        assertEquals(0, buffer.extract(NORTH, TICK, -1, OUTPUT_LIMIT, null));
        buffer.setAmount(0, 8000);
        assertEquals(0, buffer.extract(NORTH, TICK, 100, OUTPUT_LIMIT, null));
    }

    @Test
    void outputCannotExceedStoredEnergyEvenWithALargeConfiguredLimit() {
        buffer.setAmount(3, 8000);
        assertEquals(3, buffer.extract(NORTH, TICK, Long.MAX_VALUE, Long.MAX_VALUE, null));
        assertEquals(0, buffer.getAmount());
    }

    /** A receiver using the actual Fabric transaction API, without Minecraft world setup. */
    private static final class TestBattery extends SnapshotParticipant<Long> {
        private final long capacity;
        private long amount;

        private TestBattery(long capacity, long amount) {
            this.capacity = capacity;
            this.amount = amount;
        }

        private long insert(long offered, TransactionContext transaction) {
            long accepted = Math.min(offered, capacity - amount);
            if (accepted > 0) {
                updateSnapshots(transaction);
                amount += accepted;
            }
            return accepted;
        }

        @Override protected Long createSnapshot() { return amount; }
        @Override protected void readSnapshot(Long snapshot) { amount = snapshot; }
    }
}
