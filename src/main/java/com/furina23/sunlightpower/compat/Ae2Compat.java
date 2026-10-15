package com.furina23.sunlightpower.compat;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnits;
import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.networking.events.GridPowerStorageStateChanged;
import appeng.api.util.AECableType;
import com.furina23.sunlightpower.SunlightPower;
import com.furina23.sunlightpower.block.entity.SolarPanelBlockEntity;
import com.furina23.sunlightpower.logic.SolarEnergyBuffer;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;

/** AE2 Fabric 15.4.11 官方网络 API 兼容层。 */
public final class Ae2Compat {
    private static final Map<SolarPanelBlockEntity, DeferredGridNode<CompoundTag, Ae2Host>> HOSTS = new WeakHashMap<>();

    private Ae2Compat() {
    }

    public static void bootstrap() {
        IInWorldGridNodeHost.LOOKUP.registerForBlockEntity(
                (panel, ignored) -> {
                    if (panel.getLevel() == null || panel.getLevel().isClientSide || panel.isRemoved()) return null;
                    initialize(panel);
                    return getLifecycle(panel).getHost();
                }, SunlightPower.SOLAR_PANEL_ENTITY);
        SunlightPower.LOGGER.info("已启用 AE2 Fabric 15.4.11 能源网络接口");
    }

    private static DeferredGridNode<CompoundTag, Ae2Host> getLifecycle(SolarPanelBlockEntity panel) {
        return HOSTS.computeIfAbsent(panel, key -> new DeferredGridNode<>(() -> new Ae2Host(key)));
    }

    public static void initialize(SolarPanelBlockEntity panel) {
        if (panel.getLevel() == null) return;
        if (panel.getLevel().isClientSide) {
            remove(panel);
            return;
        }
        getLifecycle(panel).initialize(callback -> GridHelper.onFirstTick(panel, ignored -> callback.run()));
    }

    public static long chargeItem(ItemStack stack, long amount) {
        if (!(stack.getItem() instanceof IAEItemPowerStorage storage)
                || !storage.getPowerFlow(stack).isAllowInsertion()) return 0;
        return Ae2EnergyConversion.charge(amount,
                value -> PowerUnits.TR.convertTo(PowerUnits.AE, value),
                value -> PowerUnits.AE.convertTo(PowerUnits.TR, value),
                (offered, simulate) -> storage.injectAEPower(stack, offered,
                        simulate ? Actionable.SIMULATE : Actionable.MODULATE));
    }

    public static void tick(SolarPanelBlockEntity panel) {
        var lifecycle = HOSTS.get(panel);
        if (lifecycle != null) lifecycle.getHost().refreshPowerAvailability();
    }

    public static boolean canChargeItem(ItemStack stack) {
        return stack.getItem() instanceof IAEItemPowerStorage storage
                && storage.getPowerFlow(stack).isAllowInsertion();
    }

    public static void remove(SolarPanelBlockEntity panel) {
        var lifecycle = HOSTS.remove(panel);
        if (lifecycle != null) lifecycle.remove();
    }

    public static void save(SolarPanelBlockEntity panel, CompoundTag tag) {
        var lifecycle = HOSTS.get(panel);
        if (lifecycle != null) {
            CompoundTag nodeTag = lifecycle.save();
            if (nodeTag != null) tag.put("Ae2Node", nodeTag.copy());
        }
    }

    public static void load(SolarPanelBlockEntity panel, CompoundTag tag) {
        if (panel.getLevel() != null && panel.getLevel().isClientSide) return;
        if (tag.contains("Ae2Node")) {
            getLifecycle(panel).load(tag.getCompound("Ae2Node").copy());
            initialize(panel);
        }
    }

