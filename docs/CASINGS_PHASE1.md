# 自动枪包弹壳：第一阶段（alpha.11）

## 已实现

新增 empty_casing 物品，通过持久化且可网络同步的 casing_ammo 组件保存 AmmoId。不同口径不能合堆，移除枪包后仍保留原 ID；名称优先读取枪包弹药译名，没有译名时显示 ID。

服务端从已加载弹药的 display/ammo 配置发现 shell.model 和 shell.texture。默认枪包共 25 种弹药，24 种有可用弹壳声明；火箭等排除。未知或没有 shell 声明的弹药默认不产出，可用数据包显式开启通用外观。发现过程仅使用 JSON，不在专用服务器加载客户端类。

客户端按物品的 AmmoId 查找当前 TaCZ ClientAmmoIndex，直接复用其弹壳模型和贴图，不复制完整子弹贴图。按模型原始几何范围近似居中、缩放。没有资源、枪包被移除或模型加载失败时，使用自有几何生成的通用开口黄铜壳；复杂骨骼旋转的边界不是精确包围盒。手持、GUI、地面使用相同身份与资源。资源重载与退出时清理适配缓存。

击发前记录可用弹药总量，成功 GunFirePostEvent 后确认同一把枪确有弹药减少，才产生一个可拾取 ItemEntity。半自动、自动、原生延迟连发与霰弹均按每次击发一次结算；不按弹丸数量计算。重复后置通知不会重复产出。取消、未完成、无实际扣弹、创造玩家和匹配的创造弹药盒不产生可回收弹壳。其他模组的无关联 FakePlayer 默认不接管。

玩家从眼部附近抛出，哨戒臂使用现有世界枪口姿态，因此可用于静态、动态、列车与 Sable。当前是近似抛出位置和相对前方向，不绑定精确枪械抛壳口骨骼，也没有等待手动拉栓/左轮退壳时机。保留 TaCZ 原有的纯视觉抛壳效果。

## 配置

当前存档 serverconfig/sentrymechanicalarm-server.toml：

```toml
[casings]
enabled = true
players = true
sentries = true
dropChance = 1.0
maxEntitiesPerChunk = 64
lifetimeTicks = 1200
```

独立于 ammo_crafting.enableAmmoCrafting。概率、数量上限和寿命用于控制负载；达到上限时跳过掉落，不影响射击耗弹。同一 tick 的多次产出也计入上限。建议保存后重启世界/服务器，确保客户端与服务器配置一致。新物品与组件注册使网络协议升至 6，双方必须一起更新。

覆盖规则：data/<弹药命名空间>/sentry_casings/<弹药路径>.json，例如 data/tacz/sentry_casings/9mm.json：

```json
{"enabled": false}
```

服务端 /reload 后刷新规则，移除覆盖包后恢复枪包发现结果。显式启用某弹药不代表实现了再装填配方。

## 验证与边界

服务端专项覆盖组件磁盘/网络往返、不同口径合堆限制、真实扣弹、取消与重复通知、主开关/玩家开关/概率/实体上限、同 tick 上限、创造玩家和无限弹药盒、与制作开关独立性。另用实际 ServerPlayer 原生射击三次，并复用 13 个哨戒臂原生枪械场景，比较实际成功击发与新生成弹壳数量；忽略存档载入的旧掉落物，隔离测试世界并清理测试实体。

客户端逐个读取默认弹药的弹壳模型，在 GUI、地面及第一/第三人称右手测试上下文检查调用矩阵保留，并生成真实渲染预览。重新运行客户端资源重载及原有 68 个标准枪械模型检查。

本阶段不包含：库存直接收集、弹壳再装填配方、createimmersivetacz 物品替换、复杂完整子弹模型的弹头自动拆分、枪械专属弹壳覆盖、物理结构完整滚转抛出方向、双独立客户端长期同步与任意第三方枪包验收。这些保留到后续阶段，不将它们描述为已完成。

## Git 与复现

- repair/release-history 保存上游基线、十个真实 ZIP 归档提交及统一更新日志。
- feature/automatic-casings 在该基线上实现本阶段；不向上游远程推送。
- 服务端：gradlew.bat runServer -PsentrySmoke=phase3 -PcasingSmoke，显式检查 CASING RESULT 与 PHASE3 RESULT。
- 重载：gradlew.bat runServer -PsentrySmoke=reload，显式检查 RELOAD RESULT。
- 客户端：gradlew.bat runClient -PsentryClientSmoke -PsentryClientReloadSmoke -PcasingClientSmoke。
- 正常 clean build 不包含测试类、测试枪械数据、测试世界或截图。

## alpha.11 本轮验收记录

- 弹壳专项 15 项、原生枪械专项 68 项全部通过；隔离世界记录 544 个符合条件的新击发与 544 个新弹壳。另有三个实际 ServerPlayer 原生击发包含在其中。
- 静态回归 31 项、弹药库回归 42 项、列车带弹药库回归 46 项通过。
- 静态/动态共享库存/Sable 的弹药库场景：1680 次符合条件击发对应 1680 个新弹壳；列车场景：717 次对应 717 个。
- 重载专项 27 项通过，包含弹壳规则禁用与移除覆盖后的恢复。
- 客户端 24 套原生弹壳模型/贴图、26 个预览样本（含火箭无壳回退和已移除枪包回退）检查通过，实际截图已人工目视检查；原有 68 个枪械模型与资源重载检查通过。
- 本轮没有重跑三进程完整重启，也没有完成双独立客户端测试。上述物理场景仅覆盖既有固定夹具，不代表高速自由飞行与任意嵌套结构的全面验收。
