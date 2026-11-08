# Create: Sentry Mechanical Arm

Create: Sentry Mechanical Arm 的非官方 Minecraft 1.21.1 NeoForge 修复分支。基于 [pihoue 的 1.21.1 移植版](https://github.com/pihoue/Create-SentryMechanicalArm1.21.1) 继续开发。让哨戒动力臂装备 TaCZ 枪械自动防御，并将弹药制造、补给和弹壳回收接入 Create 物流。

当前版本：**1.4.0-alpha.41**，建议作为 Alpha 测试版使用。复杂枪包、多人同步和长期运行仍存在兼容边界。

## 运行环境

- Minecraft 1.21.1、Java 21、NeoForge 21.1.228 或以上。
- Create **6.0.10**、官方 TaCZ **1.2.0**，当前依赖声明固定这两个版本。
- JEI 可选；Create JEI Compat 1.0.3 仅放客户端，不能放独立服务端。
- Sable／Create: Aeronautics 可选；Sable Companion 1.6.0 已内嵌。

客户端与服务端均需安装本模组，使用相同版本。详情见 [安装与升级](docs/RELEASE_INSTALL.md)。

## 功能

- 哨戒动力臂自动索敌，支持 TaCZ 枪械模式、弹药盒与弹药库补给。
- 火控台、目标名单及哨戒瞄准镜提供协同攻击。
- 支持 Create 移动结构、列车，以及 Sable 结构中的射击与同结构收集。
- 按枪包数据识别弹壳与口径，使用模具制壳、流态火药装药和单发再装填。
- 动力臂将射后产物优先送入相邻或同结构收集器，玩家默认存入背包。
- 数据包可覆盖制造、抛壳类型、数量及概率，自定义优先于自动推导。

持枪对准动力臂，使用 TaCZ 交互键（默认 O）装枪。完整操作、配置及边界见 [模组使用说明](docs/MOD_GUIDE.md)。

## 文档

- [制造成本规则](docs/COST_ALLOCATION.md)
- [自定义制造与抛壳](docs/CUSTOM_MANUFACTURING.md)
- [数据包示例](examples/manufacturing-rules)
- [构建与测试入口](docs/BUILDING.md)

## 构建

使用 Java 21：

```sh
./gradlew build
```

输出位于 build/libs/。源码仓库保留必要的模型、贴图、思索结构、测试源码和构建工具；运行目录、依赖缓存、发布产物及开发记录不纳入版本控制。

## 许可

MIT，详见 [LICENSE](LICENSE)。
