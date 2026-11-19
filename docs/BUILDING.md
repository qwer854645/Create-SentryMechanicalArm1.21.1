# 构建与测试入口

## 构建

需要 Java 21。使用仓库自带的 Gradle Wrapper，依赖版本由 gradle.properties、gradle/libs.versions.toml 和 build.gradle.kts 管理。

```sh
./gradlew clean build
```

首次构建需要下载依赖。产物位于 build/libs/，普通构建不会编入 src/smoke 下的集成测试。gradle/wrapper/gradle-wrapper.jar 是启动 Wrapper 的必要文件，随源码保留。

## 开发运行

```sh
./gradlew runClient
./gradlew runServer -PwithoutJei -PdevRunDirectory=run-server
```

可用 -PdevRunDirectory 指定独立运行目录，-PdevNeoForgeVersion 覆盖开发加载器版本，-PdevJeiJar 指定本地 JEI JAR。机器专属路径不写入仓库的 gradle.properties。客户端可选模组不应复制进独立服务端目录，尤其是 Create JEI Compat 1.0.3。实际 Sable、航空学和枪包需要自行安装，不随源码分发。

## 集成测试

src/smoke 是可重复执行的测试代码，不是测试结果归档。通过显式 Gradle 属性启用：

```sh
./gradlew runServer -PsentrySmoke=phase2 -PwithoutJei -PdevRunDirectory=verification/server
./gradlew runServer -PsentrySmoke=release -PwithoutJei -PdevRunDirectory=verification/release
```

服务端还提供 phase3、compat、train、depot、restart、gunpacks、randompacks、casingconfig、reloading 等测试入口；部分入口需要额外模组、枪包、测试属性或连续重启。入口参数以 build.gradle.kts 及对应测试类为准。客户端入口为 -PsentryClientSmoke，思索检查另加 -PponderSmoke。加 `-PtaczHotfixSmoke` 可检查 TaCZ 共用按键、独立交互键和手柄输入的实际装枪及物品消耗；应使用独立测试世界副本，该检查会清空测试玩家背包。

枪械制造可用 `./gradlew runServer -PsentrySmoke=gunassembly -PwithoutJei -PdevRunDirectory=verification/gun-assembly-server`。该入口需要独立测试目录中的 EULA 和枪包，检查所有生成序列的原生匹配与加工结果，再以真实机械手、冲压机和锯床分步加工全部零件及代表枪械，包含不消耗工具的目标复制、原枪械配方移除／恢复与数据重载。夹具负责在机器之间搬运物品，不覆盖连续传送带物流。结果以 `GUN_ASSEMBLY RESULT` 的 failed=0 为准。

测试会创建方块、实体、世界和数据包，应使用独立目录，不要指向游玩存档。只有 Gradle 退出成功不足以判断通过，还应核对日志中的对应 RESULT 及 failed=0。

## 资源与发行包工具

### 机匣选择界面试用

准备独立的测试世界，默认目录为 run/saves/receiver-gui-trial。执行 `./gradlew runClient -PsentryGuiTrial` 可自动进入该世界，检查目标隔离、机匣转换／退回、机械手复制和第一道装配工序，再打开选择界面并由游戏自身截图。可用 `-PguiTrialWorld=<测试世界目录名>` 指定其他测试副本，不应指向游玩存档。

此入口仅编入 src/guiTrial/java；普通构建不包含该代码。日志中 `GUN_GUI_TRIAL RESULT PASS` 表示这些简单检查通过，不代表整条实体生产线验收。

- scripts/update-ponder-resources.py：生成思索预置结构。
- scripts/normalize-ponder-empty-items.py：规范结构中的旧物品 NBT。
- scripts/update-collector-model.py：生成收集器模型。
- scripts/verify-release-artifact.py：检查实际发行 JAR 的版本、许可证、资源和测试类隔离。
- scripts 下的 PowerShell 文件提供构建及专项测试包装入口。

运行目录、依赖缓存、截图、测试日志、版本开发记录和发布产物保留在本地，不纳入 Git。Python 工具需要 Python 3.11 或以上。
