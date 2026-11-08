# 自定义制造与抛壳模板

将本目录或打包 zip 放入世界 datapacks 中并启用，然后执行 /reload。唯一默认生效文件为 data/tacz/sentry_manufacturing/9mm.json，示范 9mm 的完整规则。examples/ 目录中的其他 JSON 只供参考，不会被游戏加载。

更换口径时，同时更改 data 下的命名空间、文件路径以及 drops.casing（若使用该字段）。例如 tacz:556x45 对应 data/tacz/sentry_manufacturing/556x45.json。枪包弹药 ID 含目录时保留完整目录。

examples/custom-item-drop.json 示范掉落普通物品；examples/another-casing-drop.json 示范抛出其他口径弹壳。映射只改变掉落物，不自动更改原弹药的再装填输入，避免共享弹壳导致配方争抢。

完整原生 Create 配方覆盖 > 此统一规则 > 旧 sentry_reloading / sentry_casings 入口 > 自动推导 > 无来源通用铜壳。无效或显式禁用的简化规则不回退。总开关和有限弹药要求仍保留。完整字段见 [自定义制造与抛壳](../../docs/CUSTOM_MANUFACTURING.md)。
