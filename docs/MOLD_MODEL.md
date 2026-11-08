# alpha.31：引用 Create 材质的模具模型

模具从自制平面图标改为原生 JSON 立体物品模型：工业铁底座和外框、黄铜模芯、下沉的深色弹壳凹槽、两侧握柄及四角夹紧螺栓。凹槽包含较宽的壳底边缘、壳身和逐级收窄的壳颈；模芯端部保留分模缝。

模型只通过资源路径引用 Create 自身的材质：

- create:block/industrial_iron_block
- create:block/industrial_iron_block_top
- create:block/brass_block
- create:block/dark_metal_block

没有将这些 PNG 复制到本模组资源目录，也不将它们打包进发布 jar。用于理解材质的参考文件仅保存在工作区 work/evidence/alpha31-material-references，位于项目打包目录之外。删除旧的 sentrymechanicalarm:item/casing_mold PNG；物品 ID、口径选择和制造配方沿用 alpha.30。

本轮只编译打包，不运行检查、测试或开发客户端。模型尚未经游戏内视觉确认。

## 后续开发客户端启动修复

用户要求启动时发现 Create JEI Compat 1.0.3 引用了旧 JEI 19.21 不存在的 IRecipeLayoutWithButtons；使用整合包提供的 JEI 19.70.0.469 后，其最低 NeoForge 要求为 21.1.238。开发环境因此对齐本机已有的 NeoForge 21.1.248。

新增 devJeiJar 本地运行时覆盖（指向 work/dependencies 中用户整合包的 JEI 副本）及 devNeoForgeVersion 覆盖，保留原版本目录用于基础依赖。JEI 文件不存在时回退到版本目录里的 JEI；Create JEI Compat 仍是 run/mods 下的外部组件。此修复针对用户要求的正常客户端启动，没有运行自动测试。