    private static final class Ae2Host implements IInWorldGridNodeHost, IGridNodeListener<Ae2Host>,
            DeferredGridNode.Host<CompoundTag> {
        private final SolarPanelBlockEntity panel;
        private final IManagedGridNode managedNode;
        private final Ae2PowerStorage storage;

        private Ae2Host(SolarPanelBlockEntity panel) {
            this.panel = panel;
            this.storage = new Ae2PowerStorage(panel);
            this.managedNode = GridHelper.createManagedNode(this, this)
                    .setInWorldNode(true)
                    .setIdlePowerUsage(0)
                    .setExposedOnSides(EnumSet.complementOf(EnumSet.of(Direction.UP)))
                    .setVisualRepresentation(SunlightPower.SOLAR_PANEL)
                    // AE2 pulls energy through this service; there is no separate push-transfer path.
                    .addService(IAEPowerStorage.class, storage);
        }

        @Override
        public void create() {
            managedNode.create(panel.getLevel(), panel.getBlockPos());
        }

        private void refreshPowerAvailability() {
            // Run outside extractAEPower: AE2 forbids changes to providers during an extraction.
            managedNode.ifPresent(grid -> storage.availability.notifyIfAvailable(storage.availableOutput(),
                    () -> grid.postEvent(new GridPowerStorageStateChanged(storage,
                            GridPowerStorageStateChanged.PowerEventType.PROVIDE_POWER))));
        }

        @Override
        public IGridNode getGridNode(Direction direction) {
            return direction == Direction.UP ? null : managedNode.getNode();
        }

        @Override
        public AECableType getCableConnectionType(Direction direction) {
            return direction == Direction.UP ? AECableType.NONE : AECableType.SMART;
        }

        @Override
        public void onSaveChanges(Ae2Host owner, IGridNode node) {
            panel.setChanged();
        }

        @Override
        public void destroy() {
            managedNode.destroy();
        }

        @Override
        public CompoundTag save() {
            CompoundTag nodeTag = new CompoundTag();
            managedNode.saveToNBT(nodeTag);
            return nodeTag;
        }

        @Override
        public void load(CompoundTag tag) {
            managedNode.loadFromNBT(tag);
        }
    }

    private static final class Ae2PowerStorage implements IAEPowerStorage {
        private final SolarPanelBlockEntity panel;
        private final Ae2PowerAvailability availability = new Ae2PowerAvailability();

        private Ae2PowerStorage(SolarPanelBlockEntity panel) {
            this.panel = panel;
        }

        @Override
        public double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) {
            if (panel.getLevel() == null) return 0;
            double requestedAe = Math.max(0, multiplier.multiply(amount));
            long gameTime = panel.getLevel().getGameTime();
            SolarEnergyBuffer energy = panel.getEnergyBuffer();
            var quote = Ae2EnergyConversion.extraction(requestedAe, availableOutput(),
                    value -> PowerUnits.TR.convertTo(PowerUnits.AE, value),
                    value -> PowerUnits.AE.convertTo(PowerUnits.TR, value));
            double deliveredAe = quote.aePower();
            if (mode == Actionable.MODULATE) {
                long extracted = energy.extract(SolarEnergyBuffer.AE2_CHANNEL, gameTime, quote.storedUnits(),
                        SunlightPower.CONFIG.maxOutputPerSide, null);
                deliveredAe = Math.min(deliveredAe, PowerUnits.TR.convertTo(PowerUnits.AE, extracted));
            }
            availability.onExtraction(requestedAe, deliveredAe, mode == Actionable.SIMULATE);
            return multiplier.divide(deliveredAe);
        }

        private long availableOutput() {
            if (panel.getLevel() == null) return 0;
            return panel.getEnergyBuffer().getAvailableOutput(SolarEnergyBuffer.AE2_CHANNEL,
                    panel.getLevel().getGameTime(), SunlightPower.CONFIG.maxOutputPerSide);
        }

        @Override public double injectAEPower(double amount, Actionable mode) { return amount; }
        @Override public double getAEMaxPower() { return PowerUnits.TR.convertTo(PowerUnits.AE, panel.getCapacity()); }
        @Override public double getAECurrentPower() { return PowerUnits.TR.convertTo(PowerUnits.AE, panel.getEnergy()); }
        @Override public boolean isAEPublicPowerStorage() { return true; }
        @Override public AccessRestriction getPowerFlow() { return AccessRestriction.READ; }
        @Override public int getPriority() { return 0; }
    }
}
