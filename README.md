# Create: Sentry Mechanical Arm（非官方移植）

当前修复开发版本：**1.4.0-alpha.37**。目标环境：Minecraft 1.21.1 / NeoForge 21.1.228+ / Create 6.0.10 / TaCZ 1.2.0 / Java 21。当前本地开发运行环境使用 NeoForge 21.1.248 与 JEI 19.70.0.469。

**当前完整玩法、配置、制造成本和兼容边界见 [模组完整介绍](docs/MOD_GUIDE.md)。** 各阶段文档保留开发历史；现行功能以该介绍与当前源码为准。

两份第三方枪包的实测与限制见 [docs/GUNPACK_COMPATIBILITY.md](docs/GUNPACK_COMPATIBILITY.md)。

版本历史见 [CHANGELOG.md](CHANGELOG.md)，自动枪包弹壳第一阶段见 [docs/CASINGS_PHASE1.md](docs/CASINGS_PHASE1.md)。此前十个版本已按已保存源码快照建立本地 Git 归档标签；这是非官方修复开发分支。

一个 Minecraft NeoForge 模组，让 Create 动力臂能够装备 [TaCZ](https://github.com/tacz-dev/tacz)（Timeless and Classics: Zero）枪械，作为自动化防御炮塔使用。

## 功能特性

- **自动索敌** — 哨戒臂在范围内扫描敌对实体，锁定目标并以平滑关节运动追踪。
- **TaCZ 射击接入** — 处理半自动、连射、全自动、栓动和充能状态；特殊脚本与非标准模型仍可能存在兼容边界。
- **向量枪口计算** — 利用手臂关节角度 + 枪械模型骨骼数据精确计算子弹射出位置，远距离瞄准无偏差。
- **实体子弹武器适配** — 自动识别 RPG/火箭/榴弹等有实体模型子弹的武器，跳过冗余 tracer，只保留 TACZ 原生弹道。
- **火控系统** — 连接烈焰火控台，可跨多个哨戒臂统一管理目标白名单/黑名单。
- **聚焦瞄准** — 使用哨戒瞄准镜指定优先目标，所有已连接哨戒集中攻击。
- **弹药管理** — 为哨戒臂附加弹药箱实现自动补给，弹药可用时即时装填。
- **物理结构兼容** — 实现 **Sable**（Create: Aeronautics）姿态与子世界坐标变换，新增移动收集逻辑尚待当前版本游戏内验收。
- **静态 + Contraption 双状态** — 支持普通方块放置和 Create 动态结构（传送带/矿车/飞船）安装。
- **弹壳回收与再装填** — 通用识别枪包弹壳，口径模具制造新壳，无水流态火药按原方比例装药，单壳装配单发弹药；普通材料定量投放，JEI 按口径区分。旧模板链、底火与小火药已移除。现行成本见 [成本分摊](docs/COST_ALLOCATION.md)，自定义见 [制造与抛壳规则](docs/CUSTOM_MANUFACTURING.md)。
- **弹壳收集** — 动力臂优先存入相邻或同结构的 27 格弹壳收集器，余量落地；玩家默认背包收集，满包参考原版 /give 溢出。

## 前置模组

| 模组 | 要求 |
|-----------|----------|
| [NeoForge](https://neoforged.net) 21.1.228 | ✅ 必需 |
| [Create](https://modrinth.com/mod/create) 6.0.10（固定验证） | ✅ 必需 |
| [TaCZ](https://modrinth.com/mod/tacz) 1.2.0（官方） | ✅ 必需 |
| [Sable Companion](https://github.com/ryanhcode/sable-companion) 1.6.0 | 已内嵌；实际 Sable 为可选依赖 |

## 操作键位

| 操作 | 输入 |
|--------|-------|
| 放入枪械 | 手持枪械对准哨戒臂，使用 TaCZ 交互键（默认 O） |
| 取出枪械 | 右键已装枪的哨戒臂 |
| 附加弹药箱 | 手持弹药箱右键 |
| 聚焦瞄准目标 | 瞄准镜 + 左键目标 |
| 配置火控 | 手持剪贴板右键火控台 |
| 调整射程 | 空手在哨戒臂上滚轮 |

## 坐标系说明（Sable 飞船）

| 操作 | 坐标空间 |
|-----------|-----------------|
| 实体扫描 AABB | **世界**（从子世界投影） |
| 角度计算 | **世界**（枪口和目标都在世界空间） |
| 视线检测 clip | **子世界**（转换后执行 level.clip） |
| 子弹生成 | **世界**（FakePlayer 放置在投影后位置） |

## 技术架构

### 子弹位置计算

```
服务端 fireGun():
  普通枪械 → getMuzzlePosition(armLen)  → FP 移到枪口 - 眼高
  实体子弹 → 不移动 FP，保持 sync() 位置
  → operator.shoot() → EntityKineticBullet 构造函数
  → y = getY() + (getY() - yOld)/2 + getEyeHeight()

客户端 tracer:
  → calculateExactMuzzle() 从 TACZ BedrockGunModel.muzzleFlashPosPath
  → 完整变换链（关节 → 枪械 → 握持 → 缩放 → 枪口）
  → hasEntityBullet() 判断是否跳过
```

### 检测分类（`hasEntityBullet`）

| 类别 | 示例 | 行为 |
|------|------|------|
| 动能枪械 | 步枪/手枪/SMG/霰弹/机枪/狙击/迷你枪 | 向量枪口 + tracer |
| 实体子弹 | RPG/火箭/榴弹/发射器/非动能武器 | 固定位置，跳过 tracer |

## 开发

```bash
./gradlew build          # 编译打包
./gradlew runClient      # 启动游戏客户端
./gradlew runServer      # 启动专用服务器
```

## 版本历史

- **v1.3.4** — 向量枪口计算、Contraption 适配、hasEntityBullet 检测、渲染稳定性修复、Mixin 隔离 TACZ 动画状态机、无网络同步简化架构。
- **v1.0.0** — 正式版：Sable 兼容，坐标空间修复，全物品配方
- **v0.5.0** — Sable（Aeronautics 子世界）兼容，坐标空间修复
- **v0.4.0** — 初始 NeoForge 1.21.1 移植

## 许可

MIT © Euphy

开发执行约定：用户未明确要求时，修改完成后不运行检查、测试或开发客户端；安装包仅执行编译打包，并明确区分打包完成与运行验证。

当前制造成本分摊见 [COST_ALLOCATION.md](docs/COST_ALLOCATION.md)：新壳补足主金属预算，回收壳省去制壳材料。

自定义制造与抛壳统一入口见 [CUSTOM_MANUFACTURING.md](docs/CUSTOM_MANUFACTURING.md)，可直接启用的模板位于 [examples/manufacturing-rules](examples/manufacturing-rules)。明确自定义规则优先于自动推导；可配置掉落物、口径、数量及概率。

弹壳输出可通过 [弹壳收集器与玩家背包收集](docs/CASING_COLLECTION.md) 自动入库：静态动力臂用相邻收集器，动态结构用同结构收集器；玩家默认背包收集，可通过配置恢复地面抛壳。
