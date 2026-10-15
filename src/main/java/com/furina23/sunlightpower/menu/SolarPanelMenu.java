package com.furina23.sunlightpower.menu;

import com.furina23.sunlightpower.SunlightPower;
import com.furina23.sunlightpower.block.entity.SolarPanelBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class SolarPanelMenu extends AbstractContainerMenu {
    private static final int PLAYER_INVENTORY_START = 1;
    private final ContainerLevelAccess access;
    private final ContainerData data;

    public SolarPanelMenu(int syncId, Inventory playerInventory, SolarPanelBlockEntity panel) {
        this(syncId, playerInventory, panel, ContainerLevelAccess.create(panel.getLevel(), panel.getBlockPos()),
                new ContainerData() {
                    @Override public int get(int index) {
                        return switch (index) {
                            case 0 -> (int) Math.min(Integer.MAX_VALUE, panel.getEnergy());
                            case 1 -> (int) Math.min(Integer.MAX_VALUE, panel.getCapacity());
                            case 2 -> (int) Math.min(Integer.MAX_VALUE, panel.getCurrentGeneration());
                            case 3 -> (int) Math.min(Integer.MAX_VALUE, SunlightPower.CONFIG.maxOutputPerSide);
                            case 4 -> (int) Math.min(Integer.MAX_VALUE, SunlightPower.CONFIG.generationPerTick);
                            default -> 0;
                        };
                    }
                    @Override public void set(int index, int value) { }
                    @Override public int getCount() { return 5; }
                });
    }

    private SolarPanelMenu(int syncId, Inventory playerInventory, Container container,
                           ContainerLevelAccess access, ContainerData data) {
        super(SunlightPower.SOLAR_PANEL_MENU, syncId);
        this.access = access;
        this.data = data;
        checkContainerSize(container, 1);
        addSlot(new ChargeSlot(container, 0, 151, 9));
        addPlayerInventory(playerInventory);
        addDataSlots(data);
    }

    public static SolarPanelMenu fromClient(int syncId, Inventory inventory, FriendlyByteBuf buffer) {
        buffer.readBlockPos();
        return new SolarPanelMenu(syncId, inventory, new SimpleContainer(1), ContainerLevelAccess.NULL,
                new net.minecraft.world.inventory.SimpleContainerData(5));
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 98 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 156));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack empty = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return empty;
        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();
        if (index == 0) {
            if (!moveItemStackTo(original, PLAYER_INVENTORY_START, slots.size(), true)) return empty;
        } else if (!moveItemStackTo(original, 0, PLAYER_INVENTORY_START, false)) {
            return empty;
        }
        if (original.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, SunlightPower.SOLAR_PANEL);
    }

    public int energy() { return data.get(0); }
    public int capacity() { return data.get(1); }
    public int generation() { return data.get(2); }
    public int output() { return data.get(3); }
    public int peakGeneration() { return data.get(4); }

    private static final class ChargeSlot extends Slot {
        private ChargeSlot(Container container, int index, int x, int y) { super(container, index, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return stack.getCount() == 1 && SunlightPower.canChargeItem(stack); }
        @Override public int getMaxStackSize() { return 1; }
        @Override public int getMaxStackSize(ItemStack stack) { return 1; }
    }
}
