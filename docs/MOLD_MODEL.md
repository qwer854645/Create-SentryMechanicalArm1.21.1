# alpha.31：引用 Create 材质的模具模型

模具从自制平面图标改为原生 JSON 立体物品模型：工业铁底座和外框、黄铜模芯、下沉的深色弹壳凹槽、两侧握柄及四角夹紧螺栓。凹槽包含较宽的壳底边缘、壳身和逐级收窄的壳颈；模芯端部保留分模缝。

模型只通过资源路径引用 Create 自身的材质：

- create:block/industrial_iron_block
- create:block/industrial_iron_block_top
- create:block/brass_block
- create:block/dark_metal_block

没有将这些 PNG 复制到本模组资源目录，也不将它们打包进发布 jar。用于理解材质的参考文件仅保存在工作区 work/evidence/alpha31-material-references，位于项目打包目录之外。删除旧的 sentrymechanicalarm:item/casing_mold PNG；物品 ID、口径选择和制造配方沿用 alpha.30。

本轮只编译打包，不运行检查、测试或开发客户端。模型尚未经游戏内视觉确认。
