# alpha.2：修复一直无法开火的 TaCZ 时钟兼容问题

2026-11-07，用户在 alpha.1 客户端报告哨戒臂似乎不开枪。

## 根因与修改

TaCZ 1.2.0 的 GunTime 使用独立、可随暂停停止的时钟。哨戒臂原先却把 System.currentTimeMillis()
写入 ShooterDataHolder.drawTimestamp，导致 TaCZ 计算出的拔枪冷却时间异常巨大，shoot 返回 IS_DRAWING。

- SentryFakePlayer.sync 改用 GunTime.nowMillis() 设置拔枪完成时间。
- 拉栓与过热处理中，读取 TaCZ boltTimestamp、heatTimestamp 时使用同一个 GunTime。
- 视觉特效自身的系统时间戳未混用 TaCZ 时间字段，继续保留。
- 版本升级为 1.4.0-alpha.2。

## 实际接口回归测试

新增 src/smoke/java，只有 `-PsentrySmoke` 时才加入编译，正常构建不会包含测试入口。

执行 `./gradlew.bat -PsentrySmoke runServer`，测试服务器启动后自动运行并退出。
检查日志必须出现 SHOOT_CLOCK_SMOKE PASS；测试会捕获异常写 FAIL，不能仅依赖 Gradle 退出码判断通过。

在真实 Minecraft/NeoForge/TaCZ 服务端中，构造未放置到世界的哨戒臂及其 FakePlayer，使用默认 Glock 17：

1. 调用实际 SentryFakePlayer.sync 初始化枪械；准备已装填弹药，并使普通首次射击冷却已结束。
2. 还原旧时间写法，调用真实 operator.shoot，断言返回 IS_DRAWING。
3. 恢复修复后的时间戳，再调用 operator.shoot。
4. 断言返回 SUCCESS，监听到真实 GunFireEvent，弹匣加膛内总弹药由 11 减到 10。
5. 移除 FakePlayer，自动关闭测试服务器。

已得到：`SHOOT_CLOCK_SMOKE PASS before=IS_DRAWING after=SUCCESS fired=true ammo=11->10`。

该测试覆盖本次直接阻止开火的兼容错误，不等于完整验证放置炮塔的索敌、视线、姿态、供能或动态结构。
拉栓、过热的时间修正经过编译检查，尚未分别进行玩法回归。
