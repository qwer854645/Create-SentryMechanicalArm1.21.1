# CurseForge 随机枪包兼容测试（alpha.13）

日期：2026-11-07。环境：Minecraft 1.21.1、NeoForge 21.1.228、Create 6.0.10、官方 TaCZ 1.2.0、Java 21。结论限定于以下文件和抽样范围。

## 抽样方法与文件

从 CurseForge 搜索得到七个 TaCZ 项目，使用 PowerShell Get-Random，种子 512317632，生成随机顺序：Resident Evil、Spearhead、Enlisted WW2、Metis、Destiny、EOS、Khan Oldguns。这是在七个候选中的随机抽样，不是全站均匀随机。

- Resident Evil：RE_Gunpack_v1.1.5-hotfix_tacz1.1.8.zip，文件 9038229。测试的是项目最新的 1.20.1 文件，属于跨版本边界样本。项目另有标注 1.21.1 的 1.1.3 文件 7850230，说明要求 Gunsmith Lib；本轮没有运行该组合，不能将结果推及它。来源：https://www.curseforge.com/minecraft/customization/tacz-re-gunpack-resident-evil/files/9038229
- Spearhead：Spearhead-1.49.2+mc1.20.1-mc1.21.1+1.21.1.zip，文件 9302119，明确标注 1.21.1。来源：https://www.curseforge.com/minecraft/customization/tacz-gunpackspearhead/files/9302119
- Enlisted WW2：[TaCZ] Enlisted Gun Pack v1.2.1.3.zip，文件 7640682，标注 1.21.1。作者声明需要 Endless Ammo，实际文件也引用 ea:32acp 等外部弹药。Endless Ammo 官方文件列表目前仅提供 Forge 1.20.1 / 1.19.2 / 1.18.2。本轮只静态审查，未安装 Forge JAR 或擅自提取移植依赖，改补测 Metis。这个结论是测试环境依赖未就绪，不是判定 Enlisted 永远无法兼容。来源：https://www.curseforge.com/minecraft/customization/tacz-enlisted-gunpack ；https://www.curseforge.com/minecraft/mc-mods/tacz-endless-ammo/files/all
- Metis：metis_gun_pack_v1.7.1.zip，文件 8828574，标注 1.20.1，同样作为跨版本边界样本。来源：https://www.curseforge.com/minecraft/mc-addons/metiss-gun-pack-for-tacz/files/8828574

仅本地下载测试，第三方 ZIP、贴图、模型和脚本没有加入源码、发布 JAR 或 Git。RE 为自定义许可，Spearhead 为 All Rights Reserved，Metis 页面为 GPLv3。文件大小、SHA-256、完整候选池和结果见 random-gunpack-results.json。

## 服务端实测

每次只加入一个抽样枪包和 TaCZ 默认枪包，独立新建世界。按同一种子随机排列该包枪械，优先保留每类枪种一个代表，再补足 12 把；测试抽中枪械的全部射击模式。每条射击场景运行 500 tick，再断电观察 15 tick。

使用实际 SentryArmBlockEntity 射击控制器、原生 TaCZ 脚本与有限弹药箱，检查原生击发事件、子弹实体、枪口位置、总耗弹、脚本失败标记、断电后停止，以及成功耗弹对应的弹壳数量。测试臂强制激活并提供转速，靶子使用火控白名单隔离；这次没有重新覆盖真实电机装配、列车和船体运动。

- RE：运行时加载 73 把本包枪械、5 种本包弹药。12 把抽样枪、12 种模式场景，63 项通过、0 失败，1,139 个弹壳。
- Spearhead：加载 53 把 spearhead 命名空间枪械，无自有弹药，复用 TaCZ 弹药。ZIP 中另有 2 个 tacz 枪械索引覆盖，因此静态唯一索引计数为 55，不应称为 55 把新增枪。12 把抽样枪、21 种模式场景，108 项通过、0 失败，4,143 个弹壳。
- Metis：加载 15 把本包枪械、6 种本包弹药。12 把抽样枪、19 种模式场景，98 项通过、0 失败，1,989 个弹壳。

合计 36 把抽样枪、52 种模式场景、269 项通过、0 失败，7,271 个弹壳。每条场景的枪械 ID、模式、击发、子弹、命中记录、初始/剩余弹药、枪口误差和状态均保存在证据 JSON 中。命中记录用于诊断，本轮没有给每种曲射、爆炸或特殊伤害设统一命中率验收线。

