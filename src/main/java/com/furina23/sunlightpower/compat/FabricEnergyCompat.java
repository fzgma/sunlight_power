package com.furina23.sunlightpower.compat;

import com.furina23.sunlightpower.SunlightPower;
import com.furina23.sunlightpower.block.entity.SolarPanelBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import team.reborn.energy.api.EnergyStorage;

/** Fabric Energy API 兼容层，仅在 team_reborn_energy 存在时由入口调用。 */
public final class FabricEnergyCompat {
    private FabricEnergyCompat() {
    }

    public static void bootstrap() {
        EnergyStorage.SIDED.registerForBlockEntity((panel, side) ->
                side != Direction.UP ? new PanelEnergyStorage(panel, side) : null, SunlightPower.SOLAR_PANEL_ENTITY);
        SunlightPower.LOGGER.info("已启用 Fabric Energy API 能源接口");
    }

    public static long transfer(SolarPanelBlockEntity panel, Direction side, long amount) {
        if (side == Direction.UP || amount <= 0 || panel.getLevel() == null) return 0;
        BlockEntity target = panel.getLevel().getBlockEntity(panel.getBlockPos().relative(side));
        if (target == null) return 0;
        EnergyStorage storage = EnergyStorage.SIDED.find(panel.getLevel(), panel.getBlockPos().relative(side),
                side.getOpposite());
        if (storage == null || !storage.supportsInsertion()) return 0;
        return panel.getEnergyBuffer().transferTo(side.ordinal(), panel.getLevel().getGameTime(), amount,
                SunlightPower.CONFIG.maxOutputPerSide, storage::insert);
    }

    public static long chargeItem(SolarPanelBlockEntity panel, long amount) {
        ContainerItemContext context = ContainerItemContext.ofSingleSlot(InventoryStorage.of(panel, null).getSlot(0));
        EnergyStorage storage = context.find(EnergyStorage.ITEM);
        if (storage == null || !storage.supportsInsertion()) return 0;
        return panel.getEnergyBuffer().transferToItem(Math.min(amount, SunlightPower.CONFIG.chargingSlotRate),
                storage::insert);
    }

    public static boolean canChargeItem(ItemStack stack) {
        EnergyStorage storage = EnergyStorage.ITEM.find(stack, ContainerItemContext.withConstant(stack));
        return storage != null && storage.supportsInsertion();
    }

    private static final class PanelEnergyStorage implements EnergyStorage {
        private final SolarPanelBlockEntity panel;
        private final Direction side;

        private PanelEnergyStorage(SolarPanelBlockEntity panel, Direction side) {
            this.panel = panel;
            this.side = side;
        }

        @Override public long insert(long maxAmount, TransactionContext transaction) { return 0; }

        @Override
        public long extract(long maxAmount, TransactionContext transaction) {
            if (panel.getLevel() == null) return 0;
            // Unsided lookups share the bottom-side budget rather than bypassing side limits.
            int channel = side == null ? Direction.DOWN.ordinal() : side.ordinal();
            return panel.getEnergyBuffer().extract(channel, panel.getLevel().getGameTime(), maxAmount,
                    SunlightPower.CONFIG.maxOutputPerSide, transaction);
        }

        @Override public long getAmount() { return panel.getEnergy(); }
        @Override public long getCapacity() { return panel.getCapacity(); }
        @Override public boolean supportsInsertion() { return false; }
        @Override public boolean supportsExtraction() { return true; }
    }
}
