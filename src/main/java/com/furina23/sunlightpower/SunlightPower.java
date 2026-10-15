package com.furina23.sunlightpower;

import com.furina23.sunlightpower.block.SolarPanelBlock;
import com.furina23.sunlightpower.block.entity.SolarPanelBlockEntity;
import com.furina23.sunlightpower.compat.Ae2Compat;
import com.furina23.sunlightpower.compat.FabricEnergyCompat;
import com.furina23.sunlightpower.config.SunlightConfig;
import com.furina23.sunlightpower.item.SolarPanelItem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import com.furina23.sunlightpower.menu.SolarPanelMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SunlightPower implements ModInitializer {
    public static final String MOD_ID = "sunlight_power";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final SunlightConfig CONFIG = SunlightConfig.load();

    public static final Block SOLAR_PANEL = Registry.register(BuiltInRegistries.BLOCK, id("solar_panel"),
            new SolarPanelBlock(BlockBehaviour.Properties.copy(Blocks.GLASS)
                    .sound(SoundType.METAL).noOcclusion().strength(0.3f, 6.0f)));
    public static final Item SOLAR_PANEL_ITEM = Registry.register(BuiltInRegistries.ITEM, id("solar_panel"),
            new SolarPanelItem(SOLAR_PANEL, new Item.Properties()));
    public static final BlockEntityType<SolarPanelBlockEntity> SOLAR_PANEL_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("solar_panel"),
            BlockEntityType.Builder.of(SolarPanelBlockEntity::new, SOLAR_PANEL).build(null));
    public static final net.minecraft.world.inventory.MenuType<SolarPanelMenu> SOLAR_PANEL_MENU = Registry.register(
            BuiltInRegistries.MENU, id("solar_panel"),
            new ExtendedScreenHandlerType<>(SolarPanelMenu::fromClient));
    public static final CreativeModeTab CREATIVE_TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("main"),
            FabricItemGroup.builder().icon(() -> new ItemStack(SOLAR_PANEL_ITEM))
                    .title(Component.translatable("itemGroup.sunlight_power.main"))
                    .displayItems((context, entries) -> entries.accept(SOLAR_PANEL_ITEM)).build());

    @Override
    public void onInitialize() {
        LOGGER.info("阳光动力正在加载，基础发电量 {} / tick，储能容量 {}", CONFIG.generationPerTick, CONFIG.capacity);
        if (FabricLoader.getInstance().isModLoaded("team_reborn_energy")) {
            FabricEnergyCompat.bootstrap();
        }
        if (FabricLoader.getInstance().isModLoaded("ae2")) {
            Ae2Compat.bootstrap();
        }
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    public static long transferToFabric(SolarPanelBlockEntity panel, net.minecraft.core.Direction side, long amount) {
        if (!FabricLoader.getInstance().isModLoaded("team_reborn_energy") || amount <= 0) return 0;
        return FabricEnergyCompat.transfer(panel, side, amount);
    }

    public static long chargeSlot(SolarPanelBlockEntity panel, long amount) {
        amount = Math.min(amount, panel.getEnergy());
        if (amount <= 0 || panel.getChargingStack().isEmpty()) return 0;
        if (FabricLoader.getInstance().isModLoaded("team_reborn_energy")) {
            long result = FabricEnergyCompat.chargeItem(panel, amount);
            if (result > 0) return result;
        }
        if (FabricLoader.getInstance().isModLoaded("ae2")) {
            long result = Ae2Compat.chargeItem(panel.getChargingStack(), amount);
            if (result > 0) panel.setEnergy(panel.getEnergy() - result);
            return result;
        }
        return 0;
    }

    public static boolean canChargeItem(ItemStack stack) {
        if (FabricLoader.getInstance().isModLoaded("team_reborn_energy")
                && FabricEnergyCompat.canChargeItem(stack)) return true;
        return FabricLoader.getInstance().isModLoaded("ae2") && Ae2Compat.canChargeItem(stack);
    }

    public static void initializeAe2(SolarPanelBlockEntity panel) {
        if (FabricLoader.getInstance().isModLoaded("ae2")) {
            Ae2Compat.initialize(panel);
        }
    }

    public static void tickAe2(SolarPanelBlockEntity panel) {
        if (FabricLoader.getInstance().isModLoaded("ae2")) {
            Ae2Compat.tick(panel);
        }
    }

    public static void removeAe2(SolarPanelBlockEntity panel) {
        if (FabricLoader.getInstance().isModLoaded("ae2")) {
            Ae2Compat.remove(panel);
        }
    }

    public static void saveAe2(SolarPanelBlockEntity panel, net.minecraft.nbt.CompoundTag tag) {
        if (FabricLoader.getInstance().isModLoaded("ae2")) {
            Ae2Compat.save(panel, tag);
        }
    }

    public static void loadAe2(SolarPanelBlockEntity panel, net.minecraft.nbt.CompoundTag tag) {
        if (FabricLoader.getInstance().isModLoaded("ae2")) {
            Ae2Compat.load(panel, tag);
        }
    }
}
