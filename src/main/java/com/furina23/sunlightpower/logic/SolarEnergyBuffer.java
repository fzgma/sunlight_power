package com.furina23.sunlightpower.logic;

import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;

import java.util.Arrays;

/** Shared storage and output budgets, including transactional rollback across all sides. */
public final class SolarEnergyBuffer extends SnapshotParticipant<SolarEnergyBuffer.Snapshot> {
    public static final int AE2_CHANNEL = 6;
    private static final int CHARGING_CHANNEL = -1;

    private final Runnable changed;
    private long amount;
    private long outputTick;
    private long[] outputUsed = new long[7];

    public SolarEnergyBuffer(Runnable changed) {
        this.changed = changed;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long value, long capacity) {
        long bounded = Math.max(0, Math.min(Math.max(0, capacity), value));
        if (amount != bounded) {
            amount = bounded;
            changed.run();
        }
    }

    public long addGeneration(long generated, long capacity) {
        long boundedCapacity = Math.max(0, capacity);
        long stored = Math.min(amount, boundedCapacity);
        // Subtract before adding so a valid long-sized configuration cannot overflow.
        long accepted = Math.min(Math.max(0, generated), boundedCapacity - stored);
        setAmount(stored + accepted, boundedCapacity);
        return accepted;
    }

    public long getAvailableOutput(int channel, long gameTime, long maxOutput) {
        long used = outputTick == gameTime ? outputUsed[channel] : 0;
        return Math.min(amount, Math.max(0, Math.max(0, maxOutput) - used));
    }

    public long extract(int channel, long gameTime, long requested, long maxOutput,
                        TransactionContext transaction) {
        long available = channel == CHARGING_CHANNEL ? amount : getAvailableOutput(channel, gameTime, maxOutput);
        long extracted = Math.min(Math.max(0, requested), available);
        if (extracted == 0) return 0;
        if (transaction != null) updateSnapshots(transaction);
        amount -= extracted;
        if (channel != CHARGING_CHANNEL) {
            if (outputTick != gameTime) {
                Arrays.fill(outputUsed, 0);
                outputTick = gameTime;
            }
            outputUsed[channel] += extracted;
        }
        if (transaction == null) changed.run();
        return extracted;
    }

    public long transferToItem(long maxAmount, Receiver receiver) {
        return transferTo(CHARGING_CHANNEL, 0, maxAmount, 0, receiver);
    }

    public long transferTo(int channel, long gameTime, long maxAmount, long maxOutput, Receiver receiver) {
        long available = channel == CHARGING_CHANNEL ? amount : getAvailableOutput(channel, gameTime, maxOutput);
        long offered = Math.min(Math.max(0, maxAmount), available);
        if (offered == 0) return 0;
        try (Transaction transaction = Transaction.openOuter()) {
            long accepted = receiver.insert(offered, transaction);
            if (accepted < 0 || accepted > offered) {
                throw new IllegalStateException("Energy receiver accepted an invalid amount: " + accepted);
            }
            if (accepted > 0) {
                long extracted = extract(channel, gameTime, accepted, maxOutput, transaction);
                if (extracted != accepted) {
                    throw new IllegalStateException("Energy changed during transfer");
                }
                transaction.commit();
            }
            return accepted;
        }
    }

    @Override
    protected Snapshot createSnapshot() {
        return new Snapshot(amount, outputTick, outputUsed.clone());
    }

    @Override
    protected void readSnapshot(Snapshot snapshot) {
        amount = snapshot.amount();
        outputTick = snapshot.outputTick();
        outputUsed = snapshot.outputUsed();
    }

    @Override
    protected void onFinalCommit() {
        changed.run();
    }

    @FunctionalInterface
    public interface Receiver {
        long insert(long offered, TransactionContext transaction);
    }

    record Snapshot(long amount, long outputTick, long[] outputUsed) {
    }
}
