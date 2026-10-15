# 阳光动力

Minecraft 1.20.1 Fabric 太阳能发电模组。

Mod ID：`sunlight_power`

作者：`Furina-23`

## 开发环境

Minecraft 1.20.1、Fabric Loader 0.19.5、Fabric API 0.92.12、Java 17。

## 构建

```text
./gradlew build
```

请将 `JAVA_HOME` 指向 Java 17；现有 Gradle Wrapper 为 8.8，构建工具本身也需要使用兼容的 Java 运行。

## 开源协议

本项目使用 GNU General Public License v3.0 或更高版本，详见 `LICENSE`。

## UI 贴图来源与许可

本项目太阳能板 UI 贴图源自 [SolarFluxReborn](https://github.com/dragon-forge/SolarFluxReborn) GitHub 仓库中的太阳能板 UI 贴图。已获得原作者对这些贴图进行使用和修改的明确许可；本项目在此注明来源。

## 发电规则

太阳能板在白天且上方为空气、可见天空时发电。晴天使用 `generationPerTick` 作为基准；雨天乘以 `rainMultiplier`；雷暴优先使用 `thunderMultiplier`，不与雨天倍率叠乘。倍率计算结果向下取整为整数能量。

储能达到 `capacity` 后停止接收发电量。Fabric 能量接口每侧每 tick 的主动输出与外部抽取共享 `maxOutputPerSide` 上限；顶部不输出能量。AE2 网络另有同值的每 tick 输出额度，物品充电受 `chargingSlotRate` 限制。

内部储能使用整数单位。AE2 的小数能量请求按实际请求供给，扣除储能时向上取整，单次可能损耗不足一个储能单位；模拟请求不会扣除能量。

## 太阳能板界面

右上角是唯一的物品充电槽，每次可放入一件受已安装能量兼容模组支持的可充电物品；鼠标悬停空槽时会显示用途提示。左侧显示充电槽是否放有物品，状态文字不代表物品正在接受能量。界面没有升级槽，下方槽位为玩家背包。
