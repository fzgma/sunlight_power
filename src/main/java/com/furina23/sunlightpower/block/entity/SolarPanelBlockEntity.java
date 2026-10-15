package com.furina23.sunlightpower.block.entity;

import com.furina23.sunlightpower.SunlightPower;
import com.furina23.sunlightpower.logic.SolarGeneration;
import com.furina23.sunlightpower.logic.SolarEnergyBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Container;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import com.furina23.sunlightpower.menu.SolarPanelMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class SolarPanelBlockEntity extends BlockEntity implements Container, ExtendedScreenHandlerFactory {
    private final SolarEnergyBuffer energy = new SolarEnergyBuffer(this::setChanged);
    private long currentGeneration;
    private ItemStack chargingStack = ItemStack.EMPTY;
    private boolean interactionRequested;

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(SunlightPower.SOLAR_PANEL_ENTITY, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SolarPanelBlockEntity panel) {
        panel.tickServer(level);
    }

    private void tickServer(Level level) {
        boolean skyVisible = level.canSeeSky(worldPosition.above())
                && level.getBlockState(worldPosition.above()).isAir();
        long generated = SolarGeneration.calculate(level.getDayTime(), skyVisible,
                level.isRaining(), level.isThundering(), SunlightPower.CONFIG.generationPerTick,
                SunlightPower.CONFIG.rainMultiplier, SunlightPower.CONFIG.thunderMultiplier);
        currentGeneration = generated;
        energy.addGeneration(generated, getCapacity());
        if (getEnergy() > 0) {
            SunlightPower.chargeSlot(this, Math.min(SunlightPower.CONFIG.chargingSlotRate, getEnergy()));
            for (Direction direction : Direction.values()) {
                if (direction == Direction.UP) {
                    continue;
                }
                SunlightPower.transferToFabric(this, direction,
                        Math.min(SunlightPower.CONFIG.maxOutputPerSide, getEnergy()));
            }
        }
        SunlightPower.tickAe2(this);
        interactionRequested = false;
    }

    public long getEnergy() {
        return energy.getAmount();
    }

    public SolarEnergyBuffer getEnergyBuffer() {
        return energy;
    }

    public long getCurrentGeneration() {
        return currentGeneration;
    }

    public long getCapacity() {
        return SunlightPower.CONFIG.capacity;
    }

    public void setEnergy(long value) {
        energy.setAmount(value, getCapacity());
    }

    public ItemStack getChargingStack() {
        return chargingStack;
    }

    public void setChargingStack(ItemStack stack) {
        chargingStack = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        setChanged();
    }

    @Override public int getContainerSize() { return 1; }
    @Override public boolean isEmpty() { return chargingStack.isEmpty(); }
    @Override public ItemStack getItem(int slot) { return slot == 0 ? chargingStack : ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot, int amount) {
        if (slot != 0 || chargingStack.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = chargingStack.split(Math.min(1, amount));
        setChanged();
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        if (slot != 0) return ItemStack.EMPTY;
        ItemStack result = chargingStack;
        chargingStack = ItemStack.EMPTY;
        return result;
    }
    @Override public void setItem(int slot, ItemStack stack) { if (slot == 0) setChargingStack(stack); }
    @Override public void clearContent() { chargingStack = ItemStack.EMPTY; setChanged(); }
    @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this, player); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == 0 && stack.getCount() == 1 && SunlightPower.canChargeItem(stack);
    }
    @Override public Component getDisplayName() { return Component.translatable("block.sunlight_power.solar_panel"); }
    @Override public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
        return new SolarPanelMenu(syncId, inventory, this);
    }
    @Override public void writeScreenOpeningData(ServerPlayer player, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(worldPosition);
    }

    public void markInteractionRequested() {
        interactionRequested = true;
    }

    public boolean consumeInteractionRequest() {
        boolean requested = interactionRequested;
        interactionRequested = false;
        return requested;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("Energy", getEnergy());
        if (!chargingStack.isEmpty()) {
            tag.put("ChargingStack", chargingStack.save(new CompoundTag()));
        }
        SunlightPower.saveAe2(this, tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy.setAmount(tag.getLong("Energy"), getCapacity());
        chargingStack = tag.contains("ChargingStack") ? ItemStack.of(tag.getCompound("ChargingStack")) : ItemStack.EMPTY;
        if (chargingStack.getCount() > 1) {
            chargingStack.setCount(1);
        }
        SunlightPower.loadAe2(this, tag);
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        SunlightPower.initializeAe2(this);
    }

    @Override
    public void setRemoved() {
        SunlightPower.removeAe2(this);
        super.setRemoved();
    }

    public void dropContents() {
        if (level != null && !chargingStack.isEmpty()) {
            Block.popResource(level, worldPosition, chargingStack);
            chargingStack = ItemStack.EMPTY;
        }
    }
}
