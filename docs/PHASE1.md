# 第一阶段：开发基线

日期：2026-11-07。此分支是整体修复工作的开发起点，不是稳定修复版。

## 固定版本

- Minecraft 1.21.1；NeoForge 21.1.228；Java 21；Gradle Wrapper 8.12。
- Create 6.0.10：`maven.modrinth:create:6.0.10+mc1.21.1`。
- 官方 TaCZ 1.2.0：`curse.maven:timeless-and-classics-zero-1028108:9454361`。
- 本模组开发版本：1.4.0-alpha.1。
- 沿用依赖：Ponder 1.0.82、Flywheel 1.0.6、Sable Companion 1.6.0。
- JEI 19.21.0.247 是目前开发运行环境的一部分；未验证移除后的最小环境。

## 来源

- 移植仓库： https://github.com/pihoue/Create-SentryMechanicalArm1.21.1
- 基线提交：`f1592ee50f9731661b93ad1a8542a19d5d410cff`。
- 分支：`repair/phase1-baseline`。
- PR #7 参考提交：`5fbcf01ab5030c5e78bc280cacabf82734873f10`，仅比较，未合并。
- 原版默认分支 1.20.1/0.3.0：`76624b14f4b6e1c120358b713c4b80676f6ceb72`。
  该公开源码 gradle.properties 标识 0.3.3，不能当作 CurseForge 0.3.6 的完整对应源码。
- TaCZ 1.2.0 官方标签：`1.2.0-neoforge-1.21.1`，提交 `7f5b112301afeb41a9f98b0cce2e1b2a75651511`。

## 发布包核对

原始文件： https://www.curseforge.com/minecraft/mc-mods/create-sentry-mechanical-arm-1/files/8897599

SHA-256：`9bcf9451486d7f8940d144bf876991275f98b1e3a797f053a148a3339b3ae94f`。

使用 Java 21 javap 检查 DynamicRecipeManager 字节码：发布包已有 uniqueRecipeId、encodeIdPart、
DataComponentIngredient.of 与保留 Ingredient 的装配步骤。之前根据提交日期推测这些修复可能未发布，
已被实物检查纠正。此检查不代表整个发布包与当前源码逐字节或语义完全相同。

另核对了 125 个源资源文件（不含已修改的模组元数据）：77 个与发布包逐字节一致，
另外 48 个归一化换行及首尾空白后相同；没有发现这批资源内容缺失或实质变更。

官方 TaCZ 文件 SHA-256：`b01384895b51a32e65ae9e2de6b99fa92b2f9285c2dafcac2112932fc93bff21`。

## 本阶段改动

1. 将非官方 TaCZ 1.1.8-r2 替换为用户指定的官方 1.2.0；Create/TaCZ 坐标集中到版本目录。
2. 对齐原有两处不一致的 NeoForge 版本；实际构建继续使用原版本目录中的 21.1.228。
3. 最小声音适配：使用 SoundData，保留音量、音调及随机范围，支持 TaCZ ogg 与 SoundEvent 回退。
   未改射击、索敌、弹药和配方算法。
4. 使用单独 alpha 版本号，并声明 TaCZ 必需。当前 alpha 仅声明支持 Create 6.0.10 和 TaCZ 1.2.0。
5. 在服务器运行配置中添加 --nogui。不要用 Gradle `runServer --args=--nogui` 覆盖 DevLaunch 参数。
6. 添加可选代理构建脚本；没有将本机 JDK 路径或代理端口写入仓库配置。

## 已验证

- 未适配源码在目标 TaCZ 版本下产生 10 个 Java 编译错误，全部位于 ArmSoundHelper。
- 声音适配后 `build` 成功；原项目没有测试源码，Gradle test 为 NO-SOURCE。
- 最终通过仓库 Wrapper 和 scripts/build-baseline.ps1 再次执行 clean build，成功生成 alpha JAR。
- 开发专用服务器加载 Create 6.0.10、TaCZ 1.2.0、本模组 alpha，达到 `Done`。
- TaCZ 默认枪包载入；模组日志报告注入 75 条动态配方、RecipeManager 中本模组配方共 80 条。
  此处仅验证加载，未验证机器能逐条加工。
- 服务端冒烟测试结束后停止测试 JVM；这是临时开发世界，不使用玩家存档。
- 最终 JAR 未包含仓库根目录下的 com/tacz 或 squeek 预编译 class。

## 尚未验证

- 客户端加载、声音实际播放、渲染、命中、装填与物品守恒。
- 玩家连接、多人同步、区块重载、旧存档迁移、/reload。
- 第三方枪包、动态结构、Sable 飞船与发布 JAR 的独立整合环境。
- 未使用原版 0.3.6 JAR 作为行为对照；公开默认分支比它旧。

## 复现构建

安装 Java 21，使用仓库 Wrapper：`./gradlew.bat clean build`。
Windows 可使用：`./scripts/build-baseline.ps1 -JavaHome '<Java 21目录>' -UseWindowsProxy`。
代理是可选参数；通过注册表读取本机配置，无需编辑 gradle.properties。

服务端：`./gradlew.bat runServer`。需要代理时给 Wrapper 传递标准 http/https proxyHost/proxyPort JVM 参数。
开发服务器不应暴露到公网。客户端：`./gradlew.bat runClient`，尚未运行验证。
详细缺陷及复现步骤见 ISSUES.md。