RE 首轮 w870_police 没开火，复核发现其最大索敌范围仅 20 格，测试靶却在 24 格。这是测试布置问题。最终测试按每把枪的实际索敌范围缩短靶距，并取消靶子受伤以避免大威力武器提前杀死靶子；复测该枪正常射击。首轮失败记录仍保存在工作区日志，没有算入最终通过统计。

Spearhead 服务端运行时带有之前的 Aero/Sable 开发组件；后续为减少可选运行库干扰临时移出它们。核心版本一致，本轮不比较性能；测试结束恢复原开发组件和 Ars/Applied 枪包，不修改 F: 游戏实例。

## 客户端实测与发现

三个包分别完整加载客户端枪械索引，比较原生第三人称枪口矩阵与服务端同步模型参数，并进行实际资源重载、四种物品显示上下文的弹壳渲染及截图。

- RE：140 个标准枪械模型校验通过，3 个使用枪口回退，0 失败；总计 143 个索引（包含默认包）。26 套可用原生弹壳资源、31 个预览物品，3 个缺失贴图引用。
- Spearhead：121 个标准模型通过，2 个回退，0 失败；123 个索引。24 套可用原生弹壳资源、26 个预览，缺失贴图 0。
- Metis：83 个标准模型通过，2 个回退，0 失败；85 个索引。修复后 25 套可用原生弹壳资源、32 个预览，4 个缺失贴图引用。

这些数量均包含默认枪包，不能把它们加起来称为新增独立模型数量。最大模型枪口位置误差低于 2.4e-7 格。三个客户端均通过真实资源重载校验。

RE 的 re:12755、re:44ae、re:m500 指向缺失的 re:textures/shell/50ae_shell.png。Metis 的 20x145rs、357nitro、65mm_swedish 缺少所声明的弹壳资源；86blk 有模型却没有纹理。修复前最后一种会进入本模组的原生模型渲染分支，显示缺失材质。

alpha.13 在使用枪包弹壳前增加真实贴图资源存在检查。缺模型或缺贴图时，本模组的弹壳物品回退到通用空壳，保留原 AmmoId、名称和堆叠区分。实际截图复核未见这些弹壳出现紫黑缺失材质。没有替枪包补造贴图，也没有改 TaCZ 自己的装饰抛壳效果。

RE 的最新 1.20.1 文件还有工作台数据格式错误（旧 item/nbt 格式无法被当前 TaCZ 的物品 codec 读取）；Metis 含两个大小写/空格不合法的未加载脚本路径。这些上游数据问题没有被改写，不能据射击通过宣称整包配方、工作台或所有脚本完整兼容。

预览为了检查回退路径，会人工创建全部已加载 AmmoId 和一个已移除枪包的样品，包括火箭或未声明弹壳的类型；这不代表这些类型在实际击发时会产出弹壳。

## 复现与范围

仅使用本地官方 ZIP，放入开发目录 run/tacz，逐包隔离。服务端示例：

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21'
../tools/gradle-8.12/bin/gradle.bat runServer -PsentrySmoke=randompacks -PrandomNamespace=re --offline --console=plain
```

另两个命名空间为 spearhead、metis。客户端：

```powershell
../tools/gradle-8.12/bin/gradle.bat runClient -PsentryClientSmoke -PcasingClientSmoke -PsentryClientReloadSmoke --offline --console=plain
```

随机服务端测试入口仅在显式启用 smoke 时编译，发布 JAR 不包含它。检查日志的 RESULT 和 FAIL 标记，不仅看 Gradle 退出码。最终补跑默认枪械/弹壳专项并执行 clean build。

本轮未覆盖全部枪械开火、配件组合、真人多人、装填动画、所有合成配方、曲射解算或其他模组组合。自动弹壳仍依据弹药声明的 shell 资源与服务端规则识别；不会凭完整子弹图标推断并生成任意专属弹壳贴图。库存收集、再装填、createimmersivetacz 桥接仍属后续阶段。

最终回归：PHASE3 68 项通过、0 失败；CASING 15 项通过、0 失败，实际产出 550 个弹壳；clean build 成功。alpha.13 发布 JAR 已检查不含 smoke 类。

修复原则：第三方枪包仅作为验证样本。生产实现不包含本轮枪包名或弹药 ID 的判断，不提供针对这些 ZIP 的覆盖补丁；资源存在检查对任意已加载枪包统一生效。
