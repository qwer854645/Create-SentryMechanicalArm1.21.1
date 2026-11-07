# 弹壳开关（alpha.18）

## 启动阶段总开关

文件：游戏/服务器实例的 `config/sentrymechanicalarm-startup.toml`。

```toml
[casings]
enabled = true
```

默认 true。false 时不向物品注册表注册 `sentrymechanicalarm:empty_casing`，创造栏不添加弹壳，发现逻辑和掉落逻辑跳过弹壳功能。AmmoId 数据组件仍保留其 codec，其他物品注册不受影响。

此开关在注册前由 NeoForge STARTUP 配置读取，必须完全重启客户端/服务端，且两端配置一致。关闭后旧弹壳的物品 ID 不再存在，已有弹壳物品无法在关闭期间保留；有旧存档时应先备份。仅想暂停掉落并保留旧弹壳，应使用下列来源开关，或旧版运行时 enabled 开关。

## 三类射手的掉落开关

文件：实例的 `config/sentrymechanicalarm-server.toml`；如使用 NeoForge 世界级 serverconfig 覆盖，应修改实际生效的同名配置。服务端控制掉落，客户端渲染不能自行产生物品。

```toml
[casings]
players = true
sentries = true
others = false
```

- players：真实玩家，默认开启。创造和旁观玩家不产出可回收弹壳。
- sentries：本模组动力臂，包括动态结构、列车和船体复用的动力臂射手，默认开启。与 players 独立，即使玩家开关关闭，动力臂仍可按自身开关产出。
- others：其他 LivingEntity 生物，默认关闭。打开后，使用 TaCZ 原生射击事件且确实扣除弹药的生物可以产出。此开关不会给生物发枪或增加射击 AI，不支持只播放特效而不扣弹的行为。其他模组未关联本模组动力臂的 FakePlayer 不归入此项，仍排除。

三类仍统一受掉落概率、区块数量上限、寿命、弹药 eligibility 规则约束。未扣弹、重复通知、有限弹药未实际消耗、玩家/动力臂的匹配创造弹药箱，以及生物可见装备槽中的匹配创造弹药箱，都不能产生可回收弹壳。其他模组私有库存无法通用枚举；仍以原生射击事件和实际枪内扣弹为必要条件。

## 与旧版本兼容

旧 `sentrymechanicalarm-server.toml` 中 `[casings].enabled` 保留为兼容用的运行时掉落总闸。它不会取消物品注册。要实现本次要求的“不注册弹壳”，必须修改新增的 **startup** 文件。这样不会把老配置中“临时停掉落”变成删除注册物品的行为。

没有修改子弹制造开关，也没有修改当前 38% 的图标裁剪设置。

## 验证

无客户端启动。专用服务器分别以启动总开关 true/false 启动两个全新进程，验证真实注册表成员、创造栏构建、禁用物品工厂、关闭后的射击钩子和资源发现。开启时验证玩家/动力臂/其他生物类别隔离、无耗弹拒绝、装备中创造弹药箱拒绝；实际 Husk 通过 TaCZ 原生 API 发射三发，其中两发在 others=true 时产出，另一发在 others=false 时不产出。另运行原有枪械与弹壳回归。
